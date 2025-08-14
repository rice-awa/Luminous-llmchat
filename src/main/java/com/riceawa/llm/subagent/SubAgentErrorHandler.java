package com.riceawa.llm.subagent;

import com.riceawa.llm.logging.LLMLogUtils;
import com.riceawa.llm.logging.LogLevel;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 子代理错误处理器
 * 提供统一的错误处理、重试机制和降级策略
 */
public class SubAgentErrorHandler {
    
    private static final String LOG_PREFIX = "[SubAgentErrorHandler]";
    
    // 重试配置
    private final int maxRetries;
    private final long baseRetryDelayMs;
    private final double retryBackoffMultiplier;
    private final long maxRetryDelayMs;
    
    // 错误统计
    private final Map<String, AtomicInteger> errorCounts;
    private final Map<String, Long> lastErrorTimes;
    
    /**
     * 构造函数
     */
    public SubAgentErrorHandler(int maxRetries, long baseRetryDelayMs, 
                              double retryBackoffMultiplier, long maxRetryDelayMs) {
        this.maxRetries = maxRetries;
        this.baseRetryDelayMs = baseRetryDelayMs;
        this.retryBackoffMultiplier = retryBackoffMultiplier;
        this.maxRetryDelayMs = maxRetryDelayMs;
        
        this.errorCounts = new ConcurrentHashMap<>();
        this.lastErrorTimes = new ConcurrentHashMap<>();
        
        LLMLogUtils.log(LogLevel.DEBUG, LOG_PREFIX + " 错误处理器已初始化");
    }
    
    /**
     * 处理任务执行错误
     */
    public <R extends SubAgentResult> CompletableFuture<R> handleTaskError(
            SubAgentTask<R> task, Throwable error, int currentRetry) {
        
        String taskId = task.getTaskId();
        String errorType = classifyError(error);
        
        // 记录错误统计
        recordError(taskId, errorType);
        
        LLMLogUtils.log(LogLevel.WARN, LOG_PREFIX + " 任务执行错误: " + taskId + 
            ", 错误类型: " + errorType + ", 重试次数: " + currentRetry, error);
        
        // 检查是否可以重试
        if (isRetryableError(error) && currentRetry < maxRetries) {
            return scheduleRetry(task, currentRetry + 1);
        } else {
            // 不可重试或达到最大重试次数，返回失败结果
            return CompletableFuture.completedFuture(createFailureResult(task, error));
        }
    }
    
    /**
     * 处理代理创建错误
     */
    public SubAgentCreationException handleCreationError(String agentType, Throwable error) {
        String errorType = classifyError(error);
        recordError("creation_" + agentType, errorType);
        
        LLMLogUtils.log(LogLevel.ERROR, LOG_PREFIX + " 代理创建错误: " + agentType + 
            ", 错误类型: " + errorType, error);
        
        // 根据错误类型提供不同的错误信息
        String message = generateErrorMessage(agentType, error, errorType);
        return new SubAgentCreationException(message, error);
    }
    
    /**
     * 处理代理池错误
     */
    public void handlePoolError(String poolType, String operation, Throwable error) {
        String errorType = classifyError(error);
        recordError("pool_" + poolType + "_" + operation, errorType);
        
        LLMLogUtils.log(LogLevel.ERROR, LOG_PREFIX + " 代理池错误: " + poolType + 
            ", 操作: " + operation + ", 错误类型: " + errorType, error);
    }
    
    /**
     * 分类错误类型
     */
    private String classifyError(Throwable error) {
        if (error instanceof TimeoutException || error instanceof SocketTimeoutException) {
            return "TIMEOUT";
        } else if (error instanceof IOException) {
            return "NETWORK";
        } else if (error instanceof InterruptedException) {
            return "INTERRUPTED";
        } else if (error instanceof IllegalArgumentException || error instanceof IllegalStateException) {
            return "VALIDATION";
        } else if (error instanceof OutOfMemoryError) {
            return "MEMORY";
        } else if (error instanceof SubAgentCreationException) {
            return "CREATION";
        } else {
            return "UNKNOWN";
        }
    }
    
    /**
     * 检查错误是否可重试
     */
    private boolean isRetryableError(Throwable error) {
        String errorType = classifyError(error);
        
        switch (errorType) {
            case "TIMEOUT":
            case "NETWORK":
            case "INTERRUPTED":
                return true;
            case "VALIDATION":
            case "MEMORY":
            case "CREATION":
                return false;
            default:
                // 未知错误，谨慎起见不重试
                return false;
        }
    }
    
