package com.riceawa.mcp.client;

import com.riceawa.mcp.config.MCPServerConfig;
import io.modelcontextprotocol.spec.McpSchema.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * MCP 客户端接口
 * 提供与 MCP 服务器交互的统一接口
 */
public interface MCPClient {
    
    /**
     * 连接到 MCP 服务器
     * @return 连接结果
     */
    CompletableFuture<Boolean> connect();
    
    /**
     * 断开与 MCP 服务器的连接
     */
    void disconnect();
    
    /**
     * 检查是否已连接
     * @return 连接状态
     */
    boolean isConnected();
    
    /**
     * 列出可用的工具
     * @return 工具列表
     */
    CompletableFuture<List<Tool>> listTools();
    
    /**
     * 调用工具
     * @param toolName 工具名称
     * @param arguments 参数
     * @return 调用结果
     */
    CompletableFuture<CallToolResult> callTool(String toolName, Map<String, Object> arguments);
    
    /**
     * 列出可用的资源
     * @return 资源列表
     */
    CompletableFuture<List<Resource>> listResources();
    
    /**
     * 读取资源
     * @param uri 资源URI
     * @return 资源内容
     */
    CompletableFuture<ReadResourceResult> readResource(String uri);
    
    /**
     * 列出可用的提示
     * @return 提示列表
     */
    CompletableFuture<List<Prompt>> listPrompts();
    
    /**
     * 获取提示
     * @param name 提示名称
     * @param arguments 参数
     * @return 提示结果
     */
    CompletableFuture<GetPromptResult> getPrompt(String name, Map<String, Object> arguments);
    
    /**
     * 获取服务器配置
     * @return 配置对象
     */
    MCPServerConfig getConfig();
    
    /**
     * 获取最后活动时间
     * @return 时间戳
     */
    long getLastActivityTime();
    
    /**
     * 获取服务器名称
     * @return 服务器名称
     */
    String getServerName();
    
    /**
     * 检查客户端健康状态
     * @return 健康状态
     */
    boolean isHealthy();
}