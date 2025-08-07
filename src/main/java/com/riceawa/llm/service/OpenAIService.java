package com.riceawa.llm.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.riceawa.llm.core.*;
import com.riceawa.llm.config.ConcurrencySettings;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.logging.LLMLogUtils;
import com.riceawa.llm.logging.LLMRequestLogEntry;
import com.riceawa.llm.logging.LLMResponseLogEntry;
import okhttp3.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

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
        this.httpClient = createOptimizedHttpClient();
        this.gson = new Gson();
    }

    /**
     * 创建优化的HTTP客户端
     */
    private OkHttpClient createOptimizedHttpClient() {
        ConcurrencySettings settings = LLMChatConfig.getInstance().getConcurrencySettings();

        // 创建连接池
        ConnectionPool connectionPool = new ConnectionPool(
            settings.getMaxIdleConnections(),
            settings.getKeepAliveDurationMs(),
            TimeUnit.MILLISECONDS
        );

        return new OkHttpClient.Builder()
                .connectTimeout(settings.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(settings.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(settings.getWriteTimeoutMs(), TimeUnit.MILLISECONDS)
                .connectionPool(connectionPool)
                .retryOnConnectionFailure(settings.isEnableRetry())
                .build();
    }

    @Override
    public CompletableFuture<LLMResponse> chat(List<LLMMessage> messages, LLMConfig config) {
        return chat(messages, config, null);
    }

    @Override
    public CompletableFuture<LLMResponse> chat(List<LLMMessage> messages, LLMConfig config, LLMContext context) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        return ConcurrencyManager.getInstance().submitRequest(() -> {
            ConcurrencySettings settings = LLMChatConfig.getInstance().getConcurrencySettings();

            try {
                return executeRequestWithRetry(messages, config, settings, requestId, context);
            } catch (Exception e) {
                LLMResponse errorResponse = new LLMResponse();
                errorResponse.setError("Request failed: " + e.getMessage());
                return errorResponse;
            }
        }, requestId);
    }

    /**
     * 执行带重试的请求
     */
    private LLMResponse executeRequestWithRetry(List<LLMMessage> messages, LLMConfig config,
                                              ConcurrencySettings settings, String requestId) throws Exception {
        return executeRequestWithRetry(messages, config, settings, requestId, null);
    }

    /**
     * 执行带重试的请求（带上下文信息）
     */
    private LLMResponse executeRequestWithRetry(List<LLMMessage> messages, LLMConfig config,
                                              ConcurrencySettings settings, String requestId, LLMContext context) throws Exception {
        Exception lastException = null;
        int maxAttempts = settings.isEnableRetry() ? settings.getMaxRetryAttempts() + 1 : 1;

        String playerName = context != null ? context.getPlayerName() : null;
        String playerUuid = context != null ? context.getPlayerUuid() : null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return executeRequest(messages, config, requestId, playerName, playerUuid);
            } catch (Exception e) {
                lastException = e;

                if (attempt < maxAttempts && shouldRetry(e)) {
                    long delay = (long) (settings.getRetryDelayMs() * Math.pow(settings.getRetryBackoffMultiplier(), attempt - 1));
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Request interrupted", ie);
                    }
                } else {
                    break;
                }
            }
        }

        throw lastException;
    }

    /**
     * 执行单次请求
     */
    private LLMResponse executeRequest(List<LLMMessage> messages, LLMConfig config, String requestId) throws IOException {
        return executeRequest(messages, config, requestId, null, null);
    }

    /**
     * 执行单次请求（带上下文信息用于日志记录）
     */
    private LLMResponse executeRequest(List<LLMMessage> messages, LLMConfig config, String requestId,
                                     String playerName, String playerUuid) throws IOException {
        long startTime = System.currentTimeMillis();
        JsonObject requestBody = buildRequestBody(messages, config);
        String requestUrl = baseUrl + "/chat/completions";

        // 构建请求头
        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("Authorization", "Bearer " + apiKey);
        requestHeaders.put("Content-Type", "application/json");
        requestHeaders.put("X-Request-ID", requestId);

        // 记录请求日志
        LLMRequestLogEntry requestLog = LLMLogUtils.createRequestLogBuilder(requestId)
                .serviceName(getServiceName())
                .playerName(playerName)
                .playerUuid(playerUuid)
                .messages(messages)
                .config(config)
                .rawRequestJson(requestBody.toString())
                .requestUrl(requestUrl)
                .requestHeaders(LLMLogUtils.sanitizeHeaders(requestHeaders))
                .estimatedTokens(LLMLogUtils.estimateTokens(messages))
                .build();

        LLMLogUtils.logRequest(requestLog);

        Request request = new Request.Builder()
                .url(requestUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("X-Request-ID", requestId)
                .post(RequestBody.create(requestBody.toString(), MediaType.get("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            long endTime = System.currentTimeMillis();
            long responseTime = endTime - startTime;
            String responseBody = response.body().string();
            String responseId = LLMLogUtils.generateResponseId();

            // 构建响应头
            Map<String, String> responseHeaders = new HashMap<>();
            for (String headerName : response.headers().names()) {
                responseHeaders.put(headerName, response.header(headerName));
            }

            if (!response.isSuccessful()) {
                // 记录错误响应日志
                LLMResponseLogEntry responseLog = LLMLogUtils.createResponseLogBuilder(responseId, requestId)
                        .httpStatusCode(response.code())
                        .success(false)
                        .errorMessage("HTTP " + response.code() + ": " + responseBody)
                        .rawResponseJson(responseBody)
                        .responseHeaders(responseHeaders)
                        .responseTimeMs(responseTime)
                        .build();

                LLMLogUtils.logResponse(responseLog);

                LLMResponse errorResponse = new LLMResponse();
                errorResponse.setError("HTTP " + response.code() + ": " + responseBody);
                return errorResponse;
            }

            LLMResponse llmResponse = parseResponse(responseBody);

            // 记录成功响应日志
            LLMResponseLogEntry responseLog = LLMLogUtils.createResponseLogBuilder(responseId, requestId)
                    .httpStatusCode(response.code())
                    .success(llmResponse.isSuccess())
                    .llmResponse(llmResponse)
                    .rawResponseJson(responseBody)
                    .responseHeaders(responseHeaders)
                    .responseTimeMs(responseTime)
                    .build();

            LLMLogUtils.logResponse(responseLog);

            // 记录token使用情况
            if (llmResponse.isSuccess() && llmResponse.getUsage() != null) {
                LLMResponse.Usage usage = llmResponse.getUsage();
                ConcurrencyManager.getInstance().recordTokenUsage(
                    usage.getPromptTokens(),
                    usage.getCompletionTokens(),
                    usage.getTotalTokens()
                );
            }

            return llmResponse;
        }
    }

    /**
     * 判断是否应该重试
     */
    private boolean shouldRetry(Exception e) {
        if (e instanceof IOException) {
            return true; // 网络错误通常可以重试
        }

        String message = e.getMessage();
        if (message != null) {
            // 检查是否是可重试的HTTP错误
            return message.contains("HTTP 429") || // 速率限制
                   message.contains("HTTP 502") || // 网关错误
                   message.contains("HTTP 503") || // 服务不可用
                   message.contains("HTTP 504");   // 网关超时
        }

        return false;
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
    public CompletableFuture<Boolean> healthCheck() {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(false);
        }

        // 创建最小的测试请求
        LLMMessage testMessage = new LLMMessage(LLMMessage.MessageRole.USER, "test");
        LLMConfig testConfig = new LLMConfig();
        testConfig.setModel("gpt-3.5-turbo"); // 使用最便宜的模型
        testConfig.setMaxTokens(1); // 最小token数
        testConfig.setTemperature(0.1);

        return chat(List.of(testMessage), testConfig)
            .thenApply(response -> response != null && response.isSuccess())
            .exceptionally(throwable -> false);
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

            // 处理不同类型的消息
            if (message.getRole() == LLMMessage.MessageRole.TOOL) {
                // Tool消息格式
                messageObj.addProperty("content", message.getContent());
                if (message.getName() != null) {
                    messageObj.addProperty("name", message.getName());
                }
                if (message.getToolCallId() != null) {
                    messageObj.addProperty("tool_call_id", message.getToolCallId());
                }
            } else if (message.getRole() == LLMMessage.MessageRole.ASSISTANT &&
                      message.getMetadata() != null &&
                      message.getMetadata().getFunctionCall() != null) {
                // Assistant消息包含tool_calls
                if (message.getContent() != null) {
                    messageObj.addProperty("content", message.getContent());
                }

                LLMMessage.FunctionCall functionCall = message.getMetadata().getFunctionCall();
                JsonArray toolCallsArray = new JsonArray();
                JsonObject toolCallObj = new JsonObject();
                toolCallObj.addProperty("id", functionCall.getToolCallId());
                toolCallObj.addProperty("type", "function");

                JsonObject functionObj = new JsonObject();
                functionObj.addProperty("name", functionCall.getName());
                functionObj.addProperty("arguments", functionCall.getArguments());
                toolCallObj.add("function", functionObj);

                toolCallsArray.add(toolCallObj);
                messageObj.add("tool_calls", toolCallsArray);
            } else {
                // 普通消息
                messageObj.addProperty("content", message.getContent());
            }

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

        // 添加工具定义（新的API格式）
        if (config.getTools() != null && !config.getTools().isEmpty()) {
            JsonArray toolsArray = new JsonArray();
            for (LLMConfig.ToolDefinition tool : config.getTools()) {
                JsonObject toolObj = new JsonObject();
                toolObj.addProperty("type", tool.getType());

                JsonObject functionObj = new JsonObject();
                functionObj.addProperty("name", tool.getFunction().getName());
                functionObj.addProperty("description", tool.getFunction().getDescription());
                functionObj.add("parameters", gson.toJsonTree(tool.getFunction().getParameters()));

                toolObj.add("function", functionObj);
                toolsArray.add(toolObj);
            }
            
            // 检查是否启用联网搜索且当前模型是Gemini模型
            String model = config.getModel() != null ? config.getModel() : "gpt-3.5-turbo";
            boolean isGeminiModel = model.toLowerCase().startsWith("gemini");
            boolean webSearchEnabled = LLMChatConfig.getInstance().isEnableWebSearch();
            
            if (webSearchEnabled && isGeminiModel) {
                // 添加 googleSearch 工具
                JsonObject googleSearchTool = new JsonObject();
                googleSearchTool.addProperty("type", "function");
                
                JsonObject googleSearchFunction = new JsonObject();
                googleSearchFunction.addProperty("name", "googleSearch");
                googleSearchTool.add("function", googleSearchFunction);
                
                toolsArray.add(googleSearchTool);
            }
            
            requestBody.add("tools", toolsArray);

            if (config.getToolChoice() != null) {
                requestBody.addProperty("tool_choice", config.getToolChoice());
            }
        }
        // 如果没有其他工具但启用了联网搜索且是Gemini模型，单独添加googleSearch工具
        else {
            String model = config.getModel() != null ? config.getModel() : "gpt-3.5-turbo";
            boolean isGeminiModel = model.toLowerCase().startsWith("gemini");
            boolean webSearchEnabled = LLMChatConfig.getInstance().isEnableWebSearch();
            
            if (webSearchEnabled && isGeminiModel) {
                JsonArray toolsArray = new JsonArray();
                JsonObject googleSearchTool = new JsonObject();
                googleSearchTool.addProperty("type", "function");
                
                JsonObject googleSearchFunction = new JsonObject();
                googleSearchFunction.addProperty("name", "googleSearch");
                googleSearchTool.add("function", googleSearchFunction);
                
                toolsArray.add(googleSearchTool);
                requestBody.add("tools", toolsArray);
            }
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
                        String content = messageObj.has("content") && !messageObj.get("content").isJsonNull() ?
                            messageObj.get("content").getAsString() : null;

                        LLMMessage.MessageRole messageRole = LLMMessage.MessageRole.ASSISTANT;
                        if ("user".equals(role)) {
                            messageRole = LLMMessage.MessageRole.USER;
                        } else if ("system".equals(role)) {
                            messageRole = LLMMessage.MessageRole.SYSTEM;
                        }

                        LLMMessage message = new LLMMessage(messageRole, content);

                        // 处理新的tool_calls格式
                        if (messageObj.has("tool_calls")) {
                            JsonArray toolCallsArray = messageObj.getAsJsonArray("tool_calls");
                            if (toolCallsArray.size() > 0) {
                                // 目前只处理第一个tool call，后续可以扩展支持多个
                                JsonObject toolCallObj = toolCallsArray.get(0).getAsJsonObject();
                                if ("function".equals(toolCallObj.get("type").getAsString())) {
                                    JsonObject functionObj = toolCallObj.getAsJsonObject("function");
                                    String functionName = functionObj.get("name").getAsString();
                                    String functionArgs = functionObj.get("arguments").getAsString();
                                    String toolCallId = toolCallObj.get("id").getAsString();

                                    LLMMessage.FunctionCall functionCall = new LLMMessage.FunctionCall(functionName, functionArgs);
                                    functionCall.setToolCallId(toolCallId); // 添加tool_call_id支持
                                    LLMMessage.MessageMetadata metadata = new LLMMessage.MessageMetadata();
                                    metadata.setFunctionCall(functionCall);
                                    message.setMetadata(metadata);
                                }
                            }
                        }
                        // 保持对旧格式的兼容性
                        else if (messageObj.has("function_call")) {
                            JsonObject functionCallObj = messageObj.getAsJsonObject("function_call");
                            String functionName = functionCallObj.get("name").getAsString();
                            String functionArgs = functionCallObj.get("arguments").getAsString();

                            LLMMessage.FunctionCall functionCall = new LLMMessage.FunctionCall(functionName, functionArgs);
                            LLMMessage.MessageMetadata metadata = new LLMMessage.MessageMetadata();
                            metadata.setFunctionCall(functionCall);
                            message.setMetadata(metadata);
                        }

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
