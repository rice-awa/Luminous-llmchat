package com.riceawa.llm.subagent;

import java.util.Map;
import java.util.HashMap;

/**
 * 子代理管理器统计信息
 * 提供管理器运行状态和性能指标的详细统计
 */
public class SubAgentManagerStatistics {
    
    private final long totalAgentsCreated;
    private final long totalAgentsDestroyed;
    private final long totalTasksRouted;
    private final long totalTasksCompleted;
    private final long totalTasksFailed;
    private final int currentActiveAgents;
    private final int currentActiveTasks;
    private final Map<String, SubAgentPool.PoolStatistics> poolStatistics;
    private final boolean isRunning;
    private final boolean isShuttingDown;
    
    /**
     * 构造函数
     */
    public SubAgentManagerStatistics(long totalAgentsCreated, long totalAgentsDestroyed,
                                   long totalTasksRouted, long totalTasksCompleted,
                                   long totalTasksFailed, int currentActiveAgents,
                                   int currentActiveTasks,
                                   Map<String, SubAgentPool.PoolStatistics> poolStatistics,
                                   boolean isRunning, boolean isShuttingDown) {
        this.totalAgentsCreated = totalAgentsCreated;
        this.totalAgentsDestroyed = totalAgentsDestroyed;
        this.totalTasksRouted = totalTasksRouted;
        this.totalTasksCompleted = totalTasksCompleted;
        this.totalTasksFailed = totalTasksFailed;
        this.currentActiveAgents = currentActiveAgents;
        this.currentActiveTasks = currentActiveTasks;
        this.poolStatistics = new HashMap<>(poolStatistics);
        this.isRunning = isRunning;
        this.isShuttingDown = isShuttingDown;
    }
    
    // 基本统计信息 Getters
    public long getTotalAgentsCreated() {
        return totalAgentsCreated;
    }
    
    public long getTotalAgentsDestroyed() {
        return totalAgentsDestroyed;
    }
    
    public long getTotalTasksRouted() {
        return totalTasksRouted;
    }
    
    public long getTotalTasksCompleted() {
        return totalTasksCompleted;
    }
    
    public long getTotalTasksFailed() {
        return totalTasksFailed;
    }
    
    public int getCurrentActiveAgents() {
        return currentActiveAgents;
    }
    
    public int getCurrentActiveTasks() {
        return currentActiveTasks;
    }
    
