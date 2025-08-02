package com.riceawa.mcp.util;

import com.google.gson.JsonObject;
import com.riceawa.mcp.exception.MCPException;
import com.riceawa.mcp.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCPJsonUtils工具类测试
 */
class MCPJsonUtilsTest {

    private MCPTool tool;
    private MCPToolResult toolResult;
    private MCPContent content;
    private MCPResource resource;
    private MCPPrompt prompt;

    @BeforeEach
    void setUp() {
        // 设置测试用的MCPTool
        tool = new MCPTool("test_tool", "测试工具", "这是一个测试工具");
        tool.setClientName("test_client");
        
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        tool.setInputSchema(schema);
        
        Map<String, Object> annotations = new HashMap<>();
        annotations.put("version", "1.0");
        tool.setAnnotations(annotations);
        
        // 设置测试用的MCPContent
        content = MCPContent.text("测试文本内容");
        content.addAnnotation("source", "test");
        
        // 设置测试用的MCPToolResult
        List<MCPContent> contentList = new ArrayList<>();
        contentList.add(content);
        toolResult = MCPToolResult.success(contentList);
        toolResult.setToolName("test_tool");
        toolResult.setClientName("test_client");
        
        // 设置测试用的MCPResource
        resource = new MCPResource("file:///test.txt", "test.txt");
        resource.setTitle("测试文件");
        resource.setMimeType("text/plain");
        resource.setClientName("filesystem");
        
        // 设置测试用的MCPPrompt
        prompt = new MCPPrompt("test_prompt", "测试提示词", "这是一个测试提示词");
        prompt.setClientName("test_client");
        prompt.setArgumentSchema(schema);
    }

    @Test
    void testGsonInstance() {
        assertNotNull(MCPJsonUtils.getGson());
    }

    @Test
    void testMCPToolSerialization() throws MCPException {
        // 测试序列化
        String json = MCPJsonUtils.toJson(tool);
        assertNotNull(json);
        assertTrue(json.contains("test_tool"));
        assertTrue(json.contains("测试工具"));
        assertTrue(json.contains("test_client"));
        
        // 测试反序列化
        MCPTool deserialized = MCPJsonUtils.fromJsonToTool(json);
        assertEquals(tool.getName(), deserialized.getName());
        assertEquals(tool.getTitle(), deserialized.getTitle());
        assertEquals(tool.getDescription(), deserialized.getDescription());
        assertEquals(tool.getClientName(), deserialized.getClientName());
        assertNotNull(deserialized.getInputSchema());
    }

    @Test
    void testMCPToolListSerialization() throws MCPException {
        List<MCPTool> tools = new ArrayList<>();
        tools.add(tool);
        
        MCPTool tool2 = new MCPTool("tool2", "工具2", "第二个工具");
        tools.add(tool2);
        
        // 序列化工具列表
        String json = MCPJsonUtils.toJson(tools);
        assertNotNull(json);
        
        // 反序列化工具列表
        List<MCPTool> deserializedTools = MCPJsonUtils.fromJsonToToolList(json);
        assertEquals(2, deserializedTools.size());
        assertEquals("test_tool", deserializedTools.get(0).getName());
        assertEquals("tool2", deserializedTools.get(1).getName());
    }

    @Test
    void testMCPToolResultSerialization() throws MCPException {
        // 测试序列化
        String json = MCPJsonUtils.toJson(toolResult);
        assertNotNull(json);
        assertTrue(json.contains("test_tool"));
        assertTrue(json.contains("test_client"));
        
        // 测试反序列化
        MCPToolResult deserialized = MCPJsonUtils.fromJsonToToolResult(json);
        assertEquals(toolResult.getToolName(), deserialized.getToolName());
        assertEquals(toolResult.getClientName(), deserialized.getClientName());
        assertEquals(toolResult.isError(), deserialized.isError());
        assertEquals(toolResult.getContent().size(), deserialized.getContent().size());
    }

    @Test
    void testMCPContentSerialization() throws MCPException {
        // 测试序列化
        String json = MCPJsonUtils.toJson(content);
        assertNotNull(json);
        assertTrue(json.contains("text"));
        assertTrue(json.contains("测试文本内容"));
        
        // 测试反序列化
        MCPContent deserialized = MCPJsonUtils.fromJsonToContent(json);
        assertEquals(content.getType(), deserialized.getType());
        assertEquals(content.getText(), deserialized.getText());
        assertEquals(content.getAnnotations().size(), deserialized.getAnnotations().size());
    }

