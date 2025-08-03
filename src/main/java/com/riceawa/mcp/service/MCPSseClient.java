package com.riceawa.mcp.service;

import com.riceawa.mcp.config.MCPConfig;
import com.riceawa.mcp.config.MCPServerConfig;
import com.riceawa.mcp.exception.MCPException;
import com.riceawa.mcp.exception.MCPErrorType;
import com.riceawa.mcp.model.MCPClientStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE (Server-Sent Events) 传输层的MCP客户端实现
 * 通过HTTP SSE与MCP服务器通信
 */
public class MCPSseClient extends MCPBaseClient {
    
    // SSE连接
    private HttpURLConnection sseConnection;
    private BufferedReader sseReader;
    
    // 控制SSE读取循环
    private final AtomicBoolean reading = new AtomicBoolean(false);
    
    // 会话管理
    private String sessionId = null;
    
    public MCPSseClient(MCPServerConfig serverConfig, MCPConfig globalConfig) {
        super(serverConfig, globalConfig);
        
        if (!serverConfig.isSseType()) {
            throw new IllegalArgumentException("服务器配置不是SSE类型: " + serverConfig.getName());
        }
    }
    
    @Override
    protected void doConnect() throws Exception {
        try {
            // 创建SSE连接
            String baseUrl = serverConfig.getUrl();
            // 根据新MCP协议，SSE端点就是基础URL（不再需要/sse后缀）
            String sseUrl = baseUrl;
            
            // 添加详细调试日志
            System.out.println("[MCP DEBUG] 开始连接 SSE 端点");
            System.out.println("[MCP DEBUG] 原始 URL: " + baseUrl);
            System.out.println("[MCP DEBUG] SSE URL: " + sseUrl);
            
            URL url = new URL(sseUrl);
            sseConnection = (HttpURLConnection) url.openConnection();
            
            // 设置SSE请求头
            sseConnection.setRequestMethod("GET");
            sseConnection.setRequestProperty("Accept", "text/event-stream");
            sseConnection.setRequestProperty("Cache-Control", "no-cache");
            sseConnection.setRequestProperty("MCP-Protocol-Version", "2025-06-18");
            sseConnection.setConnectTimeout(globalConfig.getConnectionTimeoutMs());
            sseConnection.setReadTimeout(globalConfig.getRequestTimeoutMs());
            
            // 建立连接
            System.out.println("[MCP DEBUG] 正在建立 SSE 连接...");
            sseConnection.connect();
            
            // 检查响应状态
            int responseCode = sseConnection.getResponseCode();
            System.out.println("[MCP DEBUG] SSE 连接响应状态: " + responseCode);
            
            if (responseCode != 200) {
                System.out.println("[MCP DEBUG] SSE 连接失败，状态码: " + responseCode);
                // 尝试读取错误响应
                try {
                    java.io.InputStream errorStream = sseConnection.getErrorStream();
                    if (errorStream != null) {
                        String errorResponse = new java.io.BufferedReader(new java.io.InputStreamReader(errorStream, StandardCharsets.UTF_8))
                            .lines().collect(java.util.stream.Collectors.joining("\n"));
                        System.out.println("[MCP DEBUG] 错误响应内容: " + errorResponse);
                    }
                } catch (Exception e) {
                    System.out.println("[MCP DEBUG] 无法读取错误响应: " + e.getMessage());
                }
                throw MCPException.connectionFailed(serverConfig.getName(), "SSE连接失败，状态码: " + responseCode);
            }
            
            // 创建读取器
            sseReader = new BufferedReader(new InputStreamReader(
                sseConnection.getInputStream(), StandardCharsets.UTF_8));
            
            // 启动SSE事件读取循环
            startSseEventLoop();
            
            // 发送初始化握手
            performHandshake();
            
        } catch (Exception e) {
            cleanup();
            throw e;
        }
    }
    
    @Override
    protected void doDisconnect() throws Exception {
        reading.set(false);
        cleanup();
    }
    
