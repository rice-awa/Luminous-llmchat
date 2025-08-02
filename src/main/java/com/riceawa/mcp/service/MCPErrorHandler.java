package com.riceawa.mcp.service;

import com.riceawa.mcp.exception.MCPException;
import com.riceawa.mcp.exception.MCPErrorType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * MCP错误处理工具类
 * 提供统一的错误处理、异常转换和用户友好的错误消息生成
 */
public class MCPErrorHandler {
    
    /**
     * 处理CompletableFuture中的异常，转换为用户友好的错误
     */
    public static <T> CompletableFuture<T> handleErrors(CompletableFuture<T> future, String clientName, String operationName) {
        return future.handle((result, throwable) -> {
            if (throwable != null) {
                MCPException mcpException = convertToMCPException(throwable, clientName, operationName);
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
     * 创建错误恢复函数
     */
    public static <T> Function<Throwable, T> createRecoveryFunction(T defaultValue, String clientName, String operationName) {
        return throwable -> {
            // 记录错误（实际项目中应该使用日志框架）
            MCPException mcpException = convertToMCPException(throwable, clientName, operationName);
            System.err.println("MCP操作失败: " + mcpException.getUserFriendlyMessage());
            
            return defaultValue;
        };
    }
    
    /**
     * 创建错误恢复函数（返回空CompletableFuture）
     */
    public static <T> Function<Throwable, CompletableFuture<T>> createAsyncRecoveryFunction(T defaultValue, String clientName, String operationName) {
        return throwable -> {
            MCPException mcpException = convertToMCPException(throwable, clientName, operationName);
            System.err.println("MCP操作失败: " + mcpException.getUserFriendlyMessage());
            
            return CompletableFuture.completedFuture(defaultValue);
        };
    }
    
    /**
     * 创建错误传播函数
     */
    public static <T> Function<Throwable, T> createPropagationFunction(String clientName, String operationName) {
        return throwable -> {
            MCPException mcpException = convertToMCPException(throwable, clientName, operationName);
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
            System.err.println("MCP操作失败: " + mcpException.getUserFriendlyMessage());
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
            System.err.println("MCP操作失败: " + mcpException.getUserFriendlyMessage());
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
                
                // 记录错误（实际项目中应该使用日志框架）
                if (mcpException.getSeverityLevel() >= 3) { // 严重错误
                    System.err.println("严重MCP错误: " + mcpException.getDetailedMessage());
                } else {
                    System.err.println("MCP错误: " + mcpException.getUserFriendlyMessage());
                }
            }
        });
    }
}