package com.riceawa.llm.function.impl;

import com.google.gson.JsonObject;
import com.riceawa.llm.function.LLMFunction;
import com.riceawa.llm.function.PermissionHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * 生成实体的函数
 * 安全性：仅OP可用，限制实体类型和数量
 */
public class SummonEntityFunction implements LLMFunction {
    
    private static final int MAX_DISTANCE = 50; // 最大生成距离
    private static final int MAX_COUNT = 10;    // 最大生成数量
    
    @Override
    public String getName() {
        return "summon_entity";
    }
    
    @Override
    public String getDescription() {
        return "在指定位置生成实体";
    }
    
    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        
        JsonObject properties = new JsonObject();
        
        // 实体类型
        JsonObject entityParam = new JsonObject();
        entityParam.addProperty("type", "string");
        entityParam.addProperty("description", "实体类型（如：cow, pig, zombie等）");
        properties.add("entity_type", entityParam);
        
        // X坐标
        JsonObject xParam = new JsonObject();
        xParam.addProperty("type", "number");
        xParam.addProperty("description", "X坐标（可以是小数）");
        properties.add("x", xParam);
        
        // Y坐标
        JsonObject yParam = new JsonObject();
        yParam.addProperty("type", "number");
        yParam.addProperty("description", "Y坐标（可以是小数）");
        properties.add("y", yParam);
        
        // Z坐标
        JsonObject zParam = new JsonObject();
        zParam.addProperty("type", "number");
        zParam.addProperty("description", "Z坐标（可以是小数）");
        properties.add("z", zParam);
        
        // 数量
        JsonObject countParam = new JsonObject();
        countParam.addProperty("type", "integer");
        countParam.addProperty("description", "生成数量（1-10）");
        countParam.addProperty("default", 1);
        countParam.addProperty("minimum", 1);
        countParam.addProperty("maximum", MAX_COUNT);
        properties.add("count", countParam);
        
        schema.add("properties", properties);
        
        // 必需参数
        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("entity_type");
        required.add("x");
        required.add("y");
        required.add("z");
        schema.add("required", required);
        
        return schema;
    }
    
    @Override
    public FunctionResult execute(PlayerEntity player, MinecraftServer server, JsonObject arguments) {
        try {
            // 获取参数
            if (!arguments.has("entity_type") || !arguments.has("x") || 
                !arguments.has("y") || !arguments.has("z")) {
                return FunctionResult.error("缺少必需参数: entity_type, x, y, z");
            }
            
            String entityType = arguments.get("entity_type").getAsString().trim().toLowerCase();
            double x = arguments.get("x").getAsDouble();
            double y = arguments.get("y").getAsDouble();
            double z = arguments.get("z").getAsDouble();
            int count = arguments.has("count") ? arguments.get("count").getAsInt() : 1;
            
            // 验证数量
            if (count < 1 || count > MAX_COUNT) {
                return FunctionResult.error(String.format("生成数量必须在1到%d之间", MAX_COUNT));
            }
            
            // 检查距离限制
            Vec3d targetPos = new Vec3d(x, y, z);
            Vec3d playerPos = player.getPos();
            double distance = playerPos.distanceTo(targetPos);
            
            if (distance > MAX_DISTANCE) {
                return FunctionResult.error(String.format(
                    "目标位置距离过远（%.1f方块），最大允许距离为%d方块", distance, MAX_DISTANCE));
            }
            
            // 获取实体类型
            Identifier entityId;
            if (entityType.contains(":")) {
                entityId = Identifier.of(entityType); // 1.19+ 推荐方式
            } else {
                entityId = Identifier.of("minecraft", entityType); // 默认命名空间为 minecraft
            }
            
            EntityType<?> type = Registries.ENTITY_TYPE.get(entityId);
            if (type == null) {
                return FunctionResult.error("未知的实体类型: " + entityType);
            }
            
            ServerWorld world = (ServerWorld) player.getWorld();
            int successCount = 0;
            
            // 生成实体
            for (int i = 0; i < count; i++) {
                try {
                    // 为每个实体添加小的随机偏移，避免重叠
                    double offsetX = x + (Math.random() - 0.5) * 2;
                    double offsetZ = z + (Math.random() - 0.5) * 2;
                    
                    Entity entity = type.create(world, net.minecraft.entity.SpawnReason.COMMAND);
                    if (entity != null) {
                        entity.refreshPositionAndAngles(offsetX, y, offsetZ, 0.0F, 0.0F);

                        if (world.spawnEntity(entity)) {
                            successCount++;
                        }
                    }
                } catch (Exception e) {
                    // 单个实体生成失败，继续尝试其他实体
                    continue;
                }
            }
            
            if (successCount > 0) {
                return FunctionResult.success(String.format(
                    "成功在位置 (%.1f, %.1f, %.1f) 生成了 %d 个 %s", 
                    x, y, z, successCount, entityType));
            } else {
                return FunctionResult.error("生成实体失败，可能是位置无效或实体类型不支持");
            }
            
        } catch (Exception e) {
            return FunctionResult.error("生成实体时发生错误: " + e.getMessage());
        }
    }
    
    @Override
    public boolean hasPermission(PlayerEntity player) {
        return PermissionHelper.canSummonEntity(player);
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
