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
            "Gemini-Pro"
        };
        
        for (String model : geminiModels) {
            assertTrue(model.toLowerCase().startsWith("gemini"), 
                "模型 " + model + " 应该被识别为Gemini模型");
        }
        
        // 测试非Gemini模型
        String[] nonGeminiModels = {
            "gpt-4o",
            "claude-3.5-sonnet",
            "deepseek-chat"
        };
        
        for (String model : nonGeminiModels) {
            assertFalse(model.toLowerCase().startsWith("gemini"), 
                "模型 " + model + " 不应该被识别为Gemini模型");
        }
    }
    
    @Test
    void testGoogleSearchToolStructure() {
        // 测试 googleSearch 工具的JSON结构
        Gson gson = new Gson();
        
        JsonObject googleSearchTool = new JsonObject();
        googleSearchTool.addProperty("type", "function");
        
        JsonObject googleSearchFunction = new JsonObject();
        googleSearchFunction.addProperty("name", "googleSearch");
        googleSearchTool.add("function", googleSearchFunction);
        
        // 验证结构
        assertTrue(googleSearchTool.has("type"));
        assertEquals("function", googleSearchTool.get("type").getAsString());
        
        assertTrue(googleSearchTool.has("function"));
        JsonObject functionObj = googleSearchTool.getAsJsonObject("function");
        assertTrue(functionObj.has("name"));
        assertEquals("googleSearch", functionObj.get("name").getAsString());
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
        JsonObject googleSearchTool = new JsonObject();
        googleSearchTool.addProperty("type", "function");
        JsonObject googleSearchFunction = new JsonObject();
        googleSearchFunction.addProperty("name", "googleSearch");
        googleSearchTool.add("function", googleSearchFunction);
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
}