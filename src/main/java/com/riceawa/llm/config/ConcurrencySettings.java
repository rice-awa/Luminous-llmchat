package com.riceawa.llm.config;

/**
 * 并发设置配置类
 */
public class ConcurrencySettings {
    // HTTP连接池配置
    private int maxIdleConnections = 20;
    private long keepAliveDurationMs = 300000; // 5分钟
    private int connectTimeoutMs = 30000; // 30秒
    private int readTimeoutMs = 60000; // 60秒
    private int writeTimeoutMs = 60000; // 60秒
    
    // 并发控制配置
    private int maxConcurrentRequests = 10;
    private int queueCapacity = 50;
    private long requestTimeoutMs = 30000; // 30秒
    
    // 线程池配置
    private int corePoolSize = 5;
    private int maximumPoolSize = 20;
    private long keepAliveTimeMs = 60000; // 60秒
    
    // 重试配置
    private boolean enableRetry = true;
    private int maxRetryAttempts = 3;
    private long retryDelayMs = 1000; // 1秒
    private double retryBackoffMultiplier = 2.0;
    
    // 速率限制配置
    private boolean enableRateLimit = false;
    private int requestsPerMinute = 60;
    private int requestsPerHour = 1000;
    
    public ConcurrencySettings() {
    }
    
    public static ConcurrencySettings createDefault() {
        return new ConcurrencySettings();
    }
    
    // HTTP连接池配置的getter和setter
    public int getMaxIdleConnections() {
        return maxIdleConnections;
    }
    
    public void setMaxIdleConnections(int maxIdleConnections) {
        this.maxIdleConnections = maxIdleConnections;
    }
    
    public long getKeepAliveDurationMs() {
        return keepAliveDurationMs;
    }
    
    public void setKeepAliveDurationMs(long keepAliveDurationMs) {
        this.keepAliveDurationMs = keepAliveDurationMs;
    }
    
    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }
    
    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }
    
    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }
    
    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
    
    public int getWriteTimeoutMs() {
        return writeTimeoutMs;
    }
    
    public void setWriteTimeoutMs(int writeTimeoutMs) {
        this.writeTimeoutMs = writeTimeoutMs;
    }
    
    // 并发控制配置的getter和setter
    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }
    
    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
    }
    
    public int getQueueCapacity() {
        return queueCapacity;
    }
    
    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }
    
    public long getRequestTimeoutMs() {
        return requestTimeoutMs;
    }
    
    public void setRequestTimeoutMs(long requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }
    
    // 线程池配置的getter和setter
    public int getCorePoolSize() {
        return corePoolSize;
    }
    
    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }
    
    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }
    
    public void setMaximumPoolSize(int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
    }
    
    public long getKeepAliveTimeMs() {
        return keepAliveTimeMs;
    }
    
    public void setKeepAliveTimeMs(long keepAliveTimeMs) {
        this.keepAliveTimeMs = keepAliveTimeMs;
    }
    
    // 重试配置的getter和setter
    public boolean isEnableRetry() {
        return enableRetry;
    }
    
    public void setEnableRetry(boolean enableRetry) {
        this.enableRetry = enableRetry;
    }
    
    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }
    
    public void setMaxRetryAttempts(int maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
    }
    
    public long getRetryDelayMs() {
        return retryDelayMs;
    }
    
    public void setRetryDelayMs(long retryDelayMs) {
        this.retryDelayMs = retryDelayMs;
    }
    
    public double getRetryBackoffMultiplier() {
        return retryBackoffMultiplier;
    }
    
    public void setRetryBackoffMultiplier(double retryBackoffMultiplier) {
        this.retryBackoffMultiplier = retryBackoffMultiplier;
    }
    
    // 速率限制配置的getter和setter
    public boolean isEnableRateLimit() {
        return enableRateLimit;
    }
    
    public void setEnableRateLimit(boolean enableRateLimit) {
        this.enableRateLimit = enableRateLimit;
    }
    
    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }
    
    public void setRequestsPerMinute(int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }
    
    public int getRequestsPerHour() {
        return requestsPerHour;
    }
    
    public void setRequestsPerHour(int requestsPerHour) {
        this.requestsPerHour = requestsPerHour;
    }
    
    /**
     * 验证配置的有效性
     */
    public boolean isValid() {
        return maxIdleConnections > 0 &&
               keepAliveDurationMs > 0 &&
               connectTimeoutMs > 0 &&
               readTimeoutMs > 0 &&
               writeTimeoutMs > 0 &&
               maxConcurrentRequests > 0 &&
               queueCapacity > 0 &&
               requestTimeoutMs > 0 &&
               corePoolSize > 0 &&
               maximumPoolSize >= corePoolSize &&
               keepAliveTimeMs > 0 &&
               maxRetryAttempts >= 0 &&
               retryDelayMs >= 0 &&
               retryBackoffMultiplier > 0 &&
               requestsPerMinute > 0 &&
               requestsPerHour > 0;
    }
    
    @Override
    public String toString() {
        return "ConcurrencySettings{" +
                "maxIdleConnections=" + maxIdleConnections +
                ", keepAliveDurationMs=" + keepAliveDurationMs +
                ", connectTimeoutMs=" + connectTimeoutMs +
                ", readTimeoutMs=" + readTimeoutMs +
                ", writeTimeoutMs=" + writeTimeoutMs +
                ", maxConcurrentRequests=" + maxConcurrentRequests +
                ", queueCapacity=" + queueCapacity +
                ", requestTimeoutMs=" + requestTimeoutMs +
                ", corePoolSize=" + corePoolSize +
                ", maximumPoolSize=" + maximumPoolSize +
                ", keepAliveTimeMs=" + keepAliveTimeMs +
                ", enableRetry=" + enableRetry +
                ", maxRetryAttempts=" + maxRetryAttempts +
                ", retryDelayMs=" + retryDelayMs +
                ", retryBackoffMultiplier=" + retryBackoffMultiplier +
                ", enableRateLimit=" + enableRateLimit +
                ", requestsPerMinute=" + requestsPerMinute +
                ", requestsPerHour=" + requestsPerHour +
                '}';
    }
}
