package com.riceawa.mcp.client;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP客户端状态信息类
 * 记录客户端连接状态、性能指标和健康状态
 */
public class MCPClientStatus {
    
    public enum ConnectionState {
        DISCONNECTED,    // 未连接
        CONNECTING,      // 连接中
        CONNECTED,       // 已连接
        RECONNECTING,    // 重连中
        ERROR,           // 错误状态
        SHUTDOWN         // 已关闭
    }
    
    private final String clientName;
    private ConnectionState state;
    private Instant lastConnectedTime;
    private Instant lastDisconnectedTime;
    private Instant lastHealthCheckTime;
    private String lastError;
    private Exception lastException;
    
    // 性能指标
    private long totalRequests;
    private long successfulRequests;
    private long failedRequests;
    private long averageResponseTimeMs;
    private long lastResponseTimeMs;
    
    // 连接统计
    private int reconnectAttempts;
    private int maxReconnectAttempts;
    private boolean healthCheckPassed;
    
    // 功能状态
    private int availableToolsCount;
    private int availableResourcesCount;
    private int availablePromptsCount;
    
    // 自定义属性
    private final Map<String, Object> customProperties = new ConcurrentHashMap<>();
    
    public MCPClientStatus(String clientName) {
        this.clientName = clientName;
        this.state = ConnectionState.DISCONNECTED;
        this.lastHealthCheckTime = Instant.now();
        this.maxReconnectAttempts = 3;
    }
    
    // Getters and Setters
    public String getClientName() {
        return clientName;
    }
    
    public ConnectionState getState() {
        return state;
    }
    
    public void setState(ConnectionState state) {
        this.state = state;
        if (state == ConnectionState.CONNECTED) {
            this.lastConnectedTime = Instant.now();
            this.reconnectAttempts = 0;
        } else if (state == ConnectionState.DISCONNECTED || state == ConnectionState.ERROR) {
            this.lastDisconnectedTime = Instant.now();
        }
    }
    
    public Instant getLastConnectedTime() {
        return lastConnectedTime;
    }
    
    public void setLastConnectedTime(Instant lastConnectedTime) {
        this.lastConnectedTime = lastConnectedTime;
    }
    
    public Instant getLastDisconnectedTime() {
        return lastDisconnectedTime;
    }
    
    public void setLastDisconnectedTime(Instant lastDisconnectedTime) {
        this.lastDisconnectedTime = lastDisconnectedTime;
    }
    
    public Instant getLastHealthCheckTime() {
        return lastHealthCheckTime;
    }
    
    public void setLastHealthCheckTime(Instant lastHealthCheckTime) {
        this.lastHealthCheckTime = lastHealthCheckTime;
    }
    
    public String getLastError() {
        return lastError;
    }
    
    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
    
    public Exception getLastException() {
        return lastException;
    }
    
    public void setLastException(Exception lastException) {
        this.lastException = lastException;
        if (lastException != null) {
            this.lastError = lastException.getMessage();
        }
    }
    
    public long getTotalRequests() {
        return totalRequests;
    }
    
    public void incrementTotalRequests() {
        this.totalRequests++;
    }
    
    public long getSuccessfulRequests() {
        return successfulRequests;
    }
    
    public void incrementSuccessfulRequests() {
        this.successfulRequests++;
    }
    
    public long getFailedRequests() {
        return failedRequests;
    }
    
    public void incrementFailedRequests() {
        this.failedRequests++;
    }
    
    public long getAverageResponseTimeMs() {
        return averageResponseTimeMs;
    }
    
    public void updateResponseTime(long responseTimeMs) {
        this.lastResponseTimeMs = responseTimeMs;
        // 简单的移动平均计算
        if (this.averageResponseTimeMs == 0) {
            this.averageResponseTimeMs = responseTimeMs;
        } else {
            this.averageResponseTimeMs = (this.averageResponseTimeMs + responseTimeMs) / 2;
        }
    }
    
    public long getLastResponseTimeMs() {
        return lastResponseTimeMs;
    }
    
    public int getReconnectAttempts() {
        return reconnectAttempts;
    }
    
    public void incrementReconnectAttempts() {
        this.reconnectAttempts++;
    }
    
    public void resetReconnectAttempts() {
        this.reconnectAttempts = 0;
    }
    
    public int getMaxReconnectAttempts() {
        return maxReconnectAttempts;
    }
    
    public void setMaxReconnectAttempts(int maxReconnectAttempts) {
        this.maxReconnectAttempts = maxReconnectAttempts;
    }
    
