package com.riceawa.llm.template;

import com.google.gson.annotations.SerializedName;
import com.riceawa.llm.config.LLMChatConfig;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.fabricmc.loader.api.FabricLoader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 提示词模板
 */
public class PromptTemplate {
    @SerializedName("id")
    private String id;
    
    @SerializedName("name")
    private String name;
    
    @SerializedName("description")
    private String description;
    
    @SerializedName("system_prompt")
    private String systemPrompt;
    
    @SerializedName("user_prompt_prefix")
    private String userPromptPrefix;
    
    @SerializedName("user_prompt_suffix")
    private String userPromptSuffix;
    
    @SerializedName("variables")
    private Map<String, String> variables;
    
    @SerializedName("enabled")
    private boolean enabled;

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    public PromptTemplate() {
        this.variables = new HashMap<>();
        this.enabled = true;
    }

    public PromptTemplate(String id, String name, String description, String systemPrompt) {
        this();
        this.id = id;
        this.name = name;
        this.description = description;
        this.systemPrompt = systemPrompt;
    }

    /**
     * 渲染系统提示词
     */
    public String renderSystemPrompt() {
        return renderTemplate(systemPrompt, null);
    }

    /**
     * 渲染带全局上下文的系统提示词
     */
    public String renderSystemPromptWithContext(ServerPlayerEntity player, LLMChatConfig config) {
        StringBuilder result = new StringBuilder();

        // 添加原始系统提示词
        String originalPrompt = renderTemplate(systemPrompt, player);
        if (originalPrompt != null && !originalPrompt.trim().isEmpty()) {
            result.append(originalPrompt);
        }

        // 如果启用了全局上下文，添加全局上下文信息
        if (config.isEnableGlobalContext() && config.getGlobalContextPrompt() != null && !config.getGlobalContextPrompt().trim().isEmpty()) {
            if (result.length() > 0) {
                result.append("\n\n");
            }

            // 生成全局上下文信息
            String globalContext = generateGlobalContext(player, config.getGlobalContextPrompt());
            result.append(globalContext);
        }

        return result.toString();
    }

    /**
     * 渲染用户消息
     */
    public String renderUserMessage(String userInput) {
        return renderUserMessage(userInput, null);
    }

    /**
     * 渲染用户消息（带玩家信息）
     */
    public String renderUserMessage(String userInput, ServerPlayerEntity player) {
        StringBuilder result = new StringBuilder();

        if (userPromptPrefix != null && !userPromptPrefix.isEmpty()) {
            result.append(renderTemplate(userPromptPrefix, player));
        }

        result.append(userInput);

        if (userPromptSuffix != null && !userPromptSuffix.isEmpty()) {
            result.append(renderTemplate(userPromptSuffix, player));
        }

        return result.toString();
    }

    /**
     * 渲染模板，替换变量（不带玩家信息）
     */
    private String renderTemplate(String template) {
        return renderTemplate(template, null);
    }

    /**
     * 渲染模板，替换变量（带玩家信息支持内置变量）
     */
    private String renderTemplate(String template, ServerPlayerEntity player) {
        if (template == null) {
            return "";
        }

        String result = template;
        Matcher matcher = VARIABLE_PATTERN.matcher(template);

        while (matcher.find()) {
            String variableName = matcher.group(1);
            String variableValue = getVariableValue(variableName, player);
            result = result.replace("{{" + variableName + "}}", variableValue);
        }

        return result;
    }

    /**
     * 获取变量值（支持内置变量和自定义变量）
     */
    private String getVariableValue(String variableName, ServerPlayerEntity player) {
        // 首先检查是否是内置变量
        String builtinValue = getBuiltinVariable(variableName, player);
        if (builtinValue != null) {
            return builtinValue;
        }

        // 如果不是内置变量，从自定义变量中获取
        return variables.getOrDefault(variableName, "");
    }

