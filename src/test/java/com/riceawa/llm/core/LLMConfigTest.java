package com.riceawa.llm.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LLMConfigTest {

    private final Gson gson = new Gson();

    @Test
    void testDefaultConfiguration() {
        LLMConfig config = new LLMConfig();
        
        assertEquals(0.7, config.getTemperature());
        assertEquals(2048, config.getMaxTokens());
        assertFalse(config.getStream());
        assertNull(config.getTools());
        assertNull(config.getToolChoice());
    }

    @Test
    void testSettersAndGetters() {
        LLMConfig config = new LLMConfig();
        
        config.setModel("gpt-4o");
        config.setTemperature(0.8);
        config.setMaxTokens(4096);
        config.setTopP(0.9);
        config.setFrequencyPenalty(0.1);
        config.setPresencePenalty(0.2);
        config.setStop(Arrays.asList("stop1", "stop2"));
        config.setStream(true);
        config.setToolChoice("auto");
        
        assertEquals("gpt-4o", config.getModel());
        assertEquals(0.8, config.getTemperature());
        assertEquals(4096, config.getMaxTokens());
        assertEquals(0.9, config.getTopP());
        assertEquals(0.1, config.getFrequencyPenalty());
        assertEquals(0.2, config.getPresencePenalty());
        assertEquals(Arrays.asList("stop1", "stop2"), config.getStop());
        assertTrue(config.getStream());
        assertEquals("auto", config.getToolChoice());
    }

    @Test
    void testToolDefinition() {
        // 创建FunctionDefinition
        JsonObject parametersSchema = new JsonObject();
        parametersSchema.addProperty("type", "object");
        
        LLMConfig.FunctionDefinition functionDef = new LLMConfig.FunctionDefinition(
            "test_function",
            "测试函数",
            parametersSchema
        );
        
        assertEquals("test_function", functionDef.getName());
        assertEquals("测试函数", functionDef.getDescription());
        assertEquals(parametersSchema, functionDef.getParameters());
        
        // 创建ToolDefinition
        LLMConfig.ToolDefinition toolDef = new LLMConfig.ToolDefinition(functionDef);
        
        assertEquals("function", toolDef.getType());
        assertEquals(functionDef, toolDef.getFunction());
    }

    @Test
    void testToolDefinitionSerialization() {
        // 创建完整的配置
        JsonObject parametersSchema = new JsonObject();
        parametersSchema.addProperty("type", "object");
        
        LLMConfig.FunctionDefinition functionDef = new LLMConfig.FunctionDefinition(
            "get_weather",
            "获取天气信息",
            parametersSchema
        );
        
        LLMConfig.ToolDefinition toolDef = new LLMConfig.ToolDefinition(functionDef);
        
        LLMConfig config = new LLMConfig();
        config.setModel("gpt-4o");
        config.setTools(Arrays.asList(toolDef));
        config.setToolChoice("auto");
        
        // 序列化为JSON
        String json = gson.toJson(config);
        
        // 验证JSON包含预期的字段
        assertTrue(json.contains("\"model\":\"gpt-4o\""));
        assertTrue(json.contains("\"tools\""));
        assertTrue(json.contains("\"tool_choice\":\"auto\""));
        assertTrue(json.contains("\"type\":\"function\""));
        assertTrue(json.contains("\"name\":\"get_weather\""));
        assertTrue(json.contains("\"description\":\"获取天气信息\""));
        
        // 反序列化
        LLMConfig deserializedConfig = gson.fromJson(json, LLMConfig.class);
        
        assertEquals("gpt-4o", deserializedConfig.getModel());
        assertEquals("auto", deserializedConfig.getToolChoice());
        assertNotNull(deserializedConfig.getTools());
        assertEquals(1, deserializedConfig.getTools().size());
        
        LLMConfig.ToolDefinition deserializedTool = deserializedConfig.getTools().get(0);
        assertEquals("function", deserializedTool.getType());
        assertEquals("get_weather", deserializedTool.getFunction().getName());
        assertEquals("获取天气信息", deserializedTool.getFunction().getDescription());
    }

    @Test
    void testFunctionDefinitionConstructors() {
        // 测试默认构造函数
        LLMConfig.FunctionDefinition functionDef1 = new LLMConfig.FunctionDefinition();
        assertNull(functionDef1.getName());
        assertNull(functionDef1.getDescription());
        assertNull(functionDef1.getParameters());
        
        // 测试参数构造函数
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        
        LLMConfig.FunctionDefinition functionDef2 = new LLMConfig.FunctionDefinition(
            "test_func",
            "测试描述",
            schema
        );
        
        assertEquals("test_func", functionDef2.getName());
        assertEquals("测试描述", functionDef2.getDescription());
        assertEquals(schema, functionDef2.getParameters());
        
        // 测试setter方法
        functionDef1.setName("updated_name");
        functionDef1.setDescription("更新的描述");
        functionDef1.setParameters(schema);
        
        assertEquals("updated_name", functionDef1.getName());
        assertEquals("更新的描述", functionDef1.getDescription());
        assertEquals(schema, functionDef1.getParameters());
    }

    @Test
    void testToolDefinitionConstructors() {
        // 测试默认构造函数
        LLMConfig.ToolDefinition toolDef1 = new LLMConfig.ToolDefinition();
        assertEquals("function", toolDef1.getType()); // 默认值
        assertNull(toolDef1.getFunction());
        
        // 测试参数构造函数
        LLMConfig.FunctionDefinition functionDef = new LLMConfig.FunctionDefinition(
            "test_func",
            "测试函数",
            new JsonObject()
        );
        
        LLMConfig.ToolDefinition toolDef2 = new LLMConfig.ToolDefinition(functionDef);
        assertEquals("function", toolDef2.getType());
        assertEquals(functionDef, toolDef2.getFunction());
        
        // 测试setter方法
        toolDef1.setType("custom_type");
        toolDef1.setFunction(functionDef);
        
        assertEquals("custom_type", toolDef1.getType());
        assertEquals(functionDef, toolDef1.getFunction());
    }

    @Test
    void testComplexToolConfiguration() {
        // 创建多个工具的复杂配置
        JsonObject weatherSchema = new JsonObject();
        weatherSchema.addProperty("type", "object");
        JsonObject weatherProps = new JsonObject();
        JsonObject locationProp = new JsonObject();
        locationProp.addProperty("type", "string");
        locationProp.addProperty("description", "城市名称");
        weatherProps.add("location", locationProp);
        weatherSchema.add("properties", weatherProps);
        
        LLMConfig.FunctionDefinition weatherFunc = new LLMConfig.FunctionDefinition(
            "get_weather",
            "获取天气信息",
            weatherSchema
        );
        
        JsonObject timeSchema = new JsonObject();
        timeSchema.addProperty("type", "object");
        timeSchema.add("properties", new JsonObject());
        
        LLMConfig.FunctionDefinition timeFunc = new LLMConfig.FunctionDefinition(
            "get_time",
            "获取当前时间",
            timeSchema
        );
        
        List<LLMConfig.ToolDefinition> tools = Arrays.asList(
            new LLMConfig.ToolDefinition(weatherFunc),
            new LLMConfig.ToolDefinition(timeFunc)
        );
        
        LLMConfig config = new LLMConfig();
        config.setModel("gpt-4o");
        config.setTemperature(0.7);
        config.setMaxTokens(2048);
        config.setTools(tools);
        config.setToolChoice("auto");
        
        // 验证配置
        assertEquals(2, config.getTools().size());
        assertEquals("get_weather", config.getTools().get(0).getFunction().getName());
        assertEquals("get_time", config.getTools().get(1).getFunction().getName());
        
        // 序列化测试
        String json = gson.toJson(config);
        LLMConfig deserializedConfig = gson.fromJson(json, LLMConfig.class);
        
        assertEquals(2, deserializedConfig.getTools().size());
        assertEquals("get_weather", deserializedConfig.getTools().get(0).getFunction().getName());
        assertEquals("get_time", deserializedConfig.getTools().get(1).getFunction().getName());
    }
}
