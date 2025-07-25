package com.riceawa.llm.context;

import net.minecraft.entity.player.PlayerEntity;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 聊天上下文管理器，管理所有玩家的聊天上下文
 */
public class ChatContextManager {
    private static ChatContextManager instance;
    private final Map<UUID, ChatContext> contexts;
    private final ScheduledExecutorService scheduler;
    private final long contextTimeoutMs;

    private ChatContextManager() {
        this.contexts = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.contextTimeoutMs = TimeUnit.HOURS.toMillis(2); // 2小时超时
        
        // 启动清理任务
        startCleanupTask();
    }

    public static ChatContextManager getInstance() {
        if (instance == null) {
            synchronized (ChatContextManager.class) {
                if (instance == null) {
                    instance = new ChatContextManager();
                }
            }
        }
        return instance;
    }

    /**
     * 获取玩家的聊天上下文
     */
    public ChatContext getContext(UUID playerId) {
        return contexts.computeIfAbsent(playerId, ChatContext::new);
    }

    /**
     * 获取玩家的聊天上下文
     */
    public ChatContext getContext(PlayerEntity player) {
        return getContext(player.getUuid());
    }

    /**
     * 移除玩家的聊天上下文
     */
    public void removeContext(UUID playerId) {
        contexts.remove(playerId);
    }

    /**
     * 移除玩家的聊天上下文
     */
    public void removeContext(PlayerEntity player) {
        removeContext(player.getUuid());
    }

    /**
     * 清空指定玩家的聊天历史
     */
    public void clearContext(UUID playerId) {
        ChatContext context = contexts.get(playerId);
        if (context != null) {
            context.clear();
        }
    }

    /**
     * 清空指定玩家的聊天历史
     */
    public void clearContext(PlayerEntity player) {
        clearContext(player.getUuid());
    }

    /**
     * 获取活跃上下文数量
     */
    public int getActiveContextCount() {
        return contexts.size();
    }

    /**
     * 启动清理任务
     */
    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(this::cleanupExpiredContexts, 1, 1, TimeUnit.HOURS);
    }

    /**
     * 清理过期的上下文
     */
    private void cleanupExpiredContexts() {
        contexts.entrySet().removeIf(entry -> {
            ChatContext context = entry.getValue();
            return context.isExpired(contextTimeoutMs);
        });
    }

    /**
     * 关闭管理器
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        contexts.clear();
    }
}
