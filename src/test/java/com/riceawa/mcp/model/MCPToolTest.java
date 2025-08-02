package com.riceawa.mcp.model;

import com.google.gson.JsonObject;
import com.riceawa.mcp.exception.MCPException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCPTool数据模型测试类
 */
class MCPToolTest {

    private MCPTool tool;
    private JsonObject inputSchema;
    private JsonObject outputSchema;

    @BeforeEach
    void setUp() {
        tool = new MCPTool();
        
        // 创建测试用的JSON Schema
        inputSchema = new JsonObject();
        inputSchema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject nameProperty = new JsonObject();
        nameProperty.addProperty("type", "string");
        nameProperty.addProperty("description", "文件名");
        properties.add("filename", nameProperty);
        inputSchema.add("properties", properties);
        
        outputSchema = new JsonObject();
        outputSchema.addProperty("type", "object");
    }

    @Test
    void testConstructorAndGetters() {
        MCPTool testTool = new MCPTool("test_tool", "测试工具", "这是一个测试工具");
        
        assertEquals("test_tool", testTool.getName());
        assertEquals("测试工具", testTool.getTitle());
        assertEquals("这是一个测试工具", testTool.getDescription());
        assertNull(testTool.getInputSchema());
        assertNull(testTool.getOutputSchema());
        assertNull(testTool.getClientName());
        assertNotNull(testTool.getAnnotations());
        assertTrue(testTool.getAnnotations().isEmpty());
    }

    @Test
    void testSettersAndGetters() {
        tool.setName("file_reader");
        tool.setTitle("文件读取器");
        tool.setDescription("读取文件内容的工具");
        tool.setInputSchema(inputSchema);
        tool.setOutputSchema(outputSchema);
        tool.setClientName("filesystem");
        
        assertEquals("file_reader", tool.getName());
        assertEquals("文件读取器", tool.getTitle());
        assertEquals("读取文件内容的工具", tool.getDescription());
        assertEquals(inputSchema, tool.getInputSchema());
        assertEquals(outputSchema, tool.getOutputSchema());
        assertEquals("filesystem", tool.getClientName());
    }

    @Test
    void testAnnotations() {
        Map<String, Object> annotations = new HashMap<>();
        annotations.put("category", "file");
        annotations.put("permission", "read");
        annotations.put("version", "1.0");
        
        tool.setAnnotations(annotations);
        
        Map<String, Object> retrievedAnnotations = tool.getAnnotations();
        assertEquals(3, retrievedAnnotations.size());
        assertEquals("file", retrievedAnnotations.get("category"));
        assertEquals("read", retrievedAnnotations.get("permission"));
        assertEquals("1.0", retrievedAnnotations.get("version"));
        
        // 测试添加单个注解
        tool.addAnnotation("author", "test");
        assertEquals("test", tool.getAnnotation("author"));
        
        // 测试null key
        tool.addAnnotation(null, "value");
        assertNull(tool.getAnnotation(null));
    }

    @Test
    void testGetFullName() {
        tool.setName("read_file");
        tool.setClientName("filesystem");
        
        assertEquals("mcp_filesystem_read_file", tool.getFullName());
        
        // 测试没有客户端名称的情况
        tool.setClientName(null);
        assertEquals("read_file", tool.getFullName());
        
        tool.setClientName("");
        assertEquals("read_file", tool.getFullName());
    }

    @Test
    void testGetDisplayName() {
        tool.setName("read_file");
        tool.setTitle("文件读取工具");
        
        assertEquals("文件读取工具", tool.getDisplayName());
        
        // 测试没有标题的情况
        tool.setTitle(null);
        assertEquals("read_file", tool.getDisplayName());
        
        tool.setTitle("   ");
        assertEquals("read_file", tool.getDisplayName());
    }

    @Test
    void testIsValid() {
        // 无效的工具（没有名称）
        assertFalse(tool.isValid());
        
        tool.setName("");
        assertFalse(tool.isValid());
        
        tool.setName("   ");
        assertFalse(tool.isValid());
        
        // 有效的工具
        tool.setName("valid_tool");
        assertTrue(tool.isValid());
    }

