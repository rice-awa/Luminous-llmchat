package com.riceawa.llm.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.riceawa.llm.core.*;
import okhttp3.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * OpenAI API服务实现
 */
public class OpenAIService implements LLMService {
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String apiKey;
    private final String baseUrl;

    public OpenAIService(String apiKey) {
        this(apiKey, "https://api.openai.com/v1");
    }

    public OpenAIService(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    @Override
    public CompletableFuture<LLMResponse> chat(List<LLMMessage> messages, LLMConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject requestBody = buildRequestBody(messages, config);
                
                Request request = new Request.Builder()
                        .url(baseUrl + "/chat/completions")
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(requestBody.toString(), MediaType.get("application/json")))
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    String responseBody = response.body().string();
                    
                    if (!response.isSuccessful()) {
                        LLMResponse errorResponse = new LLMResponse();
                        errorResponse.setError("HTTP " + response.code() + ": " + responseBody);
                        return errorResponse;
                    }

                    return parseResponse(responseBody);
                }
            } catch (IOException e) {
                LLMResponse errorResponse = new LLMResponse();
                errorResponse.setError("Network error: " + e.getMessage());
                return errorResponse;
            }
        });
    }

    @Override
    public CompletableFuture<Void> chatStream(List<LLMMessage> messages, LLMConfig config, StreamCallback callback) {
        return CompletableFuture.runAsync(() -> {
            try {
                JsonObject requestBody = buildRequestBody(messages, config);
                requestBody.addProperty("stream", true);
                
                Request request = new Request.Builder()
                        .url(baseUrl + "/chat/completions")
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(requestBody.toString(), MediaType.get("application/json")))
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        callback.onError(new RuntimeException("HTTP " + response.code()));
                        return;
                    }

                    // 处理流式响应
                    processStreamResponse(response, callback);
                }
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    @Override
    public List<String> getSupportedModels() {
        return Arrays.asList(
                "gpt-4o",
                "gpt-4-turbo",
                "gpt-3.5-turbo",
                "gpt-3.5-turbo-16k"
        );
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    @Override
    public String getServiceName() {
        return "OpenAI";
    }

    /**
     * 构建请求体
     */
    private JsonObject buildRequestBody(List<LLMMessage> messages, LLMConfig config) {
        JsonObject requestBody = new JsonObject();
        
        // 设置模型
        requestBody.addProperty("model", config.getModel() != null ? config.getModel() : "gpt-3.5-turbo");
        
        // 设置消息
        JsonArray messagesArray = new JsonArray();
        for (LLMMessage message : messages) {
            JsonObject messageObj = new JsonObject();
            messageObj.addProperty("role", message.getRole().getValue());
            messageObj.addProperty("content", message.getContent());
            messagesArray.add(messageObj);
        }
        requestBody.add("messages", messagesArray);
        
        // 设置其他参数
        if (config.getTemperature() != null) {
            requestBody.addProperty("temperature", config.getTemperature());
        }
        if (config.getMaxTokens() != null) {
            requestBody.addProperty("max_tokens", config.getMaxTokens());
        }
        if (config.getTopP() != null) {
            requestBody.addProperty("top_p", config.getTopP());
        }
        if (config.getFrequencyPenalty() != null) {
            requestBody.addProperty("frequency_penalty", config.getFrequencyPenalty());
        }
        if (config.getPresencePenalty() != null) {
            requestBody.addProperty("presence_penalty", config.getPresencePenalty());
        }
        if (config.getStop() != null && !config.getStop().isEmpty()) {
            JsonArray stopArray = new JsonArray();
            for (String stop : config.getStop()) {
                stopArray.add(stop);
            }
            requestBody.add("stop", stopArray);
        }

        return requestBody;
    }

    /**
     * 解析响应
     */
    private LLMResponse parseResponse(String responseBody) {
        try {
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            LLMResponse response = new LLMResponse();
            
            if (jsonResponse.has("id")) {
                response.setId(jsonResponse.get("id").getAsString());
            }
            if (jsonResponse.has("model")) {
                response.setModel(jsonResponse.get("model").getAsString());
            }
            
            // 解析choices
            if (jsonResponse.has("choices")) {
                JsonArray choicesArray = jsonResponse.getAsJsonArray("choices");
                List<LLMResponse.Choice> choices = new java.util.ArrayList<>();
                
                for (JsonElement choiceElement : choicesArray) {
                    JsonObject choiceObj = choiceElement.getAsJsonObject();
                    LLMResponse.Choice choice = new LLMResponse.Choice();
                    
                    if (choiceObj.has("index")) {
                        choice.setIndex(choiceObj.get("index").getAsInt());
                    }
                    if (choiceObj.has("finish_reason")) {
                        choice.setFinishReason(choiceObj.get("finish_reason").getAsString());
                    }
                    if (choiceObj.has("message")) {
                        JsonObject messageObj = choiceObj.getAsJsonObject("message");
                        String role = messageObj.get("role").getAsString();
                        String content = messageObj.get("content").getAsString();
                        
                        LLMMessage.MessageRole messageRole = LLMMessage.MessageRole.ASSISTANT;
                        if ("user".equals(role)) {
                            messageRole = LLMMessage.MessageRole.USER;
                        } else if ("system".equals(role)) {
                            messageRole = LLMMessage.MessageRole.SYSTEM;
                        }
                        
                        LLMMessage message = new LLMMessage(messageRole, content);
                        choice.setMessage(message);
                    }
                    
                    choices.add(choice);
                }
                response.setChoices(choices);
            }
            
            // 解析usage
            if (jsonResponse.has("usage")) {
                JsonObject usageObj = jsonResponse.getAsJsonObject("usage");
                LLMResponse.Usage usage = new LLMResponse.Usage();
                
                if (usageObj.has("prompt_tokens")) {
                    usage.setPromptTokens(usageObj.get("prompt_tokens").getAsInt());
                }
                if (usageObj.has("completion_tokens")) {
                    usage.setCompletionTokens(usageObj.get("completion_tokens").getAsInt());
                }
                if (usageObj.has("total_tokens")) {
                    usage.setTotalTokens(usageObj.get("total_tokens").getAsInt());
                }
                
                response.setUsage(usage);
            }
            
            return response;
        } catch (Exception e) {
            LLMResponse errorResponse = new LLMResponse();
            errorResponse.setError("Failed to parse response: " + e.getMessage());
            return errorResponse;
        }
    }

    /**
     * 处理流式响应
     */
    private void processStreamResponse(Response response, StreamCallback callback) {
        // 简化的流式处理实现
        // 实际实现需要处理SSE格式的数据
        try {
            String responseBody = response.body().string();
            callback.onChunk(responseBody);
            
            LLMResponse finalResponse = parseResponse(responseBody);
            callback.onComplete(finalResponse);
        } catch (IOException e) {
            callback.onError(e);
        }
    }
}
