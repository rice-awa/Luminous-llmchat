package com.riceawa.llm.subagent;


import com.riceawa.llm.logging.LogManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;

/**
 * 集成任务队列系统
 * 整合UniversalTaskQueue、TaskLifecycleManager、TaskMonitor和CallbackManager
 * 提供完整的异步任务处理解决方案
 */
public class IntegratedTaskQueueSystem {
    
    private static final String LOG_PREFIX = "[IntegratedTaskQueueSystem]";
    
    // 核心组件
    private final UniversalTaskQueue taskQueue;
    private final TaskLifecycleManager lifecycleManager;
    private final TaskMonitor taskMonitor;
    private final CallbackManager callbackManager;
    
    // 系统状态
    private volatile boolean isRunning;
    
    /**
     * 构造函数
     * 
     * @param maxQueueSize 最大队列大小
     * @param defaultTimeoutMs 默认超时时间
     * @param cleanupIntervalMs 清理间隔时间
     * @param maxConcurrentCallbacks 最大并发回调数
     * @param monitoringIntervalMs 监控间隔时间
     */
    public IntegratedTaskQueueSystem(int maxQueueSize, long defaultTimeoutMs, long cleanupIntervalMs,
                                   int maxConcurrentCallbacks, long monitoringIntervalMs) {
        
        // 初始化核心组件
        this.taskQueue = new UniversalTaskQueue(maxQueueSize, defaultTimeoutMs, cleanupIntervalMs);
        this.lifecycleManager = new TaskLifecycleManager();
        this.taskMonitor = new TaskMonitor(monitoringIntervalMs, 1000, TimeUnit.HOURS.toMillis(24));
        this.callbackManager = new CallbackManager(maxConcurrentCallbacks, defaultTimeoutMs);
        
        // 集成组件
        integrateComponents();
        
        this.isRunning = true;
        
        LogManager.getInstance().system( LOG_PREFIX + " 集成任务队列系统已初始化");
    }
    
    /**
     * 使用默认配置的构造函数
     */
    public IntegratedTaskQueueSystem() {
        this(1000, 300000, 60000, 50, 30000); // 默认配置
    }
    
