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
		// 初始化配置管理器
		LLMChatConfig.getInstance();
		LOGGER.info("Configuration manager initialized");

		// 初始化提示词模板管理器
		PromptTemplateManager.getInstance();
		LOGGER.info("Prompt template manager initialized");

		// 初始化函数注册表
		FunctionRegistry.getInstance();
		LOGGER.info("Function registry initialized");

		// 初始化服务管理器
		LLMServiceManager.getInstance();
		LOGGER.info("LLM service manager initialized");

		// 初始化上下文管理器
		ChatContextManager.getInstance();
		LOGGER.info("Chat context manager initialized");
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
			ChatContextManager.getInstance().shutdown();
		});
	}
}