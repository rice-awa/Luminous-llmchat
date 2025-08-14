package com.riceawa.llm.subagent;


import com.riceawa.llm.logging.LogManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 任务生命周期管理器
 * 负责管理任务的状态转换、生命周期跟踪和路由机制
 */
public class TaskLifecycleManager {
    
    private static final String LOG_PREFIX = "[TaskLifecycleManager]";
    
    // 任务状态转换映射
    private static final Map<SubAgentTaskStatus, Set<SubAgentTaskStatus>> VALID_TRANSITIONS;
    
    static {
        VALID_TRANSITIONS = new HashMap<>();
        
        // PENDING 可以转换到的状态
        VALID_TRANSITIONS.put(SubAgentTaskStatus.PENDING, Set.of(
            SubAgentTaskStatus.PROCESSING,
            SubAgentTaskStatus.CANCELLED,
            SubAgentTaskStatus.TIMEOUT
        ));
        
        // PROCESSING 可以转换到的状态
        VALID_TRANSITIONS.put(SubAgentTaskStatus.PROCESSING, Set.of(
            SubAgentTaskStatus.EXECUTING,
            SubAgentTaskStatus.FAILED,
            SubAgentTaskStatus.TIMEOUT,
            SubAgentTaskStatus.CANCELLED
        ));
        
        // EXECUTING 可以转换到的状态
        VALID_TRANSITIONS.put(SubAgentTaskStatus.EXECUTING, Set.of(
            SubAgentTaskStatus.ANALYZING,
            SubAgentTaskStatus.COMPLETED,
            SubAgentTaskStatus.FAILED,
            SubAgentTaskStatus.TIMEOUT,
            SubAgentTaskStatus.MAX_ROUNDS_REACHED
        ));
        
        // ANALYZING 可以转换到的状态
        VALID_TRANSITIONS.put(SubAgentTaskStatus.ANALYZING, Set.of(
            SubAgentTaskStatus.COMPLETED,
            SubAgentTaskStatus.FAILED,
            SubAgentTaskStatus.TIMEOUT,
            SubAgentTaskStatus.MAX_ROUNDS_REACHED
        ));
        
        // 终态不能再转换
        VALID_TRANSITIONS.put(SubAgentTaskStatus.COMPLETED, Set.of());
        VALID_TRANSITIONS.put(SubAgentTaskStatus.FAILED, Set.of());
        VALID_TRANSITIONS.put(SubAgentTaskStatus.TIMEOUT, Set.of());
        VALID_TRANSITIONS.put(SubAgentTaskStatus.CANCELLED, Set.of());
        VALID_TRANSITIONS.put(SubAgentTaskStatus.MAX_ROUNDS_REACHED, Set.of());
    }
    
    // 任务生命周期跟踪
    private final ConcurrentHashMap<String, TaskLifecycle> taskLifecycles;
    
    // 任务路由器映射
    private final ConcurrentHashMap<String, TaskRouter> taskRouters;
    
    // 状态监听器
    private final List<TaskStatusListener> statusListeners;
    
    // 统计信息
    private final ConcurrentHashMap<SubAgentTaskStatus, AtomicLong> statusCounters;
    private final ConcurrentHashMap<String, AtomicLong> taskTypeCounters;
    
    // 读写锁
    private final ReentrantReadWriteLock lifecycleLock;
    
    /**
     * 构造函数
     */
    public TaskLifecycleManager() {
        this.taskLifecycles = new ConcurrentHashMap<>();
        this.taskRouters = new ConcurrentHashMap<>();
        this.statusListeners = new ArrayList<>();
        this.statusCounters = new ConcurrentHashMap<>();
        this.taskTypeCounters = new ConcurrentHashMap<>();
        this.lifecycleLock = new ReentrantReadWriteLock();
        
        // 初始化状态计数器
        for (SubAgentTaskStatus status : SubAgentTaskStatus.values()) {
            statusCounters.put(status, new AtomicLong(0));
        }
        
        LogManager.getInstance().system( LOG_PREFIX + " 任务生命周期管理器已初始化");
    }
    
