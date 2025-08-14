package com.riceawa.llm.subagent;

import com.riceawa.llm.logging.LLMLogUtils;
import com.riceawa.llm.logging.LogLevel;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.List;
import java.util.ArrayList;

/**
 * 子代理池
 * 管理特定类型的子代理实例，提供池化和负载均衡功能
 */
public class SubAgentPool {
    
    private static final String LOG_PREFIX = "[SubAgentPool]";
    
    private final String agentType;
    private final int maxPoolSize;
    private final long idleTimeoutMs;
    
    // 可用代理队列
    private final BlockingQueue<SubAgent<?, ?>> availableAgents;
    
    // 所有代理列表（用于管理和清理）
    private final List<SubAgent<?, ?>> allAgents;
    
    // 并发控制
    private final ReentrantReadWriteLock poolLock;
    
    // 统计信息
    private final AtomicInteger totalAgents;
    private final AtomicInteger availableCount;
    private final AtomicInteger borrowedCount;
    
    // 状态
    private volatile boolean isShutdown;
    
    /**
     * 构造函数
     * 
     * @param agentType 代理类型
     * @param maxPoolSize 最大池大小
     * @param idleTimeoutMs 空闲超时时间
     */
    public SubAgentPool(String agentType, int maxPoolSize, long idleTimeoutMs) {
        this.agentType = agentType;
        this.maxPoolSize = maxPoolSize;
        this.idleTimeoutMs = idleTimeoutMs;
        
        this.availableAgents = new LinkedBlockingQueue<>();
        this.allAgents = new ArrayList<>();
        this.poolLock = new ReentrantReadWriteLock();
        
        this.totalAgents = new AtomicInteger(0);
        this.availableCount = new AtomicInteger(0);
        this.borrowedCount = new AtomicInteger(0);
        
        this.isShutdown = false;
        
        LLMLogUtils.log(LogLevel.DEBUG, LOG_PREFIX + " 创建代理池: " + agentType + 
            ", 最大大小: " + maxPoolSize);
    }
    
    /**
     * 从池中借用代理
     * 
     * @return 可用的代理实例，如果没有则返回null
     */
    public SubAgent<?, ?> borrowAgent() {
        if (isShutdown) {
            return null;
        }
        
        poolLock.readLock().lock();
        try {
            SubAgent<?, ?> agent = availableAgents.poll();
            
            if (agent != null) {
                // 检查代理是否仍然可用
                if (agent.isAvailable() && !isAgentIdleTimeout(agent)) {
                    availableCount.decrementAndGet();
                    borrowedCount.incrementAndGet();
                    
                    LLMLogUtils.log(LogLevel.DEBUG, LOG_PREFIX + " 借用代理: " + 
                        agent.getAgentId() + " from pool: " + agentType);
                    
                    return agent;
                } else {
                    // 代理不可用或超时，从池中移除并销毁
                    removeAgentFromPool(agent);
                    destroyAgent(agent);
                    
                    // 递归尝试获取下一个代理
                    return borrowAgent();
                }
            }
            
            return null;
            
        } finally {
            poolLock.readLock().unlock();
        }
    }
    
    /**
     * 将代理返回到池中
     * 
     * @param agent 要返回的代理
     * @return 是否成功返回
     */
    public boolean returnAgent(SubAgent<?, ?> agent) {
        if (agent == null || isShutdown) {
            return false;
        }
        
        // 验证代理类型
        if (!agentType.equals(agent.getAgentType())) {
            LLMLogUtils.log(LogLevel.WARN, LOG_PREFIX + " 代理类型不匹配: 期望 " + 
                agentType + ", 实际 " + agent.getAgentType());
            return false;
        }
        
        poolLock.writeLock().lock();
        try {
            // 检查代理是否仍然可用
            if (!agent.isAvailable()) {
                LLMLogUtils.log(LogLevel.DEBUG, LOG_PREFIX + " 代理不可用，不返回池中: " + 
                    agent.getAgentId());
                removeAgentFromPool(agent);
                destroyAgent(agent);
                return false;
            }
            
            // 检查池是否已满
            if (availableAgents.size() >= maxPoolSize) {
                LLMLogUtils.log(LogLevel.DEBUG, LOG_PREFIX + " 池已满，销毁多余代理: " + 
                    agent.getAgentId());
                removeAgentFromPool(agent);
                destroyAgent(agent);
                return false;
            }
            
            // 将代理返回池中
            if (availableAgents.offer(agent)) {
                availableCount.incrementAndGet();
                borrowedCount.decrementAndGet();
                
                LLMLogUtils.log(LogLevel.DEBUG, LOG_PREFIX + " 代理已返回池中: " + 
                    agent.getAgentId() + " to pool: " + agentType);
                
                return true;
            } else {
                LLMLogUtils.log(LogLevel.WARN, LOG_PREFIX + " 无法将代理返回池中: " + 
                    agent.getAgentId());
                return false;
            }
            
        } finally {
            poolLock.writeLock().unlock();
        }
    }
    
