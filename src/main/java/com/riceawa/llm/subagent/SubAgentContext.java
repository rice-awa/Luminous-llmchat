package com.riceawa.llm.subagent;

import com.riceawa.llm.core.LLMContext;

import java.util.HashMap;
import java.util.Map;

/**
 * 子代理上下文
 * 扩展LLMContext，提供子代理特定的上下文信息
 */
public class SubAgentContext extends LLMContext {
    
    private final String subAgentType;
    private final String subAgentId;
    private final Map<String, Object> subAgentMetadata;
    
    private SubAgentContext(Builder builder) {
        super(builder.llmContextBuilder);
        this.subAgentType = builder.subAgentType;
        this.subAgentId = builder.subAgentId;
        this.subAgentMetadata = new HashMap<>(builder.subAgentMetadata);
    }
    
    /**
     * 获取子代理类型
     * 
     * @return 子代理类型
     */
    public String getSubAgentType() {
        return subAgentType;
    }
    
    /**
     * 获取子代理ID
     * 
     * @return 子代理ID
     */
    public String getSubAgentId() {
        return subAgentId;
    }
    
    /**
     * 获取子代理元数据
     * 
     * @return 子代理元数据副本
     */
    public Map<String, Object> getSubAgentMetadata() {
        return new HashMap<>(subAgentMetadata);
    }
    
    /**
     * 获取指定键的子代理元数据
     * 
     * @param key 键
     * @return 元数据值
     */
    public Object getSubAgentMetadata(String key) {
        return subAgentMetadata.get(key);
    }
    
    /**
     * 获取指定键的子代理元数据，带默认值
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return 元数据值或默认值
     */
    @SuppressWarnings("unchecked")
    public <T> T getSubAgentMetadata(String key, T defaultValue) {
        Object value = subAgentMetadata.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return (T) value;
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }
    
    /**
     * 构建器类
     */
    public static class Builder {
        private final LLMContext.Builder llmContextBuilder;
        private String subAgentType;
        private String subAgentId;
        private Map<String, Object> subAgentMetadata = new HashMap<>();
        
        public Builder() {
            this.llmContextBuilder = LLMContext.builder();
        }
        
        public Builder(LLMContext.Builder llmContextBuilder) {
            this.llmContextBuilder = llmContextBuilder;
        }
        
        public Builder subAgentType(String subAgentType) {
            this.subAgentType = subAgentType;
            return this;
        }
        
        public Builder subAgentId(String subAgentId) {
            this.subAgentId = subAgentId;
            return this;
        }
        
        public Builder subAgentMetadata(String key, Object value) {
            this.subAgentMetadata.put(key, value);
            return this;
        }
        
        public Builder subAgentMetadata(Map<String, Object> metadata) {
            if (metadata != null) {
                this.subAgentMetadata.putAll(metadata);
            }
            return this;
        }
        
        // 代理LLMContext.Builder的方法
        public Builder playerName(String playerName) {
            this.llmContextBuilder.playerName(playerName);
            return this;
        }
        
        public Builder playerUuid(String playerUuid) {
            this.llmContextBuilder.playerUuid(playerUuid);
            return this;
        }
        
        public Builder sessionId(String sessionId) {
            this.llmContextBuilder.sessionId(sessionId);
            return this;
        }
        
        public Builder metadata(String key, Object value) {
            this.llmContextBuilder.metadata(key, value);
            return this;
        }
        
        public Builder metadata(Map<String, Object> metadata) {
            this.llmContextBuilder.metadata(metadata);
            return this;
        }
        
        public SubAgentContext build() {
            return new SubAgentContext(this);
        }
    }
    
    /**
     * 创建构建器
     * 
     * @return 构建器实例
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * 从现有LLMContext创建构建器
     * 
     * @param llmContext 现有LLMContext
     * @return 构建器实例
     */
    public static Builder builder(LLMContext llmContext) {
        Builder builder = new Builder();
        
        if (llmContext != null) {
            builder.playerName(llmContext.getPlayerName())
                   .playerUuid(llmContext.getPlayerUuid())
                   .sessionId(llmContext.getSessionId())
                   .metadata(llmContext.getMetadata());
        }
        
        return builder;
    }
}