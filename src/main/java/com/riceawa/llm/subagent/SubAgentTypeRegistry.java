package com.riceawa.llm.subagent;

import com.riceawa.llm.logging.LogManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 子代理类型注册表
 * 提供插件式的子代理类型发现和管理功能
 */
public class SubAgentTypeRegistry {
    
    private static SubAgentTypeRegistry instance;
    
    // 类型信息注册表
    private final Map<String, SubAgentTypeInfo> typeInfoMap;
    
    // 类型依赖关系
    private final Map<String, Set<String>> typeDependencies;
    
    // 类型加载状态
    private final Map<String, TypeLoadStatus> typeLoadStatus;
    
    private SubAgentTypeRegistry() {
        this.typeInfoMap = new ConcurrentHashMap<>();
        this.typeDependencies = new ConcurrentHashMap<>();
        this.typeLoadStatus = new ConcurrentHashMap<>();
        
        LogManager.getInstance().system("SubAgentTypeRegistry initialized");
    }
    
    /**
     * 获取注册表单例实例
     * 
     * @return 注册表实例
     */
    public static SubAgentTypeRegistry getInstance() {
        if (instance == null) {
            synchronized (SubAgentTypeRegistry.class) {
                if (instance == null) {
                    instance = new SubAgentTypeRegistry();
                }
            }
        }
        return instance;
    }
    
    /**
     * 注册子代理类型信息
     * 
     * @param typeInfo 类型信息
     */
    public void registerTypeInfo(SubAgentTypeInfo typeInfo) {
        if (typeInfo == null || typeInfo.getTypeName() == null) {
            throw new IllegalArgumentException("Type info and type name cannot be null");
        }
        
        String typeName = typeInfo.getTypeName();
        typeInfoMap.put(typeName, typeInfo);
        typeLoadStatus.put(typeName, TypeLoadStatus.REGISTERED);
        
        // 注册依赖关系
        if (typeInfo.getDependencies() != null && !typeInfo.getDependencies().isEmpty()) {
            typeDependencies.put(typeName, new HashSet<>(typeInfo.getDependencies()));
        }
        
        LogManager.getInstance().system("Registered sub-agent type info: " + typeName);
    }
    
    /**
     * 取消注册子代理类型信息
     * 
     * @param typeName 类型名称
     */
    public void unregisterTypeInfo(String typeName) {
        if (typeName != null) {
            typeInfoMap.remove(typeName);
            typeDependencies.remove(typeName);
            typeLoadStatus.remove(typeName);
            
            LogManager.getInstance().system("Unregistered sub-agent type info: " + typeName);
        }
    }
    
    /**
     * 获取类型信息
     * 
     * @param typeName 类型名称
     * @return 类型信息，不存在时返回null
     */
    public SubAgentTypeInfo getTypeInfo(String typeName) {
        return typeInfoMap.get(typeName);
    }
    
    /**
     * 获取所有已注册的类型名称
     * 
     * @return 类型名称集合
     */
    public Set<String> getAllTypeNames() {
        return new HashSet<>(typeInfoMap.keySet());
    }
    
    /**
     * 获取所有已注册的类型信息
     * 
     * @return 类型信息列表
     */
    public List<SubAgentTypeInfo> getAllTypeInfos() {
        return new ArrayList<>(typeInfoMap.values());
    }
    
    /**
     * 检查类型是否已注册
     * 
     * @param typeName 类型名称
     * @return 是否已注册
     */
    public boolean isTypeRegistered(String typeName) {
        return typeInfoMap.containsKey(typeName);
    }
    
    /**
     * 获取类型的依赖关系
     * 
     * @param typeName 类型名称
     * @return 依赖的类型集合
     */
    public Set<String> getTypeDependencies(String typeName) {
        Set<String> dependencies = typeDependencies.get(typeName);
        return dependencies != null ? new HashSet<>(dependencies) : new HashSet<>();
    }
    
    /**
     * 检查类型依赖是否满足
     * 
     * @param typeName 类型名称
     * @return 依赖是否满足
     */
    public boolean areDependenciesSatisfied(String typeName) {
        Set<String> dependencies = getTypeDependencies(typeName);
        if (dependencies.isEmpty()) {
            return true; // 无依赖
        }
        
        for (String dependency : dependencies) {
            if (!isTypeRegistered(dependency)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 获取类型加载状态
     * 
     * @param typeName 类型名称
     * @return 加载状态
     */
    public TypeLoadStatus getTypeLoadStatus(String typeName) {
        return typeLoadStatus.getOrDefault(typeName, TypeLoadStatus.UNKNOWN);
    }
    
    /**
     * 设置类型加载状态
     * 
     * @param typeName 类型名称
     * @param status 加载状态
     */
    public void setTypeLoadStatus(String typeName, TypeLoadStatus status) {
        if (typeName != null && status != null) {
            typeLoadStatus.put(typeName, status);
        }
    }
    
    /**
     * 发现并注册可用的子代理类型
     * 这是一个插件式发现机制的入口点
     */
    public void discoverAndRegisterTypes() {
        LogManager.getInstance().system("Starting sub-agent type discovery...");
        
        // 这里可以实现自动发现机制，例如：
        // 1. 扫描classpath中的特定注解
        // 2. 读取配置文件
        // 3. 通过SPI机制加载
        
        // 目前先手动注册已知类型
        registerBuiltinTypes();
        
        LogManager.getInstance().system("Sub-agent type discovery completed. Found " + 
            typeInfoMap.size() + " types.");
    }
    
    /**
     * 注册内置的子代理类型
     */
    private void registerBuiltinTypes() {
        // 这里会在后续任务中添加具体的类型注册
        // 例如：智能搜索子代理类型
    }
    
    /**
     * 按依赖关系排序类型
     * 
     * @param typeNames 类型名称集合
     * @return 排序后的类型名称列表
     */
    public List<String> sortTypesByDependencies(Set<String> typeNames) {
        List<String> sorted = new ArrayList<>();
        Set<String> processed = new HashSet<>();
        Set<String> processing = new HashSet<>();
        
        for (String typeName : typeNames) {
            if (!processed.contains(typeName)) {
                sortTypeRecursive(typeName, sorted, processed, processing);
            }
        }
        
        return sorted;
    }
    
    /**
     * 递归排序类型（拓扑排序）
     */
    private void sortTypeRecursive(String typeName, List<String> sorted, 
                                  Set<String> processed, Set<String> processing) {
        if (processing.contains(typeName)) {
            LogManager.getInstance().error("Circular dependency detected for type: " + typeName);
            return;
        }
        
        if (processed.contains(typeName)) {
            return;
        }
        
        processing.add(typeName);
        
        Set<String> dependencies = getTypeDependencies(typeName);
        for (String dependency : dependencies) {
            if (isTypeRegistered(dependency)) {
                sortTypeRecursive(dependency, sorted, processed, processing);
            }
        }
        
        processing.remove(typeName);
        processed.add(typeName);
        sorted.add(typeName);
    }
    
    /**
     * 清空所有注册信息
     */
    public void clear() {
        typeInfoMap.clear();
        typeDependencies.clear();
        typeLoadStatus.clear();
        
        LogManager.getInstance().system("Cleared all sub-agent type registry data");
    }
    
    /**
     * 类型加载状态枚举
     */
    public enum TypeLoadStatus {
        UNKNOWN,        // 未知状态
        REGISTERED,     // 已注册
        LOADING,        // 加载中
        LOADED,         // 已加载
        FAILED,         // 加载失败
        DISABLED        // 已禁用
    }
}