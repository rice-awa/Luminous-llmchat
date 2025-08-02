package com.riceawa.mcp.service;

import com.riceawa.mcp.exception.MCPException;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * MCP异步操作工具类
 * 提供超时控制、重试机制和统一的异步处理支持
 */
public class MCPAsyncUtils {
    
    // 默认超时时间
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    
    // 默认重试次数
    private static final int DEFAULT_MAX_RETRIES = 3;
    
    // 默认重试延迟
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(1);
    
    /**
     * 执行带超时的异步操作
     * @param operation 要执行的操作
     * @param timeout 超时时间
     * @param operationName 操作名称（用于错误信息）
     * @return 异步结果
     */
    public static <T> CompletableFuture<T> withTimeout(
            Supplier<CompletableFuture<T>> operation, 
            Duration timeout, 
            String operationName) {
        
        CompletableFuture<T> future = operation.get();
        
        return future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .handle((result, throwable) -> {
                    if (throwable instanceof TimeoutException) {
                        throw new RuntimeException(MCPException.timeout(null, operationName, timeout.toMillis()));
                    } else if (throwable != null) {
                        if (throwable instanceof RuntimeException) {
                            throw (RuntimeException) throwable;
                        }
                        throw new RuntimeException(throwable);
                    }
                    return result;
                });
    }
    
    /**
     * 执行带超时的异步操作（使用默认超时时间）
     */
    public static <T> CompletableFuture<T> withTimeout(
            Supplier<CompletableFuture<T>> operation, 
            String operationName) {
        return withTimeout(operation, DEFAULT_TIMEOUT, operationName);
    }
    
    /**
     * 执行带重试机制的异步操作
     * @param operation 要执行的操作
     * @param maxRetries 最大重试次数
     * @param retryDelay 重试延迟
     * @param operationName 操作名称
     * @return 异步结果
     */
    public static <T> CompletableFuture<T> withRetry(
            Supplier<CompletableFuture<T>> operation,
            int maxRetries,
            Duration retryDelay,
            String operationName) {
        
        return executeWithRetry(operation, 0, maxRetries, retryDelay, operationName);
    }
    
    /**
     * 执行带重试机制的异步操作（使用默认重试参数）
     */
    public static <T> CompletableFuture<T> withRetry(
            Supplier<CompletableFuture<T>> operation,
            String operationName) {
        return withRetry(operation, DEFAULT_MAX_RETRIES, DEFAULT_RETRY_DELAY, operationName);
    }
    
    /**
     * 执行带超时和重试机制的异步操作
     * @param operation 要执行的操作
     * @param timeout 超时时间
     * @param maxRetries 最大重试次数
     * @param retryDelay 重试延迟
     * @param operationName 操作名称
     * @return 异步结果
     */
    public static <T> CompletableFuture<T> withTimeoutAndRetry(
            Supplier<CompletableFuture<T>> operation,
            Duration timeout,
            int maxRetries,
            Duration retryDelay,
            String operationName) {
        
        return executeWithRetry(() -> withTimeout(operation, timeout, operationName), 
                               0, maxRetries, retryDelay, operationName);
    }
    
    /**
     * 执行带超时和重试机制的异步操作（使用默认参数）
     */
    public static <T> CompletableFuture<T> withTimeoutAndRetry(
            Supplier<CompletableFuture<T>> operation,
            String operationName) {
        return withTimeoutAndRetry(operation, DEFAULT_TIMEOUT, DEFAULT_MAX_RETRIES, 
                                 DEFAULT_RETRY_DELAY, operationName);
    }
    
    /**
     * 递归执行重试逻辑
     */
    private static <T> CompletableFuture<T> executeWithRetry(
            Supplier<CompletableFuture<T>> operation,
            int currentAttempt,
            int maxRetries,
            Duration retryDelay,
            String operationName) {
        
        return operation.get()
                .exceptionallyCompose(throwable -> {
                    // 检查是否应该重试
                    if (currentAttempt >= maxRetries || !shouldRetry(throwable)) {
                        return CompletableFuture.failedFuture(throwable);
                    }
                    
                    // 延迟后重试
                    return delay(retryDelay)
                            .thenCompose(v -> executeWithRetry(operation, currentAttempt + 1, 
                                                             maxRetries, retryDelay, operationName));
                });
    }
    
    /**
     * 判断错误是否应该重试
     */
    private static boolean shouldRetry(Throwable throwable) {
        // 如果是MCPException，检查其是否可重试
        if (throwable.getCause() instanceof MCPException) {
            MCPException mcpException = (MCPException) throwable.getCause();
            return mcpException.isRetryable();
        }
        
        // 对于RuntimeException包装的MCPException
        if (throwable instanceof RuntimeException && throwable.getCause() instanceof MCPException) {
            MCPException mcpException = (MCPException) throwable.getCause();
            return mcpException.isRetryable();
        }
        
        // 网络相关错误通常可以重试
        if (throwable instanceof java.net.ConnectException ||
            throwable instanceof java.net.SocketTimeoutException ||
            throwable instanceof java.net.UnknownHostException) {
            return true;
        }
        
        // 超时错误可以重试
        if (throwable instanceof TimeoutException) {
            return true;
        }
        
        // 其他错误一般不重试
        return false;
    }
    
    /**
     * 创建延迟执行的CompletableFuture
     */
    public static CompletableFuture<Void> delay(Duration delay) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS)
                .execute(() -> future.complete(null));
        return future;
    }
    
    /**
     * 安全地取消CompletableFuture
     */
    public static <T> void safeCancelFuture(CompletableFuture<T> future) {
        if (future != null && !future.isDone()) {
            try {
                future.cancel(true);
            } catch (Exception e) {
                // 忽略取消失败的错误
            }
        }
    }
    
    /**
     * 批量取消CompletableFuture
     */
    public static void safeCancelFutures(CompletableFuture<?>... futures) {
        for (CompletableFuture<?> future : futures) {
            safeCancelFuture(future);
        }
    }
    
    /**
     * 创建已完成的CompletableFuture（成功）
     */
    public static <T> CompletableFuture<T> completedFuture(T value) {
        return CompletableFuture.completedFuture(value);
    }
    
    /**
     * 创建已完成的CompletableFuture（失败）
     */
    public static <T> CompletableFuture<T> failedFuture(Throwable throwable) {
        return CompletableFuture.failedFuture(throwable);
    }
    
    /**
     * 创建已完成的CompletableFuture（失败，使用MCPException）
     */
    public static <T> CompletableFuture<T> failedFuture(MCPException exception) {
        return CompletableFuture.failedFuture(new RuntimeException(exception));
    }
}