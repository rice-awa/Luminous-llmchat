package com.riceawa.llm.context;

import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.logging.LogManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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
