package com.riceawa.mcp.model;

import com.riceawa.mcp.exception.MCPException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCPContent数据模型测试类
 */
class MCPContentTest {

    private MCPContent content;

    @BeforeEach
    void setUp() {
        content = new MCPContent();
    }

    @Test
    void testDefaultConstructor() {
        assertNull(content.getType());
        assertNull(content.getText());
        assertNull(content.getData());
        assertNull(content.getMimeType());
        assertNull(content.getUri());
        assertNotNull(content.getAnnotations());
        assertTrue(content.getAnnotations().isEmpty());
    }

    @Test
    void testConstructorWithTypeAndText() {
        MCPContent testContent = new MCPContent("text", "测试文本");
        
        assertEquals("text", testContent.getType());
        assertEquals("测试文本", testContent.getText());
        assertNull(testContent.getData());
        assertNull(testContent.getMimeType());
        assertNull(testContent.getUri());
    }

    @Test
    void testStaticFactoryMethods() {
        // 测试text方法
        MCPContent textContent = MCPContent.text("这是文本内容");
        assertEquals("text", textContent.getType());
        assertEquals("这是文本内容", textContent.getText());
        assertTrue(textContent.isText());
        
        // 测试image方法
        MCPContent imageContent = MCPContent.image("base64imagedata", "image/jpeg");
        assertEquals("image", imageContent.getType());
        assertEquals("base64imagedata", imageContent.getData());
        assertEquals("image/jpeg", imageContent.getMimeType());
        assertTrue(imageContent.isImage());
        
        // 测试resourceLink方法
        MCPContent linkContent = MCPContent.resourceLink("file:///path/to/file.txt");
        assertEquals("resource_link", linkContent.getType());
        assertEquals("file:///path/to/file.txt", linkContent.getUri());
        assertTrue(linkContent.isResourceLink());
        
        // 测试resource方法
        MCPContent resourceContent = MCPContent.resource("file:///document.pdf", "PDF文档内容");
        assertEquals("resource", resourceContent.getType());
        assertEquals("file:///document.pdf", resourceContent.getUri());
        assertEquals("PDF文档内容", resourceContent.getText());
        assertTrue(resourceContent.isResource());
    }

    @Test
    void testSettersAndGetters() {
        content.setType("audio");
        content.setText("音频转录文本");
        content.setData("base64audiodata");
        content.setMimeType("audio/mp3");
        content.setUri("file:///audio.mp3");
        
        assertEquals("audio", content.getType());
        assertEquals("音频转录文本", content.getText());
        assertEquals("base64audiodata", content.getData());
        assertEquals("audio/mp3", content.getMimeType());
        assertEquals("file:///audio.mp3", content.getUri());
    }

    @Test
    void testAnnotations() {
        Map<String, Object> annotations = new HashMap<>();
        annotations.put("source", "user_upload");
        annotations.put("timestamp", System.currentTimeMillis());
        annotations.put("size", 1024);
        
        content.setAnnotations(annotations);
        
        Map<String, Object> retrieved = content.getAnnotations();
        assertEquals(3, retrieved.size());
        assertEquals("user_upload", retrieved.get("source"));
        assertNotNull(retrieved.get("timestamp"));
        assertEquals(1024, retrieved.get("size"));
        
        // 测试添加单个注解
        content.addAnnotation("author", "test_user");
        assertEquals("test_user", content.getAnnotation("author"));
        
        // 测试null key
        content.addAnnotation(null, "value");
        assertNull(content.getAnnotation(null));
    }

    @Test
    void testTypeCheckers() {
        // 测试文本类型
        content.setType("text");
        assertTrue(content.isText());
        assertFalse(content.isImage());
        assertFalse(content.isAudio());
        assertFalse(content.isResourceLink());
        assertFalse(content.isResource());
        
        // 测试图片类型
        content.setType("image");
        assertFalse(content.isText());
        assertTrue(content.isImage());
        assertFalse(content.isAudio());
        assertFalse(content.isResourceLink());
        assertFalse(content.isResource());
        
        // 测试音频类型
        content.setType("audio");
        assertFalse(content.isText());
        assertFalse(content.isImage());
        assertTrue(content.isAudio());
        assertFalse(content.isResourceLink());
        assertFalse(content.isResource());
        
        // 测试资源链接类型
        content.setType("resource_link");
        assertFalse(content.isText());
        assertFalse(content.isImage());
        assertFalse(content.isAudio());
        assertTrue(content.isResourceLink());
        assertFalse(content.isResource());
        
        // 测试资源类型
        content.setType("resource");
        assertFalse(content.isText());
        assertFalse(content.isImage());
        assertFalse(content.isAudio());
        assertFalse(content.isResourceLink());
        assertTrue(content.isResource());
    }

    @Test
    void testIsValid() {
        // 无效：没有类型
        assertFalse(content.isValid());
        
        // 无效：空类型
        content.setType("");
        assertFalse(content.isValid());
        
        // 无效：空格类型
        content.setType("   ");
        assertFalse(content.isValid());
        
        // 文本类型验证
        content.setType("text");
        assertFalse(content.isValid()); // 没有文本内容
        content.setText("有效文本");
        assertTrue(content.isValid());
        
        // 图片类型验证
        content = new MCPContent();
        content.setType("image");
        assertFalse(content.isValid()); // 没有数据和MIME类型
        content.setData("base64data");
        assertFalse(content.isValid()); // 没有MIME类型
        content.setMimeType("image/png");
        assertTrue(content.isValid());
        
        // 音频类型验证
        content = new MCPContent();
        content.setType("audio");
        content.setData("audiodata");
        content.setMimeType("audio/mp3");
        assertTrue(content.isValid());
        
        // 资源链接类型验证
        content = new MCPContent();
        content.setType("resource_link");
        assertFalse(content.isValid()); // 没有URI
        content.setUri("");
        assertFalse(content.isValid()); // 空URI
        content.setUri("file:///test.txt");
        assertTrue(content.isValid());
        
        // 资源类型验证
        content = new MCPContent();
        content.setType("resource");
        content.setUri("file:///document.pdf");
        assertTrue(content.isValid());
        
        // 未知类型验证（允许）
        content = new MCPContent();
        content.setType("unknown_type");
        assertTrue(content.isValid());
    }

