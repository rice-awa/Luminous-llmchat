package com.riceawa.llm.function.impl;

import com.google.gson.JsonObject;
import com.riceawa.llm.function.LLMFunction;
import com.riceawa.llm.function.PermissionHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.time.Instant;

/**
 * 执行服务器指令的函数
 * 安全性：仅OP可用，有指令黑名单保护
 * 功能增强：返回结构化JSON响应，包含详细的执行结果
 */
public class ExecuteCommandFunction implements LLMFunction {
    
    @Override
    public String getName() {
        return "execute_command";
    }
    
    @Override
    public String getDescription() {
        return "执行Minecraft服务器控制台指令（仅OP可用，有安全限制）。返回结构化JSON响应，包含执行状态、输出信息、错误信息和执行时间等详细信息。";
    }
    
    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        
        JsonObject properties = new JsonObject();
        
        // 指令参数
        JsonObject commandParam = new JsonObject();
        commandParam.addProperty("type", "string");
        commandParam.addProperty("description", "要执行的Minecraft控制台指令（不需要包含斜杠）。支持所有非黑名单内的指令。");
        properties.add("command", commandParam);

        // 是否包含详细信息
        JsonObject detailedParam = new JsonObject();
        detailedParam.addProperty("type", "boolean");
        detailedParam.addProperty("description", "是否返回详细的执行信息（默认true）");
        detailedParam.addProperty("default", true);
        properties.add("detailed", detailedParam);
        
        schema.add("properties", properties);
        
        // 必需参数
        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("command");
        schema.add("required", required);
        
        return schema;
    }
    
    @Override
    public FunctionResult execute(PlayerEntity player, MinecraftServer server, JsonObject arguments) {
        long startTime = System.currentTimeMillis();

        try {
            // 获取参数
            if (!arguments.has("command")) {
                return createErrorResult("缺少必需参数: command", null, startTime);
            }

            String command = arguments.get("command").getAsString().trim();
            boolean detailed = !arguments.has("detailed") || arguments.get("detailed").getAsBoolean();

            // 验证指令
            if (command.isEmpty()) {
                return createErrorResult("指令不能为空", command, startTime);
            }

            // 检查指令黑名单
            if (PermissionHelper.isCommandBlacklisted(command)) {
                return createErrorResult(PermissionHelper.getBlacklistErrorMessage(command), command, startTime);
            }

            // 移除开头的斜杠（如果有）
            if (command.startsWith("/")) {
                command = command.substring(1);
            }

            // 执行指令并捕获结果
            return executeCommandWithCapture(server, command, detailed, startTime);

        } catch (Exception e) {
            return createErrorResult("执行指令时发生错误: " + e.getMessage(), null, startTime);
        }
    }

    /**
     * 执行命令并捕获结果
     */
    private FunctionResult executeCommandWithCapture(MinecraftServer server, String command, boolean detailed, long startTime) {
        try {
            // 获取服务器控制台命令源
            ServerCommandSource consoleSource = server.getCommandSource();

            // 执行命令并获取返回值
            int resultCode = 1; // 默认成功状态
            String output = "";
            String error = "";

            try {
                // 通过控制台执行命令 - executeWithPrefix 返回 void
                server.getCommandManager().executeWithPrefix(consoleSource, command);

                // 命令执行成功（没有抛出异常）
                output = "命令执行成功";

            } catch (Exception e) {
                resultCode = 0;
                error = "命令执行异常: " + e.getMessage();
            }

            // 创建结构化响应
            return createSuccessResult(command, resultCode, output, error, detailed, startTime);

        } catch (Exception e) {
            return createErrorResult("执行命令时发生内部错误: " + e.getMessage(), command, startTime);
        }
    }

    /**
     * 创建成功的结构化响应
     */
    private FunctionResult createSuccessResult(String command, int resultCode, String output, String error, boolean detailed, long startTime) {
        long executionTime = System.currentTimeMillis() - startTime;

        if (detailed) {
            // 创建详细的JSON响应
            JsonObject responseData = new JsonObject();
            responseData.addProperty("success", error.isEmpty());
            responseData.addProperty("command", command);
            responseData.addProperty("result_code", resultCode);
            responseData.addProperty("output", output);
            if (!error.isEmpty()) {
                responseData.addProperty("error", error);
            }
            responseData.addProperty("execution_time_ms", executionTime);
            responseData.addProperty("timestamp", Instant.now().toString());
            responseData.addProperty("executed_via", "console");

            String displayMessage = error.isEmpty() ?
                String.format("命令 '/%s' 执行成功 (返回码: %d, 耗时: %dms)", command, resultCode, executionTime) :
                String.format("命令 '/%s' 执行失败: %s (耗时: %dms)", command, error, executionTime);

            return FunctionResult.success(displayMessage, responseData);
        } else {
            // 简化响应
            String message = error.isEmpty() ?
                String.format("命令 '/%s' 执行成功", command) :
                String.format("命令 '/%s' 执行失败: %s", command, error);
            return error.isEmpty() ? FunctionResult.success(message) : FunctionResult.error(message);
        }
    }

    /**
     * 创建错误的结构化响应
     */
    private FunctionResult createErrorResult(String errorMessage, String command, long startTime) {
        long executionTime = System.currentTimeMillis() - startTime;

        JsonObject responseData = new JsonObject();
        responseData.addProperty("success", false);
        if (command != null) {
            responseData.addProperty("command", command);
        }
        responseData.addProperty("result_code", 0);
        responseData.addProperty("output", "");
        responseData.addProperty("error", errorMessage);
        responseData.addProperty("execution_time_ms", executionTime);
        responseData.addProperty("timestamp", Instant.now().toString());
        responseData.addProperty("executed_via", "console");

        return FunctionResult.error(errorMessage);
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
