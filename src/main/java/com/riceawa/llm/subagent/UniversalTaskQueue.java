package com.riceawa.llm.subagent;

import com.riceawa.llm.logging.LLMLogUtils;
import com.riceawa.llm.logging.LogLevel;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 通用任务队列
 * 支持多种任务类型的优先级队列管理，提供任务注册表和状态跟踪功能
 */
public class UniversalTaskQueue {
    
    private static final String LOG_PREFIX = "[UniversalTaskQueue]";
    
    // 优先级队列，支持任务类型分类
    private final PriorityBlockingQueue<TaskWrapper> taskQueue;
    
    // 任务注册表，用于快速查找和状态跟踪
    private final ConcurrentHashMap<String, TaskWrapper> taskRegistry;
    
    // 任务类型统计
    private final ConcurrentHashMap<String, AtomicLong> taskTypeCounters;
    
    // 超时检测和清理
    private final ScheduledExecutorService timeoutScheduler;
    private final ReentrantReadWriteLock queueLock;
    
    // 配置参数
    private final int maxQueueSize;
    private final long defaultTimeoutMs;
    private final long cleanupIntervalMs;
    
    // 统计信息
    private final AtomicLong totalTasksSubmitted;
    private final AtomicLong totalTasksCompleted;
    private final AtomicLong totalTasksFailed;
    private final AtomicLong totalTasksTimeout;
    
