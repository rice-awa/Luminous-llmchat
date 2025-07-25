package com.riceawa.llm.core;

import com.riceawa.llm.logging.LogManager;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 并发管理器 - 管理LLM请求的并发执行和资源控制
 */
public class ConcurrencyManager {
    private static ConcurrencyManager instance;
    
    // 配置参数
    private final int maxConcurrentRequests;
    private final int queueCapacity;
    private final long requestTimeoutMs;
    private final int corePoolSize;
    private final int maximumPoolSize;
    private final long keepAliveTimeMs;
    
    // 线程池和信号量
    private final ThreadPoolExecutor executorService;
    private final Semaphore requestSemaphore;
    
    // 统计信息
    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private final AtomicInteger queuedRequests = new AtomicInteger(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong completedRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);

    // Token统计信息
    private final AtomicLong totalPromptTokens = new AtomicLong(0);
    private final AtomicLong totalCompletionTokens = new AtomicLong(0);
    private final AtomicLong totalTokens = new AtomicLong(0);
    
    private ConcurrencyManager(ConcurrencyConfig config) {
        this.maxConcurrentRequests = config.maxConcurrentRequests;
        this.queueCapacity = config.queueCapacity;
        this.requestTimeoutMs = config.requestTimeoutMs;
        this.corePoolSize = config.corePoolSize;
        this.maximumPoolSize = config.maximumPoolSize;
        this.keepAliveTimeMs = config.keepAliveTimeMs;
        
        // 创建自定义线程池
        this.executorService = new ThreadPoolExecutor(
            corePoolSize,
            maximumPoolSize,
            keepAliveTimeMs,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(queueCapacity),
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "LLM-Worker-" + threadNumber.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // 当队列满时，在调用者线程中执行
        );
        
        // 创建信号量来控制并发请求数
        this.requestSemaphore = new Semaphore(maxConcurrentRequests, true);
        
        LogManager.getInstance().log(com.riceawa.llm.logging.LogLevel.INFO, "system",
            "ConcurrencyManager initialized with config: " +
            "maxConcurrent=" + maxConcurrentRequests +
            ", queueCapacity=" + queueCapacity +
            ", corePoolSize=" + corePoolSize +
            ", maxPoolSize=" + maximumPoolSize);
    }
    
    public static synchronized void initialize(ConcurrencyConfig config) {
        if (instance != null) {
            instance.shutdown();
        }
        instance = new ConcurrencyManager(config);
    }
    
    public static ConcurrencyManager getInstance() {
        if (instance == null) {
            synchronized (ConcurrencyManager.class) {
                if (instance == null) {
                    // 使用默认配置初始化
                    initialize(ConcurrencyConfig.createDefault());
                }
            }
        }
        return instance;
    }
    
    /**
     * 提交一个LLM请求任务
     */
    public <T> CompletableFuture<T> submitRequest(Supplier<T> task, String requestId) {
        totalRequests.incrementAndGet();
        
        CompletableFuture<T> future = new CompletableFuture<>();
        
        // 检查是否可以获取信号量（非阻塞）
        if (!requestSemaphore.tryAcquire()) {
            // 如果无法立即获取信号量，说明已达到最大并发数
            queuedRequests.incrementAndGet();
            LogManager.getInstance().log(com.riceawa.llm.logging.LogLevel.DEBUG, "system",
                "Request queued due to concurrency limit: " + requestId);
        }
        
        try {
            executorService.submit(() -> {
                long startTime = System.currentTimeMillis();
                try {
                    // 获取信号量（如果之前没有获取到）
                    if (!requestSemaphore.tryAcquire(requestTimeoutMs, TimeUnit.MILLISECONDS)) {
                        future.completeExceptionally(new TimeoutException("Request timeout waiting for concurrency slot"));
                        failedRequests.incrementAndGet();
                        return;
                    }
                    
                    activeRequests.incrementAndGet();
                    queuedRequests.decrementAndGet();
                    
                    LogManager.getInstance().log(com.riceawa.llm.logging.LogLevel.DEBUG, "system",
                        "Starting LLM request: " + requestId +
                        " (active: " + activeRequests.get() + "/" + maxConcurrentRequests + ")");
                    
                    // 执行实际任务
                    T result = task.get();
                    future.complete(result);
                    completedRequests.incrementAndGet();
                    
                    long duration = System.currentTimeMillis() - startTime;
                    LogManager.getInstance().performance("LLM request completed: " + requestId,
                        java.util.Map.of(
                            "duration_ms", duration,
                            "active_requests", activeRequests.get(),
                            "queued_requests", queuedRequests.get()
                        ));
                    
                } catch (Exception e) {
                    future.completeExceptionally(e);
                    failedRequests.incrementAndGet();
                    LogManager.getInstance().error("LLM request failed: " + requestId, e);
                } finally {
                    activeRequests.decrementAndGet();
                    requestSemaphore.release();
                }
            });
        } catch (RejectedExecutionException e) {
            // 线程池队列已满
            future.completeExceptionally(new RuntimeException("Request rejected: thread pool queue is full", e));
            failedRequests.incrementAndGet();
            requestSemaphore.release(); // 释放可能已获取的信号量
        }
        
        return future;
    }
    