    public boolean isHealthCheckPassed() {
        return healthCheckPassed;
    }
    
    public void setHealthCheckPassed(boolean healthCheckPassed) {
        this.healthCheckPassed = healthCheckPassed;
        this.lastHealthCheckTime = Instant.now();
    }
    
    public int getAvailableToolsCount() {
        return availableToolsCount;
    }
    
    public void setAvailableToolsCount(int availableToolsCount) {
        this.availableToolsCount = availableToolsCount;
    }
    
    public int getAvailableResourcesCount() {
        return availableResourcesCount;
    }
    
    public void setAvailableResourcesCount(int availableResourcesCount) {
        this.availableResourcesCount = availableResourcesCount;
    }
    
    public int getAvailablePromptsCount() {
        return availablePromptsCount;
    }
    
    public void setAvailablePromptsCount(int availablePromptsCount) {
        this.availablePromptsCount = availablePromptsCount;
    }
    
    public Map<String, Object> getCustomProperties() {
        return customProperties;
    }
    
    public void setCustomProperty(String key, Object value) {
        customProperties.put(key, value);
    }
    
    public Object getCustomProperty(String key) {
        return customProperties.get(key);
    }
    
    // 便利方法
    public boolean isConnected() {
        return state == ConnectionState.CONNECTED;
    }
    
    public boolean isConnecting() {
        return state == ConnectionState.CONNECTING || state == ConnectionState.RECONNECTING;
    }
    
    public boolean hasError() {
        return state == ConnectionState.ERROR || lastError != null;
    }
    
    public boolean canReconnect() {
        return reconnectAttempts < maxReconnectAttempts && 
               (state == ConnectionState.DISCONNECTED || state == ConnectionState.ERROR);
    }
    
    public double getSuccessRate() {
        if (totalRequests == 0) {
            return 0.0;
        }
        return (double) successfulRequests / totalRequests;
    }
    
    public long getUptimeMs() {
        if (lastConnectedTime == null) {
            return 0;
        }
        if (state == ConnectionState.CONNECTED) {
            return Instant.now().toEpochMilli() - lastConnectedTime.toEpochMilli();
        } else if (lastDisconnectedTime != null) {
            return lastDisconnectedTime.toEpochMilli() - lastConnectedTime.toEpochMilli();
        }
        return 0;
    }
    
    /**
     * 生成状态报告
     */
    public String generateStatusReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== MCP客户端状态报告 ===\n");
        report.append("客户端名称: ").append(clientName).append("\n");
        report.append("连接状态: ").append(state).append("\n");
        
        if (lastConnectedTime != null) {
            report.append("最后连接时间: ").append(lastConnectedTime).append("\n");
        }
        
        if (lastDisconnectedTime != null) {
            report.append("最后断开时间: ").append(lastDisconnectedTime).append("\n");
        }
        
        if (state == ConnectionState.CONNECTED) {
            report.append("运行时间: ").append(getUptimeMs()).append("ms\n");
        }
        
        report.append("健康检查: ").append(healthCheckPassed ? "通过" : "失败").append("\n");
        report.append("最后健康检查: ").append(lastHealthCheckTime).append("\n");
        
        if (hasError()) {
            report.append("最后错误: ").append(lastError).append("\n");
        }
        
        report.append("重连尝试: ").append(reconnectAttempts).append("/").append(maxReconnectAttempts).append("\n");
        
        // 性能统计
        report.append("\n=== 性能统计 ===\n");
        report.append("总请求数: ").append(totalRequests).append("\n");
        report.append("成功请求: ").append(successfulRequests).append("\n");
        report.append("失败请求: ").append(failedRequests).append("\n");
        report.append("成功率: ").append(String.format("%.2f%%", getSuccessRate() * 100)).append("\n");
        report.append("平均响应时间: ").append(averageResponseTimeMs).append("ms\n");
        report.append("最后响应时间: ").append(lastResponseTimeMs).append("ms\n");
        
        // 功能统计
        report.append("\n=== 功能统计 ===\n");
        report.append("可用工具数: ").append(availableToolsCount).append("\n");
        report.append("可用资源数: ").append(availableResourcesCount).append("\n");
        report.append("可用提示词数: ").append(availablePromptsCount).append("\n");
        
        return report.toString();
    }
    
    @Override
    public String toString() {
        return String.format("MCPClientStatus{name='%s', state=%s, connected=%s, error='%s'}", 
                           clientName, state, isConnected(), lastError);
    }
}