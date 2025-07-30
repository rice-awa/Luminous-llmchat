package com.riceawa.llm.logging;

/**
 * 日志配置类
 */
public class LogConfig {
    // 默认配置值
    private LogLevel logLevel = LogLevel.INFO;
    private boolean enableFileLogging = true;
    private boolean enableConsoleLogging = true;
    private boolean enableJsonFormat = true;
    private int maxFileSize = 10 * 1024 * 1024; // 10MB
    private int maxBackupFiles = 5;
    private int retentionDays = 30;
    private boolean enableAsyncLogging = true;
    private int asyncQueueSize = 1000;
    
    // 日志类别配置
    private boolean enableSystemLog = true;
    private boolean enableChatLog = true;
    private boolean enableErrorLog = true;
    private boolean enablePerformanceLog = true;
    private boolean enableAuditLog = true;
    private boolean enableLLMRequestLog = true;

    // LLM请求日志特殊配置
    private boolean logFullRequestBody = true;
    private boolean logFullResponseBody = true;
    private int maxLogContentLength = 10000; // 最大日志内容长度
    private boolean sanitizeSensitiveData = true; // 是否脱敏敏感数据

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    public boolean isEnableFileLogging() {
        return enableFileLogging;
    }

    public void setEnableFileLogging(boolean enableFileLogging) {
        this.enableFileLogging = enableFileLogging;
    }

    public boolean isEnableConsoleLogging() {
        return enableConsoleLogging;
    }

    public void setEnableConsoleLogging(boolean enableConsoleLogging) {
        this.enableConsoleLogging = enableConsoleLogging;
    }

    public boolean isEnableJsonFormat() {
        return enableJsonFormat;
    }

    public void setEnableJsonFormat(boolean enableJsonFormat) {
        this.enableJsonFormat = enableJsonFormat;
    }

    public int getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(int maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public int getMaxBackupFiles() {
        return maxBackupFiles;
    }

    public void setMaxBackupFiles(int maxBackupFiles) {
        this.maxBackupFiles = maxBackupFiles;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public boolean isEnableAsyncLogging() {
        return enableAsyncLogging;
    }

    public void setEnableAsyncLogging(boolean enableAsyncLogging) {
        this.enableAsyncLogging = enableAsyncLogging;
    }

    public int getAsyncQueueSize() {
        return asyncQueueSize;
    }

    public void setAsyncQueueSize(int asyncQueueSize) {
        this.asyncQueueSize = asyncQueueSize;
    }

    public boolean isEnableSystemLog() {
        return enableSystemLog;
    }

    public void setEnableSystemLog(boolean enableSystemLog) {
        this.enableSystemLog = enableSystemLog;
    }

    public boolean isEnableChatLog() {
        return enableChatLog;
    }

    public void setEnableChatLog(boolean enableChatLog) {
        this.enableChatLog = enableChatLog;
    }

    public boolean isEnableErrorLog() {
        return enableErrorLog;
    }

    public void setEnableErrorLog(boolean enableErrorLog) {
        this.enableErrorLog = enableErrorLog;
    }

    public boolean isEnablePerformanceLog() {
        return enablePerformanceLog;
    }

    public void setEnablePerformanceLog(boolean enablePerformanceLog) {
        this.enablePerformanceLog = enablePerformanceLog;
    }

    public boolean isEnableAuditLog() {
        return enableAuditLog;
    }

    public void setEnableAuditLog(boolean enableAuditLog) {
        this.enableAuditLog = enableAuditLog;
    }

    public boolean isEnableLLMRequestLog() {
        return enableLLMRequestLog;
    }

    public void setEnableLLMRequestLog(boolean enableLLMRequestLog) {
        this.enableLLMRequestLog = enableLLMRequestLog;
    }

    public boolean isLogFullRequestBody() {
        return logFullRequestBody;
    }

    public void setLogFullRequestBody(boolean logFullRequestBody) {
        this.logFullRequestBody = logFullRequestBody;
    }

    public boolean isLogFullResponseBody() {
        return logFullResponseBody;
    }

    public void setLogFullResponseBody(boolean logFullResponseBody) {
        this.logFullResponseBody = logFullResponseBody;
    }

    public int getMaxLogContentLength() {
        return maxLogContentLength;
    }

    public void setMaxLogContentLength(int maxLogContentLength) {
        this.maxLogContentLength = maxLogContentLength;
    }

    public boolean isSanitizeSensitiveData() {
        return sanitizeSensitiveData;
    }

    public void setSanitizeSensitiveData(boolean sanitizeSensitiveData) {
        this.sanitizeSensitiveData = sanitizeSensitiveData;
    }

    /**
     * 检查指定类别的日志是否启用
     */
    public boolean isCategoryEnabled(String category) {
        if (category == null) return true;

        switch (category.toLowerCase()) {
            case "system":
                return enableSystemLog;
            case "chat":
                return enableChatLog;
            case "error":
                return enableErrorLog;
            case "performance":
                return enablePerformanceLog;
            case "audit":
                return enableAuditLog;
            case "llm_request":
                return enableLLMRequestLog;
            default:
                return true;
        }
    }

    /**
     * 创建默认配置
     */
    public static LogConfig createDefault() {
        return new LogConfig();
    }

    /**
     * 验证配置的有效性
     */
    public boolean isValid() {
        return logLevel != null && 
               maxFileSize > 0 && 
               maxBackupFiles >= 0 && 
               retentionDays > 0 && 
               asyncQueueSize > 0;
    }
}
