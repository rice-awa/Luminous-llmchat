package com.riceawa.mcp.util;

import com.google.gson.JsonObject;
import com.riceawa.mcp.exception.MCPException;
import com.riceawa.mcp.model.*;
import com.riceawa.mcp.util.MCPModelUtils.ValidationResult;
import com.riceawa.mcp.util.MCPModelUtils.ValidationStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCPModelUtils工具类测试
 */
class MCPModelUtilsTest {

    private MCPTool validTool;
    private MCPTool invalidTool;
    private MCPContent validContent;
    private MCPContent invalidContent;
    private JsonObject validSchema;

    @BeforeEach
    void setUp() {
        // 设置有效的工具
        validTool = new MCPTool("valid_tool", "有效工具", "这是一个有效的工具");
        validTool.setClientName("test_client");
        
        validSchema = new JsonObject();
        validSchema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "string");
        properties.add("filename", prop);
        validSchema.add("properties", properties);
        validTool.setInputSchema(validSchema);
        
        // 设置无效的工具
        invalidTool = new MCPTool();
        // 没有设置名称，所以无效
        
        // 设置有效的内容
        validContent = MCPContent.text("有效的文本内容");
        
        // 设置无效的内容
        invalidContent = new MCPContent();
        invalidContent.setType("image"); // 图片类型但没有数据
    }

    @Test
    void testValidateValidTool() {
        ValidationResult result = MCPModelUtils.validateTool(validTool);
        
        assertTrue(result.isValid());
        assertEquals(0, result.getErrors().size());
        assertEquals("验证通过", result.getErrorSummary());
    }

    @Test
    void testValidateInvalidTool() {
        ValidationResult result = MCPModelUtils.validateTool(invalidTool);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().size() > 0);
        assertTrue(result.getErrorSummary().contains("工具名称不能为空"));
    }

    @Test
    void testValidateNullTool() {
        ValidationResult result = MCPModelUtils.validateTool(null);
        
        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("工具对象不能为null"));
    }

    @Test
    void testValidateToolWithInvalidName() {
        MCPTool tool = new MCPTool();
        tool.setName("123invalid"); // 不能以数字开头
        
        ValidationResult result = MCPModelUtils.validateTool(tool);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                  .anyMatch(error -> error.contains("工具名称格式无效")));
    }

    @Test
    void testValidateToolWithEmptyStrings() {
        MCPTool tool = new MCPTool();
        tool.setName("valid_name");
        tool.setTitle("");
        tool.setDescription("   ");
        tool.setClientName("");
        
        ValidationResult result = MCPModelUtils.validateTool(tool);
        
        assertTrue(result.isValid()); // 仍然有效，因为只有名称是必需的
        assertTrue(result.hasWarnings());
        assertTrue(result.getWarnings().size() >= 2); // 应该有关于空字符串的警告
    }

    @Test
    void testValidateValidContent() {
        ValidationResult result = MCPModelUtils.validateContent(validContent);
        
        assertTrue(result.isValid());
        assertEquals(0, result.getErrors().size());
    }

    @Test
    void testValidateInvalidContent() {
        ValidationResult result = MCPModelUtils.validateContent(invalidContent);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().size() > 0);
        assertTrue(result.getErrors().stream()
                  .anyMatch(error -> error.contains("必须包含data字段") || 
                                   error.contains("必须包含mimeType字段")));
    }

    @Test
    void testValidateContentTypes() {
        // 测试文本内容
        MCPContent textContent = new MCPContent();
        textContent.setType("text");
        textContent.setText("有效文本");
        assertTrue(MCPModelUtils.validateContent(textContent).isValid());
        
        // 测试无效文本内容
        textContent.setText(null);
        assertFalse(MCPModelUtils.validateContent(textContent).isValid());
        
        // 测试图片内容
        MCPContent imageContent = new MCPContent();
        imageContent.setType("image");
        imageContent.setData("base64data");
        imageContent.setMimeType("image/png");
        assertTrue(MCPModelUtils.validateContent(imageContent).isValid());
        
        // 测试资源链接内容
        MCPContent linkContent = new MCPContent();
        linkContent.setType("resource_link");
        linkContent.setUri("file:///test.txt");
        assertTrue(MCPModelUtils.validateContent(linkContent).isValid());
        
        // 测试无效URI
        linkContent.setUri("invalid-uri");
        assertFalse(MCPModelUtils.validateContent(linkContent).isValid());
    }

    @Test
    void testValidateResource() {
        MCPResource validResource = new MCPResource("file:///test.txt", "test.txt");
        validResource.setMimeType("text/plain");
        
        ValidationResult result = MCPModelUtils.validateResource(validResource);
        assertTrue(result.isValid());
        
        // 测试无效资源
        MCPResource invalidResource = new MCPResource();
        // 没有URI
        result = MCPModelUtils.validateResource(invalidResource);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                  .anyMatch(error -> error.contains("资源URI不能为空")));
    }

    @Test
    void testValidatePrompt() {
        MCPPrompt validPrompt = new MCPPrompt("valid_prompt", "有效提示词", "描述");
        validPrompt.setArgumentSchema(validSchema);
        
        ValidationResult result = MCPModelUtils.validatePrompt(validPrompt);
        assertTrue(result.isValid());
        
        // 测试无效提示词
        MCPPrompt invalidPrompt = new MCPPrompt();
        // 没有名称
        result = MCPModelUtils.validatePrompt(invalidPrompt);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                  .anyMatch(error -> error.contains("提示词名称不能为空")));
    }

    @Test
    void testValidateJsonSchema() {
        // 测试有效schema
        ValidationResult result = MCPModelUtils.validateJsonSchema(validSchema, "测试Schema");
        assertTrue(result.isValid());
        
        // 测试没有type字段的schema
        JsonObject schemaWithoutType = new JsonObject();
        schemaWithoutType.addProperty("description", "测试");
        result = MCPModelUtils.validateJsonSchema(schemaWithoutType, "无类型Schema");
        assertTrue(result.isValid()); // 仍然有效，但会有警告
        assertTrue(result.hasWarnings());
        
        // 测试null schema
        result = MCPModelUtils.validateJsonSchema(null, "空Schema");
        assertFalse(result.isValid());
    }

    @Test
    void testCleanTool() throws MCPException {
        MCPTool dirtyTool = new MCPTool();
        dirtyTool.setName("  clean_me  ");
        dirtyTool.setTitle("");
        dirtyTool.setDescription("   ");
        dirtyTool.setClientName("test_client");
        
        Map<String, Object> dirtyAnnotations = new HashMap<>();
        dirtyAnnotations.put("  key1  ", "value1");
        dirtyAnnotations.put("", "empty_key");
        dirtyAnnotations.put("key2", null);
        dirtyAnnotations.put("key3", "value3");
        dirtyTool.setAnnotations(dirtyAnnotations);
        
        MCPTool cleaned = MCPModelUtils.cleanTool(dirtyTool);
        
        assertEquals("clean_me", cleaned.getName());
        assertNull(cleaned.getTitle()); // 空字符串应该变为null
        assertNull(cleaned.getDescription()); // 只有空格的字符串应该变为null
        assertEquals("test_client", cleaned.getClientName());
        
        Map<String, Object> cleanedAnnotations = cleaned.getAnnotations();
        assertEquals(2, cleanedAnnotations.size()); // 只保留有效的注解
        assertTrue(cleanedAnnotations.containsKey("key1"));
        assertTrue(cleanedAnnotations.containsKey("key3"));
        assertFalse(cleanedAnnotations.containsKey(""));
        assertFalse(cleanedAnnotations.containsKey("key2"));
    }

    @Test
    void testConvertContentType() throws MCPException {
        // 测试文本到资源链接的转换
        MCPContent source = MCPContent.text("测试文本");
        source.setUri("file:///test.txt");
        
        MCPContent converted = MCPModelUtils.convertContentType(source, "resource_link");
        assertEquals("resource_link", converted.getType());
        assertEquals("file:///test.txt", converted.getUri());
        
        // 测试任意类型到文本的转换
        MCPContent imageSource = MCPContent.image("data", "image/png");
        converted = MCPModelUtils.convertContentType(imageSource, "text");
        assertEquals("text", converted.getType());
        assertNotNull(converted.getText());
        
        // 测试转换到相同类型
        MCPContent sameType = MCPModelUtils.convertContentType(source, "text");
        assertSame(source, sameType);
        
        // 测试无效转换
        assertThrows(MCPException.class, () -> {
            MCPModelUtils.convertContentType(validContent, "unsupported_type");
        });
        
        // 测试缺少URI的资源链接转换
        MCPContent noUri = MCPContent.text("无URI文本");
        assertThrows(MCPException.class, () -> {
            MCPModelUtils.convertContentType(noUri, "resource_link");
        });
    }

    @Test
    void testUtilityMethods() {
        // 测试isValidString
        assertTrue(MCPModelUtils.isValidString("有效字符串"));
        assertFalse(MCPModelUtils.isValidString(null));
        assertFalse(MCPModelUtils.isValidString(""));
        assertFalse(MCPModelUtils.isValidString("   "));
        
        // 测试isValidUri
        assertTrue(MCPModelUtils.isValidUri("file:///test.txt"));
        assertTrue(MCPModelUtils.isValidUri("http://example.com"));
        assertTrue(MCPModelUtils.isValidUri("https://example.com/path"));
        assertFalse(MCPModelUtils.isValidUri("invalid-uri"));
        assertFalse(MCPModelUtils.isValidUri(""));
        assertFalse(MCPModelUtils.isValidUri(null));
        
        // 测试cleanString
        assertEquals("clean", MCPModelUtils.cleanString("  clean  "));
        assertNull(MCPModelUtils.cleanString(""));
        assertNull(MCPModelUtils.cleanString("   "));
        assertNull(MCPModelUtils.cleanString(null));
        
        // 测试cleanAnnotations
        Map<String, Object> dirtyAnnotations = new HashMap<>();
        dirtyAnnotations.put("  key1  ", "value1");
        dirtyAnnotations.put("", "empty");
        dirtyAnnotations.put("key2", null);
        dirtyAnnotations.put("key3", "value3");
        
        Map<String, Object> cleaned = MCPModelUtils.cleanAnnotations(dirtyAnnotations);
        assertEquals(2, cleaned.size());
        assertTrue(cleaned.containsKey("key1"));
        assertTrue(cleaned.containsKey("key3"));
        
        // 测试null和空注解
        assertTrue(MCPModelUtils.cleanAnnotations(null).isEmpty());
        assertTrue(MCPModelUtils.cleanAnnotations(new HashMap<>()).isEmpty());
    }

    @Test
    void testBatchValidation() {
        List<MCPTool> tools = Arrays.asList(validTool, invalidTool);
        Map<MCPTool, ValidationResult> results = MCPModelUtils.validateTools(tools);
        
        assertEquals(2, results.size());
        assertTrue(results.get(validTool).isValid());
        assertFalse(results.get(invalidTool).isValid());
        
        // 测试null列表
        assertTrue(MCPModelUtils.validateTools(null).isEmpty());
    }

    @Test
    void testValidationStatistics() {
        Map<MCPTool, ValidationResult> results = new HashMap<>();
        results.put(validTool, MCPModelUtils.validateTool(validTool));
        results.put(invalidTool, MCPModelUtils.validateTool(invalidTool));
        
        ValidationStatistics stats = MCPModelUtils.getValidationStatistics(results);
        
        assertEquals(2, stats.getTotalItems());
        assertEquals(1, stats.getValidItems());
        assertEquals(1, stats.getInvalidItems());
        assertEquals(50.0, stats.getValidPercentage(), 0.1);
        assertTrue(stats.getTotalErrors() > 0);
        
        String statsString = stats.toString();
        assertNotNull(statsString);
        assertTrue(statsString.contains("total=2"));
        assertTrue(statsString.contains("valid=1"));
    }

    @Test
    void testValidationResult() {
        ValidationResult result = new ValidationResult();
        
        // 测试初始状态
        assertTrue(result.isValid());
        assertFalse(result.hasWarnings());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getWarnings().isEmpty());
        
        // 添加错误
        result.addError("测试错误");
        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrorSummary().contains("测试错误"));
        
        // 添加警告
        result.addWarning("测试警告");
        assertTrue(result.hasWarnings());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarningSummary().contains("测试警告"));
        
        // 测试合并
        ValidationResult other = new ValidationResult();
        other.addError("其他错误");
        other.addWarning("其他警告");
        
        result.merge(other);
        assertEquals(2, result.getErrors().size());
        assertEquals(2, result.getWarnings().size());
        
        // 测试toString
        String resultString = result.toString();
        assertNotNull(resultString);
        assertTrue(resultString.contains("valid=false"));
        assertTrue(resultString.contains("errors=2"));
        assertTrue(resultString.contains("warnings=2"));
    }

    @Test
    void testValidationResultSummaries() {
        ValidationResult result = new ValidationResult();
        
        // 测试无错误无警告的情况
        assertEquals("验证通过", result.getErrorSummary());
        assertEquals("无警告", result.getWarningSummary());
        
        // 测试有错误的情况
        result.addError("错误1");
        result.addError("错误2");
        String errorSummary = result.getErrorSummary();
        assertTrue(errorSummary.contains("验证失败"));
        assertTrue(errorSummary.contains("错误1"));
        assertTrue(errorSummary.contains("错误2"));
        
        // 测试有警告的情况
        result.addWarning("警告1");
        result.addWarning("警告2");
        String warningSummary = result.getWarningSummary();
        assertTrue(warningSummary.contains("警告"));
        assertTrue(warningSummary.contains("警告1"));
        assertTrue(warningSummary.contains("警告2"));
    }

    @Test
    void testEdgeCasesValidation() {
        // 测试带有无效MIME类型的资源
        MCPResource resource = new MCPResource("file:///test.txt", "test");
        resource.setMimeType("invalid-mime-type"); // 没有斜杠，确实无效
        ValidationResult result = MCPModelUtils.validateResource(resource);
        assertFalse(result.isValid());
        
        // 测试带有无效MIME类型的内容
        MCPContent content = new MCPContent();
        content.setType("image");
        content.setData("data");
        content.setMimeType("invalid-mime-type"); // 没有斜杠，确实无效
        result = MCPModelUtils.validateContent(content);
        assertFalse(result.isValid());
        
        // 测试空统计信息
        Map<MCPTool, ValidationResult> emptyResults = new HashMap<>();
        ValidationStatistics emptyStats = MCPModelUtils.getValidationStatistics(emptyResults);
        assertEquals(0, emptyStats.getTotalItems());
        assertEquals(0.0, emptyStats.getValidPercentage(), 0.1);
    }
}