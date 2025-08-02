package com.riceawa.mcp.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * MCPConfig类的单元测试
 */
class MCPConfigTest {

    private MCPConfig config;

    @BeforeEach
    void setUp() {
        config = MCPConfig.createDefault();
    }

    @Test
    @DisplayName("测试默认配置创建")
    void testCreateDefault() {
        MCPConfig defaultConfig = MCPConfig.createDefault();
        
        assertNotNull(defaultConfig);
        assertFalse(defaultConfig.isEnabled());
        assertTrue(defaultConfig.getServers().isEmpty());
        assertEquals(30000, defaultConfig.getConnectionTimeoutMs());
        assertEquals(10000, defaultConfig.getRequestTimeoutMs());
        assertEquals(3, defaultConfig.getMaxRetries());
        assertTrue(defaultConfig.isEnableResourceCaching());
        assertEquals(100, defaultConfig.getResourceCacheSize());
        assertEquals(30, defaultConfig.getResourceCacheTtlMinutes());
        assertEquals("OP_ONLY", defaultConfig.getDefaultPermissionPolicy());
        assertTrue(defaultConfig.isEnableToolChangeNotifications());
        assertTrue(defaultConfig.isEnableResourceChangeNotifications());
    }

    @Test
    @DisplayName("测试启用/禁用功能")
    void testEnableDisable() {
        assertFalse(config.isEnabled());
        
        config.setEnabled(true);
        assertTrue(config.isEnabled());
        
        config.setEnabled(false);
        assertFalse(config.isEnabled());
    }

    @Test
    @DisplayName("测试服务器配置管理")
    void testServerManagement() {
        assertTrue(config.getServers().isEmpty());
        
        // 添加STDIO服务器
        MCPServerConfig stdioServer = MCPServerConfig.createStdioConfig(
            "test-stdio", "uvx", Arrays.asList("test-server@latest"));
        config.addServer(stdioServer);
        
        assertEquals(1, config.getServers().size());
        assertEquals("test-stdio", config.getServers().get(0).getName());
        
        // 添加SSE服务器
        MCPServerConfig sseServer = MCPServerConfig.createSseConfig(
            "test-sse", "https://example.com/mcp");
        config.addServer(sseServer);
        
        assertEquals(2, config.getServers().size());
        
        // 获取特定服务器
        MCPServerConfig retrieved = config.getServer("test-stdio");
        assertNotNull(retrieved);
        assertEquals("test-stdio", retrieved.getName());
        
        // 移除服务器
        config.removeServer("test-stdio");
        assertEquals(1, config.getServers().size());
        assertNull(config.getServer("test-stdio"));
    }

    @Test
    @DisplayName("测试重复服务器名称处理")
    void testDuplicateServerNames() {
        MCPServerConfig server1 = MCPServerConfig.createStdioConfig(
            "duplicate", "command1", Arrays.asList("arg1"));
        MCPServerConfig server2 = MCPServerConfig.createStdioConfig(
            "duplicate", "command2", Arrays.asList("arg2"));
        
        config.addServer(server1);
        config.addServer(server2);
        
        // 应该只有一个服务器，且是最后添加的
        assertEquals(1, config.getServers().size());
        assertEquals("command2", config.getServer("duplicate").getCommand());
    }

    @Test
    @DisplayName("测试超时配置")
    void testTimeoutConfiguration() {
        // 测试连接超时
        config.setConnectionTimeoutMs(5000);
        assertEquals(5000, config.getConnectionTimeoutMs());
        
        // 测试最小值限制
        config.setConnectionTimeoutMs(500);
        assertEquals(1000, config.getConnectionTimeoutMs()); // 应该被限制为最小值
        
        // 测试请求超时
        config.setRequestTimeoutMs(8000);
        assertEquals(8000, config.getRequestTimeoutMs());
        
        // 测试最小值限制
        config.setRequestTimeoutMs(500);
        assertEquals(1000, config.getRequestTimeoutMs()); // 应该被限制为最小值
    }

    @Test
    @DisplayName("测试重试配置")
    void testRetryConfiguration() {
        config.setMaxRetries(5);
        assertEquals(5, config.getMaxRetries());
        
        // 测试最小值限制
        config.setMaxRetries(-1);
        assertEquals(0, config.getMaxRetries());
        
        // 测试最大值限制
        config.setMaxRetries(15);
        assertEquals(10, config.getMaxRetries());
    }

    @Test
    @DisplayName("测试缓存配置")
    void testCacheConfiguration() {
        // 测试缓存开关
        config.setEnableResourceCaching(false);
        assertFalse(config.isEnableResourceCaching());
        
        config.setEnableResourceCaching(true);
        assertTrue(config.isEnableResourceCaching());
        
        // 测试缓存大小
        config.setResourceCacheSize(200);
        assertEquals(200, config.getResourceCacheSize());
        
        // 测试最小值限制
        config.setResourceCacheSize(5);
        assertEquals(10, config.getResourceCacheSize());
        
        // 测试最大值限制
        config.setResourceCacheSize(2000);
        assertEquals(1000, config.getResourceCacheSize());
        
        // 测试TTL
        config.setResourceCacheTtlMinutes(60);
        assertEquals(60, config.getResourceCacheTtlMinutes());
        
        // 测试最小值限制
        config.setResourceCacheTtlMinutes(0);
        assertEquals(1, config.getResourceCacheTtlMinutes());
    }

