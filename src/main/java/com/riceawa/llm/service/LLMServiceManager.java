package com.riceawa.llm.service;

import com.riceawa.llm.core.LLMService;
import com.riceawa.llm.config.LLMChatConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * LLM服务管理器
 */
public class LLMServiceManager {
    private static LLMServiceManager instance;
    private final Map<String, LLMService> services;
    private String defaultServiceName;

    private LLMServiceManager() {
        this.services = new HashMap<>();
        this.defaultServiceName = "openai";
        initializeServices();
    }

    public static LLMServiceManager getInstance() {
        if (instance == null) {
            synchronized (LLMServiceManager.class) {
                if (instance == null) {
                    instance = new LLMServiceManager();
                }
            }
        }
        return instance;
    }

    /**
     * 初始化服务
     */
    private void initializeServices() {
        LLMChatConfig config = LLMChatConfig.getInstance();
        
        // 初始化OpenAI服务
        String openaiApiKey = config.getOpenAIApiKey();
        String openaiBaseUrl = config.getOpenAIBaseUrl();
        if (openaiApiKey != null && !openaiApiKey.trim().isEmpty()) {
            OpenAIService openaiService = new OpenAIService(openaiApiKey, openaiBaseUrl);
            services.put("openai", openaiService);
        }

        // 可以在这里添加其他服务的初始化
        // 例如：Claude、Gemini等
    }

    /**
     * 获取服务
     */
    public LLMService getService(String serviceName) {
        return services.get(serviceName);
    }

    /**
     * 获取默认服务
     */
    public LLMService getDefaultService() {
        return getService(defaultServiceName);
    }

    /**
     * 设置默认服务
     */
    public void setDefaultService(String serviceName) {
        if (services.containsKey(serviceName)) {
            this.defaultServiceName = serviceName;
        }
    }

    /**
     * 注册服务
     */
    public void registerService(String name, LLMService service) {
        services.put(name, service);
    }

    /**
     * 移除服务
     */
    public void removeService(String name) {
        services.remove(name);
    }

    /**
     * 获取所有服务名称
     */
    public Set<String> getServiceNames() {
        return services.keySet();
    }

    /**
     * 检查服务是否可用
     */
    public boolean isServiceAvailable(String serviceName) {
        LLMService service = getService(serviceName);
        return service != null && service.isAvailable();
    }

    /**
     * 获取可用的服务
     */
    public Map<String, LLMService> getAvailableServices() {
        Map<String, LLMService> availableServices = new HashMap<>();
        for (Map.Entry<String, LLMService> entry : services.entrySet()) {
            if (entry.getValue().isAvailable()) {
                availableServices.put(entry.getKey(), entry.getValue());
            }
        }
        return availableServices;
    }

    /**
     * 重新加载服务
     */
    public void reload() {
        services.clear();
        initializeServices();
    }

    /**
     * 获取默认服务名称
     */
    public String getDefaultServiceName() {
        return defaultServiceName;
    }
}