    /**
     * 添加新代理到池中
     * 
     * @param agent 新代理
     * @return 是否成功添加
     */
    public boolean addAgent(SubAgent<?, ?> agent) {
        if (agent == null || isShutdown) {
            return false;
        }
        
        // 验证代理类型
        if (!agentType.equals(agent.getAgentType())) {
            LLMLogUtils.log(LogLevel.WARN, LOG_PREFIX + " 代理类型不匹配: 期望 " + 
                agentType + ", 实际 " + agent.getAgentType());
            return false;
        }
        
        poolLock.writeLock().lock();
        try {
            // 检查是否超过最大池大小
            if (totalAgents.get() >= maxPoolSize) {
                LLMLogUtils.log(LogLevel.WARN, LOG_PREFIX + " 池已达到最大大小，拒绝添加代理: " + 
                    agent.getAgentId());
                return false;
            }
            
            // 添加到所有代理列表
            allAgents.add(agent);
            totalAgents.incrementAndGet();
            
            // 如果代理可用，添加到可用队列
            if (agent.isAvailable()) {
                if (availableAgents.offer(agent)) {
                    availableCount.incrementAndGet();
                    
                    LLMLogUtils.log(LogLevel.DEBUG, LOG_PREFIX + " 新代理已添加到池中: " + 
                        agent.getAgentId() + " in pool: " + agentType);
                    
                    return true;
                } else {
                    // 添加到可用队列失败，从所有代理列表中移除
                    allAgents.remove(agent);
                    totalAgents.decrementAndGet();
                    return false;
                }
            } else {
                LLMLogUtils.log(LogLevel.WARN, LOG_PREFIX + " 代理不可用，仅添加到管理列表: " + 
                    agent.getAgentId());
                return true;
            }
            
        } finally {
            poolLock.writeLock().unlock();
        }
    }
    
    /**
     * 清理空闲超时的代理
     * 
     * @return 清理的代理数量
     */
    public int cleanupIdleAgents() {
        if (isShutdown) {
            return 0;
        }
        
        poolLock.writeLock().lock();
        try {
            List<SubAgent<?, ?>> agentsToRemove = new ArrayList<>();
            
            // 检查可用队列中的代理
            for (SubAgent<?, ?> agent : availableAgents) {
                if (isAgentIdleTimeout(agent) || !agent.isAvailable()) {
                    agentsToRemove.add(agent);
                }
            }
            
            // 移除超时或不可用的代理
            int cleanedCount = 0;
            for (SubAgent<?, ?> agent : agentsToRemove) {
                if (availableAgents.remove(agent)) {
                    availableCount.decrementAndGet();
                    cleanedCount++;
                    
                    LLMLogUtils.log(LogLevel.DEBUG, LOG_PREFIX + " 清理空闲超时代理: " + 
                        agent.getAgentId());
                }
                
                removeAgentFromPool(agent);
                destroyAgent(agent);
            }
            
            return cleanedCount;
            
        } finally {
            poolLock.writeLock().unlock();
        }
    }
    
    /**
     * 检查代理是否空闲超时
     */
    private boolean isAgentIdleTimeout(SubAgent<?, ?> agent) {
        if (agent instanceof BaseSubAgent) {
            return ((BaseSubAgent<?, ?>) agent).isIdleTimeout(idleTimeoutMs);
        }
        
        // 如果不是BaseSubAgent，使用简单的时间检查
        return System.currentTimeMillis() - agent.getLastActivityTime() > idleTimeoutMs;
    }
    