    @Test
    void testMCPResourceSerialization() throws MCPException {
        // 测试序列化
        String json = MCPJsonUtils.toJson(resource);
        assertNotNull(json);
        assertTrue(json.contains("file:///test.txt"));
        assertTrue(json.contains("测试文件"));
        
        // 测试反序列化
        MCPResource deserialized = MCPJsonUtils.fromJsonToResource(json);
        assertEquals(resource.getUri(), deserialized.getUri());
        assertEquals(resource.getName(), deserialized.getName());
        assertEquals(resource.getTitle(), deserialized.getTitle());
        assertEquals(resource.getMimeType(), deserialized.getMimeType());
        assertEquals(resource.getClientName(), deserialized.getClientName());
    }

    @Test
    void testMCPResourceListSerialization() throws MCPException {
        List<MCPResource> resources = new ArrayList<>();
        resources.add(resource);
        
        MCPResource resource2 = new MCPResource("file:///test2.txt", "test2.txt");
        resources.add(resource2);
        
        // 序列化资源列表
        String json = MCPJsonUtils.toJson(resources);
        assertNotNull(json);
        
        // 反序列化资源列表
        List<MCPResource> deserializedResources = MCPJsonUtils.fromJsonToResourceList(json);
        assertEquals(2, deserializedResources.size());
        assertEquals("file:///test.txt", deserializedResources.get(0).getUri());
        assertEquals("file:///test2.txt", deserializedResources.get(1).getUri());
    }

    @Test
    void testMCPPromptSerialization() throws MCPException {
        // 测试序列化
        String json = MCPJsonUtils.toJson(prompt);
        assertNotNull(json);
        assertTrue(json.contains("test_prompt"));
        assertTrue(json.contains("测试提示词"));
        
        // 测试反序列化
        MCPPrompt deserialized = MCPJsonUtils.fromJsonToPrompt(json);
        assertEquals(prompt.getName(), deserialized.getName());
        assertEquals(prompt.getTitle(), deserialized.getTitle());
        assertEquals(prompt.getDescription(), deserialized.getDescription());
        assertEquals(prompt.getClientName(), deserialized.getClientName());
        assertNotNull(deserialized.getArgumentSchema());
    }

    @Test
    void testMCPPromptListSerialization() throws MCPException {
        List<MCPPrompt> prompts = new ArrayList<>();
        prompts.add(prompt);
        
        MCPPrompt prompt2 = new MCPPrompt("prompt2", "提示词2", "第二个提示词");
        prompts.add(prompt2);
        
        // 序列化提示词列表
        String json = MCPJsonUtils.toJson(prompts);
        assertNotNull(json);
        
        // 反序列化提示词列表
        List<MCPPrompt> deserializedPrompts = MCPJsonUtils.fromJsonToPromptList(json);
        assertEquals(2, deserializedPrompts.size());
        assertEquals("test_prompt", deserializedPrompts.get(0).getName());
        assertEquals("prompt2", deserializedPrompts.get(1).getName());
    }

    @Test
    void testGenericSerialization() throws MCPException {
        Map<String, Object> testData = new HashMap<>();
        testData.put("name", "test");
        testData.put("value", 42);
        testData.put("enabled", true);
        
        // 测试通用序列化
        String json = MCPJsonUtils.toJson(testData);
        assertNotNull(json);
        assertTrue(json.contains("test"));
        assertTrue(json.contains("42"));
        assertTrue(json.contains("true"));
        
        // 测试通用反序列化
        @SuppressWarnings("unchecked")
        Map<String, Object> deserialized = MCPJsonUtils.fromJson(json, Map.class);
        assertEquals("test", deserialized.get("name"));
        // 注意：JSON反序列化可能会将数字转换为double
        assertTrue(deserialized.get("value") instanceof Number);
        assertEquals(true, deserialized.get("enabled"));
    }

    @Test
    void testErrorSerialization() throws MCPException {
        MCPToolResult errorResult = MCPToolResult.error("测试错误信息");
        errorResult.setToolName("error_tool");
        
        String json = MCPJsonUtils.toJson(errorResult);
        assertNotNull(json);
        assertTrue(json.contains("测试错误信息"));
        
        MCPToolResult deserialized = MCPJsonUtils.fromJsonToToolResult(json);
        assertTrue(deserialized.isError());
        assertEquals("测试错误信息", deserialized.getErrorMessage());
        assertEquals("error_tool", deserialized.getToolName());
    }

    @Test
    void testInvalidJsonHandling() {
        // 测试无效JSON
        assertThrows(MCPException.class, () -> {
            MCPJsonUtils.fromJsonToTool("invalid json");
        });
        
        assertThrows(MCPException.class, () -> {
            MCPJsonUtils.fromJsonToToolResult("{invalid json}");
        });
        
        assertThrows(MCPException.class, () -> {
            MCPJsonUtils.fromJsonToContent("not json at all");
        });
        
        assertThrows(MCPException.class, () -> {
            MCPJsonUtils.fromJsonToResource("");
        });
        
        assertThrows(MCPException.class, () -> {
            MCPJsonUtils.fromJsonToPrompt("null");
        });
        
        // 测试null JSON
        assertThrows(MCPException.class, () -> {
            MCPJsonUtils.fromJsonToTool(null);
        });
    }

