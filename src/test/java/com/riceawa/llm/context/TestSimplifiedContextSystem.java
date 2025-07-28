package com.riceawa.llm.context;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.context.ChatContext;
import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.core.LLMMessage.MessageRole;
import java.util.UUID;

/**
 * 测试简化后的字符长度限制系统
 */
public class TestSimplifiedContextSystem {
    public static void main(String[] args) {
        System.out.println("=== 测试简化的字符长度限制系统 ===");
        
        // 1. 测试基本配置
        testBasicConfiguration();
        
        // 2. 测试字符长度计算和缓存
        testCharacterCalculation();
        
        // 3. 测试完整消息压缩
        testCompleteMessageCompression();
        
        // 4. 测试回退删除策略
        testFallbackDeletion();
        
        System.out.println("\n=== 测试完成 ===");
    }
    
    private static void testBasicConfiguration() {
        System.out.println("\n--- 测试基本配置 ---");
        
        LLMChatConfig config = LLMChatConfig.getInstance();
        
        // 测试默认值
        System.out.println("默认 maxContextCharacters: " + config.getMaxContextCharacters());
        
        // 测试设置新值
        config.setMaxContextCharacters(1000);
        System.out.println("设置后 maxContextCharacters: " + config.getMaxContextCharacters());
        
        // 测试向后兼容的方法名
        System.out.println("通过getMaxContextLength获取: " + config.getMaxContextLength());
        
        config.setMaxContextLength(2000);
        System.out.println("通过setMaxContextLength设置后: " + config.getMaxContextCharacters());
    }
    
    private static void testCharacterCalculation() {
        System.out.println("\n--- 测试字符长度计算和缓存 ---");
        
        UUID testPlayerId = UUID.randomUUID();
        ChatContext context = new ChatContext(testPlayerId);
        
        // 添加测试消息
        context.addSystemMessage("系统消息：这是一个测试系统消息。");
        context.addUserMessage("用户消息：Hello World! 你好世界！这是一个包含中英文的测试消息。");
        context.addAssistantMessage("助手回复：这是一个详细的助手回复，包含更多的文字内容来测试字符长度计算功能的准确性和性能。");
        
        // 第一次计算（会缓存）
        long startTime = System.nanoTime();
        int totalCharacters1 = context.calculateTotalCharacters();
        long endTime1 = System.nanoTime();
        
        // 第二次计算（使用缓存）
        long startTime2 = System.nanoTime();
        int totalCharacters2 = context.calculateTotalCharacters();
        long endTime2 = System.nanoTime();
        
        System.out.println("消息数量: " + context.getMessageCount());
        System.out.println("总字符长度: " + totalCharacters1);
        System.out.println("第一次计算耗时: " + (endTime1 - startTime) + " 纳秒");
        System.out.println("第二次计算耗时: " + (endTime2 - startTime2) + " 纳秒");
        
        if (totalCharacters1 == totalCharacters2) {
            System.out.println("✅ 字符长度计算和缓存机制正常");
        } else {
            System.out.println("❌ 字符长度计算或缓存机制有问题");
        }
    }
    
    private static void testCompleteMessageCompression() {
        System.out.println("\n--- 测试完整消息压缩 ---");
        
        UUID testPlayerId = UUID.randomUUID();
        ChatContext context = new ChatContext(testPlayerId);
        context.setMaxContextCharacters(500); // 设置较小的字符限制
        
        // 添加系统消息
        context.addSystemMessage("系统消息：这是一个重要的系统消息，应该被保留。");
        
        // 添加多个用户和助手消息
        for (int i = 1; i <= 5; i++) {
            context.addUserMessage("用户消息" + i + "：这是第" + i + "个用户消息，包含一些测试内容。每个消息都有足够的长度来测试压缩功能。");
            context.addAssistantMessage("助手回复" + i + "：这是对第" + i + "个用户消息的详细回复，包含更多的文字内容来模拟真实的对话场景。");
        }
        
        int finalMessageCount = context.getMessageCount();
        int finalCharacters = context.calculateTotalCharacters();
        
        System.out.println("添加10条消息后的状态：");
        System.out.println("最终消息数量: " + finalMessageCount);
        System.out.println("最终字符长度: " + finalCharacters);
        System.out.println("字符限制: " + context.getMaxContextCharacters());
        
        if (finalCharacters <= context.getMaxContextCharacters()) {
            System.out.println("✅ 字符长度限制正常工作，消息被正确压缩");
        } else {
            System.out.println("❌ 字符长度限制未生效");
        }
        
        // 检查是否保留了系统消息
        boolean hasSystemMessage = context.getMessages().stream()
            .anyMatch(msg -> msg.getRole() == MessageRole.SYSTEM && 
                           msg.getContent().contains("系统消息"));
        
        if (hasSystemMessage) {
            System.out.println("✅ 系统消息被正确保留");
        } else {
            System.out.println("❌ 系统消息丢失");
        }
    }
    
    private static void testFallbackDeletion() {
        System.out.println("\n--- 测试回退删除策略 ---");
        
        UUID testPlayerId = UUID.randomUUID();
        ChatContext context = new ChatContext(testPlayerId);
        context.setMaxContextCharacters(300); // 设置很小的字符限制
        
        // 添加系统消息
        context.addSystemMessage("系统消息");
        
        // 添加一些较长的消息
        context.addUserMessage("这是一个很长的用户消息，包含大量的文字内容，用来测试当压缩失败时的回退删除策略是否能正确工作。");
        context.addAssistantMessage("这是一个很长的助手回复，同样包含大量的文字内容，继续测试回退删除策略的有效性。");
        context.addUserMessage("这是另一个用户消息，用来进一步测试系统的处理能力。");
        context.addAssistantMessage("这是最后一个助手回复，用来完成测试。");
        
        int finalMessageCount = context.getMessageCount();
        int finalCharacters = context.calculateTotalCharacters();
        
        System.out.println("回退删除后的状态：");
        System.out.println("最终消息数量: " + finalMessageCount);
        System.out.println("最终字符长度: " + finalCharacters);
        System.out.println("字符限制: " + context.getMaxContextCharacters());
        
        if (finalCharacters <= context.getMaxContextCharacters()) {
            System.out.println("✅ 回退删除策略正常工作");
        } else {
            System.out.println("❌ 回退删除策略未生效");
        }
        
        // 检查是否保留了最新的消息
        if (finalMessageCount > 0) {
            LLMMessage lastMessage = context.getMessages().get(context.getMessages().size() - 1);
            if (lastMessage.getContent().contains("最后一个")) {
                System.out.println("✅ 最新消息被正确保留");
            } else {
                System.out.println("⚠️ 最新消息可能未被保留（这在极端情况下是正常的）");
            }
        }
    }
}
