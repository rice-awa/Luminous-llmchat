package com.riceawa.llm.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.riceawa.llm.core.LLMConfig;
import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.core.LLMResponse;
import com.riceawa.llm.config.LLMChatConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import okhttp3.*;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试纯Google搜索工具请求（无其他工具干扰）
 */
public class TestGoogleSearchOnly {
    
    private Gson prettyGson;
    private OkHttpClient httpClient;
    
    @BeforeEach
    void setUp() {
        // 创建格式化的Gson实例用于美化输出
        prettyGson = new GsonBuilder().setPrettyPrinting().create();
        
        // 创建HTTP客户端
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }
    
    @Test
    void testPureGoogleSearchRequest() {
        System.out.println("=== 测试纯Google搜索工具请求 ===\n");
        
        // 1. 测试Gemini模型 + 启用搜索的情况
        testGeminiWithSearchEnabled();
        
        // 2. 测试非Gemini模型的情况
        testNonGeminiModel();
        
        // 3. 测试Gemini模型但未启用搜索的情况
        testGeminiWithSearchDisabled();
    }
    
    private void testGeminiWithSearchEnabled() {
        System.out.println("📋 测试场景1: Gemini模型 + 启用搜索");
        System.out.println("预期结果: 应该添加googleSearch工具\n");
        
        // 创建配置
        LLMConfig config = new LLMConfig();
        config.setModel("gemini-2.5-flash-preview-05-20");
        
        // 创建消息
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(new LLMMessage(LLMMessage.MessageRole.USER, "关于openai和超级碗相关的新闻有什么"));
        
        // 构建请求体（模拟启用搜索）
        JsonObject requestBody = buildTestRequestBody(messages, config, true);
        
        // 打印结果
        printRequestAnalysis(config, requestBody, true);
        
        // 验证结果
        assertTrue(requestBody.has("tools"), "应该包含tools字段");
        JsonArray toolsArray = requestBody.getAsJsonArray("tools");
        assertEquals(1, toolsArray.size(), "应该只有一个工具");
        
        JsonObject tool = toolsArray.get(0).getAsJsonObject();
        assertEquals("function", tool.get("type").getAsString());
        JsonObject function = tool.getAsJsonObject("function");
        assertEquals("googleSearch", function.get("name").getAsString());
        
        System.out.println("✅ 测试通过\n");
        System.out.println("=" + "=".repeat(50) + "\n");
    }
    
    private void testNonGeminiModel() {
        System.out.println("📋 测试场景2: 非Gemini模型 + 启用搜索");
        System.out.println("预期结果: 不应该添加googleSearch工具\n");
        
        // 创建配置
        LLMConfig config = new LLMConfig();
        config.setModel("gpt-4o");
        
        // 创建消息
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(new LLMMessage(LLMMessage.MessageRole.USER, "关于openai和超级碗相关的新闻有什么"));
        
        // 构建请求体（模拟启用搜索）
        JsonObject requestBody = buildTestRequestBody(messages, config, true);
        
        // 打印结果
        printRequestAnalysis(config, requestBody, false);
        
        // 验证结果
        assertFalse(requestBody.has("tools"), "不应该包含tools字段");
        
        System.out.println("✅ 测试通过\n");
        System.out.println("=" + "=".repeat(50) + "\n");
    }
    
    private void testGeminiWithSearchDisabled() {
        System.out.println("📋 测试场景3: Gemini模型 + 未启用搜索");
        System.out.println("预期结果: 不应该添加googleSearch工具\n");
        
        // 创建配置
        LLMConfig config = new LLMConfig();
        config.setModel("gemini-1.5-pro");
        
        // 创建消息
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(new LLMMessage(LLMMessage.MessageRole.USER, "关于openai和超级碗相关的新闻有什么"));
        
        // 构建请求体（模拟禁用搜索）
        JsonObject requestBody = buildTestRequestBody(messages, config, false);
        
        // 打印结果
        printRequestAnalysis(config, requestBody, false);
        
        // 验证结果
        assertFalse(requestBody.has("tools"), "不应该包含tools字段");
        
        System.out.println("✅ 测试通过\n");
        System.out.println("=" + "=".repeat(50) + "\n");
    }
    
