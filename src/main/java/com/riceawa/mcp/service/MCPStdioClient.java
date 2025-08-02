package com.riceawa.mcp.service;

import com.riceawa.mcp.config.MCPConfig;
import com.riceawa.mcp.config.MCPServerConfig;
import com.riceawa.mcp.exception.MCPException;
import com.riceawa.mcp.exception.MCPException.MCPErrorType;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * STDIO传输层的MCP客户端实现
 * 通过标准输入输出与MCP服务器进程通信
 */
public class MCPStdioClient extends MCPBaseClient {
    
    // 外部进程
    private Process process;
    
    // 输入输出流
    private BufferedReader reader;
    private BufferedWriter writer;
    
    public MCPStdioClient(MCPServerConfig serverConfig, MCPConfig globalConfig) {
        super(serverConfig, globalConfig);
        
        if (!serverConfig.isStdioType()) {
            throw new IllegalArgumentException("服务器配置不是STDIO类型: " + serverConfig.getName());
        }
    }
    
    @Override
    protected void doConnect() throws Exception {
        // 构建命令行
        List<String> command = new ArrayList<>();
        command.add(serverConfig.getCommand());
        command.addAll(serverConfig.getArgs());
        
        // 构建进程
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        
        // 设置环境变量
        if (!serverConfig.getEnv().isEmpty()) {
            processBuilder.environment().putAll(serverConfig.getEnv());
        }
        
        // 重定向错误流到标准输出
        processBuilder.redirectErrorStream(true);
        
        try {
            // 启动进程
            process = processBuilder.start();
            
            // 创建输入输出流
            reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(
                process.getOutputStream(), StandardCharsets.UTF_8));
            
            // 等待进程启动
            Thread.sleep(1000);
            
            // 检查进程是否正常启动
            if (!process.isAlive()) {
                throw MCPException.connectionFailed(serverConfig.getName(), "MCP服务器进程启动失败");
            }
            
            // 发送初始化握手
            performHandshake();
            
        } catch (Exception e) {
            // 清理资源
            cleanup();
            throw e;
        }
    }
    
    @Override
    protected void doDisconnect() throws Exception {
        cleanup();
    }
    
    @Override
    protected boolean doPing() throws Exception {
        if (process == null || !process.isAlive()) {
            return false;
        }
        
        try {
            // 发送ping请求
            sendMessage("{\"jsonrpc\":\"2.0\",\"method\":\"ping\",\"id\":\"ping-" + System.currentTimeMillis() + "\"}");
            
            // 等待响应（简化实现）
            Thread.sleep(100);
            
            return process.isAlive();
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 执行MCP协议握手
     */
    private void performHandshake() throws Exception {
        // 发送初始化请求
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
        
        sendMessage(initRequest);
        
        // 等待初始化响应（简化实现）
        String response = readMessage();
        if (response == null || response.contains("\"error\"")) {
            throw MCPException.protocolError(serverConfig.getName(), "MCP初始化失败: " + response);
        }
        
        // 发送initialized通知
        String initializedNotification = "{\n" +
            "  \"jsonrpc\": \"2.0\",\n" +
            "  \"method\": \"initialized\"\n" +
            "}";
        
        sendMessage(initializedNotification);
    }
    
    /**
     * 发送消息到MCP服务器
     */
    private void sendMessage(String message) throws IOException {
        if (writer == null) {
            throw new IOException("输出流未初始化");
        }
        
        writer.write(message);
        writer.newLine();
        writer.flush();
    }
    
    /**
     * 从MCP服务器读取消息
     */
    private String readMessage() throws IOException {
        if (reader == null) {
            throw new IOException("输入流未初始化");
        }
        
        return reader.readLine();
    }
    
    /**
     * 清理资源
     */
    private void cleanup() {
        // 关闭输入输出流
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                // 忽略关闭异常
            }
            reader = null;
        }
        
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                // 忽略关闭异常
            }
            writer = null;
        }
        
        // 终止进程
        if (process != null) {
            if (process.isAlive()) {
                // 尝试优雅关闭
                process.destroy();
                
                try {
                    // 等待进程结束
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        // 强制终止
                        process.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    process.destroyForcibly();
                    Thread.currentThread().interrupt();
                }
            }
            process = null;
        }
    }
    
    @Override
    public void shutdown() {
        cleanup();
        super.shutdown();
    }
}