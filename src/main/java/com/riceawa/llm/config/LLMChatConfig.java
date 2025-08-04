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
 * LLM聊天配置管理
 */
public class LLMChatConfig {
    private static LLMChatConfig instance;
    private final Gson gson;
    private final Path configFile;
    private boolean isInitializing = false;
    private ProviderManager providerManager;

    // 配置版本
    private static final String CURRENT_CONFIG_VERSION = "2.0.0";

    // 配置项 - 使用ConfigDefaults中的默认值
    private String configVersion = CURRENT_CONFIG_VERSION;
    private String defaultPromptTemplate = ConfigDefaults.DEFAULT_PROMPT_TEMPLATE;
    private double defaultTemperature = ConfigDefaults.DEFAULT_TEMPERATURE;
    private int defaultMaxTokens = ConfigDefaults.DEFAULT_MAX_TOKENS;
    private int maxContextCharacters = ConfigDefaults.DEFAULT_MAX_CONTEXT_CHARACTERS;
    private boolean enableHistory = ConfigDefaults.DEFAULT_ENABLE_HISTORY;
    private boolean enableFunctionCalling = ConfigDefaults.DEFAULT_ENABLE_FUNCTION_CALLING;
    private boolean enableBroadcast = ConfigDefaults.DEFAULT_ENABLE_BROADCAST;
    private Set<String> broadcastPlayers = ConfigDefaults.createDefaultBroadcastPlayers();
    private int historyRetentionDays = ConfigDefaults.DEFAULT_HISTORY_RETENTION_DAYS;

    // 上下文压缩配置
    private String compressionModel = ConfigDefaults.DEFAULT_COMPRESSION_MODEL;
    private boolean enableCompressionNotification = ConfigDefaults.DEFAULT_ENABLE_COMPRESSION_NOTIFICATION;

    // 消息预览配置
    private int messagePreviewCount = ConfigDefaults.DEFAULT_MESSAGE_PREVIEW_COUNT;
    private int messagePreviewMaxLength = ConfigDefaults.DEFAULT_MESSAGE_PREVIEW_MAX_LENGTH;

    // 全局上下文配置
    private boolean enableGlobalContext = ConfigDefaults.DEFAULT_ENABLE_GLOBAL_CONTEXT;
    private String globalContextPrompt = ConfigDefaults.DEFAULT_GLOBAL_CONTEXT_PROMPT;

    // 标题生成配置
    private boolean enableTitleGeneration = ConfigDefaults.DEFAULT_ENABLE_TITLE_GENERATION;
    private String titleGenerationModel = ConfigDefaults.DEFAULT_TITLE_GENERATION_MODEL;

    // Wiki API 配置
    private String wikiApiUrl = ConfigDefaults.DEFAULT_WIKI_API_URL;
    
    // 多轮函数调用配置
    private boolean enableRecursiveFunctionCalls = ConfigDefaults.DEFAULT_ENABLE_RECURSIVE_FUNCTION_CALLS;
    private int maxFunctionCallDepth = ConfigDefaults.DEFAULT_MAX_FUNCTION_CALL_DEPTH;
    private int functionCallTimeoutMs = ConfigDefaults.DEFAULT_FUNCTION_CALL_TIMEOUT_MS;

    // 并发配置
    private ConcurrencySettings concurrencySettings = ConcurrencySettings.createDefault();

    // 日志配置
    private LogConfig logConfig = LogConfig.createDefault();

    // Providers配置
    private List<Provider> providers = new ArrayList<>();
    private String currentProvider = ConfigDefaults.EMPTY_STRING;
    private String currentModel = ConfigDefaults.EMPTY_STRING;

    private LLMChatConfig() {
        this.isInitializing = true;
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

        // 初始化Provider管理器
        this.providerManager = new ProviderManager(this.providers);

        // 验证和修复配置
        validateAndFixConfiguration();

        this.isInitializing = false;

        System.out.println("LLMChatConfig initialized successfully");
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
            System.out.println("Config file does not exist, creating default configuration...");
            createDefaultConfig();
            System.out.println("Default configuration created with maxContextCharacters: " + this.maxContextCharacters);
            saveConfig();
            System.out.println("Default configuration saved to file");
            return;
        }

