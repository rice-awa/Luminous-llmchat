package com.riceawa.llm.subagent;

import java.util.concurrent.CompletableFuture;

/**
 * 通用子代理接口
 * 定义所有子代理必须实现的基本方法
 */
public interface SubAgent<T extends SubAgentTask<R>, R extends SubAgentResult> {
    
    /**
     * 执行子代理任务
     * 
     * @param task 要执行的任务
     * @return 异步执行结果
     */
    CompletableFuture<R> executeTask(T task);
    
    /**
     * 检查子代理是否可用
     * 
     * @return 是否可用
     */
    boolean isAvailable();
    
    /**
     * 关闭子代理，清理资源
     */
    void shutdown();
    
    /**
     * 获取子代理唯一标识
     * 
     * @return 代理ID
     */
    String getAgentId();
    
    /**
     * 获取子代理类型
     * 
     * @return 代理类型
     */
    String getAgentType();
    
    /**
     * 获取子代理当前状态
     * 
     * @return 代理状态
     */
    SubAgentStatus getStatus();
    
    /**
     * 获取子代理创建时间
     * 
     * @return 创建时间戳
     */
    long getCreatedTime();
    
    /**
     * 获取子代理最后活动时间
     * 
     * @return 最后活动时间戳
     */
    long getLastActivityTime();
}