package com.riceawa.llm.logging;

import com.google.gson.annotations.SerializedName;
import com.riceawa.llm.core.LLMResponse;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.HashMap;

/**
 * LLM响应日志条目
 * 记录完整的LLM API响应信息
 */
public class LLMResponseLogEntry {
    @SerializedName("response_id")
    private final String responseId;
    
    @SerializedName("request_id")
    private final String requestId;
    
    @SerializedName("timestamp")
    private final LocalDateTime timestamp;
    
    @SerializedName("response_time_ms")
    private final long responseTimeMs;
    
    @SerializedName("http_status_code")
    private final int httpStatusCode;
    
    @SerializedName("success")
    private final boolean success;
    
    @SerializedName("error_message")
    private final String errorMessage;
    
    @SerializedName("llm_response")
    private final LLMResponse llmResponse;
    
    @SerializedName("raw_response_json")
    private final String rawResponseJson;
    
    @SerializedName("response_headers")
    private final Map<String, String> responseHeaders;
    
    @SerializedName("content")
    private final String content;
    
    @SerializedName("model")
    private final String model;
    
    @SerializedName("usage")
    private final TokenUsage usage;
    
    @SerializedName("finish_reason")
    private final String finishReason;
    
    @SerializedName("metadata")
    private final Map<String, Object> metadata;

    private LLMResponseLogEntry(Builder builder) {
        this.responseId = builder.responseId;
        this.requestId = builder.requestId;
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
        this.responseTimeMs = builder.responseTimeMs;
        this.httpStatusCode = builder.httpStatusCode;
        this.success = builder.success;
        this.errorMessage = builder.errorMessage;
        this.llmResponse = builder.llmResponse;
        this.rawResponseJson = builder.rawResponseJson;
        this.responseHeaders = new HashMap<>(builder.responseHeaders);
        this.content = builder.content;
        this.model = builder.model;
        this.usage = builder.usage;
        this.finishReason = builder.finishReason;
        this.metadata = new HashMap<>(builder.metadata);
    }

    // Getters
    public String getResponseId() { return responseId; }
    public String getRequestId() { return requestId; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public long getResponseTimeMs() { return responseTimeMs; }
    public int getHttpStatusCode() { return httpStatusCode; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
    public LLMResponse getLlmResponse() { return llmResponse; }
    public String getRawResponseJson() { return rawResponseJson; }
    public Map<String, String> getResponseHeaders() { return new HashMap<>(responseHeaders); }
    public String getContent() { return content; }
    public String getModel() { return model; }
    public TokenUsage getUsage() { return usage; }
    public String getFinishReason() { return finishReason; }
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }

    /**
     * Token使用情况
     */
    public static class TokenUsage {
        @SerializedName("prompt_tokens")
        private final int promptTokens;
        
        @SerializedName("completion_tokens")
        private final int completionTokens;
        
        @SerializedName("total_tokens")
        private final int totalTokens;

        public TokenUsage(int promptTokens, int completionTokens, int totalTokens) {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
        }

        public int getPromptTokens() { return promptTokens; }
        public int getCompletionTokens() { return completionTokens; }
        public int getTotalTokens() { return totalTokens; }
    }

    /**
     * 转换为JSON字符串（用于日志输出）
     */
    public String toJsonString() {
        return LLMLogUtils.toJsonString(this);
    }

    /**
     * 转换为格式化的可读字符串
     */
    public String toFormattedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))).append("] ");
        sb.append("LLM_RESPONSE [").append(responseId).append("] ");
        sb.append("Request: ").append(requestId).append(" ");
        sb.append("Status: ").append(httpStatusCode).append(" ");
        sb.append("Success: ").append(success).append(" ");
        sb.append("Time: ").append(responseTimeMs).append("ms ");
        if (usage != null) {
            sb.append("Tokens: ").append(usage.getTotalTokens()).append(" ");
        }
        if (model != null) {
            sb.append("Model: ").append(model).append(" ");
        }
        if (!success && errorMessage != null) {
            sb.append("Error: ").append(errorMessage);
        }
        return sb.toString();
    }

    public static class Builder {
        private String responseId;
        private String requestId;
        private LocalDateTime timestamp;
        private long responseTimeMs;
        private int httpStatusCode;
        private boolean success;
        private String errorMessage;
        private LLMResponse llmResponse;
        private String rawResponseJson;
        private Map<String, String> responseHeaders = new HashMap<>();
        private String content;
        private String model;
        private TokenUsage usage;
        private String finishReason;
        private Map<String, Object> metadata = new HashMap<>();

        public Builder responseId(String responseId) {
            this.responseId = responseId;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder responseTimeMs(long responseTimeMs) {
            this.responseTimeMs = responseTimeMs;
            return this;
        }

        public Builder httpStatusCode(int httpStatusCode) {
            this.httpStatusCode = httpStatusCode;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder llmResponse(LLMResponse llmResponse) {
            this.llmResponse = llmResponse;
            if (llmResponse != null) {
                this.success = llmResponse.isSuccess();
                this.content = llmResponse.getContent();
                this.model = llmResponse.getModel();
                this.errorMessage = llmResponse.getError();
                
                // 提取token使用情况
                if (llmResponse.getUsage() != null) {
                    LLMResponse.Usage responseUsage = llmResponse.getUsage();
                    this.usage = new TokenUsage(
                        responseUsage.getPromptTokens(),
                        responseUsage.getCompletionTokens(),
                        responseUsage.getTotalTokens()
                    );
                }
                
                // 提取finish reason
                if (llmResponse.getChoices() != null && !llmResponse.getChoices().isEmpty()) {
                    this.finishReason = llmResponse.getChoices().get(0).getFinishReason();
                }
            }
            return this;
        }

        public Builder rawResponseJson(String rawResponseJson) {
            this.rawResponseJson = rawResponseJson;
            return this;
        }

        public Builder responseHeaders(Map<String, String> headers) {
            if (headers != null) {
                this.responseHeaders.putAll(headers);
            }
            return this;
        }

        public Builder responseHeader(String key, String value) {
            this.responseHeaders.put(key, value);
            return this;
        }

        public Builder usage(int promptTokens, int completionTokens, int totalTokens) {
            this.usage = new TokenUsage(promptTokens, completionTokens, totalTokens);
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            if (metadata != null) {
                this.metadata.putAll(metadata);
            }
            return this;
        }

        public LLMResponseLogEntry build() {
            if (responseId == null) throw new IllegalArgumentException("Response ID is required");
            if (requestId == null) throw new IllegalArgumentException("Request ID is required");
            return new LLMResponseLogEntry(this);
        }
    }
}
