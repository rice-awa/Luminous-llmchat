package com.riceawa.llm.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.riceawa.llm.core.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class OpenAIServiceTest {

    private MockWebServer mockWebServer;
    private OpenAIService openAIService;
    private final Gson gson = new Gson();

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        String baseUrl = mockWebServer.url("/").toString().replaceAll("/$", "");
        openAIService = new OpenAIService("test-api-key", baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void testBasicChatRequest() throws Exception {
        // 模拟OpenAI API响应
        String mockResponse = """
            {
                "id": "chatcmpl-123",
                "model": "gpt-4o",
                "choices": [
                    {
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": "Hello! How can I help you today?"
                        },
                        "finish_reason": "stop"
                    }
                ],
                "usage": {
                    "prompt_tokens": 10,
                    "completion_tokens": 15,
                    "total_tokens": 25
                }
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setBody(mockResponse)
            .addHeader("Content-Type", "application/json"));
        
        // 创建请求
        List<LLMMessage> messages = Arrays.asList(
            new LLMMessage(LLMMessage.MessageRole.USER, "Hello")
        );
        
        LLMConfig config = new LLMConfig();
        config.setModel("gpt-4o");
        config.setTemperature(0.7);
        
        // 发送请求
        CompletableFuture<LLMResponse> future = openAIService.chat(messages, config);
        LLMResponse response = future.get(5, TimeUnit.SECONDS);
        
        // 验证响应
        assertTrue(response.isSuccess());
        assertEquals("Hello! How can I help you today?", response.getContent());
        assertEquals("chatcmpl-123", response.getId());
        assertEquals("gpt-4o", response.getModel());
        
        // 验证usage
        assertNotNull(response.getUsage());
        assertEquals(10, response.getUsage().getPromptTokens());
        assertEquals(15, response.getUsage().getCompletionTokens());
        assertEquals(25, response.getUsage().getTotalTokens());
        
        // 验证请求
        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/chat/completions", request.getPath());
        assertEquals("Bearer test-api-key", request.getHeader("Authorization"));
        assertEquals("application/json", request.getHeader("Content-Type"));
        
        // 验证请求体
        String requestBody = request.getBody().readUtf8();
        JsonObject requestJson = gson.fromJson(requestBody, JsonObject.class);
        assertEquals("gpt-4o", requestJson.get("model").getAsString());
        assertEquals(0.7, requestJson.get("temperature").getAsDouble());
        assertTrue(requestJson.has("messages"));
        assertEquals(1, requestJson.getAsJsonArray("messages").size());
    }

    @Test
    void testFunctionCallingRequest() throws Exception {
        // 模拟带有tool_calls的响应
        String mockResponse = """
            {
                "id": "chatcmpl-456",
                "model": "gpt-4o",
                "choices": [
                    {
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": null,
                            "tool_calls": [
                                {
                                    "id": "call_abc123",
                                    "type": "function",
                                    "function": {
                                        "name": "get_weather",
                                        "arguments": "{\\"location\\": \\"Beijing\\", \\"unit\\": \\"celsius\\"}"
                                    }
                                }
                            ]
                        },
                        "finish_reason": "tool_calls"
                    }
                ],
                "usage": {
                    "prompt_tokens": 50,
                    "completion_tokens": 20,
                    "total_tokens": 70
                }
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setBody(mockResponse)
            .addHeader("Content-Type", "application/json"));
        
        // 创建带有工具的请求
        List<LLMMessage> messages = Arrays.asList(
            new LLMMessage(LLMMessage.MessageRole.USER, "What's the weather in Beijing?")
        );
        
        // 创建工具定义
        JsonObject parametersSchema = new JsonObject();
        parametersSchema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject locationProp = new JsonObject();
        locationProp.addProperty("type", "string");
        locationProp.addProperty("description", "The city name");
        properties.add("location", locationProp);
        parametersSchema.add("properties", properties);
        
        LLMConfig.FunctionDefinition functionDef = new LLMConfig.FunctionDefinition(
            "get_weather",
            "Get weather information",
            parametersSchema
        );
        
        LLMConfig.ToolDefinition toolDef = new LLMConfig.ToolDefinition(functionDef);
        
        LLMConfig config = new LLMConfig();
        config.setModel("gpt-4o");
        config.setTools(Arrays.asList(toolDef));
        config.setToolChoice("auto");
        
        // 发送请求
        CompletableFuture<LLMResponse> future = openAIService.chat(messages, config);
        LLMResponse response = future.get(5, TimeUnit.SECONDS);
        
        // 验证响应
        assertTrue(response.isSuccess());
        assertNull(response.getContent()); // content为null，因为有tool_calls
        
        // 验证tool_calls解析
        assertNotNull(response.getChoices());
        assertEquals(1, response.getChoices().size());
        
        LLMResponse.Choice choice = response.getChoices().get(0);
        assertNotNull(choice.getMessage());
        assertNotNull(choice.getMessage().getMetadata());
        assertNotNull(choice.getMessage().getMetadata().getFunctionCall());
        
        LLMMessage.FunctionCall functionCall = choice.getMessage().getMetadata().getFunctionCall();
        assertEquals("get_weather", functionCall.getName());
        assertEquals("call_abc123", functionCall.getToolCallId());
        assertTrue(functionCall.getArguments().contains("Beijing"));
        assertTrue(functionCall.getArguments().contains("celsius"));
        
        // 验证请求包含tools
        RecordedRequest request = mockWebServer.takeRequest();
        String requestBody = request.getBody().readUtf8();
        JsonObject requestJson = gson.fromJson(requestBody, JsonObject.class);
        
        assertTrue(requestJson.has("tools"));
        assertEquals("auto", requestJson.get("tool_choice").getAsString());
        
        JsonObject tool = requestJson.getAsJsonArray("tools").get(0).getAsJsonObject();
        assertEquals("function", tool.get("type").getAsString());
        assertEquals("get_weather", tool.getAsJsonObject("function").get("name").getAsString());
    }

    @Test
    void testToolMessageRequest() throws Exception {
        // 模拟对tool消息的响应
        String mockResponse = """
            {
                "id": "chatcmpl-789",
                "model": "gpt-4o",
                "choices": [
                    {
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": "The weather in Beijing is 22°C and sunny."
                        },
                        "finish_reason": "stop"
                    }
                ]
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
            .setBody(mockResponse)
            .addHeader("Content-Type", "application/json"));
        
        // 创建包含tool消息的对话
        LLMMessage userMessage = new LLMMessage(LLMMessage.MessageRole.USER, "What's the weather in Beijing?");
        
        LLMMessage assistantMessage = new LLMMessage(LLMMessage.MessageRole.ASSISTANT, null);
        LLMMessage.FunctionCall functionCall = new LLMMessage.FunctionCall(
            "get_weather",
            "{\"location\": \"Beijing\", \"unit\": \"celsius\"}",
            "call_abc123"
        );
        LLMMessage.MessageMetadata metadata = new LLMMessage.MessageMetadata();
        metadata.setFunctionCall(functionCall);
        assistantMessage.setMetadata(metadata);
        
        LLMMessage toolMessage = new LLMMessage(LLMMessage.MessageRole.TOOL, "22°C, sunny");
        toolMessage.setName("get_weather");
        toolMessage.setToolCallId("call_abc123");
        
        List<LLMMessage> messages = Arrays.asList(userMessage, assistantMessage, toolMessage);
        
        LLMConfig config = new LLMConfig();
        config.setModel("gpt-4o");
        
        // 发送请求
        CompletableFuture<LLMResponse> future = openAIService.chat(messages, config);
        LLMResponse response = future.get(5, TimeUnit.SECONDS);
        
        // 验证响应
        assertTrue(response.isSuccess());
        assertEquals("The weather in Beijing is 22°C and sunny.", response.getContent());
        
        // 验证请求体包含正确的消息格式
        RecordedRequest request = mockWebServer.takeRequest();
        String requestBody = request.getBody().readUtf8();
        JsonObject requestJson = gson.fromJson(requestBody, JsonObject.class);
        
        JsonObject messagesArray = requestJson.getAsJsonArray("messages").get(2).getAsJsonObject();
        assertEquals("tool", messagesArray.get("role").getAsString());
        assertEquals("22°C, sunny", messagesArray.get("content").getAsString());
        assertEquals("get_weather", messagesArray.get("name").getAsString());
        assertEquals("call_abc123", messagesArray.get("tool_call_id").getAsString());
    }

    @Test
    void testErrorResponse() throws Exception {
        // 模拟错误响应
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(400)
            .setBody("{\"error\": {\"message\": \"Invalid request\", \"type\": \"invalid_request_error\"}}"));
        
        List<LLMMessage> messages = Arrays.asList(
            new LLMMessage(LLMMessage.MessageRole.USER, "Hello")
        );
        
        LLMConfig config = new LLMConfig();
        
        CompletableFuture<LLMResponse> future = openAIService.chat(messages, config);
        LLMResponse response = future.get(5, TimeUnit.SECONDS);
        
        assertFalse(response.isSuccess());
        assertNotNull(response.getError());
        assertTrue(response.getError().contains("HTTP 400"));
    }

    @Test
    void testServiceInfo() {
        assertEquals("OpenAI", openAIService.getServiceName());
        assertTrue(openAIService.isAvailable());
        
        List<String> supportedModels = openAIService.getSupportedModels();
        assertFalse(supportedModels.isEmpty());
        assertTrue(supportedModels.contains("gpt-4o"));
        assertTrue(supportedModels.contains("gpt-3.5-turbo"));
    }

    @Test
    void testUnavailableService() {
        OpenAIService unavailableService = new OpenAIService(null, "http://localhost");
        assertFalse(unavailableService.isAvailable());
        
        OpenAIService emptyKeyService = new OpenAIService("", "http://localhost");
        assertFalse(emptyKeyService.isAvailable());
        
        OpenAIService whitespaceKeyService = new OpenAIService("   ", "http://localhost");
        assertFalse(whitespaceKeyService.isAvailable());
    }
}
