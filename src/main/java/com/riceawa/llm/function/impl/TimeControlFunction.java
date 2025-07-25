package com.riceawa.llm.function.impl;

import com.google.gson.JsonObject;
import com.riceawa.llm.function.LLMFunction;
import com.riceawa.llm.function.PermissionHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * 时间控制函数
 * 安全性：仅OP可用
 */
public class TimeControlFunction implements LLMFunction {
    
    @Override
    public String getName() {
        return "control_time";
    }
    
    @Override
    public String getDescription() {
        return "控制游戏时间（仅OP可用）";
    }
    
    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        
        JsonObject properties = new JsonObject();
        
        // 时间类型
        JsonObject timeTypeParam = new JsonObject();
        timeTypeParam.addProperty("type", "string");
        timeTypeParam.addProperty("description", "时间类型：day（白天）、night（夜晚）、noon（正午）、midnight（午夜）、sunrise（日出）、sunset（日落）、specific（指定时间）");
        properties.add("time_type", timeTypeParam);
        
        // 具体时间值（当time_type为specific时使用）
        JsonObject timeValueParam = new JsonObject();
        timeValueParam.addProperty("type", "integer");
        timeValueParam.addProperty("description", "具体时间值（0-23999，仅当time_type为specific时使用）");
        timeValueParam.addProperty("minimum", 0);
        timeValueParam.addProperty("maximum", 23999);
        properties.add("time_value", timeValueParam);
        
        // 目标世界
        JsonObject worldParam = new JsonObject();
        worldParam.addProperty("type", "string");
        worldParam.addProperty("description", "目标世界：overworld（主世界）、nether（下界）、end（末地）");
        worldParam.addProperty("default", "overworld");
        properties.add("world", worldParam);
        
        schema.add("properties", properties);
        
        // 必需参数
        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("time_type");
        schema.add("required", required);
        
        return schema;
    }
    
    @Override
    public FunctionResult execute(PlayerEntity player, MinecraftServer server, JsonObject arguments) {
        try {
            // 获取参数
            if (!arguments.has("time_type")) {
                return FunctionResult.error("缺少必需参数: time_type");
            }
            
            String timeType = arguments.get("time_type").getAsString().toLowerCase();
            String worldName = arguments.has("world") ? arguments.get("world").getAsString().toLowerCase() : "overworld";
            
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
            
            // 确定目标时间
            long targetTime;
            String timeDisplayName;
            
            switch (timeType) {
                case "day":
                    targetTime = 1000; // 白天开始
                    timeDisplayName = "白天";
                    break;
                case "noon":
                    targetTime = 6000; // 正午
                    timeDisplayName = "正午";
                    break;
                case "sunset":
                    targetTime = 12000; // 日落
                    timeDisplayName = "日落";
                    break;
                case "night":
                    targetTime = 13000; // 夜晚开始
                    timeDisplayName = "夜晚";
                    break;
                case "midnight":
                    targetTime = 18000; // 午夜
                    timeDisplayName = "午夜";
                    break;
                case "sunrise":
                    targetTime = 23000; // 日出
                    timeDisplayName = "日出";
                    break;
                case "specific":
                    if (!arguments.has("time_value")) {
                        return FunctionResult.error("使用specific时间类型时必须提供time_value参数");
                    }
                    targetTime = arguments.get("time_value").getAsLong();
                    if (targetTime < 0 || targetTime > 23999) {
                        return FunctionResult.error("时间值必须在0到23999之间");
                    }
                    timeDisplayName = "指定时间 " + targetTime;
                    break;
                default:
                    return FunctionResult.error("无效的时间类型，支持：day、night、noon、midnight、sunrise、sunset、specific");
            }
            
            // 设置时间
            targetWorld.setTimeOfDay(targetTime);
            
            // 构建结果消息
            String worldDisplayName = getWorldDisplayName(worldName);
            String message = String.format("已将%s的时间设置为%s", worldDisplayName, timeDisplayName);
            
            // 如果是具体时间，添加时间显示
            if (timeType.equals("specific")) {
                int hours = (int) ((targetTime / 1000 + 6) % 24);
                int minutes = (int) ((targetTime % 1000) * 60 / 1000);
                message += String.format("（%02d:%02d）", hours, minutes);
            }
            
            return FunctionResult.success(message);
            
        } catch (Exception e) {
            return FunctionResult.error("控制时间时发生错误: " + e.getMessage());
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
