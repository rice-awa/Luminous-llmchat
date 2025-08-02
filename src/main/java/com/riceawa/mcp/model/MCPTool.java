package com.riceawa.mcp.model;

import com.google.gson.JsonObject;
import com.riceawa.mcp.exception.MCPException;
import com.riceawa.mcp.util.MCPJsonUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP工具模型
 * 表示从MCP服务器获取的工具信息
 */
public class MCPTool {
    // 工具名称
    private String name;
    
    // 工具标题
    private String title;
    
    // 工具描述
    private String description;
    
    // 输入参数schema
    private JsonObject inputSchema;
    
    // 输出结果schema（可选）
    private JsonObject outputSchema;
    
    // 所属客户端名称
    private String clientName;
    
    // 工具注解信息
    private Map<String, Object> annotations = new HashMap<>();

    public MCPTool() {
    }

    public MCPTool(String name, String title, String description) {
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

    public JsonObject getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(JsonObject inputSchema) {
        this.inputSchema = inputSchema;
    }

    public JsonObject getOutputSchema() {
        return outputSchema;
    }

    public void setOutputSchema(JsonObject outputSchema) {
        this.outputSchema = outputSchema;
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
     * 获取工具的完整名称（包含客户端前缀）
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
     * 检查工具是否有效
     */
    public boolean isValid() {
        return name != null && !name.trim().isEmpty();
    }

    @Override
    public String toString() {
        return String.format("MCPTool{name='%s', title='%s', client='%s'}", 
                           name, title, clientName);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        MCPTool mcpTool = (MCPTool) obj;
        return name != null ? name.equals(mcpTool.name) : mcpTool.name == null;
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
     * 从JSON字符串创建MCPTool对象
     */
    public static MCPTool fromJson(String json) throws MCPException {
        return MCPJsonUtils.fromJsonToTool(json);
    }

    /**
     * 创建当前对象的深度拷贝
     */
    public MCPTool deepCopy() throws MCPException {
        return fromJson(toJson());
    }
}