    public Map<String, SubAgentPool.PoolStatistics> getPoolStatistics() {
        return new HashMap<>(poolStatistics);
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    public boolean isShuttingDown() {
        return isShuttingDown;
    }
    
    // 计算属性
    
    /**
     * 获取任务成功率
     */
    public double getTaskSuccessRate() {
        long totalTasks = totalTasksCompleted + totalTasksFailed;
        return totalTasks > 0 ? (double) totalTasksCompleted / totalTasks : 0.0;
    }
    
    /**
     * 获取任务失败率
     */
    public double getTaskFailureRate() {
        long totalTasks = totalTasksCompleted + totalTasksFailed;
        return totalTasks > 0 ? (double) totalTasksFailed / totalTasks : 0.0;
    }
    
    /**
     * 获取代理存活率
     */
    public double getAgentSurvivalRate() {
        return totalAgentsCreated > 0 ? 
            (double) (totalAgentsCreated - totalAgentsDestroyed) / totalAgentsCreated : 0.0;
    }
    
    /**
     * 获取当前代理利用率
     */
    public double getCurrentAgentUtilization() {
        return currentActiveAgents > 0 ? (double) currentActiveTasks / currentActiveAgents : 0.0;
    }
    
    /**
     * 获取总的池化代理数量
     */
    public int getTotalPooledAgents() {
        return poolStatistics.values().stream()
            .mapToInt(SubAgentPool.PoolStatistics::getTotalAgents)
            .sum();
    }
    
    /**
     * 获取总的可用代理数量
     */
    public int getTotalAvailableAgents() {
        return poolStatistics.values().stream()
            .mapToInt(SubAgentPool.PoolStatistics::getAvailableAgents)
            .sum();
    }
    
    /**
     * 获取总的借用代理数量
     */
    public int getTotalBorrowedAgents() {
        return poolStatistics.values().stream()
            .mapToInt(SubAgentPool.PoolStatistics::getBorrowedAgents)
            .sum();
    }
    
    /**
     * 获取平均池利用率
     */
    public double getAveragePoolUtilization() {
        if (poolStatistics.isEmpty()) {
            return 0.0;
        }
        
        double totalUtilization = poolStatistics.values().stream()
            .mapToDouble(SubAgentPool.PoolStatistics::getUtilizationRate)
            .sum();
        
        return totalUtilization / poolStatistics.size();
    }
    
    /**
     * 获取平均池可用性
     */
    public double getAveragePoolAvailability() {
        if (poolStatistics.isEmpty()) {
            return 0.0;
        }
        
        double totalAvailability = poolStatistics.values().stream()
            .mapToDouble(SubAgentPool.PoolStatistics::getAvailabilityRate)
            .sum();
        
        return totalAvailability / poolStatistics.size();
    }
    
    /**
     * 获取支持的代理类型数量
     */
    public int getSupportedAgentTypeCount() {
        return poolStatistics.size();
    }
    
    /**
     * 检查指定类型的池是否健康
     */
    public boolean isPoolHealthy(String agentType) {
        SubAgentPool.PoolStatistics stats = poolStatistics.get(agentType);
        return stats != null && !stats.isShutdown() && stats.getTotalAgents() >= 0;
    }
    
    /**
     * 检查所有池是否健康
     */
    public boolean areAllPoolsHealthy() {
        return poolStatistics.values().stream()
            .allMatch(stats -> !stats.isShutdown() && stats.getTotalAgents() >= 0);
    }
    
    /**
     * 获取健康池的数量
     */
    public int getHealthyPoolCount() {
        return (int) poolStatistics.values().stream()
            .filter(stats -> !stats.isShutdown() && stats.getTotalAgents() >= 0)
            .count();
    }
    
    /**
     * 获取指定类型的池统计信息
     */
    public SubAgentPool.PoolStatistics getPoolStatistics(String agentType) {
        return poolStatistics.get(agentType);
    }
    
    /**
     * 检查管理器整体健康状态
     */
    public boolean isHealthy() {
        return isRunning && !isShuttingDown && areAllPoolsHealthy();
    }
    
    /**
     * 获取性能摘要
     */
    public PerformanceSummary getPerformanceSummary() {
        return new PerformanceSummary(
            getTaskSuccessRate(),
            getTaskFailureRate(),
            getAgentSurvivalRate(),
            getCurrentAgentUtilization(),
            getAveragePoolUtilization(),
            getAveragePoolAvailability()
        );
    }
    
    @Override
    public String toString() {
        return String.format(
            "SubAgentManagerStatistics{" +
            "agentsCreated=%d, agentsDestroyed=%d, " +
            "tasksRouted=%d, tasksCompleted=%d, tasksFailed=%d, " +
            "activeAgents=%d, activeTasks=%d, " +
            "poolCount=%d, totalPooledAgents=%d, " +
            "successRate=%.2f%%, utilization=%.2f%%, " +
            "running=%s, shuttingDown=%s}",
            totalAgentsCreated, totalAgentsDestroyed,
            totalTasksRouted, totalTasksCompleted, totalTasksFailed,
            currentActiveAgents, currentActiveTasks,
            poolStatistics.size(), getTotalPooledAgents(),
            getTaskSuccessRate() * 100, getCurrentAgentUtilization() * 100,
            isRunning, isShuttingDown
        );
    }
    
    /**
     * 性能摘要内部类
     */
    public static class PerformanceSummary {
        private final double taskSuccessRate;
        private final double taskFailureRate;
        private final double agentSurvivalRate;
        private final double currentAgentUtilization;
        private final double averagePoolUtilization;
        private final double averagePoolAvailability;
        
        public PerformanceSummary(double taskSuccessRate, double taskFailureRate,
                                double agentSurvivalRate, double currentAgentUtilization,
                                double averagePoolUtilization, double averagePoolAvailability) {
            this.taskSuccessRate = taskSuccessRate;
            this.taskFailureRate = taskFailureRate;
            this.agentSurvivalRate = agentSurvivalRate;
            this.currentAgentUtilization = currentAgentUtilization;
            this.averagePoolUtilization = averagePoolUtilization;
            this.averagePoolAvailability = averagePoolAvailability;
        }
        
        // Getters
        public double getTaskSuccessRate() { return taskSuccessRate; }
        public double getTaskFailureRate() { return taskFailureRate; }
        public double getAgentSurvivalRate() { return agentSurvivalRate; }
        public double getCurrentAgentUtilization() { return currentAgentUtilization; }
        public double getAveragePoolUtilization() { return averagePoolUtilization; }
        public double getAveragePoolAvailability() { return averagePoolAvailability; }
        
        @Override
        public String toString() {
            return String.format(
                "PerformanceSummary{" +
                "taskSuccess=%.2f%%, taskFailure=%.2f%%, " +
                "agentSurvival=%.2f%%, currentUtilization=%.2f%%, " +
                "avgPoolUtilization=%.2f%%, avgPoolAvailability=%.2f%%}",
                taskSuccessRate * 100, taskFailureRate * 100,
                agentSurvivalRate * 100, currentAgentUtilization * 100,
                averagePoolUtilization * 100, averagePoolAvailability * 100
            );
        }
    }
}