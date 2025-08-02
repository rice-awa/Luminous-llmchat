package com.riceawa.mcp.model;

import com.google.gson.JsonObject;
import com.riceawa.mcp.exception.MCPException;
import com.riceawa.mcp.util.MCPJsonUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP提示词模型
 * 表示从MCP服务器获取的提示词模板信息
 */
public class MCPPrompt {
    // 提示词名称
    private String name;
    
    // 提示词标题
    private String title;
    
    // 提示词描述
    private String description;
    
    // 参数schema
    private JsonObject argumentSchema;
    
    // 所属客户端名称
    private String clientName;
    
    // 提示词注解信息
    private Map<String, Object> annotations = new HashMap<>();

    public MCPPrompt() {
    }

    public MCPPrompt(String name, String title, String description) {
        this.name = name;
        this.title = title;
        this.description = description;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public JsonObject getArgumentSchema() {
        return argumentSchema;
    }

    public void setArgumentSchema(JsonObject argumentSchema) {
        this.argumentSchema = argumentSchema;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public Map<String, Object> getAnnotations() {
        return new HashMap<>(annotations);
    }

    public void setAnnotations(Map<String, Object> annotations) {
        this.annotations = annotations != null ? new HashMap<>(annotations) : new HashMap<>();
    }

    public void addAnnotation(String key, Object value) {
        if (key != null) {
            this.annotations.put(key, value);
        }
    }

    public Object getAnnotation(String key) {
        return annotations.get(key);
    }

    /**
     * 获取提示词的完整名称（包含客户端前缀）
     */
    public String getFullName() {
        if (clientName != null && !clientName.isEmpty()) {
            return "mcp_" + clientName + "_" + name;
        }
        return name;
    }

    /**
     * 获取显示名称
     */
    public String getDisplayName() {
        if (title != null && !title.trim().isEmpty()) {
            return title;
        }
        return name;
    }

    /**
     * 检查提示词是否有效
     */
    public boolean isValid() {
        return name != null && !name.trim().isEmpty();
    }

    /**
     * 检查是否有参数
     */
    public boolean hasArguments() {
        return argumentSchema != null && argumentSchema.has("properties");
    }

    @Override
    public String toString() {
        return String.format("MCPPrompt{name='%s', title='%s', client='%s'}", 
                           name, title, clientName);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        MCPPrompt mcpPrompt = (MCPPrompt) obj;
        return name != null ? name.equals(mcpPrompt.name) : mcpPrompt.name == null;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }

    // ==================== JSON序列化支持 ====================

    /**
     * 将当前对象序列化为JSON字符串
     */
    public String toJson() throws MCPException {
        return MCPJsonUtils.toJson(this);
    }

    /**
     * 从JSON字符串创建MCPPrompt对象
     */
    public static MCPPrompt fromJson(String json) throws MCPException {
        return MCPJsonUtils.fromJsonToPrompt(json);
    }

    /**
     * 创建当前对象的深度拷贝
     */
    public MCPPrompt deepCopy() throws MCPException {
        return fromJson(toJson());
    }
}