package com.riceawa.mcp.service;

import com.riceawa.mcp.client.MCPClient;
import com.riceawa.mcp.client.MCPClientFactory;
import com.riceawa.mcp.config.MCPConfig;
import com.riceawa.mcp.config.MCPServerConfig;
import com.riceawa.llm.logging.LogManager;
import io.modelcontextprotocol.spec.McpSchema.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * MCP 客户端管理器
 * 管理多个 MCP 服务器连接，提供统一的接口
 */
public class MCPClientManager {
    private static final LogManager logger = LogManager.getInstance();
    private static MCPClientManager instance;
    
    private final Map<String, MCPClient> clients = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private MCPConfig config;
    private volatile boolean initialized = false;
    
    private MCPClientManager() {
        // 启动健康检查任务
        scheduler.scheduleAtFixedRate(this::performHealthCheck, 30, 30, TimeUnit.SECONDS);
    }

    public static synchronized MCPClientManager getInstance() {
        if (instance == null) {
            instance = new MCPClientManager();
        }
        return instance;
    }

    /**
     * 初始化管理器
     * 
     * @param config MCP 配置
     */
    public CompletableFuture<Void> initialize(MCPConfig config) {
        this.config = config;
        
        if (!config.isEnabled()) {
            logger.info("MCP 功能已禁用");
            initialized = true;
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            try {
                logger.info("初始化 MCP 客户端管理器...");
                
                // 断开所有现有连接
                disconnectAll();
                
                // 创建并连接所有启用的服务器
                List<CompletableFuture<Void>> connectionTasks = new ArrayList<>();
                
                for (MCPServerConfig serverConfig : config.getEnabledServers()) {
                    CompletableFuture<Void> task = connectToServer(serverConfig);
                    connectionTasks.add(task);
                }
                
                // 等待所有连接完成
                CompletableFuture.allOf(connectionTasks.toArray(new CompletableFuture[0])).join();
                
                initialized = true;
                logger.info("MCP 客户端管理器初始化完成，已连接 {} 个服务器", getConnectedServerCount());
                
            } catch (Exception e) {
                logger.error("初始化 MCP 客户端管理器失败", e);
                throw new RuntimeException("MCP 初始化失败", e);
            }
        });
    }

    /**
     * 连接到指定服务器
     */
    public CompletableFuture<Void> connectToServer(MCPServerConfig serverConfig) {
        return CompletableFuture.runAsync(() -> {
            try {
                logger.info("正在连接到 MCP 服务器: {}", serverConfig.getName());
                
                MCPClient client = MCPClientFactory.createClient(serverConfig);
                clients.put(serverConfig.getName(), client);
                
                boolean connected = client.connect().get(30, TimeUnit.SECONDS);
                if (connected) {
                    logger.info("成功连接到 MCP 服务器: {}", serverConfig.getName());
                } else {
                    logger.error("连接 MCP 服务器失败: {}", serverConfig.getName());
                    clients.remove(serverConfig.getName());
                }
                
            } catch (Exception e) {
                logger.error("连接 MCP 服务器时出错: {} - {}", serverConfig.getName(), e.getMessage());
                clients.remove(serverConfig.getName());
            }
        });
    }

    /**
     * 断开指定服务器的连接
     */
    public void disconnectFromServer(String serverName) {
        MCPClient client = clients.remove(serverName);
        if (client != null) {
            try {
                client.disconnect();
                logger.info("已断开 MCP 服务器连接: {}", serverName);
            } catch (Exception e) {
                logger.error("断开 MCP 服务器连接时出错: {} - {}", serverName, e.getMessage());
            }
        }
    }

    /**
     * 断开所有连接
     */
    public void disconnectAll() {
        List<String> serverNames = new ArrayList<>(clients.keySet());
        for (String serverName : serverNames) {
            disconnectFromServer(serverName);
        }
    }

    /**
     * 获取所有可用的工具
     */
    public CompletableFuture<Map<String, List<Tool>>> getAllTools() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, List<Tool>> allTools = new HashMap<>();
            List<CompletableFuture<Void>> tasks = new ArrayList<>();
            
            for (MCPClient client : getConnectedClients()) {
                CompletableFuture<Void> task = client.listTools()
                    .thenAccept(tools -> allTools.put(client.getServerName(), tools))
                    .exceptionally(throwable -> {
                        logger.error("获取工具列表失败: {} - {}", client.getServerName(), throwable.getMessage());
                        return null;
                    });
                tasks.add(task);
            }
            
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
            return allTools;
        });
    }

    /**
     * 调用指定服务器上的工具
     */
    public CompletableFuture<CallToolResult> callTool(String serverName, String toolName, Map<String, Object> arguments) {
        MCPClient client = clients.get(serverName);
        if (client == null || !client.isConnected()) {
            return CompletableFuture.failedFuture(new RuntimeException("服务器未连接: " + serverName));
        }
        
        return client.callTool(toolName, arguments);
    }

    /**
     * 在所有服务器上查找工具
     */
    public CompletableFuture<Optional<MCPClient>> findToolProvider(String toolName) {
        return getAllTools().thenApply(allTools -> {
            for (Map.Entry<String, List<Tool>> entry : allTools.entrySet()) {
                String serverName = entry.getKey();
                List<Tool> tools = entry.getValue();
                
                boolean hasToolOrPattern = tools.stream().anyMatch(tool -> {
                    String name = tool.name();
                    // 精确匹配
                    if (toolName.equals(name)) {
                        return true;
                    }
                    // 简单的通配符匹配
                    if (name.contains("*")) {
                        String pattern = name.replace("*", ".*");
                        return toolName.matches(pattern);
                    }
                    return false;
                });
                
                if (hasToolOrPattern) {
                    MCPClient client = clients.get(serverName);
                    if (client != null && client.isConnected()) {
                        return Optional.of(client);
                    }
                }
            }
            return Optional.empty();
        });
    }

    /**
     * 获取所有可用的资源
     */
    public CompletableFuture<Map<String, List<Resource>>> getAllResources() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, List<Resource>> allResources = new HashMap<>();
            List<CompletableFuture<Void>> tasks = new ArrayList<>();
            
            for (MCPClient client : getConnectedClients()) {
                CompletableFuture<Void> task = client.listResources()
                    .thenAccept(resources -> allResources.put(client.getServerName(), resources))
                    .exceptionally(throwable -> {
                        logger.error("获取资源列表失败: {} - {}", client.getServerName(), throwable.getMessage());
                        return null;
                    });
                tasks.add(task);
            }
            
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
            return allResources;
        });
    }

    /**
     * 读取指定服务器上的资源
     */
    public CompletableFuture<ReadResourceResult> readResource(String serverName, String uri) {
        MCPClient client = clients.get(serverName);
        if (client == null || !client.isConnected()) {
            return CompletableFuture.failedFuture(new RuntimeException("服务器未连接: " + serverName));
        }
        
        return client.readResource(uri);
    }

    /**
     * 获取所有可用的提示
     */
    public CompletableFuture<Map<String, List<Prompt>>> getAllPrompts() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, List<Prompt>> allPrompts = new HashMap<>();
            List<CompletableFuture<Void>> tasks = new ArrayList<>();
            
            for (MCPClient client : getConnectedClients()) {
                CompletableFuture<Void> task = client.listPrompts()
                    .thenAccept(prompts -> allPrompts.put(client.getServerName(), prompts))
                    .exceptionally(throwable -> {
                        logger.error("获取提示列表失败: {} - {}", client.getServerName(), throwable.getMessage());
                        return null;
                    });
                tasks.add(task);
            }
            
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
            return allPrompts;
        });
    }

    /**
     * 获取指定服务器上的提示
     */
    public CompletableFuture<GetPromptResult> getPrompt(String serverName, String name, Map<String, Object> arguments) {
        MCPClient client = clients.get(serverName);
        if (client == null || !client.isConnected()) {
            return CompletableFuture.failedFuture(new RuntimeException("服务器未连接: " + serverName));
        }
        
        return client.getPrompt(name, arguments);
    }

    /**
     * 获取已连接的客户端列表
     */
    public List<MCPClient> getConnectedClients() {
        return clients.values().stream()
                .filter(MCPClient::isConnected)
                .collect(Collectors.toList());
    }

    /**
     * 获取已连接的服务器数量
     */
    public int getConnectedServerCount() {
        return (int) clients.values().stream()
                .filter(MCPClient::isConnected)
                .count();
    }

    /**
     * 获取服务器状态信息
     */
    public Map<String, Boolean> getServerStatus() {
        Map<String, Boolean> status = new HashMap<>();
        for (Map.Entry<String, MCPClient> entry : clients.entrySet()) {
            status.put(entry.getKey(), entry.getValue().isConnected());
        }
        return status;
    }

    /**
     * 检查管理器是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 检查是否启用
     */
    public boolean isEnabled() {
        return config != null && config.isEnabled();
    }

    /**
     * 执行健康检查
     */
    private void performHealthCheck() {
        if (!isEnabled() || !initialized) {
            return;
        }
        
        logger.debug("执行 MCP 客户端健康检查...");
        
        List<String> unhealthyServers = new ArrayList<>();
        for (Map.Entry<String, MCPClient> entry : clients.entrySet()) {
            String serverName = entry.getKey();
            MCPClient client = entry.getValue();
            
            if (!client.isHealthy()) {
                unhealthyServers.add(serverName);
                logger.warn("MCP 服务器健康检查失败: {}", serverName);
            }
        }
        
        // 尝试重连不健康的服务器
        for (String serverName : unhealthyServers) {
            MCPServerConfig serverConfig = config.getServer(serverName);
            if (serverConfig != null && serverConfig.isEnabled()) {
                logger.info("尝试重连 MCP 服务器: {}", serverName);
                disconnectFromServer(serverName);
                connectToServer(serverConfig);
            }
        }
    }

    /**
     * 关闭管理器
     */
    public void shutdown() {
        logger.info("关闭 MCP 客户端管理器...");
        
        disconnectAll();
        scheduler.shutdown();
        
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        initialized = false;
        logger.info("MCP 客户端管理器已关闭");
    }
}