    @Test
    void testNullResultHandling() {
        // 这些测试检查当JSON解析结果为null时的处理
        assertThrows(MCPException.class, () -> {
            MCPJsonUtils.fromJsonToTool("null");
        });
        
        assertThrows(MCPException.class, () -> {
            MCPJsonUtils.fromJsonToToolResult("null");
        });
        
        assertThrows(MCPException.class, () -> {
            MCPJsonUtils.fromJsonToContent("null");
        });
    }

    @Test
    void testEmptyListHandling() throws MCPException {
        // 测试空列表的序列化和反序列化
        List<MCPTool> emptyTools = new ArrayList<>();
        String json = MCPJsonUtils.toJson(emptyTools);
        
        List<MCPTool> deserializedTools = MCPJsonUtils.fromJsonToToolList(json);
        assertTrue(deserializedTools.isEmpty());
        
        // 测试null列表JSON
        List<MCPResource> nullResources = MCPJsonUtils.fromJsonToResourceList("null");
        assertTrue(nullResources.isEmpty());
    }

    @Test
    void testJsonValidation() {
        // 测试有效JSON
        assertTrue(MCPJsonUtils.isValidJson("{\"test\": \"value\"}"));
        assertTrue(MCPJsonUtils.isValidJson("[]"));
        assertTrue(MCPJsonUtils.isValidJson("\"string\""));
        assertTrue(MCPJsonUtils.isValidJson("123"));
        assertTrue(MCPJsonUtils.isValidJson("true"));
        assertTrue(MCPJsonUtils.isValidJson("null"));
        
        // 测试无效JSON
        assertFalse(MCPJsonUtils.isValidJson("{"));  // 不完整的对象
        assertFalse(MCPJsonUtils.isValidJson("{invalid}"));
        assertFalse(MCPJsonUtils.isValidJson("{\"test\": }"));
        assertFalse(MCPJsonUtils.isValidJson(""));
    }

    @Test
    void testPrettyPrint() throws MCPException {
        String compactJson = "{\"name\":\"test\",\"value\":42}";
        
        String prettyJson = MCPJsonUtils.prettyPrint(compactJson);
        assertNotNull(prettyJson);
        assertTrue(prettyJson.contains("\n")); // 应该有换行符
        assertTrue(prettyJson.contains("test"));
        assertTrue(prettyJson.contains("42"));
        
        // 测试无效JSON的美化
        assertThrows(MCPException.class, () -> {
            MCPJsonUtils.prettyPrint("invalid json");
        });
    }

    @Test
    void testComplexObjectSerialization() throws MCPException {
        // 创建复杂的嵌套对象
        MCPPromptResult promptResult = new MCPPromptResult();
        promptResult.setDescription("复杂的提示词结果");
        promptResult.setPromptName("complex_prompt");
        promptResult.setClientName("test_client");
        
        List<MCPMessage> messages = new ArrayList<>();
        messages.add(MCPMessage.system("系统消息"));
        messages.add(MCPMessage.user("用户消息"));
        messages.add(MCPMessage.assistant("助手回复"));
        promptResult.setMessages(messages);
        
        // 序列化
        String json = MCPJsonUtils.toJson(promptResult);
        assertNotNull(json);
        assertTrue(json.contains("复杂的提示词结果"));
        assertTrue(json.contains("系统消息"));
        assertTrue(json.contains("用户消息"));
        assertTrue(json.contains("助手回复"));
        
        // 反序列化
        MCPPromptResult deserialized = MCPJsonUtils.fromJsonToPromptResult(json);
        assertEquals(promptResult.getDescription(), deserialized.getDescription());
        assertEquals(promptResult.getPromptName(), deserialized.getPromptName());
        assertEquals(promptResult.getClientName(), deserialized.getClientName());
        assertEquals(3, deserialized.getMessages().size());
        assertEquals("system", deserialized.getMessages().get(0).getRole());
        assertEquals("user", deserialized.getMessages().get(1).getRole());
        assertEquals("assistant", deserialized.getMessages().get(2).getRole());
    }

    @Test
    void testSerializationExceptionHandling() {
        // 测试序列化null对象
        String json = assertDoesNotThrow(() -> MCPJsonUtils.toJson((MCPTool) null));
        assertEquals("null", json);
    }

    @Test
    void testUnicodeHandling() throws MCPException {
        // 测试Unicode字符的处理
        MCPContent unicodeContent = MCPContent.text("测试 🎉 Unicode 字符 😊");
        
        String json = MCPJsonUtils.toJson(unicodeContent);
        assertNotNull(json);
        
        MCPContent deserialized = MCPJsonUtils.fromJsonToContent(json);
        assertEquals("测试 🎉 Unicode 字符 😊", deserialized.getText());
    }
}