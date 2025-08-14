package com.riceawa.llm.subagent;

import com.riceawa.llm.core.ConcurrencyManager;
import com.riceawa.llm.core.LLMContext;
import com.riceawa.llm.core.LLMService;
import com.riceawa.llm.logging.LogManager;
import com.riceawa.llm.config.SubAgentFrameworkConfig;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 通用子代理管理器
 * 负责所有类型子代理实例的生命周期管理、池化、负载均衡和健康监控
 */
public class SubAgentManager {
    
    private static final String LOG_PREFIX = "[SubAgentManager]";
    private static SubAgentManager instance;
    
    // 子代理池：类型 -> 代理池
    private final Map<String, SubAgentPool> agentPools;
    
    // 活跃代理：代理ID -> 代理实例
    private final Map<String, SubAgent<?, ?>> activeAgents;
    
    // 任务路由：任务ID -> 代理ID
    private final Map<String, String> taskRouting;
    
    // 配置和工厂
    private final SubAgentFrameworkConfig config;
    private final SubAgentFactory agentFactory;
    private final UniversalTaskQueue taskQueue;
    
    // 并发控制
    private final ReentrantReadWriteLock managerLock;
    private final ConcurrencyManager concurrencyManager;
    
    // 错误处理
    private final SubAgentErrorHandler errorHandler;
    
    // 健康监控和清理
    private final ScheduledExecutorService healthCheckScheduler;
    private final ScheduledExecutorService cleanupScheduler;
    
    // 统计信息
    private final AtomicLong totalAgentsCreated;
    private final AtomicLong totalAgentsDestroyed;
    private final AtomicLong totalTasksRouted;
    private final AtomicLong totalTasksCompleted;
    private final AtomicLong totalTasksFailed;
    
    // 状态管理
    private volatile boolean isRunning;
    private volatile boolean isShuttingDown;
    
