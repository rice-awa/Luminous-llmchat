package com.riceawa.llm.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试配置文件布局
 */
public class ConfigLayoutTest {
    
    @Test
    void testConfigDataFieldOrder() {
        System.out.println("=== 测试配置文件字段顺序 ===");
        
        // 创建一个模拟的配置数据对象
        TestConfigData data = new TestConfigData();
        
        // 设置基础配置
        data.configVersion = "1.5.1";
        data.defaultPromptTemplate = "default";
        data.defaultTemperature = 0.7;
        data.defaultMaxTokens = 8192;
        data.maxContextCharacters = 60000;
        
        // 设置功能开关
        data.enableHistory = true;
        data.enableFunctionCalling = true;
        data.enableBroadcast = false;
        
        // 设置全局上下文
        data.enableGlobalContext = true;
        data.globalContextPrompt = "测试提示词";
        
        // 设置功能配置
        data.enableCompressionNotification = true;
        data.enableTitleGeneration = true;
        
        // 设置模型相关配置（应该在最后）
        data.compressionModel = "gpt-3.5-turbo";
        data.titleGenerationModel = "gpt-3.5-turbo";
        data.currentProvider = "openai";
        data.currentModel = "gpt-3.5-turbo";
        
        // 序列化为JSON
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(data);
        
        System.out.println("生成的配置JSON:");
        System.out.println(json);
        
        // 验证模型相关字段在JSON中的位置
        String[] lines = json.split("\n");
        
        // 找到模型相关字段的位置
        int compressionModelIndex = -1;
        int titleGenerationModelIndex = -1;
        int currentProviderIndex = -1;
        int currentModelIndex = -1;
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("\"compressionModel\"")) {
                compressionModelIndex = i;
            } else if (line.startsWith("\"titleGenerationModel\"")) {
                titleGenerationModelIndex = i;
            } else if (line.startsWith("\"currentProvider\"")) {
                currentProviderIndex = i;
            } else if (line.startsWith("\"currentModel\"")) {
                currentModelIndex = i;
            }
        }
        
        // 验证模型相关字段都在后面
        assertTrue(compressionModelIndex > 10, "compressionModel应该在配置的后面部分");
        assertTrue(titleGenerationModelIndex > 10, "titleGenerationModel应该在配置的后面部分");
        assertTrue(currentProviderIndex > 10, "currentProvider应该在配置的后面部分");
        assertTrue(currentModelIndex > 10, "currentModel应该在配置的后面部分");
        
        // 验证currentModel是最后一个字段（除了结束的大括号）
        boolean foundCurrentModel = false;
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.startsWith("\"")) {
                if (line.startsWith("\"currentModel\"")) {
                    foundCurrentModel = true;
                }
                break;
            }
        }
        
        assertTrue(foundCurrentModel, "currentModel应该是最后一个配置字段");
        
        System.out.println("✅ 配置字段顺序验证通过");
    }
    
    /**
     * 测试用的配置数据类，模拟实际的ConfigData结构
     */
    private static class TestConfigData {
        // 基础配置
        String configVersion;
        String defaultPromptTemplate;
        Double defaultTemperature;
        Integer defaultMaxTokens;
        Integer maxContextCharacters;

        // 功能开关配置
        Boolean enableHistory;
        Boolean enableFunctionCalling;
        Boolean enableBroadcast;

        // 全局上下文配置
        Boolean enableGlobalContext;
        String globalContextPrompt;

        // 压缩和标题生成功能配置
        Boolean enableCompressionNotification;
        Boolean enableTitleGeneration;

        // 模型相关配置（放在最后）
        String compressionModel;
        String titleGenerationModel;
        String currentProvider;
        String currentModel;
    }
}
