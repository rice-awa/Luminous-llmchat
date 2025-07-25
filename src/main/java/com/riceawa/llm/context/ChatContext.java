package com.riceawa.llm.context;

import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.core.LLMMessage.MessageRole;
import net.minecraft.entity.player.PlayerEntity;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天上下文管理器，管理每个玩家的对话状态
 */
public class ChatContext {
    private final String sessionId;
    private final UUID playerId;
    private final List<LLMMessage> messages;
    private final Map<String, Object> metadata;
    private String currentPromptTemplate;
    private int maxContextLength;
    private long lastActivity;

    public ChatContext(UUID playerId) {
        this.sessionId = UUID.randomUUID().toString();
        this.playerId = playerId;
        this.messages = new ArrayList<>();
        this.metadata = new ConcurrentHashMap<>();
        this.currentPromptTemplate = "default";
        this.maxContextLength = 4000; // 默认最大上下文长度
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
            otherMessages = otherMessages.subList(
                otherMessages.size() - maxOtherMessages, 
                otherMessages.size()
            );
        }

        // 重新构建消息列表
        messages.clear();
        messages.addAll(systemMessages);
        messages.addAll(otherMessages);
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
}
