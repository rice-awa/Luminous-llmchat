package com.riceawa;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.riceawa.llm.command.LLMChatCommand;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.template.PromptTemplateManager;
import com.riceawa.llm.function.FunctionRegistry;
import com.riceawa.llm.service.LLMServiceManager;
import com.riceawa.llm.context.ChatContextManager;
import com.riceawa.llm.logging.LogManager;

public class Lllmchat implements ModInitializer {
	public static final String MOD_ID = "lllmchat";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Initializing LLM Chat Mod...");

		// 初始化核心组件
		initializeComponents();

		// 注册命令
		registerCommands();

		// 注册事件监听器
		registerEvents();

		LOGGER.info("LLM Chat Mod initialized successfully!");
	}

	/**
	 * 初始化核心组件
	 */
	private void initializeComponents() {
		// 初始化配置管理器并尝试自动恢复
		LLMChatConfig config = LLMChatConfig.getInstance();
		LOGGER.info("Configuration manager initialized");

		// 尝试自动修复配置
		try {
			boolean wasFixed = config.validateAndCompleteConfig();
			if (wasFixed) {
				LOGGER.info("Configuration auto-recovery completed");
			}
		} catch (Exception e) {
			LOGGER.warn("Configuration auto-recovery failed: " + e.getMessage());
		}

		// 初始化日志管理器
		LogManager.getInstance(config.getLogConfig());
		LOGGER.info("Log manager initialized");
		LogManager.getInstance().system("LLMChat mod starting up...");

		// 初始化提示词模板管理器
		PromptTemplateManager.getInstance();
		LOGGER.info("Prompt template manager initialized");
		LogManager.getInstance().system("Prompt template manager initialized");

		// 初始化函数注册表
		FunctionRegistry.getInstance();
		LOGGER.info("Function registry initialized");
		LogManager.getInstance().system("Function registry initialized");

		// 初始化服务管理器
		LLMServiceManager.getInstance();
		LOGGER.info("LLM service manager initialized");
		LogManager.getInstance().system("LLM service manager initialized");

		// 初始化上下文管理器
		ChatContextManager.getInstance();
		LOGGER.info("Chat context manager initialized");
		LogManager.getInstance().system("Chat context manager initialized");
	}

	/**
	 * 注册命令
	 */
	private void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			LLMChatCommand.register(dispatcher, registryAccess);
			LOGGER.info("LLM Chat commands registered");
		});
	}

	/**
	 * 注册事件监听器
	 */
	private void registerEvents() {
		// 服务器停止时的清理工作
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LOGGER.info("Server stopping, cleaning up LLM Chat resources...");
			LogManager.getInstance().system("Server stopping, cleaning up resources...");
			ChatContextManager.getInstance().shutdown();
			LogManager.getInstance().shutdown();
		});
	}
}