    /**
     * 安排重试
     */
    private <R extends SubAgentResult> CompletableFuture<R> scheduleRetry(
            SubAgentTask<R> task, int retryCount) {
        
        long delayMs = calculateRetryDelay(retryCount);
        
        LLMLogUtils.log(LogLevel.INFO, LOG_PREFIX + " 安排重试任务: " + task.getTaskId() + 
            ", 重试次数: " + retryCount + ", 延迟: " + delayMs + "ms");
        
        CompletableFuture<R> future = new CompletableFuture<>();
        
        // 使用CompletableFuture的延迟执行
        CompletableFuture.delayedExecutor(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .execute(() -> {
                try {
                    // 这里应该重新提交任务到管理器
                    // 由于循环依赖问题，这里先返回一个占位结果
                    future.complete(createRetryResult(task, retryCount));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
        
        return future;
    }
    
    /**
     * 计算重试延迟时间
     */
    private long calculateRetryDelay(int retryCount) {
        long delay = (long) (baseRetryDelayMs * Math.pow(retryBackoffMultiplier, retryCount - 1));
        return Math.min(delay, maxRetryDelayMs);
    }
    
    /**
     * 记录错误统计
     */
    private void recordError(String key, String errorType) {
        errorCounts.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
        lastErrorTimes.put(key, System.currentTimeMillis());
        
        // 记录到性能日志
        LogManager.getInstance().performance("SubAgent Error Recorded", 
            Map.of(
                "error_key", key,
                "error_type", errorType,
                "error_count", errorCounts.get(key).get(),
                "timestamp", System.currentTimeMillis()
            ));
    }
    
    /**
     * 生成错误信息
     */
    private String generateErrorMessage(String agentType, Throwable error, String errorType) {
        StringBuilder message = new StringBuilder();
        message.append("Failed to create sub-agent of type: ").append(agentType);
        message.append(", Error type: ").append(errorType);
        
        if (error.getMessage() != null) {
            message.append(", Details: ").append(error.getMessage());
        }
        
        // 添加错误统计信息
        String key = "creation_" + agentType;
        AtomicInteger count = errorCounts.get(key);
        if (count != null && count.get() > 1) {
            message.append(" (Error count: ").append(count.get()).append(")");
        }
        
        return message.toString();
    }
    
    /**
     * 创建失败结果
     */
    @SuppressWarnings("unchecked")
    private <R extends SubAgentResult> R createFailureResult(SubAgentTask<R> task, Throwable error) {
        // 这里需要根据具体的结果类型创建失败结果
        // 由于泛型限制，这里使用一个通用的实现
        return (R) new SubAgentResult() {
            {
                setSuccess(false);
                setError("Task execution failed: " + error.getMessage());
                setTotalProcessingTimeMs(System.currentTimeMillis() - task.getCreatedTime());
                getMetadata().put("error_type", classifyError(error));
                getMetadata().put("retry_count", task.getRetryCount());
            }
        };
    }
    
    /**
     * 创建重试结果
     */
    @SuppressWarnings("unchecked")
    private <R extends SubAgentResult> R createRetryResult(SubAgentTask<R> task, int retryCount) {
        return (R) new SubAgentResult() {
            {
                setSuccess(false);
                setError("Task scheduled for retry #" + retryCount);
                setTotalProcessingTimeMs(0);
                getMetadata().put("retry_scheduled", true);
                getMetadata().put("retry_count", retryCount);
            }
        };
    }
    
    /**
     * 获取错误统计信息
     */
    public Map<String, Integer> getErrorStatistics() {
        Map<String, Integer> stats = new ConcurrentHashMap<>();
        errorCounts.forEach((key, count) -> stats.put(key, count.get()));
        return stats;
    }
    
    /**
     * 清理过期的错误统计
     */
    public void cleanupExpiredStats(long maxAgeMs) {
        long currentTime = System.currentTimeMillis();
        
        lastErrorTimes.entrySet().removeIf(entry -> {
            if (currentTime - entry.getValue() > maxAgeMs) {
                errorCounts.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }
    
    /**
     * 重置错误统计
     */
    public void resetStatistics() {
        errorCounts.clear();
        lastErrorTimes.clear();
        LLMLogUtils.log(LogLevel.INFO, LOG_PREFIX + " 错误统计已重置");
    }
    
    /**
     * 检查是否处于错误状态
     */
    public boolean isInErrorState(String key, int threshold, long timeWindowMs) {
        AtomicInteger count = errorCounts.get(key);
        Long lastTime = lastErrorTimes.get(key);
        
        if (count == null || lastTime == null) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        return count.get() >= threshold && (currentTime - lastTime) <= timeWindowMs;
    }
}