    /**
     * 获取内置变量值
     */
    private String getBuiltinVariable(String variableName, ServerPlayerEntity player) {
        switch (variableName.toLowerCase()) {
            case "player":
                return player != null ? player.getName().getString() : "Unknown";

            case "time":
                return java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            case "date":
                return java.time.LocalDate.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            case "hour":
                return String.valueOf(java.time.LocalTime.now().getHour());

            case "minute":
                return String.valueOf(java.time.LocalTime.now().getMinute());

            case "world":
                return player != null ? player.getWorld().getRegistryKey().getValue().toString() : "Unknown";

            case "dimension":
                if (player != null) {
                    String worldKey = player.getWorld().getRegistryKey().getValue().toString();
                    if (worldKey.contains("overworld")) return "主世界";
                    if (worldKey.contains("nether")) return "下界";
                    if (worldKey.contains("end")) return "末地";
                    return worldKey;
                }
                return "Unknown";

            case "x":
                return player != null ? String.valueOf((int) player.getX()) : "0";

            case "y":
                return player != null ? String.valueOf((int) player.getY()) : "0";

            case "z":
                return player != null ? String.valueOf((int) player.getZ()) : "0";

            case "health":
                return player != null ? String.valueOf((int) player.getHealth()) : "0";

            case "level":
                return player != null ? String.valueOf(player.experienceLevel) : "0";

            case "gamemode":
                if (player != null) {
                    switch (player.interactionManager.getGameMode()) {
                        case SURVIVAL: return "生存模式";
                        case CREATIVE: return "创造模式";
                        case ADVENTURE: return "冒险模式";
                        case SPECTATOR: return "观察者模式";
                        default: return "未知模式";
                    }
                }
                return "Unknown";

            case "weather":
                if (player != null) {
                    if (player.getWorld().isRaining()) {
                        return player.getWorld().isThundering() ? "雷雨" : "下雨";
                    }
                    return "晴天";
                }
                return "Unknown";

            case "server":
                return player != null ? player.getServer().getName() : "Unknown";

            default:
                return null; // 不是内置变量
        }
    }

    /**
     * 设置变量
     */
    public void setVariable(String name, String value) {
        variables.put(name, value);
    }

    /**
     * 获取变量
     */
    public String getVariable(String name) {
        return variables.get(name);
    }

    /**
     * 移除变量
     */
    public void removeVariable(String name) {
        variables.remove(name);
    }

    /**
     * 清空所有变量
     */
    public void clearVariables() {
        variables.clear();
    }

    /**
     * 复制模板
     */
    public PromptTemplate copy() {
        PromptTemplate copy = new PromptTemplate();
        copy.id = this.id;
        copy.name = this.name;
        copy.description = this.description;
        copy.systemPrompt = this.systemPrompt;
        copy.userPromptPrefix = this.userPromptPrefix;
        copy.userPromptSuffix = this.userPromptSuffix;
        copy.variables = new HashMap<>(this.variables);
        copy.enabled = this.enabled;
        return copy;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getUserPromptPrefix() {
        return userPromptPrefix;
    }

    public void setUserPromptPrefix(String userPromptPrefix) {
        this.userPromptPrefix = userPromptPrefix;
    }

    public String getUserPromptSuffix() {
        return userPromptSuffix;
    }

    public void setUserPromptSuffix(String userPromptSuffix) {
        this.userPromptSuffix = userPromptSuffix;
    }

    public Map<String, String> getVariables() {
        return new HashMap<>(variables);
    }

    public void setVariables(Map<String, String> variables) {
        this.variables = new HashMap<>(variables);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 生成全局上下文信息
     */
    private String generateGlobalContext(ServerPlayerEntity player, String globalContextTemplate) {
        if (globalContextTemplate == null || globalContextTemplate.trim().isEmpty()) {
            return "";
        }

        // 创建上下文变量映射
        Map<String, String> contextVariables = new HashMap<>();

        // 玩家信息
        contextVariables.put("player_name", player.getName().getString());

        // 当前时间
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        contextVariables.put("current_time", now.format(formatter));

        // 服务器和在线玩家信息
        MinecraftServer server = player.getServer();
        if (server != null) {
            // 在线玩家信息
            var playerManager = server.getPlayerManager();
            int playerCount = playerManager.getCurrentPlayerCount();
            contextVariables.put("player_count", String.valueOf(playerCount));

            // 在线玩家列表（限制显示数量避免过长）
            String onlinePlayers = playerManager.getPlayerList().stream()
                    .limit(10) // 最多显示10个玩家
                    .map(p -> p.getName().getString())
                    .collect(Collectors.joining(", "));
            if (playerCount > 10) {
                onlinePlayers += "...";
            }
            contextVariables.put("online_players", onlinePlayers);
        } else {
            contextVariables.put("player_count", "1");
            contextVariables.put("online_players", player.getName().getString());
        }

        // 游戏版本信息
        contextVariables.put("game_version", "Minecraft 1.21.7");

        // 模组版本信息
        try {
            String modVersion = FabricLoader.getInstance()
                    .getModContainer("lllmchat")
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("Unknown");
            contextVariables.put("mod_version", modVersion);
        } catch (Exception e) {
            contextVariables.put("mod_version", "Unknown");
        }

        // 渲染模板
        return renderTemplateWithVariables(globalContextTemplate, contextVariables);
    }

    /**
     * 使用指定变量渲染模板
     */
    private String renderTemplateWithVariables(String template, Map<String, String> variables) {
        if (template == null) {
            return "";
        }

        String result = template;
        Matcher matcher = VARIABLE_PATTERN.matcher(template);

        while (matcher.find()) {
            String variableName = matcher.group(1);
            String variableValue = variables.getOrDefault(variableName, "");
            result = result.replace("{{" + variableName + "}}", variableValue);
        }

        return result;
    }


}
