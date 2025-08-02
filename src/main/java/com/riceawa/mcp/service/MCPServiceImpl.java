package com.riceawa.mcp.service;

import com.google.gson.JsonObject;
import com.riceawa.mcp.exception.MCPException;
import com.riceawa.mcp.exception.MCPErrorType;
import com.riceawa.mcp.model.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * MCP服务层实现类
 * 负责统一管理和协调多个MCP客户端的操作
 * 支持异步操作、超时控制、重试机制和统一错误处理
 */
public class MCPServiceImpl implements MCPService {
    
    private final MCPClientManager clientManager;
    
    // 工具变化通知器
    private final MCPToolChangeNotifier toolChangeNotifier;
    
    // 工具缓存：工具名称 -> 工具信息
    private final Map<String, MCPTool> toolCache = new ConcurrentHashMap<>();
    
    // 客户端工具映射：客户端名称 -> 工具列表
    private final Map<String, List<MCPTool>> clientToolsCache = new ConcurrentHashMap<>();
    
    // 资源缓存：客户端名称 -> 资源列表
    private final Map<String, List<MCPResource>> clientResourcesCache = new ConcurrentHashMap<>();
    
    // 提示词缓存：客户端名称 -> 提示词列表
    private final Map<String, List<MCPPrompt>> clientPromptsCache = new ConcurrentHashMap<>();
    
    // 默认超时时间
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    
    public MCPServiceImpl(MCPClientManager clientManager) {
        this.clientManager = clientManager;
        this.toolChangeNotifier = new MCPToolChangeNotifier();
        // 初始化时刷新所有缓存
        initializeCaches();
    }
    
    // ==================== 工具相关方法实现 ====================
    
