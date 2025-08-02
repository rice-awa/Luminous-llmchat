package com.riceawa.mcp.config;

import com.google.gson.*;
import java.util.*;

/**
 * MCP配置解析器
 * 支持新的字典格式和旧的数组格式
 */
public class MCPConfigParser {
    
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();
    
    /**
     * 解析MCP服务器配置字典
     * @param json JSON对象
     * @return 解析后的服务器配置Map
     */
    public static Map<String, MCPServerConfig> parseServerDictionary(JsonObject json) {
        Map<String, MCPServerConfig> servers = new HashMap<>();
        
        if (json == null || !json.has("mcpServers")) {
            return servers;
        }
        
        JsonElement mcpServersElement = json.get("mcpServers");
        if (!mcpServersElement.isJsonObject()) {
            return servers;
        }
        
        JsonObject mcpServers = mcpServersElement.getAsJsonObject();
        
        for (Map.Entry<String, JsonElement> entry : mcpServers.entrySet()) {
            String serverName = entry.getKey();
            JsonElement serverElement = entry.getValue();
            
            if (!serverElement.isJsonObject()) {
                continue;
            }
            
            JsonObject serverObj = serverElement.getAsJsonObject();
            MCPServerConfig config = parseServerConfig(serverName, serverObj);
            
            if (config != null && config.isValid()) {
                servers.put(serverName, config);
            }
        }
        
        return servers;
    }
    
