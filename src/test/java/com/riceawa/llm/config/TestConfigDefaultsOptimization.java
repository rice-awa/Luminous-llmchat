package com.riceawa.llm.config;
import com.riceawa.llm.config.ConfigDefaults;

/**
 * 测试配置默认值优化
 */
public class TestConfigDefaultsOptimization {
    public static void main(String[] args) {
        System.out.println("=== 测试配置默认值优化 ===");
        
        // 1. 测试默认值获取
        testDefaultValueRetrieval();
        
        // 2. 测试配置验证
        testConfigValidation();
        
        // 3. 测试占位符检测
        testPlaceholderDetection();
        
        // 4. 测试默认对象创建
        testDefaultObjectCreation();
        
        System.out.println("\n=== 测试完成 ===");
    }
    
    private static void testDefaultValueRetrieval() {
        System.out.println("\n--- 测试默认值获取 ---");
        
        String[] configKeys = {
            "defaultPromptTemplate",
            "defaultTemperature", 
            "defaultMaxTokens",
            "maxContextCharacters",
            "enableHistory",
            "enableFunctionCalling",
            "enableBroadcast",
            "historyRetentionDays",
            "compressionModel",
            "enableCompressionNotification",
            "enableGlobalContext",
            "globalContextPrompt",
            "currentProvider",
            "currentModel"
        };
        
        for (String key : configKeys) {
            Object defaultValue = ConfigDefaults.getDefaultValue(key);
            System.out.println(key + " -> " + defaultValue + " (" + 
                (defaultValue != null ? defaultValue.getClass().getSimpleName() : "null") + ")");
        }
        
        // 测试未知配置项
        Object unknownValue = ConfigDefaults.getDefaultValue("unknownConfig");
        System.out.println("unknownConfig -> " + unknownValue);
        
        System.out.println("✅ 默认值获取测试完成");
    }
    
    private static void testConfigValidation() {
        System.out.println("\n--- 测试配置验证 ---");
        
        Object[][] testCases = {
            // 温度测试
            {"defaultTemperature", 0.5, true},
            {"defaultTemperature", -0.1, false},
            {"defaultTemperature", 2.1, false},
            {"defaultTemperature", 0.0, true},
            {"defaultTemperature", 2.0, true},
            
            // Token数量测试
            {"defaultMaxTokens", 8192, true},
            {"defaultMaxTokens", 0, false},
            {"defaultMaxTokens", -100, false},
            {"defaultMaxTokens", 1000000, true},
            {"defaultMaxTokens", 1000001, false},
            
            // 上下文字符数测试
            {"maxContextCharacters", 100000, true},
            {"maxContextCharacters", 0, false},
            {"maxContextCharacters", 1000000, true},
            
            // 历史保留天数测试
            {"historyRetentionDays", 30, true},
            {"historyRetentionDays", 0, false},
            {"historyRetentionDays", 366, false},
            {"historyRetentionDays", 365, true},
            
            // 未知配置项测试
            {"unknownConfig", "anyValue", true}
        };
        
        for (Object[] testCase : testCases) {
            String key = (String) testCase[0];
            Object value = testCase[1];
            boolean expected = (Boolean) testCase[2];
            
            boolean result = ConfigDefaults.isValidConfigValue(key, value);
            String status = result == expected ? "✅" : "❌";
            System.out.println(status + " " + key + "=" + value + " -> " + result + " (expected: " + expected + ")");
        }
        
        System.out.println("✅ 配置验证测试完成");
    }
    
    private static void testPlaceholderDetection() {
        System.out.println("\n--- 测试占位符检测 ---");
        
        String[] testKeys = {
            "your-api-key-here",
            "your-openai-api-key-here", 
            "your-deepseek-api-key-here",
            "sk-123", // 太短的sk-开头密钥
            "sk-1234567890abcdef", // 短的sk-开头密钥
            "sk-1234567890abcdef1234567890abcdef12345678", // 正常长度的sk-开头密钥
            "placeholder-key",
            "example-key",
            "replace-me-with-real-key",
            "real-api-key-abcdef1234567890abcdef1234567890",
            "",
            null
        };
        
        for (String key : testKeys) {
            boolean isPlaceholder = ConfigDefaults.isPlaceholderApiKey(key);
            System.out.println("'" + key + "' -> " + (isPlaceholder ? "占位符" : "有效"));
        }
        
        System.out.println("✅ 占位符检测测试完成");
    }
    
    private static void testDefaultObjectCreation() {
        System.out.println("\n--- 测试默认对象创建 ---");
        
        try {
            // 测试默认Provider创建
            var providers = ConfigDefaults.createDefaultProviders();
            System.out.println("默认Provider数量: " + providers.size());
            
            for (var provider : providers) {
                System.out.println("  - " + provider.getName() + ": " + 
                    provider.getModels().size() + "个模型, API密钥: " + 
                    (ConfigDefaults.isPlaceholderApiKey(provider.getApiKey()) ? "占位符" : "已设置"));
            }
            
            // 测试默认广播玩家集合创建
            var broadcastPlayers = ConfigDefaults.createDefaultBroadcastPlayers();
            System.out.println("默认广播玩家集合大小: " + broadcastPlayers.size());
            System.out.println("默认广播玩家集合类型: " + broadcastPlayers.getClass().getSimpleName());
            
            // 测试常量值
            System.out.println("API密钥占位符: '" + ConfigDefaults.API_KEY_PLACEHOLDER + "'");
            System.out.println("空字符串常量: '" + ConfigDefaults.EMPTY_STRING + "'");
            
            System.out.println("✅ 默认对象创建测试完成");
            
        } catch (Exception e) {
            System.err.println("❌ 默认对象创建测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
