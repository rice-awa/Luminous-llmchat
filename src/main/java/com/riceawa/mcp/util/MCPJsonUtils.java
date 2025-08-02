package com.riceawa.mcp.util;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.riceawa.mcp.model.*;
import com.riceawa.mcp.exception.MCPException;
import com.riceawa.mcp.exception.MCPErrorType;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * MCP数据模型JSON序列化和反序列化工具类
 * 提供统一的JSON处理接口，支持所有MCP数据模型
 */
public class MCPJsonUtils {
    
    private static final Gson gson;
    
    static {
        GsonBuilder builder = new GsonBuilder();
        
        // 设置日期格式
        builder.setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        
        // 美化输出（可选）
        builder.setPrettyPrinting();
        
        // 注册自定义序列化器
        builder.registerTypeAdapter(MCPErrorType.class, new MCPErrorTypeSerializer());
        builder.registerTypeAdapter(MCPErrorType.class, new MCPErrorTypeDeserializer());
        
        gson = builder.create();
    }
    
    /**
     * 获取Gson实例
     */
    public static Gson getGson() {
        return gson;
    }
    
    // ==================== MCPTool 相关方法 ====================
    
    /**
     * 将MCPTool对象序列化为JSON字符串
     */
    public static String toJson(MCPTool tool) throws MCPException {
        try {
            return gson.toJson(tool);
        } catch (Exception e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPTool序列化失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从JSON字符串反序列化为MCPTool对象
     */
    public static MCPTool fromJsonToTool(String json) throws MCPException {
        try {
            MCPTool tool = gson.fromJson(json, MCPTool.class);
            if (tool == null) {
                throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, "反序列化结果为null");
            }
            return tool;
        } catch (JsonSyntaxException e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPTool反序列化失败: JSON格式错误", e);
        } catch (Exception e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPTool反序列化失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从JSON字符串反序列化为MCPTool列表
     */
    public static List<MCPTool> fromJsonToToolList(String json) throws MCPException {
        try {
            Type listType = new TypeToken<List<MCPTool>>(){}.getType();
            List<MCPTool> tools = gson.fromJson(json, listType);
            return tools != null ? tools : List.of();
        } catch (JsonSyntaxException e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPTool列表反序列化失败: JSON格式错误", e);
        } catch (Exception e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPTool列表反序列化失败: " + e.getMessage(), e);
        }
    }
    
    // ==================== MCPToolResult 相关方法 ====================
    
    /**
     * 将MCPToolResult对象序列化为JSON字符串
     */
    public static String toJson(MCPToolResult result) throws MCPException {
        try {
            return gson.toJson(result);
        } catch (Exception e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPToolResult序列化失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从JSON字符串反序列化为MCPToolResult对象
     */
    public static MCPToolResult fromJsonToToolResult(String json) throws MCPException {
        try {
            MCPToolResult result = gson.fromJson(json, MCPToolResult.class);
            if (result == null) {
                throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, "反序列化结果为null");
            }
            return result;
        } catch (JsonSyntaxException e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPToolResult反序列化失败: JSON格式错误", e);
        } catch (Exception e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPToolResult反序列化失败: " + e.getMessage(), e);
        }
    }
    
    // ==================== MCPContent 相关方法 ====================
    
    /**
     * 将MCPContent对象序列化为JSON字符串
     */
    public static String toJson(MCPContent content) throws MCPException {
        try {
            return gson.toJson(content);
        } catch (Exception e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPContent序列化失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从JSON字符串反序列化为MCPContent对象
     */
    public static MCPContent fromJsonToContent(String json) throws MCPException {
        try {
            MCPContent content = gson.fromJson(json, MCPContent.class);
            if (content == null) {
                throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, "反序列化结果为null");
            }
            return content;
        } catch (JsonSyntaxException e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPContent反序列化失败: JSON格式错误", e);
        } catch (Exception e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPContent反序列化失败: " + e.getMessage(), e);
        }
    }
    
    // ==================== MCPResource 相关方法 ====================
    
    /**
     * 将MCPResource对象序列化为JSON字符串
     */
    public static String toJson(MCPResource resource) throws MCPException {
        try {
            return gson.toJson(resource);
        } catch (Exception e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPResource序列化失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从JSON字符串反序列化为MCPResource对象
     */
    public static MCPResource fromJsonToResource(String json) throws MCPException {
        try {
            MCPResource resource = gson.fromJson(json, MCPResource.class);
            if (resource == null) {
                throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, "反序列化结果为null");
            }
            return resource;
        } catch (JsonSyntaxException e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPResource反序列化失败: JSON格式错误", e);
        } catch (Exception e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPResource反序列化失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从JSON字符串反序列化为MCPResource列表
     */
    public static List<MCPResource> fromJsonToResourceList(String json) throws MCPException {
        try {
            Type listType = new TypeToken<List<MCPResource>>(){}.getType();
            List<MCPResource> resources = gson.fromJson(json, listType);
            return resources != null ? resources : List.of();
        } catch (JsonSyntaxException e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPResource列表反序列化失败: JSON格式错误", e);
        } catch (Exception e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPResource列表反序列化失败: " + e.getMessage(), e);
        }
    }
    
    // ==================== MCPResourceContent 相关方法 ====================
    
    /**
     * 将MCPResourceContent对象序列化为JSON字符串
     */
    public static String toJson(MCPResourceContent content) throws MCPException {
        try {
            return gson.toJson(content);
        } catch (Exception e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPResourceContent序列化失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从JSON字符串反序列化为MCPResourceContent对象
     */
    public static MCPResourceContent fromJsonToResourceContent(String json) throws MCPException {
        try {
            MCPResourceContent content = gson.fromJson(json, MCPResourceContent.class);
            if (content == null) {
                throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, "反序列化结果为null");
            }
            return content;
        } catch (JsonSyntaxException e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPResourceContent反序列化失败: JSON格式错误", e);
        } catch (Exception e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPResourceContent反序列化失败: " + e.getMessage(), e);
        }
    }
    
    // ==================== MCPPrompt 相关方法 ====================
    
    /**
     * 将MCPPrompt对象序列化为JSON字符串
     */
    public static String toJson(MCPPrompt prompt) throws MCPException {
        try {
            return gson.toJson(prompt);
        } catch (Exception e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPPrompt序列化失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从JSON字符串反序列化为MCPPrompt对象
     */
    public static MCPPrompt fromJsonToPrompt(String json) throws MCPException {
        try {
            MCPPrompt prompt = gson.fromJson(json, MCPPrompt.class);
            if (prompt == null) {
                throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, "反序列化结果为null");
            }
            return prompt;
        } catch (JsonSyntaxException e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPPrompt反序列化失败: JSON格式错误", e);
        } catch (Exception e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPPrompt反序列化失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从JSON字符串反序列化为MCPPrompt列表
     */
    public static List<MCPPrompt> fromJsonToPromptList(String json) throws MCPException {
        try {
            Type listType = new TypeToken<List<MCPPrompt>>(){}.getType();
            List<MCPPrompt> prompts = gson.fromJson(json, listType);
            return prompts != null ? prompts : List.of();
        } catch (JsonSyntaxException e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPPrompt列表反序列化失败: JSON格式错误", e);
        } catch (Exception e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPPrompt列表反序列化失败: " + e.getMessage(), e);
        }
    }
    
    // ==================== MCPPromptResult 相关方法 ====================
    
    /**
     * 将MCPPromptResult对象序列化为JSON字符串
     */
    public static String toJson(MCPPromptResult result) throws MCPException {
        try {
            return gson.toJson(result);
        } catch (Exception e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPPromptResult序列化失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从JSON字符串反序列化为MCPPromptResult对象
     */
    public static MCPPromptResult fromJsonToPromptResult(String json) throws MCPException {
        try {
            MCPPromptResult result = gson.fromJson(json, MCPPromptResult.class);
            if (result == null) {
                throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, "反序列化结果为null");
            }
            return result;
        } catch (JsonSyntaxException e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPPromptResult反序列化失败: JSON格式错误", e);
        } catch (Exception e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPPromptResult反序列化失败: " + e.getMessage(), e);
        }
    }
    
    // ==================== MCPMessage 相关方法 ====================
    
    /**
     * 将MCPMessage对象序列化为JSON字符串
     */
    public static String toJson(MCPMessage message) throws MCPException {
        try {
            return gson.toJson(message);
        } catch (Exception e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPMessage序列化失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从JSON字符串反序列化为MCPMessage对象
     */
    public static MCPMessage fromJsonToMessage(String json) throws MCPException {
        try {
            MCPMessage message = gson.fromJson(json, MCPMessage.class);
            if (message == null) {
                throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, "反序列化结果为null");
            }
            return message;
        } catch (JsonSyntaxException e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPMessage反序列化失败: JSON格式错误", e);
        } catch (Exception e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "MCPMessage反序列化失败: " + e.getMessage(), e);
        }
    }
    
    // ==================== 通用方法 ====================
    
    /**
     * 将任意对象序列化为JSON字符串
     */
    public static String toJson(Object obj) throws MCPException {
        try {
            return gson.toJson(obj);
        } catch (Exception e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "对象序列化失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从JSON字符串反序列化为指定类型的对象
     */
    public static <T> T fromJson(String json, Class<T> classOfT) throws MCPException {
        try {
            T obj = gson.fromJson(json, classOfT);
            if (obj == null) {
                throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, "反序列化结果为null");
            }
            return obj;
        } catch (JsonSyntaxException e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "反序列化失败: JSON格式错误", e);
        } catch (Exception e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "反序列化失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从JSON字符串反序列化为指定类型的对象
     */
    public static <T> T fromJson(String json, Type typeOfT) throws MCPException {
        try {
            T obj = gson.fromJson(json, typeOfT);
            if (obj == null) {
                throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, "反序列化结果为null");
            }
            return obj;
        } catch (JsonSyntaxException e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "反序列化失败: JSON格式错误", e);
        } catch (Exception e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "反序列化失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检查JSON字符串是否有效
     */
    public static boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        try {
            JsonParser.parseString(json);
            return true;
        } catch (JsonSyntaxException e) {
            return false;
        }
    }
    
    /**
     * 美化JSON字符串输出
     */
    public static String prettyPrint(String json) throws MCPException {
        try {
            JsonElement element = JsonParser.parseString(json);
            return gson.toJson(element);
        } catch (JsonSyntaxException e) {
            throw new MCPException(MCPErrorType.SERIALIZATION_ERROR, 
                                 "JSON格式错误，无法美化输出", e);
        }
    }
    
    // ==================== 自定义序列化器 ====================
    
    /**
     * MCPErrorType序列化器
     */
    private static class MCPErrorTypeSerializer implements JsonSerializer<MCPErrorType> {
        @Override
        public JsonElement serialize(MCPErrorType src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("name", src.name());
            jsonObject.addProperty("displayName", src.getDisplayName());
            jsonObject.addProperty("description", src.getDescription());
            return jsonObject;
        }
    }
    
    /**
     * MCPErrorType反序列化器
     */
    private static class MCPErrorTypeDeserializer implements JsonDeserializer<MCPErrorType> {
        @Override
        public MCPErrorType deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                // 兼容简单字符串格式
                String name = json.getAsString();
                try {
                    return MCPErrorType.valueOf(name);
                } catch (IllegalArgumentException e) {
                    return MCPErrorType.UNKNOWN_ERROR;
                }
            } else if (json.isJsonObject()) {
                // 完整对象格式
                JsonObject jsonObject = json.getAsJsonObject();
                String name = jsonObject.get("name").getAsString();
                try {
                    return MCPErrorType.valueOf(name);
                } catch (IllegalArgumentException e) {
                    return MCPErrorType.UNKNOWN_ERROR;
                }
            }
            return MCPErrorType.UNKNOWN_ERROR;
        }
    }
}