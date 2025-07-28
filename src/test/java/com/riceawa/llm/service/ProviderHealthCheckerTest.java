package com.riceawa.llm.service;

import com.riceawa.llm.config.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProviderHealthChecker测试类
 */
public class ProviderHealthCheckerTest {
    
    private ProviderHealthChecker healthChecker;
    private Provider validProvider;
    private Provider invalidProvider;
    
    @BeforeEach
    void setUp() {
        healthChecker = ProviderHealthChecker.getInstance();
        healthChecker.clearAllCache(); // 清除缓存确保测试独立性
        
        // 创建有效的provider（使用占位符，但配置完整）
        validProvider = new Provider(
            "test-provider",
            "https://api.openai.com/v1",
            "sk-test-key-12345",
            List.of("gpt-3.5-turbo", "gpt-4")
        );
        
        // 创建无效的provider（缺少API密钥）
        invalidProvider = new Provider(
            "invalid-provider",
            "https://api.openai.com/v1",
            null,
            List.of("gpt-3.5-turbo")
        );
    }
    
    @Test
    void testCheckProviderHealth_NullProvider() throws Exception {
        CompletableFuture<ProviderHealthChecker.HealthStatus> future = 
            healthChecker.checkProviderHealth(null);
        
        ProviderHealthChecker.HealthStatus status = future.get(5, TimeUnit.SECONDS);
        
        assertFalse(status.isHealthy());
        assertEquals("Provider为空", status.getMessage());
        assertEquals(ProviderHealthChecker.HealthStatus.ErrorType.CONFIG_ERROR, status.getErrorType());
    }
    
    @Test
    void testCheckProviderHealth_InvalidProvider() throws Exception {
        CompletableFuture<ProviderHealthChecker.HealthStatus> future = 
            healthChecker.checkProviderHealth(invalidProvider);
        
        ProviderHealthChecker.HealthStatus status = future.get(5, TimeUnit.SECONDS);
        
        assertFalse(status.isHealthy());
        assertEquals("API密钥为空", status.getMessage());
        assertEquals(ProviderHealthChecker.HealthStatus.ErrorType.CONFIG_ERROR, status.getErrorType());
    }
    
    @Test
    void testCheckProviderHealth_ValidProviderConfig() throws Exception {
        // 注意：这个测试会尝试实际连接，在没有真实API密钥的情况下会失败
        // 但我们可以验证配置检查部分是否正确
        CompletableFuture<ProviderHealthChecker.HealthStatus> future = 
            healthChecker.checkProviderHealth(validProvider);
        
        ProviderHealthChecker.HealthStatus status = future.get(15, TimeUnit.SECONDS);
        
        // 由于使用的是测试密钥，预期会失败，但不应该是配置错误
        assertFalse(status.isHealthy());
        assertNotEquals(ProviderHealthChecker.HealthStatus.ErrorType.CONFIG_ERROR, status.getErrorType());
        // 应该是认证错误或API错误
        assertTrue(status.getErrorType() == ProviderHealthChecker.HealthStatus.ErrorType.AUTH_ERROR ||
                  status.getErrorType() == ProviderHealthChecker.HealthStatus.ErrorType.API_ERROR ||
                  status.getErrorType() == ProviderHealthChecker.HealthStatus.ErrorType.NETWORK_ERROR);
    }
    
    @Test
    void testCheckAllProviders() throws Exception {
        List<Provider> providers = List.of(validProvider, invalidProvider);
        
        CompletableFuture<Map<String, ProviderHealthChecker.HealthStatus>> future = 
            healthChecker.checkAllProviders(providers);
        
        Map<String, ProviderHealthChecker.HealthStatus> results = future.get(20, TimeUnit.SECONDS);
        
        assertEquals(2, results.size());
        assertTrue(results.containsKey("test-provider"));
        assertTrue(results.containsKey("invalid-provider"));
        
        // 无效provider应该是配置错误
        ProviderHealthChecker.HealthStatus invalidStatus = results.get("invalid-provider");
        assertFalse(invalidStatus.isHealthy());
        assertEquals(ProviderHealthChecker.HealthStatus.ErrorType.CONFIG_ERROR, invalidStatus.getErrorType());
    }
    
    @Test
    void testCacheFunction() throws Exception {
        // 第一次检查
        CompletableFuture<ProviderHealthChecker.HealthStatus> future1 = 
            healthChecker.checkProviderHealth(invalidProvider);
        ProviderHealthChecker.HealthStatus status1 = future1.get(5, TimeUnit.SECONDS);
        
        // 检查缓存
        ProviderHealthChecker.HealthStatus cachedStatus = 
            healthChecker.getCachedHealth("invalid-provider");
        
        assertNotNull(cachedStatus);
        assertEquals(status1.isHealthy(), cachedStatus.isHealthy());
        assertEquals(status1.getMessage(), cachedStatus.getMessage());
        assertEquals(status1.getErrorType(), cachedStatus.getErrorType());
        
        // 清除缓存
        healthChecker.clearCache("invalid-provider");
        ProviderHealthChecker.HealthStatus clearedCache = 
            healthChecker.getCachedHealth("invalid-provider");
        
        assertNull(clearedCache);
    }
    
    @Test
    void testHealthStatusExpiration() {
        ProviderHealthChecker.HealthStatus status = new ProviderHealthChecker.HealthStatus(
            true, "test", ProviderHealthChecker.HealthStatus.ErrorType.NONE, 
            java.time.LocalDateTime.now().minusMinutes(10)
        );
        
        // 5分钟缓存，10分钟前的状态应该过期
        assertTrue(status.isExpired(TimeUnit.MINUTES.toMillis(5)));
        
        ProviderHealthChecker.HealthStatus freshStatus = new ProviderHealthChecker.HealthStatus(
            true, "test", ProviderHealthChecker.HealthStatus.ErrorType.NONE, 
            java.time.LocalDateTime.now()
        );
        
        // 刚创建的状态不应该过期
        assertFalse(freshStatus.isExpired(TimeUnit.MINUTES.toMillis(5)));
    }
    
    @Test
    void testFormattedCheckTime() {
        ProviderHealthChecker.HealthStatus status = new ProviderHealthChecker.HealthStatus(
            true, "test", ProviderHealthChecker.HealthStatus.ErrorType.NONE, 
            java.time.LocalDateTime.now()
        );
        
        String formattedTime = status.getFormattedCheckTime();
        assertNotNull(formattedTime);
        assertTrue(formattedTime.matches("\\d{2}:\\d{2}:\\d{2}")); // HH:mm:ss格式
    }
}
