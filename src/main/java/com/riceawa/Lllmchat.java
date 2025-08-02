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
import com.riceawa.mcp.service.MCPClientManager;
import com.riceawa.mcp.service.MCPServiceImpl;
import com.riceawa.mcp.function.MCPFunctionRegistry;
import com.riceawa.mcp.function.MCPToolPermissionManager;
import com.riceawa.mcp.function.MCPIntegrationManager;

public class Lllmchat implements ModInitializer {
	public static final String MOD_ID = "lllmchat";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// MCP系统组件
	private static MCPClientManager mcpClientManager;
	private static MCPServiceImpl mcpService;
	private static MCPFunctionRegistry mcpFunctionRegistry;
	private static MCPIntegrationManager mcpIntegrationManager;

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

		// 初始化MCP系统
		initializeMCPSystem(config);
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
			
			// 关闭MCP系统
			shutdownMCPSystem();
			
			ChatContextManager.getInstance().shutdown();
			LogManager.getInstance().shutdown();
		});
	}

	/**
	 * 初始化MCP系统
	 */
	private static void initializeMCPSystem(LLMChatConfig config) {
		try {
			// 获取MCP配置
			com.riceawa.mcp.config.MCPConfig mcpConfig = config.getMcpConfig();
			
			// 检查MCP功能是否启用
			if (!mcpConfig.isEnabled()) {
				LOGGER.info("MCP功能已禁用，跳过MCP系统初始化");
				LogManager.getInstance().system("MCP功能已禁用");
				return;
			}

			LOGGER.info("开始初始化MCP系统...");
			LogManager.getInstance().system("Initializing MCP system...");

			// 使用分步初始化，每一步都有独立的错误处理
			boolean initSuccess = true;

			// 步骤1: 初始化MCP客户端管理器
			try {
				mcpClientManager = MCPClientManager.getInstance();
				mcpClientManager.initialize(mcpConfig).get(30, java.util.concurrent.TimeUnit.SECONDS);
				LOGGER.info("MCP客户端管理器初始化完成");
			} catch (Exception e) {
				LOGGER.error("MCP客户端管理器初始化失败: " + e.getMessage(), e);
				initSuccess = false;
			}

			// 步骤2: 初始化MCP集成管理器（替代单独初始化各个组件）
			if (initSuccess) {
				try {
					mcpIntegrationManager = MCPIntegrationManager.initialize(mcpClientManager);
					mcpIntegrationManager.start().get(30, java.util.concurrent.TimeUnit.SECONDS);
					
					// 从集成管理器获取组件引用
					mcpService = (MCPServiceImpl) mcpIntegrationManager.getMCPService();
					mcpFunctionRegistry = mcpIntegrationManager.getFunctionRegistry();
					
					LOGGER.info("MCP集成管理器初始化完成");
				} catch (Exception e) {
					LOGGER.error("MCP集成管理器初始化失败: " + e.getMessage(), e);
					initSuccess = false;
				}
			}

			// 步骤3: 初始化资源管理器和上下文提供器
			if (initSuccess) {
				try {
					com.riceawa.mcp.resource.MCPResourceManager.initialize(mcpService);
					LOGGER.info("MCP资源管理器初始化完成");

					com.riceawa.mcp.integration.MCPContextProvider.initialize(
						mcpService, 
						com.riceawa.mcp.resource.MCPResourceManager.getInstance()
					);
					LOGGER.info("MCP上下文提供器初始化完成");
				} catch (Exception e) {
					LOGGER.error("MCP资源管理器初始化失败: " + e.getMessage(), e);
					// 资源管理器失败不影响核心功能，继续初始化
				}
			}

			// 步骤4: 启动客户端管理器
			if (initSuccess && mcpClientManager != null) {
				try {
					mcpClientManager.start().get(30, java.util.concurrent.TimeUnit.SECONDS);
					LOGGER.info("MCP客户端管理器启动完成");

					// 异步注册MCP工具，不阻塞启动过程
					registerAllMCPToolsAsync();

				} catch (Exception e) {
					LOGGER.error("MCP客户端管理器启动失败: " + e.getMessage(), e);
					initSuccess = false;
				}
			}

			if (initSuccess) {
				LOGGER.info("MCP系统初始化完成");
				LogManager.getInstance().system("MCP system initialized successfully");
			} else {
				LOGGER.warn("MCP系统部分初始化失败，将以降级模式运行");
				LogManager.getInstance().system("MCP system initialized with degraded functionality");
			}

		} catch (Exception e) {
			LOGGER.error("MCP系统初始化失败: " + e.getMessage(), e);
			LogManager.getInstance().system("MCP system initialization failed: " + e.getMessage());
			
			// 初始化失败时，确保MCP系统处于安全状态
			shutdownMCPSystemSafely();
		}
	}

	/**
	 * 关闭MCP系统
	 */
	private static void shutdownMCPSystem() {
		shutdownMCPSystemSafely();
	}

	/**
	 * 安全关闭MCP系统
	 */
	private static void shutdownMCPSystemSafely() {
		try {
			LOGGER.info("开始关闭MCP系统...");
			LogManager.getInstance().system("Shutting down MCP system...");

			// 注销所有MCP工具
			try {
				if (mcpFunctionRegistry != null) {
					unregisterAllMCPTools();
				}
			} catch (Exception e) {
				LOGGER.error("注销MCP工具失败: " + e.getMessage(), e);
			}

			// 关闭MCP服务层
			try {
				if (mcpService != null) {
					mcpService.shutdown();
					mcpService = null;
					LOGGER.info("MCP服务层已关闭");
				}
			} catch (Exception e) {
				LOGGER.error("关闭MCP服务层失败: " + e.getMessage(), e);
				mcpService = null; // 确保清理引用
			}

			// 关闭MCP客户端管理器
			try {
				if (mcpClientManager != null) {
					mcpClientManager.stop().get(10, java.util.concurrent.TimeUnit.SECONDS);
					mcpClientManager = null;
					LOGGER.info("MCP客户端管理器已关闭");
				}
			} catch (Exception e) {
				LOGGER.error("关闭MCP客户端管理器失败: " + e.getMessage(), e);
				mcpClientManager = null; // 确保清理引用
			}

			// 清理MCP功能注册器
			mcpFunctionRegistry = null;

			LOGGER.info("MCP系统关闭完成");
			LogManager.getInstance().system("MCP system shutdown completed");

		} catch (Exception e) {
			LOGGER.error("MCP系统关闭失败: " + e.getMessage(), e);
			LogManager.getInstance().system("MCP system shutdown failed: " + e.getMessage());
			
			// 强制清理所有引用
			mcpService = null;
			mcpClientManager = null;
			mcpFunctionRegistry = null;
		}
	}

	/**
	 * 注册所有MCP工具（同步版本，用于兼容）
	 */
	private static void registerAllMCPTools() {
		registerAllMCPToolsAsync();
	}

	/**
	 * 异步注册所有MCP工具
	 */
	private static void registerAllMCPToolsAsync() {
		if (mcpFunctionRegistry == null || mcpService == null) {
			LOGGER.warn("MCP功能注册器或服务未初始化，跳过工具注册");
			return;
		}

		// 异步注册所有工具，避免阻塞启动过程
		java.util.concurrent.CompletableFuture.runAsync(() -> {
			try {
				LOGGER.info("开始异步注册MCP工具...");
				
				// 获取所有可用的工具，设置较短的超时时间
				java.util.List<com.riceawa.mcp.model.MCPTool> allTools = 
					mcpService.listAllTools().get(15, java.util.concurrent.TimeUnit.SECONDS);
				
				if (allTools.isEmpty()) {
					LOGGER.info("没有可用的MCP工具需要注册");
					return;
				}
				
				// 按客户端分组注册工具
				java.util.Map<String, java.util.List<com.riceawa.mcp.model.MCPTool>> toolsByClient = new java.util.HashMap<>();
				for (com.riceawa.mcp.model.MCPTool tool : allTools) {
					String clientName = tool.getClientName();
					if (clientName != null) {
						toolsByClient.computeIfAbsent(clientName, k -> new java.util.ArrayList<>()).add(tool);
					}
				}

				// 注册每个客户端的工具
				int totalRegistered = 0;
				int totalFailed = 0;
				
				for (java.util.Map.Entry<String, java.util.List<com.riceawa.mcp.model.MCPTool>> entry : toolsByClient.entrySet()) {
					String clientName = entry.getKey();
					java.util.List<com.riceawa.mcp.model.MCPTool> clientTools = entry.getValue();
					
					try {
						int registered = mcpFunctionRegistry.registerClientTools(clientName, clientTools);
						totalRegistered += registered;
						totalFailed += (clientTools.size() - registered);
						
						LOGGER.info("已注册客户端 {} 的 {}/{} 个工具", clientName, registered, clientTools.size());
					} catch (Exception e) {
						LOGGER.error("注册客户端 {} 的工具失败: {}", clientName, e.getMessage());
						totalFailed += clientTools.size();
					}
				}

				if (totalRegistered > 0) {
					LOGGER.info("MCP工具注册完成，成功注册 {} 个工具，失败 {} 个", totalRegistered, totalFailed);
					LogManager.getInstance().system("Registered " + totalRegistered + " MCP tools successfully");
				} else {
					LOGGER.warn("没有成功注册任何MCP工具");
					LogManager.getInstance().system("No MCP tools were registered successfully");
				}

			} catch (java.util.concurrent.TimeoutException e) {
				LOGGER.warn("MCP工具注册超时，将在后台继续尝试");
				LogManager.getInstance().system("MCP tool registration timed out");
			} catch (Exception e) {
				LOGGER.error("注册MCP工具失败: " + e.getMessage(), e);
				LogManager.getInstance().system("Failed to register MCP tools: " + e.getMessage());
			}
		}).exceptionally(throwable -> {
			LOGGER.error("MCP工具异步注册过程出错: " + throwable.getMessage(), throwable);
			return null;
		});
	}

	/**
	 * 注销所有MCP工具
	 */
	private static void unregisterAllMCPTools() {
		if (mcpFunctionRegistry == null) {
			return;
		}

		try {
			// 获取所有已注册的客户端
			java.util.Set<String> clientNames = new java.util.HashSet<>();
			for (com.riceawa.mcp.function.MCPFunctionAdapter adapter : mcpFunctionRegistry.getAllMCPAdapters()) {
				String clientName = adapter.getMCPTool().getClientName();
				if (clientName != null) {
					clientNames.add(clientName);
				}
			}

			// 注销每个客户端的工具
			int totalUnregistered = 0;
			for (String clientName : clientNames) {
				int unregistered = mcpFunctionRegistry.unregisterClientTools(clientName);
				totalUnregistered += unregistered;
				LOGGER.info("已注销客户端 {} 的 {} 个工具", clientName, unregistered);
			}

			LOGGER.info("MCP工具注销完成，共注销 {} 个工具", totalUnregistered);
			LogManager.getInstance().system("Unregistered " + totalUnregistered + " MCP tools");

		} catch (Exception e) {
			LOGGER.error("注销MCP工具失败: " + e.getMessage(), e);
		}
	}

	/**
	 * 获取MCP客户端管理器实例
	 */
	public static MCPClientManager getMCPClientManager() {
		return mcpClientManager;
	}

	/**
	 * 获取MCP服务实例
	 */
	public static MCPServiceImpl getMCPService() {
		return mcpService;
	}

	/**
	 * 获取MCP功能注册器实例
	 */
	public static MCPFunctionRegistry getMCPFunctionRegistry() {
		return mcpFunctionRegistry;
	}

	/**
	 * 检查MCP系统是否可用
	 */
	public static boolean isMCPAvailable() {
		return mcpService != null && mcpService.isAvailable();
	}

	/**
	 * 重新加载MCP配置
	 */
	public static java.util.concurrent.CompletableFuture<Boolean> reloadMCPConfig() {
		return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
			try {
				// 获取新的配置
				LLMChatConfig config = LLMChatConfig.getInstance();
				config.reloadMcpConfig();
				com.riceawa.mcp.config.MCPConfig newMcpConfig = config.getMcpConfig();

				// 如果MCP功能被禁用
				if (!newMcpConfig.isEnabled()) {
					if (mcpClientManager != null) {
						shutdownMCPSystem();
					}
					LOGGER.info("MCP功能已禁用，系统已关闭");
					return true;
				}

				// 如果MCP系统未初始化，重新初始化
				if (mcpClientManager == null) {
					initializeMCPSystem(config);
					return true;
				}

				// 重新加载客户端管理器配置
				mcpClientManager.reloadConfig(newMcpConfig).join();

				// 重新注册所有工具
				registerAllMCPTools();

				LOGGER.info("MCP配置重新加载完成");
				LogManager.getInstance().system("MCP configuration reloaded successfully");
				return true;

			} catch (Exception e) {
				LOGGER.error("重新加载MCP配置失败: " + e.getMessage(), e);
				LogManager.getInstance().system("Failed to reload MCP configuration: " + e.getMessage());
				return false;
			}
		});
	}

	/**
	 * 动态启用MCP功能
	 */
	public static java.util.concurrent.CompletableFuture<Boolean> enableMCP() {
		return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
			try {
				LLMChatConfig config = LLMChatConfig.getInstance();
				com.riceawa.mcp.config.MCPConfig mcpConfig = config.getMcpConfig();
				
				if (mcpConfig.isEnabled()) {
					LOGGER.info("MCP功能已经启用");
					return true;
				}

				// 启用MCP配置
				mcpConfig.setEnabled(true);
				config.setMcpConfig(mcpConfig);

				// 初始化MCP系统
				initializeMCPSystem(config);

				LOGGER.info("MCP功能已动态启用");
				LogManager.getInstance().system("MCP functionality enabled dynamically");
				return true;

			} catch (Exception e) {
				LOGGER.error("动态启用MCP功能失败: " + e.getMessage(), e);
				return false;
			}
		});
	}

	/**
	 * 动态禁用MCP功能
	 */
	public static java.util.concurrent.CompletableFuture<Boolean> disableMCP() {
		return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
			try {
				LLMChatConfig config = LLMChatConfig.getInstance();
				com.riceawa.mcp.config.MCPConfig mcpConfig = config.getMcpConfig();
				
				if (!mcpConfig.isEnabled()) {
					LOGGER.info("MCP功能已经禁用");
					return true;
				}

				// 关闭MCP系统
				shutdownMCPSystem();

				// 禁用MCP配置
				mcpConfig.setEnabled(false);
				config.setMcpConfig(mcpConfig);

				LOGGER.info("MCP功能已动态禁用");
				LogManager.getInstance().system("MCP functionality disabled dynamically");
				return true;

			} catch (Exception e) {
				LOGGER.error("动态禁用MCP功能失败: " + e.getMessage(), e);
				return false;
			}
		});
	}

	/**
	 * 检查MCP系统是否健康运行
	 */
	public static boolean isMCPHealthy() {
		try {
			if (!isMCPAvailable()) {
				return false;
			}
			
			// 检查客户端管理器状态
			if (mcpClientManager != null && mcpClientManager.isRunning()) {
				// 检查是否有连接的客户端
				return mcpClientManager.hasConnectedClients();
			}
			
			return false;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * 获取MCP系统状态摘要
	 */
	public static String getMCPStatusSummary() {
		try {
			if (!isMCPAvailable()) {
				return "MCP系统未初始化或已禁用";
			}
			
			StringBuilder status = new StringBuilder();
			status.append("MCP系统状态: ");
			
			if (isMCPHealthy()) {
				status.append("运行正常");
				
				if (mcpClientManager != null) {
					int connectedClients = mcpClientManager.getConnectedClientCount();
					int totalClients = mcpClientManager.getTotalClientCount();
					status.append(String.format(" (%d/%d 客户端已连接)", connectedClients, totalClients));
				}
				
				if (mcpFunctionRegistry != null) {
					int registeredTools = mcpFunctionRegistry.getRegisteredToolCount();
					status.append(String.format(", %d 个工具已注册", registeredTools));
				}
			} else {
				status.append("运行异常或无可用客户端");
			}
			
			return status.toString();
		} catch (Exception e) {
			return "获取MCP状态失败: " + e.getMessage();
		}
	}

	/**
	 * 尝试恢复MCP系统
	 */
	public static java.util.concurrent.CompletableFuture<Boolean> recoverMCPSystem() {
		return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
			try {
				LOGGER.info("尝试恢复MCP系统...");
				
				// 如果MCP系统不健康，尝试重新初始化
				if (!isMCPHealthy()) {
					// 先安全关闭现有系统
					shutdownMCPSystemSafely();
					
					// 等待一段时间
					Thread.sleep(2000);
					
					// 重新初始化
					LLMChatConfig config = LLMChatConfig.getInstance();
					initializeMCPSystem(config);
					
					// 等待初始化完成
					Thread.sleep(3000);
					
					// 检查恢复结果
					boolean recovered = isMCPHealthy();
					if (recovered) {
						LOGGER.info("MCP系统恢复成功");
						LogManager.getInstance().system("MCP system recovered successfully");
					} else {
						LOGGER.warn("MCP系统恢复失败");
						LogManager.getInstance().system("MCP system recovery failed");
					}
					
					return recovered;
				} else {
					LOGGER.info("MCP系统运行正常，无需恢复");
					return true;
				}
				
			} catch (Exception e) {
				LOGGER.error("MCP系统恢复过程出错: " + e.getMessage(), e);
				return false;
			}
		});
	}

	/**
	 * 安全执行MCP操作，失败时不影响主要功能
	 */
	public static <T> T safeMCPOperation(java.util.function.Supplier<T> operation, T fallbackValue) {
		try {
			if (isMCPAvailable()) {
				return operation.get();
			}
		} catch (Exception e) {
			System.err.println("MCP操作失败，使用降级处理: " + e.getMessage());
		}
		return fallbackValue;
	}

	/**
	 * 安全执行MCP操作（无返回值版本）
	 */
	public static void safeMCPOperation(Runnable operation) {
		try {
			if (isMCPAvailable()) {
				operation.run();
			}
		} catch (Exception e) {
			System.err.println("MCP操作失败: " + e.getMessage());
		}
	}
}