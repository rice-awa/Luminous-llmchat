package com.riceawa.llm.service;

import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.core.LLMConfig;
import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.core.LLMResponse;
import com.riceawa.llm.core.LLMService;
import com.riceawa.llm.core.LLMContext;
import com.riceawa.llm.core.LLMMessage.MessageRole;
import com.riceawa.llm.logging.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 对话标题生成服务
 * 使用LLM为对话会话生成简洁的标题
 */
public class TitleGenerationService {
    private static TitleGenerationService instance;
    private static final String TITLE_GENERATION_PROMPT = 
        "请为以下对话生成一个简洁的中文标题（10-20字），概括对话的主要内容：\n\n%s\n\n" +
        "要求：\n" +
        "- 标题要简洁明了，突出重点\n" +
        "- 使用中文\n" +
        "- 长度控制在10-20字\n" +
        "- 不要包含标点符号\n" +
        "- 直接返回标题，不要其他内容";

    private TitleGenerationService() {
    }

    public static TitleGenerationService getInstance() {
        if (instance == null) {
            synchronized (TitleGenerationService.class) {
                if (instance == null) {
                    instance = new TitleGenerationService();
                }
            }
        }
        return instance;
    }

    /**
     * 为对话消息生成标题
     * @param messages 对话消息列表
     * @return 异步返回生成的标题，失败时返回null
     */
    public CompletableFuture<String> generateTitle(List<LLMMessage> messages) {
        LLMChatConfig config = LLMChatConfig.getInstance();
        
        // 检查是否启用标题生成
        if (!config.isEnableTitleGeneration()) {
            return CompletableFuture.completedFuture(null);
        }

        // 检查消息数量，至少需要2条消息（用户问题+AI回答）
        if (messages == null || messages.size() < 2) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            // 获取LLM服务
            LLMServiceManager serviceManager = LLMServiceManager.getInstance();
            LLMService llmService = serviceManager.getDefaultService();
            
            if (llmService == null || !llmService.isAvailable()) {
                LogManager.getInstance().system("LLM service not available for title generation");
                return CompletableFuture.completedFuture(null);
            }

            // 构建用于标题生成的对话内容
            String conversationText = buildConversationText(messages);
            
            // 构建标题生成请求
            List<LLMMessage> titleMessages = new ArrayList<>();
            titleMessages.add(new LLMMessage(MessageRole.USER, 
                String.format(TITLE_GENERATION_PROMPT, conversationText)));

            // 配置标题生成参数
            LLMConfig titleConfig = new LLMConfig();
            titleConfig.setModel(config.getEffectiveTitleGenerationModel());
            titleConfig.setTemperature(0.3); // 使用较低的温度获得更一致的结果
            titleConfig.setMaxTokens(50); // 限制输出长度
            
            // 创建标题生成上下文
            LLMContext titleContext = LLMContext.builder()
                    .metadata("operation", "title_generation")
                    .metadata("message_count", messages.size())
                    .build();

            // 异步生成标题
            return llmService.chat(titleMessages, titleConfig, titleContext)
                .thenApply(this::extractTitleFromResponse)
                .completeOnTimeout(null, 10, TimeUnit.SECONDS) // 10秒超时
                .exceptionally(throwable -> {
                    LogManager.getInstance().system("Title generation failed: " + throwable.getMessage());
                    return null;
                });

        } catch (Exception e) {
            LogManager.getInstance().system("Title generation error: " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * 构建用于标题生成的对话文本
     * 只使用前几轮对话，避免内容过长
     */
    private String buildConversationText(List<LLMMessage> messages) {
        StringBuilder conversationText = new StringBuilder();
        int messageCount = 0;
        int maxMessages = 6; // 最多使用前6条消息（约3轮对话）
        
        for (LLMMessage message : messages) {
            if (messageCount >= maxMessages) {
                break;
            }
            
            // 跳过系统消息
            if (message.getRole() == MessageRole.SYSTEM) {
                continue;
            }
            
            String roleText = message.getRole() == MessageRole.USER ? "用户" : "助手";
            conversationText.append(roleText).append(": ");
            
            // 限制单条消息长度
            String content = message.getContent();
            if (content.length() > 200) {
                content = content.substring(0, 200) + "...";
            }
            
            conversationText.append(content).append("\n");
            messageCount++;
        }
        
        return conversationText.toString();
    }

    /**
     * 从LLM响应中提取标题
     */
    private String extractTitleFromResponse(LLMResponse response) {
        if (response == null || !response.isSuccess() || response.getContent() == null) {
            return null;
        }
        
        String title = response.getContent().trim();
        
        // 验证标题长度和内容
        if (title.isEmpty() || title.length() > 50) {
            return null;
        }
        
        // 移除可能的引号和标点符号
        title = title.replaceAll("[\"'。，！？；：]", "");
        
        // 如果标题太短或太长，返回null
        if (title.length() < 2 || title.length() > 30) {
            return null;
        }
        
        LogManager.getInstance().system("Generated title: " + title);
        return title;
    }

    /**
     * 检查消息列表是否适合生成标题
     */
    public boolean shouldGenerateTitle(List<LLMMessage> messages) {
        if (messages == null || messages.size() < 2) {
            return false;
        }
        
        // 检查是否包含用户消息和助手回复
        boolean hasUserMessage = false;
        boolean hasAssistantMessage = false;
        
        for (LLMMessage message : messages) {
            if (message.getRole() == MessageRole.USER) {
                hasUserMessage = true;
            } else if (message.getRole() == MessageRole.ASSISTANT) {
                hasAssistantMessage = true;
            }
            
            if (hasUserMessage && hasAssistantMessage) {
                return true;
            }
        }
        
        return false;
    }
}
