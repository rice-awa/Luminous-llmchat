package com.riceawa.llm.subagent;

/**
 * 子代理状态枚举
 */
public enum SubAgentStatus {
    /**
     * 初始化中
     */
    INITIALIZING,
    
    /**
     * 空闲状态，可以接受新任务
     */
    IDLE,
    
    /**
     * 忙碌状态，正在执行任务
     */
    BUSY,
    
    /**
     * 暂停状态，暂时不接受新任务
     */
    PAUSED,
    
    /**
     * 错误状态，需要重启或修复
     */
    ERROR,
    
    /**
     * 关闭状态，正在关闭
     */
    SHUTTING_DOWN,
    
    /**
     * 已关闭状态
     */
    SHUTDOWN
}