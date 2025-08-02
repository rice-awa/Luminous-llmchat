package com.riceawa.mcp.service;

import com.riceawa.llm.logging.LogManager;
import com.riceawa.mcp.exception.MCPException;
import com.riceawa.mcp.exception.MCPErrorType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * MCP错误处理工具类
 * 提供统一的错误处理、异常转换、用户友好的错误消息生成和监控功能
 */
public class MCPErrorHandler {
    private static final LogManager logger = LogManager.getInstance();
    
    // 错误统计
    private static final ConcurrentHashMap<MCPErrorType, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicLong> clientErrorCounts = new ConcurrentHashMap<>();
    
    // 初始化错误计数器
    static {
        for (MCPErrorType errorType : MCPErrorType.values()) {
            errorCounts.put(errorType, new AtomicLong(0));
        }
    }
    
    /**
     * 处理CompletableFuture中的异常，转换为用户友好的错误
     */
    public static <T> CompletableFuture<T> handleErrors(CompletableFuture<T> future, String clientName, String operationName) {
        return future.handle((result, throwable) -> {
            if (throwable != null) {
                MCPException mcpException = convertToMCPException(throwable, clientName, operationName);
                recordError(mcpException, operationName);
                throw new RuntimeException(mcpException);
            }
            return result;
        });
    }
    
    /**
     * 将任意异常转换为MCPException
     */
    public static MCPException convertToMCPException(Throwable throwable, String clientName, String operationName) {
        // 如果已经是MCPException，直接返回
        if (throwable instanceof MCPException) {
            return (MCPException) throwable;
        }
        
        // 如果是RuntimeException包装的MCPException
        if (throwable instanceof RuntimeException && throwable.getCause() instanceof MCPException) {
            return (MCPException) throwable.getCause();
        }
        
        // 如果是CompletionException包装的异常
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return convertToMCPException(throwable.getCause(), clientName, operationName);
        }
        
