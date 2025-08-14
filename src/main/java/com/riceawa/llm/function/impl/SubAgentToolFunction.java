package com.riceawa.llm.function.impl;

import com.google.gson.JsonObject;
import com.riceawa.llm.function.LLMFunction;
import com.riceawa.llm.subagent.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

/**
 * 通用子代理工具函数基类
 * 实现LLMFunction接口，提供子代理调用的通用框架
 */
public abstract class SubAgentToolFunction implements LLMFunction {
    
    @Override
    public FunctionResult execute(PlayerEntity player, MinecraftServer server, JsonObject arguments) {
        // 检查权限
        if (!hasPermission(player)) {
            return FunctionResult.error("没有权限调用此函数");
        }
        
        // 验证参数
        String validationError = validateArguments(arguments);
        if (validationError != null) {
            return FunctionResult.error("参数验证失败: " + validationError);
        }
        
        // 创建任务ID
        String taskId = "task-" + System.currentTimeMillis() + "-" + 
                       Integer.toHexString(System.identityHashCode(this));
        
        try {
            // 创建子代理任务
            SubAgentTask<? extends SubAgentResult> task = createSubAgentTask(player, arguments, taskId);
            
            // 提交任务到队列
            boolean submitted = submitTask(task);
            if (!submitted) {
                return FunctionResult.error("任务提交失败，队列可能已满");
            }
            
            // 返回任务ID，让调用者可以查询任务状态
            JsonObject data = new JsonObject();
            data.addProperty("taskId", taskId);
            data.addProperty("status", "submitted");
            data.addProperty("message", "任务已提交，请使用任务ID查询结果");
            
            return FunctionResult.success("任务已提交，任务ID: " + taskId, data);
            
        } catch (Exception e) {
            return FunctionResult.error("创建任务失败: " + e.getMessage());
        }
    }
    
    /**
     * 验证参数
     * 
     * @param arguments 参数
     * @return 错误信息，如果没有错误则返回null
     */
    protected abstract String validateArguments(JsonObject arguments);
    
    /**
     * 创建子代理任务
     * 
     * @param player 玩家
     * @param arguments 参数
     * @param taskId 任务ID
     * @return 子代理任务
     */
    protected abstract SubAgentTask<? extends SubAgentResult> createSubAgentTask(
        PlayerEntity player, JsonObject arguments, String taskId);
    
    /**
     * 提交任务到队列
     * 
     * @param task 任务
     * @return 是否成功提交
     */
    protected boolean submitTask(SubAgentTask<? extends SubAgentResult> task) {
        try {
            // 获取任务队列实例并提交任务
            // 这里需要获取UniversalTaskQueue的实例
            // 暂时返回true，实际实现需要获取队列实例
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 等待任务完成并获取结果
     * 
     * @param taskId 任务ID
     * @param timeoutMs 超时时间（毫秒）
     * @return 任务结果
     */
    protected CompletableFuture<FunctionResult> waitForTaskResult(String taskId, long timeoutMs) {
        CompletableFuture<FunctionResult> future = new CompletableFuture<>();
        
        // 这里需要实现轮询任务状态的逻辑
        // 暂时返回一个模拟的结果
        new Thread(() -> {
            try {
                Thread.sleep(timeoutMs);
                future.complete(FunctionResult.success("任务完成"));
            } catch (InterruptedException e) {
                future.complete(FunctionResult.error("任务被中断"));
            }
        }).start();
        
        return future;
    }
    
    /**
     * 将子代理结果转换为函数结果
     * 
     * @param result 子代理结果
     * @return 函数结果
     */
    protected FunctionResult convertToFunctionResult(SubAgentResult result) {
        if (result == null) {
            return FunctionResult.error("任务结果为空");
        }
        
        if (result.isSuccess()) {
            JsonObject data = new JsonObject();
            data.addProperty("success", true);
            data.addProperty("processingTime", result.getTotalProcessingTimeMs());
            
            // 添加元数据
            Map<String, Object> metadata = result.getMetadata();
            if (metadata != null && !metadata.isEmpty()) {
                JsonObject metadataJson = new JsonObject();
                for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                    if (entry.getValue() instanceof String) {
                        metadataJson.addProperty(entry.getKey(), (String) entry.getValue());
                    } else if (entry.getValue() instanceof Number) {
                        metadataJson.addProperty(entry.getKey(), (Number) entry.getValue());
                    } else if (entry.getValue() instanceof Boolean) {
                        metadataJson.addProperty(entry.getKey(), (Boolean) entry.getValue());
                    }
                }
                data.add("metadata", metadataJson);
            }
            
            return FunctionResult.success(result.getDisplayMessage(), data);
        } else {
            return FunctionResult.error(result.getError());
        }
    }
}