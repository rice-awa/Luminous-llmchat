package com.riceawa.llm.subagent;


import com.riceawa.llm.logging.LogManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 任务监控器
 * 提供任务统计、性能监控和健康检查功能
 */
public class TaskMonitor implements TaskLifecycleManager.TaskStatusListener {
    
    private static final String LOG_PREFIX = "[TaskMonitor]";
    
    // 性能指标
    private final ConcurrentHashMap<String, TaskMetrics> taskMetrics;
    private final ConcurrentHashMap<String, TaskTypeMetrics> taskTypeMetrics;
    
    // 统计计数器
    private final AtomicLong totalTasksProcessed;
    private final AtomicLong totalProcessingTime;
    private final AtomicLong totalSuccessfulTasks;
    private final AtomicLong totalFailedTasks;
    
    // 性能监控
    private final ScheduledExecutorService monitorScheduler;
    private final ReentrantReadWriteLock metricsLock;
    
    // 配置参数
    private final long monitoringIntervalMs;
    private final int maxMetricsHistory;
    private final long metricsRetentionMs;
    
    // 监控监听器
    private final List<MonitoringListener> monitoringListeners;
    
    /**
     * 构造函数
     * 
     * @param monitoringIntervalMs 监控间隔时间
     * @param maxMetricsHistory 最大指标历史记录数
     * @param metricsRetentionMs 指标保留时间
     */
    public TaskMonitor(long monitoringIntervalMs, int maxMetricsHistory, long metricsRetentionMs) {
        this.monitoringIntervalMs = monitoringIntervalMs;
        this.maxMetricsHistory = maxMetricsHistory;
        this.metricsRetentionMs = metricsRetentionMs;
        
        this.taskMetrics = new ConcurrentHashMap<>();
        this.taskTypeMetrics = new ConcurrentHashMap<>();
        this.totalTasksProcessed = new AtomicLong(0);
        this.totalProcessingTime = new AtomicLong(0);
        this.totalSuccessfulTasks = new AtomicLong(0);
        this.totalFailedTasks = new AtomicLong(0);
        this.metricsLock = new ReentrantReadWriteLock();
        this.monitoringListeners = new ArrayList<>();
        
        // 启动监控调度器
        this.monitorScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TaskMonitor-Scheduler");
            t.setDaemon(true);
            return t;
        });
        
        startMonitoring();
        
        LogManager.getInstance().system( LOG_PREFIX + " 任务监控器已启动，监控间隔: " + monitoringIntervalMs + "ms");
    }
    
    /**
     * 添加监控监听器
     * 
     * @param listener 监听器
     */
    public void addMonitoringListener(MonitoringListener listener) {
        synchronized (monitoringListeners) {
            monitoringListeners.add(listener);
        }
    }
    
    /**
     * 移除监控监听器
     * 
     * @param listener 监听器
     */
    public void removeMonitoringListener(MonitoringListener listener) {
        synchronized (monitoringListeners) {
            monitoringListeners.remove(listener);
        }
    }
    
    @Override
    public void onStatusChanged(String taskId, SubAgentTaskStatus oldStatus, SubAgentTaskStatus newStatus) {
        metricsLock.writeLock().lock();
        try {
            TaskMetrics metrics = taskMetrics.computeIfAbsent(taskId, k -> new TaskMetrics(taskId));
            
            // 记录状态变化
            metrics.addStatusChange(oldStatus, newStatus, System.currentTimeMillis());
            
            // 如果任务完成，更新统计信息
            if (isTerminalStatus(newStatus)) {
                updateCompletionMetrics(metrics, newStatus);
            }
            
            // 更新任务类型指标
            updateTaskTypeMetrics(metrics);
            
        } finally {
            metricsLock.writeLock().unlock();
        }
    }
    
    /**
     * 记录任务开始
     * 
     * @param taskId 任务ID
     * @param taskType 任务类型
     */
    public void recordTaskStart(String taskId, String taskType) {
        metricsLock.writeLock().lock();
        try {
            TaskMetrics metrics = taskMetrics.computeIfAbsent(taskId, k -> new TaskMetrics(taskId));
            metrics.setTaskType(taskType);
            metrics.setStartTime(System.currentTimeMillis());
            
            LogManager.getInstance().system( LOG_PREFIX + " 记录任务开始: " + taskId + ", 类型: " + taskType);
        } finally {
            metricsLock.writeLock().unlock();
        }
    }
    
    /**
     * 记录任务完成
     * 
     * @param taskId 任务ID
     * @param success 是否成功
     * @param processingTime 处理时间
     */
    public void recordTaskCompletion(String taskId, boolean success, long processingTime) {
        metricsLock.writeLock().lock();
        try {
            TaskMetrics metrics = taskMetrics.get(taskId);
            if (metrics != null) {
                metrics.setEndTime(System.currentTimeMillis());
                metrics.setProcessingTime(processingTime);
                metrics.setSuccess(success);
                
                // 更新全局统计
                totalTasksProcessed.incrementAndGet();
                totalProcessingTime.addAndGet(processingTime);
                
                if (success) {
                    totalSuccessfulTasks.incrementAndGet();
                } else {
                    totalFailedTasks.incrementAndGet();
                }
                
                LogManager.getInstance().system( LOG_PREFIX + " 记录任务完成: " + taskId + 
                    ", 成功: " + success + ", 处理时间: " + processingTime + "ms");
            }
        } finally {
            metricsLock.writeLock().unlock();
        }
    }
    
    /**
     * 获取任务指标
     * 
     * @param taskId 任务ID
     * @return 任务指标
     */
    public TaskMetrics getTaskMetrics(String taskId) {
        metricsLock.readLock().lock();
        try {
            return taskMetrics.get(taskId);
        } finally {
            metricsLock.readLock().unlock();
        }
    }
    
    /**
     * 获取任务类型指标
     * 
     * @param taskType 任务类型
     * @return 任务类型指标
     */
    public TaskTypeMetrics getTaskTypeMetrics(String taskType) {
        metricsLock.readLock().lock();
        try {
            return taskTypeMetrics.get(taskType);
        } finally {
            metricsLock.readLock().unlock();
        }
    }
    
    /**
     * 获取所有任务类型指标
     * 
     * @return 任务类型指标映射
     */
    public Map<String, TaskTypeMetrics> getAllTaskTypeMetrics() {
        metricsLock.readLock().lock();
        try {
            return new HashMap<>(taskTypeMetrics);
        } finally {
            metricsLock.readLock().unlock();
        }
    }
    
    /**
     * 获取监控统计信息
     * 
     * @return 监控统计信息
     */
    public MonitoringStatistics getStatistics() {
        metricsLock.readLock().lock();
        try {
            long totalTasks = totalTasksProcessed.get();
            long avgProcessingTime = totalTasks > 0 ? totalProcessingTime.get() / totalTasks : 0;
            double successRate = totalTasks > 0 ? (double) totalSuccessfulTasks.get() / totalTasks : 0.0;
            
            Map<String, TaskTypeMetrics> typeMetrics = new HashMap<>(taskTypeMetrics);
            
            return new MonitoringStatistics(
                totalTasks,
                totalSuccessfulTasks.get(),
                totalFailedTasks.get(),
                avgProcessingTime,
                successRate,
                typeMetrics,
                taskMetrics.size()
            );
        } finally {
            metricsLock.readLock().unlock();
        }
    }
    
    /**
     * 获取性能报告
     * 
     * @return 性能报告
     */
    public PerformanceReport getPerformanceReport() {
        metricsLock.readLock().lock();
        try {
            Map<String, TaskTypePerformance> typePerformance = new HashMap<>();
            
            for (Map.Entry<String, TaskTypeMetrics> entry : taskTypeMetrics.entrySet()) {
                TaskTypeMetrics metrics = entry.getValue();
                TaskTypePerformance performance = new TaskTypePerformance(
                    entry.getKey(),
                    metrics.getTotalTasks(),
                    metrics.getSuccessfulTasks(),
                    metrics.getFailedTasks(),
                    metrics.getAverageProcessingTime(),
                    metrics.getSuccessRate(),
                    metrics.getThroughput()
                );
                typePerformance.put(entry.getKey(), performance);
            }
            
            return new PerformanceReport(
                System.currentTimeMillis(),
                getStatistics(),
                typePerformance
            );
        } finally {
            metricsLock.readLock().unlock();
        }
    }
    
    /**
     * 清理过期指标
     * 
     * @return 清理的指标数量
     */
    public int cleanupExpiredMetrics() {
        metricsLock.writeLock().lock();
        try {
            List<String> expiredTaskIds = new ArrayList<>();
            long currentTime = System.currentTimeMillis();
            
            for (Map.Entry<String, TaskMetrics> entry : taskMetrics.entrySet()) {
                TaskMetrics metrics = entry.getValue();
                if (metrics.getEndTime() > 0 && 
                    (currentTime - metrics.getEndTime()) > metricsRetentionMs) {
                    expiredTaskIds.add(entry.getKey());
                }
            }
            
            // 移除过期指标
            for (String taskId : expiredTaskIds) {
                taskMetrics.remove(taskId);
            }
            
            if (!expiredTaskIds.isEmpty()) {
                LogManager.getInstance().system( LOG_PREFIX + " 清理了 " + expiredTaskIds.size() + " 个过期任务指标");
            }
            
            return expiredTaskIds.size();
        } finally {
            metricsLock.writeLock().unlock();
        }
    }
    
    /**
     * 关闭监控器
     */
    public void shutdown() {
        LogManager.getInstance().system( LOG_PREFIX + " 正在关闭任务监控器...");
        
        monitorScheduler.shutdown();
        try {
            if (!monitorScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                monitorScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            monitorScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        LogManager.getInstance().system( LOG_PREFIX + " 任务监控器已关闭");
    }
    
    /**
     * 启动监控
     */
    private void startMonitoring() {
        monitorScheduler.scheduleWithFixedDelay(
            this::performMonitoring,
            monitoringIntervalMs,
            monitoringIntervalMs,
            TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * 执行监控检查
     */
    private void performMonitoring() {
        try {
            // 清理过期指标
            cleanupExpiredMetrics();
            
            // 生成性能报告
            PerformanceReport report = getPerformanceReport();
            
            // 通知监听器
            notifyMonitoringListeners(report);
            
            // 检查性能异常
            checkPerformanceAnomalies(report);
            
        } catch (Exception e) {
            LogManager.getInstance().system( LOG_PREFIX + " 监控检查失败: " + e.getMessage());
        }
    }
    
    /**
     * 通知监控监听器
     * 
     * @param report 性能报告
     */
    private void notifyMonitoringListeners(PerformanceReport report) {
        synchronized (monitoringListeners) {
            for (MonitoringListener listener : monitoringListeners) {
                try {
                    listener.onPerformanceReport(report);
                } catch (Exception e) {
                    LogManager.getInstance().system( LOG_PREFIX + " 监控监听器执行失败: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 检查性能异常
     * 
     * @param report 性能报告
     */
    private void checkPerformanceAnomalies(PerformanceReport report) {
        MonitoringStatistics stats = report.getStatistics();
        
        // 检查成功率
        if (stats.getSuccessRate() < 0.8 && stats.getTotalTasks() > 10) {
            LogManager.getInstance().system( LOG_PREFIX + " 检测到低成功率: " + 
                String.format("%.2f%%", stats.getSuccessRate() * 100));
        }
        
        // 检查平均处理时间
        if (stats.getAverageProcessingTime() > 60000) { // 超过1分钟
            LogManager.getInstance().system( LOG_PREFIX + " 检测到高平均处理时间: " + 
                stats.getAverageProcessingTime() + "ms");
        }
        
        // 检查任务类型性能
        for (Map.Entry<String, TaskTypePerformance> entry : report.getTaskTypePerformance().entrySet()) {
            TaskTypePerformance perf = entry.getValue();
            if (perf.getSuccessRate() < 0.7 && perf.getTotalTasks() > 5) {
                LogManager.getInstance().system( LOG_PREFIX + " 任务类型 " + entry.getKey() + 
                    " 成功率较低: " + String.format("%.2f%%", perf.getSuccessRate() * 100));
            }
        }
    }
    
    /**
     * 更新完成指标
     * 
     * @param metrics 任务指标
     * @param finalStatus 最终状态
     */
    private void updateCompletionMetrics(TaskMetrics metrics, SubAgentTaskStatus finalStatus) {
        boolean success = finalStatus == SubAgentTaskStatus.COMPLETED;
        long processingTime = metrics.getProcessingTime();
        
        if (processingTime > 0) {
            totalTasksProcessed.incrementAndGet();
            totalProcessingTime.addAndGet(processingTime);
            
            if (success) {
                totalSuccessfulTasks.incrementAndGet();
            } else {
                totalFailedTasks.incrementAndGet();
            }
        }
    }
    
    /**
     * 更新任务类型指标
     * 
     * @param taskMetrics 任务指标
     */
    private void updateTaskTypeMetrics(TaskMetrics taskMetrics) {
        String taskType = taskMetrics.getTaskType();
        if (taskType != null) {
            TaskTypeMetrics typeMetrics = taskTypeMetrics.computeIfAbsent(
                taskType, k -> new TaskTypeMetrics(taskType));
            typeMetrics.updateFromTaskMetrics(taskMetrics);
        }
    }
    
    /**
     * 检查是否为终态
     * 
     * @param status 状态
     * @return 是否为终态
     */
    private boolean isTerminalStatus(SubAgentTaskStatus status) {
        return status == SubAgentTaskStatus.COMPLETED ||
               status == SubAgentTaskStatus.FAILED ||
               status == SubAgentTaskStatus.TIMEOUT ||
               status == SubAgentTaskStatus.CANCELLED ||
               status == SubAgentTaskStatus.MAX_ROUNDS_REACHED;
    }
    
    /**
     * 任务指标
     */
    public static class TaskMetrics {
        private final String taskId;
        private String taskType;
        private long startTime;
        private long endTime;
        private long processingTime;
        private boolean success;
        private final List<StatusChangeRecord> statusChanges;
        
        public TaskMetrics(String taskId) {
            this.taskId = taskId;
            this.statusChanges = new ArrayList<>();
        }
        
        public void addStatusChange(SubAgentTaskStatus from, SubAgentTaskStatus to, long timestamp) {
            statusChanges.add(new StatusChangeRecord(from, to, timestamp));
        }
        
        // Getters and setters
        public String getTaskId() { return taskId; }
        public String getTaskType() { return taskType; }
        public void setTaskType(String taskType) { this.taskType = taskType; }
        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }
        public long getProcessingTime() { return processingTime; }
        public void setProcessingTime(long processingTime) { this.processingTime = processingTime; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public List<StatusChangeRecord> getStatusChanges() { return new ArrayList<>(statusChanges); }
        
        public static class StatusChangeRecord {
            private final SubAgentTaskStatus from;
            private final SubAgentTaskStatus to;
            private final long timestamp;
            
            public StatusChangeRecord(SubAgentTaskStatus from, SubAgentTaskStatus to, long timestamp) {
                this.from = from;
                this.to = to;
                this.timestamp = timestamp;
            }
            
            public SubAgentTaskStatus getFrom() { return from; }
            public SubAgentTaskStatus getTo() { return to; }
            public long getTimestamp() { return timestamp; }
        }
    }
    
    /**
     * 任务类型指标
     */
    public static class TaskTypeMetrics {
        private final String taskType;
        private long totalTasks;
        private long successfulTasks;
        private long failedTasks;
        private long totalProcessingTime;
        private long lastUpdateTime;
        
        public TaskTypeMetrics(String taskType) {
            this.taskType = taskType;
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        public void updateFromTaskMetrics(TaskMetrics taskMetrics) {
            if (taskMetrics.getEndTime() > 0) {
                totalTasks++;
                if (taskMetrics.isSuccess()) {
                    successfulTasks++;
                } else {
                    failedTasks++;
                }
                totalProcessingTime += taskMetrics.getProcessingTime();
                lastUpdateTime = System.currentTimeMillis();
            }
        }
        
        public String getTaskType() { return taskType; }
        public long getTotalTasks() { return totalTasks; }
        public long getSuccessfulTasks() { return successfulTasks; }
        public long getFailedTasks() { return failedTasks; }
        public long getTotalProcessingTime() { return totalProcessingTime; }
        public long getLastUpdateTime() { return lastUpdateTime; }
        
        public double getSuccessRate() {
            return totalTasks > 0 ? (double) successfulTasks / totalTasks : 0.0;
        }
        
        public long getAverageProcessingTime() {
            return totalTasks > 0 ? totalProcessingTime / totalTasks : 0;
        }
        
        public double getThroughput() {
            long timeSpan = System.currentTimeMillis() - lastUpdateTime;
            return timeSpan > 0 ? (double) totalTasks / (timeSpan / 1000.0) : 0.0;
        }
    }
    
    /**
     * 监控监听器接口
     */
    public interface MonitoringListener {
        void onPerformanceReport(PerformanceReport report);
    }
    
    /**
     * 监控统计信息
     */
    public static class MonitoringStatistics {
        private final long totalTasks;
        private final long successfulTasks;
        private final long failedTasks;
        private final long averageProcessingTime;
        private final double successRate;
        private final Map<String, TaskTypeMetrics> taskTypeMetrics;
        private final int activeTaskCount;
        
        public MonitoringStatistics(long totalTasks, long successfulTasks, long failedTasks,
                                  long averageProcessingTime, double successRate,
                                  Map<String, TaskTypeMetrics> taskTypeMetrics, int activeTaskCount) {
            this.totalTasks = totalTasks;
            this.successfulTasks = successfulTasks;
            this.failedTasks = failedTasks;
            this.averageProcessingTime = averageProcessingTime;
            this.successRate = successRate;
            this.taskTypeMetrics = new HashMap<>(taskTypeMetrics);
            this.activeTaskCount = activeTaskCount;
        }
        
        // Getters
        public long getTotalTasks() { return totalTasks; }
        public long getSuccessfulTasks() { return successfulTasks; }
        public long getFailedTasks() { return failedTasks; }
        public long getAverageProcessingTime() { return averageProcessingTime; }
        public double getSuccessRate() { return successRate; }
        public Map<String, TaskTypeMetrics> getTaskTypeMetrics() { return new HashMap<>(taskTypeMetrics); }
        public int getActiveTaskCount() { return activeTaskCount; }
    }
    
    /**
     * 任务类型性能信息
     */
    public static class TaskTypePerformance {
        private final String taskType;
        private final long totalTasks;
        private final long successfulTasks;
        private final long failedTasks;
        private final long averageProcessingTime;
        private final double successRate;
        private final double throughput;
        
        public TaskTypePerformance(String taskType, long totalTasks, long successfulTasks,
                                 long failedTasks, long averageProcessingTime,
                                 double successRate, double throughput) {
            this.taskType = taskType;
            this.totalTasks = totalTasks;
            this.successfulTasks = successfulTasks;
            this.failedTasks = failedTasks;
            this.averageProcessingTime = averageProcessingTime;
            this.successRate = successRate;
            this.throughput = throughput;
        }
        
        // Getters
        public String getTaskType() { return taskType; }
        public long getTotalTasks() { return totalTasks; }
        public long getSuccessfulTasks() { return successfulTasks; }
        public long getFailedTasks() { return failedTasks; }
        public long getAverageProcessingTime() { return averageProcessingTime; }
        public double getSuccessRate() { return successRate; }
        public double getThroughput() { return throughput; }
    }
    
    /**
     * 性能报告
     */
    public static class PerformanceReport {
        private final long timestamp;
        private final MonitoringStatistics statistics;
        private final Map<String, TaskTypePerformance> taskTypePerformance;
        
        public PerformanceReport(long timestamp, MonitoringStatistics statistics,
                               Map<String, TaskTypePerformance> taskTypePerformance) {
            this.timestamp = timestamp;
            this.statistics = statistics;
            this.taskTypePerformance = new HashMap<>(taskTypePerformance);
        }
        
        public long getTimestamp() { return timestamp; }
        public MonitoringStatistics getStatistics() { return statistics; }
        public Map<String, TaskTypePerformance> getTaskTypePerformance() { return new HashMap<>(taskTypePerformance); }
    }
}