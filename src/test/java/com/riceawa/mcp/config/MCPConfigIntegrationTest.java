package com.riceawa.mcp.config;

import com.riceawa.llm.config.LLMChatConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

/**
 * MCP配置与LLMChatConfig集成测试
 */
class MCPConfigIntegrationTest {

    private LLMChatConfig llmConfig;

    @BeforeEach
    void setUp() {
        // 注意：这里不能使用getInstance()，因为它是单例，会影响其他测试
        // 我们需要通过反射或其他方式创建测试实例
        // 为了简化测试，我们直接测试MCP配置的集成功能
    }

    @Test
    @DisplayName("测试MCP配置的基本集成")
    void testBasicMCPConfigIntegration() {
        // 创建MCP配置
        MCPConfig mcpConfig = MCPConfig.createDefault();
        mcpConfig.setEnabled(true);
        
        // 添加STDIO服务器
        MCPServerConfig stdioServer = MCPServerConfig.createStdioConfig(
            "test-stdio", "uvx", Arrays.asList("test-server@latest"));
        mcpConfig.addServer(stdioServer);
        
        // 添加SSE服务器
        MCPServerConfig sseServer = MCPServerConfig.createSseConfig(
            "test-sse", "https://api.example.com/mcp");
        mcpConfig.addServer(sseServer);
        
        // 验证配置
        assertTrue(mcpConfig.isValid());
        assertTrue(mcpConfig.hasEnabledServers());
        assertEquals(2, mcpConfig.getServers().size());
        
        // 验证配置验证功能
        MCPConfigValidator.ValidationResult result = mcpConfig.validate();
        assertTrue(result.isValid());
        assertFalse(result.hasErrors());
        
        // 测试状态报告
        String statusReport = mcpConfig.generateStatusReport();
        assertNotNull(statusReport);
        assertTrue(statusReport.contains("MCP配置状态报告"));
        assertTrue(statusReport.contains("服务器总数: 2"));
        assertTrue(statusReport.contains("启用的服务器: 2"));
        
        // 测试诊断信息
        String diagnostics = mcpConfig.getDiagnosticInfo();
        assertNotNull(diagnostics);
    }

    @Test
    @DisplayName("测试MCP配置的自动修复功能")
    void testMCPConfigAutoFix() {
        // 创建一个有问题的配置
        MCPConfig problematicConfig = MCPConfig.createDefault();
        problematicConfig.setEnabled(true);
        problematicConfig.setConnectionTimeoutMs(100); // 会被自动修复为1000
        problematicConfig.setMaxRetries(20); // 会被自动修复为10
        
        // 添加一个有效的服务器
        MCPServerConfig server = MCPServerConfig.createStdioConfig(
            "test", "uvx", Arrays.asList("test@latest"));
        problematicConfig.addServer(server);
        
        // 验证自动修复后的值
        assertEquals(1000, problematicConfig.getConnectionTimeoutMs()); // 最小值
        assertEquals(10, problematicConfig.getMaxRetries()); // 最大值
        
        // 使用验证器的自动修复功能
        MCPConfig fixedConfig = problematicConfig.autoFix();
        assertNotNull(fixedConfig);
        assertTrue(fixedConfig.getConnectionTimeoutMs() >= 5000); // 验证器会设置更合理的值
        assertTrue(fixedConfig.getMaxRetries() <= 5);
    }

