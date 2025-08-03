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
    @SuppressWarnings("removal") // HttpClientSseClientTransport构造函数已过时但仍可用
    public CompletableFuture<Boolean> connect() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("正在连接到 MCP SSE 服务器: {} (URL: {})", config.getName(), config.getUrl());
                
                // 创建 SSE 传输
                McpClientTransport transport = new HttpClientSseClientTransport(config.getUrl());
                
                // 创建客户端，增加更短的超时时间
                mcpClient = McpClient.sync(transport)
                    .requestTimeout(Duration.ofSeconds(30))
                    .capabilities(ClientCapabilities.builder()
                        .roots(true)
                        .sampling()
                        .build())
                    .loggingConsumer(notification -> {
                        logger.info("MCP Server Log [{}]: {}", config.getName(), notification.data());
                    })
                    .build();
                
                // 初始化连接，增加重试机制
                InitializeResult result = null;
                int maxRetries = 3;
                Exception lastException = null;
                
                for (int i = 0; i < maxRetries; i++) {
                    try {
                        logger.info("尝试初始化 MCP 连接 ({}): {}", i + 1, config.getName());
                        result = mcpClient.initialize();
                        if (result != null) {
                            break;
                        }
                    } catch (Exception e) {
                        lastException = e;
                        logger.warn("初始化尝试 {} 失败: {} - {}", i + 1, config.getName(), e.getMessage());
                        if (i < maxRetries - 1) {
                            try {
                                Thread.sleep(1000 * (i + 1)); // 递增延迟
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
                
                if (result != null) {
                    connected = true;
                    lastActivityTime = System.currentTimeMillis();
                    
                    logger.info("成功连接到 MCP SSE 服务器: {} (版本: {})", 
                        config.getName(), result.protocolVersion());
                    
                    // 设置日志级别
                    try {
                        mcpClient.setLoggingLevel(LoggingLevel.INFO);
                    } catch (Exception e) {
                        logger.warn("设置日志级别失败: {}", e.getMessage());
                    }
                    
                    return true;
                } else {
                    logger.error("MCP SSE 服务器初始化失败: {} - 最后一次错误: {}", 
                        config.getName(), lastException != null ? lastException.getMessage() : "未知错误");
                    return false;
                }
                
            } catch (Exception e) {
                logger.error("连接 MCP SSE 服务器失败: {} - {}", config.getName(), e.getMessage());
                logger.debug("连接失败详细信息", e);
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
            // 使用更短的超时时间来检查健康状态
            listTools().get(3, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            logger.debug("健康检查失败: {} - {}", config.getName(), e.getMessage());
            // 如果健康检查失败，标记为断开
            connected = false;
            return false;
        }
    }

    private void updateLastActivity() {
        lastActivityTime = System.currentTimeMillis();
    }
}