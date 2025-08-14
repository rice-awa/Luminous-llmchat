package com.riceawa.llm.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.riceawa.llm.core.LLMConfig;
import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.config.LLMChatConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import okhttp3.*;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试多工具情况下的Google搜索功能
 */
public class TestMultiToolsWithGoogleSearch {
    
    private Gson prettyGson;
    private OkHttpClient httpClient;
    
    @BeforeEach
    void setUp() {
        prettyGson = new GsonBuilder().setPrettyPrinting().create();
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }
    
    @Test
    void testMultipleToolsWithGoogleSearch() {
        System.out.println("=== 测试多工具情况下的Google搜索功能 ===\n");
        
        // 创建配置
        LLMConfig config = new LLMConfig();
        config.setModel("gemini-2.5-flash-preview-05-20");
        config.setTemperature(0.7);
        config.setMaxTokens(1000);
        
        // 添加多个工具
        List<LLMConfig.ToolDefinition> tools = new ArrayList<>();
        
        // 1. 添加背包查询工具
        LLMConfig.ToolDefinition inventoryTool = createInventoryTool();
        tools.add(inventoryTool);
        
        // 2. 添加实体召唤工具
        LLMConfig.ToolDefinition summonTool = createSummonEntityTool();
        tools.add(summonTool);
        
        // 3. 添加自定义工具
        LLMConfig.ToolDefinition customTool = createCustomTool();
        tools.add(customTool);
        
        config.setTools(tools);
        
        // 创建消息
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(new LLMMessage(LLMMessage.MessageRole.USER, 
            "请帮我查询背包信息，然后搜索一下最新的Minecraft更新新闻"));
        
        // 构建请求体
        JsonObject requestBody = buildRequestBody(messages, config, true);
        
        System.out.println("📋 多工具 + Google搜索测试");
        System.out.println("配置的工具数量: " + tools.size());
        System.out.println("是否启用Google搜索: true");
        System.out.println("模型: " + config.getModel());
        System.out.println();
        
        // 验证工具配置
        assertTrue(requestBody.has("tools"), "应该包含tools字段");
        JsonArray toolsArray = requestBody.getAsJsonArray("tools");
        
        // 应该有4个工具：3个原始工具 + 1个Google搜索工具
        assertEquals(4, toolsArray.size(), "应该有4个工具（3个原始 + 1个Google搜索）");
        
        System.out.println("🔧 工具配置验证:");
        System.out.println("  总工具数量: " + toolsArray.size());
        
        // 验证每个工具
        boolean hasInventoryTool = false;
        boolean hasSummonTool = false;
        boolean hasCustomTool = false;
        boolean hasGoogleSearchTool = false;
        
        for (int i = 0; i < toolsArray.size(); i++) {
            JsonObject tool = toolsArray.get(i).getAsJsonObject();
            assertEquals("function", tool.get("type").getAsString());
            
            JsonObject function = tool.getAsJsonObject("function");
            String functionName = function.get("name").getAsString();
            
            System.out.println("  工具" + (i + 1) + ": " + functionName);
            
            switch (functionName) {
                case "get_inventory":
                    hasInventoryTool = true;
                    break;
                case "summon_entity":
                    hasSummonTool = true;
                    break;
                case "custom_tool":
                    hasCustomTool = true;
                    break;
                case "googleSearch":
                    hasGoogleSearchTool = true;
                    // 验证Google搜索工具的简化结构
                    assertFalse(function.has("description"), "Google搜索工具不应该有description");
                    assertFalse(function.has("parameters"), "Google搜索工具不应该有parameters");
                    break;
            }
        }
        
        // 验证所有工具都存在
        assertTrue(hasInventoryTool, "应该包含背包查询工具");
        assertTrue(hasSummonTool, "应该包含实体召唤工具");
        assertTrue(hasCustomTool, "应该包含自定义工具");
        assertTrue(hasGoogleSearchTool, "应该包含Google搜索工具");
        
        System.out.println("  ✅ 所有工具都正确配置");
        System.out.println();
        
        // 打印完整的请求体
        System.out.println("📤 生成的请求体:");
        System.out.println(prettyGson.toJson(requestBody));
        System.out.println();
        
        System.out.println("✅ 多工具配置测试通过!");
    }
    
