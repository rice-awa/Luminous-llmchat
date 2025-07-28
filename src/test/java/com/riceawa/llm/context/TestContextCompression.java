package com.riceawa.llm.context;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.context.ChatContext;
import com.riceawa.llm.context.ChatContextManager;
import com.riceawa.llm.core.LLMMessage;
import java.util.UUID;

/**
 * 测试上下文压缩功能
 */
public class TestContextCompression {
    public static void main(String[] args) {
        System.out.println("=== 测试上下文压缩功能 ===");
        
        // 1. 测试配置读取
        LLMChatConfig config = LLMChatConfig.getInstance();
        System.out.println("配置中的 maxContextLength: " + config.getMaxContextLength());
        
        // 2. 测试新创建的 ChatContext 是否使用配置值
        UUID testPlayerId = UUID.randomUUID();
        ChatContext context = new ChatContext(testPlayerId);
        System.out.println("新创建的 ChatContext maxContextLength: " + context.getMaxContextLength());
        
        // 3. 测试通过 ChatContextManager 创建的上下文
        ChatContextManager manager = ChatContextManager.getInstance();
        ChatContext managedContext = manager.getContext(testPlayerId);
        System.out.println("通过 ChatContextManager 创建的 ChatContext maxContextLength: " + managedContext.getMaxContextLength());
        
        // 4. 测试添加消息是否会触发压缩
        System.out.println("\n=== 测试消息添加和压缩触发 ===");
        System.out.println("当前消息数量: " + managedContext.getMessageCount());
        
        // 添加第一条消息
        managedContext.addUserMessage("第一条测试消息");
        System.out.println("添加第一条消息后，消息数量: " + managedContext.getMessageCount());
        
        // 添加第二条消息（应该触发压缩，因为 maxContextLength = 1）
        managedContext.addUserMessage("第二条测试消息");
        System.out.println("添加第二条消息后，消息数量: " + managedContext.getMessageCount());
        
        // 5. 测试配置更新
        System.out.println("\n=== 测试配置更新 ===");
        System.out.println("更新配置前 maxContextLength: " + managedContext.getMaxContextLength());
        
        // 更新配置
        config.setMaxContextLength(5);
        System.out.println("更新配置后 maxContextLength: " + managedContext.getMaxContextLength());
        
        System.out.println("\n=== 测试完成 ===");
    }
}
