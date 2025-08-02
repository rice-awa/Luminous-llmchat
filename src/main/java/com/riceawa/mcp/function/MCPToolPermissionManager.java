package com.riceawa.mcp.function;

import com.riceawa.mcp.model.MCPTool;
import net.minecraft.entity.player.PlayerEntity;

/**
 * MCP工具权限管理器接口
 * 用于检查玩家是否有权限使用特定的MCP工具
 */
public interface MCPToolPermissionManager {
    
    /**
     * 检查玩家是否有权限使用指定的MCP工具
     * @param player 玩家
     * @param tool MCP工具
     * @return 是否有权限
     */
    boolean checkPermission(PlayerEntity player, MCPTool tool);
    
    /**
     * 为工具设置权限策略
     * @param toolName 工具名称
     * @param policy 权限策略
     */
    void setPermissionPolicy(String toolName, PermissionPolicy policy);
    
    /**
     * 获取工具的权限策略
     * @param toolName 工具名称
     * @return 权限策略
     */
    PermissionPolicy getPermissionPolicy(String toolName);
    
    /**
     * 权限策略枚举
     */
    enum PermissionPolicy {
        /** 允许所有玩家 */
        ALLOW_ALL,
        /** 仅允许OP */
        OP_ONLY,
        /** 基于自定义规则 */
        CUSTOM,
        /** 拒绝所有玩家 */
        DENY_ALL
    }
}