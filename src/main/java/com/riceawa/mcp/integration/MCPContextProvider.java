package com.riceawa.mcp.integration;

import com.riceawa.mcp.resource.MCPResourceManager;
import com.riceawa.mcp.service.MCPService;
import com.riceawa.mcp.model.MCPResource;
import com.riceawa.llm.context.ChatContext;
import com.riceawa.llm.core.LLMMessage;
import net.minecraft.entity.player.PlayerEntity;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCP上下文提供器
 * 为AI对话提供MCP资源的上下文信息
 */
public class MCPContextProvider {
    
    private static MCPContextProvider instance;
    
    private final MCPService mcpService;
    private final MCPResourceManager resourceManager;
    
    // 资源引用模式：@resource:uri 或 @resource:"uri with spaces"
    private static final Pattern RESOURCE_REFERENCE_PATTERN = 
        Pattern.compile("@resource:(?:\"([^\"]+)\"|([^\\s]+))");
    
    // 资源搜索模式：@search:query 或 @search:"query with spaces"
    private static final Pattern RESOURCE_SEARCH_PATTERN = 
        Pattern.compile("@search:(?:\"([^\"]+)\"|([^\\s]+))");
    
    // 默认配置
    private static final int MAX_RESOURCES_PER_SEARCH = 3;
    private static final int MAX_CONTEXT_LENGTH = 4000;
    
    private MCPContextProvider(MCPService mcpService, MCPResourceManager resourceManager) {
        this.mcpService = mcpService;
        this.resourceManager = resourceManager;
    }
    
    /**
     * 获取单例实例
     */
    public static MCPContextProvider getInstance() {
        return instance;
    }
    
    /**
     * 初始化单例实例
     */
    public static void initialize(MCPService mcpService, MCPResourceManager resourceManager) {
        if (instance == null) {
            synchronized (MCPContextProvider.class) {
                if (instance == null) {
                    instance = new MCPContextProvider(mcpService, resourceManager);
                }
            }
        }
    }
    
