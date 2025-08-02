package com.riceawa.mcp.function;

import com.riceawa.mcp.service.MCPService;
import com.riceawa.mcp.service.MCPServiceImpl;
import com.riceawa.mcp.service.MCPClientManager;
import com.riceawa.mcp.model.MCPTool;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * MCP集成管理器
 * 协调MCP服务、功能注册器和权限管理器，提供统一的MCP功能集成接口
 */
public class MCPIntegrationManager {
    
    private static MCPIntegrationManager instance;
    
    private final MCPService mcpService;
    private final MCPFunctionRegistry functionRegistry;
    private final MCPToolPermissionManagerImpl permissionManager;
    
    // 是否已初始化
    private volatile boolean initialized = false;
    
    private MCPIntegrationManager(MCPClientManager clientManager) {
        // 创建权限管理器
        this.permissionManager = new MCPToolPermissionManagerImpl();
        
        // 创建MCP服务
        this.mcpService = new MCPServiceImpl(clientManager);
        
        // 创建功能注册器
        this.functionRegistry = new MCPFunctionRegistry(mcpService, permissionManager);
        
        // 初始化功能注册器单例
        MCPFunctionRegistry.initialize(mcpService, permissionManager);
    }
    
    /**
     * 获取单例实例
     */
    public static MCPIntegrationManager getInstance() {
        return instance;
    }
    
    /**
     * 初始化集成管理器
     */
    public static synchronized MCPIntegrationManager initialize(MCPClientManager clientManager) {
        if (instance == null) {
            instance = new MCPIntegrationManager(clientManager);
        }
        return instance;
    }
    
