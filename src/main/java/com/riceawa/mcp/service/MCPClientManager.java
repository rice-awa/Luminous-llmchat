package com.riceawa.mcp.service;

import com.riceawa.mcp.config.MCPConfig;
import com.riceawa.mcp.config.MCPServerConfig;
import com.riceawa.mcp.exception.MCPException;
import com.riceawa.mcp.exception.MCPException.MCPErrorType;
import com.riceawa.mcp.model.MCPClientStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MCP客户端管理器
 * 负责管理多个MCP客户端连接，支持STDIO和SSE传输层
 * 实现连接池管理、状态监控、自动重连和生命周期管理
 */
public class MCPClientManager {
    private static final String TAG = "MCPClientManager";
    
    // 单例实例
    private static volatile MCPClientManager instance;
    
    // 配置对象
    private MCPConfig config;
    
    // 客户端映射：服务器名称 -> 客户端实例
    private final Map<String, MCPClient> clients = new ConcurrentHashMap<>();
    
    // 客户端状态映射：服务器名称 -> 状态信息
    private final Map<String, MCPClientStatus> clientStatuses = new ConcurrentHashMap<>();
    
    // 管理器是否已初始化
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    
    // 管理器是否正在运行
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    // 定时任务执行器（健康检查、重连等）
    private ScheduledExecutorService scheduler;
    
    // 异步任务执行器
    private ScheduledExecutorService asyncExecutor;
    
    // 客户端工厂
    private MCPClientFactory clientFactory;
    
    // 私有构造函数（单例模式）
    private MCPClientManager() {
        this.clientFactory = new MCPClientFactory();
    }
    
