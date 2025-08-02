package com.riceawa.mcp.resource;

import com.riceawa.mcp.model.MCPResource;
import com.riceawa.mcp.model.MCPResourceContent;
import com.riceawa.mcp.service.MCPService;
import com.riceawa.mcp.exception.MCPException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * MCP资源管理器
 * 负责管理MCP资源的访问、缓存和变化通知
 */
public class MCPResourceManager {
    
    private static MCPResourceManager instance;
    
    private final MCPService mcpService;
    
    // 资源内容缓存：URI -> 缓存项
    private final Map<String, ResourceCacheItem> resourceCache = new ConcurrentHashMap<>();
    
    // 资源变化监听器
    private final List<Consumer<List<MCPResource>>> resourceChangeListeners = new ArrayList<>();
    
    // 缓存配置
    private final Duration cacheTtl;
    private final int maxCacheSize;
    
    // 默认缓存配置
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(30);
    private static final int DEFAULT_MAX_CACHE_SIZE = 100;
    
    public MCPResourceManager(MCPService mcpService) {
        this(mcpService, DEFAULT_CACHE_TTL, DEFAULT_MAX_CACHE_SIZE);
    }
    
    public MCPResourceManager(MCPService mcpService, Duration cacheTtl, int maxCacheSize) {
        this.mcpService = mcpService;
        this.cacheTtl = cacheTtl;
        this.maxCacheSize = maxCacheSize;
    }
    
    /**
     * 获取单例实例
     */
    public static MCPResourceManager getInstance() {
        return instance;
    }
    
    /**
     * 初始化单例实例
     */
    public static void initialize(MCPService mcpService) {
        if (instance == null) {
            synchronized (MCPResourceManager.class) {
                if (instance == null) {
                    instance = new MCPResourceManager(mcpService);
                }
            }
        }
    }
    
    /**
     * 获取资源内容（带缓存）
     */
    public CompletableFuture<MCPResourceContent> getResourceContent(String uri) {
        return getResourceContent(uri, true);
    }
    
    /**
     * 获取资源内容
     * @param uri 资源URI
     * @param useCache 是否使用缓存
     */
    public CompletableFuture<MCPResourceContent> getResourceContent(String uri, boolean useCache) {
        if (useCache) {
            // 检查缓存
            ResourceCacheItem cacheItem = resourceCache.get(uri);
            if (cacheItem != null && !cacheItem.isExpired()) {
                return CompletableFuture.completedFuture(cacheItem.getContent());
            }
        }
        
        // 从MCP服务获取资源
        return mcpService.readResource(uri)
            .thenApply(content -> {
                if (useCache && content != null) {
                    // 更新缓存
                    updateCache(uri, content);
                }
                return content;
            });
    }
    
    /**
     * 获取所有可用资源列表
     */
    public CompletableFuture<List<MCPResource>> listAvailableResources() {
        return mcpService.listAllResources();
    }
    
    /**
     * 获取指定客户端的资源列表
     */
    public CompletableFuture<List<MCPResource>> listClientResources(String clientName) {
        return mcpService.listResources(clientName);
    }
    
    /**
     * 搜索资源
     * @param query 搜索关键词
     * @param resourceType 资源类型过滤（可选）
     */
    public CompletableFuture<List<MCPResource>> searchResources(String query, String resourceType) {
        return listAvailableResources()
            .thenApply(resources -> {
                return resources.stream()
                    .filter(resource -> matchesQuery(resource, query))
                    .filter(resource -> resourceType == null || resourceType.equals(resource.getMimeType()))
                    .collect(java.util.stream.Collectors.toList());
            });
    }
    
