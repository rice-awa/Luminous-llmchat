package com.riceawa.mcp.client;

import com.riceawa.mcp.config.MCPServerConfig;
import io.modelcontextprotocol.spec.McpSchema.*;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import com.riceawa.llm.logging.LogManager;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MCP SSE 客户端实现
 */
public class MCPSseClient implements MCPClient {
    private static final LogManager logger = LogManager.getInstance();
    
    private final MCPServerConfig config;
    private McpSyncClient mcpClient;
    private volatile boolean connected = false;
    private volatile long lastActivityTime = 0;

    public MCPSseClient(MCPServerConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<Boolean> connect() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 创建 SSE 传输
                McpClientTransport transport = new HttpClientSseClientTransport(config.getUrl());
                
                // 创建客户端
                mcpClient = McpClient.sync(transport)
                    .requestTimeout(Duration.ofSeconds(10))
                    .capabilities(ClientCapabilities.builder()
                        .roots(true)
                        .sampling()
                        .build())
                    .loggingConsumer(notification -> {
                        logger.info("MCP Server Log [{}]: {}", config.getName(), notification.data());
                    })
                    .build();
                
                // 初始化连接
                InitializeResult result = mcpClient.initialize();
                
                if (result != null) {
                    connected = true;
                    lastActivityTime = System.currentTimeMillis();
                    
                    logger.info("成功连接到 MCP SSE 服务器: {} (版本: {})", 
                        config.getName(), result.protocolVersion());
                    
                    // 设置日志级别
                    mcpClient.setLoggingLevel(LoggingLevel.INFO);
                    
                    return true;
                } else {
                    logger.error("MCP SSE 服务器初始化失败: {}", config.getName());
                    return false;
                }
                
            } catch (Exception e) {
                logger.error("连接 MCP SSE 服务器失败: {} - {}", config.getName(), e.getMessage());
                connected = false;
                return false;
            }
        });
    }

    @Override
    public void disconnect() {
        if (mcpClient != null && connected) {
            try {
                mcpClient.closeGracefully();
                logger.info("已断开 MCP SSE 服务器连接: {}", config.getName());
            } catch (Exception e) {
                logger.error("断开 MCP SSE 服务器连接时出错: {} - {}", config.getName(), e.getMessage());
            } finally {
                connected = false;
                mcpClient = null;
            }
        }
    }

    @Override
    public boolean isConnected() {
        return connected && mcpClient != null;
    }

    @Override
    public CompletableFuture<List<Tool>> listTools() {
        return CompletableFuture.supplyAsync(() -> {
            if (!isConnected()) {
                throw new RuntimeException("MCP 客户端未连接");
            }
            
            try {
                updateLastActivity();
                ListToolsResult result = mcpClient.listTools();
                return result.tools();
            } catch (Exception e) {
                logger.error("列出工具失败: {} - {}", config.getName(), e.getMessage());
                throw new RuntimeException("列出工具失败", e);
            }
        });
    }

    @Override
    public CompletableFuture<CallToolResult> callTool(String toolName, Map<String, Object> arguments) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isConnected()) {
                throw new RuntimeException("MCP 客户端未连接");
            }
            
            // 检查工具权限
            if (!config.isToolAllowed(toolName)) {
                throw new RuntimeException("工具未被允许: " + toolName);
            }
            
            try {
                updateLastActivity();
                CallToolRequest request = new CallToolRequest(toolName, arguments);
                return mcpClient.callTool(request);
            } catch (Exception e) {
                logger.error("调用工具失败: {} / {} - {}", config.getName(), toolName, e.getMessage());
                throw new RuntimeException("调用工具失败", e);
            }
        });
    }

    @Override
    public CompletableFuture<List<Resource>> listResources() {
        return CompletableFuture.supplyAsync(() -> {
            if (!isConnected()) {
                throw new RuntimeException("MCP 客户端未连接");
            }
            
            try {
                updateLastActivity();
                ListResourcesResult result = mcpClient.listResources();
                return result.resources();
            } catch (Exception e) {
                logger.error("列出资源失败: {} - {}", config.getName(), e.getMessage());
                throw new RuntimeException("列出资源失败", e);
            }
        });
    }

    @Override
    public CompletableFuture<ReadResourceResult> readResource(String uri) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isConnected()) {
                throw new RuntimeException("MCP 客户端未连接");
            }
            
            // 检查资源权限
            if (!config.isResourceAllowed(uri)) {
                throw new RuntimeException("资源未被允许: " + uri);
            }
            
            try {
                updateLastActivity();
                ReadResourceRequest request = new ReadResourceRequest(uri);
                return mcpClient.readResource(request);
            } catch (Exception e) {
                logger.error("读取资源失败: {} / {} - {}", config.getName(), uri, e.getMessage());
                throw new RuntimeException("读取资源失败", e);
            }
        });
    }

    @Override
    public CompletableFuture<List<Prompt>> listPrompts() {
        return CompletableFuture.supplyAsync(() -> {
            if (!isConnected()) {
                throw new RuntimeException("MCP 客户端未连接");
            }
            
            try {
                updateLastActivity();
                ListPromptsResult result = mcpClient.listPrompts();
                return result.prompts();
            } catch (Exception e) {
                logger.error("列出提示失败: {} - {}", config.getName(), e.getMessage());
                throw new RuntimeException("列出提示失败", e);
            }
        });
    }

    @Override
    public CompletableFuture<GetPromptResult> getPrompt(String name, Map<String, Object> arguments) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isConnected()) {
                throw new RuntimeException("MCP 客户端未连接");
            }
            
            try {
                updateLastActivity();
                GetPromptRequest request = new GetPromptRequest(name, arguments);
                return mcpClient.getPrompt(request);
            } catch (Exception e) {
                logger.error("获取提示失败: {} / {} - {}", config.getName(), name, e.getMessage());
                throw new RuntimeException("获取提示失败", e);
            }
        });
    }

    @Override
    public MCPServerConfig getConfig() {
        return config;
    }

    @Override
    public long getLastActivityTime() {
        return lastActivityTime;
    }

    @Override
    public String getServerName() {
        return config.getName();
    }

    @Override
    public boolean isHealthy() {
        if (!isConnected()) {
            return false;
        }
        
        try {
            // 尝试列出工具来检查连接健康状况
            listTools().get(5, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            logger.debug("健康检查失败: {} - {}", config.getName(), e.getMessage());
            return false;
        }
    }

    private void updateLastActivity() {
        lastActivityTime = System.currentTimeMillis();
    }
}