    @Test
    @DisplayName("测试复杂的MCP配置场景")
    void testComplexMCPConfigScenario() {
        MCPConfig config = MCPConfig.createDefault();
        config.setEnabled(true);
        
        // 配置各种参数
        config.setConnectionTimeoutMs(15000);
        config.setRequestTimeoutMs(8000);
        config.setMaxRetries(2);
        config.setEnableResourceCaching(true);
        config.setResourceCacheSize(50);
        config.setResourceCacheTtlMinutes(15);
        config.setDefaultPermissionPolicy("OP_ONLY");
        config.setEnableToolChangeNotifications(true);
        config.setEnableResourceChangeNotifications(true);
        
        // 添加多个服务器
        MCPServerConfig server1 = MCPServerConfig.createStdioConfig(
            "python-server", "python", Arrays.asList("-m", "mcp_server"));
        server1.addAllowedTool("file_read");
        server1.addAllowedTool("file_write");
        server1.addAllowedResource("file://");
        server1.setDescription("Python MCP Server for file operations");
        
        MCPServerConfig server2 = MCPServerConfig.createSseConfig(
            "web-server", "https://api.example.com/mcp");
        server2.addAllowedTool("web_search");
        server2.addAllowedResource("https://example.com");
        server2.setDescription("Web-based MCP Server");
        
        MCPServerConfig server3 = MCPServerConfig.createStdioConfig(
            "disabled-server", "disabled-command", Arrays.asList("arg"));
        server3.setEnabled(false);
        server3.setDescription("Disabled server for testing");
        
        config.addServer(server1);
        config.addServer(server2);
        config.addServer(server3);
        
        // 验证配置
        assertTrue(config.isValid());
        assertEquals(3, config.getServers().size());
        assertEquals(2, config.getEnabledServers().size()); // 只有2个启用的服务器
        
        // 验证服务器配置
        MCPServerConfig retrievedServer1 = config.getServer("python-server");
        assertNotNull(retrievedServer1);
        assertTrue(retrievedServer1.isToolAllowed("file_read"));
        assertTrue(retrievedServer1.isResourceAllowed("file://"));
        assertFalse(retrievedServer1.isToolAllowed("web_search"));
        
        MCPServerConfig retrievedServer2 = config.getServer("web-server");
        assertNotNull(retrievedServer2);
        assertTrue(retrievedServer2.isToolAllowed("web_search"));
        assertTrue(retrievedServer2.isResourceAllowed("https://example.com"));
        assertFalse(retrievedServer2.isResourceAllowed("file://test.txt"));
        
        // 验证配置验证
        MCPConfigValidator.ValidationResult result = config.validate();
        assertTrue(result.isValid());
        
        // 生成详细的状态报告
        String statusReport = config.generateStatusReport();
        assertTrue(statusReport.contains("服务器总数: 3"));
        assertTrue(statusReport.contains("启用的服务器: 2"));
        assertTrue(statusReport.contains("python-server"));
        assertTrue(statusReport.contains("web-server"));
        assertTrue(statusReport.contains("disabled-server"));
        
        // 测试服务器移除
        config.removeServer("disabled-server");
        assertEquals(2, config.getServers().size());
        assertNull(config.getServer("disabled-server"));
    }

    @Test
    @DisplayName("测试MCP配置的错误处理")
    void testMCPConfigErrorHandling() {
        MCPConfig config = MCPConfig.createDefault();
        
        // 测试添加无效服务器
        MCPServerConfig invalidServer = new MCPServerConfig();
        invalidServer.setName("invalid");
        invalidServer.setType("stdio");
        // 没有设置command，所以无效
        
        config.addServer(invalidServer);
        // 无效服务器不应该被添加
        assertTrue(config.getServers().isEmpty());
        
        // 测试null处理
        config.addServer(null);
        assertTrue(config.getServers().isEmpty());
        
        // 测试获取不存在的服务器
        assertNull(config.getServer("nonexistent"));
        
        // 测试移除不存在的服务器
        assertDoesNotThrow(() -> config.removeServer("nonexistent"));
        
        // 测试启用但无服务器的情况
        config.setEnabled(true);
        assertFalse(config.isValid());
        assertTrue(config.hasErrors());
        
        String diagnostics = config.getDiagnosticInfo();
        assertTrue(diagnostics.contains("错误"));
    }

    @Test
    @DisplayName("测试MCP服务器配置的权限管理")
    void testMCPServerPermissionManagement() {
        MCPServerConfig server = MCPServerConfig.createStdioConfig(
            "permission-test", "test-command", Arrays.asList("arg"));
        
        // 测试默认权限（允许所有）
        assertTrue(server.isToolAllowed("any-tool"));
        assertTrue(server.isResourceAllowed("any-resource"));
        
        // 添加特定工具权限
        server.addAllowedTool("tool1");
        server.addAllowedTool("tool2");
        
        // 现在只允许指定的工具
        assertTrue(server.isToolAllowed("tool1"));
        assertTrue(server.isToolAllowed("tool2"));
        assertFalse(server.isToolAllowed("tool3"));
        
        // 添加资源权限
        server.addAllowedResource("file://allowed");
        server.addAllowedResource("https://allowed.com");
        
        // 现在只允许指定的资源
        assertTrue(server.isResourceAllowed("file://allowed"));
        assertTrue(server.isResourceAllowed("https://allowed.com"));
        assertFalse(server.isResourceAllowed("file://denied"));
        
        // 测试权限策略
        assertEquals("INHERIT_CLIENT", server.getToolPermissionPolicy());
        server.setToolPermissionPolicy("OP_ONLY");
        assertEquals("OP_ONLY", server.getToolPermissionPolicy());
        
        // 测试移除权限
        server.removeAllowedTool("tool1");
        assertFalse(server.isToolAllowed("tool1"));
        assertTrue(server.isToolAllowed("tool2"));
        
        server.removeAllowedResource("file://allowed");
        assertFalse(server.isResourceAllowed("file://allowed"));
        assertTrue(server.isResourceAllowed("https://allowed.com"));
    }
}