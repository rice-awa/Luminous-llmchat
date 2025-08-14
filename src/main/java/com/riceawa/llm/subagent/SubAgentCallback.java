package com.riceawa.llm.subagent;

/**
 * 子代理回调接口
 * 用于处理子代理任务完成后的回调通知
 * 
 * @param <R> 结果类型
 */
public interface SubAgentCallback<R extends SubAgentResult> {
    
    /**
     * 任务成功完成时的回调
     * 
     * @param taskId 任务ID
     * @param result 执行结果
     */
    void onSuccess(String taskId, R result);
    
    /**
     * 任务失败时的回调
     * 
     * @param taskId 任务ID
     * @param error 错误信息
     * @param exception 异常对象（可选）
     */
    void onFailure(String taskId, String error, Throwable exception);
    
    /**
     * 任务超时时的回调
     * 
     * @param taskId 任务ID
     */
    default void onTimeout(String taskId) {
        onFailure(taskId, "任务执行超时", null);
    }
    
    /**
     * 任务取消时的回调
     * 
     * @param taskId 任务ID
     */
    default void onCancelled(String taskId) {
        onFailure(taskId, "任务已取消", null);
    }
    
    /**
     * 任务进度更新时的回调（可选）
     * 
     * @param taskId 任务ID
     * @param progress 进度信息
     */
    default void onProgress(String taskId, String progress) {
        // 默认空实现
    }
}