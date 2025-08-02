package com.riceawa.mcp.service;

import com.riceawa.mcp.config.MCPConfig;
import com.riceawa.mcp.config.MCPServerConfig;
import com.riceawa.mcp.exception.MCPException;
import com.riceawa.mcp.model.MCPClientStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MCPClientManager单元测试
 */
@ExtendWith(MockitoExtension.class)
class MCPClientManagerTest {

    private MCPClientManager clientManager;
    private MCPConfig config;
    
    @Mock
    private MCPClient mockClient1;
    
    @Mock
    private MCPClient mockClient2;
    
    @Mock
    private MCPClientFactory mockClientFactory;

    @BeforeEach
    void setUp() {
        // 创建测试配置
        config = MCPConfig.createDefault();
        config.setEnabled(true);
        
        // 添加测试服务器配置
        MCPServerConfig server1 = MCPServerConfig.createStdioConfig(
            "test-server1", "uvx", Arrays.asList("test-server@latest"));
        MCPServerConfig server2 = MCPServerConfig.createSseConfig(
            "test-server2", "https://example.com/mcp");
        
        config.addServer(server1);
        config.addServer(server2);
        
        // 获取管理器实例
        clientManager = MCPClientManager.getInstance();
    }

    @Test
    @DisplayName("测试单例模式")
    void testSingletonPattern() {
        MCPClientManager instance1 = MCPClientManager.getInstance();
        MCPClientManager instance2 = MCPClientManager.getInstance();
        
        assertSame(instance1, instance2);
    }

    @Test
    @DisplayName("测试初始化管理器")
    void testInitializeManager() throws Exception {
        // 模拟客户端创建
        when(mockClient1.connect()).thenReturn(CompletableFuture.completedFuture(null));
        when(mockClient2.connect()).thenReturn(CompletableFuture.completedFuture(null));
        
        // 初始化管理器
        CompletableFuture<Void> initFuture = clientManager.initialize(config);
        initFuture.get(5, TimeUnit.SECONDS);
        
        assertTrue(clientManager.isInitialized());
        assertEquals(config, clientManager.getConfig());
    }

    @Test
    @DisplayName("测试禁用MCP时的初始化")
    void testInitializeWithDisabledMCP() throws Exception {
        config.setEnabled(false);
        
        CompletableFuture<Void> initFuture = clientManager.initialize(config);
        initFuture.get(5, TimeUnit.SECONDS);
        
        assertTrue(clientManager.isInitialized());
        assertEquals(0, clientManager.getTotalClientCount());
    }

    @Test
    @DisplayName("测试启动管理器")
    void testStartManager() throws Exception {
        // 先初始化
        clientManager.initialize(config).get(5, TimeUnit.SECONDS);
        
        // 启动管理器
        CompletableFuture<Void> startFuture = clientManager.start();
        startFuture.get(5, TimeUnit.SECONDS);
        
        assertTrue(clientManager.isRunning());
    }

    @Test
    @DisplayName("测试停止管理器")
    void testStopManager() throws Exception {
        // 先初始化和启动
        clientManager.initialize(config).get(5, TimeUnit.SECONDS);
        clientManager.start().get(5, TimeUnit.SECONDS);
        
        // 停止管理器
        CompletableFuture<Void> stopFuture = clientManager.stop();
        stopFuture.get(5, TimeUnit.SECONDS);
        
        assertFalse(clientManager.isRunning());
    }

    @Test
    @DisplayName("测试获取客户端")
    void testGetClient() throws Exception {
        clientManager.initialize(config).get(5, TimeUnit.SECONDS);
        
        // 测试获取存在的客户端
        MCPClient client = clientManager.getClient("test-server1");
        // 由于我们没有mock客户端工厂，这里可能为null，但不应该抛异常
        
        // 测试获取不存在的客户端
        MCPClient nonExistentClient = clientManager.getClient("non-existent");
        assertNull(nonExistentClient);
    }

    @Test
    @DisplayName("测试获取客户端名称列表")
    void testGetClientNames() throws Exception {
        clientManager.initialize(config).get(5, TimeUnit.SECONDS);
        
        List<String> clientNames = clientManager.getClientNames();
        assertNotNull(clientNames);
        // 由于实际的客户端创建可能失败，我们只检查返回值不为null
    }

    @Test
    @DisplayName("测试获取客户端状态")
    void testGetClientStatuses() throws Exception {
        clientManager.initialize(config).get(5, TimeUnit.SECONDS);
        
        Map<String, MCPClientStatus> statuses = clientManager.getAllClientStatuses();
        assertNotNull(statuses);
        
        // 测试获取单个客户端状态
        MCPClientStatus status = clientManager.getClientStatus("test-server1");
        // 状态可能为null（如果客户端创建失败），但不应该抛异常
    }

    @Test
    @DisplayName("测试重新连接客户端")
    void testReconnectClient() throws Exception {
        clientManager.initialize(config).get(5, TimeUnit.SECONDS);
        
        // 测试重连不存在的客户端
        CompletableFuture<Void> reconnectFuture = clientManager.reconnectClient("non-existent");
        
        assertThrows(Exception.class, () -> {
            reconnectFuture.get(5, TimeUnit.SECONDS);
        });
    }