    /**
     * 获取单例实例
     */
    public static MCPClientManager getInstance() {
        if (instance == null) {
            synchronized (MCPClientManager.class) {
                if (instance == null) {
                    instance = new MCPClientManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * 初始化管理器
     */
    public synchronized CompletableFuture<Void> initialize(MCPConfig config) {
        if (initialized.get()) {
            return CompletableFuture.completedFuture(null);
        }
        
        this.config = config;
        
        return CompletableFuture.runAsync(() -> {
            try {
                // 创建线程池
                this.scheduler = Executors.newScheduledThreadPool(2, 
                    r -> new Thread(r, "MCP-Scheduler"));
                this.asyncExecutor = Executors.newScheduledThreadPool(4, 
                    r -> new Thread(r, "MCP-Async"));
                
                // 如果MCP功能未启用，仅初始化基础组件
                if (!config.isEnabled()) {
                    initialized.set(true);
                    return;
                }
                
                // 初始化所有启用的客户端
                List<CompletableFuture<Void>> initFutures = new ArrayList<>();
                for (MCPServerConfig serverConfig : config.getEnabledServers()) {
                    initFutures.add(createAndInitializeClient(serverConfig));
                }
                
                // 等待所有客户端初始化完成
                CompletableFuture.allOf(initFutures.toArray(new CompletableFuture[0])).join();
                
                // 启动健康检查定时任务
                startHealthCheckTask();
                
                initialized.set(true);
                running.set(true);
                
            } catch (Exception e) {
                throw new RuntimeException(new MCPException(MCPErrorType.CONFIGURATION_ERROR, "初始化MCP客户端管理器失败", e));
            }
        });
    }
    
    /**
     * 启动管理器
     */
    public synchronized CompletableFuture<Void> start() {
        if (!initialized.get()) {
            throw new IllegalStateException("管理器未初始化");
        }
        
        if (running.get()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            try {
                // 连接所有已配置的客户端
                List<CompletableFuture<Void>> connectFutures = new ArrayList<>();
                for (MCPClient client : clients.values()) {
                    connectFutures.add(client.connect());
                }
                
                // 等待所有连接完成
                CompletableFuture.allOf(connectFutures.toArray(new CompletableFuture[0])).join();
                
                running.set(true);
                
            } catch (Exception e) {
                throw new RuntimeException(new MCPException(MCPErrorType.CONFIGURATION_ERROR, "启动MCP客户端管理器失败", e));
            }
        });
    }
    
    /**
     * 停止管理器
     */
    public synchronized CompletableFuture<Void> stop() {
        if (!running.get()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            try {
                running.set(false);
                
                // 断开所有客户端连接
                List<CompletableFuture<Void>> disconnectFutures = new ArrayList<>();
                for (MCPClient client : clients.values()) {
                    disconnectFutures.add(client.disconnect());
                }
                
                // 等待所有断开连接完成
                CompletableFuture.allOf(disconnectFutures.toArray(new CompletableFuture[0])).join();
                
                // 关闭线程池
                shutdownExecutor(scheduler, "Scheduler");
                shutdownExecutor(asyncExecutor, "AsyncExecutor");
                
            } catch (Exception e) {
                throw new RuntimeException(new MCPException(MCPErrorType.CONFIGURATION_ERROR, "停止MCP客户端管理器失败", e));
            }
        });
    }
    
    /**
     * 创建并初始化客户端
     */
    private CompletableFuture<Void> createAndInitializeClient(MCPServerConfig serverConfig) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 创建客户端状态
                MCPClientStatus status = new MCPClientStatus(serverConfig.getName());
                clientStatuses.put(serverConfig.getName(), status);
                
                // 创建客户端实例
                MCPClient client = clientFactory.createClient(serverConfig, config);
                clients.put(serverConfig.getName(), client);
                
                // 设置客户端状态监听器
                client.setStatusListener(newStatus -> {
                    clientStatuses.put(serverConfig.getName(), newStatus);
                });
                
            } catch (Exception e) {
                MCPClientStatus errorStatus = new MCPClientStatus(serverConfig.getName());
                errorStatus.markError("创建客户端失败: " + e.getMessage());
                clientStatuses.put(serverConfig.getName(), errorStatus);
                throw new RuntimeException(MCPException.connectionFailed(serverConfig.getName(), "创建客户端失败", e));
            }
        }, asyncExecutor);
    }
    
    /**
     * 获取客户端
     */
    public MCPClient getClient(String serverName) {
        return clients.get(serverName);
    }
    
    /**
     * 获取所有客户端名称
     */
    public List<String> getClientNames() {
        return new ArrayList<>(clients.keySet());
    }
    
    /**
     * 获取所有客户端状态
     */
    public Map<String, MCPClientStatus> getAllClientStatuses() {
        return Collections.unmodifiableMap(clientStatuses);
    }
    
    /**
     * 获取客户端状态
     */
    public MCPClientStatus getClientStatus(String serverName) {
        return clientStatuses.get(serverName);
    }
    
    /**
     * 重新连接指定客户端
     */
    public CompletableFuture<Void> reconnectClient(String serverName) {
        MCPClient client = clients.get(serverName);
        if (client == null) {
            return CompletableFuture.failedFuture(
                MCPException.connectionFailed(serverName, "客户端不存在"));
        }
        
        return client.reconnect();
    }
    
    /**
     * 重新连接所有客户端
     */
    public CompletableFuture<Void> reconnectAllClients() {
        List<CompletableFuture<Void>> reconnectFutures = new ArrayList<>();
        for (MCPClient client : clients.values()) {
            reconnectFutures.add(client.reconnect());
        }
        
        return CompletableFuture.allOf(reconnectFutures.toArray(new CompletableFuture[0]));
    }
    
    /**
     * 重新加载配置
     */
    public synchronized CompletableFuture<Void> reloadConfig(MCPConfig newConfig) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 如果新配置禁用了MCP功能
                if (!newConfig.isEnabled()) {
                    // 停止所有客户端
                    stop().join();
                    this.config = newConfig;
                    return;
                }
                
                this.config = newConfig;
                
                // 更新现有客户端或创建新客户端
                Map<String, MCPServerConfig> newServers = new ConcurrentHashMap<>();
                for (MCPServerConfig serverConfig : newConfig.getEnabledServers()) {
                    newServers.put(serverConfig.getName(), serverConfig);
                }
                
                // 移除不再存在的客户端
                List<String> toRemove = new ArrayList<>();
                for (String serverName : clients.keySet()) {
                    if (!newServers.containsKey(serverName)) {
                        toRemove.add(serverName);
                    }
                }
                
                for (String serverName : toRemove) {
                    removeClient(serverName);
                }
                
                // 添加或更新客户端
                List<CompletableFuture<Void>> updateFutures = new ArrayList<>();
                for (MCPServerConfig serverConfig : newServers.values()) {
                    if (clients.containsKey(serverConfig.getName())) {
                        // 更新现有客户端配置
                        updateFutures.add(updateClientConfig(serverConfig));
                    } else {
                        // 创建新客户端
                        updateFutures.add(createAndInitializeClient(serverConfig));
                    }
                }
                
                // 等待所有更新完成
                CompletableFuture.allOf(updateFutures.toArray(new CompletableFuture[0])).join();
                
            } catch (Exception e) {
                throw new RuntimeException(new MCPException(MCPErrorType.CONFIGURATION_ERROR, "重新加载配置失败", e));
            }
        }, asyncExecutor);
    }
    
    /**
     * 更新客户端配置
     */
    private CompletableFuture<Void> updateClientConfig(MCPServerConfig serverConfig) {
        return CompletableFuture.runAsync(() -> {
            MCPClient client = clients.get(serverConfig.getName());
            if (client != null) {
                client.updateConfig(serverConfig);
            }
        }, asyncExecutor);
    }
    
    /**
     * 移除客户端
     */
    private void removeClient(String serverName) {
        MCPClient client = clients.remove(serverName);
        if (client != null) {
            client.disconnect().join();
        }
        clientStatuses.remove(serverName);
    }
    
    /**
     * 启动健康检查任务
     */
    private void startHealthCheckTask() {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                performHealthCheck();
            } catch (Exception e) {
                // 记录错误但不停止健康检查
                System.err.println("健康检查失败: " + e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS); // 每30秒执行一次
    }
    
    /**
     * 执行健康检查
     */
    private void performHealthCheck() {
        for (Map.Entry<String, MCPClient> entry : clients.entrySet()) {
            String serverName = entry.getKey();
            MCPClient client = entry.getValue();
            
            try {
                // 检查客户端健康状态
                boolean isHealthy = client.isHealthy();
                MCPClientStatus status = clientStatuses.get(serverName);
                
                if (!isHealthy && status != null && status.isConnected()) {
                    // 客户端不健康但状态显示连接，可能需要重连
                    asyncExecutor.execute(() -> {
                        try {
                            client.reconnect().join();
                        } catch (Exception e) {
                            System.err.println("自动重连失败: " + serverName + " - " + e.getMessage());
                        }
                    });
                }
                
            } catch (Exception e) {
                System.err.println("健康检查客户端失败: " + serverName + " - " + e.getMessage());
            }
        }
    }
    
    /**
     * 关闭线程池
     */
    private void shutdownExecutor(ScheduledExecutorService executor, String name) {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 检查管理器是否已初始化
     */
    public boolean isInitialized() {
        return initialized.get();
    }
    
    /**
     * 检查管理器是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * 获取配置
     */
    public MCPConfig getConfig() {
        return config;
    }
    
    /**
     * 获取连接的客户端数量
     */
    public int getConnectedClientCount() {
        return (int) clientStatuses.values().stream()
                .filter(MCPClientStatus::isConnected)
                .count();
    }
    
    /**
     * 获取总客户端数量
     */
    public int getTotalClientCount() {
        return clients.size();
    }
    
    /**
     * 生成状态报告
     */
    public String generateStatusReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== MCP客户端管理器状态报告 ===\n");
        report.append("管理器状态: ").append(running.get() ? "运行中" : "已停止").append("\n");
        report.append("总客户端数: ").append(getTotalClientCount()).append("\n");
        report.append("已连接客户端数: ").append(getConnectedClientCount()).append("\n");
        report.append("生成时间: ").append(LocalDateTime.now()).append("\n\n");
        
        report.append("=== 客户端详细状态 ===\n");
        for (Map.Entry<String, MCPClientStatus> entry : clientStatuses.entrySet()) {
            String name = entry.getKey();
            MCPClientStatus status = entry.getValue();
            
            report.append("客户端: ").append(name).append("\n");
            report.append("  状态: ").append(status.getStatusDisplay()).append("\n");
            report.append("  工具数: ").append(status.getToolCount()).append("\n");
            report.append("  资源数: ").append(status.getResourceCount()).append("\n");
            report.append("  提示词数: ").append(status.getPromptCount()).append("\n");
            report.append("  成功率: ").append(String.format("%.1f%%", status.getSuccessRate())).append("\n");
            if (status.isConnected()) {
                report.append("  连接时长: ").append(status.getConnectionDurationSeconds()).append("秒\n");
            }
            report.append("\n");
        }
        
        return report.toString();
    }
}