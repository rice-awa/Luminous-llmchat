package com.riceawa;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.template.PromptTemplateManager;
import com.riceawa.llm.function.FunctionRegistry;
import com.riceawa.llm.service.LLMServiceManager;

public class LllmchatClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// 初始化客户端组件
		initializeClientComponents();

		// 注册客户端生命周期事件
		registerClientEvents();

		Lllmchat.LOGGER.info("LLM Chat Client initialized!");
	}

	/**
	 * 初始化客户端组件
	 */
	private void initializeClientComponents() {
		// 初始化配置管理器
		LLMChatConfig.getInstance();

		// 初始化提示词模板管理器
		PromptTemplateManager.getInstance();

		// 初始化函数注册表
		FunctionRegistry.getInstance();

		// 初始化服务管理器
		LLMServiceManager.getInstance();
	}

	/**
	 * 注册客户端事件
	 */
	private void registerClientEvents() {
		// 客户端停止时的清理工作
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			// 这里可以添加客户端关闭时的清理逻辑
			Lllmchat.LOGGER.info("LLM Chat Client shutting down...");
		});
	}
}