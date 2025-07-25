package com.riceawa.llm.function.impl;

import com.google.gson.JsonObject;
import com.riceawa.llm.function.LLMFunction;
import com.riceawa.llm.function.PermissionHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;

/**
 * 执行服务器指令的函数
 * 安全性：仅OP可用，有指令黑名单保护
 */
public class ExecuteCommandFunction implements LLMFunction {
    
    @Override
    public String getName() {
        return "execute_command";
    }
    
    @Override
    public String getDescription() {
        return "执行Minecraft服务器指令（仅OP可用，有安全限制）";
    }
    
    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        
        JsonObject properties = new JsonObject();
        
        // 指令参数
        JsonObject commandParam = new JsonObject();
        commandParam.addProperty("type", "string");
        commandParam.addProperty("description", "要执行的Minecraft指令（不需要包含斜杠）");
        properties.add("command", commandParam);
        
        // 是否静默执行
        JsonObject silentParam = new JsonObject();
        silentParam.addProperty("type", "boolean");
        silentParam.addProperty("description", "是否静默执行（不显示执行结果）");
        silentParam.addProperty("default", false);
        properties.add("silent", silentParam);
        
        schema.add("properties", properties);
        
        // 必需参数
        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("command");
        schema.add("required", required);
        
        return schema;
    }
    
    @Override
    public FunctionResult execute(PlayerEntity player, MinecraftServer server, JsonObject arguments) {
        try {
            // 获取参数
            if (!arguments.has("command")) {
                return FunctionResult.error("缺少必需参数: command");
            }
            
            String command = arguments.get("command").getAsString().trim();
            boolean silent = arguments.has("silent") && arguments.get("silent").getAsBoolean();
            
            // 验证指令
            if (command.isEmpty()) {
                return FunctionResult.error("指令不能为空");
            }
            
            // 检查指令黑名单
            if (PermissionHelper.isCommandBlacklisted(command)) {
                return FunctionResult.error(PermissionHelper.getBlacklistErrorMessage(command));
            }
            
            // 移除开头的斜杠（如果有）
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            
            // 简化实现：直接使用服务器执行指令
            try {
                // 创建一个临时的命令源来执行指令
                String fullCommand = "/" + command;

                // 通过服务器控制台执行指令
                server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);

                return FunctionResult.success(String.format("指令执行成功: %s", fullCommand));
            } catch (Exception e) {
                return FunctionResult.error(String.format(
                    "指令执行异常: /%s - %s", command, e.getMessage()));
            }

        } catch (Exception e) {
            return FunctionResult.error("执行指令时发生错误: " + e.getMessage());
        }
    }
    
    @Override
    public boolean hasPermission(PlayerEntity player) {
        return PermissionHelper.isOperator(player);
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
    
    @Override
    public String getCategory() {
        return "admin";
    }
}
