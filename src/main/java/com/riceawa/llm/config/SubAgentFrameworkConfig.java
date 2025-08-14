package com.riceawa.llm.config;

import java.util.HashMap;
import java.util.Map;

/**
 * 子代理框架配置类
 * 管理通用子代理框架的全局配置
 */
public class SubAgentFrameworkConfig {
    
    // 框架开关
    private boolean enableSubAgentFramework = false;
    
    // 通用队列配置
    private int maxQueueSize = 100;
    private long taskTimeoutMs = 120000; // 2分钟默认超时
    private int maxRetries = 3;
    
    // 错误处理配置
    private long baseRetryDelayMs = 1000; // 基础重试延迟1秒
    private double retryBackoffMultiplier = 2.0; // 指数退避倍数
    private long maxRetryDelayMs = 30000; // 最大重试延迟30秒
    
    // 子代理管理配置
    private int maxConcurrentSubAgents = 5;
    private int maxAgentsPerType = 3; // 每种类型最大代理数
    private long subAgentIdleTimeoutMs = 300000; // 5分钟空闲超时
    private long agentIdleTimeoutMs = 300000; // 代理空闲超时
    private long healthCheckIntervalMs = 60000; // 1分钟健康检查间隔
    private long cleanupIntervalMs = 120000; // 2分钟清理间隔
    
    // 子代理类型特定配置映射
    private Map<String, SubAgentTypeConfig> typeConfigs = new HashMap<>();
    
    /**
     * 创建默认配置
     */
    public static SubAgentFrameworkConfig createDefault() {
        SubAgentFrameworkConfig config = new SubAgentFrameworkConfig();
        
        // 添加默认的搜索子代理配置
        config.typeConfigs.put("INTELLIGENT_SEARCH", IntelligentSearchConfig.createDefault());
        
        return config;
    }
    