    /**
     * 启动MCP集成
     */
    public CompletableFuture<Void> start() {
        if (initialized) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            try {
                // 1. 配置默认权限策略
                setupDefaultPermissions();
                
                // 2. 注册工具变化监听器
                if (mcpService instanceof MCPServiceImpl) {
                    MCPServiceImpl serviceImpl = (MCPServiceImpl) mcpService;
                    serviceImpl.addToolChangeListener(functionRegistry);
                }
                
                // 3. 启用自动注册
                functionRegistry.setAutoRegistrationEnabled(true);
                
                // 4. 注册现有的工具
                registerExistingTools();
                
                initialized = true;
                
            } catch (Exception e) {
                throw new RuntimeException("启动MCP集成失败", e);
            }
        });
    }
    
    /**
     * 停止MCP集成
     */
    public CompletableFuture<Void> stop() {
        if (!initialized) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            try {
                // 1. 禁用自动注册
                functionRegistry.setAutoRegistrationEnabled(false);
                
                // 2. 注销所有MCP工具
                unregisterAllTools();
                
                // 3. 清除权限缓存
                permissionManager.clearPermissionCache();
                
                // 4. 关闭MCP服务
                mcpService.shutdown();
                
                initialized = false;
                
            } catch (Exception e) {
                throw new RuntimeException("停止MCP集成失败", e);
            }
        });
    }
    
    /**
     * 获取MCP服务
     */
    public MCPService getMCPService() {
        return mcpService;
    }
    
    /**
     * 获取功能注册器
     */
    public MCPFunctionRegistry getFunctionRegistry() {
        return functionRegistry;
    }
    
    /**
     * 获取权限管理器
     */
    public MCPToolPermissionManagerImpl getPermissionManager() {
        return permissionManager;
    }
    
    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * 手动刷新所有工具
     */
    public CompletableFuture<Void> refreshAllTools() {
        if (!initialized) {
            return CompletableFuture.failedFuture(new IllegalStateException("MCP集成未初始化"));
        }
        
        return mcpService.refreshAllTools().thenRun(() -> {
            // 刷新完成后重新注册工具
            registerExistingTools();
        });
    }
    
    /**
     * 手动注册客户端工具
     */
    public CompletableFuture<Integer> registerClientTools(String clientName) {
        if (!initialized) {
            return CompletableFuture.failedFuture(new IllegalStateException("MCP集成未初始化"));
        }
        
        return mcpService.listTools(clientName).thenApply(tools -> {
            return functionRegistry.registerClientTools(clientName, tools);
        });
    }
    
    /**
     * 获取集成状态报告
     */
    public IntegrationStatus getStatus() {
        if (!initialized) {
            return new IntegrationStatus(false, 0, 0, null);
        }
        
        int registeredTools = functionRegistry.getRegisteredToolCount();
        int connectedClients = mcpService.getClientStatuses().size();
        var permissionStats = permissionManager.getStatistics();
        
        return new IntegrationStatus(true, registeredTools, connectedClients, permissionStats);
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * 设置默认权限策略
     */
    private void setupDefaultPermissions() {
        // 设置默认策略为允许所有玩家访问
        permissionManager.setDefaultPermissionPolicy(MCPToolPermissionManager.PermissionPolicy.ALLOW_ALL);
        
        // 可以在这里添加一些基于工具名称的默认规则
        // 例如，管理类工具仅允许OP访问
        addDefaultPermissionRules();
    }
    
    /**
     * 添加默认权限规则
     */
    private void addDefaultPermissionRules() {
        // 添加管理类工具的权限规则
        permissionManager.addCustomRule(
            new MCPToolPermissionManagerImpl.PatternBasedRule(
                "admin_tools",
                "管理类工具仅允许OP访问",
                ".*(?:admin|manage|delete|remove|destroy|kill).*",
                (player, tool) -> player.hasPermissionLevel(2),
                100 // 高优先级
            )
        );
        
        // 添加危险工具的权限规则
        permissionManager.addCustomRule(
            new MCPToolPermissionManagerImpl.PatternBasedRule(
                "dangerous_tools", 
                "危险工具仅允许OP访问",
                ".*(?:execute|command|shell|script|system).*",
                (player, tool) -> player.hasPermissionLevel(2),
                90
            )
        );
        
        // 添加文件操作工具的权限规则
        permissionManager.addCustomRule(
            new MCPToolPermissionManagerImpl.PatternBasedRule(
                "file_tools",
                "文件操作工具仅允许OP访问", 
                ".*(?:file|write|read|upload|download).*",
                (player, tool) -> player.hasPermissionLevel(2),
                80
            )
        );
    }
    
    /**
     * 注册现有的工具
     */
    private void registerExistingTools() {
        mcpService.listAllTools().whenComplete((tools, throwable) -> {
            if (throwable != null) {
                System.err.println("获取工具列表失败: " + throwable.getMessage());
                return;
            }
            
            if (tools != null && !tools.isEmpty()) {
                // 按客户端分组注册
                Map<String, List<MCPTool>> clientToolsMap = new HashMap<>();
                for (MCPTool tool : tools) {
                    String clientName = tool.getClientName();
                    if (clientName != null) {
                        clientToolsMap.computeIfAbsent(clientName, k -> new ArrayList<>()).add(tool);
                    }
                }
                
                // 为每个客户端注册工具
                for (Map.Entry<String, List<MCPTool>> entry : clientToolsMap.entrySet()) {
                    String clientName = entry.getKey();
                    List<MCPTool> clientTools = entry.getValue();
                    int registeredCount = functionRegistry.registerClientTools(clientName, clientTools);
                    System.out.println("已为客户端 " + clientName + " 注册 " + registeredCount + " 个工具");
                }
            }
        });
    }
    
    /**
     * 注销所有工具
     */
    private void unregisterAllTools() {
        var adapters = functionRegistry.getAllMCPAdapters();
        for (var adapter : adapters) {
            functionRegistry.unregisterMCPTool(adapter.getName());
        }
    }
    
    // ==================== 内部类 ====================
    
    /**
     * 集成状态信息
     */
    public static class IntegrationStatus {
        private final boolean initialized;
        private final int registeredToolCount;
        private final int connectedClientCount;
        private final MCPToolPermissionManagerImpl.PermissionStatistics permissionStats;
        
        public IntegrationStatus(boolean initialized, int registeredToolCount, 
                               int connectedClientCount, MCPToolPermissionManagerImpl.PermissionStatistics permissionStats) {
            this.initialized = initialized;
            this.registeredToolCount = registeredToolCount;
            this.connectedClientCount = connectedClientCount;
            this.permissionStats = permissionStats;
        }
        
        public boolean isInitialized() { return initialized; }
        public int getRegisteredToolCount() { return registeredToolCount; }
        public int getConnectedClientCount() { return connectedClientCount; }
        public MCPToolPermissionManagerImpl.PermissionStatistics getPermissionStats() { return permissionStats; }
        
        @Override
        public String toString() {
            return String.format("IntegrationStatus{initialized=%s, tools=%d, clients=%d, permissions=%s}",
                               initialized, registeredToolCount, connectedClientCount, permissionStats);
        }
    }
}