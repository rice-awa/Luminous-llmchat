package com.riceawa.llm.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.riceawa.llm.history.ChatHistory;
import com.riceawa.llm.history.HistoryExporter;
import com.riceawa.llm.history.HistoryStatistics;
import com.riceawa.llm.logging.LogManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

/**
 * 历史记录管理命令
 */
public class HistoryCommand {
    
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(CommandManager.literal("llmhistory")
                .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK)) // 需要管理员权限
                .then(CommandManager.literal("stats")
                        .executes(context -> showPlayerStats(context, null))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(context -> showPlayerStats(context, 
                                        EntityArgumentType.getPlayer(context, "player")))))
                .then(CommandManager.literal("export")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("format", StringArgumentType.string())
                                        .suggests((context, builder) -> {
                                            builder.suggest("json");
                                            builder.suggest("csv");
                                            builder.suggest("txt");
                                            builder.suggest("html");
                                            return builder.buildFuture();
                                        })
                                        .executes(HistoryCommand::exportPlayerHistory))))
                .then(CommandManager.literal("search")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("keyword", StringArgumentType.greedyString())
                                        .executes(HistoryCommand::searchHistory))))
                .then(CommandManager.literal("clear")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(HistoryCommand::clearPlayerHistory)))
        );
    }

    /**
     * 显示玩家统计信息
     */
    private static int showPlayerStats(CommandContext<ServerCommandSource> context, ServerPlayerEntity targetPlayer) {
        ServerPlayerEntity player = targetPlayer;
        
        // 如果没有指定玩家，尝试获取执行命令的玩家
        if (player == null) {
            try {
                player = context.getSource().getPlayerOrThrow();
            } catch (Exception e) {
                context.getSource().sendFeedback(() -> 
                        Text.literal("必须指定玩家或由玩家执行此命令").formatted(Formatting.RED), false);
                return 0;
            }
        }

        UUID playerId = player.getUuid();
        String playerName = player.getName().getString();
        
        HistoryStatistics historyStats = new HistoryStatistics();
        HistoryStatistics.PlayerStatistics stats = historyStats.generatePlayerStatistics(playerId);
        
        String report = stats.generateReport();
        
        // 记录审计日志
        LogManager.getInstance().audit("Player statistics viewed", 
                java.util.Map.of(
                        "executor", context.getSource().getName(),
                        "target_player", playerName,
                        "target_player_id", playerId.toString()
                ));
        
        context.getSource().sendFeedback(() -> 
                Text.literal(report).formatted(Formatting.AQUA), false);
        
        return 1;
    }

    /**
     * 导出玩家历史记录
     */
    private static int exportPlayerHistory(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
            String formatStr = StringArgumentType.getString(context, "format");
        
        UUID playerId = player.getUuid();
        String playerName = player.getName().getString();
        
        HistoryExporter.ExportFormat format;
        try {
            format = HistoryExporter.ExportFormat.valueOf(formatStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            context.getSource().sendFeedback(() -> 
                    Text.literal("不支持的导出格式: " + formatStr).formatted(Formatting.RED), false);
            return 0;
        }
        
        HistoryExporter exporter = new HistoryExporter();
        HistoryExporter.ExportResult result = exporter.exportPlayerHistory(playerId, playerName, format);
        
        // 记录审计日志
        LogManager.getInstance().audit("Player history exported", 
                java.util.Map.of(
                        "executor", context.getSource().getName(),
                        "target_player", playerName,
                        "target_player_id", playerId.toString(),
                        "format", formatStr,
                        "success", result.isSuccess()
                ));
        
            if (result.isSuccess()) {
                context.getSource().sendFeedback(() ->
                        Text.literal("历史记录导出成功: " + result.getExportFile().getFileName())
                                .formatted(Formatting.GREEN), true);
            } else {
                context.getSource().sendFeedback(() ->
                        Text.literal("导出失败: " + result.getMessage()).formatted(Formatting.RED), false);
            }

            return result.isSuccess() ? 1 : 0;
        } catch (CommandSyntaxException e) {
            context.getSource().sendFeedback(() ->
                    Text.literal("命令语法错误: " + e.getMessage()).formatted(Formatting.RED), false);
            return 0;
        }
    }

    /**
     * 搜索历史记录
     */
    private static int searchHistory(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
            String keyword = StringArgumentType.getString(context, "keyword");
        
        UUID playerId = player.getUuid();
        String playerName = player.getName().getString();
        
        var sessions = ChatHistory.getInstance().searchHistory(playerId, keyword);
        
        // 记录审计日志
        LogManager.getInstance().audit("Player history searched", 
                java.util.Map.of(
                        "executor", context.getSource().getName(),
                        "target_player", playerName,
                        "target_player_id", playerId.toString(),
                        "keyword", keyword,
                        "results_count", sessions.size()
                ));
        
        if (sessions.isEmpty()) {
            context.getSource().sendFeedback(() -> 
                    Text.literal("没有找到包含关键词 \"" + keyword + "\" 的历史记录")
                            .formatted(Formatting.YELLOW), false);
            return 0;
        }
        
        StringBuilder result = new StringBuilder();
        result.append("=== 搜索结果 ===\n");
        result.append("玩家: ").append(playerName).append("\n");
        result.append("关键词: ").append(keyword).append("\n");
        result.append("找到 ").append(sessions.size()).append(" 个会话\n\n");
        
        int count = 0;
        for (var session : sessions) {
            if (count >= 5) { // 限制显示数量
                result.append("... 还有 ").append(sessions.size() - count).append(" 个结果\n");
                break;
            }
            
            result.append("会话 ").append(count + 1).append(":\n");
            result.append("  标题: ").append(session.getDisplayTitle()).append("\n");
            result.append("  时间: ").append(session.getFormattedTimestamp()).append("\n");
            result.append("  模板: ").append(session.getPromptTemplate()).append("\n");
            result.append("  消息数: ").append(session.getMessages().size()).append("\n");
            result.append("\n");
            count++;
        }
        
            context.getSource().sendFeedback(() ->
                    Text.literal(result.toString()).formatted(Formatting.AQUA), false);

            return 1;
        } catch (CommandSyntaxException e) {
            context.getSource().sendFeedback(() ->
                    Text.literal("命令语法错误: " + e.getMessage()).formatted(Formatting.RED), false);
            return 0;
        }
    }

    /**
     * 清除玩家历史记录
     */
    private static int clearPlayerHistory(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
        
        UUID playerId = player.getUuid();
        String playerName = player.getName().getString();
        
        // 记录审计日志（在删除之前）
        LogManager.getInstance().audit("Player history cleared", 
                java.util.Map.of(
                        "executor", context.getSource().getName(),
                        "target_player", playerName,
                        "target_player_id", playerId.toString()
                ));
        
            ChatHistory.getInstance().clearPlayerHistory(playerId);

            context.getSource().sendFeedback(() ->
                    Text.literal("已清除玩家 " + playerName + " 的所有历史记录")
                            .formatted(Formatting.GREEN), true);

            return 1;
        } catch (CommandSyntaxException e) {
            context.getSource().sendFeedback(() ->
                    Text.literal("命令语法错误: " + e.getMessage()).formatted(Formatting.RED), false);
            return 0;
        }
    }
}
