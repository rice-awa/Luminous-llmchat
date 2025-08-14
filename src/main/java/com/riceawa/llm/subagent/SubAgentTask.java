package com.riceawa.llm.subagent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 子代理任务抽象基类
 * 定义所有子代理任务的通用属性和方法
 * 
 * @param <R> 任务结果类型
 */
public abstract class SubAgentTask<R extends SubAgentResult> {
    
    private final String taskId;
    private final String requesterId;
    private final long createdTime;
    private final long timeoutMs;
    private final Map<String, Object> parameters;
    
    private SubAgentTaskStatus status;
    private int retryCount;
    private SubAgentCallback<R> callback;
    private R result;
    private String errorMessage;
    private long startTime;
    private long endTime;
    
    /**
     * 构造函数
     * 
     * @param requesterId 请求者ID
     * @param timeoutMs 超时时间（毫秒）
     * @param parameters 任务参数
     */
    protected SubAgentTask(String requesterId, long timeoutMs, Map<String, Object> parameters) {
        this.taskId = UUID.randomUUID().toString();
        this.requesterId = requesterId;
        this.createdTime = System.currentTimeMillis();
        this.timeoutMs = timeoutMs;
        this.parameters = parameters != null ? new HashMap<>(parameters) : new HashMap<>();
        this.status = SubAgentTaskStatus.PENDING;
        this.retryCount = 0;
    }
    
    /**
     * 获取任务类型，由子类实现
     * 
     * @return 任务类型标识
     */
    public abstract String getTaskType();
    
    /**
     * 获取任务特定数据，由子类实现
     * 
     * @return 任务特定数据
     */
    public abstract Map<String, Object> getTaskSpecificData();
    
    /**
     * 检查任务是否超时
     * 
     * @return 是否超时
     */
    public boolean isTimeout() {
        if (timeoutMs <= 0) {
            return false; // 无超时限制
        }
        return System.currentTimeMillis() - createdTime > timeoutMs;
    }
    
    /**
     * 获取任务剩余时间
     * 
     * @return 剩余时间（毫秒），-1表示无限制
     */
    public long getRemainingTime() {
        if (timeoutMs <= 0) {
            return -1; // 无超时限制
        }
        long elapsed = System.currentTimeMillis() - createdTime;
        return Math.max(0, timeoutMs - elapsed);
    }
    
    /**
     * 标记任务开始执行
     */
    public void markStarted() {
        this.status = SubAgentTaskStatus.PROCESSING;
        this.startTime = System.currentTimeMillis();
    }
    
    /**
     * 标记任务完成
     * 
     * @param result 任务结果
     */
    public void markCompleted(R result) {
        this.status = SubAgentTaskStatus.COMPLETED;
        this.result = result;
        this.endTime = System.currentTimeMillis();
    }
    
    /**
     * 标记任务失败
     * 
     * @param errorMessage 错误信息
     */
    public void markFailed(String errorMessage) {
        this.status = SubAgentTaskStatus.FAILED;
        this.errorMessage = errorMessage;
        this.endTime = System.currentTimeMillis();
    }
    
    /**
     * 标记任务超时
     */
    public void markTimeout() {
        this.status = SubAgentTaskStatus.TIMEOUT;
        this.endTime = System.currentTimeMillis();
    }
    
    /**
     * 增加重试次数
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }
    
    /**
     * 获取任务执行时长
     * 
     * @return 执行时长（毫秒），-1表示未开始或未结束
     */
    public long getExecutionTime() {
        if (startTime == 0) {
            return -1; // 未开始
        }
        if (endTime == 0) {
            return System.currentTimeMillis() - startTime; // 正在执行
        }
        return endTime - startTime; // 已结束
    }
    
    // Getters
    public String getTaskId() {
        return taskId;
    }
    
    public String getRequesterId() {
        return requesterId;
    }
    
    public long getCreatedTime() {
        return createdTime;
    }
    
    public long getTimeoutMs() {
        return timeoutMs;
    }
    
    public Map<String, Object> getParameters() {
        return new HashMap<>(parameters);
    }
    
    public Object getParameter(String key) {
        return parameters.get(key);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key, T defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return (T) value;
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }
    
    public SubAgentTaskStatus getStatus() {
        return status;
    }
    
    public void setStatus(SubAgentTaskStatus status) {
        this.status = status;
    }
    
    public int getRetryCount() {
        return retryCount;
    }
    
    public SubAgentCallback<R> getCallback() {
        return callback;
    }
    
    public void setCallback(SubAgentCallback<R> callback) {
        this.callback = callback;
    }
    
    public R getResult() {
        return result;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public long getEndTime() {
        return endTime;
    }
}