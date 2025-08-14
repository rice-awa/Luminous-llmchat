package com.riceawa.llm.config;

/**
 * 通用提示词生成器接口
 * 用于根据配置参数动态生成系统提示词
 */
public interface PromptGenerator<T extends SubAgentTypeConfig> {
    
    /**
     * 根据配置生成系统提示词
     * @param config 子代理配置
     * @return 生成的系统提示词
     */
    String generateSystemPrompt(T config);
    
    /**
     * 根据配置和任务上下文生成系统提示词
     * @param config 子代理配置
     * @param taskContext 任务上下文信息
     * @return 生成的系统提示词
     */
    default String generateSystemPrompt(T config, String taskContext) {
        return generateSystemPrompt(config);
    }
    
    /**
     * 验证生成的提示词是否有效
     * @param prompt 生成的提示词
     * @return 是否有效
     */
    default boolean validatePrompt(String prompt) {
        return prompt != null && !prompt.trim().isEmpty() && prompt.length() <= 4000;
    }
    
    /**
     * 获取支持的配置类型
     * @return 配置类型的Class对象
     */
    Class<T> getSupportedConfigType();
    
    /**
     * 获取生成器名称
     * @return 生成器名称
     */
    String getGeneratorName();
}