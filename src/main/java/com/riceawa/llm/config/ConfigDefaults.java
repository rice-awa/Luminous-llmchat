package com.riceawa.llm.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 配置默认值模板类
 * 集中管理所有默认配置，避免硬编码分散
 */
public class ConfigDefaults {
    
    // 基础配置默认值
    public static final String DEFAULT_PROMPT_TEMPLATE = "default";
    public static final double DEFAULT_TEMPERATURE = 0.7;
    public static final int DEFAULT_MAX_TOKENS = 8192;
    public static final int DEFAULT_MAX_CONTEXT_CHARACTERS = 60000;
    public static final boolean DEFAULT_ENABLE_HISTORY = true;
    public static final boolean DEFAULT_ENABLE_FUNCTION_CALLING = false;
    public static final boolean DEFAULT_ENABLE_BROADCAST = false;
    public static final int DEFAULT_HISTORY_RETENTION_DAYS = 30;
    
    // 上下文压缩配置默认值
    public static final String DEFAULT_COMPRESSION_MODEL = ""; // 空字符串表示使用当前模型
    public static final boolean DEFAULT_ENABLE_COMPRESSION_NOTIFICATION = true;

    // 消息预览配置默认值
    public static final int DEFAULT_MESSAGE_PREVIEW_COUNT = 5; // 恢复对话时显示的消息数量
    public static final int DEFAULT_MESSAGE_PREVIEW_MAX_LENGTH = 150; // 每条消息的最大显示长度
    
    // 全局上下文配置默认值
    public static final boolean DEFAULT_ENABLE_GLOBAL_CONTEXT = true;
    public static final String DEFAULT_GLOBAL_CONTEXT_PROMPT =
        "=== 当前游戏环境信息 ===\n" +
        "发起者：{{player_name}}\n" +
        "当前时间：{{current_time}}\n" +
        "在线玩家（{{player_count}}人）：{{online_players}}\n" +
        "游戏版本：{{game_version}}";

    // 标题生成配置默认值
    public static final boolean DEFAULT_ENABLE_TITLE_GENERATION = true;
    public static final String DEFAULT_TITLE_GENERATION_MODEL = ""; // 空字符串表示使用当前模型

    // Wiki API 配置默认值
    public static final String DEFAULT_WIKI_API_URL = "https://mcwiki.rice-awa.top";

    // API密钥占位符（用于检测无效密钥）
    public static final String API_KEY_PLACEHOLDER = "your-api-key-here";

    // 空字符串默认值（用于Provider和Model）
    public static final String EMPTY_STRING = "";
    
    /**
     * 创建默认的Provider列表
     */
    public static List<Provider> createDefaultProviders() {
        List<Provider> providers = new ArrayList<>();
        
        // OpenAI Provider
        providers.add(new Provider(
            "openai",
            "https://api.openai.com/v1",
            API_KEY_PLACEHOLDER,
            List.of("gpt-3.5-turbo", "gpt-4", "gpt-4-turbo", "gpt-4o", "gpt-4o-mini")
        ));
        
        // OpenRouter Provider
        providers.add(new Provider(
            "openrouter", 
            "https://openrouter.ai/api/v1",
            API_KEY_PLACEHOLDER,
            List.of(
                "anthropic/claude-3.5-sonnet",
                "google/gemini-2.5-pro-preview", 
                "anthropic/claude-sonnet-4",
                "openai/gpt-4o",
                "meta-llama/llama-3.1-405b-instruct"
            )
        ));
        
        // DeepSeek Provider
        providers.add(new Provider(
            "deepseek",
            "https://api.deepseek.com/v1", 
            API_KEY_PLACEHOLDER,
            List.of("deepseek-chat", "deepseek-reasoner")
        ));
        
        // Anthropic Provider
        providers.add(new Provider(
            "anthropic",
            "https://api.anthropic.com/v1",
            API_KEY_PLACEHOLDER,
            List.of("claude-3.5-sonnet", "claude-3-opus", "claude-3-haiku")
        ));
        
        // Google AI Provider
        providers.add(new Provider(
            "google",
            "https://generativelanguage.googleapis.com/v1beta",
            API_KEY_PLACEHOLDER,
            List.of("gemini-2.5-pro-preview", "gemini-1.5-pro", "gemini-1.5-flash")
        ));
        
        return providers;
    }
    