    /**
     * 解析单个服务器配置
     * @param name 服务器名称
     * @param serverObj 服务器配置JSON对象
     * @return 解析后的服务器配置
     */
    public static MCPServerConfig parseServerConfig(String name, JsonObject serverObj) {
        try {
            MCPServerConfig config = new MCPServerConfig();
            config.setName(name);
            
            // 解析type，默认为stdio
            String type = getStringWithDefault(serverObj, "type", "stdio");
            config.setType(type);
            
            // 解析enabled，默认为true
            boolean enabled = getBooleanWithDefault(serverObj, "enabled", true);
            config.setEnabled(enabled);
            
            // 解析disabled（兼容性）
            if (serverObj.has("disabled")) {
                boolean disabled = getBooleanWithDefault(serverObj, "disabled", false);
                config.setEnabled(!disabled);
            }
            
            // 根据类型解析不同的配置
            if (config.isStdioType()) {
                parseStdioConfig(config, serverObj);
            } else if (config.isSseType()) {
                parseSseConfig(config, serverObj);
            }
            
            // 解析通用配置
            parseCommonConfig(config, serverObj);
            
            return config;
            
        } catch (Exception e) {
            System.err.println("解析服务器配置失败: " + name + ", 错误: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 解析STDIO配置
     */
    private static void parseStdioConfig(MCPServerConfig config, JsonObject serverObj) {
        // 解析command
        String command = getStringWithDefault(serverObj, "command", null);
        config.setCommand(command);
        
        // 解析args，默认为空数组
        List<String> args = parseStringList(serverObj, "args", new ArrayList<>());
        config.setArgs(args);
        
        // 解析env，默认为空对象
        Map<String, String> env = parseStringMap(serverObj, "env", new HashMap<>());
        config.setEnv(env);
    }
    
    /**
     * 解析SSE配置
     */
    private static void parseSseConfig(MCPServerConfig config, JsonObject serverObj) {
        // 解析url
        String url = getStringWithDefault(serverObj, "url", null);
        config.setUrl(url);
    }
    
    /**
     * 解析通用配置
     */
    private static void parseCommonConfig(MCPServerConfig config, JsonObject serverObj) {
        // 解析autoApprove，默认为空数组
        List<String> autoApprove = parseStringList(serverObj, "autoApprove", new ArrayList<>());
        config.setAutoApprove(autoApprove);
        
        // 解析description，默认为空字符串
        String description = getStringWithDefault(serverObj, "description", "");
        config.setDescription(description);
        
        // 解析toolPermissionPolicy，默认为INHERIT_CLIENT
        String policy = getStringWithDefault(serverObj, "toolPermissionPolicy", "INHERIT_CLIENT");
        config.setToolPermissionPolicy(policy);
        
        // 解析allowedTools
        if (serverObj.has("allowedTools")) {
            Set<String> allowedTools = new HashSet<>(parseStringList(serverObj, "allowedTools", new ArrayList<>()));
            config.setAllowedTools(allowedTools);
        }
        
        // 解析allowedResources
        if (serverObj.has("allowedResources")) {
            Set<String> allowedResources = new HashSet<>(parseStringList(serverObj, "allowedResources", new ArrayList<>()));
            config.setAllowedResources(allowedResources);
        }
    }
    
    /**
     * 获取字符串值，带默认值
     */
    private static String getStringWithDefault(JsonObject obj, String key, String defaultValue) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        JsonElement element = obj.get(key);
        return element.isJsonPrimitive() ? element.getAsString() : defaultValue;
    }
    
    /**
     * 获取布尔值，带默认值
     */
    private static boolean getBooleanWithDefault(JsonObject obj, String key, boolean defaultValue) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        JsonElement element = obj.get(key);
        try {
            return element.isJsonPrimitive() ? element.getAsBoolean() : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * 解析字符串列表
     */
    private static List<String> parseStringList(JsonObject obj, String key, List<String> defaultValue) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        
        JsonElement element = obj.get(key);
        if (!element.isJsonArray()) {
            return defaultValue;
        }
        
        List<String> result = new ArrayList<>();
        JsonArray array = element.getAsJsonArray();
        for (JsonElement item : array) {
            if (item.isJsonPrimitive()) {
                result.add(item.getAsString());
            }
        }
        return result;
    }
    
    /**
     * 解析字符串映射
     */
    private static Map<String, String> parseStringMap(JsonObject obj, String key, Map<String, String> defaultValue) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        
        JsonElement element = obj.get(key);
        if (!element.isJsonObject()) {
            return defaultValue;
        }
        
        Map<String, String> result = new HashMap<>();
        JsonObject mapObj = element.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : mapObj.entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive()) {
                result.put(entry.getKey(), value.getAsString());
            }
        }
        return result;
    }
    
    /**
     * 将服务器配置字典序列化为JSON
     */
    public static JsonObject serializeServerDictionary(Map<String, MCPServerConfig> servers) {
        JsonObject mcpServers = new JsonObject();
        
        for (Map.Entry<String, MCPServerConfig> entry : servers.entrySet()) {
            String name = entry.getKey();
            MCPServerConfig config = entry.getValue();
            JsonObject serverObj = serializeServerConfig(config);
            mcpServers.add(name, serverObj);
        }
        
        JsonObject result = new JsonObject();
        result.add("mcpServers", mcpServers);
        return result;
    }
    
    /**
     * 序列化单个服务器配置
     */
    public static JsonObject serializeServerConfig(MCPServerConfig config) {
        JsonObject obj = new JsonObject();
        
        // 只在非默认值时输出type
        if (!"stdio".equals(config.getType())) {
            obj.addProperty("type", config.getType());
        }
        
        // 基于类型序列化特定配置
        if (config.isStdioType()) {
            if (config.getCommand() != null) {
                obj.addProperty("command", config.getCommand());
            }
            if (!config.getArgs().isEmpty()) {
                JsonArray args = new JsonArray();
                for (String arg : config.getArgs()) {
                    args.add(arg);
                }
                obj.add("args", args);
            }
            if (!config.getEnv().isEmpty()) {
                JsonObject env = new JsonObject();
                for (Map.Entry<String, String> envEntry : config.getEnv().entrySet()) {
                    env.addProperty(envEntry.getKey(), envEntry.getValue());
                }
                obj.add("env", env);
            }
        } else if (config.isSseType()) {
            if (config.getUrl() != null) {
                obj.addProperty("url", config.getUrl());
            }
        }
        
        // 序列化通用配置
        if (!config.isEnabled()) {
            obj.addProperty("disabled", true);
        }
        
        if (!config.getAutoApprove().isEmpty()) {
            JsonArray autoApprove = new JsonArray();
            for (String tool : config.getAutoApprove()) {
                autoApprove.add(tool);
            }
            obj.add("autoApprove", autoApprove);
        }
        
        if (!config.getDescription().isEmpty()) {
            obj.addProperty("description", config.getDescription());
        }
        
        return obj;
    }
    
    /**
     * 验证配置JSON格式
     */
    public static ValidationResult validateConfigFormat(JsonObject json) {
        ValidationResult result = new ValidationResult();
        
        if (json == null) {
            result.addError("配置JSON为空");
            return result;
        }
        
        // 检查mcpServers字段
        if (json.has("mcpServers")) {
            JsonElement mcpServersElement = json.get("mcpServers");
            if (!mcpServersElement.isJsonObject()) {
                result.addError("mcpServers必须是一个对象");
            } else {
                validateServers(mcpServersElement.getAsJsonObject(), result);
            }
        }
        
        return result;
    }
    
    /**
     * 验证服务器配置
     */
    private static void validateServers(JsonObject mcpServers, ValidationResult result) {
        for (Map.Entry<String, JsonElement> entry : mcpServers.entrySet()) {
            String serverName = entry.getKey();
            JsonElement serverElement = entry.getValue();
            
            if (!serverElement.isJsonObject()) {
                result.addError("服务器 '" + serverName + "' 配置必须是一个对象");
                continue;
            }
            
            JsonObject serverObj = serverElement.getAsJsonObject();
            validateServerConfig(serverName, serverObj, result);
        }
    }
    
    /**
     * 验证单个服务器配置
     */
    private static void validateServerConfig(String name, JsonObject serverObj, ValidationResult result) {
        String type = getStringWithDefault(serverObj, "type", "stdio");
        
        switch (type.toLowerCase()) {
            case "stdio":
                String command = getStringWithDefault(serverObj, "command", null);
                if (command == null || command.trim().isEmpty()) {
                    result.addError("服务器 '" + name + "' stdio类型必须指定command");
                }
                break;
            case "sse":
                String url = getStringWithDefault(serverObj, "url", null);
                if (url == null || url.trim().isEmpty()) {
                    result.addError("服务器 '" + name + "' sse类型必须指定url");
                } else if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("localhost")) {
                    result.addWarning("服务器 '" + name + "' url格式可能不正确");
                }
                break;
            default:
                result.addWarning("服务器 '" + name + "' 使用了未知的类型: " + type);
        }
    }
    
    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public List<String> getErrors() {
            return Collections.unmodifiableList(errors);
        }
        
        public List<String> getWarnings() {
            return Collections.unmodifiableList(warnings);
        }
        
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
        
        public boolean isValid() {
            return !hasErrors();
        }
    }
}