    @Test
    @DisplayName("测试权限策略配置")
    void testPermissionPolicyConfiguration() {
        config.setDefaultPermissionPolicy("ALLOW_ALL");
        assertEquals("ALLOW_ALL", config.getDefaultPermissionPolicy());
        
        config.setDefaultPermissionPolicy(null);
        assertEquals("OP_ONLY", config.getDefaultPermissionPolicy());
        
        config.setDefaultPermissionPolicy("");
        assertEquals("OP_ONLY", config.getDefaultPermissionPolicy());
    }

    @Test
    @DisplayName("测试通知配置")
    void testNotificationConfiguration() {
        config.setEnableToolChangeNotifications(false);
        assertFalse(config.isEnableToolChangeNotifications());
        
        config.setEnableResourceChangeNotifications(false);
        assertFalse(config.isEnableResourceChangeNotifications());
        
        config.setEnableToolChangeNotifications(true);
        assertTrue(config.isEnableToolChangeNotifications());
        
        config.setEnableResourceChangeNotifications(true);
        assertTrue(config.isEnableResourceChangeNotifications());
    }

    @Test
    @DisplayName("测试配置有效性验证")
    void testConfigValidation() {
        // 默认配置应该是有效的（未启用时）
        assertTrue(config.isValid());
        
        // 启用但没有服务器时应该无效
        config.setEnabled(true);
        assertFalse(config.isValid());
        
        // 添加有效服务器后应该有效
        MCPServerConfig validServer = MCPServerConfig.createStdioConfig(
            "valid", "uvx", Arrays.asList("test@latest"));
        config.addServer(validServer);
        assertTrue(config.isValid());
        
        // 添加无效服务器不应该影响整体有效性
        MCPServerConfig invalidServer = new MCPServerConfig();
        invalidServer.setName("invalid");
        invalidServer.setType("stdio");
        // 没有设置command，所以无效
        config.addServer(invalidServer);
        assertTrue(config.isValid()); // 仍然有效，因为有一个有效的服务器
    }

    @Test
    @DisplayName("测试启用的服务器获取")
    void testGetEnabledServers() {
        MCPServerConfig server1 = MCPServerConfig.createStdioConfig(
            "server1", "command1", Arrays.asList("arg1"));
        MCPServerConfig server2 = MCPServerConfig.createStdioConfig(
            "server2", "command2", Arrays.asList("arg2"));
        
        server1.setEnabled(true);
        server2.setEnabled(false);
        
        config.addServer(server1);
        config.addServer(server2);
        
        List<MCPServerConfig> enabledServers = config.getEnabledServers();
        assertEquals(1, enabledServers.size());
        assertEquals("server1", enabledServers.get(0).getName());
        
        assertTrue(config.hasEnabledServers());
        
        // 禁用所有服务器
        server1.setEnabled(false);
        config.addServer(server1); // 重新添加以更新状态
        
        assertFalse(config.hasEnabledServers());
    }

    @Test
    @DisplayName("测试配置验证方法")
    void testValidationMethods() {
        // 测试validate方法
        MCPConfigValidator.ValidationResult result = config.validate();
        assertNotNull(result);
        assertTrue(result.isValid()); // 默认配置应该有效
        
        // 测试hasErrors和hasWarnings方法
        assertFalse(config.hasErrors());
        // 可能有警告，取决于具体实现
        
        // 测试getDiagnosticInfo方法
        String diagnostics = config.getDiagnosticInfo();
        assertNotNull(diagnostics);
        
        // 测试generateStatusReport方法
        String report = config.generateStatusReport();
        assertNotNull(report);
        assertTrue(report.contains("MCP配置状态报告"));
        
        // 测试autoFix方法
        MCPConfig fixedConfig = config.autoFix();
        assertNotNull(fixedConfig);
    }

    @Test
    @DisplayName("测试无效配置的自动修复")
    void testAutoFixInvalidConfig() {
        // 创建一个有问题的配置
        config.setConnectionTimeoutMs(100); // 太短
        config.setMaxRetries(20); // 太多
        config.setResourceCacheSize(5); // 太小
        
        MCPConfig fixedConfig = config.autoFix();
        
        // 验证修复结果
        assertTrue(fixedConfig.getConnectionTimeoutMs() >= 5000);
        assertTrue(fixedConfig.getMaxRetries() <= 5);
        assertTrue(fixedConfig.getResourceCacheSize() >= 10);
    }

    @Test
    @DisplayName("测试null值处理")
    void testNullHandling() {
        // 测试添加null服务器
        config.addServer(null);
        assertTrue(config.getServers().isEmpty());
        
        // 测试获取不存在的服务器
        assertNull(config.getServer("nonexistent"));
        
        // 测试移除不存在的服务器
        assertDoesNotThrow(() -> config.removeServer("nonexistent"));
        
        // 测试设置null权限策略
        config.setDefaultPermissionPolicy(null);
        assertEquals("OP_ONLY", config.getDefaultPermissionPolicy());
    }
}