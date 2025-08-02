package com.riceawa.mcp.service;

import com.riceawa.mcp.config.MCPServerConfig;
import com.riceawa.mcp.model.MCPClientStatus;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * MCP客户端接口
 * 定义MCP客户端的基本操作和生命周期管理
 */
public interface MCPClient {
    
    /**
     * 连接到MCP服务器
     */
    CompletableFuture<Void> connect();
    
    /**
     * 断开与MCP服务器的连接
     */
    CompletableFuture<Void> disconnect();
    
    /**
     * 重新连接
     */
    CompletableFuture<Void> reconnect();
    
    /**
     * 检查客户端是否健康
     */
    boolean isHealthy();
    
    /**
     * 检查是否已连接
     */
    boolean isConnected();
    
    /**
     * 获取客户端状态
     */
    MCPClientStatus getStatus();
    
    /**
     * 设置状态监听器
     */
    void setStatusListener(Consumer<MCPClientStatus> listener);
    
    /**
     * 更新配置
     */
    void updateConfig(MCPServerConfig config);
    
    /**
     * 获取服务器名称
     */
    String getServerName();
    
    /**
     * 发送ping请求（健康检查）
     */
    CompletableFuture<Boolean> ping();
}