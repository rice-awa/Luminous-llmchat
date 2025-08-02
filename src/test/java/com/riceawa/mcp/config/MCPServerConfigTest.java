package com.riceawa.mcp.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * MCPServerConfig类的单元测试
 */
class MCPServerConfigTest {

    private MCPServerConfig stdioConfig;
    private MCPServerConfig sseConfig;

    @BeforeEach
    void setUp() {
        stdioConfig = MCPServerConfig.createStdioConfig(
            "test-stdio", "uvx", Arrays.asList("test-server@latest"));
        sseConfig = MCPServerConfig.createSseConfig(
            "test-sse", "https://example.com/mcp");
    }

    @Test
    @DisplayName("测试STDIO配置创建")
    void testCreateStdioConfig() {
        assertNotNull(stdioConfig);
        assertEquals("test-stdio", stdioConfig.getName());
        assertEquals("stdio", stdioConfig.getType());
        assertEquals("uvx", stdioConfig.getCommand());
        assertEquals(Arrays.asList("test-server@latest"), stdioConfig.getArgs());
        assertTrue(stdioConfig.isEnabled());
        assertTrue(stdioConfig.isStdioType());
        assertFalse(stdioConfig.isSseType());
    }

    @Test
    @DisplayName("测试SSE配置创建")
    void testCreateSseConfig() {
        assertNotNull(sseConfig);
        assertEquals("test-sse", sseConfig.getName());
        assertEquals("sse", sseConfig.getType());
        assertEquals("https://example.com/mcp", sseConfig.getUrl());
        assertTrue(sseConfig.isEnabled());
        assertFalse(sseConfig.isStdioType());
        assertTrue(sseConfig.isSseType());
    }

    @Test
    @DisplayName("测试基本属性设置")
    void testBasicProperties() {
        MCPServerConfig config = new MCPServerConfig();
        
        config.setName("test-server");
        assertEquals("test-server", config.getName());
        
        config.setType("stdio");
        assertEquals("stdio", config.getType());
        
        config.setEnabled(false);
        assertFalse(config.isEnabled());
        
        config.setEnabled(true);
        assertTrue(config.isEnabled());
        
        config.setDescription("Test server description");
        assertEquals("Test server description", config.getDescription());
    }

    @Test
    @DisplayName("测试STDIO特定属性")
    void testStdioSpecificProperties() {
        MCPServerConfig config = new MCPServerConfig();
        config.setType("stdio");
        
        // 测试命令设置
        config.setCommand("python");
        assertEquals("python", config.getCommand());
        
        // 测试参数管理
        config.setArgs(Arrays.asList("arg1", "arg2"));
        assertEquals(Arrays.asList("arg1", "arg2"), config.getArgs());
        
        config.addArg("arg3");
        assertEquals(Arrays.asList("arg1", "arg2", "arg3"), config.getArgs());
        
        // 测试null参数处理
        config.addArg(null);
        assertEquals(Arrays.asList("arg1", "arg2", "arg3"), config.getArgs());
        
        // 测试环境变量管理
        Map<String, String> env = new HashMap<>();
        env.put("KEY1", "value1");
        env.put("KEY2", "value2");
        config.setEnv(env);
        assertEquals(env, config.getEnv());
        
        config.addEnv("KEY3", "value3");
        assertTrue(config.getEnv().containsKey("KEY3"));
        assertEquals("value3", config.getEnv().get("KEY3"));
        
        // 测试null环境变量处理
        config.addEnv(null, "value");
        config.addEnv("key", null);
        assertFalse(config.getEnv().containsKey(null));
        assertFalse(config.getEnv().containsValue(null));
    }

    @Test
    @DisplayName("测试SSE特定属性")
    void testSseSpecificProperties() {
        MCPServerConfig config = new MCPServerConfig();
        config.setType("sse");
        
        config.setUrl("https://api.example.com/mcp");
        assertEquals("https://api.example.com/mcp", config.getUrl());
    }

