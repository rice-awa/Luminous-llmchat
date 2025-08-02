package com.riceawa.mcp.exception;

/**
 * MCP操作异常类
 * 表示MCP客户端操作中发生的各种错误
 */
public class MCPException extends Exception {
    // 错误类型
    private final MCPErrorType errorType;
    
    // 客户端名称
    private final String clientName;
    
    // 详细信息
    private final String details;


    public MCPException(MCPErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
        this.clientName = null;
        this.details = null;
    }

    public MCPException(MCPErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.clientName = null;
        this.details = null;
    }

    public MCPException(MCPErrorType errorType, String clientName, String message) {
        super(message);
        this.errorType = errorType;
        this.clientName = clientName;
        this.details = null;
    }

    public MCPException(MCPErrorType errorType, String clientName, String message, String details) {
        super(message);
        this.errorType = errorType;
        this.clientName = clientName;
        this.details = details;
    }

    public MCPException(MCPErrorType errorType, String clientName, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.clientName = clientName;
        this.details = null;
    }

    public MCPException(MCPErrorType errorType, String clientName, String message, String details, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.clientName = clientName;
        this.details = details;
    }

    // Getters
    public MCPErrorType getErrorType() {
        return errorType;
    }

    public String getClientName() {
        return clientName;
    }

    public String getDetails() {
        return details;
    }

    /**
     * 获取用户友好的错误消息
     */
    public String getUserFriendlyMessage() {
        StringBuilder sb = new StringBuilder();
        
        if (clientName != null) {
            sb.append("[").append(clientName).append("] ");
        }
        
        sb.append(errorType.getDisplayName());
        
        if (getMessage() != null) {
            sb.append(": ").append(getMessage());
        }
        
        return sb.toString();
    }

    /**
     * 获取详细的错误信息
     */
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append(getUserFriendlyMessage());
        
        if (details != null) {
            sb.append("\n详细信息: ").append(details);
        }
        
        if (getCause() != null) {
            sb.append("\n原因: ").append(getCause().getMessage());
        }
        
        return sb.toString();
    }

    /**
     * 创建连接失败异常
     */
    public static MCPException connectionFailed(String clientName, String message) {
        return new MCPException(MCPErrorType.CONNECTION_FAILED, clientName, message);
    }

    /**
     * 创建连接失败异常（带原因）
     */
    public static MCPException connectionFailed(String clientName, String message, Throwable cause) {
        return new MCPException(MCPErrorType.CONNECTION_FAILED, clientName, message, cause);
    }

    /**
     * 创建协议错误异常
     */
    public static MCPException protocolError(String clientName, String message) {
        return new MCPException(MCPErrorType.PROTOCOL_ERROR, clientName, message);
    }

    /**
     * 创建工具未找到异常
     */
    public static MCPException toolNotFound(String clientName, String toolName) {
        return new MCPException(MCPErrorType.TOOL_NOT_FOUND, clientName, 
                              "工具 '" + toolName + "' 未找到");
    }

    /**
     * 创建工具未找到异常（不指定客户端）
     */
    public static MCPException toolNotFound(String toolName) {
        return new MCPException(MCPErrorType.TOOL_NOT_FOUND, 
                              "工具 '" + toolName + "' 未找到");
    }

    /**
     * 创建资源未找到异常
     */
    public static MCPException resourceNotFound(String clientName, String resourceUri) {
        return new MCPException(MCPErrorType.RESOURCE_NOT_FOUND, clientName, 
                              "资源 '" + resourceUri + "' 未找到");
    }

    /**
     * 创建资源未找到异常（不指定客户端）
     */
    public static MCPException resourceNotFound(String resourceUri) {
        return new MCPException(MCPErrorType.RESOURCE_NOT_FOUND, 
                              "资源 '" + resourceUri + "' 未找到");
    }

    /**
     * 创建提示词未找到异常
     */
    public static MCPException promptNotFound(String promptName) {
        return new MCPException(MCPErrorType.TOOL_NOT_FOUND, 
                              "提示词 '" + promptName + "' 未找到");
    }

    /**
     * 创建客户端未找到异常
     */
    public static MCPException clientNotFound(String clientName) {
        return new MCPException(MCPErrorType.CONNECTION_FAILED, clientName, 
                              "客户端 '" + clientName + "' 未找到或未连接");
    }

    /**
     * 创建操作失败异常
     */
    public static MCPException operationFailed(String message) {
        return new MCPException(MCPErrorType.SERVER_ERROR, message);
    }

    /**
     * 创建操作失败异常（带原因）
     */
    public static MCPException operationFailed(String message, Throwable cause) {
        return new MCPException(MCPErrorType.SERVER_ERROR, message, cause);
    }

    /**
     * 创建权限被拒绝异常
     */
    public static MCPException permissionDenied(String clientName, String operation) {
        return new MCPException(MCPErrorType.PERMISSION_DENIED, clientName, 
                              "操作 '" + operation + "' 权限被拒绝");
    }

    /**
     * 创建超时异常
     */
    public static MCPException timeout(String clientName, String operation, long timeoutMs) {
        return new MCPException(MCPErrorType.TIMEOUT, clientName, 
                              "操作 '" + operation + "' 超时 (" + timeoutMs + "ms)");
    }

    /**
     * 创建参数无效异常
     */
    public static MCPException invalidParameters(String clientName, String message) {
        return new MCPException(MCPErrorType.INVALID_PARAMETERS, clientName, message);
    }

    /**
     * 创建服务器错误异常
     */
    public static MCPException serverError(String clientName, String message) {
        return new MCPException(MCPErrorType.SERVER_ERROR, clientName, message);
    }

    /**
     * 创建配置错误异常
     */
    public static MCPException configurationError(String message) {
        return new MCPException(MCPErrorType.CONFIGURATION_ERROR, message);
    }

    /**
     * 检查错误是否可以重试
     */
    public boolean isRetryable() {
        return errorType.isRetryable();
    }

    /**
     * 获取错误严重程度等级
     */
    public int getSeverityLevel() {
        return errorType.getSeverityLevel();
    }

    @Override
    public String toString() {
        return String.format("MCPException{type=%s, client='%s', message='%s'}", 
                           errorType, clientName, getMessage());
    }
}