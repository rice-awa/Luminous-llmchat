package com.riceawa.mcp.service;

import com.google.gson.JsonObject;
import com.riceawa.mcp.exception.MCPException;
import com.riceawa.mcp.model.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * MCP服务层核心接口
 * 提供统一的MCP服务访问方法，包括工具调用、资源访问和提示词执行
 */
public interface MCPService {
    
    // ==================== 工具相关方法 ====================
    
    /**
     * 获取指定客户端的工具列表
     * @param clientName 客户端名称，null表示获取所有客户端的工具
     * @return 工具列表
     */
    CompletableFuture<List<MCPTool>> listTools(String clientName);
    
    /**
     * 获取所有可用的工具列表
     * @return 所有工具的列表
     */
    CompletableFuture<List<MCPTool>> listAllTools();
    
    /**
     * 根据工具名称获取工具信息
     * @param toolName 工具名称
     * @return 工具信息，如果未找到则返回null
     */
    CompletableFuture<MCPTool> getTool(String toolName);
    
    /**
     * 调用指定的工具
     * @param toolName 工具名称
     * @param arguments 工具参数
     * @return 工具执行结果
     */
    CompletableFuture<MCPToolResult> callTool(String toolName, JsonObject arguments);
    
    /**
     * 调用指定的工具（带超时控制）
     * @param toolName 工具名称
     * @param arguments 工具参数
     * @param timeout 超时时间
     * @return 工具执行结果
     */
    CompletableFuture<MCPToolResult> callTool(String toolName, JsonObject arguments, Duration timeout);
    
    // ==================== 资源相关方法 ====================
    
    /**
     * 获取指定客户端的资源列表
     * @param clientName 客户端名称，null表示获取所有客户端的资源
     * @return 资源列表
     */
    CompletableFuture<List<MCPResource>> listResources(String clientName);
    
    /**
     * 获取所有可用的资源列表
     * @return 所有资源的列表
     */
    CompletableFuture<List<MCPResource>> listAllResources();
    
    /**
     * 读取指定资源的内容
     * @param resourceUri 资源URI
     * @return 资源内容
     */
    CompletableFuture<MCPResourceContent> readResource(String resourceUri);
    
    /**
     * 读取指定资源的内容（带超时控制）
     * @param resourceUri 资源URI
     * @param timeout 超时时间
     * @return 资源内容
     */
    CompletableFuture<MCPResourceContent> readResource(String resourceUri, Duration timeout);
    
    // ==================== 提示词相关方法 ====================
    
    /**
     * 获取指定客户端的提示词列表
     * @param clientName 客户端名称，null表示获取所有客户端的提示词
     * @return 提示词列表
     */
    CompletableFuture<List<MCPPrompt>> listPrompts(String clientName);
    
    /**
     * 获取所有可用的提示词列表
     * @return 所有提示词的列表
     */
    CompletableFuture<List<MCPPrompt>> listAllPrompts();
    
    /**
     * 根据提示词名称获取提示词信息
     * @param promptName 提示词名称
     * @return 提示词信息，如果未找到则返回null
     */
    CompletableFuture<MCPPrompt> getPrompt(String promptName);
    
    /**
     * 执行指定的提示词
     * @param promptName 提示词名称
     * @param arguments 提示词参数
     * @return 提示词执行结果
     */
    CompletableFuture<MCPPromptResult> getPrompt(String promptName, Map<String, Object> arguments);
    
    /**
     * 执行指定的提示词（带超时控制）
     * @param promptName 提示词名称
     * @param arguments 提示词参数
     * @param timeout 超时时间
     * @return 提示词执行结果
     */
    CompletableFuture<MCPPromptResult> getPrompt(String promptName, Map<String, Object> arguments, Duration timeout);
    
    // ==================== 状态和管理方法 ====================
    
    /**
     * 检查服务是否可用
     * @return true如果服务可用
     */
    boolean isAvailable();
    
    /**
     * 获取所有已连接客户端的状态
     * @return 客户端状态映射（客户端名称 -> 状态）
     */
    Map<String, MCPClientStatus> getClientStatuses();
    
    /**
     * 获取指定客户端的状态
     * @param clientName 客户端名称
     * @return 客户端状态，如果客户端不存在则返回null
     */
    MCPClientStatus getClientStatus(String clientName);
    
    /**
     * 刷新指定客户端的工具列表
     * @param clientName 客户端名称
     * @return 刷新操作的结果
     */
    CompletableFuture<Void> refreshTools(String clientName);
    
    /**
     * 刷新所有客户端的工具列表
     * @return 刷新操作的结果
     */
    CompletableFuture<Void> refreshAllTools();
    
    /**
     * 测试与指定客户端的连接
     * @param clientName 客户端名称
     * @return 连接测试结果
     */
    CompletableFuture<Boolean> testConnection(String clientName);
    
    /**
     * 重新连接指定的客户端
     * @param clientName 客户端名称
     * @return 重连操作的结果
     */
    CompletableFuture<Void> reconnectClient(String clientName);
    
    /**
     * 关闭服务并释放资源
     */
    void shutdown();

    /**
     * 获取健康管理器
     * @return 健康管理器实例
     */
    MCPHealthManager getHealthManager();
}