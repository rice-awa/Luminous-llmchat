package com.riceawa.mcp.exception;

/**
 * MCP错误类型枚举
 * 定义MCP客户端操作中可能出现的各种错误类型
 */
public enum MCPErrorType {
    /**
     * 连接失败 - MCP客户端无法连接到服务器
     */
    CONNECTION_FAILED("连接失败", "MCP客户端无法建立与服务器的连接"),
    
    /**
     * 协议错误 - MCP协议层出现错误
     */
    PROTOCOL_ERROR("协议错误", "MCP协议处理过程中出现错误"),
    
    /**
     * 工具未找到 - 请求的工具不存在
     */
    TOOL_NOT_FOUND("工具未找到", "请求的MCP工具不存在或不可用"),
    
    /**
     * 资源未找到 - 请求的资源不存在
     */
    RESOURCE_NOT_FOUND("资源未找到", "请求的MCP资源不存在或不可访问"),
    
    /**
     * 权限被拒绝 - 没有足够的权限执行操作
     */
    PERMISSION_DENIED("权限被拒绝", "当前用户没有足够的权限执行此操作"),
    
    /**
     * 超时 - 操作执行超时
     */
    TIMEOUT("超时", "MCP操作执行时间超过了设定的超时限制"),
    
    /**
     * 参数无效 - 提供的参数不符合要求
     */
    INVALID_PARAMETERS("参数无效", "提供的参数格式错误或不符合要求"),
    
    /**
     * 服务器错误 - MCP服务器端出现错误
     */
    SERVER_ERROR("服务器错误", "MCP服务器处理请求时出现内部错误"),
    
    /**
     * 客户端错误 - MCP客户端出现错误
     */
    CLIENT_ERROR("客户端错误", "MCP客户端处理过程中出现错误"),
    
    /**
     * 配置错误 - MCP配置信息有误
     */
    CONFIGURATION_ERROR("配置错误", "MCP配置信息错误或缺失"),
    
    /**
     * 初始化失败 - MCP客户端初始化失败
     */
    INITIALIZATION_FAILED("初始化失败", "MCP客户端初始化过程失败"),
    
    /**
     * 序列化错误 - 数据序列化或反序列化失败
     */
    SERIALIZATION_ERROR("序列化错误", "数据序列化或反序列化过程中出现错误"),
    
    /**
     * 验证错误 - 数据验证失败
     */
    VALIDATION_ERROR("验证错误", "数据验证过程中发现错误或不一致"),
    
    /**
     * 未知错误 - 未分类的其他错误
     */
    UNKNOWN_ERROR("未知错误", "发生了未知类型的错误");

    private final String displayName;
    private final String description;

    MCPErrorType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * 获取错误类型的显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取错误类型的详细描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 检查是否为连接相关错误
     */
    public boolean isConnectionError() {
        return this == CONNECTION_FAILED || this == TIMEOUT || this == INITIALIZATION_FAILED;
    }

    /**
     * 检查是否为客户端错误
     */
    public boolean isClientError() {
        return this == CLIENT_ERROR || this == INVALID_PARAMETERS || 
               this == SERIALIZATION_ERROR || this == VALIDATION_ERROR;
    }

    /**
     * 检查是否为服务器错误
     */
    public boolean isServerError() {
        return this == SERVER_ERROR || this == PROTOCOL_ERROR;
    }

    /**
     * 检查是否为权限相关错误
     */
    public boolean isPermissionError() {
        return this == PERMISSION_DENIED;
    }

    /**
     * 检查是否为配置相关错误
     */
    public boolean isConfigurationError() {
        return this == CONFIGURATION_ERROR;
    }

    /**
     * 检查是否为资源相关错误
     */
    public boolean isResourceError() {
        return this == TOOL_NOT_FOUND || this == RESOURCE_NOT_FOUND;
    }

    /**
     * 获取错误的严重程度等级
     * @return 1-5，数字越大表示越严重
     */
    public int getSeverityLevel() {
        switch (this) {
            case VALIDATION_ERROR:
            case INVALID_PARAMETERS:
                return 1; // 轻微错误
            case TOOL_NOT_FOUND:
            case RESOURCE_NOT_FOUND:
            case PERMISSION_DENIED:
                return 2; // 普通错误
            case TIMEOUT:
            case SERIALIZATION_ERROR:
                return 3; // 中等错误
            case CLIENT_ERROR:
            case SERVER_ERROR:
            case PROTOCOL_ERROR:
                return 4; // 严重错误
            case CONNECTION_FAILED:
            case INITIALIZATION_FAILED:
            case CONFIGURATION_ERROR:
            case UNKNOWN_ERROR:
                return 5; // 致命错误
            default:
                return 3; // 默认中等严重程度
        }
    }

    /**
     * 判断错误是否可以重试
     */
    public boolean isRetryable() {
        switch (this) {
            case CONNECTION_FAILED:
            case TIMEOUT:
            case SERVER_ERROR:
            case UNKNOWN_ERROR:
                return true;
            case PROTOCOL_ERROR:
            case TOOL_NOT_FOUND:
            case RESOURCE_NOT_FOUND:
            case PERMISSION_DENIED:
            case INVALID_PARAMETERS:
            case CLIENT_ERROR:
            case CONFIGURATION_ERROR:
            case INITIALIZATION_FAILED:
            case SERIALIZATION_ERROR:
            case VALIDATION_ERROR:
                return false;
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", displayName, name());
    }
}