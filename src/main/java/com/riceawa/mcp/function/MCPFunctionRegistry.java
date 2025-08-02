package com.riceawa.mcp.function;

import com.riceawa.llm.function.FunctionRegistry;
import com.riceawa.llm.function.LLMFunction;
import com.riceawa.mcp.model.MCPTool;
import com.riceawa.mcp.service.MCPService;
import com.riceawa.mcp.service.MCPToolChangeListener;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * MCP功能动态注册器
 * 负责将MCP工具动态注册为LLM函数，支持工具的注册、注销和更新
 */
public class MCPFunctionRegistry implements MCPToolChangeListener {
    
    private static MCPFunctionRegistry instance;
    
    private final MCPService mcpService;
    private final FunctionRegistry functionRegistry;
    private final MCPToolPermissionManager permissionManager;
    
    // MCP工具名称到适配器的映射
    private final Map<String, MCPFunctionAdapter> mcpAdapters = new ConcurrentHashMap<>();
    
    // 客户端名称到工具列表的映射
    private final Map<String, Set<String>> clientToolsMap = new ConcurrentHashMap<>();
    
    // 工具名称冲突处理策略
    private ConflictResolutionStrategy conflictStrategy = ConflictResolutionStrategy.PREFIX_CLIENT_NAME;
    
    // 状态监听器
    private final List<MCPRegistryListener> listeners = new CopyOnWriteArrayList<>();
    
    // 是否启用自动注册
    private volatile boolean autoRegistrationEnabled = true;
    
    public MCPFunctionRegistry(MCPService mcpService, MCPToolPermissionManager permissionManager) {
        this.mcpService = mcpService;
        this.functionRegistry = FunctionRegistry.getInstance();
        this.permissionManager = permissionManager;
    }
    
    /**
     * 获取单例实例
     */
    public static MCPFunctionRegistry getInstance() {
        return instance;
    }
    
    /**
     * 初始化单例实例
     */
    public static void initialize(MCPService mcpService, MCPToolPermissionManager permissionManager) {
        if (instance == null) {
            synchronized (MCPFunctionRegistry.class) {
                if (instance == null) {
                    instance = new MCPFunctionRegistry(mcpService, permissionManager);
                }
            }
        }
    }
    