    @Test
    @DisplayName("测试工具权限管理")
    void testToolPermissions() {
        MCPServerConfig config = new MCPServerConfig();
        
        // 测试默认状态（允许所有工具）
        assertTrue(config.isToolAllowed("any-tool"));
        
        // 添加允许的工具
        config.addAllowedTool("tool1");
        config.addAllowedTool("tool2");
        
        Set<String> expectedTools = new HashSet<>(Arrays.asList("tool1", "tool2"));
        assertEquals(expectedTools, config.getAllowedTools());
        
        // 测试工具权限检查
        assertTrue(config.isToolAllowed("tool1"));
        assertTrue(config.isToolAllowed("tool2"));
        assertFalse(config.isToolAllowed("tool3"));
        
        // 移除工具
        config.removeAllowedTool("tool1");
        assertFalse(config.isToolAllowed("tool1"));
        assertTrue(config.isToolAllowed("tool2"));
        
        // 测试null和空字符串处理
        config.addAllowedTool(null);
        config.addAllowedTool("");
        config.addAllowedTool("  ");
        assertFalse(config.getAllowedTools().contains(null));
        assertFalse(config.getAllowedTools().contains(""));
        assertFalse(config.getAllowedTools().contains("  "));
        
        // 测试设置工具列表
        Set<String> newTools = new HashSet<>(Arrays.asList("new-tool1", "new-tool2"));
        config.setAllowedTools(newTools);
        assertEquals(newTools, config.getAllowedTools());
        
        // 测试null列表处理
        config.setAllowedTools(null);
        assertTrue(config.getAllowedTools().isEmpty());
        assertTrue(config.isToolAllowed("any-tool")); // 空列表应该允许所有工具
    }

    @Test
    @DisplayName("测试资源权限管理")
    void testResourcePermissions() {
        MCPServerConfig config = new MCPServerConfig();
        
        // 测试默认状态（允许所有资源）
        assertTrue(config.isResourceAllowed("file://any-resource"));
        
        // 添加允许的资源
        config.addAllowedResource("file://resource1");
        config.addAllowedResource("http://resource2");
        
        Set<String> expectedResources = new HashSet<>(Arrays.asList("file://resource1", "http://resource2"));
        assertEquals(expectedResources, config.getAllowedResources());
        
        // 测试资源权限检查
        assertTrue(config.isResourceAllowed("file://resource1"));
        assertTrue(config.isResourceAllowed("http://resource2"));
        assertFalse(config.isResourceAllowed("file://resource3"));
        
        // 移除资源
        config.removeAllowedResource("file://resource1");
        assertFalse(config.isResourceAllowed("file://resource1"));
        assertTrue(config.isResourceAllowed("http://resource2"));
        
        // 测试null和空字符串处理
        config.addAllowedResource(null);
        config.addAllowedResource("");
        config.addAllowedResource("  ");
        assertFalse(config.getAllowedResources().contains(null));
        assertFalse(config.getAllowedResources().contains(""));
        assertFalse(config.getAllowedResources().contains("  "));
        
        // 测试设置资源列表
        Set<String> newResources = new HashSet<>(Arrays.asList("new-resource1", "new-resource2"));
        config.setAllowedResources(newResources);
        assertEquals(newResources, config.getAllowedResources());
        
        // 测试null列表处理
        config.setAllowedResources(null);
        assertTrue(config.getAllowedResources().isEmpty());
        assertTrue(config.isResourceAllowed("any-resource")); // 空列表应该允许所有资源
    }

    @Test
    @DisplayName("测试权限策略")
    void testPermissionPolicy() {
        MCPServerConfig config = new MCPServerConfig();
        
        // 测试默认权限策略
        assertEquals("INHERIT_CLIENT", config.getToolPermissionPolicy());
        
        // 设置权限策略
        config.setToolPermissionPolicy("OP_ONLY");
        assertEquals("OP_ONLY", config.getToolPermissionPolicy());
        
        // 测试null处理
        config.setToolPermissionPolicy(null);
        assertEquals("INHERIT_CLIENT", config.getToolPermissionPolicy());
    }