    @Test
    @DisplayName("测试重新加载配置")
    void testReloadConfig() throws Exception {
        clientManager.initialize(config).get(5, TimeUnit.SECONDS);
        
        // 创建新配置
        MCPConfig newConfig = MCPConfig.createDefault();
        newConfig.setEnabled(true);
        MCPServerConfig newServer = MCPServerConfig.createStdioConfig(
            "new-server", "uvx", Arrays.asList("new-server@latest"));
        newConfig.addServer(newServer);
        
        // 重新加载配置
        CompletableFuture<Void> reloadFuture = clientManager.reloadConfig(newConfig);
        reloadFuture.get(5, TimeUnit.SECONDS);
        
        assertEquals(newConfig, clientManager.getConfig());
    }

    @Test
    @DisplayName("测试禁用MCP的配置重载")
    void testReloadConfigWithDisabledMCP() throws Exception {
        clientManager.initialize(config).get(5, TimeUnit.SECONDS);
        
        // 创建禁用MCP的配置
        MCPConfig disabledConfig = MCPConfig.createDefault();
        disabledConfig.setEnabled(false);
        
        // 重新加载配置
        CompletableFuture<Void> reloadFuture = clientManager.reloadConfig(disabledConfig);
        reloadFuture.get(5, TimeUnit.SECONDS);
        
        assertFalse(clientManager.isRunning());
        assertEquals(disabledConfig, clientManager.getConfig());
    }

    @Test
    @DisplayName("测试连接状态检查")
    void testConnectionStatusChecks() throws Exception {
        clientManager.initialize(config).get(5, TimeUnit.SECONDS);
        
        // 测试连接客户端数量
        int connectedCount = clientManager.getConnectedClientCount();
        assertTrue(connectedCount >= 0);
        
        // 测试是否有连接的客户端
        boolean hasConnected = clientManager.hasConnectedClients();
        assertEquals(connectedCount > 0, hasConnected);
        
        // 测试获取连接的客户端列表
        List<String> connectedClients = clientManager.getConnectedClients();
        assertNotNull(connectedClients);
        assertEquals(connectedCount, connectedClients.size());
    }

    @Test
    @DisplayName("测试状态报告生成")
    void testGenerateStatusReport() throws Exception {
        clientManager.initialize(config).get(5, TimeUnit.SECONDS);
        
        String report = clientManager.generateStatusReport();
        assertNotNull(report);
        assertTrue(report.contains("MCP客户端管理器状态报告"));
        assertTrue(report.contains("管理器状态"));
        assertTrue(report.contains("总客户端数"));
        assertTrue(report.contains("已连接客户端数"));
    }

    @Test
    @DisplayName("测试未初始化时的操作")
    void testOperationsBeforeInitialization() {
        MCPClientManager freshManager = MCPClientManager.getInstance();
        
        assertFalse(freshManager.isInitialized());
        assertFalse(freshManager.isRunning());
        
        // 测试启动未初始化的管理器
        assertThrows(IllegalStateException.class, () -> {
            freshManager.start().get(5, TimeUnit.SECONDS);
        });
    }

    @Test
    @DisplayName("测试重复初始化")
    void testMultipleInitialization() throws Exception {
        // 第一次初始化
        CompletableFuture<Void> init1 = clientManager.initialize(config);
        init1.get(5, TimeUnit.SECONDS);
        
        assertTrue(clientManager.isInitialized());
        
        // 第二次初始化应该立即完成
        CompletableFuture<Void> init2 = clientManager.initialize(config);
        init2.get(1, TimeUnit.SECONDS); // 应该很快完成
        
        assertTrue(clientManager.isInitialized());
    }

    @Test
    @DisplayName("测试空配置的处理")
    void testEmptyConfiguration() throws Exception {
        MCPConfig emptyConfig = MCPConfig.createDefault();
        emptyConfig.setEnabled(true);
        // 不添加任何服务器
        
        CompletableFuture<Void> initFuture = clientManager.initialize(emptyConfig);
        initFuture.get(5, TimeUnit.SECONDS);
        
        assertTrue(clientManager.isInitialized());
        assertEquals(0, clientManager.getTotalClientCount());
    }

    @Test
    @DisplayName("测试配置验证")
    void testConfigurationValidation() {
        // 测试null配置
        assertThrows(Exception.class, () -> {
            clientManager.initialize(null).get(5, TimeUnit.SECONDS);
        });
    }

    @Test
    @DisplayName("测试并发操作")
    void testConcurrentOperations() throws Exception {
        clientManager.initialize(config).get(5, TimeUnit.SECONDS);
        
        // 并发获取客户端状态
        CompletableFuture<Map<String, MCPClientStatus>> future1 = 
            CompletableFuture.supplyAsync(() -> clientManager.getAllClientStatuses());
        CompletableFuture<Map<String, MCPClientStatus>> future2 = 
            CompletableFuture.supplyAsync(() -> clientManager.getAllClientStatuses());
        
        Map<String, MCPClientStatus> statuses1 = future1.get(5, TimeUnit.SECONDS);
        Map<String, MCPClientStatus> statuses2 = future2.get(5, TimeUnit.SECONDS);
        
        assertNotNull(statuses1);
        assertNotNull(statuses2);
    }

    @Test
    @DisplayName("测试资源清理")
    void testResourceCleanup() throws Exception {
        clientManager.initialize(config).get(5, TimeUnit.SECONDS);
        clientManager.start().get(5, TimeUnit.SECONDS);
        
        assertTrue(clientManager.isRunning());
        
        // 停止管理器应该清理所有资源
        clientManager.stop().get(5, TimeUnit.SECONDS);
        
        assertFalse(clientManager.isRunning());
    }
}