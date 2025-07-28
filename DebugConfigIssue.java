/**
 * 调试配置问题的简单测试
 */
public class DebugConfigIssue {
    public static void main(String[] args) {
        System.out.println("=== 调试配置默认值问题 ===");
        
        // 模拟配置类的行为
        testConfigDataSerialization();
        testDefaultValueAssignment();
    }
    
    private static void testConfigDataSerialization() {
        System.out.println("\n--- 测试ConfigData序列化 ---");
        
        // 模拟ConfigData类
        class TestConfigData {
            Integer maxContextCharacters;
            Integer maxContextLength;
            
            @Override
            public String toString() {
                return "TestConfigData{" +
                    "maxContextCharacters=" + maxContextCharacters +
                    ", maxContextLength=" + maxContextLength +
                    '}';
            }
        }
        
        // 测试1：只设置maxContextCharacters
        TestConfigData data1 = new TestConfigData();
        data1.maxContextCharacters = 100000;
        System.out.println("设置maxContextCharacters=100000: " + data1);
        
        // 测试2：两个都不设置
        TestConfigData data2 = new TestConfigData();
        System.out.println("都不设置: " + data2);
        
        // 测试3：设置为0
        TestConfigData data3 = new TestConfigData();
        data3.maxContextCharacters = 0;
        System.out.println("设置maxContextCharacters=0: " + data3);
        
        // 测试4：设置为1
        TestConfigData data4 = new TestConfigData();
        data4.maxContextCharacters = 1;
        System.out.println("设置maxContextCharacters=1: " + data4);
    }
    
    private static void testDefaultValueAssignment() {
        System.out.println("\n--- 测试默认值分配逻辑 ---");
        
        // 模拟applyConfigData的逻辑
        class TestConfig {
            int maxContextCharacters = 100000; // 字段默认值
            
            void applyConfigData(Integer dataMaxContextCharacters, Integer dataMaxContextLength) {
                // 模拟原始逻辑
                if (dataMaxContextLength != null) {
                    this.maxContextCharacters = dataMaxContextLength;
                    System.out.println("使用legacy maxContextLength: " + this.maxContextCharacters);
                } else if (dataMaxContextCharacters != null) {
                    this.maxContextCharacters = dataMaxContextCharacters;
                    System.out.println("使用maxContextCharacters: " + this.maxContextCharacters);
                } else {
                    this.maxContextCharacters = 100000;
                    System.out.println("使用默认值: " + this.maxContextCharacters);
                }
            }
            
            @Override
            public String toString() {
                return "TestConfig{maxContextCharacters=" + maxContextCharacters + '}';
            }
        }
        
        // 测试场景1：数据为null
        TestConfig config1 = new TestConfig();
        System.out.println("初始状态: " + config1);
        config1.applyConfigData(null, null);
        System.out.println("应用null数据后: " + config1);
        
        // 测试场景2：数据为0
        TestConfig config2 = new TestConfig();
        config2.applyConfigData(0, null);
        System.out.println("应用0数据后: " + config2);
        
        // 测试场景3：数据为1
        TestConfig config3 = new TestConfig();
        config3.applyConfigData(1, null);
        System.out.println("应用1数据后: " + config3);
        
        // 测试场景4：legacy数据
        TestConfig config4 = new TestConfig();
        config4.applyConfigData(null, 50000);
        System.out.println("应用legacy数据后: " + config4);
    }
}
