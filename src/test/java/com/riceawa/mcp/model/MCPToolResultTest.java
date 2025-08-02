package com.riceawa.mcp.model;

import com.riceawa.mcp.exception.MCPException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCPToolResult数据模型测试类
 */
class MCPToolResultTest {

    private MCPToolResult result;
    private List<MCPContent> contentList;

    @BeforeEach
    void setUp() {
        result = new MCPToolResult();
        
        contentList = new ArrayList<>();
        contentList.add(MCPContent.text("第一段文本"));
        contentList.add(MCPContent.text("第二段文本"));
        contentList.add(MCPContent.image("base64data", "image/png"));
    }

    @Test
    void testDefaultConstructor() {
        assertNotNull(result.getContent());
        assertTrue(result.getContent().isEmpty());
        assertNull(result.getStructuredContent());
        assertFalse(result.isError());
        assertNull(result.getErrorMessage());
        assertNull(result.getToolName());
        assertNull(result.getClientName());
    }

    @Test
    void testConstructorWithContent() {
        MCPToolResult testResult = new MCPToolResult(contentList);
        
        assertEquals(3, testResult.getContent().size());
        assertFalse(testResult.isError());
        assertTrue(testResult.isSuccess());
    }

    @Test
    void testStaticFactoryMethods() {
        // 测试success方法
        MCPToolResult successResult = MCPToolResult.success(contentList);
        assertFalse(successResult.isError());
        assertTrue(successResult.isSuccess());
        assertEquals(3, successResult.getContent().size());
        
        // 测试error方法
        MCPToolResult errorResult = MCPToolResult.error("测试错误");
        assertTrue(errorResult.isError());
        assertFalse(errorResult.isSuccess());
        assertEquals("测试错误", errorResult.getErrorMessage());
        assertTrue(errorResult.getContent().isEmpty());
        
        // 测试text方法
        MCPToolResult textResult = MCPToolResult.text("简单文本结果");
        assertFalse(textResult.isError());
        assertEquals(1, textResult.getContent().size());
        assertEquals("简单文本结果", textResult.getTextContent());
    }

