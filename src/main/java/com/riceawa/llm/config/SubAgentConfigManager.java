package com.riceawa.llm.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 子代理配置管理器
 * 负责管理不同类型子代理的配置
 */
public class SubAgentConfigManager {
    
    private static SubAgentConfigManager instance;
    private final Map<String, Class<? extends SubAgentTypeConfig>> registeredConfigTypes;
    
    private SubAgentConfigManager() {
        this.registeredConfigTypes = new HashMap<>();
        registerDefaultConfigTypes();
    }
    
    public static SubAgentConfigManager getInstance() {
        if (instance == null) {
            synchronized (SubAgentConfigManager.class) {
                if (instance == null) {
                    instance = new SubAgentConfigManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * 注册默认的配置类型
     */
    private void registerDefaultConfigTypes() {
        registerConfigType("INTELLIGENT_SEARCH", IntelligentSearchConfig.class);
    }
    
    /**
     * 注册子代理配置类型
     */
    public void registerConfigType(String agentType, Class<? extends SubAgentTypeConfig> configClass) {
        if (agentType != null && !agentType.trim().isEmpty() && configClass != null) {
            registeredConfigTypes.put(agentType, configClass);
        }
    }
    
    /**
     * 取消注册子代理配置类型
     */
    public void unregisterConfigType(String agentType) {
        registeredConfigTypes.remove(agentType);
    }
    
    /**
     * 检查是否支持指定的子代理类型
     */
    public boolean isConfigTypeSupported(String agentType) {
        return registeredConfigTypes.containsKey(agentType);
    }
    
    /**
     * 获取所有支持的子代理类型
     */
    public Set<String> getSupportedAgentTypes() {
        return registeredConfigTypes.keySet();
    }
    
    /**
     * 创建指定类型的默认配置
     */
    public Optional<SubAgentTypeConfig> createDefaultConfig(String agentType) {
        Class<? extends SubAgentTypeConfig> configClass = registeredConfigTypes.get(agentType);
        if (configClass == null) {
            return Optional.empty();
        }
        
        try {
            // 尝试调用createDefault静态方法
            try {
                java.lang.reflect.Method createDefaultMethod = configClass.getMethod("createDefault");
                if (java.lang.reflect.Modifier.isStatic(createDefaultMethod.getModifiers())) {
                    Object result = createDefaultMethod.invoke(null);
                    if (result instanceof SubAgentTypeConfig) {
                        return Optional.of((SubAgentTypeConfig) result);
                    }
                }
            } catch (NoSuchMethodException e) {
                // 如果没有createDefault方法，尝试使用默认构造函数
            }
            
            // 使用默认构造函数
            SubAgentTypeConfig config = configClass.getDeclaredConstructor().newInstance();
            return Optional.of(config);
            
        } catch (Exception e) {
            System.err.println("Failed to create default config for agent type " + agentType + ": " + e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * 验证配置对象是否与指定类型匹配
     */
    public boolean validateConfigType(String agentType, SubAgentTypeConfig config) {
        if (config == null) {
            return false;
        }
        
        Class<? extends SubAgentTypeConfig> expectedClass = registeredConfigTypes.get(agentType);
        if (expectedClass == null) {
            return false;
        }
        
        return expectedClass.isInstance(config) && config.isValid();
    }
    
    /**
     * 获取指定类型的配置类
     */
    public Optional<Class<? extends SubAgentTypeConfig>> getConfigClass(String agentType) {
        return Optional.ofNullable(registeredConfigTypes.get(agentType));
    }
    
    /**
     * 从框架配置中获取指定类型的配置
     */
    @SuppressWarnings("unchecked")
    public <T extends SubAgentTypeConfig> Optional<T> getConfig(String agentType, Class<T> configClass) {
        SubAgentFrameworkConfig frameworkConfig = LLMChatConfig.getInstance().getSubAgentFrameworkConfig();
        if (frameworkConfig == null) {
            return Optional.empty();
        }
        
        T config = frameworkConfig.getTypeConfig(agentType, configClass);
        return Optional.ofNullable(config);
    }
    
    /**
     * 设置指定类型的配置到框架配置中
     */
    public boolean setConfig(String agentType, SubAgentTypeConfig config) {
        if (!validateConfigType(agentType, config)) {
            return false;
        }
        
        SubAgentFrameworkConfig frameworkConfig = LLMChatConfig.getInstance().getSubAgentFrameworkConfig();
        if (frameworkConfig == null) {
            frameworkConfig = SubAgentFrameworkConfig.createDefault();
            LLMChatConfig.getInstance().setSubAgentFrameworkConfig(frameworkConfig);
        }
        
        frameworkConfig.setTypeConfig(agentType, config);
        LLMChatConfig.getInstance().setSubAgentFrameworkConfig(frameworkConfig);
        
        return true;
    }
    
    /**
     * 获取或创建指定类型的配置
     */
    @SuppressWarnings("unchecked")
    public <T extends SubAgentTypeConfig> T getOrCreateConfig(String agentType, Class<T> configClass) {
        // 首先尝试从现有配置中获取
        Optional<T> existingConfig = getConfig(agentType, configClass);
        if (existingConfig.isPresent()) {
            return existingConfig.get();
        }
        
        // 如果不存在，创建默认配置
        Optional<SubAgentTypeConfig> defaultConfig = createDefaultConfig(agentType);
        if (defaultConfig.isPresent() && configClass.isInstance(defaultConfig.get())) {
            T config = (T) defaultConfig.get();
            setConfig(agentType, config);
            return config;
        }
        
        throw new IllegalStateException("Cannot create or retrieve config for agent type: " + agentType);
    }
    
    /**
     * 移除指定类型的配置
     */
    public void removeConfig(String agentType) {
        SubAgentFrameworkConfig frameworkConfig = LLMChatConfig.getInstance().getSubAgentFrameworkConfig();
        if (frameworkConfig != null) {
            frameworkConfig.removeTypeConfig(agentType);
            LLMChatConfig.getInstance().setSubAgentFrameworkConfig(frameworkConfig);
        }
    }
    
    /**
     * 验证所有已配置的子代理类型配置
     */
    public Map<String, Boolean> validateAllConfigs() {
        Map<String, Boolean> validationResults = new HashMap<>();
        
        SubAgentFrameworkConfig frameworkConfig = LLMChatConfig.getInstance().getSubAgentFrameworkConfig();
        if (frameworkConfig == null) {
            return validationResults;
        }
        
        Map<String, SubAgentTypeConfig> typeConfigs = frameworkConfig.getTypeConfigs();
        for (Map.Entry<String, SubAgentTypeConfig> entry : typeConfigs.entrySet()) {
            String agentType = entry.getKey();
            SubAgentTypeConfig config = entry.getValue();
            
            boolean isValid = validateConfigType(agentType, config);
            validationResults.put(agentType, isValid);
        }
        
        return validationResults;
    }
    
    /**
     * 获取配置状态报告
     */
    public String getConfigurationReport() {
        StringBuilder report = new StringBuilder();
        report.append("子代理配置状态报告:\n");
        
        report.append("支持的代理类型: ").append(registeredConfigTypes.keySet()).append("\n");
        
        Map<String, Boolean> validationResults = validateAllConfigs();
        if (validationResults.isEmpty()) {
            report.append("当前没有配置任何子代理类型\n");
        } else {
            report.append("已配置的代理类型:\n");
            for (Map.Entry<String, Boolean> entry : validationResults.entrySet()) {
                String status = entry.getValue() ? "有效" : "无效";
                report.append("  - ").append(entry.getKey()).append(": ").append(status).append("\n");
            }
        }
        
        return report.toString();
    }
}