    /**
     * 验证配置有效性
     */
    public boolean isValid() {
        if (maxQueueSize <= 0 || maxQueueSize > 1000) {
            return false;
        }
        
        if (taskTimeoutMs <= 0 || taskTimeoutMs > 600000) { // 最大10分钟
            return false;
        }
        
        if (maxRetries < 0 || maxRetries > 10) {
            return false;
        }
        
        if (maxConcurrentSubAgents <= 0 || maxConcurrentSubAgents > 20) {
            return false;
        }
        
        if (subAgentIdleTimeoutMs <= 0 || subAgentIdleTimeoutMs > 3600000) { // 最大1小时
            return false;
        }
        
        if (healthCheckIntervalMs <= 0 || healthCheckIntervalMs > 600000) { // 最大10分钟
            return false;
        }
        
        // 验证所有子代理类型配置
        for (SubAgentTypeConfig typeConfig : typeConfigs.values()) {
            if (!typeConfig.isValid()) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 获取指定类型的子代理配置
     */
    @SuppressWarnings("unchecked")
    public <T extends SubAgentTypeConfig> T getTypeConfig(String agentType, Class<T> configClass) {
        SubAgentTypeConfig config = typeConfigs.get(agentType);
        if (config != null && configClass.isInstance(config)) {
            return (T) config;
        }
        return null;
    }
    
    /**
     * 设置指定类型的子代理配置
     */
    public void setTypeConfig(String agentType, SubAgentTypeConfig config) {
        if (agentType != null && config != null && config.isValid()) {
            typeConfigs.put(agentType, config);
        }
    }
    
    /**
     * 移除指定类型的子代理配置
     */
    public void removeTypeConfig(String agentType) {
        typeConfigs.remove(agentType);
    }
    
    // Getters and Setters
    
    public boolean isEnableSubAgentFramework() {
        return enableSubAgentFramework;
    }
    
    public void setEnableSubAgentFramework(boolean enableSubAgentFramework) {
        this.enableSubAgentFramework = enableSubAgentFramework;
    }
    
    public int getMaxQueueSize() {
        return maxQueueSize;
    }
    
    public void setMaxQueueSize(int maxQueueSize) {
        if (maxQueueSize > 0 && maxQueueSize <= 1000) {
            this.maxQueueSize = maxQueueSize;
        }
    }
    
    public long getTaskTimeoutMs() {
        return taskTimeoutMs;
    }
    
    public void setTaskTimeoutMs(long taskTimeoutMs) {
        if (taskTimeoutMs > 0 && taskTimeoutMs <= 600000) {
            this.taskTimeoutMs = taskTimeoutMs;
        }
    }
    
    public int getMaxRetries() {
        return maxRetries;
    }
    
    public void setMaxRetries(int maxRetries) {
        if (maxRetries >= 0 && maxRetries <= 10) {
            this.maxRetries = maxRetries;
        }
    }
    
    public int getMaxConcurrentSubAgents() {
        return maxConcurrentSubAgents;
    }
    
    public void setMaxConcurrentSubAgents(int maxConcurrentSubAgents) {
        if (maxConcurrentSubAgents > 0 && maxConcurrentSubAgents <= 20) {
            this.maxConcurrentSubAgents = maxConcurrentSubAgents;
        }
    }
    
    public long getSubAgentIdleTimeoutMs() {
        return subAgentIdleTimeoutMs;
    }
    
    public void setSubAgentIdleTimeoutMs(long subAgentIdleTimeoutMs) {
        if (subAgentIdleTimeoutMs > 0 && subAgentIdleTimeoutMs <= 3600000) {
            this.subAgentIdleTimeoutMs = subAgentIdleTimeoutMs;
        }
    }
    
    public long getHealthCheckIntervalMs() {
        return healthCheckIntervalMs;
    }
    
    public void setHealthCheckIntervalMs(long healthCheckIntervalMs) {
        if (healthCheckIntervalMs > 0 && healthCheckIntervalMs <= 600000) {
            this.healthCheckIntervalMs = healthCheckIntervalMs;
        }
    }
    
    public Map<String, SubAgentTypeConfig> getTypeConfigs() {
        return new HashMap<>(typeConfigs);
    }
    
    public void setTypeConfigs(Map<String, SubAgentTypeConfig> typeConfigs) {
        this.typeConfigs = typeConfigs != null ? new HashMap<>(typeConfigs) : new HashMap<>();
    }
    
    public long getBaseRetryDelayMs() {
        return baseRetryDelayMs;
    }
    
    public void setBaseRetryDelayMs(long baseRetryDelayMs) {
        if (baseRetryDelayMs > 0 && baseRetryDelayMs <= 10000) {
            this.baseRetryDelayMs = baseRetryDelayMs;
        }
    }
    
    public double getRetryBackoffMultiplier() {
        return retryBackoffMultiplier;
    }
    
    public void setRetryBackoffMultiplier(double retryBackoffMultiplier) {
        if (retryBackoffMultiplier >= 1.0 && retryBackoffMultiplier <= 5.0) {
            this.retryBackoffMultiplier = retryBackoffMultiplier;
        }
    }
    
    public long getMaxRetryDelayMs() {
        return maxRetryDelayMs;
    }
    
    public void setMaxRetryDelayMs(long maxRetryDelayMs) {
        if (maxRetryDelayMs > 0 && maxRetryDelayMs <= 300000) {
            this.maxRetryDelayMs = maxRetryDelayMs;
        }
    }
    
    public int getMaxAgentsPerType() {
        return maxAgentsPerType;
    }
    
    public void setMaxAgentsPerType(int maxAgentsPerType) {
        if (maxAgentsPerType > 0 && maxAgentsPerType <= 10) {
            this.maxAgentsPerType = maxAgentsPerType;
        }
    }
    
    public long getAgentIdleTimeoutMs() {
        return agentIdleTimeoutMs;
    }
    
    public void setAgentIdleTimeoutMs(long agentIdleTimeoutMs) {
        if (agentIdleTimeoutMs > 0 && agentIdleTimeoutMs <= 3600000) {
            this.agentIdleTimeoutMs = agentIdleTimeoutMs;
        }
    }
    
    public long getCleanupIntervalMs() {
        return cleanupIntervalMs;
    }
    
    public void setCleanupIntervalMs(long cleanupIntervalMs) {
        if (cleanupIntervalMs > 0 && cleanupIntervalMs <= 600000) {
            this.cleanupIntervalMs = cleanupIntervalMs;
        }
    }
}