    @Test
    void testGetContentString() {
        // 文本内容
        content.setType("text");
        content.setText("测试文本");
        assertEquals("测试文本", content.getContentString());
        
        // 资源内容
        content.setType("resource");
        content.setUri("file:///test.txt");
        content.setText("资源文本");
        assertEquals("资源文本", content.getContentString());
        
        // 资源链接内容
        content.setType("resource_link");
        content.setUri("http://example.com/file.txt");
        assertEquals("http://example.com/file.txt", content.getContentString());
        
        // 图片内容
        content.setType("image");
        content.setMimeType("image/png");
        String imageString = content.getContentString();
        assertTrue(imageString.contains("[image:"));
        assertTrue(imageString.contains("image/png"));
        
        // 音频内容
        content.setType("audio");
        content.setMimeType("audio/mp3");
        String audioString = content.getContentString();
        assertTrue(audioString.contains("[audio:"));
        assertTrue(audioString.contains("audio/mp3"));
        
        // 未知类型
        content.setType("unknown");
        String unknownString = content.getContentString();
        assertTrue(unknownString.contains("[unknown content]"));
        
        // null类型
        content.setType(null);
        String nullString = content.getContentString();
        assertTrue(nullString.contains("[unknown content]"));
    }

    @Test
    void testToString() {
        content.setType("text");
        content.setText("测试文本");
        content.setUri("file:///test.txt");
        
        String result = content.toString();
        assertTrue(result.contains("text"));
        assertTrue(result.contains("hasText=true"));
        assertTrue(result.contains("hasData=false"));
        assertTrue(result.contains("file:///test.txt"));
    }

    @Test
    void testJsonSerialization() throws MCPException {
        content.setType("text");
        content.setText("测试文本内容");
        content.setUri("file:///test.txt");
        
        Map<String, Object> annotations = new HashMap<>();
        annotations.put("version", "1.0");
        content.setAnnotations(annotations);
        
        // 测试序列化
        String json = content.toJson();
        assertNotNull(json);
        assertTrue(json.contains("text"));
        assertTrue(json.contains("测试文本内容"));
        
        // 测试反序列化
        MCPContent deserialized = MCPContent.fromJson(json);
        assertEquals(content.getType(), deserialized.getType());
        assertEquals(content.getText(), deserialized.getText());
        assertEquals(content.getUri(), deserialized.getUri());
        
        // 测试深度拷贝
        MCPContent copy = content.deepCopy();
        assertEquals(content.getType(), copy.getType());
        assertEquals(content.getText(), copy.getText());
        assertNotSame(content, copy);
    }

    @Test
    void testJsonSerializationWithComplexContent() throws MCPException {
        content.setType("image");
        content.setData("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==");
        content.setMimeType("image/png");
        
        String json = content.toJson();
        assertNotNull(json);
        
        MCPContent deserialized = MCPContent.fromJson(json);
        assertEquals(content.getType(), deserialized.getType());
        assertEquals(content.getData(), deserialized.getData());
        assertEquals(content.getMimeType(), deserialized.getMimeType());
    }

    @Test
    void testJsonSerializationWithInvalidJson() {
        assertThrows(MCPException.class, () -> {
            MCPContent.fromJson("invalid json");
        });
        
        assertThrows(MCPException.class, () -> {
            MCPContent.fromJson("null");
        });
    }

    @Test
    void testImmutableAnnotations() {
        Map<String, Object> annotations = new HashMap<>();
        annotations.put("test", "value");
        content.setAnnotations(annotations);
        
        // 修改原始map不应影响内容的注解
        annotations.put("new", "value");
        assertFalse(content.getAnnotations().containsKey("new"));
        
        // 修改返回的map不应影响内容的注解
        Map<String, Object> retrieved = content.getAnnotations();
        retrieved.put("another", "value");
        assertFalse(content.getAnnotations().containsKey("another"));
    }

    @Test
    void testNullHandling() {
        // 测试设置null值
        content.setType(null);
        content.setText(null);
        content.setData(null);
        content.setMimeType(null);
        content.setUri(null);
        content.setAnnotations(null);
        
        assertNull(content.getType());
        assertNull(content.getText());
        assertNull(content.getData());
        assertNull(content.getMimeType());
        assertNull(content.getUri());
        assertNotNull(content.getAnnotations());
        assertTrue(content.getAnnotations().isEmpty());
    }

    @Test
    void testEdgeCases() {
        // 测试空字符串处理
        content.setType("");
        content.setText("");
        content.setData("");
        content.setMimeType("");
        content.setUri("");
        
        assertEquals("", content.getType());
        assertEquals("", content.getText());
        assertEquals("", content.getData());
        assertEquals("", content.getMimeType());
        assertEquals("", content.getUri());
        
        // 测试只有空格的字符串
        content.setType("   ");
        assertFalse(content.isValid());
        
        content.setType("text");
        content.setText("   ");
        assertTrue(content.isValid()); // 有内容就是有效的，即使只是空格
    }
}