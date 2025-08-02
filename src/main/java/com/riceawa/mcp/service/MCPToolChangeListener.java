package com.riceawa.mcp.service;

import com.riceawa.mcp.model.MCPTool;

import java.util.List;

/**
 * MCP工具变化监听器接口
 * 用于监听MCP服务器的工具列表变化并作出相应处理
 */
public interface MCPToolChangeListener {
    
    /**
     * 工具列表发生变化时的回调
     * @param clientName 客户端名称
     * @param oldTools 变化前的工具列表
     * @param newTools 变化后的工具列表
     */
    void onToolsChanged(String clientName, List<MCPTool> oldTools, List<MCPTool> newTools);
    
    /**
     * 新工具被添加时的回调
     * @param clientName 客户端名称
     * @param addedTools 新添加的工具列表
     */
    default void onToolsAdded(String clientName, List<MCPTool> addedTools) {
        // 默认实现为空，子类可以选择性重写
    }
    
    /**
     * 工具被移除时的回调
     * @param clientName 客户端名称
     * @param removedTools 被移除的工具列表
     */
    default void onToolsRemoved(String clientName, List<MCPTool> removedTools) {
        // 默认实现为空，子类可以选择性重写
    }
    
    /**
     * 工具被更新时的回调
     * @param clientName 客户端名称
     * @param updatedTools 被更新的工具列表
     */
    default void onToolsUpdated(String clientName, List<MCPTool> updatedTools) {
        // 默认实现为空，子类可以选择性重写
    }
    
    /**
     * 客户端连接状态变化时的回调
     * @param clientName 客户端名称
     * @param connected 是否已连接
     */
    default void onClientConnectionChanged(String clientName, boolean connected) {
        // 默认实现为空，子类可以选择性重写
    }
    
    /**
     * 工具列表刷新完成时的回调
     * @param clientName 客户端名称
     * @param success 刷新是否成功
     * @param errorMessage 错误信息（如果有）
     */
    default void onToolsRefreshCompleted(String clientName, boolean success, String errorMessage) {
        // 默认实现为空，子类可以选择性重写
    }
}