    @Test
    void testEqualsAndHashCode() {
        MCPTool tool1 = new MCPTool();
        tool1.setName("test_tool");
        
        MCPTool tool2 = new MCPTool();
        tool2.setName("test_tool");
        
        MCPTool tool3 = new MCPTool();
        tool3.setName("other_tool");
        
        // 测试相等性
        assertEquals(tool1, tool2);
        assertNotEquals(tool1, tool3);
        assertNotEquals(tool1, null);
        assertNotEquals(tool1, "not a tool");
        
        // 测试hashCode
        assertEquals(tool1.hashCode(), tool2.hashCode());
        
        // 测试null名称
        MCPTool tool4 = new MCPTool();
        MCPTool tool5 = new MCPTool();
        assertEquals(tool4, tool5);
    }

    @Test
    void testToString() {
        tool.setName("test_tool");
        tool.setTitle("测试工具");
        tool.setClientName("test_client");
        
        String result = tool.toString();
        assertTrue(result.contains("test_tool"));
        assertTrue(result.contains("测试工具"));
        assertTrue(result.contains("test_client"));
    }

    @Test
    void testJsonSerialization() throws MCPException {
        tool.setName("test_tool");
        tool.setTitle("测试工具");
        tool.setDescription("这是一个测试工具");
        tool.setInputSchema(inputSchema);
        tool.setClientName("test_client");
        
        Map<String, Object> annotations = new HashMap<>();
        annotations.put("version", "1.0");
        tool.setAnnotations(annotations);
        
        // 测试序列化
        String json = tool.toJson();
        assertNotNull(json);
        assertTrue(json.contains("test_tool"));
        assertTrue(json.contains("测试工具"));
        
        // 测试反序列化
        MCPTool deserialized = MCPTool.fromJson(json);
        assertEquals(tool.getName(), deserialized.getName());
        assertEquals(tool.getTitle(), deserialized.getTitle());
        assertEquals(tool.getDescription(), deserialized.getDescription());
        assertEquals(tool.getClientName(), deserialized.getClientName());
        assertEquals(tool.getInputSchema(), deserialized.getInputSchema());
        
        // 测试深度拷贝
        MCPTool copy = tool.deepCopy();
        assertEquals(tool.getName(), copy.getName());
        assertEquals(tool.getTitle(), copy.getTitle());
        assertNotSame(tool, copy);
    }

    @Test
    void testJsonSerializationWithInvalidJson() {
        assertThrows(MCPException.class, () -> {
            MCPTool.fromJson("invalid json");
        });
        
        assertThrows(MCPException.class, () -> {
            MCPTool.fromJson("null");
        });
    }

    @Test
    void testImmutableAnnotations() {
        Map<String, Object> annotations = new HashMap<>();
        annotations.put("test", "value");
        tool.setAnnotations(annotations);
        
        // 修改原始map不应影响工具的注解
        annotations.put("new", "value");
        assertFalse(tool.getAnnotations().containsKey("new"));
        
        // 修改返回的map不应影响工具的注解
        Map<String, Object> retrieved = tool.getAnnotations();
        retrieved.put("another", "value");
        assertFalse(tool.getAnnotations().containsKey("another"));
    }

    @Test
    void testNullHandling() {
        // 测试设置null值
        tool.setName(null);
        tool.setTitle(null);
        tool.setDescription(null);
        tool.setInputSchema(null);
        tool.setOutputSchema(null);
        tool.setClientName(null);
        tool.setAnnotations(null);
        
        assertNull(tool.getName());
        assertNull(tool.getTitle());
        assertNull(tool.getDescription());
        assertNull(tool.getInputSchema());
        assertNull(tool.getOutputSchema());
        assertNull(tool.getClientName());
        assertNotNull(tool.getAnnotations());
        assertTrue(tool.getAnnotations().isEmpty());
    }
}