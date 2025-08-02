package com.riceawa.mcp.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP服务器配置类
 * 定义单个MCP服务器的连接参数和权限设置
 */
public class MCPServerConfig {
    // 服务器名称（唯一标识符）
    private String name;
    
    // 连接类型：stdio 或 sse
    private String type;
    
    // STDIO连接参数
    private String command; // 启动命令
    private List<String> args = new ArrayList<>(); // 命令参数
    private Map<String, String> env = new HashMap<>(); // 环境变量
    
    // SSE连接参数
    private String url; // SSE服务器URL
    
    // 服务器是否启用
    private boolean enabled = true;
    
    // 允许的工具列表（空表示允许所有）
    private Set<String> allowedTools = new HashSet<>();
    
    // 允许的资源列表（空表示允许所有）
    private Set<String> allowedResources = new HashSet<>();
    
    // 工具权限策略
    private String toolPermissionPolicy = "INHERIT_CLIENT";
    
    // 服务器描述
    private String description = "";

    /**
     * 创建STDIO类型的服务器配置
     */
    public static MCPServerConfig createStdioConfig(String name, String command, List<String> args) {
        MCPServerConfig config = new MCPServerConfig();
        config.name = name;
        config.type = "stdio";
        config.command = command;
        config.args = args != null ? new ArrayList<>(args) : new ArrayList<>();
        return config;
    }

    /**
     * 创建SSE类型的服务器配置
     */
    public static MCPServerConfig createSseConfig(String name, String url) {
        MCPServerConfig config = new MCPServerConfig();
        config.name = name;
        config.type = "sse";
        config.url = url;
        return config;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public List<String> getArgs() {
        return Collections.unmodifiableList(new ArrayList<>(args));
    }

    public void setArgs(List<String> args) {
        this.args = args != null ? new ArrayList<>(args) : new ArrayList<>();
    }

    public void addArg(String arg) {
        if (arg != null) {
            this.args.add(arg);
        }
    }

    public Map<String, String> getEnv() {
        return Collections.unmodifiableMap(new HashMap<>(env));
    }

    public void setEnv(Map<String, String> env) {
        this.env = env != null ? new HashMap<>(env) : new HashMap<>();
    }

    public void addEnv(String key, String value) {
        if (key != null && value != null) {
            this.env.put(key, value);
        }
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<String> getAllowedTools() {
        return Collections.unmodifiableSet(new HashSet<>(allowedTools));
    }

    public void setAllowedTools(Set<String> allowedTools) {
        this.allowedTools = allowedTools != null ? new HashSet<>(allowedTools) : new HashSet<>();
    }

    public void addAllowedTool(String toolName) {
        if (toolName != null && !toolName.trim().isEmpty()) {
            this.allowedTools.add(toolName.trim());
        }
    }

    public void removeAllowedTool(String toolName) {
        if (toolName != null) {
            this.allowedTools.remove(toolName.trim());
        }
    }

    public boolean isToolAllowed(String toolName) {
        if (allowedTools.isEmpty()) {
            return true; // 空列表表示允许所有工具
        }
        return toolName != null && allowedTools.contains(toolName);
    }

    public Set<String> getAllowedResources() {
        return Collections.unmodifiableSet(new HashSet<>(allowedResources));
    }

    public void setAllowedResources(Set<String> allowedResources) {
        this.allowedResources = allowedResources != null ? new HashSet<>(allowedResources) : new HashSet<>();
    }

    public void addAllowedResource(String resourceUri) {
        if (resourceUri != null && !resourceUri.trim().isEmpty()) {
            this.allowedResources.add(resourceUri.trim());
        }
    }

    public void removeAllowedResource(String resourceUri) {
        if (resourceUri != null) {
            this.allowedResources.remove(resourceUri.trim());
        }
    }

    public boolean isResourceAllowed(String resourceUri) {
        if (allowedResources.isEmpty()) {
            return true; // 空列表表示允许所有资源
        }
        return resourceUri != null && allowedResources.contains(resourceUri);
    }

    public String getToolPermissionPolicy() {
        return toolPermissionPolicy;
    }

    public void setToolPermissionPolicy(String toolPermissionPolicy) {
        this.toolPermissionPolicy = toolPermissionPolicy != null ? toolPermissionPolicy : "INHERIT_CLIENT";
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description != null ? description : "";
    }

    /**
     * 验证服务器配置是否有效
     */
    public boolean isValid() {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        if (type == null) {
            return false;
        }
        
        switch (type.toLowerCase()) {
            case "stdio":
                return command != null && !command.trim().isEmpty();
            case "sse":
                return url != null && !url.trim().isEmpty() && 
                       (url.startsWith("http://") || url.startsWith("https://"));
            default:
                return false;
        }
    }

    /**
     * 检查是否为STDIO类型
     */
    public boolean isStdioType() {
        return "stdio".equalsIgnoreCase(type);
    }

    /**
     * 检查是否为SSE类型
     */
    public boolean isSseType() {
        return "sse".equalsIgnoreCase(type);
    }

    @Override
    public String toString() {
        return String.format("MCPServerConfig{name='%s', type='%s', enabled=%s}", 
                           name, type, enabled);
    }
}