    private SubAgentManager(SubAgentFrameworkConfig config, UniversalTaskQueue taskQueue) {
        this.config = config;
        this.taskQueue = taskQueue;
        this.agentFactory = SubAgentFactory.getInstance();
        this.concurrencyManager = ConcurrencyManager.getInstance();
        
        // 初始化错误处理器
        this.errorHandler = new SubAgentErrorHandler(
            config.getMaxRetries(),
            config.getBaseRetryDelayMs(),
            config.getRetryBackoffMultiplier(),
            config.getMaxRetryDelayMs()
        );
        
        // 初始化数据结构
        this.agentPools = new ConcurrentHashMap<>();
        this.activeAgents = new ConcurrentHashMap<>();
        this.taskRouting = new ConcurrentHashMap<>();
        this.managerLock = new ReentrantReadWriteLock();
        
        // 初始化统计计数器
        this.totalAgentsCreated = new AtomicLong(0);
        this.totalAgentsDestroyed = new AtomicLong(0);
        this.totalTasksRouted = new AtomicLong(0);
        this.totalTasksCompleted = new AtomicLong(0);
        this.totalTasksFailed = new AtomicLong(0);
        
        // 初始化调度器
        this.healthCheckScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SubAgent-HealthChecker");
            t.setDaemon(true);
            return t;
        });
        
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SubAgent-Cleaner");
            t.setDaemon(true);
            return t;
        });
        
        this.isRunning = false;
        this.isShuttingDown = false;
        
        LogManager.getInstance().system( LOG_PREFIX + " 子代理管理器已初始化");
    }
    
    /**
     * 初始化子代理管理器
     */
    public static synchronized void initialize(SubAgentFrameworkConfig config, UniversalTaskQueue taskQueue) {
        if (instance != null) {
            instance.shutdown();
        }
        instance = new SubAgentManager(config, taskQueue);
        instance.start();
    }
    
    /**
     * 获取管理器实例
     */
    public static SubAgentManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("SubAgentManager not initialized. Call initialize() first.");
        }
        return instance;
    }
    
    /**
     * 启动管理器
     */
    public void start() {
        if (isRunning) {
            return;
        }
        
        managerLock.writeLock().lock();
        try {
            if (isRunning) {
                return;
            }
            
            // 初始化支持的代理类型池
            initializeAgentPools();
            
            // 启动健康检查
            startHealthCheck();
            
            // 启动清理任务
            startCleanupTask();
            
            isRunning = true;
            
            LogManager.getInstance().system( LOG_PREFIX + " 子代理管理器已启动");
            
        } finally {
            managerLock.writeLock().unlock();
        }
    }
    
    /**
     * 路由任务到合适的子代理
     */
    @SuppressWarnings("unchecked")
    public <T extends SubAgentTask<R>, R extends SubAgentResult> CompletableFuture<R> routeTask(T task) {
        if (!isRunning || isShuttingDown) {
            return CompletableFuture.completedFuture(
                (R) createErrorResult("SubAgentManager is not running", 0)
            );
        }
        
        String taskType = task.getTaskType();
        totalTasksRouted.incrementAndGet();
        
        LogManager.getInstance().system( LOG_PREFIX + " 路由任务: " + task.getTaskId() + 
            ", 类型: " + taskType);
        
        // 检查是否支持该任务类型
        if (!agentFactory.isTypeSupported(taskType)) {
            LogManager.getInstance().error( LOG_PREFIX + " 不支持的任务类型: " + taskType);
            totalTasksFailed.incrementAndGet();
            return CompletableFuture.completedFuture(
                (R) createErrorResult("Unsupported task type: " + taskType, 0)
            );
        }
        
        // 获取或创建子代理
        CompletableFuture<SubAgent<T, R>> agentFuture = getOrCreateAgent(taskType, task);
        
        return agentFuture.thenCompose(agent -> {
            if (agent == null) {
                totalTasksFailed.incrementAndGet();
                return CompletableFuture.completedFuture(
                    (R) createErrorResult("Failed to get agent for task type: " + taskType, 0)
                );
            }
            
            // 记录任务路由
            taskRouting.put(task.getTaskId(), agent.getAgentId());
            
            // 执行任务
            return agent.executeTask(task)
                .handle((result, throwable) -> {
                    // 清理路由记录
                    taskRouting.remove(task.getTaskId());
                    
                    if (throwable != null) {
                        totalTasksFailed.incrementAndGet();
                        LogManager.getInstance().error( LOG_PREFIX + " 任务执行失败: " + 
                            task.getTaskId(), throwable);
                        
                        // 使用错误处理器处理错误
                        return errorHandler.handleTaskError(task, throwable, task.getRetryCount())
                            .join(); // 同步等待错误处理结果
                    } else {
                        totalTasksCompleted.incrementAndGet();
                        LogManager.getInstance().system( LOG_PREFIX + " 任务执行完成: " + 
                            task.getTaskId());
                        return result;
                    }
                })
                .whenComplete((finalResult, finalThrowable) -> {
                    // 将代理返回池中
                    returnAgentToPool(agent);
                });
        });
    }
    
    /**
     * 获取或创建子代理
     */
    @SuppressWarnings("unchecked")
    private <T extends SubAgentTask<R>, R extends SubAgentResult> CompletableFuture<SubAgent<T, R>> 
            getOrCreateAgent(String agentType, T task) {
        
        return concurrencyManager.submitRequest(() -> {
            managerLock.readLock().lock();
            try {
                SubAgentPool pool = agentPools.get(agentType);
                if (pool == null) {
                    LogManager.getInstance().error( LOG_PREFIX + " 未找到代理池: " + agentType);
                    return null;
                }
                
                // 尝试从池中获取可用代理
                SubAgent<T, R> agent = (SubAgent<T, R>) pool.borrowAgent();
                if (agent != null) {
                    LogManager.getInstance().system( LOG_PREFIX + " 从池中获取代理: " + 
                        agent.getAgentId());
                    return agent;
                }
                
                // 池中没有可用代理，创建新代理
                return createNewAgent(agentType, task);
                
            } finally {
                managerLock.readLock().unlock();
            }
        }, "get-or-create-agent-" + agentType);
    }
    
    /**
     * 创建新的子代理
     */
    @SuppressWarnings("unchecked")
    private <T extends SubAgentTask<R>, R extends SubAgentResult> SubAgent<T, R> 
            createNewAgent(String agentType, T task) {
        
        try {
            // 创建代理上下文
            SubAgentContext context = createAgentContext(task);
            
            // 创建LLM服务实例
            LLMService llmService = createLLMService(agentType, context);
            
            // 使用工厂创建代理
            SubAgent<T, R> agent = agentFactory.createAgent(agentType, llmService, context);
            
            // 初始化代理
            if (agent instanceof BaseSubAgent) {
                ((BaseSubAgent<T, R>) agent).initialize(llmService, context);
            }
            
            // 注册到活跃代理列表
            activeAgents.put(agent.getAgentId(), agent);
            totalAgentsCreated.incrementAndGet();
            
            LogManager.getInstance().system( LOG_PREFIX + " 创建新代理: " + agent.getAgentId() + 
                ", 类型: " + agentType);
            
            return agent;
            
        } catch (Exception e) {
            // 使用错误处理器处理创建错误
            SubAgentCreationException handledException = errorHandler.handleCreationError(agentType, e);
            LogManager.getInstance().error( LOG_PREFIX + " 创建代理失败: " + agentType, handledException);
            return null;
        }
    }
    
    /**
     * 创建代理上下文
     */
    private <T extends SubAgentTask<R>, R extends SubAgentResult> SubAgentContext 
            createAgentContext(T task) {
        
        // 从任务中获取现有的LLMContext（如果有）
        LLMContext existingContext = task.getLlmContext();
        
        SubAgentContext.Builder builder;
        if (existingContext != null) {
            // 基于现有LLMContext创建
            builder = SubAgentContext.builder(existingContext);
        } else {
            // 创建新的上下文
            builder = SubAgentContext.builder()
                .sessionId("subagent-" + UUID.randomUUID().toString())
                .playerName("system") // 默认系统用户
                .metadata("created_by", "SubAgentManager");
        }
        
        // 添加任务相关的元数据
        return builder
            .metadata("task_type", task.getTaskType())
            .metadata("task_id", task.getTaskId())
            .metadata("created_time", System.currentTimeMillis())
            .subAgentType(task.getTaskType())
            .subAgentId(UUID.randomUUID().toString())
            .subAgentMetadata("task_parameters", task.getParameters())
            .subAgentMetadata("task_priority", task.getPriority())
            .subAgentMetadata("max_retries", config.getMaxRetries())
            .build();
    }
    
    /**
     * 创建LLM服务实例
     */
    private LLMService createLLMService(String agentType, SubAgentContext context) {
        // 这里应该根据代理类型和配置创建相应的LLM服务
        // 暂时返回null，具体实现需要根据实际的LLM服务创建逻辑
        return null;
    }
    
    /**
     * 将代理返回到池中
     */
    private void returnAgentToPool(SubAgent<?, ?> agent) {
        if (agent == null || isShuttingDown) {
            return;
        }
        
        managerLock.readLock().lock();
        try {
            SubAgentPool pool = agentPools.get(agent.getAgentType());
            if (pool != null && agent.isAvailable()) {
                pool.returnAgent(agent);
                LogManager.getInstance().system( LOG_PREFIX + " 代理已返回池中: " + 
                    agent.getAgentId());
            } else {
                // 代理不可用或池不存在，销毁代理
                destroyAgent(agent);
            }
        } finally {
            managerLock.readLock().unlock();
        }
    }
    
    /**
     * 销毁代理
     */
    private void destroyAgent(SubAgent<?, ?> agent) {
        if (agent == null) {
            return;
        }
        
        try {
            activeAgents.remove(agent.getAgentId());
            agent.shutdown();
            totalAgentsDestroyed.incrementAndGet();
            
            LogManager.getInstance().system( LOG_PREFIX + " 代理已销毁: " + agent.getAgentId());
            
        } catch (Exception e) {
            LogManager.getInstance().error( LOG_PREFIX + " 销毁代理失败: " + 
                agent.getAgentId(), e);
        }
    }
    
    /**
     * 初始化代理池
     */
    private void initializeAgentPools() {
        Set<String> supportedTypes = agentFactory.getSupportedTypes();
        
        for (String agentType : supportedTypes) {
            SubAgentPool pool = new SubAgentPool(
                agentType,
                config.getMaxAgentsPerType(),
                config.getAgentIdleTimeoutMs()
            );
            agentPools.put(agentType, pool);
            
            LogManager.getInstance().system( LOG_PREFIX + " 初始化代理池: " + agentType);
        }
    }
    
    /**
     * 启动健康检查
     */
    private void startHealthCheck() {
        healthCheckScheduler.scheduleWithFixedDelay(
            this::performHealthCheck,
            config.getHealthCheckIntervalMs(),
            config.getHealthCheckIntervalMs(),
            TimeUnit.MILLISECONDS
        );
        
        LogManager.getInstance().system( LOG_PREFIX + " 健康检查已启动，间隔: " + 
            config.getHealthCheckIntervalMs() + "ms");
    }
    
    /**
     * 启动清理任务
     */
    private void startCleanupTask() {
        cleanupScheduler.scheduleWithFixedDelay(
            this::performCleanup,
            config.getCleanupIntervalMs(),
            config.getCleanupIntervalMs(),
            TimeUnit.MILLISECONDS
        );
        
        LogManager.getInstance().system( LOG_PREFIX + " 清理任务已启动，间隔: " + 
            config.getCleanupIntervalMs() + "ms");
    }
    
    /**
     * 执行健康检查
     */
    private void performHealthCheck() {
        if (!isRunning || isShuttingDown) {
            return;
        }
        
        try {
            managerLock.readLock().lock();
            try {
                int totalAgents = 0;
                int healthyAgents = 0;
                
                for (SubAgentPool pool : agentPools.values()) {
                    SubAgentPool.PoolStatistics stats = pool.getStatistics();
                    totalAgents += stats.getTotalAgents();
                    healthyAgents += stats.getAvailableAgents();
                }
                
                LogManager.getInstance().system( LOG_PREFIX + " 健康检查完成 - 总代理数: " + 
                    totalAgents + ", 健康代理数: " + healthyAgents);
                
            } finally {
                managerLock.readLock().unlock();
            }
        } catch (Exception e) {
            LogManager.getInstance().error( LOG_PREFIX + " 健康检查失败", e);
        }
    }
    
    /**
     * 执行清理任务
     */
    private void performCleanup() {
        if (!isRunning || isShuttingDown) {
            return;
        }
        
        try {
            managerLock.writeLock().lock();
            try {
                int cleanedAgents = 0;
                
                // 清理各个池中的空闲超时代理
                for (SubAgentPool pool : agentPools.values()) {
                    cleanedAgents += pool.cleanupIdleAgents();
                }
                
                if (cleanedAgents > 0) {
                    LogManager.getInstance().system( LOG_PREFIX + " 清理了 " + cleanedAgents + 
                        " 个空闲超时代理");
                }
                
            } finally {
                managerLock.writeLock().unlock();
            }
        } catch (Exception e) {
            LogManager.getInstance().error( LOG_PREFIX + " 清理任务失败", e);
        }
    }
    
    /**
     * 获取错误处理器统计信息
     */
    public Map<String, Integer> getErrorStatistics() {
        return errorHandler.getErrorStatistics();
    }
    
    /**
     * 清理过期的错误统计
     */
    public void cleanupErrorStatistics() {
        errorHandler.cleanupExpiredStats(config.getAgentIdleTimeoutMs());
    }
    
    /**
     * 获取管理器统计信息
     */
    public SubAgentManagerStatistics getStatistics() {
        managerLock.readLock().lock();
        try {
            Map<String, SubAgentPool.PoolStatistics> poolStats = new HashMap<>();
            for (Map.Entry<String, SubAgentPool> entry : agentPools.entrySet()) {
                poolStats.put(entry.getKey(), entry.getValue().getStatistics());
            }
            
            return new SubAgentManagerStatistics(
                totalAgentsCreated.get(),
                totalAgentsDestroyed.get(),
                totalTasksRouted.get(),
                totalTasksCompleted.get(),
                totalTasksFailed.get(),
                activeAgents.size(),
                taskRouting.size(),
                poolStats,
                isRunning,
                isShuttingDown
            );
            
        } finally {
            managerLock.readLock().unlock();
        }
    }
    
    /**
     * 检查管理器是否健康
     */
    public boolean isHealthy() {
        if (!isRunning || isShuttingDown) {
            return false;
        }
        
        managerLock.readLock().lock();
        try {
            // 检查是否有可用的代理池
            for (SubAgentPool pool : agentPools.values()) {
                if (pool.getStatistics().getTotalAgents() > 0) {
                    return true;
                }
            }
            
            // 如果没有代理但系统正在运行，也认为是健康的（可以按需创建）
            return true;
            
        } finally {
            managerLock.readLock().unlock();
        }
    }
    
    /**
     * 关闭管理器
     */
    public void shutdown() {
        if (isShuttingDown) {
            return;
        }
        
        LogManager.getInstance().system( LOG_PREFIX + " 正在关闭子代理管理器...");
        
        managerLock.writeLock().lock();
        try {
            isShuttingDown = true;
            isRunning = false;
            
            // 关闭调度器
            shutdownScheduler(healthCheckScheduler, "健康检查调度器");
            shutdownScheduler(cleanupScheduler, "清理任务调度器");
            
            // 关闭所有代理池
            for (Map.Entry<String, SubAgentPool> entry : agentPools.entrySet()) {
                try {
                    entry.getValue().shutdown();
                    LogManager.getInstance().system( LOG_PREFIX + " 代理池已关闭: " + entry.getKey());
                } catch (Exception e) {
                    LogManager.getInstance().error( LOG_PREFIX + " 关闭代理池失败: " + 
                        entry.getKey(), e);
                }
            }
            
            // 销毁所有活跃代理
            for (SubAgent<?, ?> agent : activeAgents.values()) {
                destroyAgent(agent);
            }
            
            // 清理数据结构
            agentPools.clear();
            activeAgents.clear();
            taskRouting.clear();
            
            LogManager.getInstance().system( LOG_PREFIX + " 子代理管理器已关闭");
            
        } finally {
            managerLock.writeLock().unlock();
        }
    }
    
    /**
     * 关闭调度器
     */
    private void shutdownScheduler(ScheduledExecutorService scheduler, String name) {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                LogManager.getInstance().system( LOG_PREFIX + " 强制关闭调度器: " + name);
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
            LogManager.getInstance().system( LOG_PREFIX + " 关闭调度器被中断: " + name);
        }
    }
    
    /**
     * 创建错误结果
     */
    private SubAgentResult createErrorResult(String error, long processingTime) {
        Map<String, Object> metadata = new java.util.HashMap<>();
        return new GenericSubAgentResult(false, error, processingTime, metadata);
    }
    
    // Getters
    public boolean isRunning() {
        return isRunning;
    }
    
    public boolean isShuttingDown() {
        return isShuttingDown;
    }
    
    public int getActiveAgentCount() {
        return activeAgents.size();
    }
    
    public int getActiveTaskCount() {
        return taskRouting.size();
    }
    
    public Set<String> getSupportedAgentTypes() {
        return new HashSet<>(agentPools.keySet());
    }
}