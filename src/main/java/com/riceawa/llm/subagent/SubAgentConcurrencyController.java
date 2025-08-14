package com.riceawa.llm.subagent;

import com.riceawa.llm.core.ConcurrencyManager;
import com.riceawa.llm.logging.LogManager;
import com.riceawa.llm.config.SubAgentFrameworkConfig;
import com.riceawa.llm.config.SubAgentTypeConfig;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * 子代理并发控制器
 * 负责管理子代理的并发执行、任务分发和负载均衡
 */
public class SubAgentConcurrencyController {
    
    private static final String LOG_PREFIX = "[SubAgentConcurrencyController]";
    private static SubAgentConcurrencyController instance;
    
    // 并发管理器
    private final ConcurrencyManager concurrencyManager;
    private final SubAgentFrameworkConfig config;
    
    // 按类型分类的并发限制
    private final Map<String, Integer> typeConcurrencyLimits = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> typeActiveCounts = new ConcurrentHashMap<>();
    
    // 负载均衡统计
    private final Map<String, AtomicInteger> typeTaskCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> typeAverageProcessingTimes = new ConcurrentHashMap<>();
    
    // 队列管理
    private final Map<String, Integer> queueSizes = new ConcurrentHashMap<>();
    
    private SubAgentConcurrencyController(SubAgentFrameworkConfig config) {
        this.config = config;
        this.concurrencyManager = ConcurrencyManager.getInstance();
        
        // 初始化类型并发限制
        initializeTypeConcurrencyLimits();
        
        LogManager.getInstance().system(LOG_PREFIX + " 子代理并发控制器已初始化");
    }
    
    /**
     * 初始化并发控制器
     */
    public static synchronized void initialize(SubAgentFrameworkConfig config) {
        instance = new SubAgentConcurrencyController(config);
    }
    
    /**
     * 获取并发控制器实例
     */
    public static SubAgentConcurrencyController getInstance() {
        if (instance == null) {
            throw new IllegalStateException("SubAgentConcurrencyController not initialized. Call initialize() first.");
        }
        return instance;
    }
    
    /**
     * 初始化类型并发限制
     */
    private void initializeTypeConcurrencyLimits() {
        Map<String, SubAgentTypeConfig> typeConfigs = config.getTypeConfigs();
        for (Map.Entry<String, SubAgentTypeConfig> entry : typeConfigs.entrySet()) {
            String type = entry.getKey();
            SubAgentTypeConfig typeConfig = entry.getValue();
            
            // 获取类型特定的并发限制
            int concurrencyLimit = typeConfig.getMaxConcurrentInstances();
            if (concurrencyLimit <= 0) {
                // 使用默认限制
                concurrencyLimit = Math.max(1, config.getMaxConcurrentSubAgents() / Math.max(1, typeConfigs.size()));
            }
            
            typeConcurrencyLimits.put(type, concurrencyLimit);
            typeActiveCounts.put(type, new AtomicInteger(0));
            typeTaskCounts.put(type, new AtomicInteger(0));
        }
        
        LogManager.getInstance().system(LOG_PREFIX + " 初始化类型并发限制: " + typeConcurrencyLimits);
    }
    
    /**
     * 检查是否可以执行指定类型的代理任务
     */
    public boolean canExecuteTask(String agentType) {
        AtomicInteger activeCount = typeActiveCounts.get(agentType);
        Integer limit = typeConcurrencyLimits.get(agentType);
        
        if (activeCount == null || limit == null) {
            // 未知类型，使用默认限制
            return true;
        }
        
        return activeCount.get() < limit;
    }
    
    /**
     * 开始执行任务
     */
    public void startTaskExecution(String agentType) {
        AtomicInteger activeCount = typeActiveCounts.get(agentType);
        AtomicInteger taskCount = typeTaskCounts.get(agentType);
        
        if (activeCount != null) {
            activeCount.incrementAndGet();
        }
        
        if (taskCount != null) {
            taskCount.incrementAndGet();
        }
    }
    
