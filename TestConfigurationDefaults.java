import com.riceawa.llm.config.LLMChatConfig;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 测试配置默认值生成逻辑
 */
public class TestConfigurationDefaults {
    public static void main(String[] args) {
        System.out.println("=== 测试配置默认值生成逻辑 ===");
        
        try {
            // 1. 测试删除配置文件后的重新生成
            testConfigFileRegeneration();
            
            // 2. 测试配置验证逻辑
            testConfigurationValidation();
            
            // 3. 测试向后兼容性
            testBackwardCompatibility();
            
        } catch (Exception e) {
            System.err.println("测试过程中发生错误: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("\n=== 测试完成 ===");
    }
    
    private static void testConfigFileRegeneration() {
        System.out.println("\n--- 测试配置文件重新生成 ---");
        
        try {
            // 模拟删除配置文件的情况
            System.out.println("模拟配置文件不存在的情况...");
            
            // 获取配置实例（这会触发配置加载）
            LLMChatConfig config = LLMChatConfig.getInstance();
            
            // 检查默认值
            int maxContextCharacters = config.getMaxContextCharacters();
            System.out.println("maxContextCharacters: " + maxContextCharacters);
            
            if (maxContextCharacters == 100000) {
                System.out.println("✅ 默认maxContextCharacters值正确");
            } else {
                System.out.println("❌ 默认maxContextCharacters值错误，期望100000，实际" + maxContextCharacters);
            }
            
            // 检查其他关键配置
            double temperature = config.getDefaultTemperature();
            int maxTokens = config.getDefaultMaxTokens();
            
            System.out.println("defaultTemperature: " + temperature);
            System.out.println("defaultMaxTokens: " + maxTokens);
            
            if (temperature == 0.7 && maxTokens == 8192) {
                System.out.println("✅ 其他默认配置值正确");
            } else {
                System.out.println("❌ 其他默认配置值有问题");
            }
            
        } catch (Exception e) {
            System.err.println("❌ 配置文件重新生成测试失败: " + e.getMessage());
        }
    }
    
    private static void testConfigurationValidation() {
        System.out.println("\n--- 测试配置验证逻辑 ---");
        
        try {
            LLMChatConfig config = LLMChatConfig.getInstance();
            
            // 测试设置无效值
            System.out.println("测试设置无效的maxContextCharacters值...");
            
            // 保存原始值
            int originalValue = config.getMaxContextCharacters();
            
            // 设置无效值（这应该被验证逻辑修正）
            config.setMaxContextCharacters(-1);
            
            // 检查是否被修正
            int correctedValue = config.getMaxContextCharacters();
            
            if (correctedValue > 0) {
                System.out.println("✅ 无效值被正确修正为: " + correctedValue);
            } else {
                System.out.println("❌ 无效值未被修正，当前值: " + correctedValue);
            }
            
            // 恢复原始值
            config.setMaxContextCharacters(originalValue);
            
        } catch (Exception e) {
            System.err.println("❌ 配置验证测试失败: " + e.getMessage());
        }
    }
    
    private static void testBackwardCompatibility() {
        System.out.println("\n--- 测试向后兼容性 ---");
        
        try {
            LLMChatConfig config = LLMChatConfig.getInstance();
            
            // 测试向后兼容的方法名
            int valueFromNewMethod = config.getMaxContextCharacters();
            int valueFromOldMethod = config.getMaxContextLength();
            
            System.out.println("getMaxContextCharacters(): " + valueFromNewMethod);
            System.out.println("getMaxContextLength(): " + valueFromOldMethod);
            
            if (valueFromNewMethod == valueFromOldMethod) {
                System.out.println("✅ 向后兼容方法返回相同值");
            } else {
                System.out.println("❌ 向后兼容方法返回不同值");
            }
            
            // 测试设置方法
            int testValue = 50000;
            config.setMaxContextLength(testValue);
            
            int newValue = config.getMaxContextCharacters();
            if (newValue == testValue) {
                System.out.println("✅ setMaxContextLength正确设置了maxContextCharacters");
            } else {
                System.out.println("❌ setMaxContextLength未正确设置值");
            }
            
        } catch (Exception e) {
            System.err.println("❌ 向后兼容性测试失败: " + e.getMessage());
        }
    }
    
    /**
     * 辅助方法：检查配置文件是否存在
     */
    private static boolean configFileExists() {
        try {
            // 这里需要根据实际的配置文件路径进行调整
            Path configPath = Paths.get("config/lllmchat/config.json");
            return Files.exists(configPath);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 辅助方法：删除配置文件（用于测试）
     */
    private static void deleteConfigFile() {
        try {
            Path configPath = Paths.get("config/lllmchat/config.json");
            if (Files.exists(configPath)) {
                Files.delete(configPath);
                System.out.println("已删除配置文件用于测试");
            }
        } catch (Exception e) {
            System.err.println("删除配置文件失败: " + e.getMessage());
        }
    }
}