    /**
     * 从池中移除代理
     */
    private void removeAgentFromPool(SubAgent<?, ?> agent) {
        allAgents.remove(agent);
        totalAgents.decrementAndGet();
    }
    
    /**
     * 销毁代理
     */
    private void destroyAgent(SubAgent<?, ?> agent) {
        try {
            agent.shutdown();
            LLMLogUtils.log(LogLevel.DEBUG, LOG_PREFIX + " 代理已销毁: " + agent.getAgentId());
        } catch (Exception e) {
            LLMLogUtils.log(LogLevel.ERROR, LOG_PREFIX + " 销毁代理失败: " + 
                agent.getAgentId(), e);
        }
    }
    
    /**
     * 获取池统计信息
     */
    public PoolStatistics getStatistics() {
        poolLock.readLock().lock();
        try {
            return new PoolStatistics(
                agentType,
                totalAgents.get(),
                availableCount.get(),
                borrowedCount.get(),
                maxPoolSize,
                idleTimeoutMs,
                isShutdown
            );
        } finally {
            poolLock.readLock().unlock();
        }
    }
    
    /**
     * 检查池是否健康
     */
    public boolean isHealthy() {
        return !isShutdown && totalAgents.get() >= 0 && availableCount.get() >= 0;
    }
    
    /**
     * 关闭池
     */
    public void shutdown() {
        if (isShutdown) {
            return;
        }
        
        LLMLogUtils.log(LogLevel.INFO, LOG_PREFIX + " 正在关闭代理池: " + agentType);
        
        poolLock.writeLock().lock();
        try {
            isShutdown = true;
            
            // 销毁所有代理
            for (SubAgent<?, ?> agent : allAgents) {
                destroyAgent(agent);
            }
            
            // 清理数据结构
            availableAgents.clear();
            allAgents.clear();
            
            // 重置计数器
            totalAgents.set(0);
            availableCount.set(0);
            borrowedCount.set(0);
            
            LLMLogUtils.log(LogLevel.INFO, LOG_PREFIX + " 代理池已关闭: " + agentType);
            
        } finally {
            poolLock.writeLock().unlock();
        }
    }
    
    // Getters
    public String getAgentType() {
        return agentType;
    }
    
    public int getMaxPoolSize() {
        return maxPoolSize;
    }
    
    public long getIdleTimeoutMs() {
        return idleTimeoutMs;
    }
    
    public boolean isShutdown() {
        return isShutdown;
    }
    
    /**
     * 池统计信息
     */
    public static class PoolStatistics {
        private final String agentType;
        private final int totalAgents;
        private final int availableAgents;
        private final int borrowedAgents;
        private final int maxPoolSize;
        private final long idleTimeoutMs;
        private final boolean isShutdown;
        
        public PoolStatistics(String agentType, int totalAgents, int availableAgents, 
                            int borrowedAgents, int maxPoolSize, long idleTimeoutMs, 
                            boolean isShutdown) {
            this.agentType = agentType;
            this.totalAgents = totalAgents;
            this.availableAgents = availableAgents;
            this.borrowedAgents = borrowedAgents;
            this.maxPoolSize = maxPoolSize;
            this.idleTimeoutMs = idleTimeoutMs;
            this.isShutdown = isShutdown;
        }
        
        // Getters
        public String getAgentType() { return agentType; }
        public int getTotalAgents() { return totalAgents; }
        public int getAvailableAgents() { return availableAgents; }
        public int getBorrowedAgents() { return borrowedAgents; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public long getIdleTimeoutMs() { return idleTimeoutMs; }
        public boolean isShutdown() { return isShutdown; }
        
        public double getUtilizationRate() {
            return totalAgents > 0 ? (double) borrowedAgents / totalAgents : 0.0;
        }
        
        public double getAvailabilityRate() {
            return totalAgents > 0 ? (double) availableAgents / totalAgents : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "PoolStatistics{type=%s, total=%d, available=%d, borrowed=%d, " +
                "maxSize=%d, utilization=%.2f%%, availability=%.2f%%, shutdown=%s}",
                agentType, totalAgents, availableAgents, borrowedAgents, maxPoolSize,
                getUtilizationRate() * 100, getAvailabilityRate() * 100, isShutdown
            );
        }
    }
}