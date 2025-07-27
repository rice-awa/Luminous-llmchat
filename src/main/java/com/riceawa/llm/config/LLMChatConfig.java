package com.riceawa.llm.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.riceawa.llm.logging.LogConfig;

import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * LLM聊天模组配置管理
 */
public class LLMChatConfig {
    private static LLMChatConfig instance;
    private final Gson gson;
    private final Path configFile;
    
    // 配置版本，用于配置升级
    private static final String CURRENT_CONFIG_VERSION = "1.5.1";

    // 配置项
    private String configVersion = CURRENT_CONFIG_VERSION;
    private String defaultPromptTemplate = "default";
    private double defaultTemperature = 0.7;
    private int defaultMaxTokens = 8192;
    private int maxContextLength = 8192;
    private boolean enableHistory = true;
    private boolean enableFunctionCalling = false;
    private boolean enableBroadcast = false;
    private Set<String> broadcastPlayers = new HashSet<>();
    private int historyRetentionDays = 30;

    // 全局上下文配置
    private boolean enableGlobalContext = true;
    private String globalContextPrompt = "=== 当前游戏环境信息 ===\n发起者：{{player_name}}\n当前时间：{{current_time}}\n在线玩家（{{player_count}}人）：{{online_players}}\n游戏版本：{{game_version}}";

    // 并发配置
    private ConcurrencySettings concurrencySettings = ConcurrencySettings.createDefault();

    // 日志配置
    private LogConfig logConfig = LogConfig.createDefault();

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