    /**
     * 创建默认的广播玩家集合
     */
    public static Set<String> createDefaultBroadcastPlayers() {
        return new HashSet<>();
    }
    
    /**
     * 检查API密钥是否为占位符（无效）
     */
    public static boolean isPlaceholderApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return true;
        }
        
        String key = apiKey.trim().toLowerCase();
        return key.equals(API_KEY_PLACEHOLDER.toLowerCase()) ||
               key.contains("your-api-key") ||
               key.contains("placeholder") ||
               key.contains("example") ||
               key.contains("replace-me") ||
               key.startsWith("sk-") && key.length() < 20; // 太短的sk-开头密钥也视为无效
    }
    
    /**
     * 获取配置项的显示名称（用于日志）
     */
    public static String getConfigDisplayName(String configKey) {
        switch (configKey) {
            case "maxContextCharacters": return "最大上下文字符数";
            case "defaultTemperature": return "默认温度";
            case "defaultMaxTokens": return "默认最大Token数";
            case "enableHistory": return "启用历史记录";
            case "enableFunctionCalling": return "启用函数调用";
            case "enableBroadcast": return "启用广播";
            case "compressionModel": return "压缩模型";
            case "enableCompressionNotification": return "启用压缩通知";
            case "enableGlobalContext": return "启用全局上下文";
            default: return configKey;
        }
    }
    
    /**
     * 验证配置值是否在合理范围内
     */
    public static boolean isValidConfigValue(String configKey, Object value) {
        if (value == null) return false;

        switch (configKey) {
            case "defaultTemperature":
                if (value instanceof Number) {
                    double temp = ((Number) value).doubleValue();
                    return temp >= 0.0 && temp <= 2.0;
                }
                return false;

            case "defaultMaxTokens":
            case "maxContextCharacters":
                if (value instanceof Number) {
                    int num = ((Number) value).intValue();
                    return num > 0 && num <= 1000000; // 合理的上限
                }
                return false;

            case "historyRetentionDays":
                if (value instanceof Number) {
                    int days = ((Number) value).intValue();
                    return days >= 1 && days <= 365; // 1天到1年
                }
                return false;

            default:
                return true; // 其他配置项不做特殊验证
        }
    }

    /**
     * 获取指定配置项的默认值
     */
    public static Object getDefaultValue(String configKey) {
        switch (configKey) {
            case "defaultPromptTemplate": return DEFAULT_PROMPT_TEMPLATE;
            case "defaultTemperature": return DEFAULT_TEMPERATURE;
            case "defaultMaxTokens": return DEFAULT_MAX_TOKENS;
            case "maxContextCharacters": return DEFAULT_MAX_CONTEXT_CHARACTERS;
            case "enableHistory": return DEFAULT_ENABLE_HISTORY;
            case "enableFunctionCalling": return DEFAULT_ENABLE_FUNCTION_CALLING;
            case "enableBroadcast": return DEFAULT_ENABLE_BROADCAST;
            case "historyRetentionDays": return DEFAULT_HISTORY_RETENTION_DAYS;
            case "compressionModel": return DEFAULT_COMPRESSION_MODEL;
            case "enableCompressionNotification": return DEFAULT_ENABLE_COMPRESSION_NOTIFICATION;
            case "enableGlobalContext": return DEFAULT_ENABLE_GLOBAL_CONTEXT;
            case "globalContextPrompt": return DEFAULT_GLOBAL_CONTEXT_PROMPT;
            case "enableTitleGeneration": return DEFAULT_ENABLE_TITLE_GENERATION;
            case "titleGenerationModel": return DEFAULT_TITLE_GENERATION_MODEL;
            case "wikiApiUrl": return DEFAULT_WIKI_API_URL;
            case "currentProvider": return EMPTY_STRING;
            case "currentModel": return EMPTY_STRING;
            default: return null;
        }
    }
}
