package com.riceawa.llm.function.impl;

import com.google.gson.JsonObject;
import com.riceawa.llm.function.LLMFunction;
import com.riceawa.llm.function.PermissionHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
        return "执行Minecraft服务器控制台指令。返回结构化JSON响应，包含执行状态、输出信息、错误信息和执行时间等详细信息。";
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
            // 创建输出捕获器
            List<String> outputMessages = new ArrayList<>();

            // 获取服务器控制台命令源
            ServerCommandSource consoleSource = server.getCommandSource();

            // 创建自定义的CommandOutput来捕获输出
            CommandOutputCapture outputCapture = new CommandOutputCapture(outputMessages);

            // 创建自定义的CommandSource来捕获输出
            ServerCommandSource captureSource = new ServerCommandSource(
                outputCapture,
                consoleSource.getPosition(),
                consoleSource.getRotation(),
                consoleSource.getWorld(),
                4, // 最高权限级别
                consoleSource.getName(),
                consoleSource.getDisplayName(),
                consoleSource.getServer(),
                consoleSource.getEntity()
            );

            // 执行命令并获取返回值
            int resultCode = 0;
            String output = "";
            String error = "";

            try {
                // 通过自定义CommandSource执行命令
                System.out.println("[ExecuteCommandFunction] 开始执行命令: " + command);

                // 尝试使用CommandDispatcher直接执行命令并获取返回值
                try {
                    resultCode = server.getCommandManager().getDispatcher().execute(command, captureSource);
                    System.out.println("[ExecuteCommandFunction] 命令执行完成，返回码: " + resultCode + ", 捕获到 " + outputMessages.size() + " 条消息");
                } catch (Exception e) {
                    // 如果直接执行失败，尝试使用executeWithPrefix
                    System.out.println("[ExecuteCommandFunction] 直接执行失败，尝试executeWithPrefix: " + e.getMessage());
                    server.getCommandManager().executeWithPrefix(captureSource, command);
                    resultCode = 1; // 如果没有异常，认为成功
                    System.out.println("[ExecuteCommandFunction] executeWithPrefix完成，捕获到 " + outputMessages.size() + " 条消息");
                }

                // 收集输出信息
                if (!outputMessages.isEmpty()) {
                    output = "捕获到的输出:\n" + String.join("\n", outputMessages);
                } else {
                    // 如果没有捕获到输出，尝试使用原始命令源执行并提供详细信息
                    output = "命令执行成功，但未捕获到输出。";

                    // 对于某些特定命令，提供更详细的信息
                    String commandInfo = getCommandInfo(command);
                    if (!commandInfo.isEmpty()) {
                        output += "\n" + commandInfo;
                    }

                    // 添加调试信息
                    output += "\n[调试] 命令: " + command +
                             ", 捕获器消息数: " + outputCapture.getMessageCount() +
                             ", 输出捕获器配置: shouldReceiveFeedback=" + outputCapture.shouldReceiveFeedback() +
                             ", shouldTrackOutput=" + outputCapture.shouldTrackOutput();
                }

            } catch (RuntimeException e) {
                resultCode = 0;
                error = "命令执行失败: " + e.getMessage();
                // 检查是否是权限问题
                if (e.getMessage() != null && e.getMessage().toLowerCase().contains("permission")) {
                    error += "\n提示: 可能是权限不足导致的错误";
                }
                // 检查是否是语法错误
                if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("syntax") ||
                    e.getMessage().toLowerCase().contains("unknown") ||
                    e.getMessage().toLowerCase().contains("expected"))) {
                    error += "\n提示: 可能是命令语法错误";
                }
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
     * 根据命令类型提供额外信息
     */
    private String getCommandInfo(String command) {
        String[] parts = command.split("\\s+");
        if (parts.length == 0) return "";

        String commandName = parts[0].toLowerCase();

        switch (commandName) {
            case "say":
                return "消息已广播到所有玩家";
            case "tell":
            case "msg":
            case "w":
                return "私人消息已发送";
            case "give":
                return "物品已给予玩家";
            case "tp":
            case "teleport":
                return "传送已执行";
            case "time":
                return "时间已设置";
            case "weather":
                return "天气已更改";
            case "gamemode":
                return "游戏模式已更改";
            case "difficulty":
                return "难度已设置";
            case "kill":
                return "实体已被清除";
            case "clear":
                return "物品已清除";
            case "effect":
                return "效果已应用";
            case "enchant":
                return "附魔已应用";
            case "experience":
            case "xp":
                return "经验已给予";
            case "fill":
                return "方块已填充";
            case "setblock":
                return "方块已设置";
            case "summon":
                return "实体已生成";
            case "list":
                return "玩家列表已显示";
            case "whitelist":
                return "白名单已更新";
            case "ban":
            case "ban-ip":
                return "封禁已执行";
            case "pardon":
            case "pardon-ip":
                return "解封已执行";
            case "op":
                return "OP权限已授予";
            case "deop":
                return "OP权限已移除";
            default:
                return "";
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

    /**
     * 自定义命令输出捕获器
     */
    private static class CommandOutputCapture implements net.minecraft.server.command.CommandOutput {
        private final List<String> outputMessages;
        private int messageCount = 0;

        public CommandOutputCapture(List<String> outputMessages) {
            this.outputMessages = outputMessages;
        }

        @Override
        public void sendMessage(Text message) {
            messageCount++;
            String messageText = message.getString();
            outputMessages.add("[消息" + messageCount + "] " + messageText);
            // 添加调试日志
            System.out.println("[CommandOutputCapture] 捕获到消息: " + messageText);
        }

        @Override
        public boolean shouldReceiveFeedback() {
            return true;
        }

        @Override
        public boolean shouldTrackOutput() {
            return true;
        }

        @Override
        public boolean shouldBroadcastConsoleToOps() {
            return false;
        }

        public int getMessageCount() {
            return messageCount;
        }
    }
}