    /**
     * 完成任务执行
     */
    public void finishTaskExecution(String agentType, long processingTimeMs) {
        AtomicInteger activeCount = typeActiveCounts.get(agentType);
        if (activeCount != null) {
            activeCount.decrementAndGet();
        }
        
        // 更新平均处理时间
        updateAverageProcessingTime(agentType, processingTimeMs);
    }
    
    /**
     * 更新平均处理时间
     */
    private void updateAverageProcessingTime(String agentType, long processingTimeMs) {
        Long currentAverage = typeAverageProcessingTimes.get(agentType);
        AtomicInteger taskCount = typeTaskCounts.get(agentType);
        
        if (currentAverage == null) {
            typeAverageProcessingTimes.put(agentType, processingTimeMs);
        } else if (taskCount != null) {
            int count = taskCount.get();
            if (count > 0) {
                long newAverage = (currentAverage * (count - 1) + processingTimeMs) / count;
                typeAverageProcessingTimes.put(agentType, newAverage);
            }
        }
    }
    
    /**
     * 选择最优的代理类型来处理任务（负载均衡）
     */
    public String selectOptimalAgentType(Set<String> availableTypes) {
        if (availableTypes == null || availableTypes.isEmpty()) {
            return null;
        }
        
        String optimalType = null;
        long minLoadScore = Long.MAX_VALUE;
        
        for (String type : availableTypes) {
            // 计算负载分数（考虑活跃任务数和平均处理时间）
            AtomicInteger activeCount = typeActiveCounts.get(type);
            Long avgProcessingTime = typeAverageProcessingTimes.get(type);
            
            int activeTasks = activeCount != null ? activeCount.get() : 0;
            long avgTime = avgProcessingTime != null ? avgProcessingTime : 1000; // 默认1秒
            
            // 负载分数 = 活跃任务数 * 平均处理时间
            long loadScore = activeTasks * avgTime;
            
            if (loadScore < minLoadScore) {
                minLoadScore = loadScore;
                optimalType = type;
            }
        }
        
        return optimalType;
    }
    
    /**
     * 检查队列是否已满
     */
    public boolean isQueueFull(String agentType) {
        Integer queueSize = queueSizes.get(agentType);
        if (queueSize == null) {
            return false;
        }
        
        AtomicInteger activeCount = typeActiveCounts.get(agentType);
        Integer limit = typeConcurrencyLimits.get(agentType);
        
        if (activeCount == null || limit == null) {
            return queueSize >= config.getMaxQueueSize();
        }
        
        // 队列满的条件：队列大小 + 活跃任务数 >= 队列容量
        return (queueSize + activeCount.get()) >= config.getMaxQueueSize();
    }
    
    /**
     * 更新队列大小
     */
    public void updateQueueSize(String agentType, int size) {
        queueSizes.put(agentType, size);
    }
    
    /**
     * 获取队列满时的处理策略
     */
    public QueueFullStrategy getQueueFullStrategy() {
        // 可以根据配置返回不同的策略
        return QueueFullStrategy.REJECT; // 默认拒绝策略
    }
    
    /**
     * 获取指定类型的并发限制
     */
    public int getConcurrencyLimit(String agentType) {
        return typeConcurrencyLimits.getOrDefault(agentType, config.getMaxConcurrentSubAgents());
    }
    
    /**
     * 获取指定类型的活跃任务数
     */
    public int getActiveTaskCount(String agentType) {
        AtomicInteger count = typeActiveCounts.get(agentType);
        return count != null ? count.get() : 0;
    }
    
    /**
     * 获取指定类型的平均处理时间
     */
    public long getAverageProcessingTime(String agentType) {
        Long avgTime = typeAverageProcessingTimes.get(agentType);
        return avgTime != null ? avgTime : 0;
    }
    
