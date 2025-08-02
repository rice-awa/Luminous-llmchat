package com.riceawa.mcp.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * MCPConfigValidator类的单元测试
 */
class MCPConfigValidatorTest {

    private MCPConfig validConfig;
    private MCPConfig invalidConfig;

    @BeforeEach
    void setUp() {
        // 创建有效配置
        validConfig = MCPConfig.createDefault();
        validConfig.setEnabled(true);
        MCPServerConfig validServer = MCPServerConfig.createStdioConfig(
            "valid-server", "uvx", Arrays.asList("test@latest"));
        validConfig.addServer(validServer);

        // 创建无效配置
        invalidConfig = MCPConfig.createDefault();
        invalidConfig.setEnabled(true); // 启用但没有服务器
    }

    @Test
    @DisplayName("测试null配置验证")
    void testValidateNullConfig() {
        MCPConfigValidator.ValidationResult result = MCPConfigValidator.validateConfig(null);
        
        assertNotNull(result);
        assertFalse(result.isValid());
        assertTrue(result.hasErrors());
        assertEquals(1, result.getErrors().size());
        
        MCPConfigValidator.ValidationIssue error = result.getErrors().get(0);
        assertEquals(MCPConfigValidator.ValidationSeverity.ERROR, error.getSeverity());
        assertEquals("MCPConfig", error.getComponent());
        assertTrue(error.getMessage().contains("null"));
    }

    @Test
    @DisplayName("测试有效配置验证")
    void testValidateValidConfig() {
        MCPConfigValidator.ValidationResult result = MCPConfigValidator.validateConfig(validConfig);
        
        assertNotNull(result);
        assertTrue(result.isValid());
        assertFalse(result.hasErrors());
        
        // 可能有警告，但不应该有错误
        List<MCPConfigValidator.ValidationIssue> issues = result.getIssues();
        for (MCPConfigValidator.ValidationIssue issue : issues) {
            assertNotEquals(MCPConfigValidator.ValidationSeverity.ERROR, issue.getSeverity());
        }
    }

    @Test
    @DisplayName("测试无效配置验证 - 启用但无服务器")
    void testValidateInvalidConfig() {
        MCPConfigValidator.ValidationResult result = MCPConfigValidator.validateConfig(invalidConfig);
        
        assertNotNull(result);
        assertFalse(result.isValid());
        assertTrue(result.hasErrors());
        
        // 应该有关于启用但无服务器的错误
        boolean foundExpectedError = result.getErrors().stream()
            .anyMatch(error -> error.getMessage().contains("启用但没有配置任何服务器"));
        assertTrue(foundExpectedError);
    }

    @Test
    @DisplayName("测试基础配置验证")
    void testBasicConfigValidation() {
        // 测试有服务器但功能未启用的情况
        MCPConfig config = MCPConfig.createDefault();
        config.setEnabled(false);
        MCPServerConfig server = MCPServerConfig.createStdioConfig(
            "test", "uvx", Arrays.asList("test@latest"));
        config.addServer(server);
        
        MCPConfigValidator.ValidationResult result = MCPConfigValidator.validateConfig(config);
        
        // 应该有警告但不是错误
        assertTrue(result.isValid());
        assertTrue(result.hasWarnings());
        
        boolean foundWarning = result.getWarnings().stream()
            .anyMatch(warning -> warning.getMessage().contains("已配置MCP服务器但功能未启用"));
        assertTrue(foundWarning);
    }

