package com.riceawa.llm.context;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.context.ChatContext;
import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.core.LLMMessage.MessageRole;
import java.util.UUID;

/**
 * 测试字符长度限制功能
 */
public class TestContextCharacterLimit {
    public static void main(String[] args) {
        System.out.println("=== 测试字符长度限制功能 ===");
        
        // 1. 测试配置
        testConfiguration();
        
        // 2. 测试字符长度计算
        testCharacterCalculation();
        
        // 3. 测试字符长度限制
        testCharacterLimitMode();
        
        System.out.println("\n=== 测试完成 ===");
    }
    
    private static void testConfiguration() {
        System.out.println("\n--- 测试配置 ---");
        
        LLMChatConfig config = LLMChatConfig.getInstance();
        
        // 测试默认值
        System.out.println("默认 maxContextCharacters: " + config.getMaxContextCharacters());

        // 测试设置新值
        config.setMaxContextCharacters(5000);

        System.out.println("设置后 maxContextCharacters: " + config.getMaxContextCharacters());
    }
    
    private static void testCharacterCalculation() {
        System.out.println("\n--- 测试字符长度计算 ---");
        
        UUID testPlayerId = UUID.randomUUID();
        ChatContext context = new ChatContext(testPlayerId);
        
        // 添加一些测试消息
        context.addSystemMessage("这是系统消息，包含中文字符。");
        context.addUserMessage("用户消息：Hello World! 你好世界！");
        context.addAssistantMessage("助手回复：这是一个测试回复，包含更多的文字内容来测试字符长度计算功能。");
        
        int totalCharacters = context.calculateTotalCharacters();
        System.out.println("总消息数量: " + context.getMessageCount());
        System.out.println("总字符长度: " + totalCharacters);
        
        // 验证缓存机制
        long startTime = System.nanoTime();
        int cachedResult = context.calculateTotalCharacters();
        long endTime = System.nanoTime();
        
        System.out.println("缓存结果: " + cachedResult);
        System.out.println("缓存查询耗时: " + (endTime - startTime) + " 纳秒");
        
        if (totalCharacters == cachedResult) {
            System.out.println("✅ 字符长度计算和缓存机制正常");
        } else {
            System.out.println("❌ 字符长度计算或缓存机制有问题");
        }
    }
    
    private static void testCharacterLimitMode() {
        System.out.println("\n--- 测试字符长度限制模式 ---");

        // 测试字符长度限制
        testCharacterMode();
    }
    

    
    private static void testCharacterMode() {
        System.out.println("\n子测试：按字符长度限制");
        
        UUID testPlayerId = UUID.randomUUID();
        ChatContext context = new ChatContext(testPlayerId);
        context.setMaxContextCharacters(100); // 设置较小的字符限制
        
        // 添加长消息
        context.addSystemMessage("系统消息");
        context.addUserMessage("这是一个很长的用户消息，包含大量文字内容，用来测试字符长度限制功能是否正常工作。");
        context.addAssistantMessage("这是一个很长的助手回复，同样包含大量文字内容，继续测试字符长度限制。");
        
        int totalCharacters = context.calculateTotalCharacters();
        System.out.println("总字符长度: " + totalCharacters);
        System.out.println("字符限制: " + context.getMaxContextCharacters());
        
        if (totalCharacters <= context.getMaxContextCharacters()) {
            System.out.println("✅ 字符长度限制正常工作");
        } else {
            System.out.println("❌ 字符长度限制未生效");
        }
    }
}
