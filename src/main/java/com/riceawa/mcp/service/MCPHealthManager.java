package com.riceawa.mcp.service;

import com.riceawa.llm.logging.LogManager;
import com.riceawa.mcp.model.MCPClientStatus;
import com.riceawa.mcp.config.MCPConfig;
import com.riceawa.mcp.config.MCPServerConfig;
import com.riceawa.mcp.exception.MCPException;
import com.riceawa.mcp.exception.MCPErrorType;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP健康检查和自动恢复管理器
 * 定期检查MCP客户端连接状态，实现自动重连和故障恢复
 */
public class MCPHealthManager {
    private static final LogManager logger = LogManager.getInstance();
    
    // 健康检查配置
    private final long healthCheckIntervalMs;
    private final long connectionTimeoutMs;
    private final int maxConsecutiveFailures;
    private final long recoveryDelayMs;
    
    // 运行状态
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private ScheduledExecutorService healthCheckExecutor;
    private ScheduledExecutorService recoveryExecutor;
    
    // 监控数据
    private final ConcurrentHashMap<String, HealthStatus> clientHealthStatus = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> consecutiveFailures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> lastSuccessTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> lastFailureTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<Void>> recoveryTasks = new ConcurrentHashMap<>();
    
    // 依赖组件
    private MCPClientManager clientManager;
    private MCPConfig config;
    
    /**
     * 健康状态枚举
     */
    public enum HealthStatus {
        HEALTHY("健康"),
        DEGRADED("降级"),
        UNHEALTHY("不健康"),
        RECOVERING("恢复中"),
        DISABLED("已禁用");
        
        private final String displayName;
        
        HealthStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    /**
     * 健康检查结果
     */
    public static class HealthCheckResult {
        private final String clientName;
        private final HealthStatus status;
        private final long responseTimeMs;
        private final String message;
        private final Throwable error;
        
        public HealthCheckResult(String clientName, HealthStatus status, long responseTimeMs, String message) {
            this.clientName = clientName;
            this.status = status;
            this.responseTimeMs = responseTimeMs;
            this.message = message;
            this.error = null;
        }
        
        public HealthCheckResult(String clientName, HealthStatus status, long responseTimeMs, String message, Throwable error) {
            this.clientName = clientName;
            this.status = status;
            this.responseTimeMs = responseTimeMs;
            this.message = message;
            this.error = error;
        }
        
        // Getters
        public String getClientName() { return clientName; }
        public HealthStatus getStatus() { return status; }
        public long getResponseTimeMs() { return responseTimeMs; }
        public String getMessage() { return message; }
        public Throwable getError() { return error; }
        public boolean isHealthy() { return status == HealthStatus.HEALTHY; }
    }
    
    public MCPHealthManager(long healthCheckIntervalMs, long connectionTimeoutMs, 
                           int maxConsecutiveFailures, long recoveryDelayMs) {
        this.healthCheckIntervalMs = healthCheckIntervalMs;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.maxConsecutiveFailures = maxConsecutiveFailures;
        this.recoveryDelayMs = recoveryDelayMs;
    }
    
    /**
     * 初始化健康管理器
     */
    public void initialize(MCPClientManager clientManager, MCPConfig config) {
        this.clientManager = clientManager;
        this.config = config;
        
        // 初始化客户端健康状态
        for (MCPServerConfig serverConfig : config.getEnabledServers()) {
            String clientName = serverConfig.getName();
            clientHealthStatus.put(clientName, HealthStatus.HEALTHY);
            consecutiveFailures.put(clientName, new AtomicInteger(0));
            lastSuccessTime.put(clientName, new AtomicLong(System.currentTimeMillis()));
            lastFailureTime.put(clientName, new AtomicLong(0));
        }
        
        logger.logInfo(
            "MCP健康管理器已初始化",
            String.format("监控 %d 个客户端，检查间隔: %dms", 
                         clientHealthStatus.size(), healthCheckIntervalMs),
            "clientCount", String.valueOf(clientHealthStatus.size()),
            "healthCheckInterval", String.valueOf(healthCheckIntervalMs)
        );
    }
    
