package com.riceawa.llm.subagent;

import java.util.HashMap;
import java.util.Map;

/**
 * 子代理结果抽象基类
 * 定义所有子代理执行结果的通用属性
 */
public abstract class SubAgentResult {
    
    private final boolean success;
    private final String error;
    private final long totalProcessingTimeMs;
    private final Map<String, Object> metadata;
    private final long createdTime;
    
    /**
     * 构造函数
     * 
     * @param success 是否成功
     * @param error 错误信息（成功时为null）
     * @param totalProcessingTimeMs 总处理时间
     * @param metadata 元数据
     */
    protected SubAgentResult(boolean success, String error, long totalProcessingTimeMs, Map<String, Object> metadata) {
        this.success = success;
        this.error = error;
        this.totalProcessingTimeMs = totalProcessingTimeMs;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        this.createdTime = System.currentTimeMillis();
    }
    
    /**
     * 创建成功结果的便捷方法
     * 
     * @param totalProcessingTimeMs 总处理时间
     * @param metadata 元数据
     * @return 成功结果
     */
    protected static <T extends SubAgentResult> T createSuccess(long totalProcessingTimeMs, Map<String, Object> metadata) {
        throw new UnsupportedOperationException("Subclasses must implement createSuccess method");
    }
    
    /**
     * 创建失败结果的便捷方法
     * 
     * @param error 错误信息
     * @param totalProcessingTimeMs 总处理时间
     * @param metadata 元数据
     * @return 失败结果
     */
    protected static <T extends SubAgentResult> T createFailure(String error, long totalProcessingTimeMs, Map<String, Object> metadata) {
        throw new UnsupportedOperationException("Subclasses must implement createFailure method");
    }
    
    /**
     * 获取结果摘要，由子类实现
     * 
     * @return 结果摘要
     */
    public abstract String getSummary();
    
    /**
     * 获取详细结果数据，由子类实现
     * 
     * @return 详细结果数据
     */
    public abstract Map<String, Object> getDetailedData();
    
    /**
     * 检查结果是否成功
     * 
     * @return 是否成功
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * 获取错误信息
     * 
     * @return 错误信息，成功时为null
     */
    public String getError() {
        return error;
    }
    
    /**
     * 获取总处理时间
     * 
     * @return 总处理时间（毫秒）
     */
    public long getTotalProcessingTimeMs() {
        return totalProcessingTimeMs;
    }
    
    /**
     * 获取元数据
     * 
     * @return 元数据副本
     */
    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }
    
    /**
     * 获取指定键的元数据
     * 
     * @param key 键
     * @return 元数据值
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
    
    /**
     * 获取指定键的元数据，带默认值
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return 元数据值或默认值
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, T defaultValue) {
        Object value = metadata.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return (T) value;
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }
    
    /**
     * 获取结果创建时间
     * 
     * @return 创建时间戳
     */
    public long getCreatedTime() {
        return createdTime;
    }
    
    /**
     * 获取显示用的消息
     * 
     * @return 显示消息
     */
    public String getDisplayMessage() {
        if (success) {
            return getSummary();
        } else {
            return error != null ? error : "操作失败";
        }
    }
}