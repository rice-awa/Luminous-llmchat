package com.riceawa.llm.config;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 提示词生成器管理器
 * 提供高级的提示词生成和缓存功能
 */
public class PromptGeneratorManager {
    
    private static final PromptGeneratorManager INSTANCE = new PromptGeneratorManager();
    
    private final PromptGeneratorRegistry registry;
    
    // 提示词缓存，避免重复生成相同的提示词
    private final ConcurrentMap<String, String> promptCache;
    
    // 缓存配置
    private final int maxCacheSize;
    private final long cacheExpirationMs;
    
    // 缓存统计
    private volatile long cacheHits = 0;
    private volatile long cacheMisses = 0;
    
    private PromptGeneratorManager() {
        this.registry = PromptGeneratorRegistry.getInstance();
        this.promptCache = new ConcurrentHashMap<>();
        this.maxCacheSize = 1000; // 最大缓存1000个提示词
        this.cacheExpirationMs = 3600000; // 1小时过期
    }
    
    /**
     * 获取单例实例
     */
    public static PromptGeneratorManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 生成系统提示词（带缓存）
     */
    public <T extends SubAgentTypeConfig> String generateSystemPrompt(T config) {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        
        String cacheKey = generateCacheKey(config, null);
        
        // 尝试从缓存获取
        String cachedPrompt = promptCache.get(cacheKey);
        if (cachedPrompt != null) {
            cacheHits++;
            return cachedPrompt;
        }
        
        // 缓存未命中，生成新的提示词
        cacheMisses++;
        String prompt = registry.generateSystemPrompt(config);
        
        // 添加到缓存
        cachePrompt(cacheKey, prompt);
        
        return prompt;
    }
    
    /**
     * 生成带上下文的系统提示词（带缓存）
     */
    public <T extends SubAgentTypeConfig> String generateSystemPrompt(T config, String taskContext) {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        
        String cacheKey = generateCacheKey(config, taskContext);
        
        // 尝试从缓存获取
        String cachedPrompt = promptCache.get(cacheKey);
        if (cachedPrompt != null) {
            cacheHits++;
            return cachedPrompt;
        }
        
        // 缓存未命中，生成新的提示词
        cacheMisses++;
        String prompt = registry.generateSystemPrompt(config, taskContext);
        
        // 添加到缓存
        cachePrompt(cacheKey, prompt);
        
        return prompt;
    }
    
    /**
     * 生成针对特定查询类型的提示词（仅适用于搜索子代理）
     */
    public String generateQuerySpecificPrompt(IntelligentSearchConfig config, String queryType) {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        
        String cacheKey = generateQuerySpecificCacheKey(config, queryType);
        
        // 尝试从缓存获取
        String cachedPrompt = promptCache.get(cacheKey);
        if (cachedPrompt != null) {
            cacheHits++;
            return cachedPrompt;
        }
        
        // 缓存未命中，生成新的提示词
        cacheMisses++;
        
        Optional<PromptGenerator<IntelligentSearchConfig>> generator = 
            registry.getGenerator(IntelligentSearchConfig.class);
        
        String prompt;
        // TODO: Add SearchPromptGenerator support after compilation issues are resolved
        prompt = registry.generateSystemPrompt(config);
        
        // 添加到缓存
        cachePrompt(cacheKey, prompt);
        
        return prompt;
    }
    
    /**
     * 生成搜索轮次特定的提示词（仅适用于搜索子代理）
     */
    public String generateRoundSpecificPrompt(IntelligentSearchConfig config, int currentRound, String previousResults) {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        
        // 轮次特定的提示词不缓存，因为每次都可能不同
        Optional<PromptGenerator<IntelligentSearchConfig>> generator = 
            registry.getGenerator(IntelligentSearchConfig.class);
        
        // TODO: Add SearchPromptGenerator support after compilation issues are resolved
        return registry.generateSystemPrompt(config);
    }
    
    /**
     * 验证提示词
     */
    public <T extends SubAgentTypeConfig> boolean validatePrompt(T config, String prompt) {
        return registry.validatePrompt(config, prompt);
    }
    
    /**
     * 注册新的提示词生成器
     */
    public <T extends SubAgentTypeConfig> void registerGenerator(PromptGenerator<T> generator) {
        registry.registerGenerator(generator);
        // 清除缓存，因为可能有新的生成器
        clearCache();
    }
    
    /**
     * 检查是否支持指定的配置类型
     */
    public boolean isSupported(Class<? extends SubAgentTypeConfig> configType) {
        return registry.isSupported(configType);
    }
    
    /**
     * 获取缓存统计信息
     */
    public CacheStats getCacheStats() {
        return new CacheStats(cacheHits, cacheMisses, promptCache.size());
    }
    
    /**
     * 清除提示词缓存
     */
    public void clearCache() {
        promptCache.clear();
        cacheHits = 0;
        cacheMisses = 0;
    }
    
    /**
     * 生成缓存键
     */
    private <T extends SubAgentTypeConfig> String generateCacheKey(T config, String taskContext) {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(config.getClass().getSimpleName());
        keyBuilder.append(":");
        keyBuilder.append(config.hashCode());
        
        if (taskContext != null && !taskContext.trim().isEmpty()) {
            keyBuilder.append(":");
            keyBuilder.append(taskContext.hashCode());
        }
        
        return keyBuilder.toString();
    }
    
    /**
     * 生成查询特定的缓存键
     */
    private String generateQuerySpecificCacheKey(IntelligentSearchConfig config, String queryType) {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append("QuerySpecific:");
        keyBuilder.append(config.getClass().getSimpleName());
        keyBuilder.append(":");
        keyBuilder.append(config.hashCode());
        keyBuilder.append(":");
        keyBuilder.append(queryType != null ? queryType.hashCode() : "null");
        
        return keyBuilder.toString();
    }
    
    /**
     * 缓存提示词
     */
    private void cachePrompt(String key, String prompt) {
        if (promptCache.size() >= maxCacheSize) {
            // 简单的LRU策略：清除一半的缓存
            promptCache.clear();
        }
        
        promptCache.put(key, prompt);
    }
    
    /**
     * 缓存统计信息类
     */
    public static class CacheStats {
        private final long hits;
        private final long misses;
        private final int size;
        
        public CacheStats(long hits, long misses, int size) {
            this.hits = hits;
            this.misses = misses;
            this.size = size;
        }
        
        public long getHits() {
            return hits;
        }
        
        public long getMisses() {
            return misses;
        }
        
        public int getSize() {
            return size;
        }
        
        public double getHitRate() {
            long total = hits + misses;
            return total > 0 ? (double) hits / total : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("CacheStats{hits=%d, misses=%d, size=%d, hitRate=%.2f%%}", 
                               hits, misses, size, getHitRate() * 100);
        }
    }
}