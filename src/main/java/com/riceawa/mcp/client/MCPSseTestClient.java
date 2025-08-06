package com.riceawa.mcp.client;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema.*;
import com.riceawa.llm.logging.LogManager;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * MCP SSE 测试客户端
 * 基于SDK示例创建的简单客户端，用于测试连接问题
 */
public class MCPSseTestClient {
    private static final LogManager logger = LogManager.getInstance();
    
    /**
     * 测试MCP SSE服务器连接
     * @param serverUrl 服务器URL
     * @return 连接结果
     */
    public static CompletableFuture<TestResult> testConnection(String serverUrl) {
        return CompletableFuture.supplyAsync(() -> {
            TestResult result = new TestResult();
            result.serverUrl = serverUrl;
            
            try {
                logger.info("开始测试MCP SSE连接: {}", serverUrl);
                result.startTime = System.currentTimeMillis();
                
                // 第一步：创建传输层
                logger.debug("步骤1: 创建SSE传输层");
                McpClientTransport transport = HttpClientSseClientTransport.builder(serverUrl)
                    .customizeClient(clientBuilder -> clientBuilder
                        .version(java.net.http.HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(20))
                        .followRedirects(java.net.http.HttpClient.Redirect.NORMAL))
                    .customizeRequest(requestBuilder -> requestBuilder
                        .header("User-Agent", "MCP-Test-Client/1.0")
                        .header("Accept", "text/event-stream")
                        .header("Cache-Control", "no-cache")
                        .header("Connection", "keep-alive"))
                    .build();
                
                result.transportCreated = true;
                logger.debug("步骤1完成: SSE传输层创建成功");
                
                // 第二步：创建客户端
                logger.debug("步骤2: 创建MCP客户端");
                var mcpClient = McpClient.sync(transport)
                    .requestTimeout(Duration.ofSeconds(25))
                    .capabilities(ClientCapabilities.builder()
                        .roots(true)
                        .sampling()
                        .build())
                    .loggingConsumer(notification -> {
                        logger.debug("MCP Server Log: {}", notification.data());
                    })
                    .build();
                
                result.clientCreated = true;
                logger.debug("步骤2完成: MCP客户端创建成功");
                
                // 第三步：初始化连接
                logger.debug("步骤3: 初始化MCP连接");
                InitializeResult initResult = mcpClient.initialize();
                
                if (initResult != null) {
                    result.initialized = true;
                    result.protocolVersion = initResult.protocolVersion();
                    result.serverInfo = initResult.serverInfo();
                    logger.info("步骤3完成: MCP连接初始化成功 - 协议版本: {}", initResult.protocolVersion());
                    
                    // 第四步：测试基本功能
                    logger.debug("步骤4: 测试工具列表");
                    try {
                        ListToolsResult toolsResult = mcpClient.listTools();
                        result.toolsListed = true;
                        result.toolCount = toolsResult.tools().size();
                        logger.info("步骤4完成: 获取到 {} 个工具", result.toolCount);
                    } catch (Exception e) {
                        logger.warn("步骤4警告: 无法获取工具列表 - {}", e.getMessage());
                        result.toolsError = e.getMessage();
                    }
                    
                    // 第五步：测试资源列表
                    logger.debug("步骤5: 测试资源列表");
                    try {
                        ListResourcesResult resourcesResult = mcpClient.listResources();
                        result.resourcesListed = true;
                        result.resourceCount = resourcesResult.resources().size();
                        logger.info("步骤5完成: 获取到 {} 个资源", result.resourceCount);
                    } catch (Exception e) {
                        logger.warn("步骤5警告: 无法获取资源列表 - {}", e.getMessage());
                        result.resourcesError = e.getMessage();
                    }
                    
                    // 第六步：设置日志级别
                    logger.debug("步骤6: 设置日志级别");
                    try {
                        mcpClient.setLoggingLevel(LoggingLevel.INFO);
                        result.loggingSet = true;
                        logger.debug("步骤6完成: 日志级别设置成功");
                    } catch (Exception e) {
                        logger.warn("步骤6警告: 无法设置日志级别 - {}", e.getMessage());
                        result.loggingError = e.getMessage();
                    }
                    
                    result.success = true;
                    
                    // 清理资源
                    try {
                        mcpClient.closeGracefully();
                        logger.debug("连接已正常关闭");
                    } catch (Exception e) {
                        logger.warn("关闭连接时出现警告: {}", e.getMessage());
                    }
                    
                } else {
                    result.error = "初始化返回null结果";
                    logger.error("步骤3失败: 初始化返回null结果");
                }
                
            } catch (Exception e) {
                result.error = e.getMessage();
                result.exception = e;
                logger.error("MCP SSE连接测试失败: {} - {}", serverUrl, e.getMessage());
                logger.debug("连接测试详细错误", e);
            } finally {
                result.endTime = System.currentTimeMillis();
                result.duration = result.endTime - result.startTime;
            }
            
            return result;
        });
    }
    
