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
            // 避免重复添加 /sse 路径
            String sseUrl = baseUrl.endsWith("/sse") ? baseUrl : baseUrl + "/sse";
            
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
     */
    private void performHandshake() throws Exception {
        // 对于SSE，初始化通过HTTP POST请求发送
        String baseUrl = serverConfig.getUrl();
        // 修复：应该是 /messages 而不是 /message
        String messageUrl = baseUrl.contains("/sse") ? 
            baseUrl.replace("/sse", "/messages") : baseUrl + "/messages";
        
        System.out.println("[MCP DEBUG] 开始握手流程");
        System.out.println("[MCP DEBUG] 基础 URL: " + baseUrl);
        System.out.println("[MCP DEBUG] 消息 URL: " + messageUrl);
        
        URL initUrl = new URL(messageUrl);
        HttpURLConnection initConnection = (HttpURLConnection) initUrl.openConnection();
        
        try {
            initConnection.setRequestMethod("POST");
            initConnection.setRequestProperty("Content-Type", "application/json");
            initConnection.setDoOutput(true);
            
            // 初始化请求消息
            String initRequest = "{\n" +
                "  \"jsonrpc\": \"2.0\",\n" +
                "  \"method\": \"initialize\",\n" +
                "  \"params\": {\n" +
                "    \"protocolVersion\": \"2024-11-05\",\n" +
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
            System.out.println("[MCP DEBUG] 发送初始化请求: " + initRequest);
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
            } else {
                // 读取成功响应
                try {
                    java.io.InputStream inputStream = initConnection.getInputStream();
                    if (inputStream != null) {
                        String response = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream, StandardCharsets.UTF_8))
                            .lines().collect(java.util.stream.Collectors.joining("\n"));
                        System.out.println("[MCP DEBUG] 初始化成功响应: " + response);
                    }
                } catch (Exception e) {
                    System.out.println("[MCP DEBUG] 无法读取初始化成功响应: " + e.getMessage());
                }
            }
            
            // 发送initialized通知
            sendInitializedNotification();
            
        } finally {
            initConnection.disconnect();
        }
    }
    
    /**
     * 发送initialized通知
     */
    private void sendInitializedNotification() throws Exception {
        String baseUrl = serverConfig.getUrl();
        // 修复：应该是 /messages 而不是 /message
        String messageUrl = baseUrl.contains("/sse") ? 
            baseUrl.replace("/sse", "/messages") : baseUrl + "/messages";
        
        System.out.println("[MCP DEBUG] 发送初始化完成通知");
        System.out.println("[MCP DEBUG] 通知 URL: " + messageUrl);
        URL notifyUrl = new URL(messageUrl);
        HttpURLConnection notifyConnection = (HttpURLConnection) notifyUrl.openConnection();
        
        try {
            notifyConnection.setRequestMethod("POST");
            notifyConnection.setRequestProperty("Content-Type", "application/json");
            notifyConnection.setDoOutput(true);
            
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
            
            if (responseCode != 200 && responseCode != 204) {
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
    
    @Override
    public void shutdown() {
        cleanup();
        super.shutdown();
    }
}