        try (InputStreamReader reader = new InputStreamReader(
                Files.newInputStream(configFile), StandardCharsets.UTF_8)) {
            ConfigData data = gson.fromJson(reader, ConfigData.class);
            if (data != null) {
                // 检查配置版本并升级
                boolean needsUpgrade = upgradeConfigIfNeeded(data);
                applyConfigData(data);

                // 如果配置被升级，保存新配置
                if (needsUpgrade) {
                    System.out.println("Configuration upgraded to version " + CURRENT_CONFIG_VERSION);
                    saveConfig();
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load config: " + e.getMessage());
            System.err.println("Creating backup and using default configuration...");

            // 备份损坏的配置文件
            backupCorruptedConfig();

            // 使用默认配置
            createDefaultConfig();
            saveConfig();
        }
    }

    /**
     * 保存配置
     */
    public void saveConfig() {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                Files.newOutputStream(configFile), StandardCharsets.UTF_8)) {
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
     * 检查并升级配置版本
     */
    private boolean upgradeConfigIfNeeded(ConfigData data) {
        String loadedVersion = data.configVersion;

        // 如果没有版本信息，说明是旧版本配置
        if (loadedVersion == null || loadedVersion.isEmpty()) {
            System.out.println("Upgrading configuration from legacy version to " + CURRENT_CONFIG_VERSION);
            return upgradeFromLegacy(data);
        }

        // 检查是否需要升级
        if (!CURRENT_CONFIG_VERSION.equals(loadedVersion)) {
            System.out.println("Upgrading configuration from " + loadedVersion + " to " + CURRENT_CONFIG_VERSION);
            return upgradeFromVersion(data, loadedVersion);
        }

        return false; // 不需要升级
    }

    /**
     * 从旧版本升级配置
     */
    private boolean upgradeFromLegacy(ConfigData data) {
        boolean upgraded = false;

        // 设置配置版本
        data.configVersion = CURRENT_CONFIG_VERSION;
        upgraded = true;

        // 添加缺失的并发配置
        if (data.concurrencySettings == null) {
            data.concurrencySettings = ConcurrencySettings.createDefault();
            System.out.println("Added default concurrency settings");
            upgraded = true;
        }

        // 确保日志配置存在
        if (data.logConfig == null) {
            data.logConfig = LogConfig.createDefault();
            System.out.println("Added default log configuration");
            upgraded = true;
        }

        // 确保providers配置存在
        if (data.providers == null || data.providers.isEmpty()) {
            createDefaultProviders();
            data.providers = this.providers;
            System.out.println("Added default providers configuration");
            upgraded = true;
        }

        return upgraded;
    }

    /**
     * 从指定版本升级配置
     */
    private boolean upgradeFromVersion(ConfigData data, String fromVersion) {
        boolean upgraded = false;

        // 设置新版本号
        data.configVersion = CURRENT_CONFIG_VERSION;
        upgraded = true;

        // 根据版本进行特定升级
        switch (fromVersion) {
            case "1.5.0":
                // 从1.5.0升级到1.5.1，添加并发配置
                if (data.concurrencySettings == null) {
                    data.concurrencySettings = ConcurrencySettings.createDefault();
                    System.out.println("Added concurrency settings for version 1.5.1");
                    upgraded = true;
                }
                break;

            default:
                // 对于未知版本，执行完整升级
                System.out.println("Unknown version " + fromVersion + ", performing full upgrade");
                return upgradeFromLegacy(data);
        }

        return upgraded;
    }

    /**
     * 备份损坏的配置文件
     */
    private void backupCorruptedConfig() {
        try {
            Path backupFile = configFile.getParent().resolve("config.json.backup." + System.currentTimeMillis());
            Files.copy(configFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Corrupted config backed up to: " + backupFile);
        } catch (IOException e) {
            System.err.println("Failed to backup corrupted config: " + e.getMessage());
        }
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
        this.configVersion = data.configVersion != null ? data.configVersion : CURRENT_CONFIG_VERSION;
        this.defaultPromptTemplate = data.defaultPromptTemplate != null ? data.defaultPromptTemplate : "default";
        this.defaultTemperature = data.defaultTemperature != null ? data.defaultTemperature : 0.7;
        this.defaultMaxTokens = data.defaultMaxTokens != null ? data.defaultMaxTokens : 8192;
        this.maxContextLength = data.maxContextLength != null ? data.maxContextLength : 8192;
        this.enableHistory = data.enableHistory != null ? data.enableHistory : true;
        this.enableFunctionCalling = data.enableFunctionCalling != null ? data.enableFunctionCalling : true;
        this.enableBroadcast = data.enableBroadcast != null ? data.enableBroadcast : false;
        this.broadcastPlayers = data.broadcastPlayers != null ? new HashSet<>(data.broadcastPlayers) : new HashSet<>();
        this.historyRetentionDays = data.historyRetentionDays != null ? data.historyRetentionDays : 30;
        this.enableGlobalContext = data.enableGlobalContext != null ? data.enableGlobalContext : true;
        this.globalContextPrompt = data.globalContextPrompt != null ? data.globalContextPrompt :
            "=== 当前游戏环境信息 ===\n发起者：{{player_name}}\n当前时间：{{current_time}}\n在线玩家（{{player_count}}人）：{{online_players}}\n游戏版本：{{game_version}}";

        // 处理并发配置
        this.concurrencySettings = data.concurrencySettings != null ? data.concurrencySettings : ConcurrencySettings.createDefault();

        // 处理日志配置
        this.logConfig = data.logConfig != null ? data.logConfig : LogConfig.createDefault();

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
        data.configVersion = this.configVersion;
        data.defaultPromptTemplate = this.defaultPromptTemplate;
        data.defaultTemperature = this.defaultTemperature;
        data.defaultMaxTokens = this.defaultMaxTokens;
        data.maxContextLength = this.maxContextLength;
        data.enableHistory = this.enableHistory;
        data.enableFunctionCalling = this.enableFunctionCalling;
        data.enableBroadcast = this.enableBroadcast;
        data.broadcastPlayers = new HashSet<>(this.broadcastPlayers);
        data.historyRetentionDays = this.historyRetentionDays;
        data.enableGlobalContext = this.enableGlobalContext;
        data.globalContextPrompt = this.globalContextPrompt;
        data.concurrencySettings = this.concurrencySettings;
        data.logConfig = this.logConfig;
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

    public boolean isEnableBroadcast() {
        return enableBroadcast;
    }

    public void setEnableBroadcast(boolean enableBroadcast) {
        this.enableBroadcast = enableBroadcast;
        saveConfig();
    }

    public Set<String> getBroadcastPlayers() {
        return new HashSet<>(broadcastPlayers);
    }

    public void setBroadcastPlayers(Set<String> broadcastPlayers) {
        this.broadcastPlayers = broadcastPlayers != null ? new HashSet<>(broadcastPlayers) : new HashSet<>();
        saveConfig();
    }

    public void addBroadcastPlayer(String playerName) {
        if (playerName != null && !playerName.trim().isEmpty()) {
            this.broadcastPlayers.add(playerName.trim());
            saveConfig();
        }
    }

    public void removeBroadcastPlayer(String playerName) {
        if (playerName != null) {
            this.broadcastPlayers.remove(playerName.trim());
            saveConfig();
        }
    }

    public boolean isBroadcastPlayer(String playerName) {
        return playerName != null && this.broadcastPlayers.contains(playerName.trim());
    }

    public void clearBroadcastPlayers() {
        this.broadcastPlayers.clear();
        saveConfig();
    }

    public int getHistoryRetentionDays() {
        return historyRetentionDays;
    }

    public void setHistoryRetentionDays(int historyRetentionDays) {
        this.historyRetentionDays = historyRetentionDays;
        saveConfig();
    }

    // 全局上下文配置相关方法
    public boolean isEnableGlobalContext() {
        return enableGlobalContext;
    }

    public void setEnableGlobalContext(boolean enableGlobalContext) {
        this.enableGlobalContext = enableGlobalContext;
        saveConfig();
    }

    public String getGlobalContextPrompt() {
        return globalContextPrompt;
    }

    public void setGlobalContextPrompt(String globalContextPrompt) {
        this.globalContextPrompt = globalContextPrompt != null ? globalContextPrompt : "";
        saveConfig();
    }

    // 并发配置相关方法
    public ConcurrencySettings getConcurrencySettings() {
        return concurrencySettings;
    }

    public void setConcurrencySettings(ConcurrencySettings concurrencySettings) {
        this.concurrencySettings = concurrencySettings != null ? concurrencySettings : ConcurrencySettings.createDefault();
        saveConfig();
    }

    // 日志配置相关方法
    public LogConfig getLogConfig() {
        return logConfig;
    }

    public void setLogConfig(LogConfig logConfig) {
        this.logConfig = logConfig != null ? logConfig : LogConfig.createDefault();
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
     * 检查是否是第一次使用（API密钥未配置）
     */
    public boolean isFirstTimeUse() {
        // 获取当前provider配置
        Provider currentProviderConfig = getCurrentProviderConfig();
        if (currentProviderConfig == null) {
            return true; // 没有当前provider，认为是第一次使用
        }

        String apiKey = currentProviderConfig.getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return true; // API密钥为空，认为是第一次使用
        }

        // 检查是否为默认占位符
        return apiKey.contains("your-") || apiKey.contains("-api-key-here");
    }

    /**
     * 检查当前配置是否有效（用于配置验证）
     */
    public boolean isConfigurationValid() {
        Provider currentProviderConfig = getCurrentProviderConfig();
        if (currentProviderConfig == null) {
            return false;
        }

        String apiKey = currentProviderConfig.getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return false;
        }

        // 检查是否为默认占位符
        if (apiKey.contains("your-") || apiKey.contains("-api-key-here")) {
            return false;
        }

        // 检查模型是否设置
        return currentModel != null && !currentModel.trim().isEmpty();
    }

    /**
     * 简化的配置恢复功能
     */
    public boolean validateAndCompleteConfig() {
        boolean updated = false;

        // 确保基本配置存在
        if (concurrencySettings == null) {
            concurrencySettings = ConcurrencySettings.createDefault();
            updated = true;
        }

        if (logConfig == null) {
            logConfig = LogConfig.createDefault();
            updated = true;
        }

        if (providers == null || providers.isEmpty()) {
            createDefaultProviders();
            updated = true;
        }

        if (currentProvider == null || currentProvider.isEmpty()) {
            if (!providers.isEmpty()) {
                currentProvider = providers.get(0).getName();
                updated = true;
            }
        }

        if (currentModel == null || currentModel.isEmpty()) {
            String defaultModel = getDefaultModelForCurrentProvider();
            if (!defaultModel.isEmpty()) {
                currentModel = defaultModel;
                updated = true;
            }
        }

        if (broadcastPlayers == null) {
            broadcastPlayers = new HashSet<>();
            updated = true;
        }

        // 如果有更新，保存配置
        if (updated) {
            saveConfig();
        }

        return updated;
    }

    /**
     * 获取配置版本
     */
    public String getConfigVersion() {
        return configVersion;
    }

    /**
     * 配置数据类
     */
    private static class ConfigData {
        String configVersion;
        String defaultPromptTemplate;
        Double defaultTemperature;
        Integer defaultMaxTokens;
        Integer maxContextLength;
        Boolean enableHistory;
        Boolean enableFunctionCalling;
        Boolean enableBroadcast;
        Set<String> broadcastPlayers;
        Integer historyRetentionDays;

        // 全局上下文配置
        Boolean enableGlobalContext;
        String globalContextPrompt;

        // 并发配置
        ConcurrencySettings concurrencySettings;

        // 日志配置
        LogConfig logConfig;

        // Providers配置
        List<Provider> providers;
        String currentProvider;
        String currentModel;
    }
}
