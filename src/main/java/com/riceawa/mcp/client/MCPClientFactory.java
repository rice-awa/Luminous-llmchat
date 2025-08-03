package com.riceawa.mcp.client;

import com.riceawa.mcp.config.MCPServerConfig;
import com.riceawa.llm.logging.LogManager;

/**
 * MCP 客户端工厂
 * 根据配置创建对应类型的 MCP 客户端
 */
public class MCPClientFactory {
    private static final LogManager logger = LogManager.getInstance();

    /**
     * 根据服务器配置创建 MCP 客户端
     * 
     * @param config 服务器配置
     * @return MCP 客户端实例
     * @throws IllegalArgumentException 如果配置无效或类型不支持
     */
    public static MCPClient createClient(MCPServerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("服务器配置不能为空");
        }

        if (!config.isValid()) {
            throw new IllegalArgumentException("服务器配置无效: " + config.getName());
        }

        String type = config.getType();
        if (type == null || type.trim().isEmpty()) {
            type = "stdio"; // 默认类型
        }

        logger.info("创建 MCP 客户端: {} (类型: {})", config.getName(), type);

        switch (type.toLowerCase()) {
            case "stdio":
                return new MCPStdioClient(config);
                
            case "sse":
                return new MCPSseClient(config);
                
            default:
                throw new IllegalArgumentException("不支持的 MCP 客户端类型: " + type);
        }
    }

    /**
     * 检查客户端类型是否支持
     * 
     * @param type 客户端类型
     * @return 是否支持
     */
    public static boolean isSupportedType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return true; // 默认支持 stdio
        }
        
        String lowerType = type.toLowerCase();
        return "stdio".equals(lowerType) || "sse".equals(lowerType);
    }

    /**
     * 获取支持的客户端类型列表
     * 
     * @return 支持的类型数组
     */
    public static String[] getSupportedTypes() {
        return new String[]{"stdio", "sse"};
    }
}