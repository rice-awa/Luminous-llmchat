package com.riceawa.llm.function.impl;

import com.google.gson.JsonObject;
import com.riceawa.llm.function.LLMFunction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * 获取玩家背包信息的函数
 */
public class InventoryFunction implements LLMFunction {
    
    @Override
    public String getName() {
        return "get_inventory";
    }
    
    @Override
    public String getDescription() {
        return "获取玩家的背包物品信息";
    }
    
    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        
        // 可选参数：是否显示空槽位
        JsonObject showEmpty = new JsonObject();
        showEmpty.addProperty("type", "boolean");
        showEmpty.addProperty("description", "是否显示空槽位");
        showEmpty.addProperty("default", false);
        properties.add("show_empty", showEmpty);
        
        // 可选参数：目标玩家名称（需要OP权限）
        JsonObject playerName = new JsonObject();
        playerName.addProperty("type", "string");
        playerName.addProperty("description", "要查询的玩家名称，不填则查询自己（查询他人需要OP权限）");
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
                
                // 检查权限：只有OP才能查看其他玩家的背包
                if (!foundPlayer.equals(player) && !server.getPlayerManager().isOperator(player.getGameProfile())) {
                    return FunctionResult.error("没有权限查看其他玩家的背包");
                }
                
                targetPlayer = foundPlayer;
            }
            
            boolean showEmpty = arguments.has("show_empty") && 
                              arguments.get("show_empty").getAsBoolean();
            
            StringBuilder inventory = new StringBuilder();
            inventory.append("=== ").append(targetPlayer.getName().getString()).append(" 的背包 ===\n");
            
            // 主背包 (0-35)
            inventory.append("\n--- 主背包 ---\n");
            int itemCount = 0;
            for (int i = 0; i < 36; i++) {
                ItemStack stack = targetPlayer.getInventory().getStack(i);
                if (!stack.isEmpty()) {
                    String itemName = getItemDisplayName(stack);
                    inventory.append("槽位 ").append(i).append(": ")
                           .append(itemName).append(" x").append(stack.getCount());
                    
                    // 如果有附魔或其他特殊属性
                    if (stack.hasNbt()) {
                        inventory.append(" (有特殊属性)");
                    }
                    inventory.append("\n");
                    itemCount++;
                } else if (showEmpty) {
                    inventory.append("槽位 ").append(i).append(": 空\n");
                }
            }
            
            // 装备栏
            inventory.append("\n--- 装备栏 ---\n");
            String[] equipmentSlots = {"头盔", "胸甲", "护腿", "靴子"};
            for (int i = 0; i < 4; i++) {
                ItemStack stack = targetPlayer.getInventory().armor.get(i);
                inventory.append(equipmentSlots[i]).append(": ");
                if (!stack.isEmpty()) {
                    String itemName = getItemDisplayName(stack);
                    inventory.append(itemName);
                    if (stack.hasNbt()) {
                        inventory.append(" (有特殊属性)");
                    }
                } else {
                    inventory.append("无");
                }
                inventory.append("\n");
            }
            
            // 副手
            ItemStack offhandStack = targetPlayer.getInventory().offHand.get(0);
            inventory.append("副手: ");
            if (!offhandStack.isEmpty()) {
                String itemName = getItemDisplayName(offhandStack);
                inventory.append(itemName).append(" x").append(offhandStack.getCount());
                if (offhandStack.hasNbt()) {
                    inventory.append(" (有特殊属性)");
                }
            } else {
                inventory.append("无");
            }
            inventory.append("\n");
            
            // 统计信息
            inventory.append("\n--- 统计 ---\n");
            inventory.append("物品种类数: ").append(itemCount).append("\n");
            inventory.append("已使用槽位: ").append(itemCount).append("/36\n");
            
            return FunctionResult.success(inventory.toString());
            
        } catch (Exception e) {
            return FunctionResult.error("获取背包信息失败: " + e.getMessage());
        }
    }
    
    private String getItemDisplayName(ItemStack stack) {
        Text displayName = stack.getName();
        String name = displayName.getString();
        
        // 如果是默认名称，尝试获取更友好的中文名称
        String itemId = stack.getItem().toString();
        return name.isEmpty() ? itemId : name;
    }
    
    @Override
    public boolean hasPermission(PlayerEntity player) {
        return true; // 所有玩家都可以查看自己的背包
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
