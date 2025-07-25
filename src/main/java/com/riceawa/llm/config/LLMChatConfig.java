package com.riceawa.llm.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * LLM聊天模组配置管理
 */
public class LLMChatConfig {
    private static LLMChatConfig instance;
    private final Gson gson;
    private final Path configFile;
    
    // 配置项
    private String defaultPromptTemplate = "default";
    private double defaultTemperature = 0.7;
    private int defaultMaxTokens = 8192;
    private int maxContextLength = 8192;
    private boolean enableHistory = true;
    private boolean enableFunctionCalling = false;
    private int historyRetentionDays = 30;

    // Providers配置
    private List<Provider> providers = new ArrayList<>();
    private String currentProvider = "";
    private String currentModel = "";

    private LLMChatConfig() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("lllmchat");
        this.configFile = configDir.resolve("config.json");
        
        // 确保配置目录存在
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create config directory", e);
        }
        
        // 加载配置
        loadConfig();
    }

    public static LLMChatConfig getInstance() {
        if (instance == null) {
            synchronized (LLMChatConfig.class) {
                if (instance == null) {
                    instance = new LLMChatConfig();
                }
            }
        }
        return instance;
    }

    /**
     * 加载配置
     */
    private void loadConfig() {
        if (!Files.exists(configFile)) {
            // 创建默认配置文件
            createDefaultConfig();
            saveConfig();
            return;
        }

        try (FileReader reader = new FileReader(configFile.toFile())) {
            ConfigData data = gson.fromJson(reader, ConfigData.class);
            if (data != null) {
                applyConfigData(data);
            }
        } catch (IOException e) {
            System.err.println("Failed to load config: " + e.getMessage());
            // 使用默认配置
        }
    }

    /**
     * 保存配置
     */
    public void saveConfig() {
        try (FileWriter writer = new FileWriter(configFile.toFile())) {
            ConfigData data = createConfigData();
            gson.toJson(data, writer);
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }

    /**
     * 重新加载配置
     */
    public void reload() {
        loadConfig();
    }

    /**
     * 创建默认配置
     */
    private void createDefaultConfig() {
        createDefaultProviders();
    }

    /**
     * 创建默认的providers配置
     */
    private void createDefaultProviders() {
        // 创建默认的providers配置
        List<Provider> defaultProviders = new ArrayList<>();

        // OpenAI Provider示例
        Provider openaiProvider = new Provider();
        openaiProvider.setName("openai");
        openaiProvider.setApiBaseUrl("https://api.openai.com/v1");
        openaiProvider.setApiKey("your-openai-api-key-here");
        openaiProvider.setModels(List.of("gpt-3.5-turbo", "gpt-4", "gpt-4-turbo", "gpt-4o"));
        defaultProviders.add(openaiProvider);

        // OpenRouter Provider示例
        Provider openrouterProvider = new Provider();
        openrouterProvider.setName("openrouter");
        openrouterProvider.setApiBaseUrl("https://openrouter.ai/api/v1");
        openrouterProvider.setApiKey("your-openrouter-api-key-here");
        openrouterProvider.setModels(List.of(
            "anthropic/claude-3.5-sonnet",
            "google/gemini-2.5-pro-preview",
            "anthropic/claude-sonnet-4"
        ));
        defaultProviders.add(openrouterProvider);

        // DeepSeek Provider示例
        Provider deepseekProvider = new Provider();
        deepseekProvider.setName("deepseek");
        deepseekProvider.setApiBaseUrl("https://api.deepseek.com/v1");
        deepseekProvider.setApiKey("your-deepseek-api-key-here");
        deepseekProvider.setModels(List.of("deepseek-chat", "deepseek-reasoner"));
        defaultProviders.add(deepseekProvider);

        this.providers = defaultProviders;
        this.currentProvider = "openai"; // 默认使用OpenAI
        this.currentModel = "gpt-3.5-turbo"; // 默认模型
    }

    /**
     * 获取当前provider的默认模型
     */
    private String getDefaultModelForCurrentProvider() {
        if (currentProvider == null || currentProvider.isEmpty()) {
            return "";
        }

        Provider provider = getProvider(currentProvider);
        if (provider != null && provider.getModels() != null && !provider.getModels().isEmpty()) {
            return provider.getModels().get(0);
        }

        return "";
    }

    /**
     * 应用配置数据
     */
    private void applyConfigData(ConfigData data) {
        this.defaultPromptTemplate = data.defaultPromptTemplate != null ? data.defaultPromptTemplate : "default";
        this.defaultTemperature = data.defaultTemperature != null ? data.defaultTemperature : 0.7;
        this.defaultMaxTokens = data.defaultMaxTokens != null ? data.defaultMaxTokens : 8192;
        this.maxContextLength = data.maxContextLength != null ? data.maxContextLength : 8192;
        this.enableHistory = data.enableHistory != null ? data.enableHistory : true;
        this.enableFunctionCalling = data.enableFunctionCalling != null ? data.enableFunctionCalling : true;
        this.historyRetentionDays = data.historyRetentionDays != null ? data.historyRetentionDays : 30;

        // 处理providers配置 - 如果为null或空，创建默认配置
        if (data.providers == null || data.providers.isEmpty()) {
            createDefaultProviders();
        } else {
            this.providers = data.providers;
        }

        // 处理当前provider和model配置
        this.currentProvider = data.currentProvider != null && !data.currentProvider.isEmpty() ?
            data.currentProvider : (this.providers.isEmpty() ? "" : this.providers.get(0).getName());
        this.currentModel = data.currentModel != null && !data.currentModel.isEmpty() ?
            data.currentModel : getDefaultModelForCurrentProvider();
    }

    /**
     * 创建配置数据
     */
    private ConfigData createConfigData() {
        ConfigData data = new ConfigData();
        data.defaultPromptTemplate = this.defaultPromptTemplate;
        data.defaultTemperature = this.defaultTemperature;
        data.defaultMaxTokens = this.defaultMaxTokens;
        data.maxContextLength = this.maxContextLength;
        data.enableHistory = this.enableHistory;
        data.enableFunctionCalling = this.enableFunctionCalling;
        data.historyRetentionDays = this.historyRetentionDays;
        data.providers = this.providers;
        data.currentProvider = this.currentProvider;
        data.currentModel = this.currentModel;
        return data;
    }


    // Getters and Setters

    public String getDefaultPromptTemplate() {
        return defaultPromptTemplate;
    }

    public void setDefaultPromptTemplate(String defaultPromptTemplate) {
        this.defaultPromptTemplate = defaultPromptTemplate;
        saveConfig();
    }

    public double getDefaultTemperature() {
        return defaultTemperature;
    }

    public void setDefaultTemperature(double defaultTemperature) {
        this.defaultTemperature = defaultTemperature;
        saveConfig();
    }

    public int getDefaultMaxTokens() {
        return defaultMaxTokens;
    }

    public void setDefaultMaxTokens(int defaultMaxTokens) {
        this.defaultMaxTokens = defaultMaxTokens;
        saveConfig();
    }

    public int getMaxContextLength() {
        return maxContextLength;
    }

    public void setMaxContextLength(int maxContextLength) {
        this.maxContextLength = maxContextLength;
        saveConfig();
    }

    public boolean isEnableHistory() {
        return enableHistory;
    }

    public void setEnableHistory(boolean enableHistory) {
        this.enableHistory = enableHistory;
        saveConfig();
    }

    public boolean isEnableFunctionCalling() {
        return enableFunctionCalling;
    }

    public void setEnableFunctionCalling(boolean enableFunctionCalling) {
        this.enableFunctionCalling = enableFunctionCalling;
        saveConfig();
    }

    public int getHistoryRetentionDays() {
        return historyRetentionDays;
    }

    public void setHistoryRetentionDays(int historyRetentionDays) {
        this.historyRetentionDays = historyRetentionDays;
        saveConfig();
    }



    // Providers相关方法
    public List<Provider> getProviders() {
        return new ArrayList<>(providers);
    }

    public void setProviders(List<Provider> providers) {
        this.providers = providers != null ? new ArrayList<>(providers) : new ArrayList<>();
        saveConfig();
    }

    public void addProvider(Provider provider) {
        if (provider != null && provider.isValid()) {
            // 移除同名的provider
            providers.removeIf(p -> p.getName().equals(provider.getName()));
            providers.add(provider);
            saveConfig();
        }
    }

    public void removeProvider(String providerName) {
        providers.removeIf(p -> p.getName().equals(providerName));
        saveConfig();
    }

    public Provider getProvider(String providerName) {
        return providers.stream()
                .filter(p -> p.getName().equals(providerName))
                .findFirst()
                .orElse(null);
    }

    public String getCurrentProvider() {
        return currentProvider;
    }

    public void setCurrentProvider(String currentProvider) {
        this.currentProvider = currentProvider != null ? currentProvider : "";
        saveConfig();
    }

    public String getCurrentModel() {
        return currentModel;
    }

    public void setCurrentModel(String currentModel) {
        this.currentModel = currentModel != null ? currentModel : "";
        saveConfig();
    }

    /**
     * 获取当前provider的配置
     */
    public Provider getCurrentProviderConfig() {
        if (currentProvider.isEmpty()) {
            return null;
        }
        return getProvider(currentProvider);
    }

    /**
     * 检查指定provider是否支持指定模型
     */
    public boolean isModelSupported(String providerName, String model) {
        Provider provider = getProvider(providerName);
        return provider != null && provider.supportsModel(model);
    }

    /**
     * 获取指定provider支持的所有模型
     */
    public List<String> getSupportedModels(String providerName) {
        Provider provider = getProvider(providerName);
        return provider != null ? new ArrayList<>(provider.getModels()) : new ArrayList<>();
    }

    /**
     * 配置数据类
     */
    private static class ConfigData {
        String defaultPromptTemplate;
        Double defaultTemperature;
        Integer defaultMaxTokens;
        Integer maxContextLength;
        Boolean enableHistory;
        Boolean enableFunctionCalling;
        Integer historyRetentionDays;

        // Providers配置
        List<Provider> providers;
        String currentProvider;
        String currentModel;
    }
}
