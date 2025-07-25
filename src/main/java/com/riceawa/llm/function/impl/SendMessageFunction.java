package com.riceawa.llm.function.impl;

import com.google.gson.JsonObject;
import com.riceawa.llm.function.LLMFunction;
import com.riceawa.llm.function.PermissionHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 发送消息给其他玩家的函数
 */
public class SendMessageFunction implements LLMFunction {
    
    @Override
    public String getName() {
        return "send_message";
    }
    
    @Override
    public String getDescription() {
        return "向指定玩家或所有玩家发送消息";
    }
    
    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject required = new JsonObject();
        
        // 必需参数：消息内容
        JsonObject message = new JsonObject();
        message.addProperty("type", "string");
        message.addProperty("description", "要发送的消息内容");
        properties.add("message", message);
        
        // 可选参数：目标玩家
        JsonObject target = new JsonObject();
        target.addProperty("type", "string");
        target.addProperty("description", "目标玩家名称，不填则发送给所有玩家（需要OP权限）");
        properties.add("target", target);
        
        // 可选参数：消息类型
        JsonObject messageType = new JsonObject();
        messageType.addProperty("type", "string");
        messageType.addProperty("description", "消息类型：chat(聊天), system(系统消息), actionbar(动作栏)");
        messageType.addProperty("default", "chat");
        properties.add("message_type", messageType);
        
        schema.add("properties", properties);
        schema.add("required", new com.google.gson.JsonArray());
        schema.getAsJsonArray("required").add("message");
        
        return schema;
    }
    
    @Override
    public FunctionResult execute(PlayerEntity player, MinecraftServer server, JsonObject arguments) {
        try {
            // 获取必需参数
            if (!arguments.has("message")) {
                return FunctionResult.error("缺少必需参数: message");
            }
            
            String messageContent = arguments.get("message").getAsString();
            if (messageContent.trim().isEmpty()) {
                return FunctionResult.error("消息内容不能为空");
            }
            
            String target = arguments.has("target") ? 
                arguments.get("target").getAsString() : null;
            String messageType = arguments.has("message_type") ? 
                arguments.get("message_type").getAsString() : "chat";
            
            // 构建消息
            Text messageText = buildMessage(player, messageContent, messageType);
            
            if (target == null || target.trim().isEmpty()) {
                // 发送给所有玩家 - 需要OP权限
                if (!PermissionHelper.canSendBroadcast(player)) {
                    return FunctionResult.error(PermissionHelper.getPermissionErrorMessage("向所有玩家发送消息"));
                }
                
                // 发送给所有在线玩家
                for (ServerPlayerEntity onlinePlayer : server.getPlayerManager().getPlayerList()) {
                    sendMessageToPlayer(onlinePlayer, messageText, messageType);
                }
                
                return FunctionResult.success("消息已发送给所有在线玩家 (" + 
                    server.getCurrentPlayerCount() + " 人)");
                
            } else {
                // 发送给指定玩家
                ServerPlayerEntity targetPlayer = server.getPlayerManager().getPlayer(target);
                if (targetPlayer == null) {
                    return FunctionResult.error("找不到玩家: " + target);
                }
                
                sendMessageToPlayer(targetPlayer, messageText, messageType);
                return FunctionResult.success("消息已发送给 " + target);
            }
            
        } catch (Exception e) {
            return FunctionResult.error("发送消息失败: " + e.getMessage());
        }
    }
    
    private Text buildMessage(PlayerEntity sender, String content, String messageType) {
        String senderName = sender.getName().getString();
        
        switch (messageType.toLowerCase()) {
            case "system":
                return Text.literal("[系统] " + content).formatted(Formatting.YELLOW);
            case "actionbar":
                return Text.literal(content).formatted(Formatting.AQUA);
            case "chat":
            default:
                return Text.literal("[" + senderName + " 通过AI] " + content).formatted(Formatting.GREEN);
        }
    }
    
    private void sendMessageToPlayer(ServerPlayerEntity player, Text message, String messageType) {
        switch (messageType.toLowerCase()) {
            case "actionbar":
                player.sendMessage(message, true); // true = actionbar
                break;
            case "system":
            case "chat":
            default:
                player.sendMessage(message, false); // false = chat
                break;
        }
    }
    
    @Override
    public boolean hasPermission(PlayerEntity player) {
        // 所有玩家都可以使用此功能，但向所有玩家发送消息需要OP权限
        return true;
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
    
    @Override
    public String getCategory() {
        return "interaction";
    }
}
