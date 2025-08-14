package com.riceawa.llm.subagent;

/**
 * 子代理创建异常
 * 当子代理创建失败时抛出
 */
public class SubAgentCreationException extends Exception {
    
    /**
     * 构造函数
     * 
     * @param message 错误信息
     */
    public SubAgentCreationException(String message) {
        super(message);
    }
    
    /**
     * 构造函数
     * 
     * @param message 错误信息
     * @param cause 原因异常
     */
    public SubAgentCreationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * 构造函数
     * 
     * @param cause 原因异常
     */
    public SubAgentCreationException(Throwable cause) {
        super(cause);
    }
}