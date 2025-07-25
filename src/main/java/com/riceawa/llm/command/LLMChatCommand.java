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
import java.util.concurrent.CompletableFuture;

/**
 * LLM聊天命令处理器
 */
public class LLMChatCommand {
    private static final Gson gson = new Gson();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
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
                                .executes(LLMChatCommand::handleBroadcastStatus)))
                .then(CommandManager.literal("reload")
                        .executes(LLMChatCommand::handleReload))
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
        
        // 异步处理聊天请求
        CompletableFuture.runAsync(() -> {
            try {
                processChatMessage(player, message);
            } catch (Exception e) {
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
     * 处理重新加载配置命令
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

        try {
            // 重新加载配置
            LLMChatConfig config = LLMChatConfig.getInstance();
            config.reload();

            // 重新加载提示词模板
            PromptTemplateManager templateManager = PromptTemplateManager.getInstance();
            templateManager.reload();

            // 重新初始化服务管理器
            LLMServiceManager serviceManager = LLMServiceManager.getInstance();
            serviceManager.reload();

            player.sendMessage(Text.literal("配置已重新加载").formatted(Formatting.GREEN), false);

        } catch (Exception e) {
            player.sendMessage(Text.literal("重新加载配置失败: " + e.getMessage()).formatted(Formatting.RED), false);
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
        player.sendMessage(Text.literal("/llmchat reload - 重新加载配置文件 (仅OP)").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("/llmchat help - 显示此帮助").formatted(Formatting.WHITE), false);
        
        return 1;
    }

    /**
     * 处理聊天消息的核心逻辑
     */
    private static void processChatMessage(PlayerEntity player, String message) {
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
            String systemPrompt = template.renderSystemPrompt();
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

        // 广播用户消息（如果开启了广播）
        if (config.isEnableBroadcast()) {
            serverPlayer.getServer().getPlayerManager().broadcast(
                Text.literal("[" + serverPlayer.getName().getString() + " 问AI] " + message)
                    .formatted(Formatting.LIGHT_PURPLE),
                false
            );
        }

        // 发送请求
        if (config.isEnableBroadcast()) {
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
                    if (response.isSuccess()) {
                        handleLLMResponse(response, serverPlayer, chatContext, config);
                    } else {
                        serverPlayer.sendMessage(Text.literal("AI响应错误: " + response.getError()).formatted(Formatting.RED), false);
                    }
                })
                .exceptionally(throwable -> {
                    serverPlayer.sendMessage(Text.literal("请求失败: " + throwable.getMessage()).formatted(Formatting.RED), false);
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
                if (config.isEnableBroadcast()) {
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
                                if (config.isEnableBroadcast()) {
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
        LLMServiceManager serviceManager = LLMServiceManager.getInstance();

        if (serviceManager.switchToProvider(providerName, null)) {
            player.sendMessage(Text.literal("已切换到provider: " + providerName).formatted(Formatting.GREEN), false);
        } else {
            player.sendMessage(Text.literal("切换失败，provider不存在或配置无效: " + providerName).formatted(Formatting.RED), false);
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

        String status = isEnabled ? "开启" : "关闭";
        Formatting color = isEnabled ? Formatting.GREEN : Formatting.RED;

        player.sendMessage(Text.literal("AI聊天广播状态: " + status).formatted(color), false);

        if (isEnabled) {
            player.sendMessage(Text.literal("所有玩家的AI对话将对全服可见").formatted(Formatting.GRAY), false);
        } else {
            player.sendMessage(Text.literal("AI对话只对发起者可见").formatted(Formatting.GRAY), false);
        }

        return 1;
    }
}
