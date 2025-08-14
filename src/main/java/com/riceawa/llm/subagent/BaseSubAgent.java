package com.riceawa.llm.subagent;

import com.riceawa.llm.core.LLMService;
import com.riceawa.llm.core.LLMContext;
import com.riceawa.llm.logging.LogManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 子代理抽象基类
 * 提供所有子代理的通用功能实现
 * 
 * @param <T> 任务类型
 * @param <R> 结果类型
 */
public abstract class BaseSubAgent<T extends SubAgentTask<R>, R extends SubAgentResult> 
    implements SubAgent<T, R> {
    
    protected final String agentId;
    protected final String agentType;
    protected final long createdTime;
    protected final AtomicLong lastActivityTime;
    
    protected volatile SubAgentStatus status;
    protected LLMService llmService;
    protected SubAgentContext context;
    
    // 资源管理
    protected SubAgentResourceManager resourceManager;
    protected long allocatedMemoryBytes = 0;
    protected int allocatedConnections = 0;
    
    /**
     * 构造函数
     * 
     * @param agentType 代理类型
     */
    protected BaseSubAgent(String agentType) {
        this.agentId = UUID.randomUUID().toString();
        this.agentType = agentType;
        this.createdTime = System.currentTimeMillis();
        this.lastActivityTime = new AtomicLong(System.currentTimeMillis());
        this.status = SubAgentStatus.INITIALIZING;
        
        // 获取资源管理器实例
        try {
            this.resourceManager = SubAgentResourceManager.getInstance();
        } catch (IllegalStateException e) {
            // 资源管理器未初始化，使用null
            this.resourceManager = null;
        }
        
        // 记录代理创建日志
        LogManager.getInstance().system("Created sub-agent: " + agentType + " with ID: " + agentId);
    }
    
    @Override
    public CompletableFuture<R> executeTask(T task) {
        updateLastActivity();
        
        // 检查代理状态
        if (status != SubAgentStatus.IDLE) {
            return CompletableFuture.completedFuture(
                createErrorResult("Sub-agent is not available, current status: " + status, 0)
            );
        }
        
        // 检查任务是否已超时
        if (task.isTimeout()) {
            return CompletableFuture.completedFuture(
                createErrorResult("Task already timed out", 0)
            );
        }
        
        // 标记代理为忙碌状态
        status = SubAgentStatus.BUSY;
        task.markStarted();
        
        LogManager.getInstance().system("Sub-agent " + agentId + " started executing task: " + task.getTaskId());
        
        return performExecution(task)
            .whenComplete((result, throwable) -> {
                updateLastActivity();
                
                if (throwable != null) {
                    // 执行出现异常
                    LogManager.getInstance().error("Sub-agent " + agentId + " execution failed for task: " + 
                        task.getTaskId(), throwable);
                    
                    task.markFailed(throwable.getMessage());
                    
                    // 通知回调
                    if (task.getCallback() != null) {
                        task.getCallback().onFailure(task.getTaskId(), throwable.getMessage(), throwable);
                    }
                } else {
                    // 执行成功
                    LogManager.getInstance().system("Sub-agent " + agentId + " completed task: " + 
                        task.getTaskId() + " in " + task.getExecutionTime() + "ms");
                    
                    task.markCompleted(result);
                    
                    // 通知回调
                    if (task.getCallback() != null) {
                        task.getCallback().onSuccess(task.getTaskId(), result);
                    }
                }
                
                // 恢复空闲状态
                if (status == SubAgentStatus.BUSY) {
                    status = SubAgentStatus.IDLE;
                }
            });
    }
    
    /**
     * 执行具体任务，由子类实现
     * 
     * @param task 要执行的任务
     * @return 异步执行结果
     */
    protected abstract CompletableFuture<R> performExecution(T task);
    
    /**
     * 创建结果对象，由子类实现
     * 
     * @return 结果对象
     */
    protected abstract R createResult();
    
    /**
     * 创建错误结果，由子类实现
     * 
     * @param error 错误信息
     * @param processingTime 处理时间
     * @return 错误结果
     */
    protected abstract R createErrorResult(String error, long processingTime);
    
    /**
     * 配置代理，由子类实现
     * 
     * @param task 任务对象
     */
    protected abstract void configureAgent(T task);
    
    /**
     * 初始化代理
     * 
     * @param llmService LLM服务
     * @param context 代理上下文
     */
    public void initialize(LLMService llmService, SubAgentContext context) {
        this.llmService = llmService;
        this.context = context;
        this.status = SubAgentStatus.IDLE;
        
        // 注册资源使用
        if (resourceManager != null) {
            resourceManager.registerResourceUsage(agentId, agentType, allocatedMemoryBytes, allocatedConnections);
        }
        
        LogManager.getInstance().system("Sub-agent " + agentId + " initialized successfully");
    }
    
    @Override
    public boolean isAvailable() {
        return status == SubAgentStatus.IDLE && llmService != null && llmService.isAvailable();
    }
    
    @Override
    public void shutdown() {
        LogManager.getInstance().system("Shutting down sub-agent: " + agentId);
        
        status = SubAgentStatus.SHUTTING_DOWN;
        
        try {
            // 执行清理逻辑
            performCleanup();
        } catch (Exception e) {
            LogManager.getInstance().error("Error during sub-agent cleanup: " + agentId, e);
        } finally {
            // 释放资源
            if (resourceManager != null) {
                resourceManager.releaseResources(agentId);
            }
            
            status = SubAgentStatus.SHUTDOWN;
            LogManager.getInstance().system("Sub-agent shutdown completed: " + agentId);
        }
    }
    
    /**
     * 执行清理逻辑，由子类重写
     */
    protected void performCleanup() {
        // 默认空实现
    }
    
    /**
     * 更新最后活动时间
     */
    protected void updateLastActivity() {
        lastActivityTime.set(System.currentTimeMillis());
    }
    
    /**
     * 更新资源使用情况
     */
    protected void updateResourceUsage(long memoryBytes, int connections) {
        this.allocatedMemoryBytes = memoryBytes;
        this.allocatedConnections = connections;
        
        if (resourceManager != null) {
            resourceManager.updateResourceUsage(agentId, memoryBytes, connections);
        }
    }
    
    /**
     * 获取已分配的内存字节数
     */
    public long getAllocatedMemoryBytes() {
        return allocatedMemoryBytes;
    }
    
    /**
     * 获取已分配的连接数
     */
    public int getAllocatedConnections() {
        return allocatedConnections;
    }
    
    /**
     * 检查代理是否空闲超时
     * 
     * @param timeoutMs 超时时间（毫秒）
     * @return 是否超时
     */
    public boolean isIdleTimeout(long timeoutMs) {
        if (status != SubAgentStatus.IDLE) {
            return false;
        }
        return System.currentTimeMillis() - lastActivityTime.get() > timeoutMs;
    }
    
    // Getters
    @Override
    public String getAgentId() {
        return agentId;
    }
    
    @Override
    public String getAgentType() {
        return agentType;
    }
    
    @Override
    public SubAgentStatus getStatus() {
        return status;
    }
    
    @Override
    public long getCreatedTime() {
        return createdTime;
    }
    
    @Override
    public long getLastActivityTime() {
        return lastActivityTime.get();
    }
    
    /**
     * 获取代理上下文
     * 
     * @return 代理上下文
     */
    public SubAgentContext getContext() {
        return context;
    }
    
    /**
     * 获取LLM服务
     * 
     * @return LLM服务
     */
    public LLMService getLlmService() {
        return llmService;
    }
}