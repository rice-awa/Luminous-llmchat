package com.riceawa.llm.function.impl;

import com.google.gson.JsonObject;
import com.riceawa.llm.function.LLMFunction;
import com.riceawa.llm.function.PermissionHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * 天气控制函数
 * 安全性：仅OP可用
 */
public class WeatherControlFunction implements LLMFunction {
    
    @Override
    public String getName() {
        return "control_weather";
    }
    
    @Override
    public String getDescription() {
        return "控制天气";
    }
    
    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        
        JsonObject properties = new JsonObject();
        
        // 天气类型
        JsonObject weatherParam = new JsonObject();
        weatherParam.addProperty("type", "string");
        weatherParam.addProperty("description", "天气类型：clear（晴朗）、rain（下雨）、thunder（雷雨）");
        properties.add("weather_type", weatherParam);
        
        // 持续时间
        JsonObject durationParam = new JsonObject();
        durationParam.addProperty("type", "integer");
        durationParam.addProperty("description", "持续时间（秒），0表示永久");
        durationParam.addProperty("default", 0);
        durationParam.addProperty("minimum", 0);
        durationParam.addProperty("maximum", 86400); // 最大24小时
        properties.add("duration", durationParam);
        
        // 目标世界
        JsonObject worldParam = new JsonObject();
        worldParam.addProperty("type", "string");
        worldParam.addProperty("description", "目标世界：overworld（主世界）、nether（下界）、end（末地）");
        worldParam.addProperty("default", "overworld");
        properties.add("world", worldParam);
        
        schema.add("properties", properties);
        
        // 必需参数
        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("weather_type");
        schema.add("required", required);
        
        return schema;
    }
    
    @Override
    public FunctionResult execute(PlayerEntity player, MinecraftServer server, JsonObject arguments) {
        try {
            // 获取参数
            if (!arguments.has("weather_type")) {
                return FunctionResult.error("缺少必需参数: weather_type");
            }
            
            String weatherType = arguments.get("weather_type").getAsString().toLowerCase();
            int duration = arguments.has("duration") ? arguments.get("duration").getAsInt() : 0;
            String worldName = arguments.has("world") ? arguments.get("world").getAsString().toLowerCase() : "overworld";
            
            // 验证天气类型
            if (!weatherType.equals("clear") && !weatherType.equals("rain") && !weatherType.equals("thunder")) {
                return FunctionResult.error("无效的天气类型，支持：clear、rain、thunder");
            }
            
            // 验证持续时间
            if (duration < 0 || duration > 86400) {
                return FunctionResult.error("持续时间必须在0到86400秒之间");
            }
            
            // 获取目标世界
            ServerWorld targetWorld;
            switch (worldName) {
                case "overworld":
                    targetWorld = server.getOverworld();
                    break;
                case "nether":
                    targetWorld = server.getWorld(World.NETHER);
                    break;
                case "end":
                    targetWorld = server.getWorld(World.END);
                    break;
                default:
                    return FunctionResult.error("无效的世界名称，支持：overworld、nether、end");
            }
            
            if (targetWorld == null) {
                return FunctionResult.error("目标世界不存在: " + worldName);
            }
            
            // 转换持续时间（秒转换为游戏刻）
            int durationTicks = duration * 20; // 1秒 = 20游戏刻
            
            // 设置天气
            switch (weatherType) {
                case "clear":
                    targetWorld.setWeather(durationTicks, 0, false, false);
                    break;
                case "rain":
                    targetWorld.setWeather(0, durationTicks, true, false);
                    break;
                case "thunder":
                    targetWorld.setWeather(0, durationTicks, true, true);
                    break;
            }
            
            // 构建结果消息
            String weatherName = getWeatherDisplayName(weatherType);
            String worldDisplayName = getWorldDisplayName(worldName);
            String durationText = duration > 0 ? String.format("持续%d秒", duration) : "永久";
            
            String message = String.format("已将%s的天气设置为%s（%s）", 
                worldDisplayName, weatherName, durationText);
            
            return FunctionResult.success(message);
            
        } catch (Exception e) {
            return FunctionResult.error("控制天气时发生错误: " + e.getMessage());
        }
    }
    
    private String getWeatherDisplayName(String weatherType) {
        switch (weatherType) {
            case "clear":
                return "晴朗";
            case "rain":
                return "下雨";
            case "thunder":
                return "雷雨";
            default:
                return weatherType;
        }
    }
    
    private String getWorldDisplayName(String worldName) {
        switch (worldName) {
            case "overworld":
                return "主世界";
            case "nether":
                return "下界";
            case "end":
                return "末地";
            default:
                return worldName;
        }
    }
    
    @Override
    public boolean hasPermission(PlayerEntity player) {
        return PermissionHelper.canControlEnvironment(player);
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
    
    @Override
    public String getCategory() {
        return "world";
    }
}
