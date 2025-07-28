package com.riceawa.llm.config;
/**
 * 快速测试配置默认值问题
 */
public class QuickConfigTest {
    public static void main(String[] args) {
        System.out.println("=== 快速配置测试 ===");
        
        // 模拟配置类的字段初始化
        testFieldInitialization();
        
        // 模拟配置数据的处理
        testConfigDataHandling();
        
        System.out.println("\n=== 测试完成 ===");
    }
    
    private static void testFieldInitialization() {
        System.out.println("\n--- 测试字段初始化 ---");
        
        // 模拟LLMChatConfig的字段初始化
        class MockConfig {
            private int maxContextCharacters = 100000; // 字段默认值
            
            public int getMaxContextCharacters() {
                return maxContextCharacters;
            }
            
            public void setMaxContextCharacters(int value) {
                this.maxContextCharacters = value;
            }
        }
        
        MockConfig config = new MockConfig();
        System.out.println("字段初始化后的值: " + config.getMaxContextCharacters());
        
        // 测试设置为1
        config.setMaxContextCharacters(1);
        System.out.println("设置为1后的值: " + config.getMaxContextCharacters());
        
        // 测试设置为0
        config.setMaxContextCharacters(0);
        System.out.println("设置为0后的值: " + config.getMaxContextCharacters());
    }
    
    private static void testConfigDataHandling() {
        System.out.println("\n--- 测试配置数据处理 ---");
        
        // 模拟ConfigData类
        class MockConfigData {
            Integer maxContextCharacters;
            Integer maxContextLength;
        }
        
        // 模拟配置处理逻辑
        class MockConfigProcessor {
            private int maxContextCharacters = 100000;
            
            void processConfigData(MockConfigData data) {
                System.out.println("处理前的值: " + this.maxContextCharacters);
                System.out.println("数据中的maxContextCharacters: " + data.maxContextCharacters);
                System.out.println("数据中的maxContextLength: " + data.maxContextLength);
                
                // 模拟applyConfigData的逻辑
                if (data.maxContextLength != null) {
                    this.maxContextCharacters = data.maxContextLength;
                    System.out.println("使用legacy maxContextLength: " + this.maxContextCharacters);
                } else if (data.maxContextCharacters != null) {
                    this.maxContextCharacters = data.maxContextCharacters;
                    System.out.println("使用maxContextCharacters: " + this.maxContextCharacters);
                } else {
                    this.maxContextCharacters = 100000;
                    System.out.println("使用默认值: " + this.maxContextCharacters);
                }
                
                System.out.println("处理后的值: " + this.maxContextCharacters);
            }
            
            int getValue() {
                return maxContextCharacters;
            }
        }
        
        MockConfigProcessor processor = new MockConfigProcessor();
        
        // 测试场景1：空数据
        System.out.println("\n场景1：空数据");
        MockConfigData emptyData = new MockConfigData();
        processor.processConfigData(emptyData);
        
        // 测试场景2：maxContextCharacters为1
        System.out.println("\n场景2：maxContextCharacters为1");
        MockConfigData data1 = new MockConfigData();
        data1.maxContextCharacters = 1;
        processor.processConfigData(data1);
        
        // 测试场景3：maxContextCharacters为0
        System.out.println("\n场景3：maxContextCharacters为0");
        MockConfigData data0 = new MockConfigData();
        data0.maxContextCharacters = 0;
        processor.processConfigData(data0);
        
        // 测试场景4：legacy数据
        System.out.println("\n场景4：legacy maxContextLength");
        MockConfigData legacyData = new MockConfigData();
        legacyData.maxContextLength = 50000;
        processor.processConfigData(legacyData);
    }
}
