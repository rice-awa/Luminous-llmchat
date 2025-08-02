package com.riceawa.mcp.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP客户端配置类
 * 管理MCP服务器连接配置和相关设置
 */
public class MCPConfig {
    // MCP功能总开关
    private boolean enabled = false;
    
    // MCP服务器配置列表
    private List<MCPServerConfig> servers = new ArrayList<>();
    
    // 连接超时时间（毫秒）
    private int connectionTimeoutMs = 30000;
    
    // 请求超时时间（毫秒）
    private int requestTimeoutMs = 10000;
    
    // 最大重试次数
    private int maxRetries = 3;
    
    // 是否启用资源缓存
    private boolean enableResourceCaching = true;
    
    // 资源缓存大小
    private int resourceCacheSize = 100;
    
    // 资源缓存TTL（分钟）
    private int resourceCacheTtlMinutes = 30;
    
    // 默认权限策略
    private String defaultPermissionPolicy = "OP_ONLY";
    
    // 是否启用工具变化通知
    private boolean enableToolChangeNotifications = true;
    
    // 是否启用资源变化通知
    private boolean enableResourceChangeNotifications = true;

    /**
     * 创建默认配置
     */
    public static MCPConfig createDefault() {
        MCPConfig config = new MCPConfig();
        config.enabled = false;
        config.servers = new ArrayList<>();
        return config;
    }

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<MCPServerConfig> getServers() {
        return new ArrayList<>(servers);
    }

    public void setServers(List<MCPServerConfig> servers) {
        this.servers = servers != null ? new ArrayList<>(servers) : new ArrayList<>();
    }

    public void addServer(MCPServerConfig server) {
        if (server != null && server.isValid()) {
            // 移除同名服务器配置
            servers.removeIf(s -> s.getName().equals(server.getName()));
            servers.add(server);
        }
    }

    public void removeServer(String serverName) {
        servers.removeIf(s -> s.getName().equals(serverName));
    }

    public MCPServerConfig getServer(String serverName) {
        return servers.stream()
                .filter(s -> s.getName().equals(serverName))
                .findFirst()
                .orElse(null);
    }

    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public void setConnectionTimeoutMs(int connectionTimeoutMs) {
        this.connectionTimeoutMs = Math.max(1000, connectionTimeoutMs);
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = Math.max(1000, requestTimeoutMs);
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = Math.max(0, Math.min(10, maxRetries));
    }

    public boolean isEnableResourceCaching() {
        return enableResourceCaching;
    }

    public void setEnableResourceCaching(boolean enableResourceCaching) {
        this.enableResourceCaching = enableResourceCaching;
    }

    public int getResourceCacheSize() {
        return resourceCacheSize;
    }

    public void setResourceCacheSize(int resourceCacheSize) {
        this.resourceCacheSize = Math.max(10, Math.min(1000, resourceCacheSize));
    }

    public int getResourceCacheTtlMinutes() {
        return resourceCacheTtlMinutes;
    }

    public void setResourceCacheTtlMinutes(int resourceCacheTtlMinutes) {
        this.resourceCacheTtlMinutes = Math.max(1, resourceCacheTtlMinutes);
    }

    public String getDefaultPermissionPolicy() {
        return defaultPermissionPolicy;
    }

    public void setDefaultPermissionPolicy(String defaultPermissionPolicy) {
        this.defaultPermissionPolicy = defaultPermissionPolicy != null ? defaultPermissionPolicy : "OP_ONLY";
    }

    public boolean isEnableToolChangeNotifications() {
        return enableToolChangeNotifications;
    }

    public void setEnableToolChangeNotifications(boolean enableToolChangeNotifications) {
        this.enableToolChangeNotifications = enableToolChangeNotifications;
    }

    public boolean isEnableResourceChangeNotifications() {
        return enableResourceChangeNotifications;
    }

    public void setEnableResourceChangeNotifications(boolean enableResourceChangeNotifications) {
        this.enableResourceChangeNotifications = enableResourceChangeNotifications;
    }

    /**
     * 验证配置是否有效
     */
    public boolean isValid() {
        if (!enabled) {
            return true; // 如果未启用，配置总是有效的
        }
        
        // 检查是否至少有一个有效的服务器配置
        return servers.stream().anyMatch(MCPServerConfig::isValid);
    }

    /**
     * 获取所有启用的服务器配置
     */
    public List<MCPServerConfig> getEnabledServers() {
        return servers.stream()
                .filter(MCPServerConfig::isEnabled)
                .filter(MCPServerConfig::isValid)
                .toList();
    }

    /**
     * 检查是否有任何启用的服务器
     */
    public boolean hasEnabledServers() {
        return !getEnabledServers().isEmpty();
    }
}