    @Test
    @DisplayName("测试服务器配置验证")
    void testServerConfigValidation() {
        MCPConfig config = MCPConfig.createDefault();
        config.setEnabled(true);
        
        // 添加重复名称的服务器
        MCPServerConfig server1 = MCPServerConfig.createStdioConfig(
            "duplicate", "command1", Arrays.asList("arg1"));
        MCPServerConfig server2 = MCPServerConfig.createStdioConfig(
            "duplicate", "command2", Arrays.asList("arg2"));
        
        config.addServer(server1);
        config.addServer(server2);
        
        MCPConfigValidator.ValidationResult result = MCPConfigValidator.validateConfig(config);
        
        // 由于MCPConfig.addServer会自动处理重复名称，这里不会有错误
        // 但我们可以测试其他服务器配置问题
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("测试超时配置验证")
    void testTimeoutConfigValidation() {
        MCPConfig config = MCPConfig.createDefault();
        config.setEnabled(true);
        config.addServer(MCPServerConfig.createStdioConfig(
            "test", "uvx", Arrays.asList("test@latest")));
        
        // 设置过短的超时时间
        config.setConnectionTimeoutMs(3000);
        config.setRequestTimeoutMs(2000);
        
        MCPConfigValidator.ValidationResult result = MCPConfigValidator.validateConfig(config);
        
        assertTrue(result.hasWarnings());
        
        boolean foundConnectionTimeoutWarning = result.getWarnings().stream()
            .anyMatch(warning -> warning.getMessage().contains("连接超时时间过短"));
        assertTrue(foundConnectionTimeoutWarning);
        
        boolean foundRequestTimeoutWarning = result.getWarnings().stream()
            .anyMatch(warning -> warning.getMessage().contains("请求超时时间过短"));
        assertTrue(foundRequestTimeoutWarning);
    }

    @Test
    @DisplayName("测试重试配置验证")
    void testRetryConfigValidation() {
        MCPConfig config = MCPConfig.createDefault();
        config.setEnabled(true);
        config.addServer(MCPServerConfig.createStdioConfig(
            "test", "uvx", Arrays.asList("test@latest")));
        
        // 设置过多的重试次数
        config.setMaxRetries(8);
        
        MCPConfigValidator.ValidationResult result = MCPConfigValidator.validateConfig(config);
        
        assertTrue(result.hasWarnings());
        
        boolean foundRetryWarning = result.getWarnings().stream()
            .anyMatch(warning -> warning.getMessage().contains("最大重试次数过多"));
        assertTrue(foundRetryWarning);
    }

    @Test
    @DisplayName("测试缓存配置验证")
    void testCacheConfigValidation() {
        // 创建一个测试用的配置类，绕过自动修复的setter
        MCPConfig config = new MCPConfig() {
            @Override
            public int getResourceCacheSize() {
                return 5; // 返回一个过小的值
            }
            
            @Override
            public int getResourceCacheTtlMinutes() {
                return 2; // 返回一个过短的值
            }
            
            @Override
            public boolean isEnableResourceCaching() {
                return true;
            }
            
            @Override
            public boolean isEnabled() {
                return true;
            }
            
            @Override
            public List<MCPServerConfig> getServers() {
                return Collections.singletonList(MCPServerConfig.createStdioConfig(
                    "test", "uvx", Arrays.asList("test@latest")));
            }
        };
        
        MCPConfigValidator.ValidationResult result = MCPConfigValidator.validateConfig(config);
        
        assertTrue(result.hasWarnings());
        
        boolean foundCacheSizeWarning = result.getWarnings().stream()
            .anyMatch(warning -> warning.getMessage().contains("资源缓存大小过小"));
        assertTrue(foundCacheSizeWarning);
        
        boolean foundTtlWarning = result.getWarnings().stream()
            .anyMatch(warning -> warning.getMessage().contains("资源缓存TTL过短"));
        assertTrue(foundTtlWarning);
    }

    @Test
    @DisplayName("测试权限配置验证")
    void testPermissionConfigValidation() {
        MCPConfig config = MCPConfig.createDefault();
        config.setEnabled(true);
        config.addServer(MCPServerConfig.createStdioConfig(
            "test", "uvx", Arrays.asList("test@latest")));
        
        // 设置宽松的权限策略
        config.setDefaultPermissionPolicy("ALLOW_ALL");
        
        MCPConfigValidator.ValidationResult result = MCPConfigValidator.validateConfig(config);
        
        assertTrue(result.hasWarnings());
        
        boolean foundPermissionWarning = result.getWarnings().stream()
            .anyMatch(warning -> warning.getMessage().contains("宽松的默认权限策略"));
        assertTrue(foundPermissionWarning);
    }

    @Test
    @DisplayName("测试STDIO服务器配置验证")
    void testStdioServerValidation() {
        MCPConfig config = MCPConfig.createDefault();
        config.setEnabled(true);
        
        // 创建一个使用非标准命令的STDIO服务器
        MCPServerConfig server = MCPServerConfig.createStdioConfig(
            "custom", "custom-command", Arrays.asList("arg1"));
        config.addServer(server);
        
        MCPConfigValidator.ValidationResult result = MCPConfigValidator.validateConfig(config);
        
        // 应该有信息性提示
        List<MCPConfigValidator.ValidationIssue> infoIssues = result.getIssues().stream()
            .filter(issue -> issue.getSeverity() == MCPConfigValidator.ValidationSeverity.INFO)
            .toList();
        
        boolean foundCustomCommandInfo = infoIssues.stream()
            .anyMatch(info -> info.getMessage().contains("非标准的MCP服务器启动命令"));
        assertTrue(foundCustomCommandInfo);
    }

    @Test
    @DisplayName("测试SSE服务器配置验证")
    void testSseServerValidation() {
        MCPConfig config = MCPConfig.createDefault();
        config.setEnabled(true);
        
        // 创建一个使用HTTP（非HTTPS）的SSE服务器
        MCPServerConfig server = MCPServerConfig.createSseConfig(
            "insecure", "http://example.com/mcp");
        config.addServer(server);
        
        MCPConfigValidator.ValidationResult result = MCPConfigValidator.validateConfig(config);
        
        assertTrue(result.hasWarnings());
        
        boolean foundHttpWarning = result.getWarnings().stream()
            .anyMatch(warning -> warning.getMessage().contains("不安全的HTTP连接"));
        assertTrue(foundHttpWarning);
    }

    @Test
    @DisplayName("测试状态报告生成")
    void testStatusReportGeneration() {
        String report = MCPConfigValidator.generateStatusReport(validConfig);
        
        assertNotNull(report);
        assertTrue(report.contains("MCP配置状态报告"));
        assertTrue(report.contains("功能状态"));
        assertTrue(report.contains("服务器总数"));
        assertTrue(report.contains("配置验证"));
        
        // 测试null配置的报告
        String nullReport = MCPConfigValidator.generateStatusReport(null);
        assertNotNull(nullReport);
        assertTrue(nullReport.contains("配置未初始化"));
    }

    @Test
    @DisplayName("测试自动修复功能")
    void testAutoFixConfig() {
        // 创建一个有问题的配置
        MCPConfig problematicConfig = MCPConfig.createDefault();
        problematicConfig.setConnectionTimeoutMs(100); // 太短
        problematicConfig.setMaxRetries(20); // 太多
        problematicConfig.setResourceCacheSize(5); // 太小
        problematicConfig.setDefaultPermissionPolicy(""); // 空策略
        
        MCPConfig fixedConfig = MCPConfigValidator.autoFixConfig(problematicConfig);
        
        assertNotNull(fixedConfig);
        assertTrue(fixedConfig.getConnectionTimeoutMs() >= 5000);
        assertTrue(fixedConfig.getMaxRetries() <= 5);
        assertTrue(fixedConfig.getResourceCacheSize() >= 10);
        assertEquals("OP_ONLY", fixedConfig.getDefaultPermissionPolicy());
        
        // 测试null配置的修复
        MCPConfig fixedNull = MCPConfigValidator.autoFixConfig(null);
        assertNotNull(fixedNull);
        assertTrue(fixedNull.isValid());
    }

    @Test
    @DisplayName("测试ValidationResult类")
    void testValidationResult() {
        MCPConfigValidator.ValidationResult result = MCPConfigValidator.validateConfig(validConfig);
        
        assertNotNull(result.getIssues());
        assertNotNull(result.getSuggestions());
        assertNotNull(result.getErrors());
        assertNotNull(result.getWarnings());
        
        // 测试不可变性
        List<MCPConfigValidator.ValidationIssue> issues = result.getIssues();
        List<String> suggestions = result.getSuggestions();
        
        // 修改返回的列表不应该影响原始结果
        assertThrows(UnsupportedOperationException.class, () -> {
            issues.clear();
        });
        
        assertThrows(UnsupportedOperationException.class, () -> {
            suggestions.clear();
        });
    }

    @Test
    @DisplayName("测试ValidationIssue类")
    void testValidationIssue() {
        MCPConfigValidator.ValidationIssue issue = new MCPConfigValidator.ValidationIssue(
            MCPConfigValidator.ValidationSeverity.ERROR,
            "TestComponent",
            "Test message",
            "Test suggestion"
        );
        
        assertEquals(MCPConfigValidator.ValidationSeverity.ERROR, issue.getSeverity());
        assertEquals("TestComponent", issue.getComponent());
        assertEquals("Test message", issue.getMessage());
        assertEquals("Test suggestion", issue.getSuggestion());
        
        String issueString = issue.toString();
        assertTrue(issueString.contains("ERROR"));
        assertTrue(issueString.contains("TestComponent"));
        assertTrue(issueString.contains("Test message"));
    }

    @Test
    @DisplayName("测试复杂配置场景")
    void testComplexConfigScenario() {
        MCPConfig complexConfig = MCPConfig.createDefault();
        complexConfig.setEnabled(true);
        
        // 添加多个不同类型的服务器
        MCPServerConfig stdioServer = MCPServerConfig.createStdioConfig(
            "stdio-server", "uvx", Arrays.asList("server@latest"));
        MCPServerConfig sseServer = MCPServerConfig.createSseConfig(
            "sse-server", "https://api.example.com/mcp");
        MCPServerConfig httpServer = MCPServerConfig.createSseConfig(
            "http-server", "http://insecure.example.com/mcp");
        
        // 配置权限
        stdioServer.addAllowedTool("tool1");
        stdioServer.addAllowedResource("file://resource1");
        
        complexConfig.addServer(stdioServer);
        complexConfig.addServer(sseServer);
        complexConfig.addServer(httpServer);
        
        // 设置一些有问题的配置
        complexConfig.setConnectionTimeoutMs(2000); // 太短
        complexConfig.setMaxRetries(8); // 太多
        complexConfig.setDefaultPermissionPolicy("ALLOW_ALL"); // 太宽松
        
        MCPConfigValidator.ValidationResult result = MCPConfigValidator.validateConfig(complexConfig);
        
        // 应该有多个警告但配置仍然有效
        assertTrue(result.isValid());
        assertTrue(result.hasWarnings());
        assertFalse(result.hasErrors());
        
        // 检查建议
        assertFalse(result.getSuggestions().isEmpty());
        
        // 生成状态报告
        String report = MCPConfigValidator.generateStatusReport(complexConfig);
        assertTrue(report.contains("服务器总数: 3"));
        assertTrue(report.contains("启用的服务器: 3"));
    }
}