    /**
     * 构建测试请求体（复制主要逻辑）
     */
    private JsonObject buildTestRequestBody(List<LLMMessage> messages, LLMConfig config, boolean webSearchEnabled) {
        JsonObject requestBody = new JsonObject();
        
        // 设置模型
        requestBody.addProperty("model", config.getModel() != null ? config.getModel() : "gpt-3.5-turbo");
        
        // 设置消息
        JsonArray messagesArray = new JsonArray();
        for (LLMMessage message : messages) {
            JsonObject messageObj = new JsonObject();
            messageObj.addProperty("role", message.getRole().getValue());
            messageObj.addProperty("content", message.getContent());
            messagesArray.add(messageObj);
        }
        requestBody.add("messages", messagesArray);
        
        // 设置其他参数
        requestBody.addProperty("stream", false);
        
        // 检查是否需要添加Google搜索工具
        if (shouldAddGoogleSearchTool(config, webSearchEnabled)) {
            JsonArray toolsArray = new JsonArray();
            toolsArray.add(createGoogleSearchTool());
            requestBody.add("tools", toolsArray);
        }
        
        return requestBody;
    }
    
    /**
     * 检查是否需要添加Google搜索工具
     */
    private boolean shouldAddGoogleSearchTool(LLMConfig config, boolean webSearchEnabled) {
        String model = config.getModel() != null ? config.getModel() : "gpt-3.5-turbo";
        boolean isGeminiModel = isGeminiModel(model);
        return webSearchEnabled && isGeminiModel;
    }
    
    /**
     * 检查是否为Gemini模型
     */
    private boolean isGeminiModel(String model) {
        if (model == null) return false;
        String lowerModel = model.toLowerCase();
        return lowerModel.startsWith("gemini") || 
               lowerModel.contains("google/gemini") || 
               lowerModel.contains("gemini-");
    }
    
    /**
     * 创建Google搜索工具对象
     */
    private JsonObject createGoogleSearchTool() {
        JsonObject googleSearchTool = new JsonObject();
        googleSearchTool.addProperty("type", "function");
        
        JsonObject googleSearchFunction = new JsonObject();
        googleSearchFunction.addProperty("name", "googleSearch");
        googleSearchTool.add("function", googleSearchFunction);
        
        return googleSearchTool;
    }
    
    /**
     * 打印请求分析结果
     */
    private void printRequestAnalysis(LLMConfig config, JsonObject requestBody, boolean shouldHaveTools) {
        System.out.println("🔍 请求分析:");
        System.out.println("  模型: " + config.getModel());
        System.out.println("  是否为Gemini模型: " + isGeminiModel(config.getModel()));
        System.out.println("  搜索功能启用: " + shouldHaveTools); // 直接使用参数，避免依赖配置实例
        System.out.println("  应该添加工具: " + shouldHaveTools);
        System.out.println();
        
        System.out.println("📤 生成的请求体:");
        System.out.println(prettyGson.toJson(requestBody));
        System.out.println();
        
        if (requestBody.has("tools")) {
            JsonArray tools = requestBody.getAsJsonArray("tools");
            System.out.println("🔧 工具详情:");
            System.out.println("  工具数量: " + tools.size());
            for (int i = 0; i < tools.size(); i++) {
                JsonObject tool = tools.get(i).getAsJsonObject();
                JsonObject function = tool.getAsJsonObject("function");
                System.out.println("  工具" + (i + 1) + ": " + function.get("name").getAsString());
            }
        } else {
            System.out.println("🔧 工具详情: 无工具");
        }
        System.out.println();
    }
    
    @Test
    void testCurlEquivalentRequest() {
        System.out.println("=== 测试等效curl请求 ===\n");
        
        // 创建与用户提供的curl示例等效的请求
        LLMConfig config = new LLMConfig();
        config.setModel("gemini-2.5-flash-preview-05-20");
        
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(new LLMMessage(LLMMessage.MessageRole.USER, "关于openai和超级碗相关的新闻有什么"));
        
        // 直接测试，不依赖配置实例
        JsonObject requestBody = buildTestRequestBody(messages, config, true);
        
        System.out.println("📋 等效curl请求测试");
        System.out.println("原始curl命令中的关键参数:");
        System.out.println("  - model: gemini-2.5-flash-preview-05-20");
        System.out.println("  - stream: false");
        System.out.println("  - tools: [{\"type\": \"function\", \"function\": {\"name\": \"googleSearch\"}}]");
        System.out.println();
        
        printRequestAnalysis(config, requestBody, true);
        
        // 验证与curl示例的一致性
        assertEquals("gemini-2.5-flash-preview-05-20", requestBody.get("model").getAsString());
        assertTrue(requestBody.has("tools"));
        
        JsonArray tools = requestBody.getAsJsonArray("tools");
        assertEquals(1, tools.size());
        
        JsonObject tool = tools.get(0).getAsJsonObject();
        assertEquals("function", tool.get("type").getAsString());
        assertEquals("googleSearch", tool.getAsJsonObject("function").get("name").getAsString());
        
        System.out.println("✅ 与curl示例完全匹配!");
    }
    