    @Override
    protected boolean doPing() throws Exception {
        try {
            // 发送HTTP ping请求
            String baseUrl = serverConfig.getUrl();
            // 构建ping URL，根据基础URL是否已包含路径来决定
            String pingUrl = baseUrl.contains("/sse") ? 
                baseUrl.replace("/sse", "/ping") : baseUrl + "/ping";
            System.out.println("[MCP DEBUG] Ping URL: " + pingUrl);
            HttpURLConnection pingConnection = (HttpURLConnection) new URL(pingUrl).openConnection();
            pingConnection.setRequestMethod("GET");
            pingConnection.setConnectTimeout(5000);
            pingConnection.setReadTimeout(5000);
            
            int responseCode = pingConnection.getResponseCode();
            pingConnection.disconnect();
            
            return responseCode == 200;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 启动SSE事件读取循环
     */
    private void startSseEventLoop() {
        reading.set(true);
        
        CompletableFuture.runAsync(() -> {
            try {
                String line;
                while (reading.get() && (line = sseReader.readLine()) != null) {
                    processSseEvent(line);
                }
            } catch (IOException e) {
                if (reading.get()) {
                    // 只有在仍在读取时才报告错误
                    updateStatus(MCPClientStatus.ConnectionStatus.ERROR, 
                        "SSE读取错误: " + e.getMessage());
                }
            }
        }, executor);
    }
    
    /**
     * 处理SSE事件
     */
    private void processSseEvent(String eventLine) {
        if (eventLine.isEmpty()) {
            return;
        }
        
        // 解析SSE事件格式
        if (eventLine.startsWith("data: ")) {
            String data = eventLine.substring(6);
            handleSseData(data);
        } else if (eventLine.startsWith("event: ")) {
            String eventType = eventLine.substring(7);
            handleSseEventType(eventType);
        }
        // 忽略其他SSE字段（id:, retry:等）
    }
    
    /**
     * 处理SSE数据
     */
    private void handleSseData(String data) {
        try {
            // 这里应该解析JSON-RPC消息并处理
            // 简化实现：仅记录收到的数据
            status.recordRequest(true, 0);
        } catch (Exception e) {
            status.recordRequest(false, 0);
        }
    }
    
    /**
     * 处理SSE事件类型
     */
    private void handleSseEventType(String eventType) {
        // 处理特定的事件类型
        switch (eventType) {
            case "message":
                // JSON-RPC消息事件
                break;
            case "error":
                // 错误事件
                updateStatus(MCPClientStatus.ConnectionStatus.ERROR, "收到服务器错误事件");
                break;
            default:
                // 未知事件类型，忽略
                break;
        }
    }
    
    /**
     * 执行MCP协议握手
     * 根据MCP 2025-06-18协议，初始化请求应该直接发送到MCP端点
     */
    private void performHandshake() throws Exception {
        String baseUrl = serverConfig.getUrl();
        // 根据新协议，初始化请求应该发送到MCP端点本身，而不是/messages
        String mcpEndpoint = baseUrl;
        
        System.out.println("[MCP DEBUG] 开始握手流程");
        System.out.println("[MCP DEBUG] MCP端点 URL: " + mcpEndpoint);
        
        URL initUrl = new URL(mcpEndpoint);
        HttpURLConnection initConnection = (HttpURLConnection) initUrl.openConnection();
        
        try {
            initConnection.setRequestMethod("POST");
            initConnection.setRequestProperty("Content-Type", "application/json");
            initConnection.setRequestProperty("Accept", "application/json, text/event-stream");
            initConnection.setRequestProperty("MCP-Protocol-Version", "2025-06-18");
            initConnection.setDoOutput(true);
            initConnection.setConnectTimeout(globalConfig.getConnectionTimeoutMs());
            initConnection.setReadTimeout(globalConfig.getRequestTimeoutMs());
            
            // 初始化请求消息，使用最新协议版本
            String initRequest = "{\n" +
                "  \"jsonrpc\": \"2.0\",\n" +
                "  \"method\": \"initialize\",\n" +
                "  \"params\": {\n" +
                "    \"protocolVersion\": \"2025-06-18\",\n" +
                "    \"capabilities\": {\n" +
                "      \"tools\": {},\n" +
                "      \"resources\": {},\n" +
                "      \"prompts\": {}\n" +
                "    },\n" +
                "    \"clientInfo\": {\n" +
                "      \"name\": \"Luminous-LLMChat\",\n" +
                "      \"version\": \"1.0.0\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"id\": \"init-" + System.currentTimeMillis() + "\"\n" +
                "}";
            
            // 发送请求
            System.out.println("[MCP DEBUG] 发送初始化请求到MCP端点: " + initRequest);
            try (OutputStream os = initConnection.getOutputStream()) {
                os.write(initRequest.getBytes(StandardCharsets.UTF_8));
            }
            
            // 检查响应
            int responseCode = initConnection.getResponseCode();
            System.out.println("[MCP DEBUG] 初始化请求响应状态: " + responseCode);
            
            if (responseCode != 200) {
                // 尝试读取错误响应
                try {
                    java.io.InputStream errorStream = initConnection.getErrorStream();
                    if (errorStream != null) {
                        String errorResponse = new java.io.BufferedReader(new java.io.InputStreamReader(errorStream, StandardCharsets.UTF_8))
                            .lines().collect(java.util.stream.Collectors.joining("\n"));
                        System.out.println("[MCP DEBUG] 初始化错误响应: " + errorResponse);
                    }
                } catch (Exception e) {
                    System.out.println("[MCP DEBUG] 无法读取初始化错误响应: " + e.getMessage());
                }
                throw MCPException.protocolError(serverConfig.getName(), "初始化请求失败，状态码: " + responseCode);
            }
            
            // 检查响应的Content-Type
            String contentType = initConnection.getContentType();
            System.out.println("[MCP DEBUG] 响应Content-Type: " + contentType);
            
            if (contentType != null && contentType.startsWith("text/event-stream")) {
                // 服务器返回SSE流，处理初始化响应和后续事件
                System.out.println("[MCP DEBUG] 服务器返回SSE流，开始处理事件");
                
                // 检查是否有会话ID
                String mcpSessionId = initConnection.getHeaderField("Mcp-Session-Id");
                if (mcpSessionId != null) {
                    this.sessionId = mcpSessionId;
                    System.out.println("[MCP DEBUG] 收到会话ID: " + sessionId);
                }
                
                handleInitializationSSEStream(initConnection);
            } else if (contentType != null && contentType.startsWith("application/json")) {
                // 服务器返回JSON响应
                System.out.println("[MCP DEBUG] 服务器返回JSON响应");
                
                // 检查是否有会话ID
                String mcpSessionId = initConnection.getHeaderField("Mcp-Session-Id");
                if (mcpSessionId != null) {
                    this.sessionId = mcpSessionId;
                    System.out.println("[MCP DEBUG] 收到会话ID: " + sessionId);
                }
                
                String response = readResponseBody(initConnection);
                System.out.println("[MCP DEBUG] 初始化成功响应: " + response);
                
                // 发送initialized通知
                sendInitializedNotification();
            } else {
                throw MCPException.protocolError(serverConfig.getName(), "未知的响应Content-Type: " + contentType);
            }
            
        } finally {
            // 注意：如果是SSE流，不要立即关闭连接
            String contentType = initConnection.getContentType();
            if (contentType == null || !contentType.startsWith("text/event-stream")) {
                initConnection.disconnect();
            }
        }
    }
    
    /**
     * 处理初始化阶段的SSE流
     */
    private void handleInitializationSSEStream(HttpURLConnection connection) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
            connection.getInputStream(), StandardCharsets.UTF_8));
        
