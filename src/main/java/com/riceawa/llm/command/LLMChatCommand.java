package com.riceawa.llm.command;

import com.mojang.brigadier.CommandDispatcher;
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
import com.riceawa.llm.logging.LogManager;
import com.riceawa.llm.service.LLMServiceManager;
import com.riceawa.llm.template.PromptTemplate;
import com.riceawa.llm.template.PromptTemplateManager;
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
                .then(CommandManager.literal("template")
                        .then(CommandManager.literal("list")
                                .executes(LLMChatCommand::handleListTemplates))
                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("template", StringArgumentType.word())
                                        .executes(LLMChatCommand::handleSetTemplate))))

                .then(CommandManager.literal("provider")
                        .then(CommandManager.literal("list")
                                .executes(LLMChatCommand::handleListProviders))
                        .then(CommandManager.literal("switch")
                                .then(CommandManager.argument("provider", StringArgumentType.word())
                                        .executes(LLMChatCommand::handleSwitchProvider))))
                .then(CommandManager.literal("model")
                        .then(CommandManager.literal("list")
                                .executes(LLMChatCommand::handleListModels)
                                .then(CommandManager.argument("provider", StringArgumentType.word())
                                        .executes(LLMChatCommand::handleListModelsForProvider)))
                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("model", StringArgumentType.word())
                                        .executes(LLMChatCommand::handleSetCurrentModel))))
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
                                        .executes(LLMChatCommand::handleClearBroadcastPlayers))))
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

        ChatContextManager.getInstance().clearContext(player);
        player.sendMessage(Text.literal("聊天历史已清空").formatted(Formatting.GREEN), false);
        
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

        ChatContext chatContext = ChatContextManager.getInstance().getContext(player);
        chatContext.setCurrentPromptTemplate(templateId);

        PromptTemplate template = templateManager.getTemplate(templateId);
        player.sendMessage(Text.literal("已切换到模板: " + template.getName()).formatted(Formatting.GREEN), false);
        
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

            player.sendMessage(Text.literal("当前提供商: " + config.getCurrentProvider()).formatted(Formatting.GRAY), false);
            player.sendMessage(Text.literal("当前模型: " + config.getCurrentModel()).formatted(Formatting.GRAY), false);

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
     * 处理帮助命令
     */
    private static int handleHelp(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();
        
        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        player.sendMessage(Text.literal("=== LLM Chat 帮助 ===").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal("/llmchat <消息> - 发送消息给AI助手").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("/llmchat clear - 清空聊天历史").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("/llmchat template list - 列出所有模板").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("/llmchat template set <模板> - 设置提示词模板").formatted(Formatting.WHITE), false);

        player.sendMessage(Text.literal("/llmchat provider list - 列出所有providers").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("/llmchat provider switch <provider> - 切换provider (仅OP)").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("/llmchat model list [provider] - 列出模型").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("/llmchat model set <model> - 设置当前模型 (仅OP)").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("/llmchat broadcast enable - 开启AI聊天广播 (仅OP)").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("/llmchat broadcast disable - 关闭AI聊天广播 (仅OP)").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("/llmchat broadcast status - 查看广播状态").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("/llmchat broadcast player add <玩家> - 添加玩家到广播列表 (仅OP)").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("/llmchat broadcast player remove <玩家> - 从广播列表移除玩家 (仅OP)").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("/llmchat broadcast player list - 查看广播玩家列表").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("/llmchat broadcast player clear - 清空广播玩家列表 (仅OP)").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("/llmchat reload - 重载配置并尝试恢复 (仅OP)").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("/llmchat setup - 显示配置向导").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("/llmchat stats - 显示并发统计信息").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("/llmchat help - 显示此帮助").formatted(Formatting.WHITE), false);
        
        return 1;
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
        
        llmService.chat(chatContext.getMessages(), llmConfig)
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
            } else {
                player.sendMessage(Text.literal("AI没有返回有效内容").formatted(Formatting.RED), false);
                LogManager.getInstance().error("AI returned no valid content for player " +
                        player.getName().getString());
            }
        }
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

            // 发送请求获取最终响应
            llmService.chat(chatContext.getMessages(), llmConfig)
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
        LLMServiceManager serviceManager = LLMServiceManager.getInstance();

        player.sendMessage(Text.literal("可用的Providers:").formatted(Formatting.YELLOW), false);

        List<Provider> providers = config.getProviders();
        if (providers.isEmpty()) {
            player.sendMessage(Text.literal("  没有配置任何providers").formatted(Formatting.RED), false);
            return 1;
        }

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

        return 1;
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
        player.sendMessage(Text.literal("📊 当前配置状态:").formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("当前服务提供商: " + config.getCurrentProvider()).formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("当前模型: " + config.getCurrentModel()).formatted(Formatting.WHITE), false);

        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("📋 配置文件位置:").formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("config/lllmchat/config.json").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal(""), false);

        player.sendMessage(Text.literal("🔧 可用的服务提供商:").formatted(Formatting.AQUA), false);
        List<Provider> providers = config.getProviders();
        for (Provider provider : providers) {
            String status = provider.getApiKey().contains("your-") ? "❌ 需要配置API密钥" : "✅ 已配置";
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
}
