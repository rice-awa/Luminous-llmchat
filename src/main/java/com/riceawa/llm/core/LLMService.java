package com.riceawa.llm.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * LLM服务接口，定义与各种LLM API交互的标准方法
 */
public interface LLMService {
    
    /**
     * 发送聊天请求并获取响应
     * 
     * @param messages 消息列表
     * @param config 请求配置
     * @return 异步响应结果
     */
    CompletableFuture<LLMResponse> chat(List<LLMMessage> messages, LLMConfig config);
    
    /**
     * 流式聊天请求
     * 
     * @param messages 消息列表
     * @param config 请求配置
     * @param callback 流式响应回调
     * @return 异步任务
     */
    CompletableFuture<Void> chatStream(List<LLMMessage> messages, LLMConfig config, StreamCallback callback);
    
    /**
     * 获取支持的模型列表
     * 
     * @return 模型列表
     */
    List<String> getSupportedModels();
    
    /**
     * 检查服务是否可用
     * 
     * @return 是否可用
     */
    boolean isAvailable();
    
    /**
     * 获取服务名称
     * 
     * @return 服务名称
     */
    String getServiceName();
    
    /**
     * 流式响应回调接口
     */
    interface StreamCallback {
        /**
         * 接收到新的文本块
         * 
         * @param chunk 文本块
         */
        void onChunk(String chunk);
        
        /**
         * 流式响应完成
         * 
         * @param response 完整响应
         */
        void onComplete(LLMResponse response);
        
        /**
         * 发生错误
         * 
         * @param error 错误信息
         */
        void onError(Throwable error);
    }
}