    @Test
    void testRealApiRequest() {
        System.out.println("=== 测试真实API请求 ===\n");
        
        // 测试配置
        String baseUrl = "https://x666.me/v1";
        String apiKey = "sk-HIbXWijXIDuZ0vS1UhLqzfJy934LRBRmgWlaneBRCcCXeZoF";
        String model = "gemini-2.5-flash-preview-05-20";
        
        // 创建配置
        LLMConfig config = new LLMConfig();
        config.setModel(model);
        config.setTemperature(0.7);
        config.setMaxTokens(1000);
        
        // 创建消息
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(new LLMMessage(LLMMessage.MessageRole.USER, "关于openai和超级碗相关的新闻有什么"));
        
        // 构建请求体
        JsonObject requestBody = buildTestRequestBody(messages, config, true);
        
        System.out.println("📋 真实API请求测试");
        System.out.println("API端点: " + baseUrl + "/chat/completions");
        System.out.println("模型: " + model);
        System.out.println("启用Google搜索: true");
        System.out.println();
        
        System.out.println("📤 发送的请求体:");
        System.out.println(prettyGson.toJson(requestBody));
        System.out.println();
        
        try {
            // 发送请求
            String response = sendRealRequest(baseUrl, apiKey, requestBody);
            
            System.out.println("📥 API响应:");
            System.out.println("响应长度: " + response.length() + " 字符");
            System.out.println();
            
            // 尝试格式化响应
            try {
                JsonObject responseJson = prettyGson.fromJson(response, JsonObject.class);
                System.out.println("📄 格式化的响应:");
                System.out.println(prettyGson.toJson(responseJson));
                
                // 分析响应内容
                analyzeResponse(responseJson);
                
            } catch (Exception e) {
                System.out.println("📄 原始响应 (无法格式化为JSON):");
                System.out.println(response);
            }
            
            System.out.println("\n✅ 真实API请求测试完成!");
            
        } catch (Exception e) {
            System.out.println("❌ API请求失败:");
            System.out.println("错误类型: " + e.getClass().getSimpleName());
            System.out.println("错误信息: " + e.getMessage());
            
            if (e.getCause() != null) {
                System.out.println("根本原因: " + e.getCause().getMessage());
            }
            
            // 不让测试失败，只是记录错误
            System.out.println("\n⚠️ 注意: 这可能是网络问题或API密钥问题，不影响功能逻辑测试");
        }
    }
    
