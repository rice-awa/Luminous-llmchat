package com.riceawa.llm.service;

import com.riceawa.llm.config.Provider;
import com.riceawa.llm.core.LLMConfig;
import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.core.LLMResponse;
import com.riceawa.llm.core.LLMService;
import com.riceawa.llm.core.LLMContext;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Provider健康检查器
 * 负责异步检测provider的可用性，缓存检测结果
 */
public class ProviderHealthChecker {
    
    private static ProviderHealthChecker instance;
    private final Map<String, HealthStatus> healthCache = new ConcurrentHashMap<>();
    private final long CACHE_DURATION_MS = TimeUnit.MINUTES.toMillis(5); // 5分钟缓存
    
    private ProviderHealthChecker() {}
    
    public static ProviderHealthChecker getInstance() {
        if (instance == null) {
            synchronized (ProviderHealthChecker.class) {
                if (instance == null) {
                    instance = new ProviderHealthChecker();
                }
            }
        }
        return instance;
    }
    
    /**
     * 异步检测单个provider的健康状态
     */
    public CompletableFuture<HealthStatus> checkProviderHealth(Provider provider) {
        if (provider == null) {
            return CompletableFuture.completedFuture(
                new HealthStatus(false, "Provider为空", HealthStatus.ErrorType.CONFIG_ERROR, LocalDateTime.now())
            );
        }
        
        // 检查缓存
        HealthStatus cached = healthCache.get(provider.getName());
        if (cached != null && !cached.isExpired(CACHE_DURATION_MS)) {
            return CompletableFuture.completedFuture(cached);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return performHealthCheck(provider);
            } catch (Exception e) {
                return new HealthStatus(false, "检测异常: " + e.getMessage(), 
                    HealthStatus.ErrorType.UNKNOWN_ERROR, LocalDateTime.now());
            }
        }).whenComplete((result, throwable) -> {
            if (result != null) {
                healthCache.put(provider.getName(), result);
            }
        });
    }
    
    /**
     * 异步检测所有provider的健康状态
     */
    public CompletableFuture<Map<String, HealthStatus>> checkAllProviders(List<Provider> providers) {
        if (providers == null || providers.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        
        CompletableFuture<?>[] futures = providers.stream()
            .map(this::checkProviderHealth)
            .toArray(CompletableFuture[]::new);
        
        return CompletableFuture.allOf(futures)
            .thenApply(v -> {
                Map<String, HealthStatus> results = new ConcurrentHashMap<>();
                for (Provider provider : providers) {
                    HealthStatus status = healthCache.get(provider.getName());
                    if (status != null) {
                        results.put(provider.getName(), status);
                    }
                }
                return results;
            });
    }
    
    /**
     * 获取缓存的健康状态
     */
    public HealthStatus getCachedHealth(String providerName) {
        return healthCache.get(providerName);
    }
    
    /**
     * 清除指定provider的缓存
     */
    public void clearCache(String providerName) {
        healthCache.remove(providerName);
    }
    
    /**
     * 清除所有缓存
     */
    public void clearAllCache() {
        healthCache.clear();
    }
    
    /**
     * 执行实际的健康检查
     */
    private HealthStatus performHealthCheck(Provider provider) {
        LocalDateTime checkTime = LocalDateTime.now();
        
        // 基本配置检查
        if (!isProviderConfigValid(provider)) {
            return new HealthStatus(false, getConfigErrorMessage(provider), 
                HealthStatus.ErrorType.CONFIG_ERROR, checkTime);
        }
        
        try {
            // 创建服务实例进行测试
            LLMService service = createServiceForProvider(provider);
            if (service == null) {
                return new HealthStatus(false, "无法创建服务实例", 
                    HealthStatus.ErrorType.CONFIG_ERROR, checkTime);
            }
            
            // 发送测试请求
            LLMMessage testMessage = new LLMMessage(LLMMessage.MessageRole.USER, "test");
            LLMConfig testConfig = new LLMConfig();
            testConfig.setModel(provider.getModels().get(0)); // 使用第一个模型
            testConfig.setMaxTokens(1); // 最小token数
            testConfig.setTemperature(0.1);
            
            // 创建健康检查上下文
            LLMContext healthContext = LLMContext.builder()
                    .metadata("operation", "health_check")
                    .metadata("provider", provider.getName())
                    .build();

            CompletableFuture<LLMResponse> future = service.chat(List.of(testMessage), testConfig, healthContext);
            LLMResponse response = future.get(30, TimeUnit.SECONDS); // 30秒超时
            
            if (response.isSuccess()) {
                return new HealthStatus(true, "连接正常", HealthStatus.ErrorType.NONE, checkTime);
            } else {
                String error = response.getError();
                HealthStatus.ErrorType errorType = categorizeError(error);
                return new HealthStatus(false, "API错误: " + error, errorType, checkTime);
            }
            
        } catch (java.util.concurrent.TimeoutException e) {
            return new HealthStatus(false, "连接超时", HealthStatus.ErrorType.NETWORK_ERROR, checkTime);
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            HealthStatus.ErrorType errorType = categorizeError(errorMsg);
            return new HealthStatus(false, "连接失败: " + errorMsg, errorType, checkTime);
        }
    }
    
    /**
     * 检查provider配置是否有效
     */
    private boolean isProviderConfigValid(Provider provider) {
        return provider != null && 
               provider.getName() != null && !provider.getName().trim().isEmpty() &&
               provider.getApiBaseUrl() != null && !provider.getApiBaseUrl().trim().isEmpty() &&
               provider.getApiKey() != null && !provider.getApiKey().trim().isEmpty() &&
               !isPlaceholderApiKey(provider.getApiKey()) &&
               provider.getModels() != null && !provider.getModels().isEmpty();
    }
    
    /**
     * 检查是否为占位符API密钥
     */
    private boolean isPlaceholderApiKey(String apiKey) {
        return apiKey.contains("your-") || apiKey.contains("-api-key-here") || 
               apiKey.equals("sk-placeholder") || apiKey.equals("placeholder");
    }
    
    /**
     * 获取配置错误信息
     */
    private String getConfigErrorMessage(Provider provider) {
        if (provider == null) return "Provider为空";
        if (provider.getName() == null || provider.getName().trim().isEmpty()) return "名称为空";
        if (provider.getApiBaseUrl() == null || provider.getApiBaseUrl().trim().isEmpty()) return "API基础URL为空";
        if (provider.getApiKey() == null || provider.getApiKey().trim().isEmpty()) return "API密钥为空";
        if (isPlaceholderApiKey(provider.getApiKey())) return "API密钥为占位符，需要设置真实密钥";
        if (provider.getModels() == null || provider.getModels().isEmpty()) return "模型列表为空";
        return "配置无效";
    }
    
    /**
     * 为provider创建服务实例
     */
    private LLMService createServiceForProvider(Provider provider) {
        // 目前只支持OpenAI兼容的服务
        return new OpenAIService(provider.getApiKey(), provider.getApiBaseUrl());
    }
    
    /**
     * 根据错误信息分类错误类型
     */
    private HealthStatus.ErrorType categorizeError(String error) {
        if (error == null) return HealthStatus.ErrorType.UNKNOWN_ERROR;
        
        String lowerError = error.toLowerCase();
        if (lowerError.contains("unauthorized") || lowerError.contains("401") || 
            lowerError.contains("invalid api key") || lowerError.contains("authentication")) {
            return HealthStatus.ErrorType.AUTH_ERROR;
        }
        if (lowerError.contains("timeout") || lowerError.contains("connection") || 
            lowerError.contains("network") || lowerError.contains("unreachable")) {
            return HealthStatus.ErrorType.NETWORK_ERROR;
        }
        if (lowerError.contains("rate limit") || lowerError.contains("429")) {
            return HealthStatus.ErrorType.RATE_LIMIT_ERROR;
        }
        if (lowerError.contains("model") || lowerError.contains("404")) {
            return HealthStatus.ErrorType.MODEL_ERROR;
        }
        return HealthStatus.ErrorType.API_ERROR;
    }
    
    /**
     * 健康状态类
     */
    public static class HealthStatus {
        private final boolean healthy;
        private final String message;
        private final ErrorType errorType;
        private final LocalDateTime checkTime;
        
        public HealthStatus(boolean healthy, String message, ErrorType errorType, LocalDateTime checkTime) {
            this.healthy = healthy;
            this.message = message;
            this.errorType = errorType;
            this.checkTime = checkTime;
        }
        
        public boolean isHealthy() { return healthy; }
        public String getMessage() { return message; }
        public ErrorType getErrorType() { return errorType; }
        public LocalDateTime getCheckTime() { return checkTime; }
        
        public boolean isExpired(long cacheDurationMs) {
            return checkTime.plusNanos(cacheDurationMs * 1_000_000).isBefore(LocalDateTime.now());
        }
        
        public String getFormattedCheckTime() {
            return checkTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        }
        
        public enum ErrorType {
            NONE,
            CONFIG_ERROR,
            AUTH_ERROR,
            NETWORK_ERROR,
            RATE_LIMIT_ERROR,
            MODEL_ERROR,
            API_ERROR,
            UNKNOWN_ERROR
        }
    }
}