    /**
     * 提交任务
     * 
     * @param task 任务
     * @param callback 回调接口
     * @param priority 优先级
     * @return CompletableFuture
     */
    public <R extends SubAgentResult> CompletableFuture<R> submitTask(
            SubAgentTask<R> task, SubAgentCallback<R> callback, int priority) {
        
        if (!isRunning) {
            throw new IllegalStateException("任务队列系统已关闭");
        }
        
        try {
            // 开始生命周期跟踪
            lifecycleManager.startTracking(task);
            
            // 记录任务开始
            taskMonitor.recordTaskStart(task.getTaskId(), task.getTaskType());
            
            // 注册回调
            CompletableFuture<R> future = callbackManager.registerCallback(
                task.getTaskId(), callback, task.getTimeoutMs());
            
            // 提交到队列
            boolean submitted = taskQueue.submitTask(task, priority);
            if (!submitted) {
                // 提交失败，清理资源
                lifecycleManager.stopTracking(task.getTaskId());
                callbackManager.unregisterCallback(task.getTaskId());
                
                CompletableFuture<R> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(new RuntimeException("任务提交失败：队列已满"));
                return failedFuture;
            }
            
            LogManager.getInstance().system( LOG_PREFIX + " 任务已提交: " + task.getTaskId());
            
            return future;
            
        } catch (Exception e) {
            LogManager.getInstance().system( LOG_PREFIX + " 提交任务失败: " + e.getMessage());
            
            CompletableFuture<R> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }
    
    /**
     * 提交任务（使用默认优先级）
     * 
     * @param task 任务
     * @param callback 回调接口
     * @return CompletableFuture
     */
    public <R extends SubAgentResult> CompletableFuture<R> submitTask(
            SubAgentTask<R> task, SubAgentCallback<R> callback) {
        return submitTask(task, callback, UniversalTaskQueue.TaskPriority.NORMAL.getValue());
    }
    
    /**
     * 提交任务（不使用回调接口）
     * 
     * @param task 任务
     * @param priority 优先级
     * @return CompletableFuture
     */
    public <R extends SubAgentResult> CompletableFuture<R> submitTask(
            SubAgentTask<R> task, int priority) {
        
        if (!isRunning) {
            throw new IllegalStateException("任务队列系统已关闭");
        }
        
        try {
            // 开始生命周期跟踪
            lifecycleManager.startTracking(task);
            
            // 记录任务开始
            taskMonitor.recordTaskStart(task.getTaskId(), task.getTaskType());
            
            // 创建Future
            CompletableFuture<R> future = callbackManager.createFuture(task.getTaskId(), task.getTimeoutMs());
            
            // 提交到队列
            boolean submitted = taskQueue.submitTask(task, priority);
            if (!submitted) {
                // 提交失败，清理资源
                lifecycleManager.stopTracking(task.getTaskId());
                callbackManager.unregisterCallback(task.getTaskId());
                
                CompletableFuture<R> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(new RuntimeException("任务提交失败：队列已满"));
                return failedFuture;
            }
            
            LogManager.getInstance().system( LOG_PREFIX + " 任务已提交: " + task.getTaskId());
            
            return future;
            
        } catch (Exception e) {
            LogManager.getInstance().system( LOG_PREFIX + " 提交任务失败: " + e.getMessage());
            
            CompletableFuture<R> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }
    
    /**
     * 提交任务（使用默认优先级，不使用回调接口）
     * 
     * @param task 任务
     * @return CompletableFuture
     */
    public <R extends SubAgentResult> CompletableFuture<R> submitTask(SubAgentTask<R> task) {
        return submitTask(task, UniversalTaskQueue.TaskPriority.NORMAL.getValue());
    }
    
    /**
     * 获取下一个任务
     * 
     * @return 下一个任务
     */
    public SubAgentTask<?> pollTask() {
        if (!isRunning) {
            return null;
        }
        
        SubAgentTask<?> task = taskQueue.pollTask();
        if (task != null) {
            // 更新生命周期状态
            lifecycleManager.updateTaskStatus(task.getTaskId(), SubAgentTaskStatus.PROCESSING);
        }
        
        return task;
    }
    
    /**
     * 阻塞等待下一个任务
     * 
     * @param timeoutMs 等待超时时间
     * @return 下一个任务
     */
    public SubAgentTask<?> takeTask(long timeoutMs) throws InterruptedException {
        if (!isRunning) {
            return null;
        }
        
        SubAgentTask<?> task = taskQueue.takeTask(timeoutMs);
        if (task != null) {
            // 更新生命周期状态
            lifecycleManager.updateTaskStatus(task.getTaskId(), SubAgentTaskStatus.PROCESSING);
        }
        
        return task;
    }
    
    /**
     * 完成任务
     * 
     * @param taskId 任务ID
     * @param result 任务结果
     */
    public <R extends SubAgentResult> void completeTask(String taskId, R result) {
        try {
            // 更新生命周期状态
            lifecycleManager.updateTaskStatus(taskId, SubAgentTaskStatus.COMPLETED);
            
            // 记录任务完成
            taskMonitor.recordTaskCompletion(taskId, result.isSuccess(), result.getTotalProcessingTimeMs());
            
            // 完成队列中的任务
            taskQueue.completeTask(taskId, result);
            
            // 执行成功回调
            callbackManager.executeSuccessCallback(taskId, result);
            
            // 停止生命周期跟踪
            lifecycleManager.stopTracking(taskId);
            
            LogManager.getInstance().system( LOG_PREFIX + " 任务已完成: " + taskId);
            
        } catch (Exception e) {
            LogManager.getInstance().system( LOG_PREFIX + " 完成任务失败: " + taskId + ", 错误: " + e.getMessage());
        }
    }
    
    /**
     * 标记任务失败
     * 
     * @param taskId 任务ID
     * @param error 错误信息
     * @param exception 异常对象
     */
    public void failTask(String taskId, String error, Throwable exception) {
        try {
            // 更新生命周期状态
            lifecycleManager.updateTaskStatus(taskId, SubAgentTaskStatus.FAILED);
            
            // 记录任务完成（失败）
            taskMonitor.recordTaskCompletion(taskId, false, 0);
            
            // 标记队列中的任务失败
            taskQueue.failTask(taskId, error, exception);
            
            // 执行失败回调
            callbackManager.executeFailureCallback(taskId, error, exception);
            
            // 停止生命周期跟踪
            lifecycleManager.stopTracking(taskId);
            
            LogManager.getInstance().system( LOG_PREFIX + " 任务失败: " + taskId + ", 错误: " + error);
            
        } catch (Exception e) {
            LogManager.getInstance().system( LOG_PREFIX + " 标记任务失败时出错: " + taskId + ", 错误: " + e.getMessage());
        }
    }
    
    /**
     * 取消任务
     * 
     * @param taskId 任务ID
     * @return 是否成功取消
     */
    public boolean cancelTask(String taskId) {
        try {
            // 更新生命周期状态
            boolean updated = lifecycleManager.updateTaskStatus(taskId, SubAgentTaskStatus.CANCELLED);
            if (!updated) {
                return false;
            }
            
            // 取消队列中的任务
            boolean cancelled = taskQueue.cancelTask(taskId);
            
            // 执行取消回调
            callbackManager.executeCancelCallback(taskId);
            
            // 停止生命周期跟踪
            lifecycleManager.stopTracking(taskId);
            
            LogManager.getInstance().system( LOG_PREFIX + " 任务已取消: " + taskId);
            
            return cancelled;
            
        } catch (Exception e) {
            LogManager.getInstance().system( LOG_PREFIX + " 取消任务失败: " + taskId + ", 错误: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 更新任务状态
     * 
     * @param taskId 任务ID
     * @param status 新状态
     * @return 是否成功更新
     */
    public boolean updateTaskStatus(String taskId, SubAgentTaskStatus status) {
        return lifecycleManager.updateTaskStatus(taskId, status);
    }
    
    /**
     * 更新任务进度
     * 
     * @param taskId 任务ID
     * @param progress 进度信息
     */
    public void updateTaskProgress(String taskId, String progress) {
        callbackManager.executeProgressCallback(taskId, progress);
    }
    
    /**
     * 获取任务状态
     * 
     * @param taskId 任务ID
     * @return 任务状态
     */
    public SubAgentTaskStatus getTaskStatus(String taskId) {
        return taskQueue.getTaskStatus(taskId);
    }
    
    /**
     * 获取任务
     * 
     * @param taskId 任务ID
     * @return 任务对象
     */
    public SubAgentTask<?> getTask(String taskId) {
        return taskQueue.getTask(taskId);
    }
    
    /**
     * 获取任务Future
     * 
     * @param taskId 任务ID
     * @return CompletableFuture
     */
    public <R extends SubAgentResult> CompletableFuture<R> getTaskFuture(String taskId) {
        return callbackManager.getFuture(taskId);
    }
    
    /**
     * 获取指定状态的任务列表
     * 
     * @param status 任务状态
     * @return 任务ID列表
     */
    public List<String> getTasksByStatus(SubAgentTaskStatus status) {
        return lifecycleManager.getTasksByStatus(status);
    }
    
    /**
     * 获取指定类型的任务列表
     * 
     * @param taskType 任务类型
     * @return 任务ID列表
     */
    public List<String> getTasksByType(String taskType) {
        return lifecycleManager.getTasksByType(taskType);
    }
    
    /**
     * 注册任务路由器
     * 
     * @param taskType 任务类型
     * @param router 路由器
     */
    public void registerTaskRouter(String taskType, TaskLifecycleManager.TaskRouter router) {
        lifecycleManager.registerTaskRouter(taskType, router);
    }
    
    /**
     * 添加状态监听器
     * 
     * @param listener 状态监听器
     */
    public void addStatusListener(TaskLifecycleManager.TaskStatusListener listener) {
        lifecycleManager.addStatusListener(listener);
    }
    
    /**
     * 添加监控监听器
     * 
     * @param listener 监控监听器
     */
    public void addMonitoringListener(TaskMonitor.MonitoringListener listener) {
        taskMonitor.addMonitoringListener(listener);
    }
    
    /**
     * 获取系统统计信息
     * 
     * @return 系统统计信息
     */
    public SystemStatistics getSystemStatistics() {
        return new SystemStatistics(
            taskQueue.getStatistics(),
            lifecycleManager.getStatistics(),
            taskMonitor.getStatistics(),
            callbackManager.getStatistics()
        );
    }
    
    /**
     * 获取性能报告
     * 
     * @return 性能报告
     */
    public TaskMonitor.PerformanceReport getPerformanceReport() {
        return taskMonitor.getPerformanceReport();
    }
    
    /**
     * 检查系统是否正在运行
     * 
     * @return 是否正在运行
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * 关闭系统
     */
    public void shutdown() {
        if (!isRunning) {
            return;
        }
        
        LogManager.getInstance().system( LOG_PREFIX + " 正在关闭集成任务队列系统...");
        
        isRunning = false;
        
        // 按顺序关闭组件
        try {
            taskQueue.shutdown();
        } catch (Exception e) {
            LogManager.getInstance().system( LOG_PREFIX + " 关闭任务队列失败: " + e.getMessage());
        }
        
        try {
            callbackManager.shutdown();
        } catch (Exception e) {
            LogManager.getInstance().system( LOG_PREFIX + " 关闭回调管理器失败: " + e.getMessage());
        }
        
        try {
            taskMonitor.shutdown();
        } catch (Exception e) {
            LogManager.getInstance().system( LOG_PREFIX + " 关闭任务监控器失败: " + e.getMessage());
        }
        
        LogManager.getInstance().system( LOG_PREFIX + " 集成任务队列系统已关闭");
    }
    
    /**
     * 集成各个组件
     */
    private void integrateComponents() {
        // 将任务监控器注册为生命周期监听器
        lifecycleManager.addStatusListener(taskMonitor);
        
        LogManager.getInstance().system( LOG_PREFIX + " 组件集成完成");
    }
    
    /**
     * 系统统计信息
     */
    public static class SystemStatistics {
        private final UniversalTaskQueue.QueueStatistics queueStats;
        private final TaskLifecycleManager.LifecycleStatistics lifecycleStats;
        private final TaskMonitor.MonitoringStatistics monitoringStats;
        private final CallbackManager.CallbackStatistics callbackStats;
        
        public SystemStatistics(UniversalTaskQueue.QueueStatistics queueStats,
                              TaskLifecycleManager.LifecycleStatistics lifecycleStats,
                              TaskMonitor.MonitoringStatistics monitoringStats,
                              CallbackManager.CallbackStatistics callbackStats) {
            this.queueStats = queueStats;
            this.lifecycleStats = lifecycleStats;
            this.monitoringStats = monitoringStats;
            this.callbackStats = callbackStats;
        }
        
        public UniversalTaskQueue.QueueStatistics getQueueStats() { return queueStats; }
        public TaskLifecycleManager.LifecycleStatistics getLifecycleStats() { return lifecycleStats; }
        public TaskMonitor.MonitoringStatistics getMonitoringStats() { return monitoringStats; }
        public CallbackManager.CallbackStatistics getCallbackStats() { return callbackStats; }
        
        @Override
        public String toString() {
            return String.format(
                "SystemStatistics{\nqueue=%s,\nlifecycle=%s,\nmonitoring=%s,\ncallback=%s\n}",
                queueStats, lifecycleStats, monitoringStats, callbackStats
            );
        }
    }
}