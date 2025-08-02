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
            URL url = new URL(serverConfig.getUrl() + "/sse");
            sseConnection = (HttpURLConnection) url.openConnection();
            
            // 设置SSE请求头
            sseConnection.setRequestMethod("GET");
            sseConnection.setRequestProperty("Accept", "text/event-stream");
            sseConnection.setRequestProperty("Cache-Control", "no-cache");
            sseConnection.setConnectTimeout(globalConfig.getConnectionTimeoutMs());
            sseConnection.setReadTimeout(globalConfig.getRequestTimeoutMs());
            
            // 建立连接
            sseConnection.connect();
            
            // 检查响应状态
            int responseCode = sseConnection.getResponseCode();
            if (responseCode != 200) {
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
            URL pingUrl = new URL(serverConfig.getUrl() + "/ping");
            HttpURLConnection pingConnection = (HttpURLConnection) pingUrl.openConnection();
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
        URL initUrl = new URL(serverConfig.getUrl() + "/message");
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
            try (OutputStream os = initConnection.getOutputStream()) {
                os.write(initRequest.getBytes(StandardCharsets.UTF_8));
            }
            
            // 检查响应
            int responseCode = initConnection.getResponseCode();
            if (responseCode != 200) {
                throw MCPException.protocolError(serverConfig.getName(), "初始化请求失败，状态码: " + responseCode);
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
        URL notifyUrl = new URL(serverConfig.getUrl() + "/message");
        HttpURLConnection notifyConnection = (HttpURLConnection) notifyUrl.openConnection();
        
        try {
            notifyConnection.setRequestMethod("POST");
            notifyConnection.setRequestProperty("Content-Type", "application/json");
            notifyConnection.setDoOutput(true);
            
            String notification = "{\n" +
                "  \"jsonrpc\": \"2.0\",\n" +
                "  \"method\": \"initialized\"\n" +
                "}";
            
            try (OutputStream os = notifyConnection.getOutputStream()) {
                os.write(notification.getBytes(StandardCharsets.UTF_8));
            }
            
            int responseCode = notifyConnection.getResponseCode();
            if (responseCode != 200 && responseCode != 204) {
                throw MCPException.protocolError(serverConfig.getName(), "发送initialized通知失败，状态码: " + responseCode);
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