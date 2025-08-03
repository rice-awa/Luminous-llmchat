package com.riceawa.mcp.service;

import com.riceawa.mcp.client.MCPClient;
import com.riceawa.mcp.config.MCPConfig;
import com.riceawa.llm.logging.LogManager;
import io.modelcontextprotocol.spec.McpSchema.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * MCP 服务实现
 * 提供高级的 MCP 功能封装
 */
public class MCPServiceImpl implements MCPService {
    private static final LogManager logger = LogManager.getInstance();
    private static MCPServiceImpl instance;
    
    private MCPClientManager clientManager;
    private volatile boolean initialized = false;

    private MCPServiceImpl() {
        this.clientManager = MCPClientManager.getInstance();
    }

    public static synchronized MCPServiceImpl getInstance() {
        if (instance == null) {
            instance = new MCPServiceImpl();
        }
        return instance;
    }

    @Override
    public CompletableFuture<Void> initialize(MCPConfig config) {
        LogManager.getInstance().info("初始化 MCP 服务...");
        
        return clientManager.initialize(config)
            .thenRun(() -> {
                initialized = true;
                LogManager.getInstance().info("MCP 服务初始化完成");
            })
            .exceptionally(throwable -> {
                LogManager.getInstance().error("MCP 服务初始化失败: " + throwable.getMessage());
                initialized = false;
                return null;
            });
    }

    @Override
    public void shutdown() {
        LogManager.getInstance().info("关闭 MCP 服务...");
        
        if (clientManager != null) {
            clientManager.shutdown();
        }
        
        initialized = false;
        LogManager.getInstance().info("MCP 服务已关闭");
    }

    @Override
    public boolean isInitialized() {
        return initialized && clientManager.isInitialized();
    }

    @Override
    public boolean isEnabled() {
        return clientManager.isEnabled();
    }

    @Override
    public CompletableFuture<Map<String, List<Tool>>> getAllTools() {
        if (!isInitialized()) {
            return CompletableFuture.failedFuture(new RuntimeException("MCP 服务未初始化"));
        }
        
        return clientManager.getAllTools();
    }

    @Override
    public CompletableFuture<CallToolResult> callTool(String toolName, Map<String, Object> arguments) {
        if (!isInitialized()) {
            return CompletableFuture.failedFuture(new RuntimeException("MCP 服务未初始化"));
        }
        
        // 自动查找提供该工具的服务器
        return clientManager.findToolProvider(toolName)
            .thenCompose(providerOpt -> {
                if (providerOpt.isPresent()) {
                    MCPClient client = providerOpt.get();
                    LogManager.getInstance().info("使用服务器 " + client.getServerName() + " 调用工具: " + toolName);
                    return client.callTool(toolName, arguments);
                } else {
                    return CompletableFuture.failedFuture(
                        new RuntimeException("未找到提供工具 '" + toolName + "' 的服务器"));
                }
            });
    }

    @Override
    public CompletableFuture<CallToolResult> callTool(String serverName, String toolName, Map<String, Object> arguments) {
        if (!isInitialized()) {
            return CompletableFuture.failedFuture(new RuntimeException("MCP 服务未初始化"));
        }
        
        return clientManager.callTool(serverName, toolName, arguments);
    }

    @Override
    public CompletableFuture<Map<String, List<Resource>>> getAllResources() {
        if (!isInitialized()) {
            return CompletableFuture.failedFuture(new RuntimeException("MCP 服务未初始化"));
        }
        
        return clientManager.getAllResources();
    }

    @Override
    public CompletableFuture<ReadResourceResult> readResource(String serverName, String uri) {
        if (!isInitialized()) {
            return CompletableFuture.failedFuture(new RuntimeException("MCP 服务未初始化"));
        }
        
        return clientManager.readResource(serverName, uri);
    }

    @Override
    public CompletableFuture<Map<String, List<Prompt>>> getAllPrompts() {
        if (!isInitialized()) {
            return CompletableFuture.failedFuture(new RuntimeException("MCP 服务未初始化"));
        }
        
        return clientManager.getAllPrompts();
    }

    @Override
    public CompletableFuture<GetPromptResult> getPrompt(String serverName, String name, Map<String, Object> arguments) {
        if (!isInitialized()) {
            return CompletableFuture.failedFuture(new RuntimeException("MCP 服务未初始化"));
        }
        
        return clientManager.getPrompt(serverName, name, arguments);
    }

    @Override
    public Map<String, Boolean> getServerStatus() {
        if (clientManager != null) {
            return clientManager.getServerStatus();
        }
        return Map.of();
    }

    /**
     * 获取详细的服务器状态信息
     */
    public Map<String, MCPClientManager.ServerStatusInfo> getDetailedServerStatus() {
        if (clientManager != null) {
            return clientManager.getDetailedServerStatus();
        }
        return Map.of();
    }

    /**
     * 获取服务器总数（包括未连接的）
     */
    public int getTotalServerCount() {
        if (clientManager != null) {
            return clientManager.getTotalServerCount();
        }
        return 0;
    }

    @Override
    public int getConnectedServerCount() {
        if (clientManager != null) {
            return clientManager.getConnectedServerCount();
        }
        return 0;
    }

    @Override
    public CompletableFuture<Void> reload(MCPConfig config) {
        LogManager.getInstance().info("重新加载 MCP 服务配置...");
        
        return clientManager.initialize(config)
            .thenRun(() -> {
                initialized = true;
                LogManager.getInstance().info("MCP 服务配置重新加载完成");
            })
            .exceptionally(throwable -> {
                LogManager.getInstance().error("MCP 服务配置重新加载失败: " + throwable.getMessage());
                return null;
            });
    }

    /**
     * 获取客户端管理器（内部使用）
     */
    protected MCPClientManager getClientManager() {
        return clientManager;
    }
}