        try (InputStreamReader reader = new InputStreamReader(
                Files.newInputStream(configFile), StandardCharsets.UTF_8)) {
            ConfigData data = gson.fromJson(reader, ConfigData.class);
            if (data != null) {
                applyConfigData(data);
            } else {
                System.err.println("Failed to parse config file, creating default configuration");
                createDefaultConfig();
                saveConfig();
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
            System.out.println("Saving config with maxContextCharacters: " + data.maxContextCharacters);
            gson.toJson(data, writer);
            System.out.println("Configuration saved successfully");
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }

    /**
     * 重新加载配置
     */
    public void reload() {
        loadConfig();

        // 重载配置后，更新现有的上下文实例
        if (!isInitializing) {
            try {
                com.riceawa.llm.context.ChatContextManager.getInstance().updateMaxContextLength();
            } catch (Exception e) {
                System.err.println("Failed to update existing contexts after reload: " + e.getMessage());
            }

            // 触发异步健康检查
            triggerHealthCheck();
        }
    }

    /**
     * 触发provider健康检查
     */
    private void triggerHealthCheck() {
        try {
            ProviderManager providerManager = new ProviderManager(this.providers);
            providerManager.checkAllProvidersHealth().whenComplete((healthMap, throwable) -> {
                if (throwable != null) {
                    System.err.println("Provider health check failed: " + throwable.getMessage());
                } else {
                    System.out.println("Provider health check completed for " + healthMap.size() + " providers");
                }
            });
        } catch (Exception e) {
            System.err.println("Failed to trigger health check: " + e.getMessage());
        }
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
        // 设置配置版本
        this.configVersion = CURRENT_CONFIG_VERSION;

        // 使用ConfigDefaults中的默认值（字段已经在声明时初始化）
        // 只需要创建默认的Providers
        this.providers = ConfigDefaults.createDefaultProviders();

        // 自动选择第一个有效的Provider和Model
        selectInitialProviderAndModel();

        System.out.println("Created default configuration with " + this.providers.size() + " providers");
    }

    /**
     * 选择初始的Provider和Model
     */
    private void selectInitialProviderAndModel() {
        ProviderManager manager = new ProviderManager(this.providers);
        ProviderManager.ProviderModelResult result = manager.fixCurrentConfiguration("", "");

        if (result.isSuccess()) {
            this.currentProvider = result.getProviderName();
            this.currentModel = result.getModelName();
            System.out.println("Selected initial provider: " + this.currentProvider + ", model: " + this.currentModel);
        } else {
            this.currentProvider = ConfigDefaults.EMPTY_STRING;
            this.currentModel = ConfigDefaults.EMPTY_STRING;
            System.out.println("No valid provider configuration found: " + result.getMessage());
        }
    }



    /**
     * 应用配置数据
     */
    private void applyConfigData(ConfigData data) {
        // 使用ConfigDefaults提供默认值，避免硬编码
        this.configVersion = data.configVersion != null ? data.configVersion : CURRENT_CONFIG_VERSION;
        this.defaultPromptTemplate = data.defaultPromptTemplate != null ? data.defaultPromptTemplate : (String) ConfigDefaults.getDefaultValue("defaultPromptTemplate");
        this.defaultTemperature = data.defaultTemperature != null ? data.defaultTemperature : (Double) ConfigDefaults.getDefaultValue("defaultTemperature");
        this.defaultMaxTokens = data.defaultMaxTokens != null ? data.defaultMaxTokens : (Integer) ConfigDefaults.getDefaultValue("defaultMaxTokens");

        // 兼容旧配置：如果有maxContextLength，使用它作为maxContextCharacters
        if (data.maxContextLength != null) {
            this.maxContextCharacters = data.maxContextLength;
            System.out.println("Using legacy maxContextLength as maxContextCharacters: " + this.maxContextCharacters);
        } else if (data.maxContextCharacters != null) {
            this.maxContextCharacters = data.maxContextCharacters;
            System.out.println("Loaded maxContextCharacters from config: " + this.maxContextCharacters);
        } else {
            this.maxContextCharacters = ConfigDefaults.DEFAULT_MAX_CONTEXT_CHARACTERS;
            System.out.println("Applied default maxContextCharacters: " + this.maxContextCharacters);
        }

        this.enableHistory = data.enableHistory != null ? data.enableHistory : (Boolean) ConfigDefaults.getDefaultValue("enableHistory");
        this.enableFunctionCalling = data.enableFunctionCalling != null ? data.enableFunctionCalling : (Boolean) ConfigDefaults.getDefaultValue("enableFunctionCalling");
        this.enableBroadcast = data.enableBroadcast != null ? data.enableBroadcast : (Boolean) ConfigDefaults.getDefaultValue("enableBroadcast");
        this.broadcastPlayers = data.broadcastPlayers != null ? new HashSet<>(data.broadcastPlayers) : ConfigDefaults.createDefaultBroadcastPlayers();
        this.historyRetentionDays = data.historyRetentionDays != null ? data.historyRetentionDays : (Integer) ConfigDefaults.getDefaultValue("historyRetentionDays");
        this.enableGlobalContext = data.enableGlobalContext != null ? data.enableGlobalContext : (Boolean) ConfigDefaults.getDefaultValue("enableGlobalContext");
        this.globalContextPrompt = data.globalContextPrompt != null ? data.globalContextPrompt : (String) ConfigDefaults.getDefaultValue("globalContextPrompt");

        // 处理上下文压缩配置
        this.compressionModel = data.compressionModel != null ? data.compressionModel : (String) ConfigDefaults.getDefaultValue("compressionModel");
        this.enableCompressionNotification = data.enableCompressionNotification != null ? data.enableCompressionNotification : (Boolean) ConfigDefaults.getDefaultValue("enableCompressionNotification");

        // 处理标题生成配置
        this.enableTitleGeneration = data.enableTitleGeneration != null ? data.enableTitleGeneration : (Boolean) ConfigDefaults.getDefaultValue("enableTitleGeneration");
        this.titleGenerationModel = data.titleGenerationModel != null ? data.titleGenerationModel : (String) ConfigDefaults.getDefaultValue("titleGenerationModel");

        // 处理Wiki API配置
        this.wikiApiUrl = data.wikiApiUrl != null ? data.wikiApiUrl : (String) ConfigDefaults.getDefaultValue("wikiApiUrl");

        // 处理并发配置
        this.concurrencySettings = data.concurrencySettings != null ? data.concurrencySettings : ConcurrencySettings.createDefault();

        // 处理日志配置
        this.logConfig = data.logConfig != null ? data.logConfig : LogConfig.createDefault();

        // 处理providers配置 - 如果为null或空，创建默认配置
        if (data.providers == null || data.providers.isEmpty()) {
            this.providers = ConfigDefaults.createDefaultProviders();
        } else {
            this.providers = data.providers;
        }

        // 处理当前provider和model配置
        this.currentProvider = data.currentProvider != null ? data.currentProvider : (String) ConfigDefaults.getDefaultValue("currentProvider");
        this.currentModel = data.currentModel != null ? data.currentModel : (String) ConfigDefaults.getDefaultValue("currentModel");

        // 重新初始化Provider管理器
        this.providerManager = new ProviderManager(this.providers);
    }

    /**
     * 验证和修复配置
     */
    private void validateAndFixConfiguration() {
        boolean needsSave = false;

        // 验证基础配置值
        if (!ConfigDefaults.isValidConfigValue("maxContextCharacters", this.maxContextCharacters)) {
            System.out.println("Invalid maxContextCharacters (" + this.maxContextCharacters + "), resetting to default");
            this.maxContextCharacters = ConfigDefaults.DEFAULT_MAX_CONTEXT_CHARACTERS;
            needsSave = true;
        }

        if (!ConfigDefaults.isValidConfigValue("defaultTemperature", this.defaultTemperature)) {
            System.out.println("Invalid defaultTemperature (" + this.defaultTemperature + "), resetting to default");
            this.defaultTemperature = ConfigDefaults.DEFAULT_TEMPERATURE;
            needsSave = true;
        }

        if (!ConfigDefaults.isValidConfigValue("defaultMaxTokens", this.defaultMaxTokens)) {
            System.out.println("Invalid defaultMaxTokens (" + this.defaultMaxTokens + "), resetting to default");
            this.defaultMaxTokens = ConfigDefaults.DEFAULT_MAX_TOKENS;
            needsSave = true;
        }

        // 验证和修复Provider配置
        ProviderManager.ProviderModelResult result = providerManager.fixCurrentConfiguration(
            this.currentProvider, this.currentModel);

        if (result.isSuccess()) {
            if (!result.getProviderName().equals(this.currentProvider) ||
                !result.getModelName().equals(this.currentModel)) {
                this.currentProvider = result.getProviderName();
                this.currentModel = result.getModelName();
                needsSave = true;
                System.out.println("Provider configuration fixed: " + result.getMessage());
            }
        } else {
            System.out.println("Provider configuration issue: " + result.getMessage());
        }

        // 如果有修复，保存配置
        if (needsSave && !isInitializing) {
            saveConfig();
        }
    }



    /**
     * 创建配置数据
     */
    private ConfigData createConfigData() {
        ConfigData data = new ConfigData();

        // 基础配置
        data.configVersion = this.configVersion;
        data.defaultPromptTemplate = this.defaultPromptTemplate;
        data.defaultTemperature = this.defaultTemperature;
        data.defaultMaxTokens = this.defaultMaxTokens;
        data.maxContextCharacters = this.maxContextCharacters;

        // 功能开关配置
        data.enableHistory = this.enableHistory;
        data.enableFunctionCalling = this.enableFunctionCalling;
        data.enableBroadcast = this.enableBroadcast;
        data.broadcastPlayers = new HashSet<>(this.broadcastPlayers);
        data.historyRetentionDays = this.historyRetentionDays;

        // 全局上下文配置
        data.enableGlobalContext = this.enableGlobalContext;
        data.globalContextPrompt = this.globalContextPrompt;

        // 压缩和标题生成功能配置
        data.enableCompressionNotification = this.enableCompressionNotification;
        data.enableTitleGeneration = this.enableTitleGeneration;

        // Wiki API 配置
        data.wikiApiUrl = this.wikiApiUrl;

        // 系统配置
        data.concurrencySettings = this.concurrencySettings;
        data.logConfig = this.logConfig;
        data.providers = this.providers;

        // 模型相关配置（放在最后）
        data.compressionModel = this.compressionModel;
        data.titleGenerationModel = this.titleGenerationModel;
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

    public int getMaxContextCharacters() {
        return maxContextCharacters;
    }

    public void setMaxContextCharacters(int maxContextCharacters) {
        this.maxContextCharacters = maxContextCharacters;

        // 只在非初始化状态时保存配置
        if (!isInitializing) {
            saveConfig();

            // 更新现有的上下文实例
            try {
                com.riceawa.llm.context.ChatContextManager.getInstance().updateMaxContextLength();
            } catch (Exception e) {
                System.err.println("Failed to update existing contexts with new max context characters: " + e.getMessage());
            }
        }
    }

    // 保持向后兼容的方法名
    public int getMaxContextLength() {
        return maxContextCharacters;
    }

    public void setMaxContextLength(int maxContextLength) {
        setMaxContextCharacters(maxContextLength);
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

    // 标题生成配置相关方法
    public boolean isEnableTitleGeneration() {
        return enableTitleGeneration;
    }

    public void setEnableTitleGeneration(boolean enableTitleGeneration) {
        this.enableTitleGeneration = enableTitleGeneration;
        saveConfig();
    }

    public String getTitleGenerationModel() {
        return titleGenerationModel;
    }

    public void setTitleGenerationModel(String titleGenerationModel) {
        this.titleGenerationModel = titleGenerationModel != null ? titleGenerationModel : "";
        saveConfig();
    }

    /**
     * 获取有效的标题生成模型
     * 如果未设置专门的标题生成模型，则使用当前模型
     */
    public String getEffectiveTitleGenerationModel() {
        if (titleGenerationModel != null && !titleGenerationModel.trim().isEmpty()) {
            return titleGenerationModel;
        }
        return getCurrentModel();
    }

    // 上下文压缩配置相关方法
    public String getCompressionModel() {
        return compressionModel;
    }

    public void setCompressionModel(String compressionModel) {
        this.compressionModel = compressionModel != null ? compressionModel : "";
        saveConfig();
    }

    public boolean isEnableCompressionNotification() {
        return enableCompressionNotification;
    }

    public void setEnableCompressionNotification(boolean enableCompressionNotification) {
        this.enableCompressionNotification = enableCompressionNotification;
        saveConfig();
    }

    // 消息预览配置的getter和setter方法
    public int getMessagePreviewCount() {
        return messagePreviewCount;
    }

    public void setMessagePreviewCount(int messagePreviewCount) {
        this.messagePreviewCount = Math.max(1, Math.min(10, messagePreviewCount)); // 限制在1-10之间
        saveConfig();
    }

    public int getMessagePreviewMaxLength() {
        return messagePreviewMaxLength;
    }

    public void setMessagePreviewMaxLength(int messagePreviewMaxLength) {
        this.messagePreviewMaxLength = Math.max(50, Math.min(500, messagePreviewMaxLength)); // 限制在50-500之间
        saveConfig();
    }

    /**
     * 获取用于压缩的模型，如果未设置则返回当前模型
     */
    public String getEffectiveCompressionModel() {
        if (compressionModel == null || compressionModel.trim().isEmpty()) {
            return getCurrentModel();
        }
        return compressionModel;
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

        // 重新初始化Provider管理器
        this.providerManager = new ProviderManager(this.providers);

        // 验证和修复当前配置
        validateAndFixConfiguration();

        saveConfig();
    }

    public void addProvider(Provider provider) {
        if (provider != null && provider.isValid()) {
            // 移除同名的provider
            providers.removeIf(p -> p.getName().equals(provider.getName()));
            providers.add(provider);

            // 重新初始化Provider管理器
            this.providerManager = new ProviderManager(this.providers);

            // 如果当前没有有效配置，尝试使用新添加的provider
            if (!isProviderModelValid(this.currentProvider, this.currentModel)) {
                validateAndFixConfiguration();
            }

            saveConfig();
        }
    }

    public void removeProvider(String providerName) {
        // 检查是否要删除当前provider
        boolean removingCurrentProvider = providerName.equals(currentProvider);

        // 删除provider
        providers.removeIf(p -> p.getName().equals(providerName));

        // 重新初始化Provider管理器
        this.providerManager = new ProviderManager(this.providers);

        // 如果删除的是当前provider，需要切换到其他provider
        if (removingCurrentProvider) {
            // 使用Provider管理器自动选择新的配置
            ProviderManager.ProviderModelResult result = providerManager.fixCurrentConfiguration("", "");
            if (result.isSuccess()) {
                this.currentProvider = result.getProviderName();
                this.currentModel = result.getModelName();
                System.out.println("Switched to provider: " + this.currentProvider + ", model: " + this.currentModel);
            } else {
                this.currentProvider = ConfigDefaults.EMPTY_STRING;
                this.currentModel = ConfigDefaults.EMPTY_STRING;
                System.out.println("No valid provider available after removal");
            }
        }

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
     * 获取所有有效的Provider列表
     */
    public List<Provider> getValidProviders() {
        return providerManager != null ? providerManager.getValidProviders() : new ArrayList<>();
    }

    /**
     * 获取配置状态报告
     */
    public String getConfigurationReport() {
        if (providerManager == null) {
            return "Provider管理器未初始化";
        }
        return providerManager.getConfigurationReport().getReportText();
    }

    /**
     * 自动修复当前的Provider和Model配置
     * @return 修复结果信息
     */
    public String autoFixConfiguration() {
        if (providerManager == null) {
            return "Provider管理器未初始化";
        }

        ProviderManager.ProviderModelResult result = providerManager.fixCurrentConfiguration(
            this.currentProvider, this.currentModel);

        if (result.isSuccess()) {
            boolean changed = false;
            if (!result.getProviderName().equals(this.currentProvider)) {
                this.currentProvider = result.getProviderName();
                changed = true;
            }
            if (!result.getModelName().equals(this.currentModel)) {
                this.currentModel = result.getModelName();
                changed = true;
            }

            if (changed) {
                saveConfig();
            }

            return result.getMessage();
        } else {
            return "配置修复失败: " + result.getMessage();
        }
    }

    /**
     * 检查指定的Provider和Model组合是否有效
     */
    public boolean isProviderModelValid(String providerName, String modelName) {
        return providerManager != null &&
               providerManager.isProviderModelValid(providerName, modelName);
    }



    /**
     * 检查是否有任何有效的provider配置
     */
    public boolean hasAnyValidProvider() {
        return providerManager != null && !providerManager.getValidProviders().isEmpty();
    }

    /**
     * 获取第一个有效配置的provider
     */
    public Provider getFirstValidProvider() {
        return providerManager != null ?
            providerManager.getFirstValidProvider().orElse(null) : null;
    }

    /**
     * 检查是否是第一次使用（所有API密钥都未配置）
     */
    public boolean isFirstTimeUse() {
        return !hasAnyValidProvider();
    }

    /**
     * 检查当前配置是否有效（用于配置验证）
     */
    public boolean isConfigurationValid() {
        // 检查是否有任何有效的provider
        if (!hasAnyValidProvider()) {
            return false;
        }

        // 使用Provider管理器检查和修复配置
        ProviderManager.ProviderModelResult result = providerManager.fixCurrentConfiguration(
            this.currentProvider, this.currentModel);

        if (result.isSuccess()) {
            // 如果配置被修复，更新并保存
            if (!result.getProviderName().equals(this.currentProvider) ||
                !result.getModelName().equals(this.currentModel)) {
                this.currentProvider = result.getProviderName();
                this.currentModel = result.getModelName();
                saveConfig();
            }
            return true;
        }

        return false;
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
            providers = ConfigDefaults.createDefaultProviders();
            updated = true;
        }

        // 重新初始化Provider管理器
        this.providerManager = new ProviderManager(this.providers);

        // 使用Provider管理器修复配置
        ProviderManager.ProviderModelResult result = providerManager.fixCurrentConfiguration(
            this.currentProvider, this.currentModel);

        if (result.isSuccess()) {
            if (!result.getProviderName().equals(this.currentProvider) ||
                !result.getModelName().equals(this.currentModel)) {
                this.currentProvider = result.getProviderName();
                this.currentModel = result.getModelName();
                updated = true;
            }
        } else {
            // 如果没有有效配置，清空
            this.currentProvider = ConfigDefaults.EMPTY_STRING;
            this.currentModel = ConfigDefaults.EMPTY_STRING;
        }

        if (broadcastPlayers == null) {
            broadcastPlayers = ConfigDefaults.createDefaultBroadcastPlayers();
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
     * 获取Wiki API URL
     */
    public String getWikiApiUrl() {
        return wikiApiUrl;
    }

    /**
     * 设置Wiki API URL
     */
    public void setWikiApiUrl(String wikiApiUrl) {
        this.wikiApiUrl = wikiApiUrl != null ? wikiApiUrl : ConfigDefaults.DEFAULT_WIKI_API_URL;
        saveConfig();
    }

    /**
     * 获取是否启用递归函数调用
     */
    public boolean isEnableRecursiveFunctionCalls() {
        return enableRecursiveFunctionCalls;
    }

    /**
     * 设置是否启用递归函数调用
     */
    public void setEnableRecursiveFunctionCalls(boolean enableRecursiveFunctionCalls) {
        this.enableRecursiveFunctionCalls = enableRecursiveFunctionCalls;
        saveConfig();
    }

    /**
     * 获取最大函数调用深度
     */
    public int getMaxFunctionCallDepth() {
        return maxFunctionCallDepth;
    }

    /**
     * 设置最大函数调用深度
     */
    public void setMaxFunctionCallDepth(int maxFunctionCallDepth) {
        this.maxFunctionCallDepth = Math.max(1, Math.min(10, maxFunctionCallDepth)); // 限制在1-10之间
        saveConfig();
    }

    /**
     * 获取函数调用超时时间（毫秒）
     */
    public int getFunctionCallTimeoutMs() {
        return functionCallTimeoutMs;
    }

    /**
     * 设置函数调用超时时间（毫秒）
     */
    public void setFunctionCallTimeoutMs(int functionCallTimeoutMs) {
        this.functionCallTimeoutMs = Math.max(5000, Math.min(60000, functionCallTimeoutMs)); // 限制在5-60秒之间
        saveConfig();
    }

    /**
     * 配置数据类
     */
    private static class ConfigData {
        // 基础配置
        String configVersion;
        String defaultPromptTemplate;
        Double defaultTemperature;
        Integer defaultMaxTokens;
        Integer maxContextLength; // 保留用于向后兼容
        Integer maxContextCharacters;

        // 功能开关配置
        Boolean enableHistory;
        Boolean enableFunctionCalling;
        Boolean enableBroadcast;
        Set<String> broadcastPlayers;
        Integer historyRetentionDays;

        // 全局上下文配置
        Boolean enableGlobalContext;
        String globalContextPrompt;

        // 压缩和标题生成功能配置
        Boolean enableCompressionNotification;
        Boolean enableTitleGeneration;

        // Wiki API 配置
        String wikiApiUrl;

        // 系统配置
        ConcurrencySettings concurrencySettings;
        LogConfig logConfig;
        List<Provider> providers;

        // 模型相关配置（放在最后）
        String compressionModel;
        String titleGenerationModel;
        String currentProvider;
        String currentModel;
    }
}
