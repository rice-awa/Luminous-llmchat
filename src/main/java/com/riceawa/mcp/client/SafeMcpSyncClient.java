package com.riceawa.mcp.client;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpError;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * 安全的MCP同步客户端包装器
 * 
 * 这个类包装了标准的McpSyncClient，为所有操作提供超时保护，
 * 避免无限期阻塞导致的线程耗尽问题。
 */
public class SafeMcpSyncClient implements AutoCloseable {
    
    private final McpSyncClient delegate;
    private final Duration defaultTimeout;
    
    /**
     * 创建一个安全的同步客户端包装器
     * 
     * @param delegate 原始的McpSyncClient
     * @param defaultTimeout 默认超时时间
     */
    public SafeMcpSyncClient(McpSyncClient delegate, Duration defaultTimeout) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate client cannot be null");
        }
        if (defaultTimeout == null || defaultTimeout.isNegative() || defaultTimeout.isZero()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        
        this.delegate = delegate;
        this.defaultTimeout = defaultTimeout;
    }
    
    /**
     * 安全地初始化连接
     * 
     * @return 初始化结果
     * @throws McpError 如果初始化失败或超时
     */
    public McpSchema.InitializeResult initialize() {
        return executeWithTimeout(() -> {
            try {
                return delegate.initialize();
            } catch (Exception e) {
                throw new McpError("Initialization failed: " + e.getMessage() + " - " + e.getClass().getSimpleName());
            }
        }, "Initialization");
    }
    
    /**
     * 安全地列出工具
     * 
     * @return 工具列表
     * @throws McpError 如果操作失败或超时
     */
    public McpSchema.ListToolsResult listTools() {
        return executeWithTimeout(() -> {
            try {
                return delegate.listTools();
            } catch (Exception e) {
                throw new McpError("List tools failed: " + e.getMessage() + " - " + e.getClass().getSimpleName());
            }
        }, "List tools");
    }
    
    /**
     * 安全地调用工具
     * 
     * @param callToolRequest 工具调用请求
     * @return 工具调用结果
     * @throws McpError 如果操作失败或超时
     */
    public McpSchema.CallToolResult callTool(McpSchema.CallToolRequest callToolRequest) {
        return executeWithTimeout(() -> {
            try {
                return delegate.callTool(callToolRequest);
            } catch (Exception e) {
                throw new McpError("Call tool failed: " + e.getMessage() + " - " + e.getClass().getSimpleName());
            }
        }, "Call tool: " + callToolRequest.name());
    }
    
    /**
     * 安全地列出资源
     * 
     * @return 资源列表
     * @throws McpError 如果操作失败或超时
     */
    public McpSchema.ListResourcesResult listResources() {
        return executeWithTimeout(() -> {
            try {
                return delegate.listResources();
            } catch (Exception e) {
                throw new McpError("List resources failed: " + e.getMessage() + " - " + e.getClass().getSimpleName());
            }
        }, "List resources");
    }
    
    /**
     * 安全地读取资源
     * 
     * @param readResourceRequest 读取资源请求
     * @return 资源内容
     * @throws McpError 如果操作失败或超时
     */
    public McpSchema.ReadResourceResult readResource(McpSchema.ReadResourceRequest readResourceRequest) {
        return executeWithTimeout(() -> {
            try {
                return delegate.readResource(readResourceRequest);
            } catch (Exception e) {
                throw new McpError("Read resource failed: " + e.getMessage() + " - " + e.getClass().getSimpleName());
            }
        }, "Read resource: " + readResourceRequest.uri());
    }
    
    /**
     * 安全地列出提示
     * 
     * @return 提示列表
     * @throws McpError 如果操作失败或超时
     */
    public McpSchema.ListPromptsResult listPrompts() {
        return executeWithTimeout(() -> {
            try {
                return delegate.listPrompts();
            } catch (Exception e) {
                throw new McpError("List prompts failed: " + e.getMessage() + " - " + e.getClass().getSimpleName());
            }
        }, "List prompts");
    }
    
    /**
     * 安全地获取提示
     * 
     * @param getPromptRequest 获取提示请求
     * @return 提示内容
     * @throws McpError 如果操作失败或超时
     */
    public McpSchema.GetPromptResult getPrompt(McpSchema.GetPromptRequest getPromptRequest) {
        return executeWithTimeout(() -> {
            try {
                return delegate.getPrompt(getPromptRequest);
            } catch (Exception e) {
                throw new McpError("Get prompt failed: " + e.getMessage() + " - " + e.getClass().getSimpleName());
            }
        }, "Get prompt: " + getPromptRequest.name());
    }
    
    /**
     * 安全地执行ping操作
     * 
     * @return ping结果
     * @throws McpError 如果操作失败或超时
     */
    public Object ping() {
        return executeWithTimeout(() -> {
            try {
                return delegate.ping();
            } catch (Exception e) {
                throw new McpError("Ping failed: " + e.getMessage() + " - " + e.getClass().getSimpleName());
            }
        }, "Ping");
    }
    
    /**
     * 安全地设置日志级别
     * 
     * @param loggingLevel 日志级别
     * @throws McpError 如果操作失败或超时
     */
    public void setLoggingLevel(McpSchema.LoggingLevel loggingLevel) {
        executeWithTimeout(() -> {
            try {
                delegate.setLoggingLevel(loggingLevel);
                return null;
            } catch (Exception e) {
                throw new McpError("Set logging level failed: " + e.getMessage() + " - " + e.getClass().getSimpleName());
            }
        }, "Set logging level");
    }
    
    /**
     * 带超时保护的通用执行方法
     * 
     * @param operation 要执行的操作
     * @param operationName 操作名称（用于日志）
     * @param <T> 返回类型
     * @return 操作结果
     * @throws McpError 如果操作失败或超时
     */
    private <T> T executeWithTimeout(Operation<T> operation, String operationName) {
        Thread operationThread = Thread.currentThread();
        
        try {
            // 创建一个带超时的执行任务
            java.util.concurrent.CompletableFuture<T> future = new java.util.concurrent.CompletableFuture<>();
            
            // 在单独线程中执行操作
            Thread executorThread = new Thread(() -> {
                try {
                    T result = operation.execute();
                    future.complete(result);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            
            executorThread.start();
            
            // 等待完成或超时
            return future.get(defaultTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            
        } catch (TimeoutException e) {
            // 超时处理
            throw new McpError(String.format("Operation '%s' timed out after %d ms", 
                operationName, defaultTimeout.toMillis()) + " - " + e.getClass().getSimpleName());
                
        } catch (java.util.concurrent.ExecutionException e) {
            // 执行异常处理
            Throwable cause = e.getCause();
            if (cause instanceof McpError) {
                throw (McpError) cause;
            } else {
                throw new McpError("Operation '" + operationName + "' failed: " + cause.getMessage() + " - " + cause.getClass().getSimpleName());
            }
            
        } catch (InterruptedException e) {
            // 中断处理
            Thread.currentThread().interrupt();
            throw new McpError("Operation '" + operationName + "' was interrupted - " + e.getClass().getSimpleName());
        }
    }
    
    /**
     * 获取当前初始化结果
     * 
     * @return 初始化结果
     */
    public McpSchema.InitializeResult getCurrentInitializationResult() {
        return delegate.getCurrentInitializationResult();
    }
    
    /**
     * 获取服务器能力
     * 
     * @return 服务器能力
     */
    public McpSchema.ServerCapabilities getServerCapabilities() {
        return delegate.getServerCapabilities();
    }
    
    /**
     * 获取服务器指令
     * 
     * @return 服务器指令
     */
    public String getServerInstructions() {
        return delegate.getServerInstructions();
    }
    
    /**
     * 获取服务器信息
     * 
     * @return 服务器信息
     */
    public McpSchema.Implementation getServerInfo() {
        return delegate.getServerInfo();
    }
    
    /**
     * 检查是否已初始化
     * 
     * @return 是否已初始化
     */
    public boolean isInitialized() {
        return delegate.isInitialized();
    }
    
    /**
     * 获取客户端能力
     * 
     * @return 客户端能力
     */
    public McpSchema.ClientCapabilities getClientCapabilities() {
        return delegate.getClientCapabilities();
    }
    
    /**
     * 获取客户端信息
     * 
     * @return 客户端信息
     */
    public McpSchema.Implementation getClientInfo() {
        return delegate.getClientInfo();
    }
    
    @Override
    public void close() {
        try {
            // 使用较短的超时时间来关闭连接
            executeWithTimeout(() -> {
                delegate.close();
                return null;
            }, "Close client");
        } catch (McpError e) {
            // 关闭失败时只记录警告，不抛出异常
            System.err.println("Warning: Failed to close client gracefully: " + e.getMessage());
        }
    }
    
    /**
     * 优雅关闭
     * 
     * @return 是否成功关闭
     */
    public boolean closeGracefully() {
        try {
            return executeWithTimeout(() -> {
                try {
                    return delegate.closeGracefully();
                } catch (Exception e) {
                    throw new RuntimeException("Graceful close failed", e);
                }
            }, "Close client gracefully");
        } catch (McpError e) {
            return false;
        }
    }
    
    /**
     * 操作接口
     * 
     * @param <T> 返回类型
     */
    @FunctionalInterface
    private interface Operation<T> {
        T execute() throws Exception;
    }
}