    /**
     * 检查资源是否匹配查询
     */
    private boolean matchesQuery(MCPResource resource, String query) {
        if (query == null || query.trim().isEmpty()) {
            return true;
        }
        
        String lowerQuery = query.toLowerCase();
        
        // 检查名称
        if (resource.getName() != null && resource.getName().toLowerCase().contains(lowerQuery)) {
            return true;
        }
        
        // 检查标题
        if (resource.getTitle() != null && resource.getTitle().toLowerCase().contains(lowerQuery)) {
            return true;
        }
        
        // 检查描述
        if (resource.getDescription() != null && resource.getDescription().toLowerCase().contains(lowerQuery)) {
            return true;
        }
        
        // 检查URI
        if (resource.getUri() != null && resource.getUri().toLowerCase().contains(lowerQuery)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 预加载资源到缓存
     */
    public CompletableFuture<Void> preloadResources(List<String> uris) {
        List<CompletableFuture<MCPResourceContent>> futures = new ArrayList<>();
        
        for (String uri : uris) {
            futures.add(getResourceContent(uri, true));
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
    
    /**
     * 订阅资源变化通知
     */
    public void subscribeToResourceChanges(Consumer<List<MCPResource>> callback) {
        if (callback != null && !resourceChangeListeners.contains(callback)) {
            resourceChangeListeners.add(callback);
        }
    }
    
    /**
     * 取消订阅资源变化通知
     */
    public void unsubscribeFromResourceChanges(Consumer<List<MCPResource>> callback) {
        resourceChangeListeners.remove(callback);
    }
    
    /**
     * 通知资源变化
     */
    public void notifyResourceChanges(List<MCPResource> resources) {
        for (Consumer<List<MCPResource>> listener : resourceChangeListeners) {
            try {
                listener.accept(resources);
            } catch (Exception e) {
                System.err.println("资源变化通知失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 清除缓存
     */
    public void clearCache() {
        resourceCache.clear();
    }
    
    /**
     * 清除指定URI的缓存
     */
    public void clearCache(String uri) {
        resourceCache.remove(uri);
    }
    
    /**
     * 清除过期缓存
     */
    public void clearExpiredCache() {
        resourceCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    /**
     * 获取缓存统计信息
     */
    public CacheStats getCacheStats() {
        int totalItems = resourceCache.size();
        int expiredItems = (int) resourceCache.values().stream()
            .mapToLong(item -> item.isExpired() ? 1 : 0)
            .sum();
        
        return new CacheStats(totalItems, expiredItems, maxCacheSize);
    }
    
    /**
     * 更新缓存
     */
    private void updateCache(String uri, MCPResourceContent content) {
        // 如果缓存已满，移除最旧的项
        if (resourceCache.size() >= maxCacheSize) {
            removeOldestCacheItem();
        }
        
        ResourceCacheItem cacheItem = new ResourceCacheItem(content, LocalDateTime.now().plus(cacheTtl));
        resourceCache.put(uri, cacheItem);
    }
    
    /**
     * 移除最旧的缓存项
     */
    private void removeOldestCacheItem() {
        String oldestUri = null;
        LocalDateTime oldestTime = LocalDateTime.now();
        
        for (Map.Entry<String, ResourceCacheItem> entry : resourceCache.entrySet()) {
            LocalDateTime cacheTime = entry.getValue().getCacheTime();
            if (cacheTime.isBefore(oldestTime)) {
                oldestTime = cacheTime;
                oldestUri = entry.getKey();
            }
        }
        
        if (oldestUri != null) {
            resourceCache.remove(oldestUri);
        }
    }
    
    /**
     * 为AI对话提供资源上下文
     * @param query 查询关键词
     * @param maxResources 最大资源数量
     * @return 资源上下文字符串
     */
    public CompletableFuture<String> getResourceContextForAI(String query, int maxResources) {
        return searchResources(query, null)
            .thenCompose(resources -> {
                // 限制资源数量
                List<MCPResource> limitedResources = resources.stream()
                    .limit(maxResources)
                    .collect(java.util.stream.Collectors.toList());
                
                // 获取资源内容
                List<CompletableFuture<String>> contentFutures = new ArrayList<>();
                for (MCPResource resource : limitedResources) {
                    CompletableFuture<String> contentFuture = getResourceContent(resource.getUri())
                        .thenApply(content -> formatResourceForAI(resource, content))
                        .exceptionally(throwable -> {
                            return String.format("资源 %s 无法访问: %s", 
                                resource.getName(), throwable.getMessage());
                        });
                    contentFutures.add(contentFuture);
                }
                
                return CompletableFuture.allOf(contentFutures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> {
                        StringBuilder context = new StringBuilder();
                        context.append("=== 相关资源信息 ===\n");
                        
                        for (CompletableFuture<String> future : contentFutures) {
                            try {
                                String resourceText = future.get(5, TimeUnit.SECONDS);
                                context.append(resourceText).append("\n\n");
                            } catch (Exception e) {
                                // 忽略单个资源的错误
                            }
                        }
                        
                        return context.toString();
                    });
            });
    }
    
    /**
     * 格式化资源内容供AI使用
     */
    private String formatResourceForAI(MCPResource resource, MCPResourceContent content) {
        StringBuilder formatted = new StringBuilder();
        
        formatted.append("资源: ").append(resource.getName()).append("\n");
        if (resource.getTitle() != null) {
            formatted.append("标题: ").append(resource.getTitle()).append("\n");
        }
        if (resource.getDescription() != null) {
            formatted.append("描述: ").append(resource.getDescription()).append("\n");
        }
        formatted.append("类型: ").append(resource.getMimeType()).append("\n");
        formatted.append("内容:\n");
        
        if (content != null && content.getText() != null) {
            String text = content.getText();
            // 限制内容长度，避免上下文过长
            if (text.length() > 2000) {
                text = text.substring(0, 2000) + "...[内容已截断]";
            }
            formatted.append(text);
        } else {
            formatted.append("[无法获取文本内容]");
        }
        
        return formatted.toString();
    }
    
    /**
     * 资源缓存项
     */
    private static class ResourceCacheItem {
        private final MCPResourceContent content;
        private final LocalDateTime expiryTime;
        private final LocalDateTime cacheTime;
        
        public ResourceCacheItem(MCPResourceContent content, LocalDateTime expiryTime) {
            this.content = content;
            this.expiryTime = expiryTime;
            this.cacheTime = LocalDateTime.now();
        }
        
        public MCPResourceContent getContent() {
            return content;
        }
        
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiryTime);
        }
        
        public LocalDateTime getCacheTime() {
            return cacheTime;
        }
    }
    
    /**
     * 缓存统计信息
     */
    public static class CacheStats {
        private final int totalItems;
        private final int expiredItems;
        private final int maxSize;
        
        public CacheStats(int totalItems, int expiredItems, int maxSize) {
            this.totalItems = totalItems;
            this.expiredItems = expiredItems;
            this.maxSize = maxSize;
        }
        
        public int getTotalItems() {
            return totalItems;
        }
        
        public int getExpiredItems() {
            return expiredItems;
        }
        
        public int getActiveItems() {
            return totalItems - expiredItems;
        }
        
        public int getMaxSize() {
            return maxSize;
        }
        
        public double getUsagePercentage() {
            return maxSize > 0 ? (double) totalItems / maxSize * 100 : 0;
        }
        
        @Override
        public String toString() {
            return String.format("CacheStats{total=%d, active=%d, expired=%d, max=%d, usage=%.1f%%}",
                totalItems, getActiveItems(), expiredItems, maxSize, getUsagePercentage());
        }
    }
}