    /**
     * 注册MCP工具为LLM函数
     */
    public boolean registerMCPTool(MCPTool tool) {
        if (tool == null || !tool.isValid()) {
            return false;
        }
        
        try {
            String originalName = tool.getFullName();
            String finalName = resolveName(originalName, tool);
            
            // 检查是否已经注册
            if (mcpAdapters.containsKey(finalName)) {
                notifyToolConflict(tool, finalName);
                return false;
            }
            
            // 创建适配器
            MCPFunctionAdapter adapter = new MCPFunctionAdapter(tool, mcpService, permissionManager);
            
            // 如果名称有变化，需要更新工具的名称
            if (!originalName.equals(finalName)) {
                // 这里可能需要克隆工具并修改名称，但MCPTool没有提供setName方法
                // 所以暂时使用原名称
            }
            
            // 注册到LLM函数注册表
            functionRegistry.registerFunction(adapter);
            
            // 记录映射
            mcpAdapters.put(finalName, adapter);
            
            // 更新客户端工具映射
            String clientName = tool.getClientName();
            if (clientName != null) {
                clientToolsMap.computeIfAbsent(clientName, k -> new HashSet<>()).add(finalName);
            }
            
            // 通知监听器
            notifyToolRegistered(tool, adapter);
            
            return true;
            
        } catch (Exception e) {
            System.err.println("注册MCP工具失败: " + tool.getName() + ", 错误: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 注销MCP工具
     */
    public boolean unregisterMCPTool(String toolName) {
        MCPFunctionAdapter adapter = mcpAdapters.remove(toolName);
        if (adapter == null) {
            return false;
        }
        
        try {
            // 从LLM函数注册表中注销
            functionRegistry.unregisterFunction(toolName);
            
            // 更新客户端工具映射
            MCPTool tool = adapter.getMCPTool();
            String clientName = tool.getClientName();
            if (clientName != null) {
                Set<String> clientTools = clientToolsMap.get(clientName);
                if (clientTools != null) {
                    clientTools.remove(toolName);
                    if (clientTools.isEmpty()) {
                        clientToolsMap.remove(clientName);
                    }
                }
            }
            
            // 通知监听器
            notifyToolUnregistered(tool, adapter);
            
            return true;
            
        } catch (Exception e) {
            System.err.println("注销MCP工具失败: " + toolName + ", 错误: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 更新MCP工具
     */
    public boolean updateMCPTool(MCPTool tool) {
        if (tool == null || !tool.isValid()) {
            return false;
        }
        
        String toolName = tool.getFullName();
        MCPFunctionAdapter oldAdapter = mcpAdapters.get(toolName);
        
        if (oldAdapter == null) {
            // 如果不存在，直接注册
            return registerMCPTool(tool);
        }
        
        try {
            // 先注销旧的
            unregisterMCPTool(toolName);
            
            // 注册新的
            boolean success = registerMCPTool(tool);
            
            if (success) {
                notifyToolUpdated(tool, mcpAdapters.get(toolName));
            }
            
            return success;
            
        } catch (Exception e) {
            System.err.println("更新MCP工具失败: " + toolName + ", 错误: " + e.getMessage());
            
            // 尝试恢复旧的适配器
            try {
                registerMCPTool(oldAdapter.getMCPTool());
            } catch (Exception restoreException) {
                System.err.println("恢复旧工具失败: " + restoreException.getMessage());
            }
            
            return false;
        }
    }
    
    /**
     * 批量注册客户端的所有工具
     */
    public int registerClientTools(String clientName, List<MCPTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return 0;
        }
        
        int successCount = 0;
        for (MCPTool tool : tools) {
            if (tool.getClientName() == null) {
                tool.setClientName(clientName);
            }
            
            if (registerMCPTool(tool)) {
                successCount++;
            }
        }
        
        return successCount;
    }
    
    /**
     * 注销客户端的所有工具
     */
    public int unregisterClientTools(String clientName) {
        Set<String> clientTools = clientToolsMap.remove(clientName);
        if (clientTools == null || clientTools.isEmpty()) {
            return 0;
        }
        
        int successCount = 0;
        for (String toolName : new HashSet<>(clientTools)) {
            if (unregisterMCPTool(toolName)) {
                successCount++;
            }
        }
        
        return successCount;
    }
    
    /**
     * 获取已注册的MCP工具适配器
     */
    public MCPFunctionAdapter getMCPAdapter(String toolName) {
        return mcpAdapters.get(toolName);
    }
    
    /**
     * 获取所有已注册的MCP工具适配器
     */
    public Collection<MCPFunctionAdapter> getAllMCPAdapters() {
        return new ArrayList<>(mcpAdapters.values());
    }
    
    /**
     * 获取指定客户端的已注册工具
     */
    public Set<String> getClientTools(String clientName) {
        Set<String> tools = clientToolsMap.get(clientName);
        return tools != null ? new HashSet<>(tools) : new HashSet<>();
    }
    
    /**
     * 检查工具是否已注册
     */
    public boolean isToolRegistered(String toolName) {
        return mcpAdapters.containsKey(toolName);
    }
    
    /**
     * 获取注册的工具数量
     */
    public int getRegisteredToolCount() {
        return mcpAdapters.size();
    }
    
    /**
     * 设置冲突解决策略
     */
    public void setConflictResolutionStrategy(ConflictResolutionStrategy strategy) {
        this.conflictStrategy = strategy != null ? strategy : ConflictResolutionStrategy.PREFIX_CLIENT_NAME;
    }
    
    /**
     * 获取冲突解决策略
     */
    public ConflictResolutionStrategy getConflictResolutionStrategy() {
        return conflictStrategy;
    }
    
    /**
     * 设置是否启用自动注册
     */
    public void setAutoRegistrationEnabled(boolean enabled) {
        this.autoRegistrationEnabled = enabled;
    }
    
    /**
     * 检查是否启用自动注册
     */
    public boolean isAutoRegistrationEnabled() {
        return autoRegistrationEnabled;
    }
    
    /**
     * 添加状态监听器
     */
    public void addListener(MCPRegistryListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * 移除状态监听器
     */
    public void removeListener(MCPRegistryListener listener) {
        listeners.remove(listener);
    }
    
    // ==================== MCPToolChangeListener实现 ====================
    
    @Override
    public void onToolsChanged(String clientName, List<MCPTool> oldTools, List<MCPTool> newTools) {
        if (!autoRegistrationEnabled) {
            return;
        }
        
        // 异步处理工具变化
        CompletableFuture.runAsync(() -> {
            try {
                // 先注销旧工具
                unregisterClientTools(clientName);
                
                // 注册新工具
                registerClientTools(clientName, newTools);
                
            } catch (Exception e) {
                System.err.println("处理工具变化失败: " + clientName + ", 错误: " + e.getMessage());
            }
        });
    }
    
    @Override
    public void onToolsAdded(String clientName, List<MCPTool> addedTools) {
        if (!autoRegistrationEnabled) {
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            registerClientTools(clientName, addedTools);
        });
    }
    
    @Override
    public void onToolsRemoved(String clientName, List<MCPTool> removedTools) {
        if (!autoRegistrationEnabled) {
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            for (MCPTool tool : removedTools) {
                unregisterMCPTool(tool.getFullName());
            }
        });
    }
    
    @Override
    public void onToolsUpdated(String clientName, List<MCPTool> updatedTools) {
        if (!autoRegistrationEnabled) {
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            for (MCPTool tool : updatedTools) {
                updateMCPTool(tool);
            }
        });
    }
    
    @Override
    public void onClientConnectionChanged(String clientName, boolean connected) {
        if (!connected) {
            // 客户端断开连接时，注销其所有工具
            unregisterClientTools(clientName);
        }
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * 解决名称冲突
     */
    private String resolveName(String originalName, MCPTool tool) {
        String baseName = originalName;
        String clientName = tool.getClientName();
        
        switch (conflictStrategy) {
            case PREFIX_CLIENT_NAME:
                if (clientName != null && !clientName.isEmpty()) {
                    baseName = clientName + "_" + originalName;
                }
                break;
                
            case SUFFIX_CLIENT_NAME:
                if (clientName != null && !clientName.isEmpty()) {
                    baseName = originalName + "_" + clientName;
                }
                break;
                
            case USE_ORIGINAL_NAME:
                // 使用原名称
                break;
                
            case AUTO_INCREMENT:
                // 自动递增数字后缀
                int counter = 1;
                String testName = baseName;
                while (functionRegistry.hasFunction(testName) || mcpAdapters.containsKey(testName)) {
                    testName = baseName + "_" + counter;
                    counter++;
                }
                baseName = testName;
                break;
        }
        
        return baseName;
    }
    
    /**
     * 通知工具注册
     */
    private void notifyToolRegistered(MCPTool tool, MCPFunctionAdapter adapter) {
        for (MCPRegistryListener listener : listeners) {
            try {
                listener.onToolRegistered(tool, adapter);
            } catch (Exception e) {
                System.err.println("通知工具注册失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 通知工具注销
     */
    private void notifyToolUnregistered(MCPTool tool, MCPFunctionAdapter adapter) {
        for (MCPRegistryListener listener : listeners) {
            try {
                listener.onToolUnregistered(tool, adapter);
            } catch (Exception e) {
                System.err.println("通知工具注销失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 通知工具更新
     */
    private void notifyToolUpdated(MCPTool tool, MCPFunctionAdapter adapter) {
        for (MCPRegistryListener listener : listeners) {
            try {
                listener.onToolUpdated(tool, adapter);
            } catch (Exception e) {
                System.err.println("通知工具更新失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 通知工具冲突
     */
    private void notifyToolConflict(MCPTool tool, String conflictingName) {
        for (MCPRegistryListener listener : listeners) {
            try {
                listener.onToolConflict(tool, conflictingName);
            } catch (Exception e) {
                System.err.println("通知工具冲突失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 冲突解决策略枚举
     */
    public enum ConflictResolutionStrategy {
        /** 使用原始名称（可能导致冲突） */
        USE_ORIGINAL_NAME,
        /** 在名称前添加客户端名称前缀 */
        PREFIX_CLIENT_NAME,
        /** 在名称后添加客户端名称后缀 */
        SUFFIX_CLIENT_NAME,
        /** 自动递增数字后缀 */
        AUTO_INCREMENT
    }
    
    /**
     * 注册状态监听器接口
     */
    public interface MCPRegistryListener {
        
        /**
         * 工具注册成功时的回调
         */
        default void onToolRegistered(MCPTool tool, MCPFunctionAdapter adapter) {}
        
        /**
         * 工具注销成功时的回调
         */
        default void onToolUnregistered(MCPTool tool, MCPFunctionAdapter adapter) {}
        
        /**
         * 工具更新成功时的回调
         */
        default void onToolUpdated(MCPTool tool, MCPFunctionAdapter adapter) {}
        
        /**
         * 工具名称冲突时的回调
         */
        default void onToolConflict(MCPTool tool, String conflictingName) {}
    }
}