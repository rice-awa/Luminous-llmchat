package com.riceawa.llm.function.impl;

import com.google.gson.JsonObject;
import com.riceawa.llm.function.LLMFunction;
import com.riceawa.llm.function.PermissionHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * 设置方块的函数
 * 安全性：仅OP可用，限制操作范围
 */
public class SetBlockFunction implements LLMFunction {
    
    private static final int MAX_DISTANCE = 100; // 最大操作距离
    
    @Override
    public String getName() {
        return "set_block";
    }
    
    @Override
    public String getDescription() {
        return "在指定位置设置方块";
    }
    
    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        
        JsonObject properties = new JsonObject();
        
        // X坐标
        JsonObject xParam = new JsonObject();
        xParam.addProperty("type", "integer");
        xParam.addProperty("description", "X坐标");
        properties.add("x", xParam);
        
        // Y坐标
        JsonObject yParam = new JsonObject();
        yParam.addProperty("type", "integer");
        yParam.addProperty("description", "Y坐标");
        properties.add("y", yParam);
        
        // Z坐标
        JsonObject zParam = new JsonObject();
        zParam.addProperty("type", "integer");
        zParam.addProperty("description", "Z坐标");
        properties.add("z", zParam);
        
        // 方块类型
        JsonObject blockParam = new JsonObject();
        blockParam.addProperty("type", "string");
        blockParam.addProperty("description", "方块类型（如：stone, dirt, air等）");
        properties.add("block_type", blockParam);
        
        // 是否替换现有方块
        JsonObject replaceParam = new JsonObject();
        replaceParam.addProperty("type", "boolean");
        replaceParam.addProperty("description", "是否替换现有方块");
        replaceParam.addProperty("default", true);
        properties.add("replace", replaceParam);
        
        schema.add("properties", properties);
        
        // 必需参数
        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("x");
        required.add("y");
        required.add("z");
        required.add("block_type");
        schema.add("required", required);
        
        return schema;
    }
    
    @Override
    public FunctionResult execute(PlayerEntity player, MinecraftServer server, JsonObject arguments) {
        try {
            // 获取参数
            if (!arguments.has("x") || !arguments.has("y") || !arguments.has("z") || !arguments.has("block_type")) {
                return FunctionResult.error("缺少必需参数: x, y, z, block_type");
            }
            
            int x = arguments.get("x").getAsInt();
            int y = arguments.get("y").getAsInt();
            int z = arguments.get("z").getAsInt();
            String blockType = arguments.get("block_type").getAsString().trim().toLowerCase();
            boolean replace = !arguments.has("replace") || arguments.get("replace").getAsBoolean();
            
            // 验证坐标
            if (y < -64 || y > 320) {
                return FunctionResult.error("Y坐标超出有效范围（-64到320）");
            }
            
            // 检查距离限制
            BlockPos targetPos = new BlockPos(x, y, z);
            BlockPos playerPos = player.getBlockPos();
            double distance = Math.sqrt(playerPos.getSquaredDistance(targetPos));
            
            if (distance > MAX_DISTANCE) {
                return FunctionResult.error(String.format(
                    "目标位置距离过远（%.1f方块），最大允许距离为%d方块", distance, MAX_DISTANCE));
            }
            
            // 获取方块类型
            Identifier blockId = Identifier.tryParse(blockType);
            if (blockId == null) {
                blockId = Identifier.of("minecraft", blockType); // 如果默认没有命名空间，使用 minecraft
            }
            
            Block block = Registries.BLOCK.get(blockId);
            if (block == null) {
                return FunctionResult.error("未知的方块类型: " + blockType);
            }
            
            BlockState blockState = block.getDefaultState();
            ServerWorld world = (ServerWorld) player.getWorld();
            
            // 检查是否需要替换
            if (!replace && !world.getBlockState(targetPos).isAir()) {
                return FunctionResult.error("目标位置已有方块，且未启用替换模式");
            }
            
            // 设置方块
            boolean success = world.setBlockState(targetPos, blockState);
            
            if (success) {
                // 更新周围方块
                world.updateNeighbors(targetPos, block);
                
                return FunctionResult.success(String.format(
                    "成功在位置 (%d, %d, %d) 设置方块: %s", x, y, z, blockType));
            } else {
                return FunctionResult.error("设置方块失败，可能是区块未加载或位置无效");
            }
            
        } catch (Exception e) {
            return FunctionResult.error("设置方块时发生错误: " + e.getMessage());
        }
    }
    
    @Override
    public boolean hasPermission(PlayerEntity player) {
        return PermissionHelper.canModifyWorld(player);
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
