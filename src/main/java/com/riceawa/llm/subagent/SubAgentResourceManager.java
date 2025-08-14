package com.riceawa.llm.subagent;

import com.riceawa.llm.logging.LogManager;
import com.riceawa.llm.config.SubAgentFrameworkConfig;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * 子代理资源管理器
 * 负责管理子代理的内存、连接和其他系统资源
 * 提供优雅关闭和异常恢复机制
 */
public class SubAgentResourceManager {
    
    private static final String LOG_PREFIX = "[SubAgentResourceManager]";
    private static SubAgentResourceManager instance;
    
    // 资源管理配置
    private final SubAgentFrameworkConfig config;
    
    // 资源统计
    private final AtomicLong totalMemoryAllocated = new AtomicLong(0);
    private final AtomicLong totalConnectionsCreated = new AtomicLong(0);
    private final AtomicLong totalResourcesCleaned = new AtomicLong(0);
    
    // 按类型分类的资源统计
    private final Map<String, AtomicLong> typeMemoryUsage = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> typeConnectionUsage = new ConcurrentHashMap<>();
    
    // 活跃资源跟踪
    private final Map<String, ResourceInfo> activeResources = new ConcurrentHashMap<>();
    
    // 清理调度器
    private final ScheduledExecutorService cleanupScheduler;
    
    // 状态管理
    private volatile boolean isRunning = false;
    private volatile boolean isShuttingDown = false;
    
