package com.riceawa.llm.logging;

import com.google.gson.annotations.SerializedName;
import com.riceawa.llm.core.LLMConfig;
import com.riceawa.llm.core.LLMMessage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * LLM请求日志条目
 * 记录完整的LLM API请求信息
 */
public class LLMRequestLogEntry {
    @SerializedName("request_id")
    private final String requestId;
    
    @SerializedName("timestamp")
    private final LocalDateTime timestamp;
    
    @SerializedName("player_name")
    private final String playerName;
    
    @SerializedName("player_uuid")
    private final String playerUuid;
    
    @SerializedName("service_name")
    private final String serviceName;
    
    @SerializedName("model")
    private final String model;
    
    @SerializedName("messages")
    private final List<LLMMessage> messages;
    
    @SerializedName("config")
    private final LLMConfig config;
    
    @SerializedName("raw_request_json")
    private final String rawRequestJson;
    
    @SerializedName("request_url")
    private final String requestUrl;
    
    @SerializedName("request_headers")
    private final Map<String, String> requestHeaders;
    
    @SerializedName("context_message_count")
    private final int contextMessageCount;
    
    @SerializedName("estimated_tokens")
    private final Integer estimatedTokens;
    
    @SerializedName("metadata")
    private final Map<String, Object> metadata;

    private LLMRequestLogEntry(Builder builder) {
        this.requestId = builder.requestId;
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
        this.playerName = builder.playerName;
        this.playerUuid = builder.playerUuid;
        this.serviceName = builder.serviceName;
        this.model = builder.model;
        this.messages = builder.messages;
        this.config = builder.config;
        this.rawRequestJson = builder.rawRequestJson;
        this.requestUrl = builder.requestUrl;
        this.requestHeaders = new HashMap<>(builder.requestHeaders);
        this.contextMessageCount = builder.contextMessageCount;
        this.estimatedTokens = builder.estimatedTokens;
        this.metadata = new HashMap<>(builder.metadata);
    }

    // Getters
    public String getRequestId() { return requestId; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getPlayerName() { return playerName; }
    public String getPlayerUuid() { return playerUuid; }
    public String getServiceName() { return serviceName; }
    public String getModel() { return model; }
    public List<LLMMessage> getMessages() { return messages; }
    public LLMConfig getConfig() { return config; }
    public String getRawRequestJson() { return rawRequestJson; }
    public String getRequestUrl() { return requestUrl; }
    public Map<String, String> getRequestHeaders() { return new HashMap<>(requestHeaders); }
    public int getContextMessageCount() { return contextMessageCount; }
    public Integer getEstimatedTokens() { return estimatedTokens; }
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }

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
        sb.append("LLM_REQUEST [").append(requestId).append("] ");
        sb.append("Player: ").append(playerName).append(" ");
        sb.append("Service: ").append(serviceName).append(" ");
        sb.append("Model: ").append(model).append(" ");
        sb.append("Messages: ").append(contextMessageCount).append(" ");
        if (estimatedTokens != null) {
            sb.append("Est.Tokens: ").append(estimatedTokens).append(" ");
        }
        return sb.toString();
    }

    public static class Builder {
        private String requestId;
        private LocalDateTime timestamp;
        private String playerName;
        private String playerUuid;
        private String serviceName;
        private String model;
        private List<LLMMessage> messages;
        private LLMConfig config;
        private String rawRequestJson;
        private String requestUrl;
        private Map<String, String> requestHeaders = new HashMap<>();
        private int contextMessageCount;
        private Integer estimatedTokens;
        private Map<String, Object> metadata = new HashMap<>();

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder playerName(String playerName) {
            this.playerName = playerName;
            return this;
        }

        public Builder playerUuid(String playerUuid) {
            this.playerUuid = playerUuid;
            return this;
        }

        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder messages(List<LLMMessage> messages) {
            this.messages = messages;
            this.contextMessageCount = messages != null ? messages.size() : 0;
            return this;
        }

        public Builder config(LLMConfig config) {
            this.config = config;
            if (config != null && this.model == null) {
                this.model = config.getModel();
            }
            return this;
        }

        public Builder rawRequestJson(String rawRequestJson) {
            this.rawRequestJson = rawRequestJson;
            return this;
        }

        public Builder requestUrl(String requestUrl) {
            this.requestUrl = requestUrl;
            return this;
        }

        public Builder requestHeaders(Map<String, String> headers) {
            if (headers != null) {
                this.requestHeaders.putAll(headers);
            }
            return this;
        }

        public Builder requestHeader(String key, String value) {
            this.requestHeaders.put(key, value);
            return this;
        }

        public Builder estimatedTokens(Integer estimatedTokens) {
            this.estimatedTokens = estimatedTokens;
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

        public LLMRequestLogEntry build() {
            if (requestId == null) throw new IllegalArgumentException("Request ID is required");
            if (serviceName == null) throw new IllegalArgumentException("Service name is required");
            return new LLMRequestLogEntry(this);
        }
    }
}
