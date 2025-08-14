package com.riceawa.llm.subagent;

import com.riceawa.llm.core.LLMService;
import com.riceawa.llm.logging.LogManager;

import com.riceawa.llm.logging.LogManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;

/**
 * 子代理工厂类
 * 负责创建和管理不同类型的子代理实例，支持插件式子代理创建
 */
public class SubAgentFactory {
    
    private static final String LOG_PREFIX = "[SubAgentFactory]";
    private static SubAgentFactory instance;
    
    // 子代理类型注册表：类型名 -> 创建函数
    private final Map<String, SubAgentCreator> agentCreators;
    
    // 子代理类型验证器：类型名 -> 验证函数
    private final Map<String, SubAgentValidator> agentValidators;
    
    // 子代理类型初始化器：类型名 -> 初始化函数
    private final Map<String, SubAgentInitializer> agentInitializers;
    
    // 支持的子代理类型集合
    private final Set<String> supportedTypes;
    
    // 类型注册表引用
    private final SubAgentTypeRegistry typeRegistry;
    
    // 兼容性检查器
    private final Map<String, CompatibilityChecker> compatibilityCheckers;
    
    private SubAgentFactory() {
        this.agentCreators = new ConcurrentHashMap<>();
        this.agentValidators = new ConcurrentHashMap<>();
        this.agentInitializers = new ConcurrentHashMap<>();
        this.supportedTypes = new HashSet<>();
        this.typeRegistry = SubAgentTypeRegistry.getInstance();
        this.compatibilityCheckers = new ConcurrentHashMap<>();
        
        LogManager.getInstance().system( LOG_PREFIX + " 子代理工厂已初始化");
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
     * 注册子代理类型（完整版本）
     * 
     * @param agentType 代理类型
     * @param creator 创建函数
     * @param validator 验证函数（可选）
     * @param initializer 初始化函数（可选）
     * @param compatibilityChecker 兼容性检查器（可选）
     */
    public void registerAgentType(String agentType, SubAgentCreator creator, 
                                SubAgentValidator validator, SubAgentInitializer initializer,
                                CompatibilityChecker compatibilityChecker) {
        if (agentType == null || agentType.trim().isEmpty()) {
            throw new IllegalArgumentException("Agent type cannot be null or empty");
        }
        
        if (creator == null) {
            throw new IllegalArgumentException("Agent creator cannot be null");
        }
        
        // 检查依赖关系
        if (!checkTypeDependencies(agentType)) {
            throw new IllegalStateException("Dependencies not satisfied for agent type: " + agentType);
        }
        
        agentCreators.put(agentType, creator);
        supportedTypes.add(agentType);
        
        if (validator != null) {
            agentValidators.put(agentType, validator);
        }
        
        if (initializer != null) {
            agentInitializers.put(agentType, initializer);
        }
        
        if (compatibilityChecker != null) {
            compatibilityCheckers.put(agentType, compatibilityChecker);
        }
        
        // 更新类型注册表状态
        typeRegistry.setTypeLoadStatus(agentType, SubAgentTypeRegistry.TypeLoadStatus.LOADED);
        
        LogManager.getInstance().system( LOG_PREFIX + " 注册子代理类型: " + agentType);
    }
    
    /**
     * 注册子代理类型
     * 
     * @param agentType 代理类型
     * @param creator 创建函数
     * @param validator 验证函数（可选）
     */
    public void registerAgentType(String agentType, SubAgentCreator creator, SubAgentValidator validator) {
        registerAgentType(agentType, creator, validator, null, null);
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
            agentInitializers.remove(agentType);
            compatibilityCheckers.remove(agentType);
            supportedTypes.remove(agentType);
            
            // 更新类型注册表状态
            typeRegistry.setTypeLoadStatus(agentType, SubAgentTypeRegistry.TypeLoadStatus.UNKNOWN);
            
            LogManager.getInstance().system( LOG_PREFIX + " 取消注册子代理类型: " + agentType);
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
        
        LogManager.getInstance().system( LOG_PREFIX + " 开始创建子代理: " + agentType);
        
        // 检查是否支持该类型
        if (!isTypeSupported(agentType)) {
            throw new SubAgentCreationException("Unsupported agent type: " + agentType);
        }
        
        // 检查兼容性
        if (!checkCompatibility(agentType, context)) {
            throw new SubAgentCreationException("Compatibility check failed for agent type: " + agentType);
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
            
            // 执行初始化（如果有初始化器）
            SubAgentInitializer initializer = agentInitializers.get(agentType);
            if (initializer != null) {
                initializer.initialize(agent, context);
            }
            
            LogManager.getInstance().system( LOG_PREFIX + " 创建子代理成功: " + agentType + 
                " ID: " + agent.getAgentId());
            return agent;
            
        } catch (Exception e) {
            LogManager.getInstance().system( LOG_PREFIX + " 创建子代理失败: " + agentType, e);
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
     * 发现并注册可用的子代理类型
     */
    public void discoverAndRegisterTypes() {
        LogManager.getInstance().system( LOG_PREFIX + " 开始发现子代理类型...");
        
        // 触发类型注册表的发现机制
        typeRegistry.discoverAndRegisterTypes();
        
        // 获取所有已注册的类型信息
        List<SubAgentTypeInfo> typeInfos = typeRegistry.getAllTypeInfos();
        
        // 按依赖关系排序
        Set<String> typeNames = new HashSet<>();
        for (SubAgentTypeInfo typeInfo : typeInfos) {
            if (typeInfo.isEnabled()) {
                typeNames.add(typeInfo.getTypeName());
            }
        }
        
        List<String> sortedTypes = typeRegistry.sortTypesByDependencies(typeNames);
        
        // 按顺序加载类型
        int loadedCount = 0;
        for (String typeName : sortedTypes) {
            try {
                if (loadAgentType(typeName)) {
                    loadedCount++;
                }
            } catch (Exception e) {
                LogManager.getInstance().system( LOG_PREFIX + " 加载代理类型失败: " + typeName, e);
                typeRegistry.setTypeLoadStatus(typeName, SubAgentTypeRegistry.TypeLoadStatus.FAILED);
            }
        }
        
        LogManager.getInstance().system( LOG_PREFIX + " 类型发现完成，成功加载 " + loadedCount + 
            " 个类型，总共 " + typeInfos.size() + " 个类型");
    }
    
    /**
     * 加载指定的代理类型
     */
    private boolean loadAgentType(String typeName) {
        SubAgentTypeInfo typeInfo = typeRegistry.getTypeInfo(typeName);
        if (typeInfo == null || !typeInfo.isEnabled()) {
            return false;
        }
        
        // 检查依赖关系
        if (!typeRegistry.areDependenciesSatisfied(typeName)) {
            LogManager.getInstance().system( LOG_PREFIX + " 类型依赖不满足: " + typeName);
            return false;
        }
        
        // 这里应该根据类型信息动态加载创建器、验证器等
        // 目前先跳过，等待具体类型实现
        
        LogManager.getInstance().system( LOG_PREFIX + " 类型加载成功: " + typeName);
        return true;
    }
    
    /**
     * 检查类型依赖关系
     */
    private boolean checkTypeDependencies(String agentType) {
        return typeRegistry.areDependenciesSatisfied(agentType);
    }
    
    /**
     * 检查兼容性
     */
    private boolean checkCompatibility(String agentType, SubAgentContext context) {
        CompatibilityChecker checker = compatibilityCheckers.get(agentType);
        if (checker == null) {
            return true; // 无检查器则认为兼容
        }
        
        try {
            return checker.isCompatible(context);
        } catch (Exception e) {
            LogManager.getInstance().system( LOG_PREFIX + " 兼容性检查失败: " + agentType, e);
            return false;
        }
    }
    
    /**
     * 获取类型信息
     */
    public SubAgentTypeInfo getTypeInfo(String agentType) {
        return typeRegistry.getTypeInfo(agentType);
    }
    
    /**
     * 获取所有类型信息
     */
    public List<SubAgentTypeInfo> getAllTypeInfos() {
        return typeRegistry.getAllTypeInfos();
    }
    
    /**
     * 获取类型加载状态
     */
    public SubAgentTypeRegistry.TypeLoadStatus getTypeLoadStatus(String agentType) {
        return typeRegistry.getTypeLoadStatus(agentType);
    }
    
    /**
     * 重新加载指定类型
     */
    public boolean reloadAgentType(String agentType) {
        if (!isTypeSupported(agentType)) {
            return false;
        }
        
        try {
            // 先取消注册
            unregisterAgentType(agentType);
            
            // 重新加载
            return loadAgentType(agentType);
            
        } catch (Exception e) {
            LogManager.getInstance().system( LOG_PREFIX + " 重新加载类型失败: " + agentType, e);
            return false;
        }
    }
    
    /**
     * 清空所有注册的代理类型
     */
    public void clearAllTypes() {
        agentCreators.clear();
        agentValidators.clear();
        agentInitializers.clear();
        compatibilityCheckers.clear();
        supportedTypes.clear();
        
        LogManager.getInstance().system( LOG_PREFIX + " 清空所有注册的子代理类型");
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
    
    /**
     * 子代理初始化器接口
     */
    @FunctionalInterface
    public interface SubAgentInitializer {
        /**
         * 初始化子代理
         * 
         * @param agent 子代理实例
         * @param context 代理上下文
         * @throws Exception 初始化失败时抛出
         */
        void initialize(SubAgent<?, ?> agent, SubAgentContext context) throws Exception;
    }
    
    /**
     * 兼容性检查器接口
     */
    @FunctionalInterface
    public interface CompatibilityChecker {
        /**
         * 检查兼容性
         * 
         * @param context 代理上下文
         * @return 是否兼容
         */
        boolean isCompatible(SubAgentContext context);
    }
}