        String line;
        boolean initializeResponseReceived = false;
        
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("data: ")) {
                String data = line.substring(6);
                System.out.println("[MCP DEBUG] 收到SSE数据: " + data);
                
                // 解析JSON-RPC响应
                if (data.contains("\"method\":\"initialize\"") || data.contains("\"id\":\"init-")) {
                    // 这是初始化响应
                    System.out.println("[MCP DEBUG] 收到初始化响应: " + data);
                    initializeResponseReceived = true;
                    
                    // 发送initialized通知
                    sendInitializedNotification();
                    break;
                }
            }
        }
        
        if (!initializeResponseReceived) {
            throw MCPException.protocolError(serverConfig.getName(), "未收到初始化响应");
        }
    }
    
    /**
     * 读取HTTP响应体
     */
    private String readResponseBody(HttpURLConnection connection) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            connection.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(java.util.stream.Collectors.joining("\n"));
        }
    }
    
    /**
     * 发送initialized通知
     */
    private void sendInitializedNotification() throws Exception {
        String baseUrl = serverConfig.getUrl();
        // 根据新协议，通知也发送到MCP端点
        String mcpEndpoint = baseUrl;
        
        System.out.println("[MCP DEBUG] 发送初始化完成通知");
        System.out.println("[MCP DEBUG] 通知端点 URL: " + mcpEndpoint);
        URL notifyUrl = new URL(mcpEndpoint);
        HttpURLConnection notifyConnection = (HttpURLConnection) notifyUrl.openConnection();
        
        try {
            notifyConnection.setRequestMethod("POST");
            notifyConnection.setRequestProperty("Content-Type", "application/json");
            notifyConnection.setRequestProperty("MCP-Protocol-Version", "2025-06-18");
            
            // 如果有会话ID，则包含在请求中
            if (sessionId != null) {
                notifyConnection.setRequestProperty("Mcp-Session-Id", sessionId);
                System.out.println("[MCP DEBUG] 包含会话ID: " + sessionId);
            }
            
            notifyConnection.setDoOutput(true);
            notifyConnection.setConnectTimeout(globalConfig.getConnectionTimeoutMs());
            notifyConnection.setReadTimeout(globalConfig.getRequestTimeoutMs());
            
            String notification = "{\n" +
                "  \"jsonrpc\": \"2.0\",\n" +
                "  \"method\": \"initialized\"\n" +
                "}";
            
            System.out.println("[MCP DEBUG] 发送通知内容: " + notification);
            try (OutputStream os = notifyConnection.getOutputStream()) {
                os.write(notification.getBytes(StandardCharsets.UTF_8));
            }
            
            int responseCode = notifyConnection.getResponseCode();
            System.out.println("[MCP DEBUG] 通知响应状态: " + responseCode);
            
            if (responseCode != 200 && responseCode != 202 && responseCode != 204) {
                // 处理会话过期的情况
                if (responseCode == 404 && sessionId != null) {
                    System.out.println("[MCP DEBUG] 会话可能已过期，会话ID: " + sessionId);
                    sessionId = null; // 重置会话ID
                    throw MCPException.protocolError(serverConfig.getName(), "会话过期，需要重新初始化");
                }
                
                // 尝试读取错误响应
                try {
                    java.io.InputStream errorStream = notifyConnection.getErrorStream();
                    if (errorStream != null) {
                        String errorResponse = new java.io.BufferedReader(new java.io.InputStreamReader(errorStream, StandardCharsets.UTF_8))
                            .lines().collect(java.util.stream.Collectors.joining("\n"));
                        System.out.println("[MCP DEBUG] 通知错误响应: " + errorResponse);
                    }
                } catch (Exception e) {
                    System.out.println("[MCP DEBUG] 无法读取通知错误响应: " + e.getMessage());
                }
                throw MCPException.protocolError(serverConfig.getName(), "发送initialized通知失败，状态码: " + responseCode);
            } else {
                System.out.println("[MCP DEBUG] 初始化完成通知发送成功");
            }
            
        } finally {
            notifyConnection.disconnect();
        }
    }
    
    /**
     * 清理资源
     */
    private void cleanup() {
        reading.set(false);
        
        if (sseReader != null) {
            try {
                sseReader.close();
            } catch (IOException e) {
                // 忽略关闭异常
            }
            sseReader = null;
        }
        
        if (sseConnection != null) {
            sseConnection.disconnect();
            sseConnection = null;
        }
    }
    
    /**
     * 获取当前会话ID
     * @return 会话ID，如果没有会话则返回null
     */
    public String getSessionId() {
        return sessionId;
    }
    
    /**
     * 检查是否有活跃的会话
     * @return 如果有活跃会话返回true
     */
    public boolean hasActiveSession() {
        return sessionId != null;
    }
    
    @Override
    public void shutdown() {
        // 如果有活跃会话，尝试优雅地终止
        if (sessionId != null) {
            try {
                terminateSession();
            } catch (Exception e) {
                System.out.println("[MCP DEBUG] 会话终止失败: " + e.getMessage());
            }
        }
        
        cleanup();
        super.shutdown();
    }
    
    /**
     * 终止MCP会话
     */
    private void terminateSession() throws Exception {
        if (sessionId == null) {
            return;
        }
        
        String baseUrl = serverConfig.getUrl();
        String mcpEndpoint = baseUrl;
        
        System.out.println("[MCP DEBUG] 终止会话: " + sessionId);
        URL deleteUrl = new URL(mcpEndpoint);
        HttpURLConnection deleteConnection = (HttpURLConnection) deleteUrl.openConnection();
        
        try {
            deleteConnection.setRequestMethod("DELETE");
            deleteConnection.setRequestProperty("MCP-Protocol-Version", "2025-06-18");
            deleteConnection.setRequestProperty("Mcp-Session-Id", sessionId);
            deleteConnection.setConnectTimeout(5000); // 较短的超时时间
            deleteConnection.setReadTimeout(5000);
            
            int responseCode = deleteConnection.getResponseCode();
            System.out.println("[MCP DEBUG] 会话终止响应状态: " + responseCode);
            
            if (responseCode == 200) {
                System.out.println("[MCP DEBUG] 会话成功终止");
            } else if (responseCode == 405) {
                System.out.println("[MCP DEBUG] 服务器不支持客户端主动终止会话");
            } else {
                System.out.println("[MCP DEBUG] 会话终止返回状态: " + responseCode);
            }
            
        } finally {
            sessionId = null; // 清除会话ID
            deleteConnection.disconnect();
        }
    }
}