    /**
     * 启动健康检查
     */
    public synchronized void start() {
        if (isRunning.get()) {
            logger.logWarn("MCP健康检查服务", "健康检查服务已经在运行中");
            return;
        }
        
        // 创建执行器
        healthCheckExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "MCP-HealthCheck");
            t.setDaemon(true);
            return t;
        });
        
        recoveryExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "MCP-Recovery");
            t.setDaemon(true);
            return t;
        });
        
        // 启动定期健康检查
        healthCheckExecutor.scheduleWithFixedDelay(
            this::performHealthChecks,
            0, // 立即开始
            healthCheckIntervalMs,
            TimeUnit.MILLISECONDS
        );
        
        isRunning.set(true);
        
        logger.logInfo(
            "MCP健康检查服务已启动",
            String.format("检查间隔: %dms, 超时: %dms", healthCheckIntervalMs, connectionTimeoutMs)
        );
    }
    
    /**
     * 停止健康检查
     */
    public synchronized void stop() {
        if (!isRunning.get()) {
            return;
        }
        
        isRunning.set(false);
        
        // 停止所有恢复任务
        for (CompletableFuture<Void> recoveryTask : recoveryTasks.values()) {
            recoveryTask.cancel(true);
        }
        recoveryTasks.clear();
        
        // 关闭执行器
        shutdownExecutor(healthCheckExecutor, "健康检查");
        shutdownExecutor(recoveryExecutor, "恢复任务");
        
        logger.logInfo("MCP健康检查服务已停止", "所有健康检查和恢复任务已停止");
    }
    
    /**
     * 安全关闭执行器
     */
    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        logger.logWarn("MCP健康检查服务", name + "执行器无法正常关闭");
                    }
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 执行所有客户端的健康检查
     */
    private void performHealthChecks() {
        if (!isRunning.get() || clientManager == null) {
            return;
        }
        
        try {
            Set<String> clientNames = clientHealthStatus.keySet();
            
            // 并行执行健康检查
            List<CompletableFuture<HealthCheckResult>> futures = clientNames.stream()
                .map(this::performSingleHealthCheck)
                .toList();
            
            // 等待所有检查完成
            CompletableFuture<Void> allChecks = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
            );
            
            // 设置超时
            allChecks.orTimeout(connectionTimeoutMs * 2, TimeUnit.MILLISECONDS)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        logger.logWarn(
                            "MCP健康检查",
                            "健康检查批处理超时或失败: " + throwable.getMessage()
                        );
                    }
                });
            
        } catch (Exception e) {
            logger.logError(
                "MCP健康检查失败",
                "执行健康检查时发生异常: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * 执行单个客户端的健康检查
     */
    private CompletableFuture<HealthCheckResult> performSingleHealthCheck(String clientName) {
        long startTime = System.currentTimeMillis();
        
        return CompletableFuture
            .supplyAsync(() -> {
                try {
                    // 获取客户端状态
                    MCPClientStatus status = clientManager.getClientStatus(clientName);
                    
                    if (status == null) {
                        return new HealthCheckResult(clientName, HealthStatus.UNHEALTHY, 
                                                   System.currentTimeMillis() - startTime,
                                                   "客户端不存在");
                    }
                    
                    // 检查连接状态
                    if (!status.isConnected()) {
                        return new HealthCheckResult(clientName, HealthStatus.UNHEALTHY,
                                                   System.currentTimeMillis() - startTime,
                                                   "客户端未连接");
                    }
                    
                    // 执行ping测试
                    MCPClient client = clientManager.getClient(clientName);
                    boolean pingSuccess = false;
                    if (client != null) {
                        try {
                            pingSuccess = client.ping().get(connectionTimeoutMs, TimeUnit.MILLISECONDS);
                        } catch (Exception e) {
                            pingSuccess = false;
                        }
                    }
                    long responseTime = System.currentTimeMillis() - startTime;
                    
                    if (pingSuccess) {
                        return new HealthCheckResult(clientName, HealthStatus.HEALTHY,
                                                   responseTime, "连接正常");
                    } else {
                        return new HealthCheckResult(clientName, HealthStatus.DEGRADED,
                                                   responseTime, "Ping失败，但连接存在");
                    }
                    
                } catch (Exception e) {
                    long responseTime = System.currentTimeMillis() - startTime;
                    return new HealthCheckResult(clientName, HealthStatus.UNHEALTHY,
                                               responseTime, "健康检查异常: " + e.getMessage(), e);
                }
            }, healthCheckExecutor)
            .orTimeout(connectionTimeoutMs, TimeUnit.MILLISECONDS)
            .handle((result, throwable) -> {
                long responseTime = System.currentTimeMillis() - startTime;
                
                if (throwable != null) {
                    String message = throwable instanceof TimeoutException ? 
                        "健康检查超时" : "健康检查失败: " + throwable.getMessage();
                    result = new HealthCheckResult(clientName, HealthStatus.UNHEALTHY,
                                                 responseTime, message, throwable);
                }
                
                // 处理检查结果
                handleHealthCheckResult(result);
                return result;
            });
    }
    
    /**
     * 处理健康检查结果
     */
    private void handleHealthCheckResult(HealthCheckResult result) {
        String clientName = result.getClientName();
        HealthStatus newStatus = result.getStatus();
        
        // 更新健康状态
        HealthStatus oldStatus = clientHealthStatus.put(clientName, newStatus);
        
        if (result.isHealthy()) {
            // 健康状态 - 重置失败计数器
            consecutiveFailures.get(clientName).set(0);
            lastSuccessTime.get(clientName).set(System.currentTimeMillis());
            
            // 如果之前是不健康状态，记录恢复日志
            if (oldStatus != HealthStatus.HEALTHY) {
                logger.logInfo(
                    "MCP客户端恢复健康",
                    String.format("客户端 %s 已恢复健康状态，响应时间: %dms", 
                                 clientName, result.getResponseTimeMs()),
                    "clientName", clientName,
                    "responseTime", String.valueOf(result.getResponseTimeMs()),
                    "previousStatus", oldStatus != null ? oldStatus.name() : "UNKNOWN"
                );
            }
        } else {
            // 不健康状态 - 增加失败计数
            int failures = consecutiveFailures.get(clientName).incrementAndGet();
            lastFailureTime.get(clientName).set(System.currentTimeMillis());
            
            // 记录错误日志
            if (result.getError() != null) {
                logger.logError(
                    "MCP客户端健康检查失败",
                    String.format("客户端 %s 健康检查失败 (连续失败 %d 次): %s", 
                                 clientName, failures, result.getMessage()),
                    result.getError(),
                    "clientName", clientName,
                    "consecutiveFailures", String.valueOf(failures),
                    "status", newStatus.name(),
                    "responseTime", String.valueOf(result.getResponseTimeMs())
                );
            } else {
                logger.logWarn(
                    "MCP客户端健康检查失败",
                    String.format("客户端 %s 健康检查失败 (连续失败 %d 次): %s", 
                                 clientName, failures, result.getMessage()),
                    "clientName", clientName,
                    "consecutiveFailures", String.valueOf(failures),
                    "status", newStatus.name(),
                    "responseTime", String.valueOf(result.getResponseTimeMs())
                );
            }
            
            // 如果连续失败次数超过阈值，触发自动恢复
            if (failures >= maxConsecutiveFailures) {
                triggerAutoRecovery(clientName);
            }
        }
    }
    
    /**
     * 触发自动恢复
     */
    private void triggerAutoRecovery(String clientName) {
        // 检查是否已经在恢复中
        if (recoveryTasks.containsKey(clientName)) {
            return;
        }
        
        // 检查恢复执行器是否已初始化
        if (recoveryExecutor == null) {
            logger.logError(
                "手动恢复MCP客户端命令失败",
                "恢复执行器未初始化，可能健康管理器未启动",
                null,
                "clientName", clientName
            );
            return;
        }
        
        // 更新状态为恢复中
        clientHealthStatus.put(clientName, HealthStatus.RECOVERING);
        
        logger.logInfo(
            "触发MCP客户端自动恢复",
            String.format("客户端 %s 连续失败 %d 次，开始自动恢复", 
                         clientName, consecutiveFailures.get(clientName).get()),
            "clientName", clientName,
            "consecutiveFailures", String.valueOf(consecutiveFailures.get(clientName).get())
        );
        
        // 启动恢复任务
        CompletableFuture<Void> recoveryTask = CompletableFuture
            .runAsync(() -> performRecovery(clientName), recoveryExecutor)
            .whenComplete((result, throwable) -> {
                // 清理恢复任务
                recoveryTasks.remove(clientName);
                
                if (throwable != null) {
                    logger.logError(
                        "MCP客户端自动恢复失败",
                        String.format("客户端 %s 自动恢复过程中发生异常", clientName),
                        throwable,
                        "clientName", clientName
                    );
                    
                    // 恢复失败，标记为不健康
                    clientHealthStatus.put(clientName, HealthStatus.UNHEALTHY);
                }
            });
        
        recoveryTasks.put(clientName, recoveryTask);
    }
    
    /**
     * 执行恢复操作
     */
    private void performRecovery(String clientName) {
        try {
            logger.logInfo(
                "开始MCP客户端恢复",
                String.format("正在恢复客户端: %s", clientName),
                "clientName", clientName
            );
            
            // 等待恢复延迟
            if (recoveryDelayMs > 0) {
                Thread.sleep(recoveryDelayMs);
            }
            
            // 尝试重新连接
            try {
                clientManager.reconnectClient(clientName).get();
                boolean reconnected = true;
            
                if (reconnected) {
                    // 重置失败计数器
                    consecutiveFailures.get(clientName).set(0);
                    lastSuccessTime.get(clientName).set(System.currentTimeMillis());
                    
                    logger.logInfo(
                        "MCP客户端恢复成功",
                        String.format("客户端 %s 已成功重新连接", clientName),
                        "clientName", clientName
                    );
                    
                    // 执行一次健康检查确认状态
                    CompletableFuture.runAsync(() -> performSingleHealthCheck(clientName), healthCheckExecutor);
                    
                } else {
                    throw new MCPException(MCPErrorType.CONNECTION_FAILED, clientName, "重新连接失败");
                }
            } catch (Exception e) {
                throw new MCPException(MCPErrorType.CONNECTION_FAILED, clientName, "重新连接失败: " + e.getMessage());
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("恢复过程被中断", e);
        } catch (Exception e) {
            throw new RuntimeException("恢复过程失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 手动触发客户端恢复
     */
    public CompletableFuture<Boolean> recoverClient(String clientName) {
        if (!clientHealthStatus.containsKey(clientName)) {
            return CompletableFuture.completedFuture(false);
        }
        
        // 如果已经在恢复中，返回现有任务
        CompletableFuture<Void> existingTask = recoveryTasks.get(clientName);
        if (existingTask != null) {
            return existingTask.thenApply(v -> true).exceptionally(t -> false);
        }
        
        // 手动触发恢复
        triggerAutoRecovery(clientName);
        
        // 返回恢复任务的结果
        CompletableFuture<Void> recoveryTask = recoveryTasks.get(clientName);
        if (recoveryTask != null) {
            return recoveryTask.thenApply(v -> true).exceptionally(t -> false);
        }
        
        return CompletableFuture.completedFuture(false);
    }
    
    /**
     * 获取客户端健康状态
     */
    public HealthStatus getClientHealth(String clientName) {
        return clientHealthStatus.getOrDefault(clientName, HealthStatus.DISABLED);
    }
    
    /**
     * 获取所有客户端的健康状态
     */
    public Map<String, HealthStatus> getAllClientHealth() {
        return Map.copyOf(clientHealthStatus);
    }
    
    /**
     * 获取健康统计信息
     */
    public String getHealthStatistics() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== MCP健康状态统计 ===\\n");
        
        // 总体状态
        int total = clientHealthStatus.size();
        long healthy = clientHealthStatus.values().stream()
            .mapToInt(status -> status == HealthStatus.HEALTHY ? 1 : 0)
            .sum();
        long unhealthy = clientHealthStatus.values().stream()
            .mapToInt(status -> status == HealthStatus.UNHEALTHY ? 1 : 0)
            .sum();
        long recovering = clientHealthStatus.values().stream()
            .mapToInt(status -> status == HealthStatus.RECOVERING ? 1 : 0)
            .sum();
        
        sb.append(String.format("总客户端数: %d\\n", total));
        sb.append(String.format("健康: %d, 不健康: %d, 恢复中: %d\\n", healthy, unhealthy, recovering));
        
        // 详细状态
        sb.append("\\n详细状态:\\n");
        clientHealthStatus.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                String clientName = entry.getKey();
                HealthStatus status = entry.getValue();
                int failures = consecutiveFailures.get(clientName).get();
                long lastSuccess = lastSuccessTime.get(clientName).get();
                long lastFailure = lastFailureTime.get(clientName).get();
                
                sb.append(String.format("  %s: %s", clientName, status.getDisplayName()));
                if (failures > 0) {
                    sb.append(String.format(" (连续失败 %d 次)", failures));
                }
                if (lastSuccess > 0) {
                    long sinceSuccess = System.currentTimeMillis() - lastSuccess;
                    sb.append(String.format(" [上次成功: %d秒前]", sinceSuccess / 1000));
                }
                sb.append("\\n");
            });
        
        return sb.toString();
    }
    
    /**
     * 获取运行状态
     */
    public boolean isRunning() {
        return isRunning.get();
    }
    
    /**
     * 添加新客户端的监控
     */
    public void addClientMonitoring(String clientName) {
        if (!clientHealthStatus.containsKey(clientName)) {
            clientHealthStatus.put(clientName, HealthStatus.HEALTHY);
            consecutiveFailures.put(clientName, new AtomicInteger(0));
            lastSuccessTime.put(clientName, new AtomicLong(System.currentTimeMillis()));
            lastFailureTime.put(clientName, new AtomicLong(0));
            
            logger.logInfo(
                "添加MCP客户端监控",
                String.format("已开始监控客户端: %s", clientName),
                "clientName", clientName
            );
        }
    }
    
    /**
     * 移除客户端的监控
     */
    public void removeClientMonitoring(String clientName) {
        clientHealthStatus.remove(clientName);
        consecutiveFailures.remove(clientName);
        lastSuccessTime.remove(clientName);
        lastFailureTime.remove(clientName);
        
        // 取消恢复任务
        CompletableFuture<Void> recoveryTask = recoveryTasks.remove(clientName);
        if (recoveryTask != null) {
            recoveryTask.cancel(true);
        }
        
        logger.logInfo(
            "移除MCP客户端监控",
            String.format("已停止监控客户端: %s", clientName),
            "clientName", clientName
        );
    }
}