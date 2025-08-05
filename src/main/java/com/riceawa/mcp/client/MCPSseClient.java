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
import java.util.concurrent.TimeoutException;

/**
 * MCP SSE 客户端实现
 * 注意：当前使用SSE传输，未来会迁移到Streamable HTTP传输
 * 参考：https://modelcontextprotocol.io/specification/2025-03-26/basic/transports
 */
public class MCPSseClient implements MCPClient {
    private static final LogManager logger = LogManager.getInstance();
    
    private final MCPServerConfig config;
    private McpSyncClient rawMcpClient;
    private SafeMcpSyncClient mcpClient;
    private volatile boolean connected = false;
    private volatile long lastActivityTime = 0;

    public MCPSseClient(MCPServerConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<Boolean> connect() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("正在连接到 MCP SSE 服务器: {} (URL: {})", config.getName(), config.getUrl());
                
                // 首先验证服务器URL的可访问性
                if (!validateServerUrl()) {
                    logger.error("服务器URL验证失败，跳过连接: {}", config.getUrl());
                    return false;
                }
                
                // 创建 SSE 传输，使用正确的协议版本和端点
                String baseUrl = config.getUrl();
                String sseEndpoint = "/sse";
                
                // 如果URL已经包含/sse路径，则提取基础URL
                if (baseUrl.endsWith("/sse")) {
                    baseUrl = baseUrl.substring(0, baseUrl.length() - 4);
                } else if (baseUrl.contains("/sse")) {
                    int sseIndex = baseUrl.indexOf("/sse");
                    baseUrl = baseUrl.substring(0, sseIndex);
                }
                
                // 确保基础URL不以/结尾
                if (baseUrl.endsWith("/")) {
                    baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                }
                
                logger.info("MCP服务器基础URL: {}, SSE端点: {}", baseUrl, sseEndpoint);
                
                McpClientTransport transport = HttpClientSseClientTransport.builder(baseUrl)
                    .customizeClient(clientBuilder -> 
                        clientBuilder
                            .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                            .connectTimeout(java.time.Duration.ofSeconds(15))
                            .version(java.net.http.HttpClient.Version.HTTP_1_1))
                    .customizeRequest(requestBuilder -> 
                        requestBuilder
                            .header("MCP-Protocol-Version", "2024-11-05")
                            .header("Accept", "text/event-stream")
                            .header("Cache-Control", "no-cache")
                            .header("Connection", "keep-alive")
                            .timeout(java.time.Duration.ofSeconds(30)))
                    .sseEndpoint(sseEndpoint)
                    .build();
                
                // 创建客户端，使用更合理的超时配置以避免阻塞
                rawMcpClient = McpClient.sync(transport)
                    .requestTimeout(Duration.ofSeconds(45))
                    .initializationTimeout(Duration.ofSeconds(60))
                    .capabilities(ClientCapabilities.builder()
                        .roots(true)
                        .sampling()
                        .build())
                    .loggingConsumer(notification -> {
                        logger.info("MCP Server Log [{}]: {}", config.getName(), notification.data());
                    })
                    .build();
                
                // 使用安全的同步包装器，为所有操作提供超时保护
                mcpClient = new SafeMcpSyncClient(rawMcpClient, Duration.ofSeconds(30));
                
                // 初始化连接，增加超时保护和重试机制
                InitializeResult result = null;
                int maxRetries = 3;
                Exception lastException = null;
                
                for (int i = 0; i < maxRetries; i++) {
                    Thread initThread = null;
                    try {
                        logger.info("尝试初始化 MCP 连接 ({}/{}): {}", i + 1, maxRetries, config.getName());
                        
                        // 使用带超时的初始化，避免无限期阻塞
                        CompletableFuture<InitializeResult> future = new CompletableFuture<>();
                        
                        // 在单独线程中执行初始化
                        initThread = new Thread(() -> {
                            try {
                                InitializeResult initResult = mcpClient.initialize();
                                future.complete(initResult);
                            } catch (Exception e) {
                                future.completeExceptionally(e);
                            }
                        });
                        
                        initThread.start();
                        
                        // 等待完成或超时
                        result = future.get(30, TimeUnit.SECONDS);
                        
                        if (result != null) {
                            break;
                        }
                    } catch (TimeoutException e) {
                        lastException = e;
                        logger.warn("初始化超时 (尝试 {}/{}): {}", i + 1, maxRetries, e.getMessage());
                        
                        // 清理资源
                        if (initThread != null && initThread.isAlive()) {
                            initThread.interrupt();
                        }
                        
                        if (i < maxRetries - 1) {
                            try {
                                // 指数退避重试
                                long delay = 2000 * (long) Math.pow(2, i);
                                logger.info("等待 {} 毫秒后重试...", delay);
                                Thread.sleep(delay);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    } catch (Exception e) {
                        lastException = e;
                        String errorMsg = e.getMessage();
                        
                        // 获取完整的异常链信息
                        Throwable rootCause = e;
                        while (rootCause.getCause() != null) {
                            rootCause = rootCause.getCause();
                        }
                        String rootCauseMsg = rootCause.getMessage();
                        
                        // 检查特定的错误类型并提供更好的错误信息
                        if (errorMsg != null) {
                            if (errorMsg.contains("Invalid Content-Type header")) {
                                logger.error("MCP服务器拒绝请求 - Content-Type头错误: {}", config.getName());
                                logger.info("建议检查服务器是否正确实现了MCP SSE协议");
                                // 不重试配置错误
                                break;
                            } else if (errorMsg.contains("Invalid SSE response")) {
                                logger.error("MCP服务器返回无效的SSE响应: {}", config.getName());
                                logger.info("可能的原因:");
                                logger.info("1. 服务器端点不是SSE端点，而是HTML页面");
                                logger.info("2. 协议版本不匹配，当前使用2024-11-05版本");
                                logger.info("3. 服务器未正确实现MCP SSE协议");
                                logger.info("请检查服务器URL: {}", config.getUrl());
                                logger.info("实际响应: {}", rootCauseMsg);
                                // 不重试协议不匹配错误
                                break;
                            } else if (errorMsg.contains("400")) {
                                logger.error("MCP服务器返回HTTP 400错误: {}", config.getName());
                                logger.info("请检查服务器配置和API端点");
                                // 不重试客户端错误
                                break;
                            } else if (errorMsg.contains("Client failed to initialize by explicit API call")) {
                                logger.error("MCP客户端初始化失败: {}", config.getName());
                                logger.info("可能的原因:");
                                logger.info("1. 网络连接问题");
                                logger.info("2. 服务器未正确响应初始化请求");
                                logger.info("3. 协议握手失败");
                                logger.info("原始错误: {}", rootCauseMsg);
                                // 网络问题可以重试
                            } else if (errorMsg.contains("Connection refused")) {
                                logger.error("无法连接到MCP服务器: {}", config.getName());
                                logger.info("请检查服务器是否正在运行: {}", config.getUrl());
                                // 连接拒绝可以重试，但间隔要长一些
                                if (i < maxRetries - 1) {
                                    try {
                                        long delay = 5000 * (long) Math.pow(2, i);
                                        logger.info("连接被拒绝，等待 {} 毫秒后重试...", delay);
                                        Thread.sleep(delay);
                                    } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                        break;
                                    }
                                }
                            } else if (errorMsg.contains("timeout")) {
                                logger.error("连接超时: {}", config.getName());
                                logger.info("请检查网络连接和服务器响应时间");
                                // 超时可以重试
                            } else if (errorMsg.contains("Connection reset") || errorMsg.contains("Connection closed")) {
                                logger.error("连接被重置: {}", config.getName());
                                logger.info("网络连接不稳定，正在重试...");
                                // 连接重置可以重试
                            }
                        }
                        
                        logger.warn("初始化尝试 {} 失败: {} - {}", i + 1, config.getName(), errorMsg);
                        logger.debug("异常详情: ", e);
                        
                        if (i < maxRetries - 1) {
                            try {
                                // 指数退避重试，但增加随机性避免同步重试
                                long baseDelay = 2000 * (long) Math.pow(2, i);
                                long jitter = (long) (Math.random() * 1000);
                                long delay = baseDelay + jitter;
                                logger.info("等待 {} 毫秒后重试...", delay);
                                Thread.sleep(delay);
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
                    
                    // 设置日志级别（可选）
                    try {
                        mcpClient.setLoggingLevel(LoggingLevel.INFO);
                    } catch (Exception e) {
                        logger.warn("设置日志级别失败: {}", e.getMessage());
                    }
                    
                    return true;
                } else {
                    String errorMsg = lastException != null ? lastException.getMessage() : "未知错误";
                    logger.error("MCP SSE 服务器初始化失败: {} - 最后一次错误: {}", 
                        config.getName(), errorMsg);
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
                rawMcpClient = null;
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
            // 使用短超时时间来检查健康状态，避免阻塞
            listTools().get(3, TimeUnit.SECONDS);
            updateLastActivity();
            return true;
        } catch (Exception e) {
            logger.debug("健康检查失败: {} - {}", config.getName(), e.getMessage());
            // 如果健康检查失败，标记为断开状态
            connected = false;
            return false;
        }
    }

    private void updateLastActivity() {
        lastActivityTime = System.currentTimeMillis();
    }
    
    /**
     * 验证服务器URL的可访问性和SSE端点正确性
     */
    private boolean validateServerUrl() {
        try {
            java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .build();
                
            // 构建SSE端点URL
            String sseUrl = config.getUrl();
            if (!sseUrl.endsWith("/sse")) {
                if (sseUrl.endsWith("/")) {
                    sseUrl += "sse";
                } else {
                    sseUrl += "/sse";
                }
            }
            
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(sseUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .header("MCP-Protocol-Version", "2024-11-05")
                .GET()
                .build();
                
            java.net.http.HttpResponse<String> response = httpClient.send(request, 
                java.net.http.HttpResponse.BodyHandlers.ofString());
                
            logger.debug("服务器URL验证 - 状态码: {}, URL: {}", response.statusCode(), sseUrl);
            
            // 验证响应是否为有效的SSE流
            if (!validateSseResponse(response)) {
                return false;
            }
            
            // 接受 2xx 和 3xx 状态码
            if (response.statusCode() >= 200 && response.statusCode() < 400) {
                return true;
            } else {
                logger.warn("服务器返回状态码: {} - {}", response.statusCode(), sseUrl);
                return false;
            }
            
        } catch (Exception e) {
            logger.warn("无法验证服务器URL: {} - {}", config.getUrl(), e.getMessage());
            return false;
        }
    }
    
    /**
     * 验证服务器响应是否为有效的SSE流
     */
    private boolean validateSseResponse(java.net.http.HttpResponse<String> response) {
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        String body = response.body();
        
        // 验证Content-Type是否为SSE
        if (!contentType.contains("text/event-stream")) {
            logger.error("服务器返回了错误的Content-Type: {}, 期望: text/event-stream", contentType);
            
            // 如果是HTML响应，提供更详细的错误信息
            if (body.startsWith("<!doctype html>") || body.startsWith("<html>")) {
                logger.error("服务器返回了HTML页面而非SSE流，可能连接到了错误的端点");
                logger.info("请检查服务器URL是否正确，应该指向MCP SSE端点");
            }
            
            return false;
        }
        
        // 验证响应体是否包含SSE格式
        if (body != null && !body.isEmpty()) {
            // 检查是否包含SSE事件格式
            if (!body.contains("event:") && !body.contains("data:")) {
                logger.warn("服务器响应可能不是有效的SSE格式");
            }
        }
        
        return true;
    }
}