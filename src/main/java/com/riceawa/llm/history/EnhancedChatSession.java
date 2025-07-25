package com.riceawa.llm.history;

import com.riceawa.llm.core.LLMMessage;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 增强版聊天会话，包含更多元数据
 */
public class EnhancedChatSession {
    private final String sessionId;
    private final UUID playerId;
    private final String playerName;
    private final List<LLMMessage> messages;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final String promptTemplate;
    
    // 新增的元数据字段
    private final String provider;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final long responseTimeMs;
    private final int totalTokensUsed;
    private final int promptTokens;
    private final int completionTokens;
    private final boolean functionCallUsed;
    private final List<String> functionsUsed;
    private final Map<String, Object> additionalMetadata;
    private final String sessionStatus; // SUCCESS, ERROR, TIMEOUT, CANCELLED
    private final String errorMessage;
    private final int messageCount;
    private final long sessionDurationMs;

    private EnhancedChatSession(Builder builder) {
        this.sessionId = builder.sessionId;
        this.playerId = builder.playerId;
        this.playerName = builder.playerName;
        this.messages = new ArrayList<>(builder.messages);
        this.startTime = builder.startTime;
        this.endTime = builder.endTime != null ? builder.endTime : LocalDateTime.now();
        this.promptTemplate = builder.promptTemplate;
        this.provider = builder.provider;
        this.model = builder.model;
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.responseTimeMs = builder.responseTimeMs;
        this.totalTokensUsed = builder.totalTokensUsed;
        this.promptTokens = builder.promptTokens;
        this.completionTokens = builder.completionTokens;
        this.functionCallUsed = builder.functionCallUsed;
        this.functionsUsed = new ArrayList<>(builder.functionsUsed);
        this.additionalMetadata = new HashMap<>(builder.additionalMetadata);
        this.sessionStatus = builder.sessionStatus;
        this.errorMessage = builder.errorMessage;
        this.messageCount = this.messages.size();
        this.sessionDurationMs = calculateSessionDuration();
    }

    private long calculateSessionDuration() {
        if (startTime != null && endTime != null) {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
        return 0;
    }

    // Getters
    public String getSessionId() { return sessionId; }
    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public List<LLMMessage> getMessages() { return new ArrayList<>(messages); }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public String getPromptTemplate() { return promptTemplate; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public double getTemperature() { return temperature; }
    public int getMaxTokens() { return maxTokens; }
    public long getResponseTimeMs() { return responseTimeMs; }
    public int getTotalTokensUsed() { return totalTokensUsed; }
    public int getPromptTokens() { return promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public boolean isFunctionCallUsed() { return functionCallUsed; }
    public List<String> getFunctionsUsed() { return new ArrayList<>(functionsUsed); }
    public Map<String, Object> getAdditionalMetadata() { return new HashMap<>(additionalMetadata); }
    public String getSessionStatus() { return sessionStatus; }
    public String getErrorMessage() { return errorMessage; }
    public int getMessageCount() { return messageCount; }
    public long getSessionDurationMs() { return sessionDurationMs; }

    /**
     * 获取会话摘要信息
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Session: ").append(sessionId.substring(0, 8)).append("...");
        summary.append(" | Player: ").append(playerName);
        summary.append(" | Model: ").append(model);
        summary.append(" | Messages: ").append(messageCount);
        summary.append(" | Status: ").append(sessionStatus);
        if (totalTokensUsed > 0) {
            summary.append(" | Tokens: ").append(totalTokensUsed);
        }
        if (responseTimeMs > 0) {
            summary.append(" | Response: ").append(responseTimeMs).append("ms");
        }
        return summary.toString();
    }

    /**
     * 检查会话是否成功
     */
    public boolean isSuccessful() {
        return "SUCCESS".equals(sessionStatus);
    }

    /**
     * 检查是否有错误
     */
    public boolean hasError() {
        return errorMessage != null && !errorMessage.trim().isEmpty();
    }

    /**
     * 获取用户消息数量
     */
    public int getUserMessageCount() {
        return (int) messages.stream()
                .filter(msg -> msg.getRole() == LLMMessage.MessageRole.USER)
                .count();
    }

    /**
     * 获取助手消息数量
     */
    public int getAssistantMessageCount() {
        return (int) messages.stream()
                .filter(msg -> msg.getRole() == LLMMessage.MessageRole.ASSISTANT)
                .count();
    }

    /**
     * Builder类
     */
    public static class Builder {
        private String sessionId;
        private UUID playerId;
        private String playerName;
        private List<LLMMessage> messages = new ArrayList<>();
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String promptTemplate;
        private String provider;
        private String model;
        private double temperature = 0.7;
        private int maxTokens = 0;
        private long responseTimeMs = 0;
        private int totalTokensUsed = 0;
        private int promptTokens = 0;
        private int completionTokens = 0;
        private boolean functionCallUsed = false;
        private List<String> functionsUsed = new ArrayList<>();
        private Map<String, Object> additionalMetadata = new HashMap<>();
        private String sessionStatus = "SUCCESS";
        private String errorMessage;

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder playerId(UUID playerId) {
            this.playerId = playerId;
            return this;
        }

        public Builder playerName(String playerName) {
            this.playerName = playerName;
            return this;
        }

        public Builder messages(List<LLMMessage> messages) {
            this.messages = new ArrayList<>(messages);
            return this;
        }

        public Builder startTime(LocalDateTime startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(LocalDateTime endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder promptTemplate(String promptTemplate) {
            this.promptTemplate = promptTemplate;
            return this;
        }

        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder responseTimeMs(long responseTimeMs) {
            this.responseTimeMs = responseTimeMs;
            return this;
        }

        public Builder totalTokensUsed(int totalTokensUsed) {
            this.totalTokensUsed = totalTokensUsed;
            return this;
        }

        public Builder promptTokens(int promptTokens) {
            this.promptTokens = promptTokens;
            return this;
        }

        public Builder completionTokens(int completionTokens) {
            this.completionTokens = completionTokens;
            return this;
        }

        public Builder functionCallUsed(boolean functionCallUsed) {
            this.functionCallUsed = functionCallUsed;
            return this;
        }

        public Builder functionsUsed(List<String> functionsUsed) {
            this.functionsUsed = new ArrayList<>(functionsUsed);
            return this;
        }

        public Builder addFunctionUsed(String functionName) {
            this.functionsUsed.add(functionName);
            this.functionCallUsed = true;
            return this;
        }

        public Builder additionalMetadata(Map<String, Object> metadata) {
            this.additionalMetadata = new HashMap<>(metadata);
            return this;
        }

        public Builder addMetadata(String key, Object value) {
            this.additionalMetadata.put(key, value);
            return this;
        }

        public Builder sessionStatus(String sessionStatus) {
            this.sessionStatus = sessionStatus;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public EnhancedChatSession build() {
            if (sessionId == null) throw new IllegalArgumentException("Session ID is required");
            if (playerId == null) throw new IllegalArgumentException("Player ID is required");
            if (startTime == null) startTime = LocalDateTime.now();
            return new EnhancedChatSession(this);
        }
    }
}
