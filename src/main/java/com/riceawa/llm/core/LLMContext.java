package com.riceawa.llm.core;

import java.util.HashMap;
import java.util.Map;

/**
 * LLM请求上下文信息
 * 用于传递额外的上下文信息，如玩家信息、会话信息等
 */
public class LLMContext {
    private final String playerName;
    private final String playerUuid;
    private final String sessionId;
    private final Map<String, Object> metadata;

    private LLMContext(Builder builder) {
        this.playerName = builder.playerName;
        this.playerUuid = builder.playerUuid;
        this.sessionId = builder.sessionId;
        this.metadata = new HashMap<>(builder.metadata);
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }

    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    public static class Builder {
        private String playerName;
        private String playerUuid;
        private String sessionId;
        private Map<String, Object> metadata = new HashMap<>();

        public Builder playerName(String playerName) {
            this.playerName = playerName;
            return this;
        }

        public Builder playerUuid(String playerUuid) {
            this.playerUuid = playerUuid;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
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

        public LLMContext build() {
            return new LLMContext(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