    private SubAgentResourceManager(SubAgentFrameworkConfig config) {
        this.config = config;
        
        // 初始化清理调度器
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SubAgent-ResourceManager-Cleaner");
            t.setDaemon(true);
            return t;
        });
        
        LogManager.getInstance().system(LOG_PREFIX + " 子代理资源管理器已初始化");
    }
    
    /**
     * 初始化资源管理器
     */
    public static synchronized void initialize(SubAgentFrameworkConfig config) {
        if (instance != null) {
            instance.shutdown();
        }
        instance = new SubAgentResourceManager(config);
        instance.start();
    }
    
    /**
     * 获取资源管理器实例
     */
    public static SubAgentResourceManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("SubAgentResourceManager not initialized. Call initialize() first.");
        }
        return instance;
    }
    
    /**
     * 启动资源管理器
     */
    public void start() {
        if (isRunning) {
            return;
        }
        
        isRunning = true;
        startCleanupTask();
        
        LogManager.getInstance().system(LOG_PREFIX + " 子代理资源管理器已启动");
    }
    
    /**
     * 注册资源使用
     */
    public void registerResourceUsage(String agentId, String agentType, long memoryBytes, int connections) {
        if (!isRunning || isShuttingDown) {
            return;
        }
        
        // 更新全局统计
        totalMemoryAllocated.addAndGet(memoryBytes);
        totalConnectionsCreated.addAndGet(connections);
        
        // 更新类型统计
        typeMemoryUsage.computeIfAbsent(agentType, k -> new AtomicLong(0)).addAndGet(memoryBytes);
        typeConnectionUsage.computeIfAbsent(agentType, k -> new AtomicLong(0)).addAndGet(connections);
        
        // 记录活跃资源
        activeResources.put(agentId, new ResourceInfo(agentId, agentType, memoryBytes, connections, System.currentTimeMillis()));
        
        LogManager.getInstance().system(LOG_PREFIX + " 注册资源使用 - 代理: " + agentId +
            ", 类型: " + agentType + ", 内存: " + memoryBytes + " bytes, 连接: " + connections);
    }
    
    /**
     * 更新资源使用
     */
    public void updateResourceUsage(String agentId, long memoryBytes, int connections) {
        if (!isRunning || isShuttingDown) {
            return;
        }
        
        ResourceInfo info = activeResources.get(agentId);
        if (info != null) {
            // 更新全局统计
            long memoryDiff = memoryBytes - info.getMemoryBytes();
            long connectionDiff = connections - info.getConnections();
            
            totalMemoryAllocated.addAndGet(memoryDiff);
            totalConnectionsCreated.addAndGet(connectionDiff);
            
            // 更新类型统计
            typeMemoryUsage.computeIfAbsent(info.getAgentType(), k -> new AtomicLong(0)).addAndGet(memoryDiff);
            typeConnectionUsage.computeIfAbsent(info.getAgentType(), k -> new AtomicLong(0)).addAndGet(connectionDiff);
            
            // 更新活跃资源信息
            info.setMemoryBytes(memoryBytes);
            info.setConnections(connections);
            info.setLastUpdateTime(System.currentTimeMillis());
            
            LogManager.getInstance().system(LOG_PREFIX + " 更新资源使用 - 代理: " + agentId +
                ", 内存: " + memoryBytes + " bytes, 连接: " + connections);
        }
    }
    
    /**
     * 释放资源
     */
    public void releaseResources(String agentId) {
        if (!isRunning) {
            return;
        }
        
        ResourceInfo info = activeResources.remove(agentId);
        if (info != null) {
            totalResourcesCleaned.incrementAndGet();
            
            LogManager.getInstance().system(LOG_PREFIX + " 释放资源 - 代理: " + agentId +
                ", 内存: " + info.getMemoryBytes() + " bytes, 连接: " + info.getConnections());
        }
    }
    
    /**
     * 清理空闲资源
     */
    public int cleanupIdleResources(long idleTimeoutMs) {
        if (!isRunning || isShuttingDown) {
            return 0;
        }
        
        List<String> idleResourceIds = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        
        // 查找空闲资源
        for (ResourceInfo info : activeResources.values()) {
            if (currentTime - info.getLastUpdateTime() > idleTimeoutMs) {
                idleResourceIds.add(info.getAgentId());
            }
        }
        
        // 清理空闲资源
        int cleanedCount = 0;
        for (String agentId : idleResourceIds) {
            ResourceInfo info = activeResources.remove(agentId);
            if (info != null) {
                totalResourcesCleaned.incrementAndGet();
                cleanedCount++;
                
                LogManager.getInstance().system(LOG_PREFIX + " 清理空闲资源 - 代理: " + agentId);
            }
        }
        
        if (cleanedCount > 0) {
            LogManager.getInstance().system(LOG_PREFIX + " 清理了 " + cleanedCount + " 个空闲资源");
        }
        
        return cleanedCount;
    }
    
    /**
     * 获取特定类型代理的资源使用情况
     */
    public ResourceTypeStats getResourceTypeStats(String agentType) {
        long memoryUsage = typeMemoryUsage.getOrDefault(agentType, new AtomicLong(0)).get();
        long connectionUsage = typeConnectionUsage.getOrDefault(agentType, new AtomicLong(0)).get();
        
        return new ResourceTypeStats(agentType, memoryUsage, connectionUsage);
    }
    
    /**
     * 获取资源管理器统计信息
     */
    public ResourceManagerStats getStatistics() {
        Map<String, ResourceTypeStats> typeStats = new ConcurrentHashMap<>();
        for (String agentType : typeMemoryUsage.keySet()) {
            typeStats.put(agentType, getResourceTypeStats(agentType));
        }
        
        return new ResourceManagerStats(
            totalMemoryAllocated.get(),
            totalConnectionsCreated.get(),
            totalResourcesCleaned.get(),
            activeResources.size(),
            new ConcurrentHashMap<>(typeStats)
        );
    }
    
    /**
     * 执行优雅关闭
     */
    public void shutdown() {
        if (isShuttingDown) {
            return;
        }
        
        LogManager.getInstance().system(LOG_PREFIX + " 正在关闭子代理资源管理器...");
        
        isShuttingDown = true;
        isRunning = false;
        
        // 关闭清理调度器
        shutdownScheduler(cleanupScheduler, "资源清理调度器");
        
        // 清理所有活跃资源
        int remainingResources = activeResources.size();
        if (remainingResources > 0) {
            LogManager.getInstance().system(LOG_PREFIX + " 清理剩余 " + remainingResources + " 个活跃资源");
            
            for (String agentId : new ArrayList<>(activeResources.keySet())) {
                releaseResources(agentId);
            }
        }
        
        // 清理数据结构
        activeResources.clear();
        typeMemoryUsage.clear();
        typeConnectionUsage.clear();
        
        LogManager.getInstance().system(LOG_PREFIX + " 子代理资源管理器已关闭");
    }
    
    /**
     * 启动清理任务
     */
    private void startCleanupTask() {
        cleanupScheduler.scheduleWithFixedDelay(
            () -> {
                try {
                    cleanupIdleResources(config.getAgentIdleTimeoutMs());
                } catch (Exception e) {
                    LogManager.getInstance().error(LOG_PREFIX + " 清理任务执行失败", e);
                }
            },
            config.getCleanupIntervalMs(),
            config.getCleanupIntervalMs(),
            TimeUnit.MILLISECONDS
        );
        
        LogManager.getInstance().system(LOG_PREFIX + " 资源清理任务已启动，间隔: " + 
            config.getCleanupIntervalMs() + "ms");
    }
    
    /**
     * 关闭调度器
     */
    private void shutdownScheduler(ScheduledExecutorService scheduler, String name) {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                LogManager.getInstance().system(LOG_PREFIX + " 强制关闭调度器: " + name);
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
            LogManager.getInstance().system(LOG_PREFIX + " 关闭调度器被中断: " + name);
        }
    }
    
    /**
     * 检查资源管理器是否健康
     */
    public boolean isHealthy() {
        return isRunning && !isShuttingDown;
    }
    
    // Getters
    public boolean isRunning() {
        return isRunning;
    }
    
    public boolean isShuttingDown() {
        return isShuttingDown;
    }
    
    /**
     * 资源信息类
     */
    private static class ResourceInfo {
        private final String agentId;
        private final String agentType;
        private long memoryBytes;
        private int connections;
        private long lastUpdateTime;
        
        public ResourceInfo(String agentId, String agentType, long memoryBytes, int connections, long lastUpdateTime) {
            this.agentId = agentId;
            this.agentType = agentType;
            this.memoryBytes = memoryBytes;
            this.connections = connections;
            this.lastUpdateTime = lastUpdateTime;
        }
        
        // Getters and Setters
        public String getAgentId() { return agentId; }
        public String getAgentType() { return agentType; }
        public long getMemoryBytes() { return memoryBytes; }
        public void setMemoryBytes(long memoryBytes) { this.memoryBytes = memoryBytes; }
        public int getConnections() { return connections; }
        public void setConnections(int connections) { this.connections = connections; }
        public long getLastUpdateTime() { return lastUpdateTime; }
        public void setLastUpdateTime(long lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
    }
    
    /**
     * 资源类型统计信息
     */
    public static class ResourceTypeStats {
        private final String agentType;
        private final long memoryUsageBytes;
        private final long connectionUsage;
        
        public ResourceTypeStats(String agentType, long memoryUsageBytes, long connectionUsage) {
            this.agentType = agentType;
            this.memoryUsageBytes = memoryUsageBytes;
            this.connectionUsage = connectionUsage;
        }
        
        // Getters
        public String getAgentType() { return agentType; }
        public long getMemoryUsageBytes() { return memoryUsageBytes; }
        public long getConnectionUsage() { return connectionUsage; }
        
        @Override
        public String toString() {
            return String.format("ResourceTypeStats{type=%s, memory=%d bytes, connections=%d}", 
                agentType, memoryUsageBytes, connectionUsage);
        }
    }
    
    /**
     * 资源管理器统计信息
     */
    public static class ResourceManagerStats {
        private final long totalMemoryAllocatedBytes;
        private final long totalConnectionsCreated;
        private final long totalResourcesCleaned;
        private final int activeResourceCount;
        private final Map<String, ResourceTypeStats> typeStats;
        
        public ResourceManagerStats(long totalMemoryAllocatedBytes, long totalConnectionsCreated,
                                  long totalResourcesCleaned, int activeResourceCount,
                                  Map<String, ResourceTypeStats> typeStats) {
            this.totalMemoryAllocatedBytes = totalMemoryAllocatedBytes;
            this.totalConnectionsCreated = totalConnectionsCreated;
            this.totalResourcesCleaned = totalResourcesCleaned;
            this.activeResourceCount = activeResourceCount;
            this.typeStats = typeStats;
        }
        
        // Getters
        public long getTotalMemoryAllocatedBytes() { return totalMemoryAllocatedBytes; }
        public long getTotalConnectionsCreated() { return totalConnectionsCreated; }
        public long getTotalResourcesCleaned() { return totalResourcesCleaned; }
        public int getActiveResourceCount() { return activeResourceCount; }
        public Map<String, ResourceTypeStats> getTypeStats() { return new ConcurrentHashMap<>(typeStats); }
        
        @Override
        public String toString() {
            return String.format(
                "ResourceManagerStats{memoryAllocated=%d bytes, connectionsCreated=%d, " +
                "resourcesCleaned=%d, activeResources=%d, types=%d}",
                totalMemoryAllocatedBytes, totalConnectionsCreated, 
                totalResourcesCleaned, activeResourceCount, typeStats.size()
            );
        }
    }
}