package com.riceawa.llm.logging;

import com.riceawa.llm.core.LLMConfig;
import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.core.LLMResponse;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LLM日志工具类测试
 */
public class LLMLogUtilsTest {

    @Test
    public void testCreateRequestLogBuilder() {
        String requestId = "test_req_123";
        LLMRequestLogEntry.Builder builder = LLMLogUtils.createRequestLogBuilder(requestId);
        
        assertNotNull(builder);
        
        LLMRequestLogEntry entry = builder
                .serviceName("TestService")
                .playerName("TestPlayer")
                .build();
        
        assertEquals(requestId, entry.getRequestId());
        assertEquals("TestService", entry.getServiceName());
        assertEquals("TestPlayer", entry.getPlayerName());
        assertNotNull(entry.getTimestamp());
    }

    @Test
    public void testCreateResponseLogBuilder() {
        String responseId = "test_resp_456";
        String requestId = "test_req_123";
        
        LLMResponseLogEntry.Builder builder = LLMLogUtils.createResponseLogBuilder(responseId, requestId);
        
        assertNotNull(builder);
        
        LLMResponseLogEntry entry = builder
                .httpStatusCode(200)
                .success(true)
                .responseTimeMs(1500)
                .build();
        
        assertEquals(responseId, entry.getResponseId());
        assertEquals(requestId, entry.getRequestId());
        assertEquals(200, entry.getHttpStatusCode());
        assertTrue(entry.isSuccess());
        assertEquals(1500, entry.getResponseTimeMs());
    }

    @Test
    public void testGenerateResponseId() {
        String responseId = LLMLogUtils.generateResponseId();
        
        assertNotNull(responseId);
        assertTrue(responseId.startsWith("resp_"));
        assertTrue(responseId.length() > 5);
    }

    @Test
    public void testSanitizeHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer sk-1234567890abcdef");
        headers.put("Content-Type", "application/json");
        headers.put("X-Request-ID", "req_123");
        
        Map<String, String> sanitized = LLMLogUtils.sanitizeHeaders(headers);
        
        assertEquals("Bearer ***MASKED***", sanitized.get("Authorization"));
        assertEquals("application/json", sanitized.get("Content-Type"));
        assertEquals("req_123", sanitized.get("X-Request-ID"));
    }

    @Test
    public void testSanitizeHeadersWithNull() {
        Map<String, String> sanitized = LLMLogUtils.sanitizeHeaders(null);
        
        assertNotNull(sanitized);
        assertTrue(sanitized.isEmpty());
    }

    @Test
    public void testEstimateTokens() {
        LLMMessage message1 = new LLMMessage(LLMMessage.MessageRole.USER, "Hello, how are you?");
        LLMMessage message2 = new LLMMessage(LLMMessage.MessageRole.ASSISTANT, "I'm doing well, thank you!");
        
        int tokens = LLMLogUtils.estimateTokens(Arrays.asList(message1, message2));
        
        assertTrue(tokens > 0);
        // 简单验证估算逻辑：每个消息至少10个token（固定开销）
        assertTrue(tokens >= 20);
    }

    @Test
    public void testEstimateTokensWithEmptyList() {
        int tokens = LLMLogUtils.estimateTokens(Arrays.asList());
        assertEquals(0, tokens);
    }

    @Test
    public void testEstimateTokensWithNull() {
        int tokens = LLMLogUtils.estimateTokens(null);
        assertEquals(0, tokens);
    }

    @Test
    public void testCreateRequestMetadata() {
        Map<String, Object> metadata = LLMLogUtils.createRequestMetadata(
                "TestPlayer", "uuid-123", "OpenAI", 5);
        
        assertEquals("TestPlayer", metadata.get("player_name"));
        assertEquals("uuid-123", metadata.get("player_uuid"));
        assertEquals("OpenAI", metadata.get("service_name"));
        assertEquals(5, metadata.get("message_count"));
        assertNotNull(metadata.get("timestamp"));
    }

    @Test
    public void testCreateResponseMetadata() {
        Map<String, Object> metadata = LLMLogUtils.createResponseMetadata(
                1500L, true, "gpt-3.5-turbo", 200);
        
        assertEquals(1500L, metadata.get("response_time_ms"));
        assertEquals(true, metadata.get("success"));
        assertEquals("gpt-3.5-turbo", metadata.get("model"));
        assertEquals(200, metadata.get("total_tokens"));
        assertNotNull(metadata.get("timestamp"));
    }

    @Test
    public void testShouldLogFullContent() {
        assertTrue(LLMLogUtils.shouldLogFullContent("short content", 1000));
        assertFalse(LLMLogUtils.shouldLogFullContent("very long content that exceeds the limit", 10));
        assertTrue(LLMLogUtils.shouldLogFullContent(null, 1000));
    }

    @Test
    public void testTruncateContent() {
        String content = "This is a very long content that should be truncated";
        String truncated = LLMLogUtils.truncateContent(content, 20);
        
        assertEquals("This is a very long ... [TRUNCATED]", truncated);
        
        String shortContent = "Short";
        String notTruncated = LLMLogUtils.truncateContent(shortContent, 20);
        assertEquals("Short", notTruncated);
        
        assertNull(LLMLogUtils.truncateContent(null, 20));
    }

    @Test
    public void testFormatResponseTime() {
        assertEquals("500ms", LLMLogUtils.formatResponseTime(500));
        assertEquals("1.50s", LLMLogUtils.formatResponseTime(1500));
        assertEquals("1m 30s", LLMLogUtils.formatResponseTime(90000));
    }

    @Test
    public void testToJsonString() {
        Map<String, Object> testObject = new HashMap<>();
        testObject.put("key1", "value1");
        testObject.put("key2", 123);
        
        String json = LLMLogUtils.toJsonString(testObject);
        
        assertNotNull(json);
        assertTrue(json.contains("key1"));
        assertTrue(json.contains("value1"));
        assertTrue(json.contains("key2"));
        assertTrue(json.contains("123"));
    }
}
