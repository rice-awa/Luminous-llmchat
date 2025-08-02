package com.riceawa.mcp.model;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP客户端状态模型
 * 表示MCP客户端的连接状态和统计信息
 */
public class MCPClientStatus {
    // 客户端名称
    private String clientName;
    
    // 连接状态
    private ConnectionStatus status;
    
    // 连接时间
    private LocalDateTime connectedAt;
    
    // 最后活动时间
    private LocalDateTime lastActivity;
    
    // 错误信息
    private String errorMessage;
    
    // 工具数量
    private int toolCount = 0;
    
    // 资源数量
    private int resourceCount = 0;
    
    // 提示词数量
    private int promptCount = 0;
    
    // 请求统计
    private long totalRequests = 0;
    private long successfulRequests = 0;
    private long failedRequests = 0;
    
    // 性能统计
    private double averageResponseTime = 0.0;
    private long lastResponseTime = 0;
    
    // 额外属性
    private Map<String, Object> properties = new HashMap<>();

    /**
     * 连接状态枚举
     */
    public enum ConnectionStatus {
        DISCONNECTED("已断开"),
        CONNECTING("连接中"),
        CONNECTED("已连接"),
        ERROR("错误"),
        RECONNECTING("重连中");
        
        private final String displayName;
        
        ConnectionStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }

    public MCPClientStatus() {
        this.status = ConnectionStatus.DISCONNECTED;
    }

    public MCPClientStatus(String clientName) {
        this.clientName = clientName;
        this.status = ConnectionStatus.DISCONNECTED;
    }

    // Getters and Setters
    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public ConnectionStatus getStatus() {
        return status;
    }

    public void setStatus(ConnectionStatus status) {
        this.status = status;
        this.lastActivity = LocalDateTime.now();
    }

    public LocalDateTime getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(LocalDateTime connectedAt) {
        this.connectedAt = connectedAt;
    }

    public LocalDateTime getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(LocalDateTime lastActivity) {
        this.lastActivity = lastActivity;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getToolCount() {
        return toolCount;
    }

    public void setToolCount(int toolCount) {
        this.toolCount = Math.max(0, toolCount);
    }

    public int getResourceCount() {
        return resourceCount;
    }

    public void setResourceCount(int resourceCount) {
        this.resourceCount = Math.max(0, resourceCount);
    }

    public int getPromptCount() {
        return promptCount;
    }

    public void setPromptCount(int promptCount) {
        this.promptCount = Math.max(0, promptCount);
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(long totalRequests) {
        this.totalRequests = Math.max(0, totalRequests);
    }

    public long getSuccessfulRequests() {
        return successfulRequests;
    }

    public void setSuccessfulRequests(long successfulRequests) {
        this.successfulRequests = Math.max(0, successfulRequests);
    }

    public long getFailedRequests() {
        return failedRequests;
    }

    public void setFailedRequests(long failedRequests) {
        this.failedRequests = Math.max(0, failedRequests);
    }

    public double getAverageResponseTime() {
        return averageResponseTime;
    }

    public void setAverageResponseTime(double averageResponseTime) {
        this.averageResponseTime = Math.max(0.0, averageResponseTime);
    }

    public long getLastResponseTime() {
        return lastResponseTime;
    }

    public void setLastResponseTime(long lastResponseTime) {
        this.lastResponseTime = Math.max(0, lastResponseTime);
    }

    public Map<String, Object> getProperties() {
        return new HashMap<>(properties);
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
    }

    public void setProperty(String key, Object value) {
        if (key != null) {
            this.properties.put(key, value);
        }
    }

    public Object getProperty(String key) {
        return properties.get(key);
    }

    /**
     * 标记为已连接
     */
    public void markConnected() {
        this.status = ConnectionStatus.CONNECTED;
        this.connectedAt = LocalDateTime.now();
        this.lastActivity = LocalDateTime.now();
        this.errorMessage = null;
    }

    /**
     * 标记为断开连接
     */
    public void markDisconnected(String reason) {
        this.status = ConnectionStatus.DISCONNECTED;
        this.errorMessage = reason;
        this.lastActivity = LocalDateTime.now();
    }

    /**
     * 标记为错误状态
     */
    public void markError(String errorMessage) {
        this.status = ConnectionStatus.ERROR;
        this.errorMessage = errorMessage;
        this.lastActivity = LocalDateTime.now();
    }

    /**
     * 记录请求
     */
    public void recordRequest(boolean success, long responseTime) {
        this.totalRequests++;
        if (success) {
            this.successfulRequests++;
        } else {
            this.failedRequests++;
        }
        
        this.lastResponseTime = responseTime;
        
        // 更新平均响应时间
        if (this.totalRequests > 0) {
            this.averageResponseTime = (this.averageResponseTime * (this.totalRequests - 1) + responseTime) / this.totalRequests;
        }
        
        this.lastActivity = LocalDateTime.now();
    }

    /**
     * 获取成功率
     */
    public double getSuccessRate() {
        if (totalRequests == 0) {
            return 0.0;
        }
        return (double) successfulRequests / totalRequests * 100.0;
    }

    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        return status == ConnectionStatus.CONNECTED;
    }

    /**
     * 检查是否有错误
     */
    public boolean hasError() {
        return status == ConnectionStatus.ERROR;
    }

    /**
     * 获取状态显示文本
     */
    public String getStatusDisplay() {
        String statusText = status.getDisplayName();
        if (hasError() && errorMessage != null) {
            statusText += " (" + errorMessage + ")";
        }
        return statusText;
    }

    /**
     * 获取连接时长（秒）
     */
    public long getConnectionDurationSeconds() {
        if (connectedAt == null || !isConnected()) {
            return 0;
        }
        return java.time.Duration.between(connectedAt, LocalDateTime.now()).getSeconds();
    }

    @Override
    public String toString() {
        return String.format("MCPClientStatus{client='%s', status=%s, tools=%d, resources=%d, prompts=%d}", 
                           clientName, status, toolCount, resourceCount, promptCount);
    }
}