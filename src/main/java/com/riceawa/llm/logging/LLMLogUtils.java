package com.riceawa.llm.logging;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.riceawa.llm.core.LLMConfig;
import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.core.LLMResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * LLM日志工具类
 * 提供LLM请求响应日志记录的便捷方法和工具函数
 */
public class LLMLogUtils {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .setDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
            .create();
    
    private static final Pattern API_KEY_PATTERN = Pattern.compile("Bearer\\s+([a-zA-Z0-9\\-_]+)", Pattern.CASE_INSENSITIVE);
    private static final String MASKED_API_KEY = "Bearer ***MASKED***";
    
    /**
     * 创建LLM请求日志条目构建器
     */
    public static LLMRequestLogEntry.Builder createRequestLogBuilder(String requestId) {
        return new LLMRequestLogEntry.Builder()
                .requestId(requestId)
                .timestamp(LocalDateTime.now());
    }
    
    /**
     * 创建LLM响应日志条目构建器
     */
    public static LLMResponseLogEntry.Builder createResponseLogBuilder(String responseId, String requestId) {
        return new LLMResponseLogEntry.Builder()
                .responseId(responseId)
                .requestId(requestId)
                .timestamp(LocalDateTime.now());
    }
    
    /**
     * 生成唯一的响应ID
     */
    public static String generateResponseId() {
        return "resp_" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * 记录LLM请求日志
     */
    public static void logRequest(LLMRequestLogEntry requestLog) {
        LogManager.getInstance().llmRequest("LLM Request", requestLog.toJsonString());
    }
    
    /**
     * 记录LLM响应日志
     */
    public static void logResponse(LLMResponseLogEntry responseLog) {
        LogManager.getInstance().llmRequest("LLM Response", responseLog.toJsonString());
    }
    
    /**
     * 将对象转换为JSON字符串
     */
    public static String toJsonString(Object obj) {
        try {
            return GSON.toJson(obj);
        } catch (Exception e) {
            return "{\"error\":\"Failed to serialize to JSON: " + e.getMessage() + "\"}";
        }
    }
    
    /**
     * 脱敏处理HTTP头部信息
     * 隐藏API密钥等敏感信息
     */
    public static Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        if (headers == null) {
            return new HashMap<>();
        }
        
        Map<String, String> sanitized = new HashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            if ("Authorization".equalsIgnoreCase(key) && value != null) {
                // 脱敏API密钥
                value = API_KEY_PATTERN.matcher(value).replaceAll(MASKED_API_KEY);
            }
            
            sanitized.put(key, value);
        }
        
        return sanitized;
    }
    
    /**
     * 脱敏处理JSON字符串中的敏感信息
     */
    public static String sanitizeJsonString(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return jsonString;
        }
        
        try {
            JsonElement element = JsonParser.parseString(jsonString);
            if (element.isJsonObject()) {
                JsonObject jsonObject = element.getAsJsonObject();
                sanitizeJsonObject(jsonObject);
                return GSON.toJson(jsonObject);
            }
        } catch (Exception e) {
            // 如果解析失败，返回原始字符串
            return jsonString;
        }
        
        return jsonString;
    }
    
    /**
     * 递归脱敏JSON对象
     */
    private static void sanitizeJsonObject(JsonObject jsonObject) {
        // 这里可以添加更多需要脱敏的字段
        String[] sensitiveFields = {"api_key", "apiKey", "authorization", "token", "secret"};
        
        for (String field : sensitiveFields) {
            if (jsonObject.has(field)) {
                jsonObject.addProperty(field, "***MASKED***");
            }
        }
        
        // 递归处理嵌套对象
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonObject()) {
                sanitizeJsonObject(value.getAsJsonObject());
            }
        }
    }
    
    /**
     * 估算消息列表的token数量（简单估算）
     */
    public static int estimateTokens(List<LLMMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        
        int totalTokens = 0;
        for (LLMMessage message : messages) {
            if (message.getContent() != null) {
                // 简单估算：平均每4个字符约等于1个token
                totalTokens += message.getContent().length() / 4;
            }
            // 为角色和其他元数据添加固定开销
            totalTokens += 10;
        }
        
        return totalTokens;
    }
    
    /**
     * 创建请求元数据
     */
    public static Map<String, Object> createRequestMetadata(String playerName, String playerUuid, 
                                                           String serviceName, int messageCount) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("player_name", playerName);
        metadata.put("player_uuid", playerUuid);
        metadata.put("service_name", serviceName);
        metadata.put("message_count", messageCount);
        metadata.put("timestamp", LocalDateTime.now().toString());
        return metadata;
    }
    
    /**
     * 创建响应元数据
     */
    public static Map<String, Object> createResponseMetadata(long responseTimeMs, boolean success, 
                                                           String model, Integer totalTokens) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("response_time_ms", responseTimeMs);
        metadata.put("success", success);
        metadata.put("timestamp", LocalDateTime.now().toString());
        
        if (model != null) {
            metadata.put("model", model);
        }
        if (totalTokens != null) {
            metadata.put("total_tokens", totalTokens);
        }
        
        return metadata;
    }
    
    /**
     * 检查是否应该记录完整的请求/响应体
     * 基于配置和内容大小决定
     */
    public static boolean shouldLogFullContent(String content, int maxContentLength) {
        if (content == null) {
            return true;
        }
        return content.length() <= maxContentLength;
    }
    
    /**
     * 截断过长的内容
     */
    public static String truncateContent(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }
        
        return content.substring(0, maxLength) + "... [TRUNCATED]";
    }
    
    /**
     * 格式化响应时间为可读字符串
     */
    public static String formatResponseTime(long responseTimeMs) {
        if (responseTimeMs < 1000) {
            return responseTimeMs + "ms";
        } else if (responseTimeMs < 60000) {
            return String.format("%.2fs", responseTimeMs / 1000.0);
        } else {
            long minutes = responseTimeMs / 60000;
            long seconds = (responseTimeMs % 60000) / 1000;
            return String.format("%dm %ds", minutes, seconds);
        }
    }
}
