package com.riceawa.llm.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * LLM聊天模组配置管理
 */
public class LLMChatConfig {
    private static LLMChatConfig instance;
    private final Gson gson;
    private final Path configFile;
    
    // 配置项
    private String openaiApiKey = "";
    private String openaiBaseUrl = "https://api.openai.com/v1";
    private String defaultModel = "gpt-3.5-turbo";
    private String defaultService = "openai";
    private String defaultPromptTemplate = "default";
    private double defaultTemperature = 0.7;
    private int defaultMaxTokens = 2048;
    private int maxContextLength = 4000;
    private boolean enableHistory = true;
    private boolean enableFunctionCalling = false;
    private int historyRetentionDays = 30;
    private Map<String, String> customApiKeys = new HashMap<>();
    private Map<String, String> customBaseUrls = new HashMap<>();

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
     * 应用配置数据
     */
    private void applyConfigData(ConfigData data) {
        this.openaiApiKey = data.openaiApiKey != null ? data.openaiApiKey : "";
        this.openaiBaseUrl = data.openaiBaseUrl != null ? data.openaiBaseUrl : "https://api.openai.com/v1";
        this.defaultModel = data.defaultModel != null ? data.defaultModel : "gpt-3.5-turbo";
        this.defaultService = data.defaultService != null ? data.defaultService : "openai";
        this.defaultPromptTemplate = data.defaultPromptTemplate != null ? data.defaultPromptTemplate : "default";
        this.defaultTemperature = data.defaultTemperature != null ? data.defaultTemperature : 0.7;
        this.defaultMaxTokens = data.defaultMaxTokens != null ? data.defaultMaxTokens : 2048;
        this.maxContextLength = data.maxContextLength != null ? data.maxContextLength : 4000;
        this.enableHistory = data.enableHistory != null ? data.enableHistory : true;
        this.enableFunctionCalling = data.enableFunctionCalling != null ? data.enableFunctionCalling : false;
        this.historyRetentionDays = data.historyRetentionDays != null ? data.historyRetentionDays : 30;
        this.customApiKeys = data.customApiKeys != null ? data.customApiKeys : new HashMap<>();
        this.customBaseUrls = data.customBaseUrls != null ? data.customBaseUrls : new HashMap<>();
    }

    /**
     * 创建配置数据
     */
    private ConfigData createConfigData() {
        ConfigData data = new ConfigData();
        data.openaiApiKey = this.openaiApiKey;
        data.openaiBaseUrl = this.openaiBaseUrl;
        data.defaultModel = this.defaultModel;
        data.defaultService = this.defaultService;
        data.defaultPromptTemplate = this.defaultPromptTemplate;
        data.defaultTemperature = this.defaultTemperature;
        data.defaultMaxTokens = this.defaultMaxTokens;
        data.maxContextLength = this.maxContextLength;
        data.enableHistory = this.enableHistory;
        data.enableFunctionCalling = this.enableFunctionCalling;
        data.historyRetentionDays = this.historyRetentionDays;
        data.customApiKeys = this.customApiKeys;
        data.customBaseUrls = this.customBaseUrls;
        return data;
    }

    // Getters and Setters
    public String getOpenAIApiKey() {
        return openaiApiKey;
    }

    public void setOpenAIApiKey(String openaiApiKey) {
        this.openaiApiKey = openaiApiKey;
        saveConfig();
    }

    public String getOpenAIBaseUrl() {
        return openaiBaseUrl;
    }

    public void setOpenAIBaseUrl(String openaiBaseUrl) {
        this.openaiBaseUrl = openaiBaseUrl;
        saveConfig();
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
        saveConfig();
    }

    public String getDefaultService() {
        return defaultService;
    }

    public void setDefaultService(String defaultService) {
        this.defaultService = defaultService;
        saveConfig();
    }

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

    public Map<String, String> getCustomApiKeys() {
        return new HashMap<>(customApiKeys);
    }

    public void setCustomApiKey(String service, String apiKey) {
        customApiKeys.put(service, apiKey);
        saveConfig();
    }

    public String getCustomApiKey(String service) {
        return customApiKeys.get(service);
    }

    public Map<String, String> getCustomBaseUrls() {
        return new HashMap<>(customBaseUrls);
    }

    public void setCustomBaseUrl(String service, String baseUrl) {
        customBaseUrls.put(service, baseUrl);
        saveConfig();
    }

    public String getCustomBaseUrl(String service) {
        return customBaseUrls.get(service);
    }

    /**
     * 配置数据类
     */
    private static class ConfigData {
        String openaiApiKey;
        String openaiBaseUrl;
        String defaultModel;
        String defaultService;
        String defaultPromptTemplate;
        Double defaultTemperature;
        Integer defaultMaxTokens;
        Integer maxContextLength;
        Boolean enableHistory;
        Boolean enableFunctionCalling;
        Integer historyRetentionDays;
        Map<String, String> customApiKeys;
        Map<String, String> customBaseUrls;
    }
}
