package com.riceawa.llm.subagent;

import com.riceawa.llm.core.LLMService;
import com.riceawa.llm.logging.LogManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.Set;
import java.util.HashSet;

/**
 * 子代理工厂类
 * 负责创建和管理不同类型的子代理实例
 */
public class SubAgentFactory {
    
    private static SubAgentFactory instance;
    
    // 子代理类型注册表：类型名 -> 创建函数
    private final Map<String, SubAgentCreator> agentCreators;
    
    // 子代理类型验证器：类型名 -> 验证函数
    private final Map<String, SubAgentValidator> agentValidators;
    
    // 支持的子代理类型集合
    private final Set<String> supportedTypes;
    
    private SubAgentFactory() {
        this.agentCreators = new ConcurrentHashMap<>();
        this.agentValidators = new ConcurrentHashMap<>();
        this.supportedTypes = new HashSet<>();
        
        LogManager.getInstance().system("SubAgentFactory initialized");
    }
    
    /**
     * 获取工厂单例实例
     * 
     * @return 工厂实例
     */
    public static SubAgentFactory getInstance() {
        if (instance == null) {
            synchronized (SubAgentFactory.class) {
                if (instance == null) {
                    instance = new SubAgentFactory();
                }
            }
        }
        return instance;
    }
    
    /**
     * 注册子代理类型
     * 
     * @param agentType 代理类型
     * @param creator 创建函数
     * @param validator 验证函数（可选）
     */
    public void registerAgentType(String agentType, SubAgentCreator creator, SubAgentValidator validator) {
        if (agentType == null || agentType.trim().isEmpty()) {
            throw new IllegalArgumentException("Agent type cannot be null or empty");
        }
        
        if (creator == null) {
            throw new IllegalArgumentException("Agent creator cannot be null");
        }
        
        agentCreators.put(agentType, creator);
        supportedTypes.add(agentType);
        
        if (validator != null) {
            agentValidators.put(agentType, validator);
        }
        
        LogManager.getInstance().system("Registered sub-agent type: " + agentType);
    }
    
    /**
     * 注册子代理类型（无验证器）
     * 
     * @param agentType 代理类型
     * @param creator 创建函数
     */
    public void registerAgentType(String agentType, SubAgentCreator creator) {
        registerAgentType(agentType, creator, null);
    }
    
    /**
     * 取消注册子代理类型
     * 
     * @param agentType 代理类型
     */
    public void unregisterAgentType(String agentType) {
        if (agentType != null) {
            agentCreators.remove(agentType);
            agentValidators.remove(agentType);
            supportedTypes.remove(agentType);
            
            LogManager.getInstance().system("Unregistered sub-agent type: " + agentType);
        }
    }
    
    /**
     * 创建子代理实例
     * 
     * @param agentType 代理类型
     * @param llmService LLM服务
     * @param context 代理上下文
     * @return 子代理实例
     * @throws SubAgentCreationException 创建失败时抛出
     */
    @SuppressWarnings("unchecked")
    public <T extends SubAgentTask<R>, R extends SubAgentResult> SubAgent<T, R> createAgent(
            String agentType, LLMService llmService, SubAgentContext context) throws SubAgentCreationException {
        
        if (agentType == null || agentType.trim().isEmpty()) {
            throw new SubAgentCreationException("Agent type cannot be null or empty");
        }
        
        if (llmService == null) {
            throw new SubAgentCreationException("LLM service cannot be null");
        }
        
        if (context == null) {
            throw new SubAgentCreationException("Sub-agent context cannot be null");
        }
        
        // 检查是否支持该类型
        if (!isTypeSupported(agentType)) {
            throw new SubAgentCreationException("Unsupported agent type: " + agentType);
        }
        
        // 验证配置（如果有验证器）
        SubAgentValidator validator = agentValidators.get(agentType);
        if (validator != null) {
            SubAgentValidationResult validationResult = validator.validate(context);
            if (!validationResult.isValid()) {
                throw new SubAgentCreationException("Agent configuration validation failed: " + 
                    validationResult.getErrorMessage());
            }
        }
        
        // 创建代理实例
        SubAgentCreator creator = agentCreators.get(agentType);
        try {
            SubAgent<T, R> agent = creator.create(llmService, context);
            
            if (agent == null) {
                throw new SubAgentCreationException("Creator returned null agent for type: " + agentType);
            }
            
            LogManager.getInstance().system("Created sub-agent: " + agentType + " with ID: " + agent.getAgentId());
            return agent;
            
        } catch (Exception e) {
            LogManager.getInstance().error("Failed to create sub-agent of type: " + agentType, e);
            throw new SubAgentCreationException("Failed to create agent: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检查是否支持指定的代理类型
     * 
     * @param agentType 代理类型
     * @return 是否支持
     */
    public boolean isTypeSupported(String agentType) {
        return agentType != null && supportedTypes.contains(agentType);
    }
    
    /**
     * 获取所有支持的代理类型
     * 
     * @return 支持的代理类型集合
     */
    public Set<String> getSupportedTypes() {
        return new HashSet<>(supportedTypes);
    }
    
    /**
     * 验证代理类型配置
     * 
     * @param agentType 代理类型
     * @param context 代理上下文
     * @return 验证结果
     */
    public SubAgentValidationResult validateAgentType(String agentType, SubAgentContext context) {
        if (!isTypeSupported(agentType)) {
            return SubAgentValidationResult.invalid("Unsupported agent type: " + agentType);
        }
        
        SubAgentValidator validator = agentValidators.get(agentType);
        if (validator == null) {
            return SubAgentValidationResult.valid(); // 无验证器则认为有效
        }
        
        return validator.validate(context);
    }
    
    /**
     * 获取已注册的代理类型数量
     * 
     * @return 代理类型数量
     */
    public int getRegisteredTypeCount() {
        return supportedTypes.size();
    }
    
    /**
     * 清空所有注册的代理类型
     */
    public void clearAllTypes() {
        agentCreators.clear();
        agentValidators.clear();
        supportedTypes.clear();
        
        LogManager.getInstance().system("Cleared all registered sub-agent types");
    }
    
    /**
     * 子代理创建器接口
     */
    @FunctionalInterface
    public interface SubAgentCreator {
        /**
         * 创建子代理实例
         * 
         * @param llmService LLM服务
         * @param context 代理上下文
         * @return 子代理实例
         * @throws Exception 创建失败时抛出
         */
        <T extends SubAgentTask<R>, R extends SubAgentResult> SubAgent<T, R> create(
            LLMService llmService, SubAgentContext context) throws Exception;
    }
    
    /**
     * 子代理验证器接口
     */
    @FunctionalInterface
    public interface SubAgentValidator {
        /**
         * 验证子代理配置
         * 
         * @param context 代理上下文
         * @return 验证结果
         */
        SubAgentValidationResult validate(SubAgentContext context);
    }
}