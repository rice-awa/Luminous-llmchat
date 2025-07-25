package com.riceawa.llm.service;

import com.riceawa.llm.core.LLMService;
import com.riceawa.llm.core.ConcurrencyManager;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.config.Provider;
import com.riceawa.llm.config.ConcurrencySettings;

import java.util.HashMap;
import java.util.List;
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

        // 初始化并发管理器
        ConcurrencySettings concurrencySettings = config.getConcurrencySettings();
        ConcurrencyManager.ConcurrencyConfig concurrencyConfig = new ConcurrencyManager.ConcurrencyConfig(
            concurrencySettings.getMaxConcurrentRequests(),
            concurrencySettings.getQueueCapacity(),
            concurrencySettings.getRequestTimeoutMs(),
            concurrencySettings.getCorePoolSize(),
            concurrencySettings.getMaximumPoolSize(),
            concurrencySettings.getKeepAliveTimeMs()
        );
        ConcurrencyManager.initialize(concurrencyConfig);

        // 从providers配置中加载服务
        List<Provider> providers = config.getProviders();
        for (Provider provider : providers) {
            if (provider.isValid()) {
                createServiceFromProvider(provider);
            }
        }

        // 设置默认服务
        String currentProvider = config.getCurrentProvider();
        if (!currentProvider.isEmpty() && services.containsKey(currentProvider)) {
            this.defaultServiceName = currentProvider;
        } else if (!services.isEmpty()) {
            // 如果没有设置当前provider，使用第一个可用的
            this.defaultServiceName = services.keySet().iterator().next();
        }
    }

    /**
     * 从Provider配置创建服务
     */
    private void createServiceFromProvider(Provider provider) {
        String name = provider.getName();
        String apiKey = provider.getApiKey();
        String baseUrl = provider.getApiBaseUrl();

        // 根据provider名称或baseUrl判断服务类型
        if (isOpenAICompatible(name, baseUrl)) {
            OpenAIService service = new OpenAIService(apiKey, baseUrl);
            services.put(name, service);
        }
        // 可以在这里添加其他服务类型的支持
        // 例如：Claude、Gemini等
    }

    /**
     * 判断是否为OpenAI兼容的服务
     */
    private boolean isOpenAICompatible(String name, String baseUrl) {
        // 大多数现代LLM API都兼容OpenAI的接口格式
        return true; // 暂时默认都使用OpenAI兼容的服务
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
        // 关闭旧的并发管理器
        ConcurrencyManager.getInstance().shutdown();

        services.clear();
        initializeServices();
    }

    /**
     * 获取默认服务名称
     */
    public String getDefaultServiceName() {
        return defaultServiceName;
    }

    /**
     * 根据provider名称获取支持的模型列表
     */
    public List<String> getSupportedModels(String providerName) {
        LLMChatConfig config = LLMChatConfig.getInstance();
        return config.getSupportedModels(providerName);
    }

    /**
     * 切换到指定的provider和model
     */
    public boolean switchToProvider(String providerName, String model) {
        LLMChatConfig config = LLMChatConfig.getInstance();

        // 检查provider是否存在
        Provider provider = config.getProvider(providerName);
        if (provider == null || !provider.isValid()) {
            return false;
        }

        // 检查model是否支持
        if (model != null && !provider.supportsModel(model)) {
            return false;
        }

        // 如果服务不存在，创建它
        if (!services.containsKey(providerName)) {
            createServiceFromProvider(provider);
        }

        // 更新配置
        config.setCurrentProvider(providerName);
        if (model != null) {
            config.setCurrentModel(model);
        }

        // 更新默认服务
        this.defaultServiceName = providerName;

        return true;
    }

    /**
     * 获取当前使用的provider和model信息
     */
    public Map<String, String> getCurrentProviderInfo() {
        LLMChatConfig config = LLMChatConfig.getInstance();
        Map<String, String> info = new HashMap<>();
        info.put("provider", config.getCurrentProvider());
        info.put("model", config.getCurrentModel());
        return info;
    }
}