    /**
     * 测试结果类
     */
    public static class TestResult {
        public String serverUrl;
        public boolean success = false;
        public String error;
        public Exception exception;
        
        // 时间信息
        public long startTime;
        public long endTime;
        public long duration;
        
        // 步骤完成状态
        public boolean transportCreated = false;
        public boolean clientCreated = false;
        public boolean initialized = false;
        public boolean toolsListed = false;
        public boolean resourcesListed = false;
        public boolean loggingSet = false;
        
        // 服务器信息
        public String protocolVersion;
        public Object serverInfo;  // 使用Object避免类型导入问题
        public int toolCount = 0;
        public int resourceCount = 0;
        
        // 错误信息
        public String toolsError;
        public String resourcesError;
        public String loggingError;
        
        public String getStatusSummary() {
            if (success) {
                return String.format("连接成功 (耗时: %dms, 协议: %s, 工具: %d, 资源: %d)", 
                    duration, protocolVersion, toolCount, resourceCount);
            } else {
                StringBuilder sb = new StringBuilder("连接失败: ");
                if (!transportCreated) sb.append("传输层创建失败; ");
                else if (!clientCreated) sb.append("客户端创建失败; ");
                else if (!initialized) sb.append("初始化失败; ");
                
                if (error != null) {
                    sb.append("错误: ").append(error);
                }
                return sb.toString();
            }
        }
        
        public String getDetailedReport() {
            StringBuilder report = new StringBuilder();
            report.append("=== MCP SSE 连接测试报告 ===\n");
            report.append("服务器URL: ").append(serverUrl).append("\n");
            report.append("测试时间: ").append(duration).append("ms\n");
            report.append("最终结果: ").append(success ? "成功" : "失败").append("\n\n");
            
            report.append("步骤执行状态:\n");
            report.append("  1. 传输层创建: ").append(transportCreated ? "✓" : "✗").append("\n");
            report.append("  2. 客户端创建: ").append(clientCreated ? "✓" : "✗").append("\n");
            report.append("  3. 连接初始化: ").append(initialized ? "✓" : "✗").append("\n");
            report.append("  4. 工具列表获取: ").append(toolsListed ? "✓" : "✗").append("\n");
            report.append("  5. 资源列表获取: ").append(resourcesListed ? "✓" : "✗").append("\n");
            report.append("  6. 日志级别设置: ").append(loggingSet ? "✓" : "✗").append("\n\n");
            
            if (initialized) {
                report.append("服务器信息:\n");
                report.append("  协议版本: ").append(protocolVersion).append("\n");
                if (serverInfo != null) {
                    report.append("  服务器信息: ").append(serverInfo.toString()).append("\n");
                }
                report.append("  工具数量: ").append(toolCount).append("\n");
                report.append("  资源数量: ").append(resourceCount).append("\n\n");
            }
            
            if (!success && error != null) {
                report.append("错误信息: ").append(error).append("\n");
            }
            
            if (toolsError != null) {
                report.append("工具列表错误: ").append(toolsError).append("\n");
            }
            
            if (resourcesError != null) {
                report.append("资源列表错误: ").append(resourcesError).append("\n");
            }
            
            if (loggingError != null) {
                report.append("日志设置错误: ").append(loggingError).append("\n");
            }
            
            return report.toString();
        }
    }
}