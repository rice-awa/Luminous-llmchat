package com.riceawa.mcp.service;

import com.riceawa.mcp.model.MCPTool;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * MCP工具变化通知管理器
 * 负责管理工具变化监听器并分发通知
 */
public class MCPToolChangeNotifier {
    
    // 监听器列表（线程安全）
    private final List<MCPToolChangeListener> listeners = new CopyOnWriteArrayList<>();
    
    // 异步通知执行器
    private final ExecutorService notificationExecutor;
    
    // 客户端最后已知的工具列表
    private final Map<String, List<MCPTool>> lastKnownTools = new HashMap<>();
    
    public MCPToolChangeNotifier() {
        this.notificationExecutor = Executors.newCachedThreadPool(
            r -> new Thread(r, "MCP-ToolChangeNotifier"));
    }
    
    /**
     * 添加工具变化监听器
     */
    public void addListener(MCPToolChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * 移除工具变化监听器
     */
    public void removeListener(MCPToolChangeListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * 清除所有监听器
     */
    public void clearListeners() {
        listeners.clear();
    }
    
    /**
     * 获取监听器数量
     */
    public int getListenerCount() {
        return listeners.size();
    }
    
    /**
     * 处理工具列表更新
     * @param clientName 客户端名称
     * @param newTools 新的工具列表
     */
    public void handleToolsUpdate(String clientName, List<MCPTool> newTools) {
        if (clientName == null || newTools == null) {
            return;
        }
        
        // 获取之前的工具列表
        List<MCPTool> oldTools = lastKnownTools.get(clientName);
        if (oldTools == null) {
            oldTools = new ArrayList<>();
        }
        
        // 更新最后已知的工具列表
        lastKnownTools.put(clientName, new ArrayList<>(newTools));
        
        // 如果工具列表有变化，通知监听器
        if (!areToolListsEqual(oldTools, newTools)) {
            notifyToolsChanged(clientName, oldTools, newTools);
            
            // 分析具体变化并发送详细通知
            analyzeAndNotifyChanges(clientName, oldTools, newTools);
        }
    }
    
    /**
     * 处理tools/list_changed通知
     * @param clientName 客户端名称
     */
    public void handleToolsListChangedNotification(String clientName) {
        // 这个方法通常由MCP客户端调用，当收到服务器的tools/list_changed通知时
        // 实际的工具列表会通过后续的listTools调用获取并通过handleToolsUpdate处理
        
        // 这里可以记录日志或执行其他预处理操作
        System.out.println("收到工具列表变化通知: " + clientName);
    }
    
    /**
     * 通知客户端连接状态变化
     */
    public void notifyClientConnectionChanged(String clientName, boolean connected) {
        if (!connected) {
            // 客户端断开连接时，清除其工具列表
            lastKnownTools.remove(clientName);
        }
        
        notificationExecutor.submit(() -> {
            for (MCPToolChangeListener listener : listeners) {
                try {
                    listener.onClientConnectionChanged(clientName, connected);
                } catch (Exception e) {
                    System.err.println("通知监听器连接状态变化失败: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * 通知工具刷新完成
     */
    public void notifyToolsRefreshCompleted(String clientName, boolean success, String errorMessage) {
        notificationExecutor.submit(() -> {
            for (MCPToolChangeListener listener : listeners) {
                try {
                    listener.onToolsRefreshCompleted(clientName, success, errorMessage);
                } catch (Exception e) {
                    System.err.println("通知监听器刷新完成失败: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * 获取客户端的最后已知工具列表
     */
    public List<MCPTool> getLastKnownTools(String clientName) {
        List<MCPTool> tools = lastKnownTools.get(clientName);
        return tools != null ? new ArrayList<>(tools) : new ArrayList<>();
    }
    
    /**
     * 获取所有客户端的最后已知工具列表
     */
    public Map<String, List<MCPTool>> getAllLastKnownTools() {
        Map<String, List<MCPTool>> result = new HashMap<>();
        for (Map.Entry<String, List<MCPTool>> entry : lastKnownTools.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return result;
    }
    
    /**
     * 关闭通知器并释放资源
     */
    public void shutdown() {
        listeners.clear();
        lastKnownTools.clear();
        
        if (notificationExecutor != null && !notificationExecutor.isShutdown()) {
            notificationExecutor.shutdown();
            try {
                if (!notificationExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    notificationExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                notificationExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * 比较两个工具列表是否相等
     */
    private boolean areToolListsEqual(List<MCPTool> list1, List<MCPTool> list2) {
        if (list1.size() != list2.size()) {
            return false;
        }
        
        // 按名称排序后比较
        List<String> names1 = list1.stream()
                .map(MCPTool::getName)
                .sorted()
                .collect(Collectors.toList());
        
        List<String> names2 = list2.stream()
                .map(MCPTool::getName)
                .sorted()
                .collect(Collectors.toList());
        
        return names1.equals(names2);
    }
    
    /**
     * 通知工具列表变化
     */
    private void notifyToolsChanged(String clientName, List<MCPTool> oldTools, List<MCPTool> newTools) {
        notificationExecutor.submit(() -> {
            for (MCPToolChangeListener listener : listeners) {
                try {
                    listener.onToolsChanged(clientName, 
                                          new ArrayList<>(oldTools), 
                                          new ArrayList<>(newTools));
                } catch (Exception e) {
                    System.err.println("通知监听器工具变化失败: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * 分析工具变化并发送详细通知
     */
    private void analyzeAndNotifyChanges(String clientName, List<MCPTool> oldTools, List<MCPTool> newTools) {
        Set<String> oldToolNames = oldTools.stream()
                .map(MCPTool::getName)
                .collect(Collectors.toSet());
        
        Set<String> newToolNames = newTools.stream()
                .map(MCPTool::getName)
                .collect(Collectors.toSet());
        
        // 找出新添加的工具
        List<MCPTool> addedTools = newTools.stream()
                .filter(tool -> !oldToolNames.contains(tool.getName()))
                .collect(Collectors.toList());
        
        // 找出被移除的工具
        List<MCPTool> removedTools = oldTools.stream()
                .filter(tool -> !newToolNames.contains(tool.getName()))
                .collect(Collectors.toList());
        
        // 找出可能被更新的工具（名称相同但内容可能不同）
        List<MCPTool> potentiallyUpdatedTools = newTools.stream()
                .filter(tool -> oldToolNames.contains(tool.getName()))
                .collect(Collectors.toList());
        
        // 发送具体的变化通知
        if (!addedTools.isEmpty()) {
            notifyToolsAdded(clientName, addedTools);
        }
        
        if (!removedTools.isEmpty()) {
            notifyToolsRemoved(clientName, removedTools);
        }
        
        if (!potentiallyUpdatedTools.isEmpty()) {
            notifyToolsUpdated(clientName, potentiallyUpdatedTools);
        }
    }
    
    /**
     * 通知工具添加
     */
    private void notifyToolsAdded(String clientName, List<MCPTool> addedTools) {
        notificationExecutor.submit(() -> {
            for (MCPToolChangeListener listener : listeners) {
                try {
                    listener.onToolsAdded(clientName, new ArrayList<>(addedTools));
                } catch (Exception e) {
                    System.err.println("通知监听器工具添加失败: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * 通知工具移除
     */
    private void notifyToolsRemoved(String clientName, List<MCPTool> removedTools) {
        notificationExecutor.submit(() -> {
            for (MCPToolChangeListener listener : listeners) {
                try {
                    listener.onToolsRemoved(clientName, new ArrayList<>(removedTools));
                } catch (Exception e) {
                    System.err.println("通知监听器工具移除失败: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * 通知工具更新
     */
    private void notifyToolsUpdated(String clientName, List<MCPTool> updatedTools) {
        notificationExecutor.submit(() -> {
            for (MCPToolChangeListener listener : listeners) {
                try {
                    listener.onToolsUpdated(clientName, new ArrayList<>(updatedTools));
                } catch (Exception e) {
                    System.err.println("通知监听器工具更新失败: " + e.getMessage());
                }
            }
        });
    }
}