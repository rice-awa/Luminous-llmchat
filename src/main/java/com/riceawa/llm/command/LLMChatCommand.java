package com.riceawa.llm.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.config.Provider;

import com.riceawa.llm.context.ChatContext;
import com.riceawa.llm.context.ChatContextManager;
import com.riceawa.llm.core.*;
import com.riceawa.llm.function.FunctionRegistry;
import com.riceawa.llm.function.LLMFunction;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.riceawa.llm.history.ChatHistory;
import com.riceawa.llm.history.ChatHistory.ChatSession;
import com.riceawa.llm.logging.LogManager;
import com.riceawa.llm.service.LLMServiceManager;
import com.riceawa.llm.template.PromptTemplate;
import com.riceawa.llm.template.PromptTemplateManager;
import com.riceawa.llm.template.TemplateEditor;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * LLM聊天命令处理器
 */
public class LLMChatCommand {
    private static final Gson gson = new Gson();

    /**
     * 检查是否应该广播指定玩家的AI聊天
     */
    private static boolean shouldBroadcast(LLMChatConfig config, String playerName) {
        if (!config.isEnableBroadcast()) {
            return false;
        }

        Set<String> broadcastPlayers = config.getBroadcastPlayers();
        // 如果广播列表为空，则广播所有玩家（保持向后兼容）
        // 如果列表不为空，则只广播列表中的玩家
        return broadcastPlayers.isEmpty() || broadcastPlayers.contains(playerName);
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        // 注册日志管理命令
        LogCommand.register(dispatcher, registryAccess);

        // 注册历史记录管理命令
        HistoryCommand.register(dispatcher, registryAccess);
        dispatcher.register(CommandManager.literal("llmchat")
                .then(CommandManager.argument("message", StringArgumentType.greedyString())
                        .executes(LLMChatCommand::handleChatMessage))
                .then(CommandManager.literal("clear")
                        .executes(LLMChatCommand::handleClearHistory))
                .then(CommandManager.literal("resume")
                        .executes(LLMChatCommand::handleResume)
                        .then(CommandManager.literal("list")
                                .executes(LLMChatCommand::handleResumeList))
                        .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                                .executes(LLMChatCommand::handleResumeById)))
                .then(CommandManager.literal("template")
                        .then(CommandManager.literal("list")
                                .executes(LLMChatCommand::handleListTemplates))
                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("template", StringArgumentType.word())
                                        .executes(LLMChatCommand::handleSetTemplate)))
                        .then(CommandManager.literal("show")
                                .then(CommandManager.argument("template", StringArgumentType.word())
                                        .executes(LLMChatCommand::handleShowTemplate)))
                        .then(CommandManager.literal("edit")
                                .then(CommandManager.argument("template", StringArgumentType.word())
                                        .executes(LLMChatCommand::handleEditTemplate))
                                .then(CommandManager.literal("name")
                                        .then(CommandManager.argument("name", StringArgumentType.greedyString())
                                                .executes(LLMChatCommand::handleEditTemplateName)))
                                .then(CommandManager.literal("desc")
                                        .then(CommandManager.argument("description", StringArgumentType.greedyString())
                                                .executes(LLMChatCommand::handleEditTemplateDesc)))
                                .then(CommandManager.literal("system")
                                        .then(CommandManager.argument("prompt", StringArgumentType.greedyString())
                                                .executes(LLMChatCommand::handleEditTemplateSystem)))
                                .then(CommandManager.literal("prefix")
                                        .then(CommandManager.argument("prefix", StringArgumentType.greedyString())
                                                .executes(LLMChatCommand::handleEditTemplatePrefix)))
                                .then(CommandManager.literal("suffix")
                                        .then(CommandManager.argument("suffix", StringArgumentType.greedyString())
                                                .executes(LLMChatCommand::handleEditTemplateSuffix))))
                        .then(CommandManager.literal("create")
                                .then(CommandManager.argument("template", StringArgumentType.word())
                                        .executes(LLMChatCommand::handleCreateTemplate)))
                        .then(CommandManager.literal("var")
                                .then(CommandManager.literal("list")
                                        .executes(LLMChatCommand::handleListTemplateVars))
                                .then(CommandManager.literal("set")
                                        .then(CommandManager.argument("name", StringArgumentType.word())
                                                .then(CommandManager.argument("value", StringArgumentType.greedyString())
                                                        .executes(LLMChatCommand::handleSetTemplateVar))))
                                .then(CommandManager.literal("remove")
                                        .then(CommandManager.argument("name", StringArgumentType.word())
                                                .executes(LLMChatCommand::handleRemoveTemplateVar))))
                        .then(CommandManager.literal("preview")
                                .executes(LLMChatCommand::handlePreviewTemplate))
                        .then(CommandManager.literal("save")
                                .executes(LLMChatCommand::handleSaveTemplate))
                        .then(CommandManager.literal("cancel")
                                .executes(LLMChatCommand::handleCancelTemplate))
                        .then(CommandManager.literal("copy")
                                .then(CommandManager.argument("from", StringArgumentType.word())
                                        .then(CommandManager.argument("to", StringArgumentType.word())
                                                .executes(LLMChatCommand::handleCopyTemplate))))
                        .then(CommandManager.literal("help")
                                .executes(LLMChatCommand::handleTemplateHelp)))

                .then(CommandManager.literal("provider")
                        .then(CommandManager.literal("list")
                                .executes(LLMChatCommand::handleListProviders))
                        .then(CommandManager.literal("switch")
                                .then(CommandManager.argument("provider", StringArgumentType.word())
                                        .executes(LLMChatCommand::handleSwitchProvider)))
                        .then(CommandManager.literal("help")
                                .executes(LLMChatCommand::handleProviderHelp)))
                .then(CommandManager.literal("model")
                        .then(CommandManager.literal("list")
                                .executes(LLMChatCommand::handleListModels)
                                .then(CommandManager.argument("provider", StringArgumentType.word())
                                        .executes(LLMChatCommand::handleListModelsForProvider)))
                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("model", StringArgumentType.word())
                                        .executes(LLMChatCommand::handleSetCurrentModel)))
                        .then(CommandManager.literal("help")
                                .executes(LLMChatCommand::handleModelHelp)))
                .then(CommandManager.literal("broadcast")
                        .then(CommandManager.literal("enable")
                                .executes(LLMChatCommand::handleEnableBroadcast))
                        .then(CommandManager.literal("disable")
                                .executes(LLMChatCommand::handleDisableBroadcast))
                        .then(CommandManager.literal("status")
                                .executes(LLMChatCommand::handleBroadcastStatus))
                        .then(CommandManager.literal("player")
                                .then(CommandManager.literal("add")
                                        .then(CommandManager.argument("player", StringArgumentType.word())
                                                .executes(LLMChatCommand::handleAddBroadcastPlayer)))
                                .then(CommandManager.literal("remove")
                                        .then(CommandManager.argument("player", StringArgumentType.word())
                                                .executes(LLMChatCommand::handleRemoveBroadcastPlayer)))
                                .then(CommandManager.literal("list")
                                        .executes(LLMChatCommand::handleListBroadcastPlayers))
                                .then(CommandManager.literal("clear")
                                        .executes(LLMChatCommand::handleClearBroadcastPlayers))
                                .then(CommandManager.literal("help")
                                        .executes(LLMChatCommand::handleBroadcastPlayerHelp)))
                        .then(CommandManager.literal("help")
                                .executes(LLMChatCommand::handleBroadcastHelp)))
                .then(CommandManager.literal("reload")
                        .executes(LLMChatCommand::handleReload))
                .then(CommandManager.literal("setup")
                        .executes(LLMChatCommand::handleSetup))
                .then(CommandManager.literal("stats")
                        .executes(LLMChatCommand::handleStats))
                .then(CommandManager.literal("help")
                        .executes(LLMChatCommand::handleHelp))
        );
    }

    /**
     * 处理聊天消息
     */
    private static int handleChatMessage(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();
        
        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        String message = StringArgumentType.getString(context, "message");

        // 记录聊天请求
        LogManager.getInstance().chat("Chat request from player: " + player.getName().getString() +
                ", message: " + message);



        // 异步处理聊天请求
        CompletableFuture.runAsync(() -> {
            try {
                processChatMessage(player, message);
            } catch (Exception e) {
                LogManager.getInstance().error("Error processing chat message from " +
                        player.getName().getString(), e);
                player.sendMessage(Text.literal("处理消息时发生错误: " + e.getMessage()).formatted(Formatting.RED), false);
            }
        });

        return 1;
    }

    /**
     * 处理清空历史记录
     */
    private static int handleClearHistory(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        // 使用renewSession而不是clearContext，这样会创建新的会话ID
        ChatContextManager.getInstance().renewSession(player.getUuid());
        player.sendMessage(Text.literal("聊天历史已清空，开始新的对话会话").formatted(Formatting.GREEN), false);

        return 1;
    }

    /**
     * 处理恢复上次对话
     */
    private static int handleResume(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        try {
            ChatHistory chatHistory = ChatHistory.getInstance();
            List<ChatSession> sessions = chatHistory.loadPlayerHistory(player.getUuid());

            if (sessions == null || sessions.isEmpty()) {
                player.sendMessage(Text.literal("没有找到历史对话记录").formatted(Formatting.YELLOW), false);
                return 1;
            }

            // 获取最近的会话
            ChatSession lastSession = sessions.get(sessions.size() - 1);

            // 获取当前上下文
            ChatContextManager contextManager = ChatContextManager.getInstance();
            ChatContext currentContext = contextManager.getContext(player);

            // 检查当前上下文是否为空
            if (currentContext.getMessageCount() > 0) {
                player.sendMessage(Text.literal("当前对话不为空，请先使用 /llmchat clear 清空当前对话")
                    .formatted(Formatting.RED), false);
                return 0;
            }

            // 恢复历史对话
            List<LLMMessage> historyMessages = lastSession.getMessages();
            if (historyMessages != null && !historyMessages.isEmpty()) {
                // 将历史消息添加到当前上下文
                for (LLMMessage message : historyMessages) {
                    currentContext.addMessage(message);
                }

                // 设置提示词模板
                if (lastSession.getPromptTemplate() != null && !lastSession.getPromptTemplate().isEmpty()) {
                    currentContext.setCurrentPromptTemplate(lastSession.getPromptTemplate());
                }

                player.sendMessage(Text.literal("✅ 已恢复上次对话，共 " + historyMessages.size() + " 条消息")
                    .formatted(Formatting.GREEN), false);

                // 显示消息预览
                showMessagePreview(player, historyMessages, "上次对话");

                LogManager.getInstance().chat("Player " + player.getName().getString() +
                    " resumed chat session with " + historyMessages.size() + " messages");
            } else {
                player.sendMessage(Text.literal("历史对话记录为空").formatted(Formatting.YELLOW), false);
            }

        } catch (Exception e) {
            player.sendMessage(Text.literal("恢复对话时发生错误: " + e.getMessage())
                .formatted(Formatting.RED), false);
            LogManager.getInstance().error("Error resuming chat for player " + player.getName().getString(), e);
        }

        return 1;
    }

    /**
     * 处理列出历史对话记录
     */
    private static int handleResumeList(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        try {
            ChatHistory chatHistory = ChatHistory.getInstance();
            List<ChatSession> sessions = chatHistory.loadPlayerHistory(player.getUuid());

            if (sessions == null || sessions.isEmpty()) {
                player.sendMessage(Text.literal("没有找到历史对话记录").formatted(Formatting.YELLOW), false);
                return 1;
            }

            // 构建历史记录列表显示
            StringBuilder message = new StringBuilder();
            message.append("=== 历史对话记录 ===\n");
            message.append("共找到 ").append(sessions.size()).append(" 个会话\n\n");

            // 按时间倒序显示（最新的在前面）
            for (int i = sessions.size() - 1; i >= 0; i--) {
                ChatSession session = sessions.get(i);
                int displayIndex = sessions.size() - i; // 最新的是#1

                message.append("#").append(displayIndex).append(" ");
                message.append(session.getDisplayTitle()).append("\n");
                message.append("   时间: ").append(session.getFormattedTimestamp()).append("\n");
                message.append("   消息数: ").append(session.getMessages().size()).append(" 条");
                if (session.getPromptTemplate() != null && !session.getPromptTemplate().equals("default")) {
                    message.append("   模板: ").append(session.getPromptTemplate());
                }
                message.append("\n\n");
            }

            message.append("使用 /llmchat resume <数字> 来恢复指定对话");

            player.sendMessage(Text.literal(message.toString()).formatted(Formatting.AQUA), false);

            LogManager.getInstance().chat("Player " + player.getName().getString() +
                " listed " + sessions.size() + " chat sessions");

        } catch (Exception e) {
            player.sendMessage(Text.literal("获取历史记录时发生错误: " + e.getMessage())
                .formatted(Formatting.RED), false);
            LogManager.getInstance().error("Error listing chat history for player " + player.getName().getString(), e);
        }

        return 1;
    }

    /**
     * 处理通过ID恢复指定对话
     */
    private static int handleResumeById(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        int sessionId = IntegerArgumentType.getInteger(context, "id");

        try {
            ChatHistory chatHistory = ChatHistory.getInstance();
            ChatSession targetSession = chatHistory.getSessionByIndex(player.getUuid(), sessionId);

            if (targetSession == null) {
                player.sendMessage(Text.literal("没有找到ID为 #" + sessionId + " 的对话记录")
                    .formatted(Formatting.RED), false);
                return 0;
            }

            // 获取当前上下文
            ChatContextManager contextManager = ChatContextManager.getInstance();
            ChatContext currentContext = contextManager.getContext(player);

            // 检查当前上下文是否为空
            if (currentContext.getMessageCount() > 0) {
                player.sendMessage(Text.literal("当前对话不为空，请先使用 /llmchat clear 清空当前对话")
                    .formatted(Formatting.RED), false);
                return 0;
            }

            // 恢复指定的历史对话
            List<LLMMessage> historyMessages = targetSession.getMessages();
            if (historyMessages != null && !historyMessages.isEmpty()) {
                // 将历史消息添加到当前上下文
                for (LLMMessage message : historyMessages) {
                    currentContext.addMessage(message);
                }

                // 设置提示词模板
                if (targetSession.getPromptTemplate() != null && !targetSession.getPromptTemplate().isEmpty()) {
                    currentContext.setCurrentPromptTemplate(targetSession.getPromptTemplate());
                }

                player.sendMessage(Text.literal("✅ 已恢复对话 #" + sessionId + ": " + targetSession.getDisplayTitle() +
                    "，共 " + historyMessages.size() + " 条消息").formatted(Formatting.GREEN), false);

                // 显示消息预览
                showMessagePreview(player, historyMessages, "对话 #" + sessionId);

                LogManager.getInstance().chat("Player " + player.getName().getString() +
                    " resumed chat session #" + sessionId + " with " + historyMessages.size() + " messages");
            } else {
                player.sendMessage(Text.literal("指定的对话记录为空").formatted(Formatting.YELLOW), false);
            }

        } catch (Exception e) {
            player.sendMessage(Text.literal("恢复对话时发生错误: " + e.getMessage())
                .formatted(Formatting.RED), false);
            LogManager.getInstance().error("Error resuming chat by ID for player " + player.getName().getString(), e);
        }

        return 1;
    }

    /**
     * 处理列出模板
     */
    private static int handleListTemplates(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();
        
        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        PromptTemplateManager templateManager = PromptTemplateManager.getInstance();
        ChatContext chatContext = ChatContextManager.getInstance().getContext(player);
        
        player.sendMessage(Text.literal("可用的提示词模板:").formatted(Formatting.YELLOW), false);

        for (PromptTemplate template : templateManager.getEnabledTemplates()) {
            String prefix = template.getId().equals(chatContext.getCurrentPromptTemplate()) ? "* " : "  ";
            player.sendMessage(Text.literal(prefix + template.getId() + " - " + template.getName())
                    .formatted(template.getId().equals(chatContext.getCurrentPromptTemplate()) ?
                            Formatting.GREEN : Formatting.WHITE), false);
        }
        
        return 1;
    }

    /**
     * 处理设置模板
     */
    private static int handleSetTemplate(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        String templateId = StringArgumentType.getString(context, "template");
        PromptTemplateManager templateManager = PromptTemplateManager.getInstance();

        if (!templateManager.hasTemplate(templateId)) {
            player.sendMessage(Text.literal("模板不存在: " + templateId).formatted(Formatting.RED), false);
            return 0;
        }

        // 获取当前上下文以检查是否有历史消息
        ChatContextManager contextManager = ChatContextManager.getInstance();
        ChatContext currentContext = contextManager.getContext(player);

        if (currentContext.getMessageCount() > 0) {
            // 如果有历史消息，创建新会话并复制历史
            contextManager.createNewSessionWithHistory(player.getUuid(), templateId);
            player.sendMessage(Text.literal("已切换到模板并创建新会话，历史消息已复制").formatted(Formatting.GREEN), false);
        } else {
            // 如果没有历史消息，直接设置模板
            currentContext.setCurrentPromptTemplate(templateId);
            player.sendMessage(Text.literal("已切换到模板").formatted(Formatting.GREEN), false);
        }

        PromptTemplate template = templateManager.getTemplate(templateId);
        player.sendMessage(Text.literal("当前模板: " + template.getName()).formatted(Formatting.GRAY), false);

        return 1;
    }

    /**
     * 处理显示模板详情
     */
    private static int handleShowTemplate(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        String templateId = StringArgumentType.getString(context, "template");
        PromptTemplateManager templateManager = PromptTemplateManager.getInstance();

        if (!templateManager.hasTemplate(templateId)) {
            player.sendMessage(Text.literal("模板不存在: " + templateId).formatted(Formatting.RED), false);
            return 0;
        }

        PromptTemplate template = templateManager.getTemplate(templateId);

        player.sendMessage(Text.literal("").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("=== 模板详情 ===").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal("ID: " + template.getId()).formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("名称: " + template.getName()).formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("描述: " + template.getDescription()).formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("状态: " + (template.isEnabled() ? "启用" : "禁用")).formatted(
            template.isEnabled() ? Formatting.GREEN : Formatting.RED), false);
        player.sendMessage(Text.literal("").formatted(Formatting.GRAY), false);

        player.sendMessage(Text.literal("📋 系统提示词:").formatted(Formatting.YELLOW), false);
        String systemPrompt = template.getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            String[] lines = systemPrompt.split("\n");
            for (String line : lines) {
                if (line.length() > 80) {
                    for (int i = 0; i < line.length(); i += 80) {
                        int end = Math.min(i + 80, line.length());
                        player.sendMessage(Text.literal("  " + line.substring(i, end)).formatted(Formatting.WHITE), false);
                    }
                } else {
                    player.sendMessage(Text.literal("  " + line).formatted(Formatting.WHITE), false);
                }
            }
        } else {
            player.sendMessage(Text.literal("  (未设置)").formatted(Formatting.GRAY), false);
        }

        player.sendMessage(Text.literal("").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("📝 用户消息前缀:").formatted(Formatting.YELLOW), false);
        String prefix = template.getUserPromptPrefix();
        if (prefix != null && !prefix.trim().isEmpty()) {
            player.sendMessage(Text.literal("  " + prefix).formatted(Formatting.WHITE), false);
        } else {
            player.sendMessage(Text.literal("  (未设置)").formatted(Formatting.GRAY), false);
        }

        player.sendMessage(Text.literal("📝 用户消息后缀:").formatted(Formatting.YELLOW), false);
        String suffix = template.getUserPromptSuffix();
        if (suffix != null && !suffix.trim().isEmpty()) {
            player.sendMessage(Text.literal("  " + suffix).formatted(Formatting.WHITE), false);
        } else {
            player.sendMessage(Text.literal("  (未设置)").formatted(Formatting.GRAY), false);
        }

        player.sendMessage(Text.literal("").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("🔧 变量 (" + template.getVariables().size() + "个):").formatted(Formatting.YELLOW), false);
        if (!template.getVariables().isEmpty()) {
            for (java.util.Map.Entry<String, String> entry : template.getVariables().entrySet()) {
                player.sendMessage(Text.literal("  {{" + entry.getKey() + "}} = " + entry.getValue()).formatted(Formatting.AQUA), false);
            }
        } else {
            player.sendMessage(Text.literal("  (无变量)").formatted(Formatting.GRAY), false);
        }

        player.sendMessage(Text.literal("").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("💡 使用 /llmchat template edit " + templateId + " 来编辑此模板").formatted(Formatting.GRAY), false);

        return 1;
    }

    /**
     * 处理开始编辑模板
     */
    private static int handleEditTemplate(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        String templateId = StringArgumentType.getString(context, "template");
        TemplateEditor editor = TemplateEditor.getInstance();

        editor.startEditSession(player, templateId, false);
        return 1;
    }

    /**
     * 处理创建新模板
     */
    private static int handleCreateTemplate(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        String templateId = StringArgumentType.getString(context, "template");
        PromptTemplateManager templateManager = PromptTemplateManager.getInstance();

        if (templateManager.hasTemplate(templateId)) {
            player.sendMessage(Text.literal("模板已存在: " + templateId + "，请使用 edit 命令编辑").formatted(Formatting.RED), false);
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        editor.startEditSession(player, templateId, true);
        return 1;
    }

    /**
     * 处理编辑模板名称
     */
    private static int handleEditTemplateName(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        TemplateEditor.EditSession session = editor.getEditSession(player);

        if (session == null) {
            player.sendMessage(Text.literal("❌ 没有正在编辑的模板，请先使用 /llmchat template edit <模板ID>").formatted(Formatting.RED), false);
            return 0;
        }

        String name = StringArgumentType.getString(context, "name");
        session.getTemplate().setName(name);

        player.sendMessage(Text.literal("✅ 模板名称已更新为: " + name).formatted(Formatting.GREEN), false);
        return 1;
    }

    /**
     * 处理编辑模板描述
     */
    private static int handleEditTemplateDesc(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        TemplateEditor.EditSession session = editor.getEditSession(player);

        if (session == null) {
            player.sendMessage(Text.literal("❌ 没有正在编辑的模板，请先使用 /llmchat template edit <模板ID>").formatted(Formatting.RED), false);
            return 0;
        }

        String description = StringArgumentType.getString(context, "description");
        session.getTemplate().setDescription(description);

        player.sendMessage(Text.literal("✅ 模板描述已更新").formatted(Formatting.GREEN), false);
        return 1;
    }

    /**
     * 处理编辑系统提示词
     */
    private static int handleEditTemplateSystem(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        TemplateEditor.EditSession session = editor.getEditSession(player);

        if (session == null) {
            player.sendMessage(Text.literal("❌ 没有正在编辑的模板，请先使用 /llmchat template edit <模板ID>").formatted(Formatting.RED), false);
            return 0;
        }

        String prompt = StringArgumentType.getString(context, "prompt");
        session.getTemplate().setSystemPrompt(prompt);

        player.sendMessage(Text.literal("✅ 系统提示词已更新").formatted(Formatting.GREEN), false);
        return 1;
    }

    /**
     * 处理编辑用户消息前缀
     */
    private static int handleEditTemplatePrefix(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        TemplateEditor.EditSession session = editor.getEditSession(player);

        if (session == null) {
            player.sendMessage(Text.literal("❌ 没有正在编辑的模板，请先使用 /llmchat template edit <模板ID>").formatted(Formatting.RED), false);
            return 0;
        }

        String prefix = StringArgumentType.getString(context, "prefix");
        session.getTemplate().setUserPromptPrefix(prefix);

        player.sendMessage(Text.literal("✅ 用户消息前缀已更新").formatted(Formatting.GREEN), false);
        return 1;
    }

    /**
     * 处理编辑用户消息后缀
     */
    private static int handleEditTemplateSuffix(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        TemplateEditor.EditSession session = editor.getEditSession(player);

        if (session == null) {
            player.sendMessage(Text.literal("❌ 没有正在编辑的模板，请先使用 /llmchat template edit <模板ID>").formatted(Formatting.RED), false);
            return 0;
        }

        String suffix = StringArgumentType.getString(context, "suffix");
        session.getTemplate().setUserPromptSuffix(suffix);

        player.sendMessage(Text.literal("✅ 用户消息后缀已更新").formatted(Formatting.GREEN), false);
        return 1;
    }

    /**
     * 处理列出模板变量
     */
    private static int handleListTemplateVars(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        TemplateEditor.EditSession session = editor.getEditSession(player);

        if (session == null) {
            player.sendMessage(Text.literal("❌ 没有正在编辑的模板，请先使用 /llmchat template edit <模板ID>").formatted(Formatting.RED), false);
            return 0;
        }

        PromptTemplate template = session.getTemplate();
        player.sendMessage(Text.literal("").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("🔧 模板变量 (" + template.getVariables().size() + "个):").formatted(Formatting.YELLOW), false);

        if (!template.getVariables().isEmpty()) {
            for (java.util.Map.Entry<String, String> entry : template.getVariables().entrySet()) {
                player.sendMessage(Text.literal("  {{" + entry.getKey() + "}} = " + entry.getValue()).formatted(Formatting.AQUA), false);
            }
        } else {
            player.sendMessage(Text.literal("  (无变量)").formatted(Formatting.GRAY), false);
        }

        player.sendMessage(Text.literal("").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("💡 使用 /llmchat template var set <名称> <值> 来添加变量").formatted(Formatting.GRAY), false);
        return 1;
    }

    /**
     * 处理设置模板变量
     */
    private static int handleSetTemplateVar(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        TemplateEditor.EditSession session = editor.getEditSession(player);

        if (session == null) {
            player.sendMessage(Text.literal("❌ 没有正在编辑的模板，请先使用 /llmchat template edit <模板ID>").formatted(Formatting.RED), false);
            return 0;
        }

        String name = StringArgumentType.getString(context, "name");
        String value = StringArgumentType.getString(context, "value");

        session.getTemplate().setVariable(name, value);
        player.sendMessage(Text.literal("✅ 变量已设置: {{" + name + "}} = " + value).formatted(Formatting.GREEN), false);
        return 1;
    }

    /**
     * 处理删除模板变量
     */
    private static int handleRemoveTemplateVar(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        TemplateEditor.EditSession session = editor.getEditSession(player);

        if (session == null) {
            player.sendMessage(Text.literal("❌ 没有正在编辑的模板，请先使用 /llmchat template edit <模板ID>").formatted(Formatting.RED), false);
            return 0;
        }

        String name = StringArgumentType.getString(context, "name");

        if (!session.getTemplate().getVariables().containsKey(name)) {
            player.sendMessage(Text.literal("❌ 变量不存在: " + name).formatted(Formatting.RED), false);
            return 0;
        }

        session.getTemplate().removeVariable(name);
        player.sendMessage(Text.literal("✅ 变量已删除: {{" + name + "}}").formatted(Formatting.GREEN), false);
        return 1;
    }

    /**
     * 处理预览模板
     */
    private static int handlePreviewTemplate(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        editor.previewTemplate(player);
        return 1;
    }

    /**
     * 处理保存模板
     */
    private static int handleSaveTemplate(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        editor.saveTemplate(player);
        return 1;
    }

    /**
     * 处理取消编辑
     */
    private static int handleCancelTemplate(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        TemplateEditor editor = TemplateEditor.getInstance();
        if (editor.isEditing(player)) {
            editor.endEditSession(player);
            player.sendMessage(Text.literal("❌ 编辑已取消，所有更改未保存").formatted(Formatting.YELLOW), false);
        } else {
            player.sendMessage(Text.literal("❌ 没有正在编辑的模板").formatted(Formatting.RED), false);
        }
        return 1;
    }

    /**
     * 处理复制模板
     */
    private static int handleCopyTemplate(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        String fromId = StringArgumentType.getString(context, "from");
        String toId = StringArgumentType.getString(context, "to");

        PromptTemplateManager templateManager = PromptTemplateManager.getInstance();

        if (!templateManager.hasTemplate(fromId)) {
            player.sendMessage(Text.literal("❌ 源模板不存在: " + fromId).formatted(Formatting.RED), false);
            return 0;
        }

        if (templateManager.hasTemplate(toId)) {
            player.sendMessage(Text.literal("❌ 目标模板已存在: " + toId).formatted(Formatting.RED), false);
            return 0;
        }

        try {
            PromptTemplate sourceTemplate = templateManager.getTemplate(fromId);
            PromptTemplate newTemplate = sourceTemplate.copy();
            newTemplate.setId(toId);
            newTemplate.setName(sourceTemplate.getName() + " (副本)");

            templateManager.addTemplate(newTemplate);
            player.sendMessage(Text.literal("✅ 模板已复制: " + fromId + " → " + toId).formatted(Formatting.GREEN), false);

        } catch (Exception e) {
            player.sendMessage(Text.literal("❌ 复制模板失败: " + e.getMessage()).formatted(Formatting.RED), false);
        }

        return 1;
    }

    /**
     * 处理重新加载配置命令（简化版恢复功能）
     */
    private static int handleReload(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        // 检查OP权限
        if (!source.hasPermissionLevel(2)) {
            player.sendMessage(Text.literal("只有OP可以重载配置").formatted(Formatting.RED), false);
            return 0;
        }

        player.sendMessage(Text.literal("🔄 正在重载配置...").formatted(Formatting.YELLOW), false);

        try {
            // 重新加载配置并尝试恢复
            LLMChatConfig config = LLMChatConfig.getInstance();
            config.reload();

            // 尝试自动修复配置
            boolean wasFixed = config.validateAndCompleteConfig();

            // 重新加载提示词模板
            PromptTemplateManager templateManager = PromptTemplateManager.getInstance();
            templateManager.reload();

            // 重新初始化服务管理器
            LLMServiceManager serviceManager = LLMServiceManager.getInstance();
            serviceManager.reload();

            if (wasFixed) {
                player.sendMessage(Text.literal("✅ 配置已重载并自动修复").formatted(Formatting.GREEN), false);
            } else {
                player.sendMessage(Text.literal("✅ 配置已重载").formatted(Formatting.GREEN), false);
            }

            // 验证配置并给出反馈
            if (config.isConfigurationValid()) {
                player.sendMessage(Text.literal("✅ 配置验证通过，AI聊天功能可正常使用").formatted(Formatting.GREEN), false);
                player.sendMessage(Text.literal("当前服务提供商: " + config.getCurrentProvider()).formatted(Formatting.GRAY), false);
                player.sendMessage(Text.literal("当前模型: " + config.getCurrentModel()).formatted(Formatting.GRAY), false);
            } else {
                player.sendMessage(Text.literal("⚠️ 配置验证失败，请检查以下问题:").formatted(Formatting.YELLOW), false);
                Provider currentProvider = config.getCurrentProviderConfig();
                if (currentProvider != null) {
                    String apiKey = currentProvider.getApiKey();
                    if (apiKey != null && (apiKey.contains("your-") || apiKey.contains("-api-key-here"))) {
                        player.sendMessage(Text.literal("• 当前服务提供商 '" + config.getCurrentProvider() + "' 的API密钥仍为默认占位符，需要设置真实的API密钥").formatted(Formatting.GRAY), false);
                    }
                } else {
                    player.sendMessage(Text.literal("• 当前服务提供商配置无效或不存在，请检查配置文件").formatted(Formatting.GRAY), false);
                }

                // 检查是否有任何有效的provider
                if (!config.hasAnyValidProvider()) {
                    player.sendMessage(Text.literal("• 没有找到有效配置的服务提供商，请至少配置一个API密钥").formatted(Formatting.GRAY), false);
                }

                player.sendMessage(Text.literal("使用 /llmchat setup 查看配置向导").formatted(Formatting.GRAY), false);
            }

        } catch (Exception e) {
            player.sendMessage(Text.literal("❌ 重载配置失败: " + e.getMessage()).formatted(Formatting.RED), false);
            player.sendMessage(Text.literal("请检查配置文件或使用 /llmchat setup 重新配置").formatted(Formatting.BLUE), false);
            return 0;
        }

        return 1;
    }





    /**
     * 处理统计信息命令
     */
    private static int handleStats(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        try {
            ConcurrencyManager.ConcurrencyStats stats = ConcurrencyManager.getInstance().getStats();

            player.sendMessage(Text.literal("=== LLM Chat 并发统计 ===").formatted(Formatting.GOLD), false);
            player.sendMessage(Text.literal(""), false);

            // 请求统计
            player.sendMessage(Text.literal("📊 请求统计:").formatted(Formatting.AQUA), false);
            player.sendMessage(Text.literal("  总请求数: " + stats.totalRequests).formatted(Formatting.WHITE), false);
            player.sendMessage(Text.literal("  已完成: " + stats.completedRequests).formatted(Formatting.GREEN), false);
            player.sendMessage(Text.literal("  失败数: " + stats.failedRequests).formatted(Formatting.RED), false);
            player.sendMessage(Text.literal("  成功率: " + String.format("%.1f%%", stats.getSuccessRate() * 100)).formatted(Formatting.YELLOW), false);
            player.sendMessage(Text.literal(""), false);

            // Token统计
            player.sendMessage(Text.literal("🎯 Token统计:").formatted(Formatting.AQUA), false);
            player.sendMessage(Text.literal("  总输入Token: " + String.format("%,d", stats.totalPromptTokens)).formatted(Formatting.WHITE), false);
            player.sendMessage(Text.literal("  总输出Token: " + String.format("%,d", stats.totalCompletionTokens)).formatted(Formatting.WHITE), false);
            player.sendMessage(Text.literal("  总Token数: " + String.format("%,d", stats.totalTokens)).formatted(Formatting.WHITE), false);

            if (stats.completedRequests > 0) {
                player.sendMessage(Text.literal("  平均输入Token/请求: " + String.format("%.1f", stats.getAveragePromptTokensPerRequest())).formatted(Formatting.GRAY), false);
                player.sendMessage(Text.literal("  平均输出Token/请求: " + String.format("%.1f", stats.getAverageCompletionTokensPerRequest())).formatted(Formatting.GRAY), false);
                player.sendMessage(Text.literal("  平均总Token/请求: " + String.format("%.1f", stats.getAverageTotalTokensPerRequest())).formatted(Formatting.GRAY), false);
                player.sendMessage(Text.literal("  Token效率比: " + String.format("%.2f", stats.getTokenEfficiency())).formatted(Formatting.YELLOW), false);
            }
            player.sendMessage(Text.literal(""), false);

            // 并发状态
            player.sendMessage(Text.literal("🔄 当前状态:").formatted(Formatting.AQUA), false);
            player.sendMessage(Text.literal("  活跃请求: " + stats.activeRequests).formatted(Formatting.WHITE), false);
            player.sendMessage(Text.literal("  排队请求: " + stats.queuedRequests).formatted(Formatting.WHITE), false);
            player.sendMessage(Text.literal(""), false);

            // 线程池状态
            player.sendMessage(Text.literal("🧵 线程池状态:").formatted(Formatting.AQUA), false);
            player.sendMessage(Text.literal("  线程池大小: " + stats.poolSize).formatted(Formatting.WHITE), false);
            player.sendMessage(Text.literal("  活跃线程: " + stats.activeThreads).formatted(Formatting.WHITE), false);
            player.sendMessage(Text.literal("  队列大小: " + stats.queueSize).formatted(Formatting.WHITE), false);
            player.sendMessage(Text.literal(""), false);

            // 健康状态
            boolean isHealthy = ConcurrencyManager.getInstance().isHealthy();
            String healthStatus = isHealthy ? "健康" : "异常";
            Formatting healthColor = isHealthy ? Formatting.GREEN : Formatting.RED;
            player.sendMessage(Text.literal("💚 系统状态: " + healthStatus).formatted(healthColor), false);

        } catch (Exception e) {
            player.sendMessage(Text.literal("获取统计信息失败: " + e.getMessage()).formatted(Formatting.RED), false);
            return 0;
        }

        return 1;
    }

    /**
     * 处理主帮助命令 - 显示一级子命令概览
     */
    private static int handleHelp(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        player.sendMessage(Text.literal("=== LLM Chat 帮助 ===").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal(""), false);

        // 基本命令
        player.sendMessage(Text.literal("📝 基本命令:").formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("  /llmchat <消息> - 发送消息给AI助手").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  /llmchat clear - 清空聊天历史").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  /llmchat resume - 恢复上次对话内容").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal(""), false);

        // 子命令分类
        player.sendMessage(Text.literal("🔧 功能模块 (使用 /llmchat <模块> help 查看详细帮助):").formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("  template - 提示词模板管理").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  provider - AI服务提供商管理").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  model - AI模型管理").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  broadcast - AI聊天广播功能").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal(""), false);

        // 系统命令
        player.sendMessage(Text.literal("⚙️ 系统命令:").formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("  /llmchat setup - 显示配置向导").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  /llmchat stats - 显示系统统计信息").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  /llmchat reload - 重载配置 (仅OP)").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal(""), false);

        // 提示信息
        player.sendMessage(Text.literal("💡 提示: 使用 /llmchat <子命令> help 查看具体功能的详细帮助").formatted(Formatting.YELLOW), false);

        return 1;
    }

    /**
     * 显示消息预览
     */
    private static void showMessagePreview(PlayerEntity player, List<LLMMessage> messages, String sessionInfo) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        // 配置预览参数
        int maxPreviewCount = 5; // 显示最多5条消息
        int maxContentLength = 150; // 消息内容最大长度

        int previewCount = Math.min(maxPreviewCount, messages.size());

        // 显示标题
        player.sendMessage(Text.literal("📋 最近的对话内容" +
            (sessionInfo != null ? " (" + sessionInfo + ")" : "") +
            " (显示最后" + previewCount + "条):").formatted(Formatting.AQUA), false);

        // 显示消息
        for (int i = messages.size() - previewCount; i < messages.size(); i++) {
            LLMMessage msg = messages.get(i);
            if (msg == null || msg.getContent() == null) {
                continue;
            }

            // 确定角色显示
            String roleIcon;
            String roleText;
            Formatting roleColor;

            switch (msg.getRole()) {
                case USER:
                    roleIcon = "🙋";
                    roleText = "你";
                    roleColor = Formatting.GREEN;
                    break;
                case ASSISTANT:
                    roleIcon = "🤖";
                    roleText = "AI";
                    roleColor = Formatting.BLUE;
                    break;
                case SYSTEM:
                    roleIcon = "⚙️";
                    roleText = "系统";
                    roleColor = Formatting.YELLOW;
                    break;
                default:
                    roleIcon = "❓";
                    roleText = "未知";
                    roleColor = Formatting.GRAY;
                    break;
            }

            // 处理消息内容
            String content = msg.getContent().trim();
            if (content.length() > maxContentLength) {
                // 智能截断：尽量在句号、问号、感叹号后截断
                int cutPoint = maxContentLength;
                for (int j = Math.min(maxContentLength - 10, content.length() - 1); j >= maxContentLength - 30 && j > 0; j--) {
                    char c = content.charAt(j);
                    if (c == '。' || c == '？' || c == '！' || c == '.' || c == '?' || c == '!') {
                        cutPoint = j + 1;
                        break;
                    }
                }
                content = content.substring(0, cutPoint) + "...";
            }

            // 显示消息
            int messageIndex = i - (messages.size() - previewCount) + 1;
            player.sendMessage(Text.literal("  [" + messageIndex + "] " + roleIcon + " " + roleText + ": " + content)
                .formatted(roleColor), false);
        }

        // 添加分隔线
        player.sendMessage(Text.literal("").formatted(Formatting.GRAY), false);
    }

    /**
     * 处理聊天消息的核心逻辑
     */
    private static void processChatMessage(PlayerEntity player, String message) {
        long startTime = System.currentTimeMillis();

        // 确保player是ServerPlayerEntity类型
        if (!(player instanceof ServerPlayerEntity)) {
            player.sendMessage(Text.literal("此功能只能由服务器玩家使用").formatted(Formatting.RED), false);
            return;
        }
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        // 获取配置和服务
        LLMChatConfig config = LLMChatConfig.getInstance();

        // 检查是否是第一次使用
        if (config.isFirstTimeUse()) {
            showFirstTimeSetupGuide(serverPlayer);
            return;
        }

        LLMServiceManager serviceManager = LLMServiceManager.getInstance();
        LLMService llmService = serviceManager.getDefaultService();

        if (llmService == null || !llmService.isAvailable()) {
            serverPlayer.sendMessage(Text.literal("LLM服务不可用，请检查配置").formatted(Formatting.RED), false);
            return;
        }

        // 获取聊天上下文
        ChatContextManager contextManager = ChatContextManager.getInstance();
        ChatContext chatContext = contextManager.getContext(serverPlayer);
        
        // 获取提示词模板
        PromptTemplateManager templateManager = PromptTemplateManager.getInstance();
        PromptTemplate template = templateManager.getTemplate(chatContext.getCurrentPromptTemplate());
        
        if (template == null) {
            template = templateManager.getDefaultTemplate();
        }

        // 如果是新会话，添加系统提示词
        if (chatContext.getMessageCount() == 0 && template != null) {
            String systemPrompt = template.renderSystemPromptWithContext(serverPlayer, config);
            if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                chatContext.addSystemMessage(systemPrompt);
            }
        }

        // 处理用户消息
        String processedMessage = template != null ? template.renderUserMessage(message) : message;

        chatContext.addUserMessage(processedMessage);

        // 构建LLM配置
        LLMConfig llmConfig = new LLMConfig();

        // 使用当前设置的模型
        String currentModel = config.getCurrentModel();
        if (currentModel.isEmpty()) {
            serverPlayer.sendMessage(Text.literal("请先设置要使用的模型: /llmchat model set <模型名>").formatted(Formatting.RED), false);
            return;
        }

        llmConfig.setModel(currentModel);
        llmConfig.setTemperature(config.getDefaultTemperature());
        llmConfig.setMaxTokens(config.getDefaultMaxTokens());

        // 如果启用了Function Calling，添加工具定义
        if (config.isEnableFunctionCalling()) {
            FunctionRegistry functionRegistry = FunctionRegistry.getInstance();
            List<LLMConfig.ToolDefinition> tools = functionRegistry.generateToolDefinitions(serverPlayer);
            if (!tools.isEmpty()) {
                llmConfig.setTools(tools);
                llmConfig.setToolChoice("auto");
            }
        }

        // 广播用户消息（如果开启了广播且玩家在广播列表中）
        if (shouldBroadcast(config, serverPlayer.getName().getString())) {
            serverPlayer.getServer().getPlayerManager().broadcast(
                Text.literal("[" + serverPlayer.getName().getString() + " 问AI] " + message)
                    .formatted(Formatting.LIGHT_PURPLE),
                false
            );
        } else {
            // 如果没有启用广播，向玩家自己显示提示词确认
            serverPlayer.sendMessage(
                Text.literal("你问 AI " + message)
                    .formatted(Formatting.LIGHT_PURPLE),
                false
            );
        }

        // 发送请求
        if (shouldBroadcast(config, serverPlayer.getName().getString())) {
            serverPlayer.getServer().getPlayerManager().broadcast(
                Text.literal("[AI正在为 " + serverPlayer.getName().getString() + " 思考...]")
                    .formatted(Formatting.GRAY),
                false
            );
        } else {
            serverPlayer.sendMessage(Text.literal("正在思考...").formatted(Formatting.GRAY), false);
        }
        
        // 创建LLM上下文信息
        LLMContext llmContext = LLMContext.builder()
                .playerName(serverPlayer.getName().getString())
                .playerUuid(serverPlayer.getUuidAsString())
                .sessionId(chatContext.getSessionId())
                .metadata("server", serverPlayer.getServer().getName())
                .build();

        llmService.chat(chatContext.getMessages(), llmConfig, llmContext)
                .thenAccept(response -> {
                    long endTime = System.currentTimeMillis();
                    if (response.isSuccess()) {
                        handleLLMResponse(response, serverPlayer, chatContext, config);
                        // 记录成功的性能日志
                        LogManager.getInstance().performance("Chat processing completed successfully",
                                java.util.Map.of(
                                        "player", serverPlayer.getName().getString(),
                                        "total_time_ms", endTime - startTime,
                                        "context_messages", chatContext.getMessageCount()
                                ));
                    } else {
                        serverPlayer.sendMessage(Text.literal("AI响应错误: " + response.getError()).formatted(Formatting.RED), false);
                        LogManager.getInstance().error("AI response error for player " +
                                serverPlayer.getName().getString() + ": " + response.getError());
                    }
                })
                .exceptionally(throwable -> {
                    long endTime = System.currentTimeMillis();
                    serverPlayer.sendMessage(Text.literal("请求失败: " + throwable.getMessage()).formatted(Formatting.RED), false);
                    LogManager.getInstance().error("Chat request failed for player " +
                            serverPlayer.getName().getString(), throwable);
                    // 记录失败的性能日志
                    LogManager.getInstance().performance("Chat processing failed",
                            java.util.Map.of(
                                    "player", serverPlayer.getName().getString(),
                                    "total_time_ms", endTime - startTime,
                                    "error", throwable.getMessage()
                            ));
                    return null;
                });
    }

    /**
     * 处理LLM响应，包括function calling
     */
    private static void handleLLMResponse(LLMResponse response, ServerPlayerEntity player,
                                 ChatContext chatContext, LLMChatConfig config) {
        if (response.getChoices() == null || response.getChoices().isEmpty()) {
            player.sendMessage(Text.literal("AI没有返回有效响应").formatted(Formatting.RED), false);
            return;
        }

        LLMResponse.Choice firstChoice = response.getChoices().get(0);
        LLMMessage message = firstChoice.getMessage();

        if (message == null) {
            player.sendMessage(Text.literal("AI没有返回有效消息").formatted(Formatting.RED), false);
            return;
        }

        // 检查是否有function call
        if (message.getMetadata() != null && message.getMetadata().getFunctionCall() != null) {
            handleFunctionCall(message.getMetadata().getFunctionCall(), player, chatContext, config);
        } else {
            // 普通文本响应
            String content = message.getContent();
            if (content != null && !content.trim().isEmpty()) {
                chatContext.addAssistantMessage(content);

                // 根据广播设置发送AI回复
                if (shouldBroadcast(config, player.getName().getString())) {
                    player.getServer().getPlayerManager().broadcast(
                        Text.literal("[AI回复给 " + player.getName().getString() + "] " + content)
                            .formatted(Formatting.AQUA),
                        false
                    );
                } else {
                    player.sendMessage(Text.literal("[AI] " + content).formatted(Formatting.AQUA), false);
                }

                // 保存会话历史
                if (config.isEnableHistory()) {
                    ChatHistory.getInstance().saveSession(chatContext);
                }

                // 检查是否需要压缩上下文（对话结束后异步处理）
                checkAndNotifyCompression(chatContext, player, config);
            } else {
                player.sendMessage(Text.literal("AI没有返回有效内容").formatted(Formatting.RED), false);
                LogManager.getInstance().error("AI returned no valid content for player " +
                        player.getName().getString());
            }
        }
    }

    /**
     * 检查是否需要压缩上下文并发送通知
     */
    private static void checkAndNotifyCompression(ChatContext chatContext, ServerPlayerEntity player, LLMChatConfig config) {
        // 设置当前玩家实体，用于发送通知
        chatContext.setCurrentPlayer(player);

        // 检查是否启用压缩通知
        if (config.isEnableCompressionNotification()) {
            // 检查是否超过上下文限制
            if (chatContext.calculateTotalCharacters() > chatContext.getMaxContextCharacters()) {
                player.sendMessage(Text.literal("⚠️ 已达到最大上下文长度，您的之前上下文将被压缩")
                    .formatted(Formatting.YELLOW), false);
            }
        }

        // 启动异步压缩检查
        chatContext.scheduleCompressionIfNeeded();
    }

    /**
     * 处理function call（新的OpenAI API格式）
     */
    private static void handleFunctionCall(LLMMessage.FunctionCall functionCall, ServerPlayerEntity player,
                                  ChatContext chatContext, LLMChatConfig config) {
        try {
            String functionName = functionCall.getName();
            String argumentsStr = functionCall.getArguments();
            String toolCallId = functionCall.getToolCallId();

            player.sendMessage(Text.literal("正在执行函数: " + functionName).formatted(Formatting.YELLOW), false);

            // 解析参数
            JsonObject arguments = new JsonObject();
            if (argumentsStr != null && !argumentsStr.trim().isEmpty()) {
                try {
                    arguments = gson.fromJson(argumentsStr, JsonObject.class);
                } catch (Exception e) {
                    player.sendMessage(Text.literal("函数参数解析失败: " + e.getMessage()).formatted(Formatting.RED), false);
                    return;
                }
            }

            // 执行函数
            FunctionRegistry functionRegistry = FunctionRegistry.getInstance();
            LLMFunction.FunctionResult result = functionRegistry.executeFunction(functionName, player, arguments);

            // 根据OpenAI新API格式，需要将函数结果添加到消息列表并再次调用LLM
            if (toolCallId != null) {
                // 添加工具调用消息到上下文
                LLMMessage toolCallMessage = new LLMMessage(LLMMessage.MessageRole.ASSISTANT, null);
                LLMMessage.MessageMetadata metadata = new LLMMessage.MessageMetadata();
                metadata.setFunctionCall(functionCall);
                toolCallMessage.setMetadata(metadata);
                chatContext.addMessage(toolCallMessage);

                // 添加工具响应消息
                String resultContent = result.isSuccess() ? result.getResult() : "错误: " + result.getError();
                LLMMessage toolResponseMessage = new LLMMessage(LLMMessage.MessageRole.TOOL, resultContent);
                toolResponseMessage.setName(functionName);
                toolResponseMessage.setToolCallId(toolCallId);
                chatContext.addMessage(toolResponseMessage);

                // 再次调用LLM获取基于函数结果的响应
                callLLMWithFunctionResult(player, chatContext, config);
            } else {
                // 兼容旧格式的处理方式
                handleLegacyFunctionCall(result, functionName, player, chatContext, config);
            }

        } catch (Exception e) {
            player.sendMessage(Text.literal("函数调用处理失败: " + e.getMessage()).formatted(Formatting.RED), false);
        }
    }

    /**
     * 使用函数结果再次调用LLM
     */
    private static void callLLMWithFunctionResult(ServerPlayerEntity player, ChatContext chatContext, LLMChatConfig config) {
        try {
            LLMServiceManager serviceManager = LLMServiceManager.getInstance();
            LLMService llmService = serviceManager.getDefaultService();

            if (llmService == null) {
                player.sendMessage(Text.literal("LLM服务不可用").formatted(Formatting.RED), false);
                return;
            }

            // 构建配置
            LLMConfig llmConfig = new LLMConfig();
            llmConfig.setModel(config.getCurrentModel());
            llmConfig.setTemperature(config.getDefaultTemperature());
            llmConfig.setMaxTokens(config.getDefaultMaxTokens());

            // 创建LLM上下文信息
            LLMContext llmContext = LLMContext.builder()
                    .playerName(player.getName().getString())
                    .playerUuid(player.getUuidAsString())
                    .sessionId(chatContext.getSessionId())
                    .metadata("server", player.getServer().getName())
                    .build();

            // 发送请求获取最终响应
            llmService.chat(chatContext.getMessages(), llmConfig, llmContext)
                    .thenAccept(response -> {
                        if (response.isSuccess()) {
                            String content = response.getContent();
                            if (content != null && !content.trim().isEmpty()) {
                                chatContext.addAssistantMessage(content);

                                // 根据广播设置发送AI回复
                                if (shouldBroadcast(config, player.getName().getString())) {
                                    player.getServer().getPlayerManager().broadcast(
                                        Text.literal("[AI回复给 " + player.getName().getString() + "] " + content)
                                            .formatted(Formatting.AQUA),
                                        false
                                    );
                                } else {
                                    player.sendMessage(Text.literal("[AI] " + content).formatted(Formatting.AQUA), false);
                                }

                                // 保存会话历史
                                if (config.isEnableHistory()) {
                                    ChatHistory.getInstance().saveSession(chatContext);
                                }

                                // 检查是否需要压缩上下文（对话结束后异步处理）
                                checkAndNotifyCompression(chatContext, player, config);
                            }
                        } else {
                            player.sendMessage(Text.literal("AI响应错误: " + response.getError()).formatted(Formatting.RED), false);
                        }
                    })
                    .exceptionally(throwable -> {
                        player.sendMessage(Text.literal("请求失败: " + throwable.getMessage()).formatted(Formatting.RED), false);
                        return null;
                    });

        } catch (Exception e) {
            player.sendMessage(Text.literal("调用LLM失败: " + e.getMessage()).formatted(Formatting.RED), false);
        }
    }

    /**
     * 处理旧格式的函数调用（向后兼容）
     */
    private static void handleLegacyFunctionCall(LLMFunction.FunctionResult result, String functionName,
                                        ServerPlayerEntity player, ChatContext chatContext, LLMChatConfig config) {
        if (result.isSuccess()) {
            String resultMessage = result.getResult();
            player.sendMessage(Text.literal("[函数执行] " + resultMessage).formatted(Formatting.GREEN), false);

            // 将函数调用和结果添加到上下文中
            chatContext.addAssistantMessage("调用了函数 " + functionName + "，结果：" + resultMessage);

            // 保存会话历史
            if (config.isEnableHistory()) {
                ChatHistory.getInstance().saveSession(chatContext);
            }

            // 检查是否需要压缩上下文（对话结束后异步处理）
            checkAndNotifyCompression(chatContext, player, config);
        } else {
            String errorMessage = result.getError();
            player.sendMessage(Text.literal("[函数错误] " + errorMessage).formatted(Formatting.RED), false);
        }
    }

    /**
     * 处理列出providers命令
     */
    private static int handleListProviders(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        LLMChatConfig config = LLMChatConfig.getInstance();
        com.riceawa.llm.config.ProviderManager providerManager =
            new com.riceawa.llm.config.ProviderManager(config.getProviders());

        player.sendMessage(Text.literal("🔍 正在检测Provider状态...").formatted(Formatting.YELLOW), false);

        List<Provider> providers = config.getProviders();
        if (providers.isEmpty()) {
            player.sendMessage(Text.literal("  没有配置任何providers").formatted(Formatting.RED), false);
            return 1;
        }

        // 异步获取详细状态报告
        providerManager.getDetailedConfigurationReport().whenComplete((report, throwable) -> {
            if (throwable != null) {
                player.sendMessage(Text.literal("❌ 获取Provider状态失败: " + throwable.getMessage())
                    .formatted(Formatting.RED), false);
                // 回退到基本显示
                showBasicProviderList(player, config, providers);
            } else {
                player.sendMessage(Text.literal("📡 Provider状态报告:").formatted(Formatting.AQUA), false);
                String[] lines = report.getReportText().split("\n");
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        Formatting color = Formatting.WHITE;
                        if (line.contains("🟢")) color = Formatting.GREEN;
                        else if (line.contains("🔴")) color = Formatting.RED;
                        else if (line.contains("⚠️")) color = Formatting.YELLOW;
                        else if (line.contains("✅")) color = Formatting.GREEN;

                        player.sendMessage(Text.literal(line).formatted(color), false);
                    }
                }

                // 显示当前选择的provider
                String currentProvider = config.getCurrentProvider();
                if (!currentProvider.isEmpty()) {
                    player.sendMessage(Text.literal(""), false);
                    player.sendMessage(Text.literal("📌 当前使用: " + currentProvider + " / " + config.getCurrentModel())
                        .formatted(Formatting.AQUA), false);
                }
            }
        });

        return 1;
    }

    /**
     * 显示基本的provider列表（回退方案）
     */
    private static void showBasicProviderList(PlayerEntity player, LLMChatConfig config, List<Provider> providers) {
        LLMServiceManager serviceManager = LLMServiceManager.getInstance();
        String currentProvider = config.getCurrentProvider();

        for (Provider provider : providers) {
            String prefix = provider.getName().equals(currentProvider) ? "* " : "  ";
            boolean available = serviceManager.isServiceAvailable(provider.getName());
            String status = available ? "可用" : "不可用";
            Formatting color = available ?
                (provider.getName().equals(currentProvider) ? Formatting.GREEN : Formatting.WHITE) :
                Formatting.RED;

            player.sendMessage(Text.literal(prefix + provider.getName() + " (" + status + ") - " + provider.getApiBaseUrl())
                    .formatted(color), false);
        }
    }

    /**
     * 处理切换provider命令
     */
    private static int handleSwitchProvider(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        // 检查OP权限
        if (!source.hasPermissionLevel(2)) {
            player.sendMessage(Text.literal("只有OP可以切换API提供商").formatted(Formatting.RED), false);
            return 0;
        }

        String providerName = StringArgumentType.getString(context, "provider");
        LLMChatConfig config = LLMChatConfig.getInstance();
        LLMServiceManager serviceManager = LLMServiceManager.getInstance();

        // 检查provider是否存在
        Provider provider = config.getProvider(providerName);
        if (provider == null) {
            player.sendMessage(Text.literal("Provider不存在: " + providerName).formatted(Formatting.RED), false);
            return 0;
        }

        // 检查provider是否有可用模型
        List<String> supportedModels = config.getSupportedModels(providerName);
        if (supportedModels.isEmpty()) {
            player.sendMessage(Text.literal("无法切换到 " + providerName + "：该provider没有配置任何模型").formatted(Formatting.RED), false);
            return 0;
        }

        // 获取第一个模型作为默认模型
        String defaultModel = supportedModels.get(0);

        // 切换到provider并设置默认模型
        if (serviceManager.switchToProvider(providerName, defaultModel)) {
            player.sendMessage(Text.literal("已切换到provider: " + providerName).formatted(Formatting.GREEN), false);
            player.sendMessage(Text.literal("默认模型已设置为: " + defaultModel).formatted(Formatting.GRAY), false);
        } else {
            player.sendMessage(Text.literal("切换失败，provider配置无效: " + providerName).formatted(Formatting.RED), false);
            return 0;
        }

        return 1;
    }

    /**
     * 处理列出当前provider的模型命令
     */
    private static int handleListModels(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        LLMChatConfig config = LLMChatConfig.getInstance();
        String currentProvider = config.getCurrentProvider();

        if (currentProvider.isEmpty()) {
            player.sendMessage(Text.literal("当前没有设置provider").formatted(Formatting.RED), false);
            return 0;
        }

        return listModelsForProvider(player, currentProvider, config);
    }

    /**
     * 处理列出指定provider的模型命令
     */
    private static int handleListModelsForProvider(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        String providerName = StringArgumentType.getString(context, "provider");
        LLMChatConfig config = LLMChatConfig.getInstance();

        return listModelsForProvider(player, providerName, config);
    }

    /**
     * 列出指定provider的模型
     */
    private static int listModelsForProvider(PlayerEntity player, String providerName, LLMChatConfig config) {
        List<String> models = config.getSupportedModels(providerName);

        if (models.isEmpty()) {
            player.sendMessage(Text.literal("Provider " + providerName + " 不存在或没有配置模型").formatted(Formatting.RED), false);
            return 0;
        }

        player.sendMessage(Text.literal("Provider " + providerName + " 支持的模型:").formatted(Formatting.YELLOW), false);

        String currentModel = config.getCurrentModel();
        for (String model : models) {
            String prefix = model.equals(currentModel) ? "* " : "  ";
            Formatting color = model.equals(currentModel) ? Formatting.GREEN : Formatting.WHITE;
            player.sendMessage(Text.literal(prefix + model).formatted(color), false);
        }

        return 1;
    }

    /**
     * 处理设置当前模型命令
     */
    private static int handleSetCurrentModel(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        // 检查OP权限
        if (!source.hasPermissionLevel(2)) {
            player.sendMessage(Text.literal("只有OP可以设置模型").formatted(Formatting.RED), false);
            return 0;
        }

        String model = StringArgumentType.getString(context, "model");
        LLMChatConfig config = LLMChatConfig.getInstance();
        String currentProvider = config.getCurrentProvider();

        if (currentProvider.isEmpty()) {
            player.sendMessage(Text.literal("当前没有设置provider").formatted(Formatting.RED), false);
            return 0;
        }

        if (!config.isModelSupported(currentProvider, model)) {
            player.sendMessage(Text.literal("当前provider不支持模型: " + model).formatted(Formatting.RED), false);
            return 0;
        }

        config.setCurrentModel(model);
        player.sendMessage(Text.literal("已设置当前模型: " + model).formatted(Formatting.GREEN), false);

        return 1;
    }

    /**
     * 处理开启广播命令
     */
    private static int handleEnableBroadcast(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        // 检查OP权限
        if (!source.hasPermissionLevel(2)) {
            player.sendMessage(Text.literal("只有OP可以控制广播功能").formatted(Formatting.RED), false);
            return 0;
        }

        LLMChatConfig config = LLMChatConfig.getInstance();
        config.setEnableBroadcast(true);

        // 向所有玩家广播此消息
        source.getServer().getPlayerManager().broadcast(
            Text.literal("AI聊天广播已开启，所有玩家的AI对话将对全服可见").formatted(Formatting.YELLOW),
            false
        );

        return 1;
    }

    /**
     * 处理关闭广播命令
     */
    private static int handleDisableBroadcast(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        // 检查OP权限
        if (!source.hasPermissionLevel(2)) {
            player.sendMessage(Text.literal("只有OP可以控制广播功能").formatted(Formatting.RED), false);
            return 0;
        }

        LLMChatConfig config = LLMChatConfig.getInstance();
        config.setEnableBroadcast(false);

        // 向所有玩家广播此消息
        source.getServer().getPlayerManager().broadcast(
            Text.literal("AI聊天广播已关闭，AI对话将只对发起者可见").formatted(Formatting.YELLOW),
            false
        );

        return 1;
    }

    /**
     * 处理查看广播状态命令
     */
    private static int handleBroadcastStatus(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        LLMChatConfig config = LLMChatConfig.getInstance();
        boolean isEnabled = config.isEnableBroadcast();
        Set<String> broadcastPlayers = config.getBroadcastPlayers();

        String status = isEnabled ? "开启" : "关闭";
        Formatting color = isEnabled ? Formatting.GREEN : Formatting.RED;

        player.sendMessage(Text.literal("AI聊天广播状态: " + status).formatted(color), false);

        if (isEnabled) {
            if (broadcastPlayers.isEmpty()) {
                player.sendMessage(Text.literal("所有玩家的AI对话将对全服可见").formatted(Formatting.GRAY), false);
            } else {
                player.sendMessage(Text.literal("只有特定玩家的AI对话会被广播").formatted(Formatting.GRAY), false);
                player.sendMessage(Text.literal("广播玩家数量: " + broadcastPlayers.size()).formatted(Formatting.GRAY), false);
            }
        } else {
            player.sendMessage(Text.literal("AI对话只对发起者可见").formatted(Formatting.GRAY), false);
        }

        return 1;
    }

    /**
     * 显示第一次使用配置向导
     */
    private static void showFirstTimeSetupGuide(ServerPlayerEntity player) {
        player.sendMessage(Text.literal("=== 欢迎使用 LLM Chat! ===").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("看起来这是您第一次使用AI聊天功能。").formatted(Formatting.YELLOW), false);
        player.sendMessage(Text.literal("在开始使用之前，需要配置AI服务提供商的API密钥。").formatted(Formatting.YELLOW), false);
        player.sendMessage(Text.literal(""), false);

        player.sendMessage(Text.literal("📋 配置步骤:").formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("1. 打开配置文件: config/lllmchat/config.json").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("2. 选择一个AI服务提供商（OpenAI、OpenRouter、DeepSeek等）").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("3. 将对应的 'apiKey' 字段替换为您的真实API密钥").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("4. 使用 /llmchat reload 重新加载配置").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal(""), false);

        player.sendMessage(Text.literal("💡 提示:").formatted(Formatting.GREEN), false);
        player.sendMessage(Text.literal("• 使用 /llmchat setup 查看详细配置向导").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("• 使用 /llmchat provider list 查看所有可用的服务提供商").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("• 使用 /llmchat help 查看所有可用命令").formatted(Formatting.GRAY), false);
    }

    /**
     * 处理配置向导命令
     */
    private static int handleSetup(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        LLMChatConfig config = LLMChatConfig.getInstance();

        player.sendMessage(Text.literal("=== LLM Chat 配置向导 ===").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal(""), false);

        // 显示当前配置状态
        String configStatus = config.isConfigurationValid() ? "✅ 配置完成" : "❌ 需要配置";
        player.sendMessage(Text.literal("📊 当前配置状态: " + configStatus).formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("当前服务提供商: " + config.getCurrentProvider()).formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("当前模型: " + config.getCurrentModel()).formatted(Formatting.WHITE), false);

        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("📋 配置文件位置:").formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("config/lllmchat/config.json").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal(""), false);

        player.sendMessage(Text.literal("🔧 可用的服务提供商:").formatted(Formatting.AQUA), false);
        List<Provider> providers = config.getProviders();
        for (Provider provider : providers) {
            String apiKey = provider.getApiKey();
            String status = (apiKey != null && (apiKey.contains("your-") || apiKey.contains("-api-key-here")))
                ? "❌ 需要配置API密钥" : "✅ 已配置";
            player.sendMessage(Text.literal("• " + provider.getName() + " - " + status).formatted(Formatting.WHITE), false);
        }

        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("💡 快速配置步骤:").formatted(Formatting.GREEN), false);
        player.sendMessage(Text.literal("1. 选择一个AI服务提供商（推荐OpenAI或DeepSeek）").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("2. 获取对应的API密钥").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("3. 编辑配置文件，替换 'your-xxx-api-key-here' 为真实密钥").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("4. 使用 /llmchat reload 重新加载配置").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("5. 使用 /llmchat 你好 测试功能").formatted(Formatting.GRAY), false);

        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("📚 更多帮助: /llmchat help").formatted(Formatting.BLUE), false);

        return 1;
    }



    /**
     * 处理添加广播玩家命令
     */
    private static int handleAddBroadcastPlayer(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        // 检查OP权限
        if (!source.hasPermissionLevel(2)) {
            player.sendMessage(Text.literal("只有OP可以管理广播玩家列表").formatted(Formatting.RED), false);
            return 0;
        }

        String targetPlayer = StringArgumentType.getString(context, "player");
        LLMChatConfig config = LLMChatConfig.getInstance();

        config.addBroadcastPlayer(targetPlayer);
        player.sendMessage(Text.literal("已将玩家 " + targetPlayer + " 添加到广播列表").formatted(Formatting.GREEN), false);

        return 1;
    }

    /**
     * 处理移除广播玩家命令
     */
    private static int handleRemoveBroadcastPlayer(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        // 检查OP权限
        if (!source.hasPermissionLevel(2)) {
            player.sendMessage(Text.literal("只有OP可以管理广播玩家列表").formatted(Formatting.RED), false);
            return 0;
        }

        String targetPlayer = StringArgumentType.getString(context, "player");
        LLMChatConfig config = LLMChatConfig.getInstance();

        config.removeBroadcastPlayer(targetPlayer);
        player.sendMessage(Text.literal("已将玩家 " + targetPlayer + " 从广播列表移除").formatted(Formatting.YELLOW), false);

        return 1;
    }

    /**
     * 处理列出广播玩家命令
     */
    private static int handleListBroadcastPlayers(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        LLMChatConfig config = LLMChatConfig.getInstance();
        Set<String> broadcastPlayers = config.getBroadcastPlayers();

        if (broadcastPlayers.isEmpty()) {
            player.sendMessage(Text.literal("广播玩家列表为空").formatted(Formatting.GRAY), false);
        } else {
            player.sendMessage(Text.literal("广播玩家列表:").formatted(Formatting.AQUA), false);
            for (String playerName : broadcastPlayers) {
                player.sendMessage(Text.literal("  - " + playerName).formatted(Formatting.WHITE), false);
            }
        }

        return 1;
    }

    /**
     * 处理清空广播玩家命令
     */
    private static int handleClearBroadcastPlayers(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        // 检查OP权限
        if (!source.hasPermissionLevel(2)) {
            player.sendMessage(Text.literal("只有OP可以管理广播玩家列表").formatted(Formatting.RED), false);
            return 0;
        }

        LLMChatConfig config = LLMChatConfig.getInstance();
        config.clearBroadcastPlayers();
        player.sendMessage(Text.literal("已清空广播玩家列表").formatted(Formatting.YELLOW), false);

        return 1;
    }

    /**
     * 处理template子命令帮助
     */
    private static int handleTemplateHelp(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        player.sendMessage(Text.literal("=== 提示词模板管理 ===").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal(""), false);

        player.sendMessage(Text.literal("📋 基本命令:").formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("  /llmchat template list - 列出所有可用的提示词模板").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  /llmchat template set <模板ID> - 切换到指定的提示词模板").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  /llmchat template show <模板ID> - 显示模板详细信息").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal(""), false);

        player.sendMessage(Text.literal("✏️ 编辑命令:").formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("  /llmchat template create <模板ID> - 创建新模板").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  /llmchat template edit <模板ID> - 开始编辑模板").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  /llmchat template copy <源ID> <目标ID> - 复制模板").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal(""), false);

        player.sendMessage(Text.literal("🔧 编辑模式命令 (需要先进入编辑模式):").formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("  /llmchat template edit name <新名称> - 修改模板名称").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  /llmchat template edit desc <新描述> - 修改模板描述").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  /llmchat template edit system <系统提示词> - 修改系统提示词").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  /llmchat template edit prefix <前缀> - 修改用户消息前缀").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  /llmchat template edit suffix <后缀> - 修改用户消息后缀").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal(""), false);

        player.sendMessage(Text.literal("🔧 变量管理 (编辑模式):").formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("  /llmchat template var list - 列出所有变量").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  /llmchat template var set <名称> <值> - 设置变量").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  /llmchat template var remove <名称> - 删除变量").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal(""), false);

        player.sendMessage(Text.literal("💾 编辑控制 (编辑模式):").formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("  /llmchat template preview - 预览当前编辑的模板").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  /llmchat template save - 保存并应用模板").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  /llmchat template cancel - 取消编辑").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal(""), false);

        player.sendMessage(Text.literal("💡 说明:").formatted(Formatting.YELLOW), false);
        player.sendMessage(Text.literal("  • 提示词模板定义了AI的角色和行为风格").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("  • 使用 {{变量名}} 格式在模板中引用变量").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("  • 编辑模式支持热编辑，修改后自动保存").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("  • 内置模板包括: default, creative, survival, redstone, mod等").formatted(Formatting.GRAY), false);

        return 1;
    }

    /**
     * 处理provider子命令帮助
     */
    private static int handleProviderHelp(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        player.sendMessage(Text.literal("=== AI服务提供商管理 ===").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("📡 可用命令:").formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("  /llmchat provider list - 列出所有配置的AI服务提供商").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  /llmchat provider switch <provider> - 切换到指定的服务提供商 (仅OP)").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("💡 说明:").formatted(Formatting.YELLOW), false);
        player.sendMessage(Text.literal("  • 支持多个AI服务: OpenAI, OpenRouter, DeepSeek等").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("  • 每个provider需要配置API密钥和支持的模型").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("  • 切换provider会自动设置为该provider的第一个模型").formatted(Formatting.GRAY), false);

        return 1;
    }

    /**
     * 处理model子命令帮助
     */
    private static int handleModelHelp(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        player.sendMessage(Text.literal("=== AI模型管理 ===").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("🤖 可用命令:").formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("  /llmchat model list - 列出当前provider支持的所有模型").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  /llmchat model list <provider> - 列出指定provider支持的模型").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  /llmchat model set <模型名> - 设置当前使用的AI模型 (仅OP)").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("💡 说明:").formatted(Formatting.YELLOW), false);
        player.sendMessage(Text.literal("  • 不同模型有不同的能力和成本").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("  • 高级模型(如GPT-4)质量更好但成本更高").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("  • 可配置专用压缩模型来优化成本").formatted(Formatting.GRAY), false);

        return 1;
    }

    /**
     * 处理broadcast子命令帮助
     */
    private static int handleBroadcastHelp(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        player.sendMessage(Text.literal("=== AI聊天广播功能 ===").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("📢 基本命令:").formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("  /llmchat broadcast enable - 开启AI聊天广播 (仅OP)").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  /llmchat broadcast disable - 关闭AI聊天广播 (仅OP)").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  /llmchat broadcast status - 查看当前广播状态").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("👥 玩家管理:").formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("  /llmchat broadcast player help - 查看玩家管理命令详情").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("💡 说明:").formatted(Formatting.YELLOW), false);
        player.sendMessage(Text.literal("  • 开启后，AI对话将对全服玩家可见").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("  • 可以设置特定玩家列表进行精确控制").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("  • 默认关闭以保护玩家隐私").formatted(Formatting.GRAY), false);

        return 1;
    }

    /**
     * 处理broadcast player子命令帮助
     */
    private static int handleBroadcastPlayerHelp(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        player.sendMessage(Text.literal("=== 广播玩家管理 ===").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("👥 可用命令:").formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("  /llmchat broadcast player add <玩家名> - 添加玩家到广播列表 (仅OP)").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  /llmchat broadcast player remove <玩家名> - 从广播列表移除玩家 (仅OP)").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  /llmchat broadcast player list - 查看当前广播玩家列表").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("  /llmchat broadcast player clear - 清空广播玩家列表 (仅OP)").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("💡 广播模式说明:").formatted(Formatting.YELLOW), false);
        player.sendMessage(Text.literal("  • 列表为空: 广播所有玩家的AI对话 (全局模式)").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("  • 列表不为空: 只广播列表中玩家的AI对话 (特定玩家模式)").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("  • 可以根据需要灵活控制广播范围").formatted(Formatting.GRAY), false);

        return 1;
    }
}
