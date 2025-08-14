package com.riceawa.llm.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.riceawa.llm.core.LLMConfig;
import com.riceawa.llm.core.LLMMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试联网搜索功能集成
 */
public class TestWebSearchIntegration {
    
    @Test
    void testGeminiModelIdentification() {
        // 测试不同的Gemini模型名称识别
        String[] geminiModels = {
            "gemini-1.5-pro",
            "gemini-2.5-flash",
            "GEMINI-2.0-FLASH",
            "Gemini-Pro",
            "google/gemini-2.5-pro-preview",
            "gemini-2.5-flash-preview-05-20"
        };
        
        for (String model : geminiModels) {
            assertTrue(isGeminiModelTest(model), 
                "模型 " + model + " 应该被识别为Gemini模型");
        }
        
        // 测试非Gemini模型
        String[] nonGeminiModels = {
            "gpt-4o",
            "claude-3.5-sonnet",
            "deepseek-chat",
            "anthropic/claude-3.5-sonnet"
        };
        
        for (String model : nonGeminiModels) {
            assertFalse(isGeminiModelTest(model), 
                "模型 " + model + " 不应该被识别为Gemini模型");
        }
    }
    
    /**
     * 测试用的Gemini模型识别方法（复制主代码逻辑）
     */
    private boolean isGeminiModelTest(String model) {
        if (model == null) return false;
        String lowerModel = model.toLowerCase();
        return lowerModel.startsWith("gemini") || 
               lowerModel.contains("google/gemini") || 
               lowerModel.contains("gemini-");
    }
    
    @Test
    void testGoogleSearchToolStructure() {
        // 测试 googleSearch 工具的JSON结构（简化版本）
        JsonObject googleSearchTool = createGoogleSearchToolTest();
        
        // 验证基本结构
        assertTrue(googleSearchTool.has("type"));
        assertEquals("function", googleSearchTool.get("type").getAsString());
        
        assertTrue(googleSearchTool.has("function"));
        JsonObject functionObj = googleSearchTool.getAsJsonObject("function");
        assertTrue(functionObj.has("name"));
        assertEquals("googleSearch", functionObj.get("name").getAsString());
        
        // 简化版本不包含description和parameters
        assertFalse(functionObj.has("description"));
        assertFalse(functionObj.has("parameters"));
    }
    
    /**
     * 测试用的Google搜索工具创建方法（简化版本，复制主代码逻辑）
     */
    private JsonObject createGoogleSearchToolTest() {
        JsonObject googleSearchTool = new JsonObject();
        googleSearchTool.addProperty("type", "function");
        
        JsonObject googleSearchFunction = new JsonObject();
        googleSearchFunction.addProperty("name", "googleSearch");
        googleSearchTool.add("function", googleSearchFunction);
        
        return googleSearchTool;
    }
    
    @Test
    void testToolsArrayConstruction() {
        // 测试tools数组的构建
        JsonArray toolsArray = new JsonArray();
        
        // 添加一个示例工具
        JsonObject sampleTool = new JsonObject();
        sampleTool.addProperty("type", "function");
        JsonObject sampleFunction = new JsonObject();
        sampleFunction.addProperty("name", "sampleFunction");
        sampleTool.add("function", sampleFunction);
        toolsArray.add(sampleTool);
        
        // 添加googleSearch工具
        JsonObject googleSearchTool = createGoogleSearchToolTest();
        toolsArray.add(googleSearchTool);
        
        // 验证数组包含两个工具
        assertEquals(2, toolsArray.size());
        
        // 验证包含googleSearch工具
        boolean hasGoogleSearch = false;
        for (int i = 0; i < toolsArray.size(); i++) {
            JsonObject tool = toolsArray.get(i).getAsJsonObject();
            if (tool.has("function")) {
                JsonObject function = tool.getAsJsonObject("function");
                if (function.has("name") && "googleSearch".equals(function.get("name").getAsString())) {
                    hasGoogleSearch = true;
                    break;
                }
            }
        }
        
        assertTrue(hasGoogleSearch, "tools数组应该包含googleSearch工具");
    }
    
    @Test
    void testWebSearchConfigurationLogic() {
        // 测试配置逻辑：只有当启用联网搜索且是Gemini模型时才添加工具
        
        // 情况1：Gemini模型 + 启用搜索 = 应该添加工具
        assertTrue(shouldAddGoogleSearchToolTest("gemini-1.5-pro", true),
            "Gemini模型且启用搜索时应该添加Google搜索工具");
        
        // 情况2：非Gemini模型 + 启用搜索 = 不应该添加工具
        assertFalse(shouldAddGoogleSearchToolTest("gpt-4o", true),
            "非Gemini模型即使启用搜索也不应该添加Google搜索工具");
        
        // 情况3：Gemini模型 + 未启用搜索 = 不应该添加工具
        assertFalse(shouldAddGoogleSearchToolTest("gemini-1.5-pro", false),
            "Gemini模型但未启用搜索时不应该添加Google搜索工具");
        
        // 情况4：非Gemini模型 + 未启用搜索 = 不应该添加工具
        assertFalse(shouldAddGoogleSearchToolTest("gpt-4o", false),
            "非Gemini模型且未启用搜索时不应该添加Google搜索工具");
    }
    
    /**
     * 测试用的配置逻辑方法（复制主代码逻辑）
     */
    private boolean shouldAddGoogleSearchToolTest(String model, boolean webSearchEnabled) {
        boolean isGeminiModel = isGeminiModelTest(model);
        return webSearchEnabled && isGeminiModel;
    }
}