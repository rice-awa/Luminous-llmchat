package com.riceawa.llm.subagent;

/**
 * 子代理任务状态枚举
 */
public enum SubAgentTaskStatus {
    /**
     * 等待处理
     */
    PENDING,
    
    /**
     * 正在处理
     */
    PROCESSING,
    
    /**
     * 正在执行
     */
    EXECUTING,
    
    /**
     * 正在分析
     */
    ANALYZING,
    
    /**
     * 已完成
     */
    COMPLETED,
    
    /**
     * 失败
     */
    FAILED,
    
    /**
     * 超时
     */
    TIMEOUT,
    
    /**
     * 已取消
     */
    CANCELLED,
    
    /**
     * 达到最大轮数（适用于多轮任务）
     */
    MAX_ROUNDS_REACHED
}