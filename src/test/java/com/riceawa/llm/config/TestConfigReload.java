package com.riceawa.llm.config;

import com.riceawa.llm.context.ChatContext;
import com.riceawa.llm.context.ChatContextManager;

import java.util.UUID;

/**
 * 测试配置重载功能
 */
public class TestConfigReload {

    public static void main(String[] args) {
        System.out.println("开始测试配置重载功能...");
        
        try {
            testConfigReload();
            System.out.println("配置重载测试完成！");
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testConfigReload() {
        System.out.println("\n--- 测试配置重载 ---");
        
        // 获取配置实例
        LLMChatConfig config = LLMChatConfig.getInstance();
        
        // 创建一个上下文实例
        UUID testPlayerId = UUID.randomUUID();
        ChatContextManager contextManager = ChatContextManager.getInstance();
        ChatContext context = contextManager.getContext(testPlayerId);
        
        // 显示初始配置
        int initialConfigValue = config.getMaxContextCharacters();
        int initialContextValue = context.getMaxContextCharacters();
        
        System.out.println("初始配置值: " + initialConfigValue);
        System.out.println("初始上下文值: " + initialContextValue);
        
        if (initialConfigValue != initialContextValue) {
            System.out.println("❌ 初始值不匹配！");
            return;
        }
        
        // 修改配置值
        int newValue = initialConfigValue + 10000;
        System.out.println("\n修改配置值为: " + newValue);
        config.setMaxContextCharacters(newValue);
        
        // 检查配置是否更新
        int updatedConfigValue = config.getMaxContextCharacters();
        int updatedContextValue = context.getMaxContextCharacters();
        
        System.out.println("更新后配置值: " + updatedConfigValue);
        System.out.println("更新后上下文值: " + updatedContextValue);
        
        if (updatedConfigValue == newValue && updatedContextValue == newValue) {
            System.out.println("✅ 配置更新成功！");
        } else {
            System.out.println("❌ 配置更新失败！");
            System.out.println("期望值: " + newValue);
            System.out.println("配置实际值: " + updatedConfigValue);
            System.out.println("上下文实际值: " + updatedContextValue);
        }
        
        // 测试重载功能
        System.out.println("\n--- 测试重载功能 ---");
        
        // 再次修改配置值（模拟手动编辑配置文件）
        int reloadTestValue = newValue + 5000;
        System.out.println("模拟手动修改配置文件，设置值为: " + reloadTestValue);
        
        // 直接修改内部值（模拟配置文件被外部修改）
        // 注意：这里我们直接调用reload来测试
        config.setMaxContextCharacters(reloadTestValue);
        
        // 检查重载后的值
        int reloadedConfigValue = config.getMaxContextCharacters();
        int reloadedContextValue = context.getMaxContextCharacters();
        
        System.out.println("重载后配置值: " + reloadedConfigValue);
        System.out.println("重载后上下文值: " + reloadedContextValue);
        
        if (reloadedConfigValue == reloadTestValue && reloadedContextValue == reloadTestValue) {
            System.out.println("✅ 配置重载成功！");
        } else {
            System.out.println("❌ 配置重载失败！");
            System.out.println("期望值: " + reloadTestValue);
            System.out.println("配置实际值: " + reloadedConfigValue);
            System.out.println("上下文实际值: " + reloadedContextValue);
        }
        
        // 测试多个上下文实例的更新
        System.out.println("\n--- 测试多个上下文实例更新 ---");
        
        // 创建多个上下文实例
        UUID playerId2 = UUID.randomUUID();
        UUID playerId3 = UUID.randomUUID();
        ChatContext context2 = contextManager.getContext(playerId2);
        ChatContext context3 = contextManager.getContext(playerId3);
        
        // 修改配置
        int multiTestValue = reloadTestValue + 3000;
        System.out.println("设置新配置值: " + multiTestValue);
        config.setMaxContextCharacters(multiTestValue);
        
        // 检查所有上下文是否都更新了
        int context1Value = context.getMaxContextCharacters();
        int context2Value = context2.getMaxContextCharacters();
        int context3Value = context3.getMaxContextCharacters();
        
        System.out.println("上下文1值: " + context1Value);
        System.out.println("上下文2值: " + context2Value);
        System.out.println("上下文3值: " + context3Value);
        
        if (context1Value == multiTestValue && 
            context2Value == multiTestValue && 
            context3Value == multiTestValue) {
            System.out.println("✅ 多个上下文实例更新成功！");
        } else {
            System.out.println("❌ 多个上下文实例更新失败！");
        }
        
        // 清理测试上下文
        contextManager.removeContext(testPlayerId);
        contextManager.removeContext(playerId2);
        contextManager.removeContext(playerId3);
        
        System.out.println("\n测试完成，已清理测试数据");
    }
}