    @Test
    @DisplayName("测试配置有效性验证")
    void testConfigValidation() {
        // 测试有效的STDIO配置
        assertTrue(stdioConfig.isValid());
        
        // 测试有效的SSE配置
        assertTrue(sseConfig.isValid());
        
        // 测试无效配置 - 没有名称
        MCPServerConfig invalidConfig = new MCPServerConfig();
        assertFalse(invalidConfig.isValid());
        
        // 测试无效配置 - 空名称
        invalidConfig.setName("");
        assertFalse(invalidConfig.isValid());
        
        // 测试无效配置 - 没有类型
        invalidConfig.setName("test");
        assertFalse(invalidConfig.isValid());
        
        // 测试无效的STDIO配置 - 没有命令
        invalidConfig.setType("stdio");
        assertFalse(invalidConfig.isValid());
        
        // 修复STDIO配置
        invalidConfig.setCommand("python");
        assertTrue(invalidConfig.isValid());
        
        // 测试无效的SSE配置 - 没有URL
        MCPServerConfig sseInvalid = new MCPServerConfig();
        sseInvalid.setName("test-sse");
        sseInvalid.setType("sse");
        assertFalse(sseInvalid.isValid());
        
        // 测试无效的SSE配置 - 无效URL格式
        sseInvalid.setUrl("invalid-url");
        assertFalse(sseInvalid.isValid());
        
        // 修复SSE配置
        sseInvalid.setUrl("https://example.com");
        assertTrue(sseInvalid.isValid());
        
        // 测试未知类型
        MCPServerConfig unknownType = new MCPServerConfig();
        unknownType.setName("test");
        unknownType.setType("unknown");
        assertFalse(unknownType.isValid());
    }

    @Test
    @DisplayName("测试类型检查方法")
    void testTypeChecking() {
        assertTrue(stdioConfig.isStdioType());
        assertFalse(stdioConfig.isSseType());
        
        assertFalse(sseConfig.isStdioType());
        assertTrue(sseConfig.isSseType());
        
        // 测试大小写不敏感
        MCPServerConfig config = new MCPServerConfig();
        config.setType("STDIO");
        assertTrue(config.isStdioType());
        
        config.setType("SSE");
        assertTrue(config.isSseType());
        
        config.setType("StDiO");
        assertTrue(config.isStdioType());
    }

    @Test
    @DisplayName("测试toString方法")
    void testToString() {
        String stdioString = stdioConfig.toString();
        assertTrue(stdioString.contains("test-stdio"));
        assertTrue(stdioString.contains("stdio"));
        assertTrue(stdioString.contains("true"));
        
        String sseString = sseConfig.toString();
        assertTrue(sseString.contains("test-sse"));
        assertTrue(sseString.contains("sse"));
        assertTrue(sseString.contains("true"));
    }

    @Test
    @DisplayName("测试描述字段")
    void testDescription() {
        MCPServerConfig config = new MCPServerConfig();
        
        // 测试默认描述
        assertEquals("", config.getDescription());
        
        // 设置描述
        config.setDescription("This is a test server");
        assertEquals("This is a test server", config.getDescription());
        
        // 测试null描述
        config.setDescription(null);
        assertEquals("", config.getDescription());
    }

    @Test
    @DisplayName("测试集合的不可变性")
    void testCollectionImmutability() {
        // 测试args列表的不可变性
        stdioConfig.addArg("new-arg");
        java.util.List<String> args = stdioConfig.getArgs();
        
        // 尝试修改返回的列表不应该影响原始配置
        assertThrows(UnsupportedOperationException.class, () -> {
            args.add("should-not-work");
        });
        
        // 测试env映射的不可变性
        stdioConfig.addEnv("KEY", "value");
        Map<String, String> env = stdioConfig.getEnv();
        
        // 尝试修改返回的映射不应该影响原始配置
        assertThrows(UnsupportedOperationException.class, () -> {
            env.put("NEW_KEY", "new-value");
        });
        
        // 测试工具集合的不可变性
        stdioConfig.addAllowedTool("tool1");
        Set<String> tools = stdioConfig.getAllowedTools();
        
        // 尝试修改返回的集合不应该影响原始配置
        assertThrows(UnsupportedOperationException.class, () -> {
            tools.add("should-not-work");
        });
        
        // 测试资源集合的不可变性
        stdioConfig.addAllowedResource("resource1");
        Set<String> resources = stdioConfig.getAllowedResources();
        
        // 尝试修改返回的集合不应该影响原始配置
        assertThrows(UnsupportedOperationException.class, () -> {
            resources.add("should-not-work");
        });
    }
}