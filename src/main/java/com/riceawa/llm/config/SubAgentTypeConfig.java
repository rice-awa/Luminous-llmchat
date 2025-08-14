package com.riceawa.llm.config;

/**
 * 子代理类型配置基类
 * 所有特定类型的子代理配置都应继承此类
 */
public abstract class SubAgentTypeConfig {
    
    // 子代理类型标识
    protected String agentType;
    
    // 基础配置
    protected boolean enabled = true;
    protected int maxConcurrentInstances = 3;
    protected long instanceTimeoutMs = 120000; // 2分钟默认超时
    protected int maxRetries = 2;
    
    /**
     * 构造函数
     * @param agentType 子代理类型标识
     */
    protected SubAgentTypeConfig(String agentType) {
        this.agentType = agentType;
    }
    
    /**
     * 验证配置有效性
     * 子类应重写此方法以添加特定的验证逻辑
     */
    public boolean isValid() {
        if (agentType == null || agentType.trim().isEmpty()) {
            return false;
        }
        
        if (maxConcurrentInstances <= 0 || maxConcurrentInstances > 10) {
            return false;
        }
        
        if (instanceTimeoutMs <= 0 || instanceTimeoutMs > 600000) { // 最大10分钟
            return false;
        }
        
        if (maxRetries < 0 || maxRetries > 5) {
            return false;
        }
        
        return validateSpecificConfig();
    }
    
    /**
     * 验证特定配置
     * 子类应实现此方法以验证特定的配置项
     */
    protected abstract boolean validateSpecificConfig();
    
    /**
     * 获取配置摘要
     * 用于日志记录和调试
     */
    public String getConfigSummary() {
        return String.format("AgentType: %s, Enabled: %s, MaxInstances: %d, Timeout: %dms, MaxRetries: %d",
                agentType, enabled, maxConcurrentInstances, instanceTimeoutMs, maxRetries);
    }
    
    // Getters and Setters
    
    public String getAgentType() {
        return agentType;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public int getMaxConcurrentInstances() {
        return maxConcurrentInstances;
    }
    
    public void setMaxConcurrentInstances(int maxConcurrentInstances) {
        if (maxConcurrentInstances > 0 && maxConcurrentInstances <= 10) {
            this.maxConcurrentInstances = maxConcurrentInstances;
        }
    }
    
    public long getInstanceTimeoutMs() {
        return instanceTimeoutMs;
    }
    
    public void setInstanceTimeoutMs(long instanceTimeoutMs) {
        if (instanceTimeoutMs > 0 && instanceTimeoutMs <= 600000) {
            this.instanceTimeoutMs = instanceTimeoutMs;
        }
    }
    
    public int getMaxRetries() {
        return maxRetries;
    }
    
    public void setMaxRetries(int maxRetries) {
        if (maxRetries >= 0 && maxRetries <= 5) {
            this.maxRetries = maxRetries;
        }
    }
}