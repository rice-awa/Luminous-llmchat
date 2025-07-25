package com.riceawa.llm.function;

import com.google.gson.JsonObject;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;

/**
 * LLM Function接口，定义可被LLM调用的函数
 */
public interface LLMFunction {
    
    /**
     * 获取函数名称
     */
    String getName();
    
    /**
     * 获取函数描述
     */
    String getDescription();
    
    /**
     * 获取函数参数定义（JSON Schema格式）
     */
    JsonObject getParametersSchema();
    
    /**
     * 执行函数
     * 
     * @param player 调用函数的玩家
     * @param server 服务器实例
     * @param arguments 函数参数（JSON格式）
     * @return 函数执行结果
     */
    FunctionResult execute(PlayerEntity player, MinecraftServer server, JsonObject arguments);
    
    /**
     * 检查玩家是否有权限调用此函数
     * 
     * @param player 玩家
     * @return 是否有权限
     */
    boolean hasPermission(PlayerEntity player);
    
    /**
     * 函数是否启用
     */
    boolean isEnabled();
    
    /**
     * 获取函数类别
     */
    String getCategory();
    
    /**
     * 函数执行结果
     */
    class FunctionResult {
        private final boolean success;
        private final String result;
        private final String error;
        private final JsonObject data;

        public FunctionResult(boolean success, String result) {
            this(success, result, null, null);
        }

        public FunctionResult(boolean success, String result, String error) {
            this(success, result, error, null);
        }

        public FunctionResult(boolean success, String result, String error, JsonObject data) {
            this.success = success;
            this.result = result;
            this.error = error;
            this.data = data;
        }

        public static FunctionResult success(String result) {
            return new FunctionResult(true, result);
        }

        public static FunctionResult success(String result, JsonObject data) {
            return new FunctionResult(true, result, null, data);
        }

        public static FunctionResult error(String error) {
            return new FunctionResult(false, null, error);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getResult() {
            return result;
        }

        public String getError() {
            return error;
        }

        public JsonObject getData() {
            return data;
        }

        public String getDisplayMessage() {
            if (success) {
                return result != null ? result : "操作成功";
            } else {
                return error != null ? error : "操作失败";
            }
        }
    }
}