    @Override
    public CompletableFuture<List<MCPTool>> listTools(String clientName) {
        if (clientName == null) {
            return listAllTools();
        }
        
        return MCPAsyncUtils.withTimeoutAndRetry(() -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    MCPClient client = clientManager.getClient(clientName);
                    if (client == null || !client.isConnected()) {
                        throw MCPException.clientNotFound(clientName);
                    }
                    
                    // 检查缓存
                    List<MCPTool> cachedTools = clientToolsCache.get(clientName);
                    if (cachedTools != null) {
                        return new ArrayList<>(cachedTools);
                    }
                    
                    // 从客户端获取工具列表
                    List<MCPTool> tools = fetchToolsFromClient(client);
                    
                    // 更新缓存并通知变化
                    updateToolsCache(clientName, tools);
                    
                    return tools;
                    
                } catch (Exception e) {
                    throw new RuntimeException(MCPException.operationFailed("获取工具列表失败", e));
                }
            });
        }, "listTools-" + clientName);
    }
    
    @Override
    public CompletableFuture<List<MCPTool>> listAllTools() {
        return MCPAsyncUtils.withTimeoutAndRetry(() -> {
            return CompletableFuture.supplyAsync(() -> {
                List<CompletableFuture<List<MCPTool>>> futures = new ArrayList<>();
                
                for (String clientName : clientManager.getConnectedClients()) {
                    futures.add(listTools(clientName).exceptionally(
                        MCPErrorHandler.createRecoveryFunction(new ArrayList<>(), clientName, "listTools")
                    ));
                }
                
                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .flatMap(List::stream)
                        .collect(Collectors.toList()))
                    .join();
            });
        }, "listAllTools");
    }
    
    @Override
    public CompletableFuture<MCPTool> getTool(String toolName) {
        return MCPAsyncUtils.withTimeoutAndRetry(() -> {
            return CompletableFuture.supplyAsync(() -> {
                // 首先检查缓存
                MCPTool cachedTool = toolCache.get(toolName);
                if (cachedTool != null) {
                    return cachedTool;
                }
                
                // 如果缓存中没有，尝试刷新并查找
                try {
                    refreshAllTools().get(30, TimeUnit.SECONDS);
                    return toolCache.get(toolName);
                } catch (Exception e) {
                    throw new RuntimeException(MCPException.operationFailed("获取工具信息失败", e));
                }
            });
        }, "getTool-" + toolName);
    }
    
    @Override
    public CompletableFuture<MCPToolResult> callTool(String toolName, JsonObject arguments) {
        return callTool(toolName, arguments, DEFAULT_TIMEOUT);
    }
    
    @Override
    public CompletableFuture<MCPToolResult> callTool(String toolName, JsonObject arguments, Duration timeout) {
        return MCPAsyncUtils.withTimeoutAndRetry(() -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    // 获取工具信息
                    MCPTool tool = getTool(toolName).get(10, TimeUnit.SECONDS);
                    if (tool == null) {
                        throw MCPException.toolNotFound(toolName);
                    }
                    
                    // 获取对应的客户端
                    MCPClient client = clientManager.getClient(tool.getClientName());
                    if (client == null || !client.isConnected()) {
                        throw MCPException.clientNotFound(tool.getClientName());
                    }
                    
                    // 调用工具
                    return callToolOnClient(client, toolName, arguments, timeout);
                    
                } catch (Exception e) {
                    if (e instanceof MCPException) {
                        throw new RuntimeException(e);
                    }
                    throw new RuntimeException(MCPException.operationFailed("调用工具失败: " + toolName, e));
                }
            });
        }, timeout, 3, Duration.ofSeconds(2), "callTool-" + toolName);
    }
    
    // ==================== 资源相关方法实现 ====================
    
    @Override
    public CompletableFuture<List<MCPResource>> listResources(String clientName) {
        if (clientName == null) {
            return listAllResources();
        }
        
        return MCPAsyncUtils.withTimeoutAndRetry(() -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    MCPClient client = clientManager.getClient(clientName);
                    if (client == null || !client.isConnected()) {
                        throw MCPException.clientNotFound(clientName);
                    }
                    
                    // 检查缓存
                    List<MCPResource> cachedResources = clientResourcesCache.get(clientName);
                    if (cachedResources != null) {
                        return new ArrayList<>(cachedResources);
                    }
                    
                    // 从客户端获取资源列表
                    List<MCPResource> resources = fetchResourcesFromClient(client);
                    
                    // 更新缓存
                    clientResourcesCache.put(clientName, resources);
                    
                    return resources;
                    
                } catch (Exception e) {
                    throw new RuntimeException(MCPException.operationFailed("获取资源列表失败", e));
                }
            });
        }, "listResources-" + clientName);
    }
    
    @Override
    public CompletableFuture<List<MCPResource>> listAllResources() {
        return MCPAsyncUtils.withTimeoutAndRetry(() -> {
            return CompletableFuture.supplyAsync(() -> {
                List<CompletableFuture<List<MCPResource>>> futures = new ArrayList<>();
                
                for (String clientName : clientManager.getConnectedClients()) {
                    futures.add(listResources(clientName).exceptionally(
                        MCPErrorHandler.createRecoveryFunction(new ArrayList<>(), clientName, "listResources")
                    ));
                }
                
                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .flatMap(List::stream)
                        .collect(Collectors.toList()))
                    .join();
            });
        }, "listAllResources");
    }
    
    @Override
    public CompletableFuture<MCPResourceContent> readResource(String resourceUri) {
        return readResource(resourceUri, DEFAULT_TIMEOUT);
    }
    
    @Override
    public CompletableFuture<MCPResourceContent> readResource(String resourceUri, Duration timeout) {
        return MCPAsyncUtils.withTimeoutAndRetry(() -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    // 找到拥有该资源的客户端
                    String clientName = findClientForResource(resourceUri);
                    if (clientName == null) {
                        throw MCPException.resourceNotFound(resourceUri);
                    }
                    
                    MCPClient client = clientManager.getClient(clientName);
                    if (client == null || !client.isConnected()) {
                        throw MCPException.clientNotFound(clientName);
                    }
                    
                    // 读取资源内容
                    return readResourceFromClient(client, resourceUri, timeout);
                    
                } catch (Exception e) {
                    if (e instanceof MCPException) {
                        throw new RuntimeException(e);
                    }
                    throw new RuntimeException(MCPException.operationFailed("读取资源失败: " + resourceUri, e));
                }
            });
        }, timeout, 2, Duration.ofSeconds(1), "readResource-" + resourceUri);
    }
    
    // ==================== 提示词相关方法实现 ====================
    
    @Override
    public CompletableFuture<List<MCPPrompt>> listPrompts(String clientName) {
        if (clientName == null) {
            return listAllPrompts();
        }
        
        return MCPAsyncUtils.withTimeoutAndRetry(() -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    MCPClient client = clientManager.getClient(clientName);
                    if (client == null || !client.isConnected()) {
                        throw MCPException.clientNotFound(clientName);
                    }
                    
                    // 检查缓存
                    List<MCPPrompt> cachedPrompts = clientPromptsCache.get(clientName);
                    if (cachedPrompts != null) {
                        return new ArrayList<>(cachedPrompts);
                    }
                    
                    // 从客户端获取提示词列表
                    List<MCPPrompt> prompts = fetchPromptsFromClient(client);
                    
                    // 更新缓存
                    clientPromptsCache.put(clientName, prompts);
                    
                    return prompts;
                    
                } catch (Exception e) {
                    throw new RuntimeException(MCPException.operationFailed("获取提示词列表失败", e));
                }
            });
        }, "listPrompts-" + clientName);
    }
    
    @Override
    public CompletableFuture<List<MCPPrompt>> listAllPrompts() {
        return MCPAsyncUtils.withTimeoutAndRetry(() -> {
            return CompletableFuture.supplyAsync(() -> {
                List<CompletableFuture<List<MCPPrompt>>> futures = new ArrayList<>();
                
                for (String clientName : clientManager.getConnectedClients()) {
                    futures.add(listPrompts(clientName).exceptionally(
                        MCPErrorHandler.createRecoveryFunction(new ArrayList<>(), clientName, "listPrompts")
                    ));
                }
                
                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .flatMap(List::stream)
                        .collect(Collectors.toList()))
                    .join();
            });
        }, "listAllPrompts");
    }
    
    @Override
    public CompletableFuture<MCPPrompt> getPrompt(String promptName) {
        return MCPAsyncUtils.withTimeoutAndRetry(() -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    // 搜索所有客户端的提示词
                    for (String clientName : clientManager.getConnectedClients()) {
                        List<MCPPrompt> prompts = listPrompts(clientName).get(10, TimeUnit.SECONDS);
                        for (MCPPrompt prompt : prompts) {
                            if (promptName.equals(prompt.getName())) {
                                return prompt;
                            }
                        }
                    }
                    return null;
                } catch (Exception e) {
                    throw new RuntimeException(MCPException.operationFailed("获取提示词信息失败", e));
                }
            });
        }, "getPrompt-" + promptName);
    }
    
    @Override
    public CompletableFuture<MCPPromptResult> getPrompt(String promptName, Map<String, Object> arguments) {
        return getPrompt(promptName, arguments, DEFAULT_TIMEOUT);
    }
    
    @Override
    public CompletableFuture<MCPPromptResult> getPrompt(String promptName, Map<String, Object> arguments, Duration timeout) {
        return MCPAsyncUtils.withTimeoutAndRetry(() -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    // 找到拥有该提示词的客户端
                    String clientName = findClientForPrompt(promptName);
                    if (clientName == null) {
                        throw MCPException.promptNotFound(promptName);
                    }
                    
                    MCPClient client = clientManager.getClient(clientName);
                    if (client == null || !client.isConnected()) {
                        throw MCPException.clientNotFound(clientName);
                    }
                    
                    // 执行提示词
                    return executePromptOnClient(client, promptName, arguments, timeout);
                    
                } catch (Exception e) {
                    if (e instanceof MCPException) {
                        throw new RuntimeException(e);
                    }
                    throw new RuntimeException(MCPException.operationFailed("执行提示词失败: " + promptName, e));
                }
            });
        }, timeout, 2, Duration.ofSeconds(1), "executePrompt-" + promptName);
    }
    
    // ==================== 状态和管理方法实现 ====================
    
    @Override
    public boolean isAvailable() {
        return clientManager.hasConnectedClients();
    }
    
    @Override
    public Map<String, MCPClientStatus> getClientStatuses() {
        return clientManager.getAllClientStatuses();
    }
    
    @Override
    public MCPClientStatus getClientStatus(String clientName) {
        MCPClient client = clientManager.getClient(clientName);
        return client != null ? client.getStatus() : null;
    }
    
    @Override
    public CompletableFuture<Void> refreshTools(String clientName) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 清除指定客户端的缓存
                clientToolsCache.remove(clientName);
                
                // 移除相关的工具缓存
                toolCache.entrySet().removeIf(entry -> {
                    MCPTool tool = entry.getValue();
                    return clientName.equals(tool.getClientName());
                });
                
                // 重新获取工具列表
                listTools(clientName).get(30, TimeUnit.SECONDS);
                
            } catch (Exception e) {
                throw new RuntimeException(MCPException.operationFailed("刷新工具列表失败", e));
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> refreshAllTools() {
        return CompletableFuture.runAsync(() -> {
            try {
                // 清除所有缓存
                toolCache.clear();
                clientToolsCache.clear();
                
                // 重新获取所有工具列表
                listAllTools().get(60, TimeUnit.SECONDS);
                
            } catch (Exception e) {
                throw new RuntimeException(MCPException.operationFailed("刷新所有工具列表失败", e));
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> testConnection(String clientName) {
        return CompletableFuture.supplyAsync(() -> {
            MCPClient client = clientManager.getClient(clientName);
            if (client == null) {
                return false;
            }
            
            try {
                return client.ping().get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> reconnectClient(String clientName) {
        return CompletableFuture.runAsync(() -> {
            try {
                MCPClient client = clientManager.getClient(clientName);
                if (client == null) {
                    throw MCPException.clientNotFound(clientName);
                }
                
                client.reconnect().get(30, TimeUnit.SECONDS);
                
                // 重连成功后刷新缓存
                refreshTools(clientName).get(10, TimeUnit.SECONDS);
                
            } catch (Exception e) {
                throw new RuntimeException(MCPException.operationFailed("重连客户端失败", e));
            }
        });
    }
    
    @Override
    public void shutdown() {
        // 关闭工具变化通知器
        toolChangeNotifier.shutdown();
        
        // 清除所有缓存
        toolCache.clear();
        clientToolsCache.clear();
        clientResourcesCache.clear();
        clientPromptsCache.clear();
    }
    
    // ==================== 工具变化监听器管理方法 ====================
    
    /**
     * 添加工具变化监听器
     */
    public void addToolChangeListener(MCPToolChangeListener listener) {
        toolChangeNotifier.addListener(listener);
    }
    
    /**
     * 移除工具变化监听器
     */
    public void removeToolChangeListener(MCPToolChangeListener listener) {
        toolChangeNotifier.removeListener(listener);
    }
    
    /**
     * 处理工具列表变化通知
     */
    public void handleToolsListChangedNotification(String clientName) {
        toolChangeNotifier.handleToolsListChangedNotification(clientName);
        
        // 异步刷新工具列表
        CompletableFuture.runAsync(() -> {
            try {
                refreshTools(clientName).get(30, TimeUnit.SECONDS);
                toolChangeNotifier.notifyToolsRefreshCompleted(clientName, true, null);
            } catch (Exception e) {
                String errorMessage = "刷新工具列表失败: " + e.getMessage();
                toolChangeNotifier.notifyToolsRefreshCompleted(clientName, false, errorMessage);
                System.err.println(errorMessage);
            }
        });
    }
    
    // ==================== 私有辅助方法 ====================
    
    /**
     * 初始化缓存
     */
    private void initializeCaches() {
        // 异步初始化缓存，避免阻塞启动过程
        CompletableFuture.runAsync(() -> {
            try {
                refreshAllTools().get(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                System.err.println("Failed to initialize MCP caches: " + e.getMessage());
            }
        });
    }
    
    /**
     * 从客户端获取工具列表
     */
    private List<MCPTool> fetchToolsFromClient(MCPClient client) {
        // 这里需要实际的MCP协议调用
        // 暂时返回空列表，后续在具体的客户端实现中完成
        return new ArrayList<>();
    }
    
    /**
     * 更新工具缓存并通知变化
     */
    private void updateToolsCache(String clientName, List<MCPTool> newTools) {
        // 获取旧的工具列表用于变化检测
        List<MCPTool> oldTools = clientToolsCache.get(clientName);
        if (oldTools == null) {
            oldTools = new ArrayList<>();
        }
        
        // 更新缓存
        clientToolsCache.put(clientName, new ArrayList<>(newTools));
        
        // 更新全局工具缓存
        // 先移除该客户端的旧工具
        toolCache.entrySet().removeIf(entry -> {
            MCPTool tool = entry.getValue();
            return clientName.equals(tool.getClientName());
        });
        
        // 添加新工具到全局缓存
        newTools.forEach(tool -> {
            tool.setClientName(clientName);
            toolCache.put(tool.getFullName(), tool);
        });
        
        // 通知工具变化
        toolChangeNotifier.handleToolsUpdate(clientName, newTools);
    }
    
    /**
     * 在客户端上调用工具
     */
    private MCPToolResult callToolOnClient(MCPClient client, String toolName, JsonObject arguments, Duration timeout) {
        // 这里需要实际的MCP协议调用
        // 暂时返回模拟结果，后续在具体的客户端实现中完成
        return new MCPToolResult();
    }
    
    /**
     * 从客户端获取资源列表
     */
    private List<MCPResource> fetchResourcesFromClient(MCPClient client) {
        // 这里需要实际的MCP协议调用
        // 暂时返回空列表，后续在具体的客户端实现中完成
        return new ArrayList<>();
    }
    
    /**
     * 从客户端读取资源内容
     */
    private MCPResourceContent readResourceFromClient(MCPClient client, String resourceUri, Duration timeout) {
        // 这里需要实际的MCP协议调用
        // 暂时返回模拟结果，后续在具体的客户端实现中完成
        return new MCPResourceContent();
    }
    
    /**
     * 从客户端获取提示词列表
     */
    private List<MCPPrompt> fetchPromptsFromClient(MCPClient client) {
        // 这里需要实际的MCP协议调用
        // 暂时返回空列表，后续在具体的客户端实现中完成
        return new ArrayList<>();
    }
    
    /**
     * 在客户端上执行提示词
     */
    private MCPPromptResult executePromptOnClient(MCPClient client, String promptName, Map<String, Object> arguments, Duration timeout) {
        // 这里需要实际的MCP协议调用
        // 暂时返回模拟结果，后续在具体的客户端实现中完成
        return new MCPPromptResult();
    }
    
    /**
     * 查找拥有指定资源的客户端
     */
    private String findClientForResource(String resourceUri) {
        try {
            for (String clientName : clientManager.getConnectedClients()) {
                List<MCPResource> resources = listResources(clientName).get(10, TimeUnit.SECONDS);
                for (MCPResource resource : resources) {
                    if (resourceUri.equals(resource.getUri())) {
                        return clientName;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error finding client for resource: " + resourceUri + ", error: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * 查找拥有指定提示词的客户端
     */
    private String findClientForPrompt(String promptName) {
        try {
            for (String clientName : clientManager.getConnectedClients()) {
                List<MCPPrompt> prompts = listPrompts(clientName).get(10, TimeUnit.SECONDS);
                for (MCPPrompt prompt : prompts) {
                    if (promptName.equals(prompt.getName())) {
                        return clientName;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error finding client for prompt: " + promptName + ", error: " + e.getMessage());
        }
        return null;
    }
}