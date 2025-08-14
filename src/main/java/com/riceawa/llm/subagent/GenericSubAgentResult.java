package com.riceawa.llm.subagent;

import java.util.Map;

/**
 * 通用子代理结果实现
 * 用于处理不需要特定类型结果的场景
 */
public class GenericSubAgentResult extends SubAgentResult {
    
    /**
     * 构造函数
     * 
     * @param success 是否成功
     * @param error 错误信息
     * @param totalProcessingTimeMs 总处理时间
     * @param metadata 元数据
     */
    public GenericSubAgentResult(boolean success, String error, long totalProcessingTimeMs, Map<String, Object> metadata) {
        super(success, error, totalProcessingTimeMs, metadata);
    }
    
    @Override
    public String getSummary() {
        if (isSuccess()) {
            return "操作成功完成";
        } else {
            return "操作失败: " + (getError() != null ? getError() : "未知错误");
        }
    }
    
    @Override
    public Map<String, Object> getDetailedData() {
        return getMetadata();
    }
    
    /**
     * 创建成功结果的静态方法
     * 
     * @param totalProcessingTimeMs 总处理时间
     * @param metadata 元数据
     * @return 成功结果
     */
    public static GenericSubAgentResult createGenericSuccess(long totalProcessingTimeMs, Map<String, Object> metadata) {
        return new GenericSubAgentResult(true, null, totalProcessingTimeMs, metadata);
    }
    
    /**
     * 创建失败结果的静态方法
     * 
     * @param error 错误信息
     * @param totalProcessingTimeMs 总处理时间
     * @param metadata 元数据
     * @return 失败结果
     */
    public static GenericSubAgentResult createGenericFailure(String error, long totalProcessingTimeMs, Map<String, Object> metadata) {
        return new GenericSubAgentResult(false, error, totalProcessingTimeMs, metadata);
    }
}