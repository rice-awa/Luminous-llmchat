package com.riceawa.llm.subagent;

import com.riceawa.llm.logging.LogManager;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * 回调管理器
 * 负责管理异步回调处理，集成CompletableFuture框架，提供类型安全的回调处理
 */
public class CallbackManager {
    
    private static final String LOG_PREFIX = "[CallbackManager]";
    
    // 回调注册表
    private final ConcurrentHashMap<String, CallbackRegistration<?>> callbackRegistry;
    
    // CompletableFuture注册表
    private final ConcurrentHashMap<String, CompletableFuture<? extends SubAgentResult>> futureRegistry;
    
    // 回调执行器
    private final ExecutorService callbackExecutor;
    
    // 超时管理
    private final ScheduledExecutorService timeoutScheduler;
    
    // 统计信息
    private final AtomicLong totalCallbacksRegistered;
    private final AtomicLong totalCallbacksExecuted;
    private final AtomicLong totalCallbacksFailed;
    private final AtomicLong totalCallbacksTimeout;
    
    // 配置参数
    private final long defaultCallbackTimeoutMs;
    private final int maxConcurrentCallbacks;
    
    /**
     * 构造函数
     * 
     * @param maxConcurrentCallbacks 最大并发回调数
     * @param defaultCallbackTimeoutMs 默认回调超时时间
     */
    public CallbackManager(int maxConcurrentCallbacks, long defaultCallbackTimeoutMs) {
        this.maxConcurrentCallbacks = maxConcurrentCallbacks;
        this.defaultCallbackTimeoutMs = defaultCallbackTimeoutMs;
        
        this.callbackRegistry = new ConcurrentHashMap<>();
        this.futureRegistry = new ConcurrentHashMap<>();
        
        // 创建回调执行器
        this.callbackExecutor = new ThreadPoolExecutor(
            Math.min(4, maxConcurrentCallbacks / 2),
            maxConcurrentCallbacks,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            r -> {
                Thread t = new Thread(r, "CallbackManager-Executor");
                t.setDaemon(true);
                return t;
            }
        );
        
        // 创建超时调度器
        this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CallbackManager-Timeout");
            t.setDaemon(true);
            return t;
        });
        
        // 初始化统计计数器
        this.totalCallbacksRegistered = new AtomicLong(0);
        this.totalCallbacksExecuted = new AtomicLong(0);
        this.totalCallbacksFailed = new AtomicLong(0);
        this.totalCallbacksTimeout = new AtomicLong(0);
        
        LogManager.getInstance().system(LOG_PREFIX + " 回调管理器已初始化，最大并发回调数: " + maxConcurrentCallbacks);
    }
    
    /**
     * 注册回调
     * 
     * @param taskId 任务ID
     * @param callback 回调接口
     * @param timeoutMs 超时时间
     * @return CompletableFuture
     */
    @SuppressWarnings("unchecked")
    public <R extends SubAgentResult> CompletableFuture<R> registerCallback(
            String taskId, SubAgentCallback<R> callback, long timeoutMs) {
        
        if (taskId == null || callback == null) {
            throw new IllegalArgumentException("任务ID和回调不能为空");
        }
        
        // 创建CompletableFuture
        CompletableFuture<R> future = new CompletableFuture<>();
        
        // 创建回调注册
        CallbackRegistration<R> registration = new CallbackRegistration<>(
            taskId, callback, future, timeoutMs > 0 ? timeoutMs : defaultCallbackTimeoutMs
        );
        
        // 注册回调
        callbackRegistry.put(taskId, registration);
        futureRegistry.put(taskId, future);
        
        // 设置超时处理
        if (registration.getTimeoutMs() > 0) {
            scheduleTimeout(taskId, registration.getTimeoutMs());
        }
        
        totalCallbacksRegistered.incrementAndGet();
        
        LogManager.getInstance().system( LOG_PREFIX + " 已注册回调: " + taskId + 
            ", 超时时间: " + registration.getTimeoutMs() + "ms");
        
        return future;
    }
    
    /**
     * 注册回调（使用默认超时时间）
     * 
     * @param taskId 任务ID
     * @param callback 回调接口
     * @return CompletableFuture
     */
    public <R extends SubAgentResult> CompletableFuture<R> registerCallback(
            String taskId, SubAgentCallback<R> callback) {
        return registerCallback(taskId, callback, defaultCallbackTimeoutMs);
    }
    
    /**
     * 创建简单的CompletableFuture（不使用回调接口）
     * 
     * @param taskId 任务ID
     * @param timeoutMs 超时时间
     * @return CompletableFuture
     */
    @SuppressWarnings("unchecked")
    public <R extends SubAgentResult> CompletableFuture<R> createFuture(String taskId, long timeoutMs) {
        CompletableFuture<R> future = new CompletableFuture<>();
        
        // 创建简单注册（无回调接口）
        CallbackRegistration<R> registration = new CallbackRegistration<>(
            taskId, null, future, timeoutMs > 0 ? timeoutMs : defaultCallbackTimeoutMs
        );
        
        callbackRegistry.put(taskId, registration);
        futureRegistry.put(taskId, future);
        
        // 设置超时处理
        if (registration.getTimeoutMs() > 0) {
            scheduleTimeout(taskId, registration.getTimeoutMs());
        }
        
        totalCallbacksRegistered.incrementAndGet();
        
        LogManager.getInstance().system( LOG_PREFIX + " 已创建Future: " + taskId);
        
        return future;
    }
    
    /**
     * 创建简单的CompletableFuture（使用默认超时时间）
     * 
     * @param taskId 任务ID
     * @return CompletableFuture
     */
    public <R extends SubAgentResult> CompletableFuture<R> createFuture(String taskId) {
        return createFuture(taskId, defaultCallbackTimeoutMs);
    }
    
    /**
     * 执行成功回调
     * 
     * @param taskId 任务ID
     * @param result 执行结果
     */
    @SuppressWarnings("unchecked")
    public <R extends SubAgentResult> void executeSuccessCallback(String taskId, R result) {
        CallbackRegistration<R> registration = (CallbackRegistration<R>) callbackRegistry.remove(taskId);
        CompletableFuture<R> future = (CompletableFuture<R>) futureRegistry.remove(taskId);
        
        if (registration != null) {
            callbackExecutor.submit(() -> {
                try {
                    // 完成Future
                    if (future != null && !future.isDone()) {
                        future.complete(result);
                    }
                    
                    // 执行回调
                    SubAgentCallback<R> callback = registration.getCallback();
                    if (callback != null) {
                        callback.onSuccess(taskId, result);
                    }
                    
                    totalCallbacksExecuted.incrementAndGet();
                    
                    LogManager.getInstance().system( LOG_PREFIX + " 成功回调已执行: " + taskId);
                    
                } catch (Exception e) {
                    totalCallbacksFailed.incrementAndGet();
                    LogManager.getInstance().error( LOG_PREFIX + " 成功回调执行失败: " + taskId + 
                        ", 错误: " + e.getMessage());
                    
                    // 如果Future还未完成，则设置异常
                    if (future != null && !future.isDone()) {
                        future.completeExceptionally(e);
                    }
                }
            });
        } else {
            LogManager.getInstance().system( LOG_PREFIX + " 未找到回调注册: " + taskId);
        }
    }
    
    /**
     * 执行失败回调
     * 
     * @param taskId 任务ID
     * @param error 错误信息
     * @param exception 异常对象
     */
    @SuppressWarnings("unchecked")
    public <R extends SubAgentResult> void executeFailureCallback(String taskId, String error, Throwable exception) {
        CallbackRegistration<R> registration = (CallbackRegistration<R>) callbackRegistry.remove(taskId);
        CompletableFuture<R> future = (CompletableFuture<R>) futureRegistry.remove(taskId);
        
        if (registration != null) {
            callbackExecutor.submit(() -> {
                try {
                    // 完成Future（异常）
                    if (future != null && !future.isDone()) {
                        if (exception != null) {
                            future.completeExceptionally(exception);
                        } else {
                            future.completeExceptionally(new SubAgentException(error));
                        }
                    }
                    
                    // 执行回调
                    SubAgentCallback<R> callback = registration.getCallback();
                    if (callback != null) {
                        callback.onFailure(taskId, error, exception);
                    }
                    
                    totalCallbacksExecuted.incrementAndGet();
                    
                    LogManager.getInstance().system( LOG_PREFIX + " 失败回调已执行: " + taskId);
                    
                } catch (Exception e) {
                    totalCallbacksFailed.incrementAndGet();
                    LogManager.getInstance().error( LOG_PREFIX + " 失败回调执行失败: " + taskId + 
                        ", 错误: " + e.getMessage());
                }
            });
        } else {
            LogManager.getInstance().system( LOG_PREFIX + " 未找到回调注册: " + taskId);
        }
    }
    
    /**
     * 执行超时回调
     * 
     * @param taskId 任务ID
     */
    @SuppressWarnings("unchecked")
    public <R extends SubAgentResult> void executeTimeoutCallback(String taskId) {
        CallbackRegistration<R> registration = (CallbackRegistration<R>) callbackRegistry.remove(taskId);
        CompletableFuture<R> future = (CompletableFuture<R>) futureRegistry.remove(taskId);
        
        if (registration != null) {
            callbackExecutor.submit(() -> {
                try {
                    // 完成Future（超时异常）
                    if (future != null && !future.isDone()) {
                        future.completeExceptionally(new TimeoutException("任务执行超时: " + taskId));
                    }
                    
                    // 执行回调
                    SubAgentCallback<R> callback = registration.getCallback();
                    if (callback != null) {
                        callback.onTimeout(taskId);
                    }
                    
                    totalCallbacksTimeout.incrementAndGet();
                    
                    LogManager.getInstance().system( LOG_PREFIX + " 超时回调已执行: " + taskId);
                    
                } catch (Exception e) {
                    totalCallbacksFailed.incrementAndGet();
                    LogManager.getInstance().error( LOG_PREFIX + " 超时回调执行失败: " + taskId + 
                        ", 错误: " + e.getMessage());
                }
            });
        } else {
            LogManager.getInstance().system( LOG_PREFIX + " 超时回调未找到注册（可能已完成）: " + taskId);
        }
    }
    
    /**
     * 执行取消回调
     * 
     * @param taskId 任务ID
     */
    @SuppressWarnings("unchecked")
    public <R extends SubAgentResult> void executeCancelCallback(String taskId) {
        CallbackRegistration<R> registration = (CallbackRegistration<R>) callbackRegistry.remove(taskId);
        CompletableFuture<R> future = (CompletableFuture<R>) futureRegistry.remove(taskId);
        
        if (registration != null) {
            callbackExecutor.submit(() -> {
                try {
                    // 取消Future
                    if (future != null && !future.isDone()) {
                        future.cancel(true);
                    }
                    
                    // 执行回调
                    SubAgentCallback<R> callback = registration.getCallback();
                    if (callback != null) {
                        callback.onCancelled(taskId);
                    }
                    
                    totalCallbacksExecuted.incrementAndGet();
                    
                    LogManager.getInstance().system( LOG_PREFIX + " 取消回调已执行: " + taskId);
                    
                } catch (Exception e) {
                    totalCallbacksFailed.incrementAndGet();
                    LogManager.getInstance().error( LOG_PREFIX + " 取消回调执行失败: " + taskId + 
                        ", 错误: " + e.getMessage());
                }
            });
        } else {
            LogManager.getInstance().system( LOG_PREFIX + " 取消回调未找到注册: " + taskId);
        }
    }
    
    /**
     * 执行进度回调
     * 
     * @param taskId 任务ID
     * @param progress 进度信息
     */
    @SuppressWarnings("unchecked")
    public <R extends SubAgentResult> void executeProgressCallback(String taskId, String progress) {
        CallbackRegistration<R> registration = (CallbackRegistration<R>) callbackRegistry.get(taskId);
        
        if (registration != null) {
            callbackExecutor.submit(() -> {
                try {
                    SubAgentCallback<R> callback = registration.getCallback();
                    if (callback != null) {
                        callback.onProgress(taskId, progress);
                    }
                    
                    LogManager.getInstance().system( LOG_PREFIX + " 进度回调已执行: " + taskId + 
                        ", 进度: " + progress);
                    
                } catch (Exception e) {
                    LogManager.getInstance().error( LOG_PREFIX + " 进度回调执行失败: " + taskId + 
                        ", 错误: " + e.getMessage());
                }
            });
        }
    }
    
    /**
     * 获取CompletableFuture
     * 
     * @param taskId 任务ID
     * @return CompletableFuture
     */
    @SuppressWarnings("unchecked")
    public <R extends SubAgentResult> CompletableFuture<R> getFuture(String taskId) {
        return (CompletableFuture<R>) futureRegistry.get(taskId);
    }
    
    /**
     * 检查回调是否已注册
     * 
     * @param taskId 任务ID
     * @return 是否已注册
     */
    public boolean isCallbackRegistered(String taskId) {
        return callbackRegistry.containsKey(taskId);
    }
    
    /**
     * 取消回调注册
     * 
     * @param taskId 任务ID
     * @return 是否成功取消
     */
    public boolean unregisterCallback(String taskId) {
        CallbackRegistration<?> registration = callbackRegistry.remove(taskId);
        CompletableFuture<?> future = futureRegistry.remove(taskId);
        
        if (registration != null) {
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
            
            LogManager.getInstance().system( LOG_PREFIX + " 已取消回调注册: " + taskId);
            return true;
        }
        
        return false;
    }
    
    /**
     * 获取回调统计信息
     * 
     * @return 统计信息
     */
    public CallbackStatistics getStatistics() {
        return new CallbackStatistics(
            totalCallbacksRegistered.get(),
            totalCallbacksExecuted.get(),
            totalCallbacksFailed.get(),
            totalCallbacksTimeout.get(),
            callbackRegistry.size(),
            getActiveCallbackCount()
        );
    }
    
    /**
     * 获取活跃回调数量
     * 
     * @return 活跃回调数量
     */
    private int getActiveCallbackCount() {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) callbackExecutor;
        return executor.getActiveCount();
    }
    
    /**
     * 清理过期回调
     * 
     * @return 清理的回调数量
     */
    public int cleanupExpiredCallbacks() {
        List<String> expiredTaskIds = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        
        for (Map.Entry<String, CallbackRegistration<?>> entry : callbackRegistry.entrySet()) {
            CallbackRegistration<?> registration = entry.getValue();
            if (registration.isExpired(currentTime)) {
                expiredTaskIds.add(entry.getKey());
            }
        }
        
        // 清理过期回调
        for (String taskId : expiredTaskIds) {
            executeTimeoutCallback(taskId);
        }
        
        if (!expiredTaskIds.isEmpty()) {
            LogManager.getInstance().system( LOG_PREFIX + " 清理了 " + expiredTaskIds.size() + " 个过期回调");
        }
        
        return expiredTaskIds.size();
    }
    
    /**
     * 关闭回调管理器
     */
    public void shutdown() {
        LogManager.getInstance().system( LOG_PREFIX + " 正在关闭回调管理器...");
        
        // 取消所有未完成的回调
        for (String taskId : new ArrayList<>(callbackRegistry.keySet())) {
            executeCancelCallback(taskId);
        }
        
        // 关闭执行器
        callbackExecutor.shutdown();
        try {
            if (!callbackExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                callbackExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            callbackExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // 关闭超时调度器
        timeoutScheduler.shutdown();
        try {
            if (!timeoutScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                timeoutScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            timeoutScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        LogManager.getInstance().system( LOG_PREFIX + " 回调管理器已关闭");
    }
    
    /**
     * 安排超时处理
     * 
     * @param taskId 任务ID
     * @param timeoutMs 超时时间
     */
    private void scheduleTimeout(String taskId, long timeoutMs) {
        timeoutScheduler.schedule(() -> {
            if (callbackRegistry.containsKey(taskId)) {
                executeTimeoutCallback(taskId);
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 回调注册信息
     */
    private static class CallbackRegistration<R extends SubAgentResult> {
        private final String taskId;
        private final SubAgentCallback<R> callback;
        private final CompletableFuture<R> future;
        private final long timeoutMs;
        private final long registrationTime;
        
        public CallbackRegistration(String taskId, SubAgentCallback<R> callback, 
                                  CompletableFuture<R> future, long timeoutMs) {
            this.taskId = taskId;
            this.callback = callback;
            this.future = future;
            this.timeoutMs = timeoutMs;
            this.registrationTime = System.currentTimeMillis();
        }
        
        public String getTaskId() {
            return taskId;
        }
        
        public SubAgentCallback<R> getCallback() {
            return callback;
        }
        
        public CompletableFuture<R> getFuture() {
            return future;
        }
        
        public long getTimeoutMs() {
            return timeoutMs;
        }
        
        public long getRegistrationTime() {
            return registrationTime;
        }
        
        public boolean isExpired(long currentTime) {
            return timeoutMs > 0 && (currentTime - registrationTime) > timeoutMs;
        }
    }
    
    /**
     * 回调统计信息
     */
    public static class CallbackStatistics {
        private final long totalRegistered;
        private final long totalExecuted;
        private final long totalFailed;
        private final long totalTimeout;
        private final int pendingCallbacks;
        private final int activeCallbacks;
        
        public CallbackStatistics(long totalRegistered, long totalExecuted, long totalFailed,
                                long totalTimeout, int pendingCallbacks, int activeCallbacks) {
            this.totalRegistered = totalRegistered;
            this.totalExecuted = totalExecuted;
            this.totalFailed = totalFailed;
            this.totalTimeout = totalTimeout;
            this.pendingCallbacks = pendingCallbacks;
            this.activeCallbacks = activeCallbacks;
        }
        
        public long getTotalRegistered() { return totalRegistered; }
        public long getTotalExecuted() { return totalExecuted; }
        public long getTotalFailed() { return totalFailed; }
        public long getTotalTimeout() { return totalTimeout; }
        public int getPendingCallbacks() { return pendingCallbacks; }
        public int getActiveCallbacks() { return activeCallbacks; }
        
        public double getSuccessRate() {
            long total = totalExecuted + totalFailed + totalTimeout;
            return total > 0 ? (double) totalExecuted / total : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "CallbackStatistics{registered=%d, executed=%d, failed=%d, timeout=%d, " +
                "pending=%d, active=%d, successRate=%.2f%%}",
                totalRegistered, totalExecuted, totalFailed, totalTimeout,
                pendingCallbacks, activeCallbacks, getSuccessRate() * 100
            );
        }
    }
    
    /**
     * 子代理异常类
     */
    public static class SubAgentException extends RuntimeException {
        public SubAgentException(String message) {
            super(message);
        }
        
        public SubAgentException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}