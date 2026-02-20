package com.riceawa.llm.function.impl;

import com.google.gson.JsonObject;
import com.riceawa.llm.function.LLMFunction;
import com.riceawa.llm.function.PermissionHelper;
import com.riceawa.llm.util.EntityHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * 传送玩家的函数
 * 权限：OP可传送任何玩家，普通玩家只能传送自己
 */
public class TeleportPlayerFunction implements LLMFunction {
    
    @Override
    public String getName() {
        return "teleport_player";
    }
    
    @Override
    public String getDescription() {
        return "传送玩家到指定位置或其他玩家身边";
    }
    
    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        
        JsonObject properties = new JsonObject();
        
        // 目标玩家名称（可选，默认为自己）
        JsonObject playerParam = new JsonObject();
        playerParam.addProperty("type", "string");
        playerParam.addProperty("description", "要传送的玩家名称（不指定则传送自己）");
        properties.add("player_name", playerParam);
        
        // 目标玩家（传送到其他玩家身边）
        JsonObject targetPlayerParam = new JsonObject();
        targetPlayerParam.addProperty("type", "string");
        targetPlayerParam.addProperty("description", "传送到此玩家身边（与坐标参数二选一）");
        properties.add("target_player", targetPlayerParam);
        
        // X坐标
        JsonObject xParam = new JsonObject();
        xParam.addProperty("type", "number");
        xParam.addProperty("description", "目标X坐标");
        properties.add("x", xParam);
        
        // Y坐标
        JsonObject yParam = new JsonObject();
        yParam.addProperty("type", "number");
        yParam.addProperty("description", "目标Y坐标");
        properties.add("y", yParam);
        
        // Z坐标
        JsonObject zParam = new JsonObject();
        zParam.addProperty("type", "number");
        zParam.addProperty("description", "目标Z坐标");
        properties.add("z", zParam);
        
        // 维度
        JsonObject dimensionParam = new JsonObject();
        dimensionParam.addProperty("type", "string");
        dimensionParam.addProperty("description", "目标维度（overworld/nether/end）");
        dimensionParam.addProperty("default", "overworld");
        properties.add("dimension", dimensionParam);
        
        schema.add("properties", properties);
        
        return schema;
    }
    
    @Override
    public FunctionResult execute(PlayerEntity player, MinecraftServer server, JsonObject arguments) {
        try {
            // 确定要传送的玩家
            ServerPlayerEntity targetPlayer = (ServerPlayerEntity) player;
            
            if (arguments.has("player_name")) {
                String playerName = arguments.get("player_name").getAsString();
                ServerPlayerEntity foundPlayer = server.getPlayerManager().getPlayer(playerName);
                
                if (foundPlayer == null) {
                    return FunctionResult.error("找不到玩家: " + playerName);
                }
                
                // 检查权限：只有OP才能传送其他玩家
                if (!foundPlayer.equals(player) && !PermissionHelper.canTeleportOthers(player)) {
                    return FunctionResult.error(PermissionHelper.getPermissionErrorMessage("传送其他玩家"));
                }
                
                targetPlayer = foundPlayer;
            }
            
            // 确定传送目标
            if (arguments.has("target_player")) {
                // 传送到其他玩家身边
                String targetPlayerName = arguments.get("target_player").getAsString();
                ServerPlayerEntity destinationPlayer = server.getPlayerManager().getPlayer(targetPlayerName);
                
                if (destinationPlayer == null) {
                    return FunctionResult.error("找不到目标玩家: " + targetPlayerName);
                }
                
                if (destinationPlayer.equals(targetPlayer)) {
                    return FunctionResult.error("不能传送到自己身边");
                }
                
                // 传送到目标玩家位置
                Vec3d targetPos = EntityHelper.getPos(destinationPlayer);
                ServerWorld targetWorld = (ServerWorld) EntityHelper.getWorld(destinationPlayer);
                if (targetWorld == null) {
                    return FunctionResult.error("无法获取目标玩家所在世界信息");
                }

                targetPlayer.teleport(targetWorld, targetPos.x, targetPos.y, targetPos.z,
                                    java.util.Set.of(), targetPlayer.getYaw(), targetPlayer.getPitch(), false);
                
                // 发送消息
                String message = String.format("已将 %s 传送到 %s 身边", 
                    targetPlayer.getName().getString(), destinationPlayer.getName().getString());
                
                targetPlayer.sendMessage(Text.literal("你被传送到了 " + 
                    destinationPlayer.getName().getString() + " 身边"), false);
                
                return FunctionResult.success(message);
                
            } else if (arguments.has("x") && arguments.has("y") && arguments.has("z")) {
                // 传送到指定坐标
                double x = arguments.get("x").getAsDouble();
                double y = arguments.get("y").getAsDouble();
                double z = arguments.get("z").getAsDouble();
                
                // 验证Y坐标
                if (y < -64 || y > 320) {
                    return FunctionResult.error("Y坐标超出有效范围（-64到320）");
                }
                
                // 确定目标世界
                ServerWorld targetWorld = (ServerWorld) EntityHelper.getWorld(targetPlayer);
                if (targetWorld == null) {
                    return FunctionResult.error("无法获取玩家所在世界信息");
                }
                
                if (arguments.has("dimension")) {
                    String dimension = arguments.get("dimension").getAsString().toLowerCase();
                    switch (dimension) {
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
                            return FunctionResult.error("未知的维度: " + dimension);
                    }
                }
                
                if (targetWorld == null) {
                    return FunctionResult.error("目标世界不存在");
                }
                
                // 执行传送
                targetPlayer.teleport(targetWorld, x, y, z, java.util.Set.of(),
                                     targetPlayer.getYaw(), targetPlayer.getPitch(), false);
                
                // 发送消息
                String dimensionName = getDimensionName(targetWorld);
                String message = String.format("已将 %s 传送到 %s (%.1f, %.1f, %.1f)", 
                    targetPlayer.getName().getString(), dimensionName, x, y, z);
                
                targetPlayer.sendMessage(Text.literal(String.format(
                    "你被传送到了 %s (%.1f, %.1f, %.1f)", dimensionName, x, y, z)), false);
                
                return FunctionResult.success(message);
                
            } else {
                return FunctionResult.error("必须指定传送目标：要么是target_player，要么是x,y,z坐标");
            }
            
        } catch (Exception e) {
            return FunctionResult.error("传送玩家时发生错误: " + e.getMessage());
        }
    }
    
    private String getDimensionName(ServerWorld world) {
        String dimensionId = world.getRegistryKey().getValue().toString();
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
        return true; // 所有玩家都可以传送自己，OP可以传送其他玩家
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
