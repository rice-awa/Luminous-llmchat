package com.riceawa.mcp.service;

import com.riceawa.mcp.config.MCPConfig;
import io.modelcontextprotocol.spec.McpSchema.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * MCP 服务接口
 * 提供高级的 MCP 功能封装
 */
public interface MCPService {
    
    /**
     * 初始化 MCP 服务
     * @param config MCP 配置
     * @return 初始化结果
     */
    CompletableFuture<Void> initialize(MCPConfig config);
    
    /**
     * 关闭 MCP 服务
     */
    void shutdown();
    
    /**
     * 检查服务是否已初始化
     * @return 初始化状态
     */
    boolean isInitialized();
    
    /**
     * 检查服务是否启用
     * @return 启用状态
     */
    boolean isEnabled();
    
    /**
     * 获取所有可用的工具
     * @return 按服务器分组的工具列表
     */
    CompletableFuture<Map<String, List<Tool>>> getAllTools();
    
    /**
     * 调用指定工具
     * @param toolName 工具名称
     * @param arguments 参数
     * @return 调用结果
     */
    CompletableFuture<CallToolResult> callTool(String toolName, Map<String, Object> arguments);
    
    /**
     * 调用指定服务器上的工具
     * @param serverName 服务器名称
     * @param toolName 工具名称
     * @param arguments 参数
     * @return 调用结果
     */
    CompletableFuture<CallToolResult> callTool(String serverName, String toolName, Map<String, Object> arguments);
    
    /**
     * 获取所有可用的资源
     * @return 按服务器分组的资源列表
     */
    CompletableFuture<Map<String, List<Resource>>> getAllResources();
    
    /**
     * 读取资源
     * @param serverName 服务器名称
     * @param uri 资源URI
     * @return 资源内容
     */
    CompletableFuture<ReadResourceResult> readResource(String serverName, String uri);
    
    /**
     * 获取所有可用的提示
     * @return 按服务器分组的提示列表
     */
    CompletableFuture<Map<String, List<Prompt>>> getAllPrompts();
    
    /**
     * 获取提示
     * @param serverName 服务器名称
     * @param name 提示名称
     * @param arguments 参数
     * @return 提示结果
     */
    CompletableFuture<GetPromptResult> getPrompt(String serverName, String name, Map<String, Object> arguments);
    
    /**
     * 获取服务器状态
     * @return 服务器状态映射
     */
    Map<String, Boolean> getServerStatus();
    
    /**
     * 获取已连接的服务器数量
     * @return 连接数量
     */
    int getConnectedServerCount();
    
    /**
     * 重新加载配置
     * @param config 新配置
     * @return 重新加载结果
     */
    CompletableFuture<Void> reload(MCPConfig config);
}