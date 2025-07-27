package com.riceawa.llm.context;

import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.core.LLMMessage.MessageRole;
import com.riceawa.llm.core.LLMService;
import com.riceawa.llm.core.LLMConfig;
import com.riceawa.llm.core.LLMResponse;
import com.riceawa.llm.service.LLMServiceManager;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.logging.LogManager;
import net.minecraft.entity.player.PlayerEntity;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * 聊天上下文管理器，管理每个玩家的对话状态
 */
public class ChatContext {

    /**
     * 上下文事件通知接口
     */
    public interface ContextEventListener {
        /**
         * 当上下文即将被压缩时调用
         */
        void onContextCompressionStarted(UUID playerId, int messagesToCompress);

        /**
         * 当上下文压缩完成时调用
         */
        void onContextCompressionCompleted(UUID playerId, boolean success, int originalCount, int compressedCount);
    }
    private final String sessionId;
    private final UUID playerId;
    private final List<LLMMessage> messages;
    private final Map<String, Object> metadata;
    private String currentPromptTemplate;
    private int maxContextLength;
    private long lastActivity;
    private ContextEventListener eventListener;

    public ChatContext(UUID playerId) {
        this.sessionId = UUID.randomUUID().toString();
        this.playerId = playerId;
        this.messages = new ArrayList<>();
        this.metadata = new ConcurrentHashMap<>();
        this.currentPromptTemplate = "default";
        this.maxContextLength = 32768; // 默认最大上下文长度
        this.lastActivity = System.currentTimeMillis();
    }

    /**
     * 添加消息到上下文
     */
    public void addMessage(LLMMessage message) {
        synchronized (messages) {
            messages.add(message);
            updateLastActivity();
            
            // 如果超过最大长度，移除最早的用户消息（保留系统消息）
            trimContext();
        }
    }

    /**
     * 添加用户消息
     */
    public void addUserMessage(String content) {
        addMessage(new LLMMessage(MessageRole.USER, content));
    }

    /**
     * 添加助手消息
     */
    public void addAssistantMessage(String content) {
        addMessage(new LLMMessage(MessageRole.ASSISTANT, content));
    }

    /**
     * 添加系统消息
     */
    public void addSystemMessage(String content) {
        addMessage(new LLMMessage(MessageRole.SYSTEM, content));
    }

    /**
     * 获取所有消息
     */
    public List<LLMMessage> getMessages() {
        synchronized (messages) {
            return new ArrayList<>(messages);
        }
    }

    /**
     * 获取最近的N条消息
     */
    public List<LLMMessage> getRecentMessages(int count) {
        synchronized (messages) {
            int size = messages.size();
            if (size <= count) {
                return new ArrayList<>(messages);
            }
            return new ArrayList<>(messages.subList(size - count, size));
        }
    }

    /**
     * 清空上下文
     */
    public void clear() {
        synchronized (messages) {
            messages.clear();
            updateLastActivity();
        }
    }

    /**
     * 修剪上下文，保持在最大长度内
     * 使用智能压缩而不是简单删除
     */
    private void trimContext() {
        if (messages.size() <= maxContextLength) {
            return;
        }

        // 保留系统消息和最近的消息
        List<LLMMessage> systemMessages = new ArrayList<>();
        List<LLMMessage> otherMessages = new ArrayList<>();

        for (LLMMessage message : messages) {
            if (message.getRole() == MessageRole.SYSTEM) {
                systemMessages.add(message);
            } else {
                otherMessages.add(message);
            }
        }

        // 计算需要保留的非系统消息数量
        int maxOtherMessages = maxContextLength - systemMessages.size();
        if (otherMessages.size() > maxOtherMessages) {
            // 计算需要压缩的消息数量
            int messagesToCompress = otherMessages.size() - maxOtherMessages + (maxOtherMessages / 4); // 压缩前1/4的消息

            if (messagesToCompress > 0) {
                // 通知监听器压缩即将开始
                if (eventListener != null) {
                    eventListener.onContextCompressionStarted(playerId, messagesToCompress);
                }

                // 尝试压缩旧消息
                List<LLMMessage> messagesToCompressSublist = otherMessages.subList(0, messagesToCompress);
                String compressedSummary = compressMessages(messagesToCompressSublist);

                if (compressedSummary != null && !compressedSummary.trim().isEmpty()) {
                    // 压缩成功，用摘要替换旧消息
                    List<LLMMessage> remainingMessages = otherMessages.subList(messagesToCompress, otherMessages.size());

                    // 重新构建消息列表
                    messages.clear();
                    messages.addAll(systemMessages);

                    // 添加压缩摘要作为系统消息
                    messages.add(new LLMMessage(MessageRole.SYSTEM,
                        "=== 对话历史摘要 ===\n" + compressedSummary + "\n=== 以下是最近的对话 ==="));

                    messages.addAll(remainingMessages);

                    LogManager.getInstance().system("Context compressed for session " + sessionId +
                        ", compressed " + messagesToCompress + " messages into summary");

                    // 通知监听器压缩成功
                    if (eventListener != null) {
                        eventListener.onContextCompressionCompleted(playerId, true,
                            messagesToCompress, messages.size());
                    }
                } else {
                    // 压缩失败，回退到简单删除
                    fallbackTrimContext(systemMessages, otherMessages, maxOtherMessages);

                    // 通知监听器压缩失败
                    if (eventListener != null) {
                        eventListener.onContextCompressionCompleted(playerId, false,
                            messagesToCompress, messages.size());
                    }
                }
            }
        }
    }

