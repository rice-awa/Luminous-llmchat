package com.riceawa.llm.config;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.config.Provider;
import com.riceawa.llm.config.ConfigDefaults;
import java.util.List;

/**
 * 测试优化后的配置系统
 */
public class TestOptimizedConfig {
    public static void main(String[] args) {
        System.out.println("=== 测试优化后的配置系统 ===");
        
        try {
            // 1. 测试默认配置生成
            testDefaultConfiguration();
            
            // 2. 测试动态Provider检测
            testDynamicProviderDetection();
            
            // 3. 测试故障自动切换
            testAutoFailover();
            
            // 4. 测试配置验证和修复
            testConfigurationValidation();
            
        } catch (Exception e) {
            System.err.println("测试过程中发生错误: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("\n=== 测试完成 ===");
    }
    
    private static void testDefaultConfiguration() {
        System.out.println("\n--- 测试默认配置生成 ---");
        
        try {
            LLMChatConfig config = LLMChatConfig.getInstance();
            
            // 检查默认值
            System.out.println("默认maxContextCharacters: " + config.getMaxContextCharacters());
            System.out.println("默认temperature: " + config.getDefaultTemperature());
            System.out.println("默认maxTokens: " + config.getDefaultMaxTokens());
            
            // 检查Provider数量
            List<Provider> providers = config.getProviders();
            System.out.println("默认Provider数量: " + providers.size());
            
            for (Provider provider : providers) {
                System.out.println("  - " + provider.getName() + ": " + provider.getModels().size() + "个模型");
            }
            
            // 检查当前选择
            System.out.println("当前Provider: " + config.getCurrentProvider());
            System.out.println("当前Model: " + config.getCurrentModel());
            
            System.out.println("✅ 默认配置生成测试完成");
            
        } catch (Exception e) {
            System.err.println("❌ 默认配置生成测试失败: " + e.getMessage());
        }
    }
    
    private static void testDynamicProviderDetection() {
        System.out.println("\n--- 测试动态Provider检测 ---");
        
        try {
            LLMChatConfig config = LLMChatConfig.getInstance();
            
            // 检查有效Provider
            List<Provider> validProviders = config.getValidProviders();
            System.out.println("有效Provider数量: " + validProviders.size());
            
            // 显示配置报告
            String report = config.getConfigurationReport();
            System.out.println("配置报告:");
            System.out.println(report);
            
            // 测试占位符检测
            System.out.println("\n占位符检测测试:");
            String[] testKeys = {
                "your-api-key-here",
                "sk-1234567890abcdef",
                "real-api-key-example-12345678901234567890",
                "",
                null
            };
            
            for (String key : testKeys) {
                boolean isPlaceholder = ConfigDefaults.isPlaceholderApiKey(key);
                System.out.println("  '" + key + "' -> " + (isPlaceholder ? "占位符" : "有效"));
            }
            
            System.out.println("✅ 动态Provider检测测试完成");
            
        } catch (Exception e) {
            System.err.println("❌ 动态Provider检测测试失败: " + e.getMessage());
        }
    }
    
    private static void testAutoFailover() {
        System.out.println("\n--- 测试故障自动切换 ---");
        
        try {
            LLMChatConfig config = LLMChatConfig.getInstance();
            
            // 保存原始配置
            String originalProvider = config.getCurrentProvider();
            String originalModel = config.getCurrentModel();
            
            System.out.println("原始配置: " + originalProvider + " / " + originalModel);
            
            // 测试自动修复功能
            String fixResult = config.autoFixConfiguration();
            System.out.println("自动修复结果: " + fixResult);
            
            System.out.println("修复后配置: " + config.getCurrentProvider() + " / " + config.getCurrentModel());
            
            // 测试无效配置的处理
            System.out.println("\n测试无效配置处理:");
            
            // 设置无效的Provider和Model
            config.setCurrentProvider("invalid-provider");
            config.setCurrentModel("invalid-model");
            
            System.out.println("设置无效配置: " + config.getCurrentProvider() + " / " + config.getCurrentModel());
            
            // 检查配置有效性
            boolean isValid = config.isProviderModelValid(config.getCurrentProvider(), config.getCurrentModel());
            System.out.println("配置是否有效: " + isValid);
            
            // 再次自动修复
            String fixResult2 = config.autoFixConfiguration();
            System.out.println("再次修复结果: " + fixResult2);
            System.out.println("最终配置: " + config.getCurrentProvider() + " / " + config.getCurrentModel());
            
            System.out.println("✅ 故障自动切换测试完成");
            
        } catch (Exception e) {
            System.err.println("❌ 故障自动切换测试失败: " + e.getMessage());
        }
    }
    
    private static void testConfigurationValidation() {
        System.out.println("\n--- 测试配置验证和修复 ---");
        
        try {
            LLMChatConfig config = LLMChatConfig.getInstance();
            
            // 测试配置值验证
            System.out.println("配置值验证测试:");
            
            Object[][] testCases = {
                {"defaultTemperature", 0.5, true},
                {"defaultTemperature", -1.0, false},
                {"defaultTemperature", 3.0, false},
                {"defaultMaxTokens", 8192, true},
                {"defaultMaxTokens", -100, false},
                {"maxContextCharacters", 100000, true},
                {"maxContextCharacters", 0, false}
            };
            
            for (Object[] testCase : testCases) {
                String key = (String) testCase[0];
                Object value = testCase[1];
                boolean expected = (Boolean) testCase[2];
                
                boolean result = ConfigDefaults.isValidConfigValue(key, value);
                String status = result == expected ? "✅" : "❌";
                System.out.println("  " + status + " " + key + "=" + value + " -> " + result);
            }
            
            // 测试第一次使用检测
            boolean isFirstTime = config.isFirstTimeUse();
            System.out.println("\n是否第一次使用: " + isFirstTime);
            
            // 测试配置完整性
            boolean hasValidProviders = config.hasAnyValidProvider();
            System.out.println("是否有有效Provider: " + hasValidProviders);
            
            System.out.println("✅ 配置验证和修复测试完成");
            
        } catch (Exception e) {
            System.err.println("❌ 配置验证和修复测试失败: " + e.getMessage());
        }
    }
}