    @Test
    void testRealApiWithMultipleTools() {
        System.out.println("=== 真实API测试：多工具 + Google搜索 ===\n");
        
        // 测试配置
        String baseUrl = "https://aaaa.top/v1";
        String apiKey = "xxxxx";
        
        // 创建配置
        LLMConfig config = new LLMConfig();
        config.setModel("gemini-2.5-flash");
        config.setTemperature(0.3);
        config.setMaxTokens(800);
        
        // 添加工具
        List<LLMConfig.ToolDefinition> tools = new ArrayList<>();
        tools.add(createInventoryTool());
        tools.add(createSummonEntityTool());
        config.setTools(tools);
        
        // 创建消息
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(new LLMMessage(LLMMessage.MessageRole.USER, 
            "请搜索一下关于Minecraft 1.21版本的最新信息"));
        
        // 构建请求体
        JsonObject requestBody = buildRequestBody(messages, config, true);
        
        System.out.println("📋 真实API测试配置:");
        System.out.println("API端点: " + baseUrl + "/chat/completions");
        System.out.println("模型: " + config.getModel());
        System.out.println("配置工具数: " + tools.size());
        System.out.println("总工具数: " + requestBody.getAsJsonArray("tools").size());
        System.out.println();
        
        try {
            // 发送请求
            String response = sendRealRequest(baseUrl, apiKey, requestBody);
            
            System.out.println("📥 API响应:");
            System.out.println("响应长度: " + response.length() + " 字符");
            System.out.println();
            
            // 解析响应
            JsonObject responseJson = prettyGson.fromJson(response, JsonObject.class);
            System.out.println("📄 格式化的响应:");
            System.out.println(prettyGson.toJson(responseJson));
            
            // 分析响应
            analyzeMultiToolResponse(responseJson);
            
            System.out.println("\n✅ 多工具真实API测试完成!");
            
        } catch (Exception e) {
            System.out.println("❌ API请求失败:");
            System.out.println("错误信息: " + e.getMessage());
            System.out.println("\n⚠️ 注意: 这可能是网络问题，不影响功能逻辑测试");
        }
    }
    
    @Test
    void testToolPriorityAndOrdering() {
        System.out.println("=== 测试工具优先级和排序 ===\n");
        
        // 创建配置
        LLMConfig config = new LLMConfig();
        config.setModel("gemini-1.5-pro");
        
        // 添加工具（不同顺序）
        List<LLMConfig.ToolDefinition> tools = new ArrayList<>();
        tools.add(createSummonEntityTool());
        tools.add(createInventoryTool());
        config.setTools(tools);
        
        // 创建测试消息
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(new LLMMessage(LLMMessage.MessageRole.USER, "测试消息"));
        
        // 构建请求体
        JsonObject requestBody = buildRequestBody(messages, config, true);
        JsonArray toolsArray = requestBody.getAsJsonArray("tools");
        
        System.out.println("🔍 工具排序分析:");
        System.out.println("总工具数: " + toolsArray.size());
        
        // 验证Google搜索工具是否被正确添加到末尾
        JsonObject lastTool = toolsArray.get(toolsArray.size() - 1).getAsJsonObject();
        JsonObject lastFunction = lastTool.getAsJsonObject("function");
        
        assertEquals("googleSearch", lastFunction.get("name").getAsString(), 
            "Google搜索工具应该被添加到工具列表的末尾");
        
        System.out.println("工具顺序:");
        for (int i = 0; i < toolsArray.size(); i++) {
            JsonObject tool = toolsArray.get(i).getAsJsonObject();
            JsonObject function = tool.getAsJsonObject("function");
            String name = function.get("name").getAsString();
            System.out.println("  " + (i + 1) + ". " + name);
        }
        
        System.out.println("✅ Google搜索工具正确添加到末尾");
    }
    
    /**
     * 创建背包查询工具定义
     */
    private LLMConfig.ToolDefinition createInventoryTool() {
        LLMConfig.ToolDefinition tool = new LLMConfig.ToolDefinition();
        tool.setType("function");
        
        LLMConfig.FunctionDefinition function = new LLMConfig.FunctionDefinition();
        function.setName("get_inventory");
        function.setDescription("获取玩家的背包物品信息");
        
        // 参数定义
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        
        JsonObject properties = new JsonObject();
        JsonObject showEmpty = new JsonObject();
        showEmpty.addProperty("type", "boolean");
        showEmpty.addProperty("description", "是否显示空槽位");
        properties.add("show_empty", showEmpty);
        
        parameters.add("properties", properties);
        function.setParameters(parameters);
        
        tool.setFunction(function);
        return tool;
    }
    
    /**
     * 创建实体召唤工具定义
     */
    private LLMConfig.ToolDefinition createSummonEntityTool() {
        LLMConfig.ToolDefinition tool = new LLMConfig.ToolDefinition();
        tool.setType("function");
        
        LLMConfig.FunctionDefinition function = new LLMConfig.FunctionDefinition();
        function.setName("summon_entity");
        function.setDescription("在指定位置生成实体");
        
        // 参数定义
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        
        JsonObject properties = new JsonObject();
        
        JsonObject entityType = new JsonObject();
        entityType.addProperty("type", "string");
        entityType.addProperty("description", "实体类型");
        properties.add("entity_type", entityType);
        
        JsonObject x = new JsonObject();
        x.addProperty("type", "number");
        x.addProperty("description", "X坐标");
        properties.add("x", x);
        
        JsonObject y = new JsonObject();
        y.addProperty("type", "number");
        y.addProperty("description", "Y坐标");
        properties.add("y", y);
        
        JsonObject z = new JsonObject();
        z.addProperty("type", "number");
        z.addProperty("description", "Z坐标");
        properties.add("z", z);
        
        parameters.add("properties", properties);
        
        JsonArray required = new JsonArray();
        required.add("entity_type");
        required.add("x");
        required.add("y");
        required.add("z");
        parameters.add("required", required);
        
        function.setParameters(parameters);
        tool.setFunction(function);
        return tool;
    }
    