    /**
     * 回退的上下文修剪方法（简单删除）
     */
    private void fallbackTrimContext(List<LLMMessage> systemMessages, List<LLMMessage> otherMessages, int maxOtherMessages) {
        if (otherMessages.size() > maxOtherMessages) {
            otherMessages = otherMessages.subList(
                otherMessages.size() - maxOtherMessages,
                otherMessages.size()
            );
        }

        // 重新构建消息列表
        messages.clear();
        messages.addAll(systemMessages);
        messages.addAll(otherMessages);

        LogManager.getInstance().system("Context trimmed using fallback method for session " + sessionId);
    }

    /**
     * 压缩消息列表为摘要
     */
    private String compressMessages(List<LLMMessage> messagesToCompress) {
        try {
            LLMServiceManager serviceManager = LLMServiceManager.getInstance();
            LLMService llmService = serviceManager.getDefaultService();

            if (llmService == null || !llmService.isAvailable()) {
                LogManager.getInstance().error("LLM service not available for context compression");
                return null;
            }

            // 构建压缩提示词
            StringBuilder conversationText = new StringBuilder();
            for (LLMMessage message : messagesToCompress) {
                String roleText = message.getRole() == MessageRole.USER ? "用户" : "助手";
                conversationText.append(roleText).append(": ").append(message.getContent()).append("\n");
            }

            String compressionPrompt = "请将以下对话内容压缩成一个简洁的摘要，保留关键信息和上下文：\n\n" +
                conversationText.toString() +
                "\n请用中文回复，摘要应该简洁明了，突出重点内容和讨论的主要话题。";

            // 构建压缩请求
            List<LLMMessage> compressionMessages = new ArrayList<>();
            compressionMessages.add(new LLMMessage(MessageRole.USER, compressionPrompt));

            LLMConfig compressionConfig = new LLMConfig();
            LLMChatConfig config = LLMChatConfig.getInstance();
            compressionConfig.setModel(config.getEffectiveCompressionModel()); // 使用配置的压缩模型
            compressionConfig.setTemperature(0.3); // 使用较低的温度以获得更一致的摘要
            compressionConfig.setMaxTokens(512); // 限制摘要长度

            // 同步调用LLM进行压缩
            CompletableFuture<LLMResponse> future = llmService.chat(compressionMessages, compressionConfig);
            LLMResponse response = future.get(); // 阻塞等待结果

            if (response.isSuccess()) {
                String summary = response.getContent();
                if (summary != null && !summary.trim().isEmpty()) {
                    LogManager.getInstance().system("Successfully compressed " + messagesToCompress.size() +
                        " messages into summary for session " + sessionId);
                    return summary.trim();
                }
            } else {
                LogManager.getInstance().error("Failed to compress context: " + response.getError());
            }
        } catch (Exception e) {
            LogManager.getInstance().error("Error during context compression for session " + sessionId, e);
        }

        return null;
    }

    /**
     * 设置元数据
     */
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
        updateLastActivity();
    }

    /**
     * 获取元数据
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * 获取元数据（带默认值）
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, T defaultValue) {
        Object value = metadata.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return (T) value;
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    /**
     * 更新最后活动时间
     */
    private void updateLastActivity() {
        this.lastActivity = System.currentTimeMillis();
    }

    /**
     * 检查上下文是否过期
     */
    public boolean isExpired(long timeoutMs) {
        return System.currentTimeMillis() - lastActivity > timeoutMs;
    }

    // Getters and Setters
    public String getSessionId() {
        return sessionId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getCurrentPromptTemplate() {
        return currentPromptTemplate;
    }

    public void setCurrentPromptTemplate(String currentPromptTemplate) {
        this.currentPromptTemplate = currentPromptTemplate;
        updateLastActivity();
    }

    public int getMaxContextLength() {
        return maxContextLength;
    }

    public void setMaxContextLength(int maxContextLength) {
        this.maxContextLength = maxContextLength;
        updateLastActivity();
    }

    public long getLastActivity() {
        return lastActivity;
    }

    public int getMessageCount() {
        return messages.size();
    }

    /**
     * 设置上下文事件监听器
     */
    public void setEventListener(ContextEventListener eventListener) {
        this.eventListener = eventListener;
    }

    /**
     * 获取上下文事件监听器
     */
    public ContextEventListener getEventListener() {
        return eventListener;
    }
}