        // 根据异常类型转换
        return convertSpecificException(throwable, clientName, operationName);
    }
    
    /**
     * 转换特定类型的异常
     */
    private static MCPException convertSpecificException(Throwable throwable, String clientName, String operationName) {
        String message = operationName + "失败";
        if (throwable.getMessage() != null) {
            message += ": " + throwable.getMessage();
        }
        
        // 超时异常
        if (throwable instanceof TimeoutException) {
            return MCPException.timeout(clientName, operationName, 30000); // 默认30秒超时
        }
        
        // 网络连接异常
        if (throwable instanceof java.net.ConnectException) {
            return MCPException.connectionFailed(clientName, "无法连接到MCP服务器", throwable);
        }
        
        if (throwable instanceof java.net.SocketTimeoutException) {
            return MCPException.timeout(clientName, operationName, 0);
        }
        
        if (throwable instanceof java.net.UnknownHostException) {
            return MCPException.connectionFailed(clientName, "无法解析主机名", throwable);
        }
        
        // IO异常
        if (throwable instanceof java.io.IOException) {
            return MCPException.connectionFailed(clientName, "网络IO错误", throwable);
        }
        
        // 中断异常
        if (throwable instanceof InterruptedException) {
            return MCPException.operationFailed("操作被中断", throwable);
        }
        
        // JSON解析错误
        if (throwable instanceof com.google.gson.JsonSyntaxException ||
            throwable instanceof com.google.gson.JsonParseException) {
            return MCPException.protocolError(clientName, "JSON解析错误: " + throwable.getMessage());
        }
        
        // 非法参数异常
        if (throwable instanceof IllegalArgumentException) {
            return MCPException.invalidParameters(clientName, throwable.getMessage());
        }
        
        // 非法状态异常
        if (throwable instanceof IllegalStateException) {
            return MCPException.serverError(clientName, "客户端状态错误: " + throwable.getMessage());
        }
        
        // 空指针异常
        if (throwable instanceof NullPointerException) {
            return MCPException.serverError(clientName, "内部错误: 空指针异常");
        }
        
        // 安全异常
        if (throwable instanceof SecurityException) {
            return MCPException.permissionDenied(clientName, operationName);
        }
        
        // 默认为服务器错误
        return MCPException.serverError(clientName, message);
    }
    
    /**
     * 记录错误并更新统计
     */
    public static void recordError(MCPException exception, String operationName) {
        // 更新错误统计
        updateErrorStatistics(exception);
        
        // 记录详细日志
        logException(exception, operationName);
    }
    
    /**
     * 更新错误统计
     */
    private static void updateErrorStatistics(MCPException exception) {
        // 更新错误类型统计
        errorCounts.get(exception.getErrorType()).incrementAndGet();
        
        // 更新客户端错误统计
        if (exception.getClientName() != null) {
            clientErrorCounts.computeIfAbsent(exception.getClientName(), 
                                             k -> new AtomicLong(0)).incrementAndGet();
        }
    }
    
    /**
     * 记录异常日志
     */
    private static void logException(MCPException exception, String operationName) {
        String severity = getSeverityString(exception.getSeverityLevel());
        
        switch (exception.getSeverityLevel()) {
            case 1:
            case 2:
                logger.logWarn(
                    "MCP操作警告",
                    String.format("操作: %s, 客户端: %s, 错误: %s", 
                                 operationName,
                                 exception.getClientName() != null ? exception.getClientName() : "未知",
                                 exception.getUserFriendlyMessage()),
                    "severity", severity,
                    "errorType", exception.getErrorType().name(),
                    "clientName", exception.getClientName(),
                    "operation", operationName,
                    "retryable", String.valueOf(exception.isRetryable())
                );
                break;
            case 3:
                logger.logError(
                    "MCP操作错误",
                    String.format("操作: %s, 客户端: %s, 错误: %s", 
                                 operationName,
                                 exception.getClientName() != null ? exception.getClientName() : "未知",
                                 exception.getUserFriendlyMessage()),
                    exception,
                    "severity", severity,
                    "errorType", exception.getErrorType().name(),
                    "clientName", exception.getClientName(),
                    "operation", operationName,
                    "retryable", String.valueOf(exception.isRetryable())
                );
                break;
            case 4:
            case 5:
                logger.logError(
                    "MCP严重错误",
                    String.format("检测到严重MCP错误 - 操作: %s, 客户端: %s, 错误: %s", 
                                 operationName,
                                 exception.getClientName() != null ? exception.getClientName() : "未知",
                                 exception.getDetailedMessage()),
                    exception,
                    "severity", severity,
                    "errorType", exception.getErrorType().name(),
                    "clientName", exception.getClientName(),
                    "operation", operationName,
                    "retryable", String.valueOf(exception.isRetryable()),
                    "critical", "true"
                );
                break;
        }
    }
    
    /**
     * 获取严重程度字符串
     */
    private static String getSeverityString(int level) {
        switch (level) {
            case 1: return "TRACE";
            case 2: return "LOW";
            case 3: return "MEDIUM";
            case 4: return "HIGH";
            case 5: return "CRITICAL";
            default: return "MEDIUM";
        }
    }
    
    /**
     * 创建错误恢复函数
     */
    public static <T> Function<Throwable, T> createRecoveryFunction(T defaultValue, String clientName, String operationName) {
        return throwable -> {
            MCPException mcpException = convertToMCPException(throwable, clientName, operationName);
            recordError(mcpException, operationName);
            return defaultValue;
        };
    }
    
    /**
     * 创建错误恢复函数（返回空CompletableFuture）
     */
    public static <T> Function<Throwable, CompletableFuture<T>> createAsyncRecoveryFunction(T defaultValue, String clientName, String operationName) {
        return throwable -> {
            MCPException mcpException = convertToMCPException(throwable, clientName, operationName);
            recordError(mcpException, operationName);
            return CompletableFuture.completedFuture(defaultValue);
        };
    }
    
    /**
     * 创建错误传播函数
     */
    public static <T> Function<Throwable, T> createPropagationFunction(String clientName, String operationName) {
        return throwable -> {
            MCPException mcpException = convertToMCPException(throwable, clientName, operationName);
            recordError(mcpException, operationName);
            throw new RuntimeException(mcpException);
        };
    }
    
    /**
     * 安全地执行可能抛出异常的操作
     */
    public static <T> T safeExecute(java.util.concurrent.Callable<T> operation, T defaultValue, String clientName, String operationName) {
        try {
            return operation.call();
        } catch (Exception e) {
            MCPException mcpException = convertToMCPException(e, clientName, operationName);
            recordError(mcpException, operationName);
            return defaultValue;
        }
    }
    
    /**
     * 安全地执行可能抛出异常的无返回值操作
     */
    public static void safeExecute(Runnable operation, String clientName, String operationName) {
        try {
            operation.run();
        } catch (Exception e) {
            MCPException mcpException = convertToMCPException(e, clientName, operationName);
            recordError(mcpException, operationName);
        }
    }
    
    /**
     * 创建用户友好的错误消息
     */
    public static String createUserFriendlyErrorMessage(Throwable throwable, String context) {
        MCPException mcpException = convertToMCPException(throwable, null, context);
        return mcpException.getUserFriendlyMessage();
    }
    
    /**
     * 检查异常是否为致命错误（不应该重试）
     */
    public static boolean isFatalError(Throwable throwable) {
        MCPException mcpException = convertToMCPException(throwable, null, "operation");
        
        // 配置错误、权限被拒绝、参数无效等都是致命错误
        MCPErrorType errorType = mcpException.getErrorType();
        return errorType == MCPErrorType.CONFIGURATION_ERROR ||
               errorType == MCPErrorType.PERMISSION_DENIED ||
               errorType == MCPErrorType.INVALID_PARAMETERS;
    }
    
    /**
     * 获取异常的严重程度等级
     */
    public static int getErrorSeverity(Throwable throwable) {
        MCPException mcpException = convertToMCPException(throwable, null, "operation");
        return mcpException.getSeverityLevel();
    }
    
    /**
     * 为CompletableFuture添加标准错误处理
     */
    public static <T> CompletableFuture<T> addStandardErrorHandling(CompletableFuture<T> future, String clientName, String operationName) {
        return future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                MCPException mcpException = convertToMCPException(throwable, clientName, operationName);
                recordError(mcpException, operationName);
            }
        });
    }
    
    /**
     * 获取错误统计信息
     */
    public static String getErrorStatistics() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== MCP错误统计 ===\n");
        
        // 按错误类型统计
        sb.append("按错误类型统计:\n");
        boolean hasErrors = false;
        for (MCPErrorType errorType : MCPErrorType.values()) {
            long count = errorCounts.get(errorType).get();
            if (count > 0) {
                sb.append(String.format("  %s: %d次\n", errorType.getDisplayName(), count));
                hasErrors = true;
            }
        }
        
        if (!hasErrors) {
            sb.append("  无错误记录\n");
        }
        
        // 按客户端统计
        if (!clientErrorCounts.isEmpty()) {
            sb.append("按客户端统计:\n");
            clientErrorCounts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .forEach(entry -> {
                    sb.append(String.format("  %s: %d次\n", 
                                           entry.getKey(), 
                                           entry.getValue().get()));
                });
        }
        
        return sb.toString();
    }
    
    /**
     * 重置错误统计
     */
    public static void resetStatistics() {
        errorCounts.values().forEach(counter -> counter.set(0));
        clientErrorCounts.clear();
        
        logger.logInfo(
            "MCP错误统计已重置",
            "MCP错误统计计数器已重置为零"
        );
    }
    
    /**
     * 获取指定错误类型的计数
     */
    public static long getErrorCount(MCPErrorType errorType) {
        return errorCounts.get(errorType).get();
    }
    
    /**
     * 获取指定客户端的错误计数
     */
    public static long getClientErrorCount(String clientName) {
        AtomicLong counter = clientErrorCounts.get(clientName);
        return counter != null ? counter.get() : 0;
    }
    
    /**
     * 获取总错误计数
     */
    public static long getTotalErrorCount() {
        return errorCounts.values().stream()
            .mapToLong(AtomicLong::get)
            .sum();
    }
}