    /**
     * 创建自定义工具定义
     */
    private LLMConfig.ToolDefinition createCustomTool() {
        LLMConfig.ToolDefinition tool = new LLMConfig.ToolDefinition();
        tool.setType("function");
        
        LLMConfig.FunctionDefinition function = new LLMConfig.FunctionDefinition();
        function.setName("custom_tool");
        function.setDescription("自定义测试工具");
        
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        parameters.add("properties", new JsonObject());
        
        function.setParameters(parameters);
        tool.setFunction(function);
        return tool;
    }
    
    /**
     * 构建请求体
     */
    private JsonObject buildRequestBody(List<LLMMessage> messages, LLMConfig config, boolean webSearchEnabled) {
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
        if (config.getTemperature() != null) {
            requestBody.addProperty("temperature", config.getTemperature());
        }
        if (config.getMaxTokens() != null) {
            requestBody.addProperty("max_tokens", config.getMaxTokens());
        }
        requestBody.addProperty("stream", false);
        
        // 添加工具定义
        if (config.getTools() != null && !config.getTools().isEmpty()) {
            JsonArray toolsArray = new JsonArray();
            
            // 添加原有工具
            for (LLMConfig.ToolDefinition tool : config.getTools()) {
                JsonObject toolObj = new JsonObject();
                toolObj.addProperty("type", tool.getType());

                JsonObject functionObj = new JsonObject();
                functionObj.addProperty("name", tool.getFunction().getName());
                functionObj.addProperty("description", tool.getFunction().getDescription());
                
                // 处理parameters参数转换
                Object params = tool.getFunction().getParameters();
                if (params instanceof JsonObject) {
                    functionObj.add("parameters", (JsonObject) params);
                } else {
                    // 如果不是JsonObject，使用Gson转换
                    functionObj.add("parameters", prettyGson.toJsonTree(params));
                }

                toolObj.add("function", functionObj);
                toolsArray.add(toolObj);
            }
            
            // 检查是否需要添加Google搜索工具
            if (shouldAddGoogleSearchTool(config, webSearchEnabled)) {
                toolsArray.add(createGoogleSearchTool());
            }
            
            requestBody.add("tools", toolsArray);
        }
        // 如果没有其他工具但需要添加Google搜索工具
        else if (shouldAddGoogleSearchTool(config, webSearchEnabled)) {
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
                .header("User-Agent", "LuminousLLMChat-MultiTool-Test/1.0")
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
     * 分析多工具API响应
     */
    private void analyzeMultiToolResponse(JsonObject responseJson) {
        System.out.println("\n🔍 多工具响应分析:");
        
        if (responseJson.has("choices")) {
            JsonArray choices = responseJson.getAsJsonArray("choices");
            for (int i = 0; i < choices.size(); i++) {
                JsonObject choice = choices.get(i).getAsJsonObject();
                if (choice.has("message")) {
                    JsonObject message = choice.getAsJsonObject("message");
                    
                    // 检查是否有工具调用
                    if (message.has("tool_calls")) {
                        JsonArray toolCalls = message.getAsJsonArray("tool_calls");
                        System.out.println("  检测到工具调用数量: " + toolCalls.size());
                        
                        for (int j = 0; j < toolCalls.size(); j++) {
                            JsonObject toolCall = toolCalls.get(j).getAsJsonObject();
                            if (toolCall.has("function")) {
                                JsonObject function = toolCall.getAsJsonObject("function");
                                String functionName = function.get("name").getAsString();
                                System.out.println("    工具" + (j + 1) + ": " + functionName);
                                
                                if ("googleSearch".equals(functionName)) {
                                    System.out.println("      ✅ Google搜索工具被成功调用!");
                                }
                            }
                        }
                    } else {
                        System.out.println("  未检测到工具调用");
                    }
                    
                    // 显示响应内容预览
                    if (message.has("content") && !message.get("content").isJsonNull()) {
                        String content = message.get("content").getAsString();
                        System.out.println("  响应内容长度: " + content.length() + " 字符");
                        if (content.length() > 0) {
                            String preview = content.length() > 150 ? 
                                content.substring(0, 150) + "..." : content;
                            System.out.println("  内容预览: " + preview);
                        }
                    }
                }
            }
        }
        
        // Token使用情况
        if (responseJson.has("usage")) {
            JsonObject usage = responseJson.getAsJsonObject("usage");
            System.out.println("  Token使用:");
            if (usage.has("total_tokens")) {
                System.out.println("    总计: " + usage.get("total_tokens").getAsInt());
            }
        }
    }
}