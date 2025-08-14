package com.riceawa.llm.subagent;

import java.util.*;

/**
 * 子代理类型信息
 * 描述子代理类型的元数据信息
 */
public class SubAgentTypeInfo {
    
    private final String typeName;
    private final String displayName;
    private final String description;
    private final String version;
    private final String author;
    private final Set<String> dependencies;
    private final Map<String, Object> properties;
    private final boolean enabled;
    
    private SubAgentTypeInfo(Builder builder) {
        this.typeName = builder.typeName;
        this.displayName = builder.displayName;
        this.description = builder.description;
        this.version = builder.version;
        this.author = builder.author;
        this.dependencies = Collections.unmodifiableSet(new HashSet<>(builder.dependencies));
        this.properties = Collections.unmodifiableMap(new HashMap<>(builder.properties));
        this.enabled = builder.enabled;
    }
    
    /**
     * 获取类型名称
     * 
     * @return 类型名称
     */
    public String getTypeName() {
        return typeName;
    }
    
    /**
     * 获取显示名称
     * 
     * @return 显示名称
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * 获取描述
     * 
     * @return 描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 获取版本
     * 
     * @return 版本
     */
    public String getVersion() {
        return version;
    }
    
    /**
     * 获取作者
     * 
     * @return 作者
     */
    public String getAuthor() {
        return author;
    }
    
    /**
     * 获取依赖关系
     * 
     * @return 依赖的类型名称集合
     */
    public Set<String> getDependencies() {
        return dependencies;
    }
    
    /**
     * 获取属性
     * 
     * @return 属性映射
     */
    public Map<String, Object> getProperties() {
        return properties;
    }
    
    /**
     * 获取指定属性值
     * 
     * @param key 属性键
     * @return 属性值
     */
    public Object getProperty(String key) {
        return properties.get(key);
    }
    
    /**
     * 获取指定属性值，带默认值
     * 
     * @param key 属性键
     * @param defaultValue 默认值
     * @return 属性值或默认值
     */
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, T defaultValue) {
        Object value = properties.get(key);
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
     * 检查是否启用
     * 
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * 检查是否有依赖
     * 
     * @return 是否有依赖
     */
    public boolean hasDependencies() {
        return !dependencies.isEmpty();
    }
    
    @Override
    public String toString() {
        return "SubAgentTypeInfo{" +
                "typeName='" + typeName + '\'' +
                ", displayName='" + displayName + '\'' +
                ", version='" + version + '\'' +
                ", enabled=" + enabled +
                ", dependencies=" + dependencies.size() +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SubAgentTypeInfo that = (SubAgentTypeInfo) o;
        return Objects.equals(typeName, that.typeName);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(typeName);
    }
    
    /**
     * 构建器类
     */
    public static class Builder {
        private String typeName;
        private String displayName;
        private String description;
        private String version = "1.0.0";
        private String author;
        private Set<String> dependencies = new HashSet<>();
        private Map<String, Object> properties = new HashMap<>();
        private boolean enabled = true;
        
        public Builder(String typeName) {
            this.typeName = typeName;
            this.displayName = typeName; // 默认使用类型名称作为显示名称
        }
        
        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder version(String version) {
            this.version = version;
            return this;
        }
        
        public Builder author(String author) {
            this.author = author;
            return this;
        }
        
        public Builder dependency(String dependency) {
            if (dependency != null && !dependency.trim().isEmpty()) {
                this.dependencies.add(dependency);
            }
            return this;
        }
        
        public Builder dependencies(Collection<String> dependencies) {
            if (dependencies != null) {
                for (String dependency : dependencies) {
                    dependency(dependency);
                }
            }
            return this;
        }
        
        public Builder property(String key, Object value) {
            if (key != null) {
                this.properties.put(key, value);
            }
            return this;
        }
        
        public Builder properties(Map<String, Object> properties) {
            if (properties != null) {
                this.properties.putAll(properties);
            }
            return this;
        }
        
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        public SubAgentTypeInfo build() {
            if (typeName == null || typeName.trim().isEmpty()) {
                throw new IllegalArgumentException("Type name cannot be null or empty");
            }
            return new SubAgentTypeInfo(this);
        }
    }
    
    /**
     * 创建构建器
     * 
     * @param typeName 类型名称
     * @return 构建器实例
     */
    public static Builder builder(String typeName) {
        return new Builder(typeName);
    }
}