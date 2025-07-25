package com.riceawa.llm.core;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class LLMMessageTest {

    private final Gson gson = new Gson();

    @Test
    void testBasicMessageCreation() {
        LLMMessage message = new LLMMessage(LLMMessage.MessageRole.USER, "Hello, world!");
        
        assertEquals(LLMMessage.MessageRole.USER, message.getRole());
        assertEquals("Hello, world!", message.getContent());
        assertNotNull(message.getId());
        assertNotNull(message.getTimestamp());
        assertNotNull(message.getMetadata());
    }

    @Test
    void testMessageRoleValues() {
        assertEquals("system", LLMMessage.MessageRole.SYSTEM.getValue());
        assertEquals("user", LLMMessage.MessageRole.USER.getValue());
        assertEquals("assistant", LLMMessage.MessageRole.ASSISTANT.getValue());
        assertEquals("function", LLMMessage.MessageRole.FUNCTION.getValue());
        assertEquals("tool", LLMMessage.MessageRole.TOOL.getValue());
    }

    @Test
    void testToolMessage() {
        LLMMessage toolMessage = new LLMMessage(LLMMessage.MessageRole.TOOL, "Function result");
        toolMessage.setName("get_weather");
        toolMessage.setToolCallId("call_123456");
        
        assertEquals(LLMMessage.MessageRole.TOOL, toolMessage.getRole());
        assertEquals("Function result", toolMessage.getContent());
        assertEquals("get_weather", toolMessage.getName());
        assertEquals("call_123456", toolMessage.getToolCallId());
    }

    @Test
    void testFunctionCall() {
        // 测试基本构造函数
        LLMMessage.FunctionCall functionCall1 = new LLMMessage.FunctionCall("get_weather", "{\"location\":\"Beijing\"}");
        
        assertEquals("get_weather", functionCall1.getName());
        assertEquals("{\"location\":\"Beijing\"}", functionCall1.getArguments());
        assertNull(functionCall1.getToolCallId());
        
        // 测试带tool_call_id的构造函数
        LLMMessage.FunctionCall functionCall2 = new LLMMessage.FunctionCall(
            "get_time", 
            "{}", 
            "call_789012"
        );
        
        assertEquals("get_time", functionCall2.getName());
        assertEquals("{}", functionCall2.getArguments());
        assertEquals("call_789012", functionCall2.getToolCallId());
        
        // 测试setter方法
        functionCall1.setToolCallId("call_updated");
        assertEquals("call_updated", functionCall1.getToolCallId());
    }

    @Test
    void testMessageMetadata() {
        LLMMessage.MessageMetadata metadata = new LLMMessage.MessageMetadata();
        
        // 测试tokens
        metadata.setTokens(150);
        assertEquals(150, metadata.getTokens());
        
        // 测试model
        metadata.setModel("gpt-4o");
        assertEquals("gpt-4o", metadata.getModel());
        
        // 测试function call
        LLMMessage.FunctionCall functionCall = new LLMMessage.FunctionCall(
            "get_weather", 
            "{\"location\":\"Shanghai\"}", 
            "call_123"
        );
        metadata.setFunctionCall(functionCall);
        
        assertEquals(functionCall, metadata.getFunctionCall());
        assertEquals("get_weather", metadata.getFunctionCall().getName());
        assertEquals("call_123", metadata.getFunctionCall().getToolCallId());
    }

    @Test
    void testAssistantMessageWithFunctionCall() {
        LLMMessage assistantMessage = new LLMMessage(LLMMessage.MessageRole.ASSISTANT, null);
        
        LLMMessage.FunctionCall functionCall = new LLMMessage.FunctionCall(
            "get_player_info",
            "{}",
            "call_player_123"
        );
        
        LLMMessage.MessageMetadata metadata = new LLMMessage.MessageMetadata();
        metadata.setFunctionCall(functionCall);
        assistantMessage.setMetadata(metadata);
        
        assertEquals(LLMMessage.MessageRole.ASSISTANT, assistantMessage.getRole());
        assertNull(assistantMessage.getContent());
        assertNotNull(assistantMessage.getMetadata().getFunctionCall());
        assertEquals("get_player_info", assistantMessage.getMetadata().getFunctionCall().getName());
        assertEquals("call_player_123", assistantMessage.getMetadata().getFunctionCall().getToolCallId());
    }

    @Test
    void testMessageSerialization() {
        // 创建一个包含function call的assistant消息
        LLMMessage assistantMessage = new LLMMessage(LLMMessage.MessageRole.ASSISTANT, "I'll get the weather for you.");
        
        LLMMessage.FunctionCall functionCall = new LLMMessage.FunctionCall(
            "get_weather",
            "{\"location\":\"Tokyo\",\"unit\":\"celsius\"}",
            "call_weather_456"
        );
        
        LLMMessage.MessageMetadata metadata = new LLMMessage.MessageMetadata();
        metadata.setTokens(25);
        metadata.setModel("gpt-4o");
        metadata.setFunctionCall(functionCall);
        assistantMessage.setMetadata(metadata);
        
        // 序列化
        String json = gson.toJson(assistantMessage);
        
        // 验证JSON包含预期字段
        assertTrue(json.contains("\"role\":\"assistant\""));
        assertTrue(json.contains("\"content\":\"I'll get the weather for you.\""));
        assertTrue(json.contains("\"name\":\"get_weather\""));
        assertTrue(json.contains("\"tool_call_id\":\"call_weather_456\""));
        assertTrue(json.contains("\"tokens\":25"));
        assertTrue(json.contains("\"model\":\"gpt-4o\""));
        
        // 反序列化
        LLMMessage deserializedMessage = gson.fromJson(json, LLMMessage.class);
        
        assertEquals(LLMMessage.MessageRole.ASSISTANT, deserializedMessage.getRole());
        assertEquals("I'll get the weather for you.", deserializedMessage.getContent());
        assertNotNull(deserializedMessage.getMetadata());
        assertNotNull(deserializedMessage.getMetadata().getFunctionCall());
        assertEquals("get_weather", deserializedMessage.getMetadata().getFunctionCall().getName());
        assertEquals("call_weather_456", deserializedMessage.getMetadata().getFunctionCall().getToolCallId());
        assertEquals(25, deserializedMessage.getMetadata().getTokens());
        assertEquals("gpt-4o", deserializedMessage.getMetadata().getModel());
    }

    @Test
    void testToolMessageSerialization() {
        // 创建tool消息
        LLMMessage toolMessage = new LLMMessage(LLMMessage.MessageRole.TOOL, "Current weather in Tokyo: 22°C, sunny");
        toolMessage.setName("get_weather");
        toolMessage.setToolCallId("call_weather_456");
        
        // 序列化
        String json = gson.toJson(toolMessage);
        
        // 验证JSON包含预期字段
        assertTrue(json.contains("\"role\":\"tool\""));
        assertTrue(json.contains("\"content\":\"Current weather in Tokyo: 22°C, sunny\""));
        assertTrue(json.contains("\"name\":\"get_weather\""));
        assertTrue(json.contains("\"tool_call_id\":\"call_weather_456\""));
        
        // 反序列化
        LLMMessage deserializedMessage = gson.fromJson(json, LLMMessage.class);
        
        assertEquals(LLMMessage.MessageRole.TOOL, deserializedMessage.getRole());
        assertEquals("Current weather in Tokyo: 22°C, sunny", deserializedMessage.getContent());
        assertEquals("get_weather", deserializedMessage.getName());
        assertEquals("call_weather_456", deserializedMessage.getToolCallId());
    }

    @Test
    void testFullConstructor() {
        LocalDateTime timestamp = LocalDateTime.now();
        LLMMessage.MessageMetadata metadata = new LLMMessage.MessageMetadata();
        metadata.setTokens(100);
        
        LLMMessage message = new LLMMessage(
            "custom_id",
            LLMMessage.MessageRole.SYSTEM,
            "System message",
            timestamp,
            metadata
        );
        
        assertEquals("custom_id", message.getId());
        assertEquals(LLMMessage.MessageRole.SYSTEM, message.getRole());
        assertEquals("System message", message.getContent());
        assertEquals(timestamp, message.getTimestamp());
        assertEquals(metadata, message.getMetadata());
        assertEquals(100, message.getMetadata().getTokens());
    }

    @Test
    void testFunctionCallSetters() {
        LLMMessage.FunctionCall functionCall = new LLMMessage.FunctionCall();
        
        functionCall.setName("test_function");
        functionCall.setArguments("{\"param\":\"value\"}");
        functionCall.setToolCallId("call_test_123");
        
        assertEquals("test_function", functionCall.getName());
        assertEquals("{\"param\":\"value\"}", functionCall.getArguments());
        assertEquals("call_test_123", functionCall.getToolCallId());
    }

    @Test
    void testMessageMetadataSetters() {
        LLMMessage.MessageMetadata metadata = new LLMMessage.MessageMetadata();
        
        assertNull(metadata.getTokens());
        assertNull(metadata.getModel());
        assertNull(metadata.getFunctionCall());
        
        metadata.setTokens(200);
        metadata.setModel("gpt-3.5-turbo");
        
        LLMMessage.FunctionCall functionCall = new LLMMessage.FunctionCall("test", "{}", "call_123");
        metadata.setFunctionCall(functionCall);
        
        assertEquals(200, metadata.getTokens());
        assertEquals("gpt-3.5-turbo", metadata.getModel());
        assertEquals(functionCall, metadata.getFunctionCall());
    }
}
