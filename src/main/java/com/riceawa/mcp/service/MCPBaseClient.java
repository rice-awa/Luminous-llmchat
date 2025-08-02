package com.riceawa.mcp.service;

import com.riceawa.mcp.config.MCPConfig;
import com.riceawa.mcp.config.MCPServerConfig;
import com.riceawa.mcp.exception.MCPException;
import com.riceawa.mcp.exception.MCPErrorType;
import com.riceawa.mcp.model.MCPClientStatus;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * MCP客户端抽象基类
 * 提供通用的客户端功能实现和生命周期管理
 */
public abstract class MCPBaseClient implements MCPClient {
    protected final MCPServerConfig serverConfig;
    protected final MCPConfig globalConfig;
    protected final MCPClientStatus status;
    
    // 连接状态
    protected final AtomicBoolean connected = new AtomicBoolean(false);
    protected final AtomicBoolean connecting = new AtomicBoolean(false);
    
    // 重连计数器
    protected final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    
    // 状态监听器
    protected Consumer<MCPClientStatus> statusListener;
    
    // 异步执行器
    protected ScheduledExecutorService executor;
    
    // 最后一次成功ping的时间
    protected volatile LocalDateTime lastPingTime;
    
    public MCPBaseClient(MCPServerConfig serverConfig, MCPConfig globalConfig) {
        this.serverConfig = serverConfig;
        this.globalConfig = globalConfig;
        this.status = new MCPClientStatus(serverConfig.getName());
        this.executor = Executors.newScheduledThreadPool(2, 
            r -> new Thread(r, "MCP-" + serverConfig.getName()));
    }
    
    @Override
    public CompletableFuture<Void> connect() {
        if (connected.get()) {
            return CompletableFuture.completedFuture(null);
        }
        
        if (connecting.get()) {
            return CompletableFuture.failedFuture(
                MCPException.connectionFailed(serverConfig.getName(), "客户端正在连接中"));
        }
        
        return CompletableFuture.runAsync(() -> {
            connecting.set(true);
            updateStatus(MCPClientStatus.ConnectionStatus.CONNECTING, null);
            
            try {
                // 调用子类的连接实现
                doConnect();
                
                connected.set(true);
                reconnectAttempts.set(0);
                lastPingTime = LocalDateTime.now();
                updateStatus(MCPClientStatus.ConnectionStatus.CONNECTED, null);
                
                // 启动定期ping任务
                startPingTask();
                
            } catch (Exception e) {
                connected.set(false);
                updateStatus(MCPClientStatus.ConnectionStatus.ERROR, 
                    "连接失败: " + e.getMessage());
                throw new RuntimeException(MCPException.connectionFailed(serverConfig.getName(), "连接MCP服务器失败", e));
            } finally {
                connecting.set(false);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Void> disconnect() {
        if (!connected.get()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            try {
                // 调用子类的断开连接实现
                doDisconnect();
                
                connected.set(false);
                updateStatus(MCPClientStatus.ConnectionStatus.DISCONNECTED, "主动断开连接");
                
            } catch (Exception e) {
                updateStatus(MCPClientStatus.ConnectionStatus.ERROR, 
                    "断开连接失败: " + e.getMessage());
                throw new RuntimeException(MCPException.connectionFailed(serverConfig.getName(), "断开MCP服务器连接失败", e));
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Void> reconnect() {
        return disconnect().thenCompose(v -> {
            // 等待一小段时间再重连
            return CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, executor);
        }).thenCompose(v -> connect());
    }
    
    @Override
    public boolean isHealthy() {
        if (!connected.get()) {
            return false;
        }
        
        // 检查最后一次ping是否在合理时间内
        if (lastPingTime != null) {
            LocalDateTime now = LocalDateTime.now();
            long secondsSinceLastPing = java.time.Duration.between(lastPingTime, now).getSeconds();
            return secondsSinceLastPing < 120; // 2分钟内有过成功的ping
        }
        
        return false;
    }
    
    @Override
    public boolean isConnected() {
        return connected.get();
    }
    
    @Override
    public MCPClientStatus getStatus() {
        return status;
    }
    
    @Override
    public void setStatusListener(Consumer<MCPClientStatus> listener) {
        this.statusListener = listener;
    }
    
    @Override
    public void updateConfig(MCPServerConfig config) {
        // 基类实现：记录配置更新，子类可以重写
        // 注意：配置更新可能需要重新连接
        if (!this.serverConfig.equals(config)) {
            // 配置有变化，可能需要重连
            reconnect();
        }
    }
    
    @Override
    public String getServerName() {
        return serverConfig.getName();
    }
    
    @Override
    public CompletableFuture<Boolean> ping() {
        if (!connected.get()) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean result = doPing();
                if (result) {
                    lastPingTime = LocalDateTime.now();
                }
                return result;
            } catch (Exception e) {
                return false;
            }
        }, executor);
    }
    
    /**
     * 更新状态并通知监听器
     */
    protected void updateStatus(MCPClientStatus.ConnectionStatus connectionStatus, String message) {
        status.setStatus(connectionStatus);
        if (message != null) {
            status.setErrorMessage(message);
        }
        
        // 通知状态监听器
        if (statusListener != null) {
            try {
                statusListener.accept(status);
            } catch (Exception e) {
                // 忽略监听器错误
            }
        }
    }
    
    /**
     * 启动定期ping任务
     */
    private void startPingTask() {
        executor.scheduleWithFixedDelay(() -> {
            if (connected.get()) {
                ping().whenComplete((result, throwable) -> {
                    if (throwable != null || !result) {
                        // Ping失败，可能需要重连
                        handlePingFailure();
                    }
                });
            }
        }, 30, 30, TimeUnit.SECONDS); // 每30秒ping一次
    }
    
    /**
     * 处理ping失败
     */
    private void handlePingFailure() {
        if (reconnectAttempts.get() < globalConfig.getMaxRetries()) {
            reconnectAttempts.incrementAndGet();
            updateStatus(MCPClientStatus.ConnectionStatus.RECONNECTING, 
                "Ping失败，尝试重连 (" + reconnectAttempts.get() + "/" + globalConfig.getMaxRetries() + ")");
            
            // 延迟重连
            executor.schedule(() -> {
                reconnect().whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        updateStatus(MCPClientStatus.ConnectionStatus.ERROR, 
                            "自动重连失败: " + throwable.getMessage());
                    }
                });
            }, 5, TimeUnit.SECONDS);
        } else {
            // 超过最大重试次数
            connected.set(false);
            updateStatus(MCPClientStatus.ConnectionStatus.ERROR, 
                "连接丢失，已达到最大重试次数");
        }
    }
    
    /**
     * 关闭客户端资源
     */
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    // 抽象方法：子类需要实现
    
    /**
     * 执行实际的连接操作
     */
    protected abstract void doConnect() throws Exception;
    
    /**
     * 执行实际的断开连接操作
     */
    protected abstract void doDisconnect() throws Exception;
    
    /**
     * 执行实际的ping操作
     */
    protected abstract boolean doPing() throws Exception;
}