    /**
     * 构造函数
     * 
     * @param maxQueueSize 最大队列大小
     * @param defaultTimeoutMs 默认超时时间
     * @param cleanupIntervalMs 清理间隔时间
     */
    public UniversalTaskQueue(int maxQueueSize, long defaultTimeoutMs, long cleanupIntervalMs) {
        this.maxQueueSize = maxQueueSize;
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.cleanupIntervalMs = cleanupIntervalMs;
        
        // 初始化队列和注册表
        this.taskQueue = new PriorityBlockingQueue<>(maxQueueSize, new TaskPriorityComparator());
        this.taskRegistry = new ConcurrentHashMap<>();
        this.taskTypeCounters = new ConcurrentHashMap<>();
        this.queueLock = new ReentrantReadWriteLock();
        
        // 初始化统计计数器
        this.totalTasksSubmitted = new AtomicLong(0);
        this.totalTasksCompleted = new AtomicLong(0);
        this.totalTasksFailed = new AtomicLong(0);
        this.totalTasksTimeout = new AtomicLong(0);
        
        // 启动超时检测调度器
        this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TaskQueue-TimeoutChecker");
            t.setDaemon(true);
            return t;
        });
        
        startTimeoutChecker();
        
        LLMLogUtils.log(LogLevel.INFO, LOG_PREFIX + " 通用任务队列已初始化，最大队列大小: " + maxQueueSize);
    }
    
    /**
     * 提交任务到队列
     * 
     * @param task 要提交的任务
     * @param priority 任务优先级（数值越小优先级越高）
     * @return 是否成功提交
     */
    public <R extends SubAgentResult> boolean submitTask(SubAgentTask<R> task, int priority) {
        if (task == null) {
            LLMLogUtils.log(LogLevel.ERROR, LOG_PREFIX + " 尝试提交空任务");
            return false;
        }
        
        queueLock.writeLock().lock();
        try {
            // 检查队列大小限制
            if (taskQueue.size() >= maxQueueSize) {
                LLMLogUtils.log(LogLevel.WARN, LOG_PREFIX + " 队列已满，拒绝任务: " + task.getTaskId());
                return false;
            }
            
            // 检查任务是否已存在
            if (taskRegistry.containsKey(task.getTaskId())) {
                LLMLogUtils.log(LogLevel.WARN, LOG_PREFIX + " 任务已存在: " + task.getTaskId());
                return false;
            }
            
            // 创建任务包装器
            TaskWrapper wrapper = new TaskWrapper(task, priority, System.currentTimeMillis());
            
            // 添加到队列和注册表
            taskQueue.offer(wrapper);
            taskRegistry.put(task.getTaskId(), wrapper);
            
            // 更新统计信息
            totalTasksSubmitted.incrementAndGet();
            taskTypeCounters.computeIfAbsent(task.getTaskType(), k -> new AtomicLong(0)).incrementAndGet();
            
            LLMLogUtils.log(LogLevel.INFO, LOG_PREFIX + " 任务已提交: " + task.getTaskId() + 
                ", 类型: " + task.getTaskType() + ", 优先级: " + priority);
            
            return true;
            
        } finally {
            queueLock.writeLock().unlock();
        }
    }
    
    /**
     * 提交任务到队列（使用默认优先级）
     * 
     * @param task 要提交的任务
     * @return 是否成功提交
     */
    public <R extends SubAgentResult> boolean submitTask(SubAgentTask<R> task) {
        return submitTask(task, TaskPriority.NORMAL.getValue());
    }
    
    /**
     * 从队列中取出下一个任务
     * 
     * @return 下一个任务，如果队列为空则返回null
     */
    public SubAgentTask<?> pollTask() {
        queueLock.writeLock().lock();
        try {
            TaskWrapper wrapper = taskQueue.poll();
            if (wrapper != null) {
                SubAgentTask<?> task = wrapper.getTask();
                task.setStatus(SubAgentTaskStatus.PROCESSING);
                
                LLMLogUtils.log(LogLevel.DEBUG, LOG_PREFIX + " 任务已出队: " + task.getTaskId());
                return task;
            }
            return null;
        } finally {
            queueLock.writeLock().unlock();
        }
    }
    
    /**
     * 阻塞等待下一个任务
     * 
     * @param timeoutMs 等待超时时间
     * @return 下一个任务，超时返回null
     */
    public SubAgentTask<?> takeTask(long timeoutMs) throws InterruptedException {
        queueLock.writeLock().lock();
        try {
            TaskWrapper wrapper = taskQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (wrapper != null) {
                SubAgentTask<?> task = wrapper.getTask();
                task.setStatus(SubAgentTaskStatus.PROCESSING);
                
                LLMLogUtils.log(LogLevel.DEBUG, LOG_PREFIX + " 任务已出队: " + task.getTaskId());
                return task;
            }
            return null;
        } finally {
            queueLock.writeLock().unlock();
        }
    }
    
    /**
     * 获取指定任务的状态
     * 
     * @param taskId 任务ID
     * @return 任务状态，如果任务不存在则返回null
     */
    public SubAgentTaskStatus getTaskStatus(String taskId) {
        queueLock.readLock().lock();
        try {
            TaskWrapper wrapper = taskRegistry.get(taskId);
            return wrapper != null ? wrapper.getTask().getStatus() : null;
        } finally {
            queueLock.readLock().unlock();
        }
    }
    
    /**
     * 获取指定任务
     * 
     * @param taskId 任务ID
     * @return 任务对象，如果不存在则返回null
     */
    public SubAgentTask<?> getTask(String taskId) {
        queueLock.readLock().lock();
        try {
            TaskWrapper wrapper = taskRegistry.get(taskId);
            return wrapper != null ? wrapper.getTask() : null;
        } finally {
            queueLock.readLock().unlock();
        }
    }
    
    /**
     * 完成任务并从注册表中移除
     * 
     * @param taskId 任务ID
     * @param result 任务结果
     */
    public void completeTask(String taskId, SubAgentResult result) {
        queueLock.writeLock().lock();
        try {
            TaskWrapper wrapper = taskRegistry.remove(taskId);
            if (wrapper != null) {
                SubAgentTask<?> task = wrapper.getTask();
                task.setStatus(SubAgentTaskStatus.COMPLETED);
                
                totalTasksCompleted.incrementAndGet();
                
                LLMLogUtils.log(LogLevel.INFO, LOG_PREFIX + " 任务已完成: " + taskId);
                
                // 执行回调
                executeCallback(task, result, null);
            }
        } finally {
            queueLock.writeLock().unlock();
        }
    }
    
    /**
     * 标记任务失败并从注册表中移除
     * 
     * @param taskId 任务ID
     * @param error 错误信息
     * @param exception 异常对象
     */
    public void failTask(String taskId, String error, Throwable exception) {
        queueLock.writeLock().lock();
        try {
            TaskWrapper wrapper = taskRegistry.remove(taskId);
            if (wrapper != null) {
                SubAgentTask<?> task = wrapper.getTask();
                task.setStatus(SubAgentTaskStatus.FAILED);
                task.markFailed(error);
                
                totalTasksFailed.incrementAndGet();
                
                LLMLogUtils.log(LogLevel.ERROR, LOG_PREFIX + " 任务失败: " + taskId + ", 错误: " + error);
                
                // 执行回调
                executeCallback(task, null, exception);
            }
        } finally {
            queueLock.writeLock().unlock();
        }
    }
    
    /**
     * 取消任务
     * 
     * @param taskId 任务ID
     * @return 是否成功取消
     */
    public boolean cancelTask(String taskId) {
        queueLock.writeLock().lock();
        try {
            TaskWrapper wrapper = taskRegistry.get(taskId);
            if (wrapper != null && wrapper.getTask().getStatus() == SubAgentTaskStatus.PENDING) {
                taskQueue.remove(wrapper);
                taskRegistry.remove(taskId);
                wrapper.getTask().setStatus(SubAgentTaskStatus.CANCELLED);
                
                LLMLogUtils.log(LogLevel.INFO, LOG_PREFIX + " 任务已取消: " + taskId);
                
                // 执行取消回调
                SubAgentCallback<?> callback = wrapper.getTask().getCallback();
                if (callback != null) {
                    try {
                        callback.onCancelled(taskId);
                    } catch (Exception e) {
                        LLMLogUtils.log(LogLevel.ERROR, LOG_PREFIX + " 执行取消回调失败: " + e.getMessage());
                    }
                }
                
                return true;
            }
            return false;
        } finally {
            queueLock.writeLock().unlock();
        }
    }
    
    /**
     * 获取队列大小
     * 
     * @return 当前队列中的任务数量
     */
    public int getQueueSize() {
        return taskQueue.size();
    }
    
    /**
     * 获取注册表大小
     * 
     * @return 注册表中的任务数量
     */
    public int getRegistrySize() {
        return taskRegistry.size();
    }
    
    /**
     * 检查队列是否为空
     * 
     * @return 队列是否为空
     */
    public boolean isEmpty() {
        return taskQueue.isEmpty();
    }
    
    /**
     * 检查队列是否已满
     * 
     * @return 队列是否已满
     */
    public boolean isFull() {
        return taskQueue.size() >= maxQueueSize;
    }
    
    /**
     * 获取指定类型的任务数量
     * 
     * @param taskType 任务类型
     * @return 任务数量
     */
    public long getTaskTypeCount(String taskType) {
        AtomicLong counter = taskTypeCounters.get(taskType);
        return counter != null ? counter.get() : 0;
    }
    
    /**
     * 获取所有任务类型统计
     * 
     * @return 任务类型统计映射
     */
    public Map<String, Long> getTaskTypeStatistics() {
        Map<String, Long> stats = new HashMap<>();
        taskTypeCounters.forEach((type, counter) -> stats.put(type, counter.get()));
        return stats;
    }
    
    /**
     * 获取队列统计信息
     * 
     * @return 统计信息
     */
    public QueueStatistics getStatistics() {
        return new QueueStatistics(
            totalTasksSubmitted.get(),
            totalTasksCompleted.get(),
            totalTasksFailed.get(),
            totalTasksTimeout.get(),
            getQueueSize(),
            getRegistrySize(),
            getTaskTypeStatistics()
        );
    }
    
    /**
     * 清理过期任务
     * 
     * @return 清理的任务数量
     */
    public int cleanupExpiredTasks() {
        queueLock.writeLock().lock();
        try {
            List<String> expiredTaskIds = new ArrayList<>();
            long currentTime = System.currentTimeMillis();
            
            // 查找过期任务
            for (TaskWrapper wrapper : taskRegistry.values()) {
                SubAgentTask<?> task = wrapper.getTask();
                if (task.isTimeout()) {
                    expiredTaskIds.add(task.getTaskId());
                }
            }
            
            // 清理过期任务
            for (String taskId : expiredTaskIds) {
                TaskWrapper wrapper = taskRegistry.remove(taskId);
                if (wrapper != null) {
                    taskQueue.remove(wrapper);
                    SubAgentTask<?> task = wrapper.getTask();
                    task.markTimeout();
                    
                    totalTasksTimeout.incrementAndGet();
                    
                    LLMLogUtils.log(LogLevel.WARN, LOG_PREFIX + " 任务超时被清理: " + taskId);
                    
                    // 执行超时回调
                    SubAgentCallback<?> callback = task.getCallback();
                    if (callback != null) {
                        try {
                            callback.onTimeout(taskId);
                        } catch (Exception e) {
                            LLMLogUtils.log(LogLevel.ERROR, LOG_PREFIX + " 执行超时回调失败: " + e.getMessage());
                        }
                    }
                }
            }
            
            if (!expiredTaskIds.isEmpty()) {
                LLMLogUtils.log(LogLevel.INFO, LOG_PREFIX + " 清理了 " + expiredTaskIds.size() + " 个过期任务");
            }
            
            return expiredTaskIds.size();
        } finally {
            queueLock.writeLock().unlock();
        }
    }
    
    /**
     * 关闭队列
     */
    public void shutdown() {
        LLMLogUtils.log(LogLevel.INFO, LOG_PREFIX + " 正在关闭任务队列...");
        
        // 关闭超时检测调度器
        timeoutScheduler.shutdown();
        try {
            if (!timeoutScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                timeoutScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            timeoutScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // 清理剩余任务
        queueLock.writeLock().lock();
        try {
            for (TaskWrapper wrapper : taskRegistry.values()) {
                SubAgentTask<?> task = wrapper.getTask();
                if (task.getStatus() == SubAgentTaskStatus.PENDING || 
                    task.getStatus() == SubAgentTaskStatus.PROCESSING) {
                    task.setStatus(SubAgentTaskStatus.CANCELLED);
                    
                    SubAgentCallback<?> callback = task.getCallback();
                    if (callback != null) {
                        try {
                            callback.onCancelled(task.getTaskId());
                        } catch (Exception e) {
                            LLMLogUtils.log(LogLevel.ERROR, LOG_PREFIX + " 执行关闭回调失败: " + e.getMessage());
                        }
                    }
                }
            }
            
            taskQueue.clear();
            taskRegistry.clear();
            taskTypeCounters.clear();
        } finally {
            queueLock.writeLock().unlock();
        }
        
        LLMLogUtils.log(LogLevel.INFO, LOG_PREFIX + " 任务队列已关闭");
    }
    
    /**
     * 启动超时检测器
     */
    private void startTimeoutChecker() {
        timeoutScheduler.scheduleWithFixedDelay(
            this::cleanupExpiredTasks,
            cleanupIntervalMs,
            cleanupIntervalMs,
            TimeUnit.MILLISECONDS
        );
        
        LLMLogUtils.log(LogLevel.DEBUG, LOG_PREFIX + " 超时检测器已启动，检测间隔: " + cleanupIntervalMs + "ms");
    }
    
    /**
     * 执行任务回调
     */
    @SuppressWarnings("unchecked")
    private void executeCallback(SubAgentTask<?> task, SubAgentResult result, Throwable exception) {
        SubAgentCallback callback = task.getCallback();
        if (callback != null) {
            try {
                if (result != null && result.isSuccess()) {
                    callback.onSuccess(task.getTaskId(), result);
                } else {
                    String error = result != null ? result.getError() : 
                                  (exception != null ? exception.getMessage() : "未知错误");
                    callback.onFailure(task.getTaskId(), error, exception);
                }
            } catch (Exception e) {
                LLMLogUtils.log(LogLevel.ERROR, LOG_PREFIX + " 执行回调失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 任务包装器，用于优先级队列
     */
    private static class TaskWrapper {
        private final SubAgentTask<?> task;
        private final int priority;
        private final long submitTime;
        
        public TaskWrapper(SubAgentTask<?> task, int priority, long submitTime) {
            this.task = task;
            this.priority = priority;
            this.submitTime = submitTime;
        }
        
        public SubAgentTask<?> getTask() {
            return task;
        }
        
        public int getPriority() {
            return priority;
        }
        
        public long getSubmitTime() {
            return submitTime;
        }
    }
    
    /**
     * 任务优先级比较器
     */
    private static class TaskPriorityComparator implements Comparator<TaskWrapper> {
        @Override
        public int compare(TaskWrapper t1, TaskWrapper t2) {
            // 首先按优先级排序（数值越小优先级越高）
            int priorityCompare = Integer.compare(t1.getPriority(), t2.getPriority());
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            
            // 优先级相同时按提交时间排序（FIFO）
            return Long.compare(t1.getSubmitTime(), t2.getSubmitTime());
        }
    }
    
    /**
     * 任务优先级枚举
     */
    public enum TaskPriority {
        HIGH(1),
        NORMAL(5),
        LOW(10);
        
        private final int value;
        
        TaskPriority(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
    }
    
    /**
     * 队列统计信息
     */
    public static class QueueStatistics {
        private final long totalSubmitted;
        private final long totalCompleted;
        private final long totalFailed;
        private final long totalTimeout;
        private final int currentQueueSize;
        private final int currentRegistrySize;
        private final Map<String, Long> taskTypeStats;
        
        public QueueStatistics(long totalSubmitted, long totalCompleted, long totalFailed, 
                             long totalTimeout, int currentQueueSize, int currentRegistrySize,
                             Map<String, Long> taskTypeStats) {
            this.totalSubmitted = totalSubmitted;
            this.totalCompleted = totalCompleted;
            this.totalFailed = totalFailed;
            this.totalTimeout = totalTimeout;
            this.currentQueueSize = currentQueueSize;
            this.currentRegistrySize = currentRegistrySize;
            this.taskTypeStats = new HashMap<>(taskTypeStats);
        }
        
        // Getters
        public long getTotalSubmitted() { return totalSubmitted; }
        public long getTotalCompleted() { return totalCompleted; }
        public long getTotalFailed() { return totalFailed; }
        public long getTotalTimeout() { return totalTimeout; }
        public int getCurrentQueueSize() { return currentQueueSize; }
        public int getCurrentRegistrySize() { return currentRegistrySize; }
        public Map<String, Long> getTaskTypeStats() { return new HashMap<>(taskTypeStats); }
        
        public double getSuccessRate() {
            long total = totalCompleted + totalFailed + totalTimeout;
            return total > 0 ? (double) totalCompleted / total : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "QueueStatistics{submitted=%d, completed=%d, failed=%d, timeout=%d, " +
                "queueSize=%d, registrySize=%d, successRate=%.2f%%}",
                totalSubmitted, totalCompleted, totalFailed, totalTimeout,
                currentQueueSize, currentRegistrySize, getSuccessRate() * 100
            );
        }
    }
}