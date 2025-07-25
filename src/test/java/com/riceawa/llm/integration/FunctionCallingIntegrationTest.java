package com.riceawa.llm.integration;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.riceawa.llm.core.*;
import com.riceawa.llm.function.FunctionRegistry;
import com.riceawa.llm.function.LLMFunction;
import com.riceawa.llm.service.OpenAIService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FunctionCallingIntegrationTest {

    @Mock
    private PlayerEntity mockPlayer;

    @Mock
    private MinecraftServer mockServer;

    private MockWebServer mockWebServer;
    private OpenAIService openAIService;
    private FunctionRegistry functionRegistry;
    private final Gson gson = new Gson();

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        String baseUrl = mockWebServer.url("/").toString().replaceAll("/$", "");
        openAIService = new OpenAIService("test-api-key", baseUrl);
        
        functionRegistry = FunctionRegistry.getInstance();
        
        // 注册测试函数
        functionRegistry.registerFunction(new TestWeatherFunction());
        
        // 设置mock行为
        when(mockPlayer.hasPermissionLevel(anyInt())).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
        functionRegistry.unregisterFunction("get_test_weather");
    }

    @Test
    void testCompleteToolCallingFlow() throws Exception {
        // 第一步：模拟LLM返回tool_calls
        String toolCallResponse = """
            {
                "id": "chatcmpl-tool-123",
                "model": "gpt-4o",
                "choices": [
                    {
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": null,
                            "tool_calls": [
                                {
                                    "id": "call_weather_123",
                                    "type": "function",
                                    "function": {
                                        "name": "get_test_weather",
                                        "arguments": "{\\"location\\": \\"Beijing\\"}"
                                    }
                                }
                            ]
                        },
                        "finish_reason": "tool_calls"
                    }
                ]
            }
            """;

        // 第二步：模拟LLM基于工具结果的最终响应
        String finalResponse = """
            {
                "id": "chatcmpl-final-456",
                "model": "gpt-4o",
                "choices": [
                    {
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": "The weather in Beijing is sunny with a temperature of 22°C."
                        },
                        "finish_reason": "stop"
                    }
                ]
            }
            """;

        mockWebServer.enqueue(new MockResponse()
            .setBody(toolCallResponse)
            .addHeader("Content-Type", "application/json"));

        mockWebServer.enqueue(new MockResponse()
            .setBody(finalResponse)
            .addHeader("Content-Type", "application/json"));

        // 创建初始消息
        List<LLMMessage> messages = Arrays.asList(
            new LLMMessage(LLMMessage.MessageRole.USER, "What's the weather in Beijing?")
        );

        // 创建配置，包含工具定义
        List<LLMConfig.ToolDefinition> tools = functionRegistry.generateToolDefinitions(mockPlayer);
        
        LLMConfig config = new LLMConfig();
        config.setModel("gpt-4o");
        config.setTools(tools);
        config.setToolChoice("auto");

        // 第一次调用：获取tool_calls
        CompletableFuture<LLMResponse> future1 = openAIService.chat(messages, config);
        LLMResponse response1 = future1.get(5, TimeUnit.SECONDS);

        // 验证第一次响应
        assertTrue(response1.isSuccess());
        assertNotNull(response1.getChoices());
        assertEquals(1, response1.getChoices().size());

        LLMMessage assistantMessage = response1.getChoices().get(0).getMessage();
        assertNotNull(assistantMessage.getMetadata());
        assertNotNull(assistantMessage.getMetadata().getFunctionCall());

        LLMMessage.FunctionCall functionCall = assistantMessage.getMetadata().getFunctionCall();
        assertEquals("get_test_weather", functionCall.getName());
        assertEquals("call_weather_123", functionCall.getToolCallId());

        // 执行函数
        JsonObject arguments = gson.fromJson(functionCall.getArguments(), JsonObject.class);
        LLMFunction.FunctionResult functionResult = functionRegistry.executeFunction(
            functionCall.getName(), mockPlayer, arguments);

        assertTrue(functionResult.isSuccess());
        assertEquals("Beijing: 22°C, sunny", functionResult.getResult());

        // 构建包含工具响应的消息列表
        LLMMessage toolMessage = new LLMMessage(LLMMessage.MessageRole.TOOL, functionResult.getResult());
        toolMessage.setName(functionCall.getName());
        toolMessage.setToolCallId(functionCall.getToolCallId());

        List<LLMMessage> messagesWithTool = Arrays.asList(
            new LLMMessage(LLMMessage.MessageRole.USER, "What's the weather in Beijing?"),
            assistantMessage,
            toolMessage
        );

        // 第二次调用：获取基于工具结果的最终响应
        LLMConfig finalConfig = new LLMConfig();
        finalConfig.setModel("gpt-4o");

        CompletableFuture<LLMResponse> future2 = openAIService.chat(messagesWithTool, finalConfig);
        LLMResponse response2 = future2.get(5, TimeUnit.SECONDS);

        // 验证最终响应
        assertTrue(response2.isSuccess());
        assertEquals("The weather in Beijing is sunny with a temperature of 22°C.", response2.getContent());

        // 验证请求
        RecordedRequest request1 = mockWebServer.takeRequest();
        String requestBody1 = request1.getBody().readUtf8();
        JsonObject requestJson1 = gson.fromJson(requestBody1, JsonObject.class);
        
        assertTrue(requestJson1.has("tools"));
        assertEquals("auto", requestJson1.get("tool_choice").getAsString());

        RecordedRequest request2 = mockWebServer.takeRequest();
        String requestBody2 = request2.getBody().readUtf8();
        JsonObject requestJson2 = gson.fromJson(requestBody2, JsonObject.class);
        
        // 验证第二次请求包含tool消息
        JsonObject messagesArray = requestJson2.getAsJsonArray("messages").get(2).getAsJsonObject();
        assertEquals("tool", messagesArray.get("role").getAsString());
        assertEquals("Beijing: 22°C, sunny", messagesArray.get("content").getAsString());
        assertEquals("get_test_weather", messagesArray.get("name").getAsString());
        assertEquals("call_weather_123", messagesArray.get("tool_call_id").getAsString());
    }

    @Test
    void testToolDefinitionGeneration() {
        List<LLMConfig.ToolDefinition> tools = functionRegistry.generateToolDefinitions(mockPlayer);
        
        assertFalse(tools.isEmpty());
        
        // 查找我们的测试函数
        LLMConfig.ToolDefinition testTool = tools.stream()
            .filter(tool -> "get_test_weather".equals(tool.getFunction().getName()))
            .findFirst()
            .orElse(null);
        
        assertNotNull(testTool);
        assertEquals("function", testTool.getType());
        assertEquals("get_test_weather", testTool.getFunction().getName());
        assertEquals("获取测试天气信息", testTool.getFunction().getDescription());
        
        // 验证参数schema
        JsonObject parameters = (JsonObject) testTool.getFunction().getParameters();
        assertEquals("object", parameters.get("type").getAsString());
        assertTrue(parameters.has("properties"));
        
        JsonObject properties = parameters.getAsJsonObject("properties");
        assertTrue(properties.has("location"));
        
        JsonObject locationProp = properties.getAsJsonObject("location");
        assertEquals("string", locationProp.get("type").getAsString());
        assertEquals("城市名称", locationProp.get("description").getAsString());
    }

    @Test
    void testFunctionExecutionWithInvalidArguments() {
        JsonObject invalidArguments = new JsonObject();
        // 缺少必需的location参数
        
        LLMFunction.FunctionResult result = functionRegistry.executeFunction(
            "get_test_weather", mockPlayer, invalidArguments);
        
        assertTrue(result.isSuccess());
        assertEquals("default: 20°C, cloudy", result.getResult());
    }

    @Test
    void testFunctionExecutionWithValidArguments() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("location", "Shanghai");
        
        LLMFunction.FunctionResult result = functionRegistry.executeFunction(
            "get_test_weather", mockPlayer, arguments);
        
        assertTrue(result.isSuccess());
        assertEquals("Shanghai: 22°C, sunny", result.getResult());
    }

    // 测试用的天气函数
    private static class TestWeatherFunction implements LLMFunction {
        @Override
        public String getName() {
            return "get_test_weather";
        }

        @Override
        public String getDescription() {
            return "获取测试天气信息";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            
            JsonObject properties = new JsonObject();
            JsonObject locationProp = new JsonObject();
            locationProp.addProperty("type", "string");
            locationProp.addProperty("description", "城市名称");
            properties.add("location", locationProp);
            
            schema.add("properties", properties);
            return schema;
        }

        @Override
        public FunctionResult execute(PlayerEntity player, MinecraftServer server, JsonObject arguments) {
            String location = arguments.has("location") ? 
                arguments.get("location").getAsString() : "default";
            
            // 模拟天气数据
            if ("Beijing".equals(location)) {
                return FunctionResult.success("Beijing: 22°C, sunny");
            } else if ("Shanghai".equals(location)) {
                return FunctionResult.success("Shanghai: 22°C, sunny");
            } else {
                return FunctionResult.success(location + ": 20°C, cloudy");
            }
        }

        @Override
        public boolean hasPermission(PlayerEntity player) {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public String getCategory() {
            return "test";
        }
    }
}
