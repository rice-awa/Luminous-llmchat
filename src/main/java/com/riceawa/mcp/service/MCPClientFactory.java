package com.riceawa.mcp.service;

import com.riceawa.mcp.config.MCPConfig;
import com.riceawa.mcp.config.MCPServerConfig;
import com.riceawa.mcp.exception.MCPException;
import com.riceawa.mcp.exception.MCPException.MCPErrorType;

/**
 * MCP客户端工厂
 * 根据服务器配置创建相应类型的MCP客户端
 */
public class MCPClientFactory {
    
    /**
     * 创建MCP客户端
     */
    public MCPClient createClient(MCPServerConfig serverConfig, MCPConfig globalConfig) throws MCPException {
        if (serverConfig == null) {
            throw new IllegalArgumentException("服务器配置不能为空");
        }
        
        if (!serverConfig.isValid()) {
            throw new RuntimeException(MCPException.configurationError("无效的服务器配置: " + serverConfig.getName()));
        }
        
        switch (serverConfig.getType().toLowerCase()) {
            case "stdio":
                return new MCPStdioClient(serverConfig, globalConfig);
            case "sse":
                return new MCPSseClient(serverConfig, globalConfig);
            default:
                throw new RuntimeException(MCPException.configurationError("不支持的传输类型: " + serverConfig.getType()));
        }
    }
}