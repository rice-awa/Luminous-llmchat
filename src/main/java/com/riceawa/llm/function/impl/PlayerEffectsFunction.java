package com.riceawa.llm.function.impl;

import com.google.gson.JsonObject;
import com.riceawa.llm.function.LLMFunction;
import com.riceawa.llm.function.PermissionHelper;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 获取玩家状态效果的函数
 */
public class PlayerEffectsFunction implements LLMFunction {
    
    @Override
    public String getName() {
        return "get_player_effects";
    }
    
    @Override
    public String getDescription() {
        return "获取玩家当前的状态效果（药水效果）";
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
                    return FunctionResult.error(PermissionHelper.getPermissionErrorMessage("查看其他玩家的状态效果"));
                }
                
                targetPlayer = foundPlayer;
            }
            
            StringBuilder effects = new StringBuilder();
            effects.append("=== ").append(targetPlayer.getName().getString()).append(" 的状态效果 ===\n");
            
            var statusEffects = targetPlayer.getStatusEffects();
            
            if (statusEffects.isEmpty()) {
                effects.append("当前没有任何状态效果\n");
            } else {
                effects.append("当前状态效果数量: ").append(statusEffects.size()).append("\n\n");
                
                int index = 1;
                for (StatusEffectInstance effect : statusEffects) {
                    String effectName = getEffectDisplayName(effect);
                    int amplifier = effect.getAmplifier();
                    int duration = effect.getDuration();
                    
                    effects.append(index).append(". ").append(effectName);
                    
                    // 显示等级（如果大于0）
                    if (amplifier > 0) {
                        effects.append(" ").append(amplifier + 1).append("级");
                    }
                    
                    // 显示剩余时间
                    if (duration > 0) {
                        int minutes = duration / 1200; // 20 ticks per second * 60 seconds per minute
                        int seconds = (duration % 1200) / 20;
                        effects.append(" (剩余: ");
                        if (minutes > 0) {
                            effects.append(minutes).append("分");
                        }
                        effects.append(seconds).append("秒)");
                    } else {
                        effects.append(" (永久)");
                    }
                    
                    // 显示效果类型
                    if (effect.getEffectType().value().isBeneficial()) {
                        effects.append(" [有益]");
                    } else {
                        effects.append(" [有害]");
                    }
                    
                    // 显示是否可见
                    if (!effect.shouldShowParticles()) {
                        effects.append(" [隐藏粒子]");
                    }
                    
                    if (!effect.shouldShowIcon()) {
                        effects.append(" [隐藏图标]");
                    }
                    
                    effects.append("\n");
                    index++;
                }
                
                // 统计信息
                long beneficialCount = statusEffects.stream()
                    .mapToLong(effect -> effect.getEffectType().value().isBeneficial() ? 1 : 0)
                    .sum();
                long harmfulCount = statusEffects.size() - beneficialCount;
                
                effects.append("\n=== 统计 ===\n");
                effects.append("有益效果: ").append(beneficialCount).append("\n");
                effects.append("有害效果: ").append(harmfulCount).append("\n");
            }
            
            return FunctionResult.success(effects.toString());
            
        } catch (Exception e) {
            return FunctionResult.error("获取玩家状态效果失败: " + e.getMessage());
        }
    }
    
    private String getEffectDisplayName(StatusEffectInstance effect) {
        String effectId = effect.getEffectType().toString();
        
        // 尝试获取本地化名称
        try {
            String translationKey = effect.getEffectType().value().getTranslationKey();
            // 这里可以添加中文翻译映射
            return getChineseEffectName(effectId);
        } catch (Exception e) {
            return effectId;
        }
    }
    
    private String getChineseEffectName(String effectId) {
        // 常见状态效果的中文翻译
        switch (effectId.toLowerCase()) {
            case "speed": return "速度";
            case "slowness": return "缓慢";
            case "haste": return "急迫";
            case "mining_fatigue": return "挖掘疲劳";
            case "strength": return "力量";
            case "instant_health": return "瞬间治疗";
            case "instant_damage": return "瞬间伤害";
            case "jump_boost": return "跳跃提升";
            case "nausea": return "反胃";
            case "regeneration": return "生命恢复";
            case "resistance": return "抗性提升";
            case "fire_resistance": return "抗火";
            case "water_breathing": return "水下呼吸";
            case "invisibility": return "隐身";
            case "blindness": return "失明";
            case "night_vision": return "夜视";
            case "hunger": return "饥饿";
            case "weakness": return "虚弱";
            case "poison": return "中毒";
            case "wither": return "凋零";
            case "health_boost": return "生命提升";
            case "absorption": return "伤害吸收";
            case "saturation": return "饱和";
            case "glowing": return "发光";
            case "levitation": return "漂浮";
            case "luck": return "幸运";
            case "bad_luck": return "霉运";
            case "slow_falling": return "缓降";
            case "conduit_power": return "潮涌能量";
            case "dolphins_grace": return "海豚的恩惠";
            case "bad_omen": return "不祥之兆";
            case "hero_of_the_village": return "村庄英雄";
            default: return effectId;
        }
    }
    
    @Override
    public boolean hasPermission(PlayerEntity player) {
        return true; // 所有玩家都可以查看自己的状态效果
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