    /**
     * 获取负载均衡统计信息
     */
    public LoadBalancingStats getLoadBalancingStats() {
        Map<String, TypeLoadStats> typeStats = new ConcurrentHashMap<>();
        
        for (String type : typeConcurrencyLimits.keySet()) {
            int activeCount = getActiveTaskCount(type);
            int concurrencyLimit = getConcurrencyLimit(type);
            long avgProcessingTime = getAverageProcessingTime(type);
            int taskCount = typeTaskCounts.getOrDefault(type, new AtomicInteger(0)).get();
            
            typeStats.put(type, new TypeLoadStats(
                activeCount,
                concurrencyLimit,
                avgProcessingTime,
                taskCount
            ));
        }
        
        return new LoadBalancingStats(new ConcurrentHashMap<>(typeStats));
    }
    
    /**
     * 获取并发管理器统计信息
     */
    public ConcurrencyManager.ConcurrencyStats getConcurrencyStats() {
        return concurrencyManager.getStats();
    }
    
    /**
     * 检查并发控制器是否健康
     */
    public boolean isHealthy() {
        return concurrencyManager.isHealthy();
    }
    
    /**
     * 队列满时的处理策略枚举
     */
    public enum QueueFullStrategy {
        /**
         * 拒绝新任务
         */
        REJECT,
        
        /**
         * 等待队列有空间
         */
        WAIT,
        
        /**
         * 降级处理（使用简化逻辑）
         */
        DOWNGRADE
    }
    
    /**
     * 类型负载统计信息
     */
    public static class TypeLoadStats {
        private final int activeTasks;
        private final int concurrencyLimit;
        private final long averageProcessingTimeMs;
        private final int totalTasksProcessed;
        
        public TypeLoadStats(int activeTasks, int concurrencyLimit, 
                           long averageProcessingTimeMs, int totalTasksProcessed) {
            this.activeTasks = activeTasks;
            this.concurrencyLimit = concurrencyLimit;
            this.averageProcessingTimeMs = averageProcessingTimeMs;
            this.totalTasksProcessed = totalTasksProcessed;
        }
        
        // Getters
        public int getActiveTasks() { return activeTasks; }
        public int getConcurrencyLimit() { return concurrencyLimit; }
        public long getAverageProcessingTimeMs() { return averageProcessingTimeMs; }
        public int getTotalTasksProcessed() { return totalTasksProcessed; }
        
        public double getUtilizationRate() {
            return concurrencyLimit > 0 ? (double) activeTasks / concurrencyLimit : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "TypeLoadStats{active=%d, limit=%d, avgTime=%dms, processed=%d, utilization=%.2f%%}",
                activeTasks, concurrencyLimit, averageProcessingTimeMs, totalTasksProcessed,
                getUtilizationRate() * 100
            );
        }
    }
    
    /**
     * 负载均衡统计信息
     */
    public static class LoadBalancingStats {
        private final Map<String, TypeLoadStats> typeStats;
        
        public LoadBalancingStats(Map<String, TypeLoadStats> typeStats) {
            this.typeStats = typeStats;
        }
        
        // Getters
        public Map<String, TypeLoadStats> getTypeStats() { return new ConcurrentHashMap<>(typeStats); }
        
        /**
         * 获取最空闲的代理类型
         */
        public String getIdlestAgentType() {
            String idlestType = null;
            double minUtilization = Double.MAX_VALUE;
            
            for (Map.Entry<String, TypeLoadStats> entry : typeStats.entrySet()) {
                double utilization = entry.getValue().getUtilizationRate();
                if (utilization < minUtilization) {
                    minUtilization = utilization;
                    idlestType = entry.getKey();
                }
            }
            
            return idlestType;
        }
        
        /**
         * 获取最繁忙的代理类型
         */
        public String getBusiestAgentType() {
            String busiestType = null;
            double maxUtilization = -1.0;
            
            for (Map.Entry<String, TypeLoadStats> entry : typeStats.entrySet()) {
                double utilization = entry.getValue().getUtilizationRate();
                if (utilization > maxUtilization) {
                    maxUtilization = utilization;
                    busiestType = entry.getKey();
                }
            }
            
            return busiestType;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("LoadBalancingStats{\n");
            for (Map.Entry<String, TypeLoadStats> entry : typeStats.entrySet()) {
                sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            sb.append("}");
            return sb.toString();
        }
    }
}