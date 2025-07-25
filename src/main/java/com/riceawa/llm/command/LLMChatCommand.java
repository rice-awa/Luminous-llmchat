package com.riceawa.llm.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.context.ChatContext;
import com.riceawa.llm.context.ChatContextManager;
import com.riceawa.llm.core.*;
import com.riceawa.llm.function.FunctionRegistry;
import com.riceawa.llm.history.ChatHistory;
import com.riceawa.llm.service.LLMServiceManager;
import com.riceawa.llm.template.PromptTemplate;
import com.riceawa.llm.template.PromptTemplateManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * LLM聊天命令处理器
 */
public class LLMChatCommand {
    
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
                .then(CommandManager.literal("config")
                        .then(CommandManager.literal("model")
                                .then(CommandManager.argument("model", StringArgumentType.word())
                                        .executes(LLMChatCommand::handleSetModel)))
                        .then(CommandManager.literal("service")
                                .then(CommandManager.argument("service", StringArgumentType.word())
                                        .executes(LLMChatCommand::handleSetService))))
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
     * 处理设置模型
     */
    private static int handleSetModel(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();
        
        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        String model = StringArgumentType.getString(context, "model");
        LLMChatConfig config = LLMChatConfig.getInstance();
        config.setDefaultModel(model);

        player.sendMessage(Text.literal("已设置默认模型: " + model).formatted(Formatting.GREEN), false);
        
        return 1;
    }

    /**
     * 处理设置服务
     */
    private static int handleSetService(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerEntity player = source.getPlayer();
        
        if (player == null) {
            source.sendError(Text.literal("此命令只能由玩家执行"));
            return 0;
        }

        String service = StringArgumentType.getString(context, "service");
        LLMServiceManager serviceManager = LLMServiceManager.getInstance();
        
        if (!serviceManager.isServiceAvailable(service)) {
            player.sendMessage(Text.literal("服务不可用: " + service).formatted(Formatting.RED), false);
            return 0;
        }

        LLMChatConfig config = LLMChatConfig.getInstance();
        config.setDefaultService(service);
        serviceManager.setDefaultService(service);

        player.sendMessage(Text.literal("已设置默认服务: " + service).formatted(Formatting.GREEN), false);
        
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
        player.sendMessage(Text.literal("/llmchat config model <模型> - 设置默认模型").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("/llmchat config service <服务> - 设置默认服务").formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("/llmchat help - 显示此帮助").formatted(Formatting.WHITE), false);
        
        return 1;
    }

    /**
     * 处理聊天消息的核心逻辑
     */
    private static void processChatMessage(PlayerEntity player, String message) {
        // 获取配置和服务
        LLMChatConfig config = LLMChatConfig.getInstance();
        LLMServiceManager serviceManager = LLMServiceManager.getInstance();
        LLMService llmService = serviceManager.getDefaultService();
        
        if (llmService == null || !llmService.isAvailable()) {
            player.sendMessage(Text.literal("LLM服务不可用，请检查配置").formatted(Formatting.RED), false);
            return;
        }

        // 获取聊天上下文
        ChatContextManager contextManager = ChatContextManager.getInstance();
        ChatContext chatContext = contextManager.getContext(player);
        
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
        llmConfig.setModel(config.getDefaultModel());
        llmConfig.setTemperature(config.getDefaultTemperature());
        llmConfig.setMaxTokens(config.getDefaultMaxTokens());
        
        // 如果启用了Function Calling，添加函数定义
        if (config.isEnableFunctionCalling()) {
            FunctionRegistry functionRegistry = FunctionRegistry.getInstance();
            List<LLMConfig.FunctionDefinition> functions = functionRegistry.generateFunctionDefinitions(player);
            if (!functions.isEmpty()) {
                llmConfig.setFunctions(functions);
                llmConfig.setFunctionCall("auto");
            }
        }

        // 发送请求
        player.sendMessage(Text.literal("正在思考...").formatted(Formatting.GRAY), false);
        
        llmService.chat(chatContext.getMessages(), llmConfig)
                .thenAccept(response -> {
                    if (response.isSuccess()) {
                        String content = response.getContent();
                        if (content != null && !content.trim().isEmpty()) {
                            chatContext.addAssistantMessage(content);
                            player.sendMessage(Text.literal("[AI] " + content).formatted(Formatting.AQUA), false);

                            // 保存会话历史
                            if (config.isEnableHistory()) {
                                ChatHistory.getInstance().saveSession(chatContext);
                            }
                        } else {
                            player.sendMessage(Text.literal("AI没有返回有效响应").formatted(Formatting.RED), false);
                        }
                    } else {
                        player.sendMessage(Text.literal("AI响应错误: " + response.getError()).formatted(Formatting.RED), false);
                    }
                })
                .exceptionally(throwable -> {
                    player.sendMessage(Text.literal("请求失败: " + throwable.getMessage()).formatted(Formatting.RED), false);
                    return null;
                });
    }
}