    /**
     * 注册任务路由器
     * 
     * @param taskType 任务类型
     * @param router 路由器
     */
    public void registerTaskRouter(String taskType, TaskRouter router) {
        taskRouters.put(taskType, router);
        LogManager.getInstance().system( LOG_PREFIX + " 已注册任务路由器: " + taskType);
    }
    
    /**
     * 注销任务路由器
     * 
     * @param taskType 任务类型
     */
    public void unregisterTaskRouter(String taskType) {
        taskRouters.remove(taskType);
        LogManager.getInstance().system( LOG_PREFIX + " 已注销任务路由器: " + taskType);
    }
    
    /**
     * 添加状态监听器
     * 
     * @param listener 状态监听器
     */
    public void addStatusListener(TaskStatusListener listener) {
        synchronized (statusListeners) {
            statusListeners.add(listener);
        }
        LogManager.getInstance().system( LOG_PREFIX + " 已添加状态监听器");
    }
    
    /**
     * 移除状态监听器
     * 
     * @param listener 状态监听器
     */
    public void removeStatusListener(TaskStatusListener listener) {
        synchronized (statusListeners) {
            statusListeners.remove(listener);
        }
        LogManager.getInstance().system( LOG_PREFIX + " 已移除状态监听器");
    }
    
    /**
     * 开始跟踪任务生命周期
     * 
     * @param task 任务
     */
    public void startTracking(SubAgentTask<?> task) {
        lifecycleLock.writeLock().lock();
        try {
            TaskLifecycle lifecycle = new TaskLifecycle(task);
            taskLifecycles.put(task.getTaskId(), lifecycle);
            
            // 更新统计
            statusCounters.get(task.getStatus()).incrementAndGet();
            taskTypeCounters.computeIfAbsent(task.getTaskType(), k -> new AtomicLong(0)).incrementAndGet();
            
            LogManager.getInstance().system( LOG_PREFIX + " 开始跟踪任务: " + task.getTaskId());
            
            // 通知监听器
            notifyStatusChange(task.getTaskId(), null, task.getStatus());
            
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }
    
    /**
     * 停止跟踪任务生命周期
     * 
     * @param taskId 任务ID
     */
    public void stopTracking(String taskId) {
        lifecycleLock.writeLock().lock();
        try {
            TaskLifecycle lifecycle = taskLifecycles.remove(taskId);
            if (lifecycle != null) {
                LogManager.getInstance().system( LOG_PREFIX + " 停止跟踪任务: " + taskId);
            }
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }
    
    /**
     * 更新任务状态
     * 
     * @param taskId 任务ID
     * @param newStatus 新状态
     * @return 是否成功更新
     */
    public boolean updateTaskStatus(String taskId, SubAgentTaskStatus newStatus) {
        lifecycleLock.writeLock().lock();
        try {
            TaskLifecycle lifecycle = taskLifecycles.get(taskId);
            if (lifecycle == null) {
                LogManager.getInstance().system( LOG_PREFIX + " 任务不存在: " + taskId);
                return false;
            }
            
            SubAgentTask<?> task = lifecycle.getTask();
            SubAgentTaskStatus oldStatus = task.getStatus();
            
            // 验证状态转换是否有效
            if (!isValidTransition(oldStatus, newStatus)) {
                LogManager.getInstance().system( LOG_PREFIX + " 无效的状态转换: " + 
                    oldStatus + " -> " + newStatus + ", 任务: " + taskId);
                return false;
            }
            
            // 更新状态
            task.setStatus(newStatus);
            lifecycle.addStatusChange(oldStatus, newStatus);
            
            // 更新统计
            statusCounters.get(oldStatus).decrementAndGet();
            statusCounters.get(newStatus).incrementAndGet();
            
            LogManager.getInstance().system( LOG_PREFIX + " 任务状态已更新: " + taskId + 
                " [" + oldStatus + " -> " + newStatus + "]");
            
            // 通知监听器
            notifyStatusChange(taskId, oldStatus, newStatus);
            
            // 执行路由逻辑
            executeRouting(task, oldStatus, newStatus);
            
            return true;
            
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }
    
    /**
     * 获取任务生命周期信息
     * 
     * @param taskId 任务ID
     * @return 生命周期信息
     */
    public TaskLifecycle getTaskLifecycle(String taskId) {
        lifecycleLock.readLock().lock();
        try {
            return taskLifecycles.get(taskId);
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }
    
    /**
     * 获取指定状态的任务列表
     * 
     * @param status 任务状态
     * @return 任务ID列表
     */
    public List<String> getTasksByStatus(SubAgentTaskStatus status) {
        lifecycleLock.readLock().lock();
        try {
            List<String> taskIds = new ArrayList<>();
            for (Map.Entry<String, TaskLifecycle> entry : taskLifecycles.entrySet()) {
                if (entry.getValue().getTask().getStatus() == status) {
                    taskIds.add(entry.getKey());
                }
            }
            return taskIds;
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }
    
    /**
     * 获取指定类型的任务列表
     * 
     * @param taskType 任务类型
     * @return 任务ID列表
     */
    public List<String> getTasksByType(String taskType) {
        lifecycleLock.readLock().lock();
        try {
            List<String> taskIds = new ArrayList<>();
            for (Map.Entry<String, TaskLifecycle> entry : taskLifecycles.entrySet()) {
                if (taskType.equals(entry.getValue().getTask().getTaskType())) {
                    taskIds.add(entry.getKey());
                }
            }
            return taskIds;
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }
    
    /**
     * 获取生命周期统计信息
     * 
     * @return 统计信息
     */
    public LifecycleStatistics getStatistics() {
        lifecycleLock.readLock().lock();
        try {
            Map<SubAgentTaskStatus, Long> statusStats = new HashMap<>();
            for (Map.Entry<SubAgentTaskStatus, AtomicLong> entry : statusCounters.entrySet()) {
                statusStats.put(entry.getKey(), entry.getValue().get());
            }
            
            Map<String, Long> typeStats = new HashMap<>();
            for (Map.Entry<String, AtomicLong> entry : taskTypeCounters.entrySet()) {
                typeStats.put(entry.getKey(), entry.getValue().get());
            }
            
            return new LifecycleStatistics(statusStats, typeStats, taskLifecycles.size());
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }
    
    /**
     * 清理已完成的任务生命周期
     * 
     * @param maxAge 最大保留时间（毫秒）
     * @return 清理的任务数量
     */
    public int cleanupCompletedTasks(long maxAge) {
        lifecycleLock.writeLock().lock();
        try {
            List<String> toRemove = new ArrayList<>();
            long currentTime = System.currentTimeMillis();
            
            for (Map.Entry<String, TaskLifecycle> entry : taskLifecycles.entrySet()) {
                TaskLifecycle lifecycle = entry.getValue();
                SubAgentTask<?> task = lifecycle.getTask();
                
                // 检查是否为终态且超过保留时间
                if (isTerminalStatus(task.getStatus()) && 
                    (currentTime - lifecycle.getLastUpdateTime()) > maxAge) {
                    toRemove.add(entry.getKey());
                }
            }
            
            // 移除过期的生命周期记录
            for (String taskId : toRemove) {
                taskLifecycles.remove(taskId);
            }
            
            if (!toRemove.isEmpty()) {
                LogManager.getInstance().system( LOG_PREFIX + " 清理了 " + toRemove.size() + " 个已完成任务的生命周期记录");
            }
            
            return toRemove.size();
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }
    
    /**
     * 检查状态转换是否有效
     * 
     * @param from 源状态
     * @param to 目标状态
     * @return 是否有效
     */
    private boolean isValidTransition(SubAgentTaskStatus from, SubAgentTaskStatus to) {
        Set<SubAgentTaskStatus> validTargets = VALID_TRANSITIONS.get(from);
        return validTargets != null && validTargets.contains(to);
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
     * 通知状态变化
     * 
     * @param taskId 任务ID
     * @param oldStatus 旧状态
     * @param newStatus 新状态
     */
    private void notifyStatusChange(String taskId, SubAgentTaskStatus oldStatus, SubAgentTaskStatus newStatus) {
        synchronized (statusListeners) {
            for (TaskStatusListener listener : statusListeners) {
                try {
                    listener.onStatusChanged(taskId, oldStatus, newStatus);
                } catch (Exception e) {
                    LogManager.getInstance().system( LOG_PREFIX + " 状态监听器执行失败: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 执行路由逻辑
     * 
     * @param task 任务
     * @param oldStatus 旧状态
     * @param newStatus 新状态
     */
    private void executeRouting(SubAgentTask<?> task, SubAgentTaskStatus oldStatus, SubAgentTaskStatus newStatus) {
        TaskRouter router = taskRouters.get(task.getTaskType());
        if (router != null) {
            try {
                router.onStatusChanged(task, oldStatus, newStatus);
            } catch (Exception e) {
                LogManager.getInstance().system( LOG_PREFIX + " 任务路由执行失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 任务生命周期信息
     */
    public static class TaskLifecycle {
        private final SubAgentTask<?> task;
        private final long createdTime;
        private final List<StatusChange> statusChanges;
        private long lastUpdateTime;
        
        public TaskLifecycle(SubAgentTask<?> task) {
            this.task = task;
            this.createdTime = System.currentTimeMillis();
            this.statusChanges = new ArrayList<>();
            this.lastUpdateTime = createdTime;
        }
        
        public void addStatusChange(SubAgentTaskStatus from, SubAgentTaskStatus to) {
            statusChanges.add(new StatusChange(from, to, System.currentTimeMillis()));
            lastUpdateTime = System.currentTimeMillis();
        }
        
        public SubAgentTask<?> getTask() {
            return task;
        }
        
        public long getCreatedTime() {
            return createdTime;
        }
        
        public long getLastUpdateTime() {
            return lastUpdateTime;
        }
        
        public List<StatusChange> getStatusChanges() {
            return new ArrayList<>(statusChanges);
        }
        
        public long getTotalLifetime() {
            return System.currentTimeMillis() - createdTime;
        }
        
        public static class StatusChange {
            private final SubAgentTaskStatus from;
            private final SubAgentTaskStatus to;
            private final long timestamp;
            
            public StatusChange(SubAgentTaskStatus from, SubAgentTaskStatus to, long timestamp) {
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
     * 任务状态监听器接口
     */
    public interface TaskStatusListener {
        /**
         * 状态变化时的回调
         * 
         * @param taskId 任务ID
         * @param oldStatus 旧状态
         * @param newStatus 新状态
         */
        void onStatusChanged(String taskId, SubAgentTaskStatus oldStatus, SubAgentTaskStatus newStatus);
    }
    
    /**
     * 任务路由器接口
     */
    public interface TaskRouter {
        /**
         * 状态变化时的路由处理
         * 
         * @param task 任务
         * @param oldStatus 旧状态
         * @param newStatus 新状态
         */
        void onStatusChanged(SubAgentTask<?> task, SubAgentTaskStatus oldStatus, SubAgentTaskStatus newStatus);
    }
    
    /**
     * 生命周期统计信息
     */
    public static class LifecycleStatistics {
        private final Map<SubAgentTaskStatus, Long> statusStatistics;
        private final Map<String, Long> taskTypeStatistics;
        private final int activeTaskCount;
        
        public LifecycleStatistics(Map<SubAgentTaskStatus, Long> statusStatistics,
                                 Map<String, Long> taskTypeStatistics,
                                 int activeTaskCount) {
            this.statusStatistics = new HashMap<>(statusStatistics);
            this.taskTypeStatistics = new HashMap<>(taskTypeStatistics);
            this.activeTaskCount = activeTaskCount;
        }
        
        public Map<SubAgentTaskStatus, Long> getStatusStatistics() {
            return new HashMap<>(statusStatistics);
        }
        
        public Map<String, Long> getTaskTypeStatistics() {
            return new HashMap<>(taskTypeStatistics);
        }
        
        public int getActiveTaskCount() {
            return activeTaskCount;
        }
        
        public long getStatusCount(SubAgentTaskStatus status) {
            return statusStatistics.getOrDefault(status, 0L);
        }
        
        public long getTaskTypeCount(String taskType) {
            return taskTypeStatistics.getOrDefault(taskType, 0L);
        }
        
        @Override
        public String toString() {
            return String.format(
                "LifecycleStatistics{activeTaskCount=%d, statusStats=%s, typeStats=%s}",
                activeTaskCount, statusStatistics, taskTypeStatistics
            );
        }
    }
}