    /**
     * 处理用户消息中的MCP资源引用
     * @param message 用户消息
     * @param chatContext 聊天上下文
     * @param player 玩家
     * @return 处理后的消息（可能包含资源上下文）
     */
    public CompletableFuture<String> processMessageWithResources(String message, ChatContext chatContext, PlayerEntity player) {
        // 快速检查MCP服务可用性
        if (!isServiceAvailable()) {
            return CompletableFuture.completedFuture(message);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                StringBuilder processedMessage = new StringBuilder(message);
                StringBuilder resourceContext = new StringBuilder();
                
                // 处理资源引用（带超时和错误处理）
                processResourceReferencesWithFallback(message, resourceContext);
                
                // 处理资源搜索（带超时和错误处理）
                processResourceSearchesWithFallback(message, resourceContext);
                
                // 如果有资源上下文，添加到消息中
                if (resourceContext.length() > 0) {
                    String contextText = resourceContext.toString();
                    if (contextText.length() > MAX_CONTEXT_LENGTH) {
                        contextText = contextText.substring(0, MAX_CONTEXT_LENGTH) + "\n...[上下文已截断]";
                    }
                    
                    processedMessage.append("\n\n").append(contextText);
                }
                
                return processedMessage.toString();
                
            } catch (Exception e) {
                System.err.println("处理MCP资源引用失败: " + e.getMessage());
                return message; // 返回原始消息，确保聊天功能不受影响
            }
        }).exceptionally(throwable -> {
            System.err.println("MCP资源处理异步操作失败: " + throwable.getMessage());
            return message; // 异常时返回原始消息
        });
    }
    
    /**
     * 处理资源直接引用
     */
    private void processResourceReferences(String message, StringBuilder resourceContext) {
        Matcher matcher = RESOURCE_REFERENCE_PATTERN.matcher(message);
        
        while (matcher.find()) {
            String uri = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            
            try {
                // 获取资源内容
                var resourceContent = resourceManager.getResourceContent(uri).get(10, TimeUnit.SECONDS);
                if (resourceContent != null) {
                    resourceContext.append("=== 引用资源: ").append(uri).append(" ===\n");
                    
                    String text = resourceContent.getText();
                    if (text != null && !text.trim().isEmpty()) {
                        // 限制单个资源的长度
                        if (text.length() > 1500) {
                            text = text.substring(0, 1500) + "...[内容已截断]";
                        }
                        resourceContext.append(text).append("\n\n");
                    } else {
                        resourceContext.append("[无法获取资源文本内容]\n\n");
                    }
                }
            } catch (Exception e) {
                resourceContext.append("=== 引用资源: ").append(uri).append(" ===\n");
                resourceContext.append("错误: 无法获取资源内容 - ").append(e.getMessage()).append("\n\n");
            }
        }
    }
    
    /**
     * 处理资源搜索
     */
    private void processResourceSearches(String message, StringBuilder resourceContext) {
        Matcher matcher = RESOURCE_SEARCH_PATTERN.matcher(message);
        
        while (matcher.find()) {
            String query = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            
            try {
                // 搜索资源
                String searchContext = resourceManager.getResourceContextForAI(query, MAX_RESOURCES_PER_SEARCH)
                    .get(15, TimeUnit.SECONDS);
                
                if (searchContext != null && !searchContext.trim().isEmpty()) {
                    resourceContext.append("=== 搜索结果: ").append(query).append(" ===\n");
                    resourceContext.append(searchContext).append("\n");
                }
            } catch (Exception e) {
                resourceContext.append("=== 搜索结果: ").append(query).append(" ===\n");
                resourceContext.append("错误: 搜索失败 - ").append(e.getMessage()).append("\n\n");
            }
        }
    }
    
    /**
     * 为聊天上下文添加相关资源
     * @param chatContext 聊天上下文
     * @param keywords 关键词列表
     * @param maxResources 最大资源数量
     */
    public CompletableFuture<Void> enrichContextWithResources(ChatContext chatContext, List<String> keywords, int maxResources) {
        if (!mcpService.isAvailable() || keywords.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            try {
                // 合并关键词进行搜索
                String combinedQuery = String.join(" ", keywords);
                
                String resourceContext = resourceManager.getResourceContextForAI(combinedQuery, maxResources)
                    .get(20, TimeUnit.SECONDS);
                
                if (resourceContext != null && !resourceContext.trim().isEmpty()) {
                    // 添加资源上下文作为系统消息
                    LLMMessage resourceMessage = new LLMMessage(
                        LLMMessage.MessageRole.SYSTEM, 
                        "以下是相关的资源信息，请在回答时参考：\n\n" + resourceContext
                    );
                    
                    chatContext.addMessage(resourceMessage);
                }
                
            } catch (Exception e) {
                System.err.println("为上下文添加资源失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 检查消息是否包含资源引用
     */
    public boolean hasResourceReferences(String message) {
        return RESOURCE_REFERENCE_PATTERN.matcher(message).find() || 
               RESOURCE_SEARCH_PATTERN.matcher(message).find();
    }
    
    /**
     * 获取可用资源的摘要信息
     */
    public CompletableFuture<String> getAvailableResourcesSummary() {
        if (!mcpService.isAvailable()) {
            return CompletableFuture.completedFuture("MCP服务不可用");
        }
        
        return resourceManager.listAvailableResources()
            .thenApply(resources -> {
                if (resources.isEmpty()) {
                    return "当前没有可用的MCP资源";
                }
                
                StringBuilder summary = new StringBuilder();
                summary.append("=== 可用的MCP资源 ===\n");
                
                // 按客户端分组
                java.util.Map<String, java.util.List<MCPResource>> resourcesByClient = 
                    resources.stream().collect(
                        java.util.stream.Collectors.groupingBy(MCPResource::getClientName)
                    );
                
                for (java.util.Map.Entry<String, java.util.List<MCPResource>> entry : resourcesByClient.entrySet()) {
                    String clientName = entry.getKey();
                    java.util.List<MCPResource> clientResources = entry.getValue();
                    
                    summary.append("\n客户端: ").append(clientName).append("\n");
                    
                    for (MCPResource resource : clientResources) {
                        summary.append("  - ").append(resource.getName());
                        if (resource.getTitle() != null) {
                            summary.append(" (").append(resource.getTitle()).append(")");
                        }
                        summary.append("\n");
                        
                        if (resource.getDescription() != null) {
                            String desc = resource.getDescription();
                            if (desc.length() > 100) {
                                desc = desc.substring(0, 100) + "...";
                            }
                            summary.append("    ").append(desc).append("\n");
                        }
                    }
                }
                
                summary.append("\n使用方法:\n");
                summary.append("- 引用资源: @resource:uri 或 @resource:\"uri with spaces\"\n");
                summary.append("- 搜索资源: @search:query 或 @search:\"query with spaces\"\n");
                
                return summary.toString();
            })
            .exceptionally(throwable -> {
                return "获取资源摘要失败: " + throwable.getMessage();
            });
    }
    
    /**
     * 清理资源缓存
     */
    public void clearResourceCache() {
        if (resourceManager != null) {
            resourceManager.clearCache();
        }
    }
    
    /**
     * 获取资源缓存统计
     */
    public String getResourceCacheStats() {
        if (resourceManager == null) {
            return "资源管理器未初始化";
        }
        
        var stats = resourceManager.getCacheStats();
        return String.format("资源缓存统计: %s", stats.toString());
    }

    /**
     * 检查MCP服务是否可用
     */
    private boolean isServiceAvailable() {
        try {
            return mcpService != null && mcpService.isAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 带降级处理的资源引用处理
     */
    private void processResourceReferencesWithFallback(String message, StringBuilder resourceContext) {
        Matcher matcher = RESOURCE_REFERENCE_PATTERN.matcher(message);
        
        while (matcher.find()) {
            String uri = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            
            try {
                // 设置较短的超时时间，避免阻塞
                var resourceContent = resourceManager.getResourceContent(uri)
                    .get(5, TimeUnit.SECONDS);
                    
                if (resourceContent != null) {
                    resourceContext.append("=== 引用资源: ").append(uri).append(" ===\n");
                    
                    String text = resourceContent.getText();
                    if (text != null && !text.trim().isEmpty()) {
                        // 限制单个资源的长度
                        if (text.length() > 1500) {
                            text = text.substring(0, 1500) + "...[内容已截断]";
                        }
                        resourceContext.append(text).append("\n\n");
                    } else {
                        resourceContext.append("[无法获取资源文本内容]\n\n");
                    }
                } else {
                    resourceContext.append("=== 引用资源: ").append(uri).append(" ===\n");
                    resourceContext.append("[资源不存在或无法访问]\n\n");
                }
            } catch (java.util.concurrent.TimeoutException e) {
                resourceContext.append("=== 引用资源: ").append(uri).append(" ===\n");
                resourceContext.append("[资源加载超时]\n\n");
            } catch (Exception e) {
                resourceContext.append("=== 引用资源: ").append(uri).append(" ===\n");
                resourceContext.append("错误: 无法获取资源内容 - ").append(e.getMessage()).append("\n\n");
            }
        }
    }

    /**
     * 带降级处理的资源搜索处理
     */
    private void processResourceSearchesWithFallback(String message, StringBuilder resourceContext) {
        Matcher matcher = RESOURCE_SEARCH_PATTERN.matcher(message);
        
        while (matcher.find()) {
            String query = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            
            try {
                // 设置较短的超时时间
                String searchContext = resourceManager.getResourceContextForAI(query, MAX_RESOURCES_PER_SEARCH)
                    .get(8, TimeUnit.SECONDS);
                
                if (searchContext != null && !searchContext.trim().isEmpty()) {
                    resourceContext.append("=== 搜索结果: ").append(query).append(" ===\n");
                    resourceContext.append(searchContext).append("\n");
                } else {
                    resourceContext.append("=== 搜索结果: ").append(query).append(" ===\n");
                    resourceContext.append("[未找到相关资源]\n\n");
                }
            } catch (java.util.concurrent.TimeoutException e) {
                resourceContext.append("=== 搜索结果: ").append(query).append(" ===\n");
                resourceContext.append("[搜索超时]\n\n");
            } catch (Exception e) {
                resourceContext.append("=== 搜索结果: ").append(query).append(" ===\n");
                resourceContext.append("错误: 搜索失败 - ").append(e.getMessage()).append("\n\n");
            }
        }
    }

    /**
     * 安全地为聊天上下文添加相关资源
     */
    public CompletableFuture<Void> safeEnrichContextWithResources(ChatContext chatContext, List<String> keywords, int maxResources) {
        if (!isServiceAvailable() || keywords.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return enrichContextWithResources(chatContext, keywords, maxResources)
            .exceptionally(throwable -> {
                System.err.println("为上下文添加资源失败，将继续正常处理: " + throwable.getMessage());
                return null;
            });
    }

    /**
     * 获取MCP系统状态
     */
    public String getMCPSystemStatus() {
        try {
            if (!isServiceAvailable()) {
                return "MCP服务不可用";
            }
            
            StringBuilder status = new StringBuilder();
            status.append("MCP系统状态: 可用\n");
            
            // 获取客户端状态
            var clientStatuses = mcpService.getClientStatuses();
            if (clientStatuses != null && !clientStatuses.isEmpty()) {
                int connectedCount = (int) clientStatuses.values().stream()
                    .mapToLong(s -> s.isConnected() ? 1 : 0)
                    .sum();
                status.append("客户端: ").append(connectedCount).append("/").append(clientStatuses.size()).append(" 已连接\n");
            }
            
            // 获取资源缓存状态
            if (resourceManager != null) {
                status.append("资源缓存: ").append(getResourceCacheStats()).append("\n");
            }
            
            return status.toString();
        } catch (Exception e) {
            return "获取MCP状态失败: " + e.getMessage();
        }
    }
}