    @Test
    void testContentManagement() {
        // 测试设置内容列表
        result.setContent(contentList);
        assertEquals(3, result.getContent().size());
        
        // 测试添加单个内容
        MCPContent newContent = MCPContent.text("新内容");
        result.addContent(newContent);
        assertEquals(4, result.getContent().size());
        
        // 测试添加null内容
        result.addContent(null);
        assertEquals(4, result.getContent().size());
        
        // 测试设置null内容列表
        result.setContent(null);
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void testStructuredContent() {
        Object structuredData = new Object() {
            public String name = "test";
            public int value = 42;
        };
        
        result.setStructuredContent(structuredData);
        assertEquals(structuredData, result.getStructuredContent());
    }

    @Test
    void testErrorHandling() {
        // 测试设置错误
        result.setError(true);
        assertTrue(result.isError());
        assertFalse(result.isSuccess());
        
        // 测试设置错误消息
        result.setErrorMessage("发生了错误");
        assertTrue(result.isError());
        assertEquals("发生了错误", result.getErrorMessage());
        
        // 测试设置非空错误消息会自动设置错误标志
        MCPToolResult newResult = new MCPToolResult();
        newResult.setErrorMessage("自动错误");
        assertTrue(newResult.isError());
        
        // 测试设置空错误消息
        newResult.setErrorMessage("   ");
        assertTrue(newResult.isError());
    }

    @Test
    void testMetadata() {
        result.setToolName("test_tool");
        result.setClientName("test_client");
        
        assertEquals("test_tool", result.getToolName());
        assertEquals("test_client", result.getClientName());
    }

    @Test
    void testGetTextContent() {
        result.setContent(contentList);
        
        String textContent = result.getTextContent();
        assertTrue(textContent.contains("第一段文本"));
        assertTrue(textContent.contains("第二段文本"));
        assertFalse(textContent.contains("base64data")); // 图片内容不应包含在文本中
        
        // 测试空内容列表
        result.setContent(new ArrayList<>());
        assertEquals("", result.getTextContent());
    }

    @Test
    void testHasContent() {
        // 测试没有内容
        assertFalse(result.hasContent());
        
        // 测试有内容列表
        result.setContent(contentList);
        assertTrue(result.hasContent());
        
        // 测试只有结构化内容
        result.setContent(new ArrayList<>());
        result.setStructuredContent("structured data");
        assertTrue(result.hasContent());
        
        // 测试既没有内容列表也没有结构化内容
        result.setStructuredContent(null);
        assertFalse(result.hasContent());
    }

    @Test
    void testToString() {
        // 测试错误结果的toString
        result.setError(true);
        result.setErrorMessage("测试错误");
        String errorString = result.toString();
        assertTrue(errorString.contains("error"));
        assertTrue(errorString.contains("测试错误"));
        
        // 测试成功结果的toString
        result = new MCPToolResult(contentList);
        result.setStructuredContent("structured");
        String successString = result.toString();
        assertTrue(successString.contains("contentCount=3"));
        assertTrue(successString.contains("hasStructured=true"));
    }

    @Test
    void testJsonSerialization() throws MCPException {
        result.setContent(contentList);
        result.setStructuredContent("test data");
        result.setToolName("test_tool");
        result.setClientName("test_client");
        
        // 测试序列化
        String json = result.toJson();
        assertNotNull(json);
        assertTrue(json.contains("test_tool"));
        assertTrue(json.contains("test_client"));
        
        // 测试反序列化
        MCPToolResult deserialized = MCPToolResult.fromJson(json);
        assertEquals(result.getContent().size(), deserialized.getContent().size());
        assertEquals(result.getStructuredContent(), deserialized.getStructuredContent());
        assertEquals(result.getToolName(), deserialized.getToolName());
        assertEquals(result.getClientName(), deserialized.getClientName());
        assertEquals(result.isError(), deserialized.isError());
        
        // 测试深度拷贝
        MCPToolResult copy = result.deepCopy();
        assertEquals(result.getContent().size(), copy.getContent().size());
        assertNotSame(result, copy);
    }

    @Test
    void testJsonSerializationWithError() throws MCPException {
        MCPToolResult errorResult = MCPToolResult.error("序列化测试错误");
        
        String json = errorResult.toJson();
        assertNotNull(json);
        
        MCPToolResult deserialized = MCPToolResult.fromJson(json);
        assertTrue(deserialized.isError());
        assertEquals("序列化测试错误", deserialized.getErrorMessage());
    }

    @Test
    void testJsonSerializationWithInvalidJson() {
        assertThrows(MCPException.class, () -> {
            MCPToolResult.fromJson("invalid json");
        });
        
        assertThrows(MCPException.class, () -> {
            MCPToolResult.fromJson("null");
        });
    }

    @Test
    void testImmutableContent() {
        result.setContent(contentList);
        
        // 修改原始列表不应影响结果的内容
        contentList.add(MCPContent.text("新内容"));
        assertEquals(3, result.getContent().size());
        
        // 修改返回的列表不应影响结果的内容
        List<MCPContent> retrieved = result.getContent();
        retrieved.add(MCPContent.text("另一个新内容"));
        assertEquals(3, result.getContent().size());
    }

    @Test
    void testEdgeCases() {
        // 测试空字符串错误消息
        result.setErrorMessage("");
        assertTrue(result.isError());
        
        // 测试只有空格的错误消息
        result.setErrorMessage("   ");
        assertTrue(result.isError());
        
        // 测试null错误消息
        result.setErrorMessage(null);
        assertTrue(result.isError()); // 错误标志应该保持
        
        // 重置错误状态
        result.setError(false);
        result.setErrorMessage(null);
        assertFalse(result.isError());
    }
}