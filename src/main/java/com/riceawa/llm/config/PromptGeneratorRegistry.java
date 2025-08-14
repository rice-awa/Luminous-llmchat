package com.riceawa.llm.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

/**
 * 提示词生成器注册表
 * 管理不同类型子代理的提示词生成器
 */
public class PromptGeneratorRegistry {
    
    private static final PromptGeneratorRegistry INSTANCE = new PromptGeneratorRegistry();
    
    // 存储提示词生成器的映射
    private final Map<Class<? extends SubAgentTypeConfig>, PromptGenerator<?>> generators;
    
    // 存储生成器名称到生成器的映射
    private final Map<String, PromptGenerator<?>> generatorsByName;
    
    private PromptGeneratorRegistry() {
        this.generators = new ConcurrentHashMap<>();
        this.generatorsByName = new ConcurrentHashMap<>();
        
        // 注册默认的提示词生成器
        registerDefaultGenerators();
    }
    
    /**
     * 获取单例实例
     */
    public static PromptGeneratorRegistry getInstance() {
        return INSTANCE;
    }
    
    /**
     * 注册提示词生成器
     */
    @SuppressWarnings("unchecked")
    public <T extends SubAgentTypeConfig> void registerGenerator(PromptGenerator<T> generator) {
        if (generator == null) {
            throw new IllegalArgumentException("Generator cannot be null");
        }
        
        Class<T> configType = generator.getSupportedConfigType();
        String generatorName = generator.getGeneratorName();
        
        if (configType == null) {
            throw new IllegalArgumentException("Generator must specify supported config type");
        }
        
        if (generatorName == null || generatorName.trim().isEmpty()) {
            throw new IllegalArgumentException("Generator must have a valid name");
        }
        
        generators.put(configType, generator);
        generatorsByName.put(generatorName, generator);
    }
    
    /**
     * 获取指定配置类型的提示词生成器
     */
    @SuppressWarnings("unchecked")
    public <T extends SubAgentTypeConfig> Optional<PromptGenerator<T>> getGenerator(Class<T> configType) {
        if (configType == null) {
            return Optional.empty();
        }
        
        PromptGenerator<?> generator = generators.get(configType);
        if (generator != null) {
            return Optional.of((PromptGenerator<T>) generator);
        }
        
        return Optional.empty();
    }
    
    /**
     * 根据名称获取提示词生成器
     */
    @SuppressWarnings("unchecked")
    public <T extends SubAgentTypeConfig> Optional<PromptGenerator<T>> getGenerator(String generatorName) {
        if (generatorName == null || generatorName.trim().isEmpty()) {
            return Optional.empty();
        }
        
        PromptGenerator<?> generator = generatorsByName.get(generatorName);
        if (generator != null) {
            return Optional.of((PromptGenerator<T>) generator);
        }
        
        return Optional.empty();
    }
    
    /**
     * 生成系统提示词
     */
    public <T extends SubAgentTypeConfig> String generateSystemPrompt(T config) {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        
        @SuppressWarnings("unchecked")
        Class<T> configType = (Class<T>) config.getClass();
        
        Optional<PromptGenerator<T>> generator = getGenerator(configType);
        if (generator.isPresent()) {
            return generator.get().generateSystemPrompt(config);
        }
        
        // 如果没有找到专门的生成器，返回默认提示词
        return getDefaultPrompt(config);
    }
    
    /**
     * 生成带上下文的系统提示词
     */
    public <T extends SubAgentTypeConfig> String generateSystemPrompt(T config, String taskContext) {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        
        @SuppressWarnings("unchecked")
        Class<T> configType = (Class<T>) config.getClass();
        
        Optional<PromptGenerator<T>> generator = getGenerator(configType);
        if (generator.isPresent()) {
            return generator.get().generateSystemPrompt(config, taskContext);
        }
        
        // 如果没有找到专门的生成器，返回默认提示词
        String defaultPrompt = getDefaultPrompt(config);
        if (taskContext != null && !taskContext.trim().isEmpty()) {
            return defaultPrompt + "\n\n任务上下文：\n" + taskContext.trim();
        }
        
        return defaultPrompt;
    }
    
    /**
     * 验证提示词
     */
    public <T extends SubAgentTypeConfig> boolean validatePrompt(T config, String prompt) {
        if (config == null || prompt == null) {
            return false;
        }
        
        @SuppressWarnings("unchecked")
        Class<T> configType = (Class<T>) config.getClass();
        
        Optional<PromptGenerator<T>> generator = getGenerator(configType);
        if (generator.isPresent()) {
            return generator.get().validatePrompt(prompt);
        }
        
        // 默认验证逻辑
        return prompt.trim().length() > 0 && prompt.length() <= 4000;
    }
    
    /**
     * 检查是否支持指定的配置类型
     */
    public boolean isSupported(Class<? extends SubAgentTypeConfig> configType) {
        return generators.containsKey(configType);
    }
    
    /**
     * 获取所有已注册的生成器名称
     */
    public String[] getRegisteredGeneratorNames() {
        return generatorsByName.keySet().toArray(new String[0]);
    }
    
    /**
     * 获取所有支持的配置类型
     */
    public Class<?>[] getSupportedConfigTypes() {
        return generators.keySet().toArray(new Class<?>[0]);
    }
    
    /**
     * 注册默认的提示词生成器
     */
    private void registerDefaultGenerators() {
        // 注册搜索提示词生成器
        // TODO: Add SearchPromptGenerator after compilation issues are resolved
    }
    
    /**
     * 获取默认提示词
     */
    private <T extends SubAgentTypeConfig> String getDefaultPrompt(T config) {
        return String.format("你是一个专业的%s子代理助手。请根据用户的要求完成相应的任务。", 
                           config.getAgentType());
    }
    
    /**
     * 清除所有注册的生成器（主要用于测试）
     */
    public void clearAll() {
        generators.clear();
        generatorsByName.clear();
    }
    
    /**
     * 重新注册默认生成器
     */
    public void resetToDefaults() {
        clearAll();
        registerDefaultGenerators();
    }
}