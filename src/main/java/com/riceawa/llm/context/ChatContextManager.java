package com.riceawa.llm.context;

import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.logging.LogManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.List;
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
     * 重置单例实例（仅用于测试）
     */
    public static void resetInstance() {
        synchronized (ChatContextManager.class) {
            if (instance != null) {
                instance.shutdown();
                instance = null;
            }
        }
    }

    /**
     * 获取玩家的聊天上下文
     */
    public ChatContext getContext(UUID playerId) {
        return contexts.computeIfAbsent(playerId, id -> {
            ChatContext context = new ChatContext(id);
            // 设置事件监听器
            context.setEventListener(new CompressionNotificationListener());
            return context;
        });
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
     * 为指定玩家创建新的会话（清空当前对话并开始新会话）
     */
    public void renewSession(UUID playerId) {
        // 创建新的ChatContext实例，这样会有新的sessionId
        ChatContext newContext = new ChatContext(playerId);
        // 设置事件监听器
        newContext.setEventListener(new CompressionNotificationListener());
        // 替换旧的context
        contexts.put(playerId, newContext);
    }

    /**
     * 为指定玩家创建新会话并复制历史消息，设置新的提示词模板
     */
    public void createNewSessionWithHistory(UUID playerId, String newTemplate) {
        ChatContext oldContext = contexts.get(playerId);
        if (oldContext == null) {
            // 如果没有旧的context，直接创建新的
            ChatContext newContext = new ChatContext(playerId);
            newContext.setCurrentPromptTemplate(newTemplate);
            newContext.setEventListener(new CompressionNotificationListener());
            contexts.put(playerId, newContext);
            return;
        }

        // 创建新的ChatContext实例
        ChatContext newContext = new ChatContext(playerId);
        newContext.setEventListener(new CompressionNotificationListener());

        // 复制历史消息
        List<LLMMessage> oldMessages = oldContext.getMessages();
        for (LLMMessage message : oldMessages) {
            newContext.addMessage(message);
        }

        // 设置新的提示词模板
        newContext.setCurrentPromptTemplate(newTemplate);

        // 替换旧的context
        contexts.put(playerId, newContext);
    }

    /**
     * 更新所有上下文的最大字符长度配置
     */
    public void updateMaxContextLength() {
        LLMChatConfig config = LLMChatConfig.getInstance();
        int newMaxContextCharacters = config.getMaxContextCharacters();

        for (ChatContext context : contexts.values()) {
            context.setMaxContextCharacters(newMaxContextCharacters);
        }
        LogManager.getInstance().system("Updated max context characters to " + newMaxContextCharacters +
            " for " + contexts.size() + " active contexts");
    }

    /**
     * 更新指定玩家的最大上下文字符长度
     */
    public void updateMaxContextLength(UUID playerId) {
        ChatContext context = contexts.get(playerId);
        if (context != null) {
            LLMChatConfig config = LLMChatConfig.getInstance();
            context.setMaxContextCharacters(config.getMaxContextCharacters());
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
     * 获取调度器用于异步任务
     */
    public ScheduledExecutorService getScheduler() {
        return scheduler;
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

    /**
     * 上下文压缩通知监听器
     */
    private class CompressionNotificationListener implements ChatContext.ContextEventListener {
        @Override
        public void onContextCompressionStarted(UUID playerId, int messagesToCompress) {
            // 检查是否启用压缩通知
            LLMChatConfig config = LLMChatConfig.getInstance();
            if (!config.isEnableCompressionNotification()) {
                return;
            }

            // 查找玩家并发送通知
            PlayerEntity player = findPlayerByUuid(playerId);
            if (player != null) {
                player.sendMessage(Text.literal("⚠️ 已达到最大上下文长度，您的之前上下文将被压缩")
                    .formatted(Formatting.YELLOW), false);

                LogManager.getInstance().chat("Compression notification sent to player " +
                    player.getName().getString() + " for " + messagesToCompress + " messages");
            }
        }

        @Override
        public void onContextCompressionStarted(UUID playerId, int messagesToCompress, PlayerEntity player) {
            // 检查是否启用压缩通知
            LLMChatConfig config = LLMChatConfig.getInstance();
            if (!config.isEnableCompressionNotification()) {
                return;
            }

            // 直接使用传入的玩家实体发送通知
            if (player != null) {
                LogManager.getInstance().chat("Compression started for player " +
                    player.getName().getString() + " for " + messagesToCompress + " messages");
            }
        }

        @Override
        public void onContextCompressionCompleted(UUID playerId, boolean success, int originalCount, int compressedCount) {
            // 检查是否启用压缩通知
            LLMChatConfig config = LLMChatConfig.getInstance();
            if (!config.isEnableCompressionNotification()) {
                return;
            }

            // 查找玩家并发送完成通知
            PlayerEntity player = findPlayerByUuid(playerId);
            if (player != null) {
                if (success) {
                    player.sendMessage(Text.literal("✅ 上下文压缩完成，对话历史已优化")
                        .formatted(Formatting.GREEN), false);
                } else {
                    player.sendMessage(Text.literal("⚠️ 上下文压缩失败，已删除部分旧消息")
                        .formatted(Formatting.YELLOW), false);
                }
            }
        }

        @Override
        public void onContextCompressionCompleted(UUID playerId, boolean success, int originalCount, int compressedCount, PlayerEntity player) {
            // 检查是否启用压缩通知
            LLMChatConfig config = LLMChatConfig.getInstance();
            if (!config.isEnableCompressionNotification()) {
                return;
            }

            // 直接使用传入的玩家实体发送通知
            if (player != null) {
                if (success) {
                    player.sendMessage(Text.literal("✅ 上下文压缩完成，对话历史已优化")
                        .formatted(Formatting.GREEN), false);
                } else {
                    player.sendMessage(Text.literal("⚠️ 上下文压缩失败，已删除部分旧消息")
                        .formatted(Formatting.YELLOW), false);
                }

                LogManager.getInstance().chat("Compression completed for player " +
                    player.getName().getString() + " - success: " + success +
                    ", original: " + originalCount + ", compressed: " + compressedCount);
            }
        }

        /**
         * 根据UUID查找在线玩家
         */
        private PlayerEntity findPlayerByUuid(UUID playerId) {
            // 遍历所有上下文，找到第一个有效的玩家来获取服务器实例
            for (ChatContext context : contexts.values()) {
                // 尝试通过其他方式获取服务器实例
                // 这是一个简化的实现，在实际使用中可能需要更好的方法
                break;
            }

            // 暂时返回null，实际的通知会在LLMChatCommand中处理
            // 这样可以避免复杂的服务器实例获取问题
            return null;
        }
    }
}