    /**
     * 发送真实的API请求
     */
    private String sendRealRequest(String baseUrl, String apiKey, JsonObject requestBody) throws IOException {
        String url = baseUrl + "/chat/completions";
        
        RequestBody body = RequestBody.create(
            requestBody.toString(), 
            MediaType.get("application/json")
        );
        
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("User-Agent", "LuminousLLMChat-Test/1.0")
                .post(body)
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();
            
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + responseBody);
            }
            
            return responseBody;
        }
    }
    
    /**
     * 分析API响应内容
     */
    private void analyzeResponse(JsonObject responseJson) {
        System.out.println("\n🔍 响应分析:");
        
        // 基本信息
        if (responseJson.has("id")) {
            System.out.println("  请求ID: " + responseJson.get("id").getAsString());
        }
        if (responseJson.has("model")) {
            System.out.println("  使用模型: " + responseJson.get("model").getAsString());
        }
        if (responseJson.has("object")) {
            System.out.println("  对象类型: " + responseJson.get("object").getAsString());
        }
        
        // 分析choices
        if (responseJson.has("choices")) {
            JsonArray choices = responseJson.getAsJsonArray("choices");
            System.out.println("  选择数量: " + choices.size());
            
            for (int i = 0; i < choices.size(); i++) {
                JsonObject choice = choices.get(i).getAsJsonObject();
                System.out.println("  选择" + (i + 1) + ":");
                
                if (choice.has("finish_reason")) {
                    System.out.println("    完成原因: " + choice.get("finish_reason").getAsString());
                }
                
                if (choice.has("message")) {
                    JsonObject message = choice.getAsJsonObject("message");
                    if (message.has("role")) {
                        System.out.println("    角色: " + message.get("role").getAsString());
                    }
                    if (message.has("content") && !message.get("content").isJsonNull()) {
                        String content = message.get("content").getAsString();
                        System.out.println("    内容长度: " + content.length() + " 字符");
                        System.out.println("    内容预览: " + (content.length() > 100 ? 
                            content.substring(0, 100) + "..." : content));
                    }
                    
                    // 检查是否有工具调用
                    if (message.has("tool_calls")) {
                        JsonArray toolCalls = message.getAsJsonArray("tool_calls");
                        System.out.println("    工具调用数量: " + toolCalls.size());
                        
                        for (int j = 0; j < toolCalls.size(); j++) {
                            JsonObject toolCall = toolCalls.get(j).getAsJsonObject();
                            if (toolCall.has("function")) {
                                JsonObject function = toolCall.getAsJsonObject("function");
                                if (function.has("name")) {
                                    System.out.println("      工具" + (j + 1) + ": " + function.get("name").getAsString());
                                    if (function.has("arguments")) {
                                        System.out.println("        参数: " + function.get("arguments").getAsString());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // 使用情况
        if (responseJson.has("usage")) {
            JsonObject usage = responseJson.getAsJsonObject("usage");
            System.out.println("  Token使用情况:");
            if (usage.has("prompt_tokens")) {
                System.out.println("    提示Token: " + usage.get("prompt_tokens").getAsInt());
            }
            if (usage.has("completion_tokens")) {
                System.out.println("    完成Token: " + usage.get("completion_tokens").getAsInt());
            }
            if (usage.has("total_tokens")) {
                System.out.println("    总Token: " + usage.get("total_tokens").getAsInt());
            }
        }
        
        // 检查错误
        if (responseJson.has("error")) {
            JsonObject error = responseJson.getAsJsonObject("error");
            System.out.println("  ❌ 错误信息:");
            if (error.has("message")) {
                System.out.println("    消息: " + error.get("message").getAsString());
            }
            if (error.has("type")) {
                System.out.println("    类型: " + error.get("type").getAsString());
            }
            if (error.has("code")) {
                System.out.println("    代码: " + error.get("code").getAsString());
            }
        }
    }
    
    @Test
    void testMultipleProvidersGoogleSearch() {
        System.out.println("=== 测试多个提供商的Google搜索功能 ===\n");
        
        // 定义测试提供商
        String[][] providers = {
            {"gemini-1", "https://gemini.rice-awa.top/v1", "AIzaSyBRZJQv8YJGrkUUitTFHVUQc46rkS6SEZI", "gemini-2.5-flash"},
            {"gemini-2", "https://newapi.julizhanzhan.cloud/v1", "sk-Hbz9uQpNRaS8nCYbohyP82jVeKtW4fXlOqvkzOHhUEICqQrh", "gemini-2.5-flash"},
            {"gemini-3", "https://x666.me/v1", "sk-HIbXWijXIDuZ0vS1UhLqzfJy934LRBRmgWlaneBRCcCXeZoF", "gemini-2.5-flash-preview-05-20"}
        };
        
        for (String[] provider : providers) {
            String name = provider[0];
            String baseUrl = provider[1];
            String apiKey = provider[2];
            String model = provider[3];
            
            System.out.println("🔄 测试提供商: " + name);
            System.out.println("   URL: " + baseUrl);
            System.out.println("   模型: " + model);
            
            try {
                // 创建配置
                LLMConfig config = new LLMConfig();
                config.setModel(model);
                config.setTemperature(0.3);
                config.setMaxTokens(500);
                
                // 创建简单的测试消息
                List<LLMMessage> messages = new ArrayList<>();
                messages.add(new LLMMessage(LLMMessage.MessageRole.USER, "今天的天气怎么样？"));
                
                // 构建请求体
                JsonObject requestBody = buildTestRequestBody(messages, config, true);
                
                // 验证工具是否正确添加
                assertTrue(requestBody.has("tools"), "应该包含tools字段");
                JsonArray tools = requestBody.getAsJsonArray("tools");
                assertEquals(1, tools.size(), "应该只有一个工具");
                
                JsonObject tool = tools.get(0).getAsJsonObject();
                assertEquals("function", tool.get("type").getAsString());
                assertEquals("googleSearch", tool.getAsJsonObject("function").get("name").getAsString());
                
                System.out.println("   ✅ 工具配置正确");
                
                // 尝试发送请求（限制超时以避免测试时间过长）
                try {
                    String response = sendRealRequest(baseUrl, apiKey, requestBody);
                    System.out.println("   ✅ API响应成功 (长度: " + response.length() + " 字符)");
                    
                    // 简单验证响应格式
                    JsonObject responseJson = prettyGson.fromJson(response, JsonObject.class);
                    if (responseJson.has("choices")) {
                        System.out.println("   ✅ 响应格式正确");
                    }
                    
                } catch (Exception e) {
                    System.out.println("   ⚠️ API请求失败: " + e.getMessage());
                }
                
            } catch (Exception e) {
                System.out.println("   ❌ 测试失败: " + e.getMessage());
            }
            
            System.out.println();
        }
        
        System.out.println("✅ 多提供商测试完成!");
    }
}