    /**
     * 记录token使用情况
     */
    public void recordTokenUsage(int promptTokens, int completionTokens, int totalTokensUsed) {
        if (promptTokens > 0) {
            totalPromptTokens.addAndGet(promptTokens);
        }
        if (completionTokens > 0) {
            totalCompletionTokens.addAndGet(completionTokens);
        }
        if (totalTokensUsed > 0) {
            totalTokens.addAndGet(totalTokensUsed);
        }
    }

    /**
     * 获取当前统计信息
     */
    public ConcurrencyStats getStats() {
        return new ConcurrencyStats(
            activeRequests.get(),
            queuedRequests.get(),
            totalRequests.get(),
            completedRequests.get(),
            failedRequests.get(),
            executorService.getPoolSize(),
            executorService.getActiveCount(),
            executorService.getQueue().size(),
            totalPromptTokens.get(),
            totalCompletionTokens.get(),
            totalTokens.get()
        );
    }
    
    /**
     * 检查是否健康
     */
    public boolean isHealthy() {
        return !executorService.isShutdown() && 
               activeRequests.get() <= maxConcurrentRequests &&
               executorService.getQueue().size() < queueCapacity * 0.9; // 队列使用率不超过90%
    }
    
    /**
     * 关闭并发管理器
     */
    public void shutdown() {
        LogManager.getInstance().log(com.riceawa.llm.logging.LogLevel.INFO, "system",
            "Shutting down ConcurrencyManager...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 并发配置类
     */
    public static class ConcurrencyConfig {
        public final int maxConcurrentRequests;
        public final int queueCapacity;
        public final long requestTimeoutMs;
        public final int corePoolSize;
        public final int maximumPoolSize;
        public final long keepAliveTimeMs;
        
        public ConcurrencyConfig(int maxConcurrentRequests, int queueCapacity, long requestTimeoutMs,
                               int corePoolSize, int maximumPoolSize, long keepAliveTimeMs) {
            this.maxConcurrentRequests = maxConcurrentRequests;
            this.queueCapacity = queueCapacity;
            this.requestTimeoutMs = requestTimeoutMs;
            this.corePoolSize = corePoolSize;
            this.maximumPoolSize = maximumPoolSize;
            this.keepAliveTimeMs = keepAliveTimeMs;
        }
        
        public static ConcurrencyConfig createDefault() {
            return new ConcurrencyConfig(
                10,    // maxConcurrentRequests - 最大并发请求数
                50,    // queueCapacity - 队列容量
                30000, // requestTimeoutMs - 请求超时时间(30秒)
                5,     // corePoolSize - 核心线程数
                20,    // maximumPoolSize - 最大线程数
                60000  // keepAliveTimeMs - 线程保活时间(60秒)
            );
        }
    }
    
    /**
     * 统计信息类
     */
    public static class ConcurrencyStats {
        public final int activeRequests;
        public final int queuedRequests;
        public final long totalRequests;
        public final long completedRequests;
        public final long failedRequests;
        public final int poolSize;
        public final int activeThreads;
        public final int queueSize;

        // Token统计
        public final long totalPromptTokens;
        public final long totalCompletionTokens;
        public final long totalTokens;

        public ConcurrencyStats(int activeRequests, int queuedRequests, long totalRequests,
                              long completedRequests, long failedRequests, int poolSize,
                              int activeThreads, int queueSize, long totalPromptTokens,
                              long totalCompletionTokens, long totalTokens) {
            this.activeRequests = activeRequests;
            this.queuedRequests = queuedRequests;
            this.totalRequests = totalRequests;
            this.completedRequests = completedRequests;
            this.failedRequests = failedRequests;
            this.poolSize = poolSize;
            this.activeThreads = activeThreads;
            this.queueSize = queueSize;
            this.totalPromptTokens = totalPromptTokens;
            this.totalCompletionTokens = totalCompletionTokens;
            this.totalTokens = totalTokens;
        }
        
        public double getSuccessRate() {
            return totalRequests > 0 ? (double) completedRequests / totalRequests : 0.0;
        }
        
        public double getFailureRate() {
            return totalRequests > 0 ? (double) failedRequests / totalRequests : 0.0;
        }

        public double getAveragePromptTokensPerRequest() {
            return completedRequests > 0 ? (double) totalPromptTokens / completedRequests : 0.0;
        }

        public double getAverageCompletionTokensPerRequest() {
            return completedRequests > 0 ? (double) totalCompletionTokens / completedRequests : 0.0;
        }

        public double getAverageTotalTokensPerRequest() {
            return completedRequests > 0 ? (double) totalTokens / completedRequests : 0.0;
        }

        public double getTokenEfficiency() {
            return totalPromptTokens > 0 ? (double) totalCompletionTokens / totalPromptTokens : 0.0;
        }
    }
}
