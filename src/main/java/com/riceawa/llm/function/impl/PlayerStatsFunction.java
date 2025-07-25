package com.riceawa.llm.function.impl;

import com.google.gson.JsonObject;
import com.riceawa.llm.function.LLMFunction;
import com.riceawa.llm.function.PermissionHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;

/**
 * 获取玩家详细统计信息的函数
 */
public class PlayerStatsFunction implements LLMFunction {
    
    @Override
    public String getName() {
        return "get_player_stats";
    }
    
    @Override
    public String getDescription() {
        return "获取玩家的详细统计信息，包括生命值、饥饿值、经验、位置等";
    }
    
    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        
        // 可选参数：目标玩家名称
        JsonObject playerName = new JsonObject();
        playerName.addProperty("type", "string");
        playerName.addProperty("description", "要查询的玩家名称，不填则查询自己");
        properties.add("player_name", playerName);
        
        schema.add("properties", properties);
        return schema;
    }
    
    @Override
    public FunctionResult execute(PlayerEntity player, MinecraftServer server, JsonObject arguments) {
        try {
            ServerPlayerEntity targetPlayer = (ServerPlayerEntity) player;
            
            // 如果指定了玩家名称，尝试查找该玩家
            if (arguments.has("player_name")) {
                String playerName = arguments.get("player_name").getAsString();
                ServerPlayerEntity foundPlayer = server.getPlayerManager().getPlayer(playerName);
                if (foundPlayer == null) {
                    return FunctionResult.error("找不到玩家: " + playerName);
                }
                
                // 检查权限：只有OP或查询自己才能查看其他玩家信息
                if (!PermissionHelper.canViewOtherPlayerInfo(player, foundPlayer)) {
                    return FunctionResult.error(PermissionHelper.getPermissionErrorMessage("查看其他玩家的信息"));
                }
                
                targetPlayer = foundPlayer;
            }
            
            StringBuilder stats = new StringBuilder();
            
            // 基本信息
            stats.append("=== ").append(targetPlayer.getName().getString()).append(" 的统计信息 ===\n");
            
            // 生命和状态
            stats.append("生命值: ").append(String.format("%.1f/%.1f", 
                targetPlayer.getHealth(), targetPlayer.getMaxHealth())).append("\n");
            stats.append("饥饿值: ").append(targetPlayer.getHungerManager().getFoodLevel()).append("/20\n");
            stats.append("饱和度: ").append(String.format("%.1f", 
                targetPlayer.getHungerManager().getSaturationLevel())).append("\n");
            
            // 经验信息
            stats.append("经验等级: ").append(targetPlayer.experienceLevel).append("\n");
            stats.append("经验进度: ").append(String.format("%.1f%%", 
                targetPlayer.experienceProgress * 100)).append("\n");
            stats.append("总经验: ").append(targetPlayer.totalExperience).append("\n");
            
            // 位置信息
            BlockPos pos = targetPlayer.getBlockPos();
            stats.append("位置: ").append(pos.getX()).append(", ")
                .append(pos.getY()).append(", ").append(pos.getZ()).append("\n");
            stats.append("维度: ").append(getDimensionName(targetPlayer.getWorld().getRegistryKey().getValue().toString())).append("\n");
            
            // 游戏模式
            stats.append("游戏模式: ").append(targetPlayer.interactionManager.getGameMode().getTranslatableName().getString()).append("\n");
            
            // 移动状态
            stats.append("是否在地面: ").append(targetPlayer.isOnGround() ? "是" : "否").append("\n");
            stats.append("是否在水中: ").append(targetPlayer.isTouchingWater() ? "是" : "否").append("\n");
            stats.append("是否在潜行: ").append(targetPlayer.isSneaking() ? "是" : "否").append("\n");
            stats.append("是否在疾跑: ").append(targetPlayer.isSprinting() ? "是" : "否").append("\n");
            
            // 一些基本统计数据
            if (targetPlayer.getStatHandler() != null) {
                stats.append("\n=== 游戏统计 ===\n");
                
                // 游戏时间
                int playTime = targetPlayer.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.PLAY_TIME));
                int hours = playTime / 72000; // 20 ticks per second * 3600 seconds per hour
                int minutes = (playTime % 72000) / 1200; // 20 ticks per second * 60 seconds per minute
                stats.append("游戏时间: ").append(hours).append("小时 ").append(minutes).append("分钟\n");
                
                // 移动距离
                int walkDistance = targetPlayer.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.WALK_ONE_CM));
                stats.append("步行距离: ").append(walkDistance / 100).append("米\n");
                
                // 跳跃次数
                int jumps = targetPlayer.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.JUMP));
                stats.append("跳跃次数: ").append(jumps).append("\n");
                
                // 死亡次数
                int deaths = targetPlayer.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DEATHS));
                stats.append("死亡次数: ").append(deaths).append("\n");
            }
            
            return FunctionResult.success(stats.toString());
            
        } catch (Exception e) {
            return FunctionResult.error("获取玩家统计信息失败: " + e.getMessage());
        }
    }
    
    private String getDimensionName(String dimensionId) {
        switch (dimensionId) {
            case "minecraft:overworld":
                return "主世界";
            case "minecraft:the_nether":
                return "下界";
            case "minecraft:the_end":
                return "末地";
            default:
                return dimensionId;
        }
    }
    
    @Override
    public boolean hasPermission(PlayerEntity player) {
        return true; // 所有玩家都可以查看自己的统计信息
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
    
    @Override
    public String getCategory() {
        return "player";
    }
}
