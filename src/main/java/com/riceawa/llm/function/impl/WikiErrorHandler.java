package com.riceawa.llm.function.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.riceawa.llm.function.LLMFunction;
import okhttp3.Response;

/**
 * Wiki API错误处理工具类
 * 统一处理各种Wiki API错误响应格式，包括HTTP错误和JSON错误
 */
public class WikiErrorHandler {
    
    private static final Gson gson = new Gson();
    
    /**
     * 统一处理HTTP响应，包括HTTP状态码错误和JSON格式错误
     * @param response HTTP响应对象
     * @param contextInfo 上下文信息（如页面名称）
     * @return 成功时返回JsonObject，失败时返回null并记录错误到结果中
     */
    public static class HttpResponseResult {
        public final JsonObject jsonResponse;
        public final LLMFunction.FunctionResult errorResult;
        
        private HttpResponseResult(JsonObject jsonResponse, LLMFunction.FunctionResult errorResult) {
            this.jsonResponse = jsonResponse;
            this.errorResult = errorResult;
        }
        
        public static HttpResponseResult success(JsonObject jsonResponse) {
            return new HttpResponseResult(jsonResponse, null);
        }
        
        public static HttpResponseResult error(LLMFunction.FunctionResult errorResult) {
            return new HttpResponseResult(null, errorResult);
        }
        
        public boolean isSuccess() {
            return errorResult == null;
        }
    }
    
    /**
     * 统一处理HTTP响应错误，包括HTTP状态码错误和JSON格式错误
     * @param response HTTP响应对象
     * @param contextInfo 上下文信息（如页面名称）
     * @return HttpResponseResult包含处理结果
     */
    public static HttpResponseResult handleHttpResponse(Response response, String contextInfo) {
        try {
            String responseBody = response.body().string();
            
            if (!response.isSuccessful()) {
                // 尝试解析错误响应体
                if (responseBody != null && !responseBody.isEmpty()) {
                    try {
                        JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                        if (jsonResponse != null && jsonResponse.has("error")) {
                            JsonObject error = jsonResponse.getAsJsonObject("error");
                            String errorMsg = handleError(error, contextInfo);
                            return HttpResponseResult.error(LLMFunction.FunctionResult.error("Wiki API请求失败: " + errorMsg));
                        }
                    } catch (Exception parseEx) {
                        // JSON解析失败，使用HTTP状态码
                    }
                }
                
                // 标准HTTP错误处理
                return HttpResponseResult.error(LLMFunction.FunctionResult.error("Wiki API请求失败: HTTP " + response.code()));
            }
            
            // 解析成功的响应
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            if (jsonResponse == null) {
                return HttpResponseResult.error(LLMFunction.FunctionResult.error("Wiki API返回无效响应"));
            }
            
            if (!jsonResponse.has("success") || !jsonResponse.get("success").getAsBoolean()) {
                if (jsonResponse.has("error")) {
                    JsonObject error = jsonResponse.getAsJsonObject("error");
                    String errorMsg = handleError(error, contextInfo);
                    return HttpResponseResult.error(LLMFunction.FunctionResult.error("Wiki API请求失败: " + errorMsg));
                } else {
                    return HttpResponseResult.error(LLMFunction.FunctionResult.error("Wiki API请求失败: 未知错误"));
                }
            }
            
            // 成功，返回解析后的JSON响应
            return HttpResponseResult.success(jsonResponse);
            
        } catch (Exception e) {
            return HttpResponseResult.error(LLMFunction.FunctionResult.error("处理HTTP响应失败: " + e.getMessage()));
        }
    }
    
    /**
     * 处理Wiki API错误响应，返回格式化的错误消息
     * @param error 错误对象
     * @param contextInfo 上下文信息（如页面名称）
     * @return 格式化的错误消息
     */
    public static String handleError(JsonObject error, String contextInfo) {
        if (error == null) {
            return "未知错误";
        }
        
        String errorCode = error.has("code") ? error.get("code").getAsString() : "UNKNOWN_ERROR";
        String errorMessage = error.has("message") ? error.get("message").getAsString() : "未知错误";
        
        // 处理PAGE_NOT_FOUND错误的suggestions
        if ("PAGE_NOT_FOUND".equals(errorCode) && error.has("details")) {
            String suggestionsMsg = handlePageNotFoundSuggestions(error.getAsJsonObject("details"), contextInfo);
            if (suggestionsMsg != null) {
                return suggestionsMsg;
            }
        }
        
        // 处理RATE_LIMIT_EXCEEDED错误
        if ("RATE_LIMIT_EXCEEDED".equals(errorCode) && error.has("details")) {
            String rateLimitMsg = handleRateLimitError(error.getAsJsonObject("details"), errorMessage);
            if (rateLimitMsg != null) {
                return rateLimitMsg;
            }
        }
        
        // 默认错误格式
        return formatErrorMessage(errorCode, errorMessage);
    }
    
    /**
     * 处理页面不存在错误的建议
     */
    private static String handlePageNotFoundSuggestions(JsonObject details, String contextInfo) {
        if (details == null || !details.has("suggestions")) {
            return null;
        }
        
        JsonArray suggestions = details.getAsJsonArray("suggestions");
        if (suggestions.size() == 0) {
            return null;
        }
        
        StringBuilder suggestMsg = new StringBuilder();
        if (contextInfo != null && !contextInfo.isEmpty()) {
            suggestMsg.append("页面 \"").append(contextInfo).append("\" 不存在");
        } else {
            suggestMsg.append("页面不存在");
        }
        suggestMsg.append("\n\n建议的相似页面：\n");
        
        int maxSuggestions = Math.min(5, suggestions.size());
        for (int i = 0; i < maxSuggestions; i++) {
            JsonObject suggestion = suggestions.get(i).getAsJsonObject();
            if (suggestion.has("title")) {
                suggestMsg.append("• ").append(suggestion.get("title").getAsString()).append("\n");
            }
        }
        
        return suggestMsg.toString();
    }
    
    /**
     * 处理频率限制错误
     */
    private static String handleRateLimitError(JsonObject details, String baseMessage) {
        if (details == null) {
            return null;
        }
        
        StringBuilder rateLimitMsg = new StringBuilder();
        rateLimitMsg.append(baseMessage);
        
        if (details.has("windowMs") && details.has("maxRequests")) {
            int windowMs = details.get("windowMs").getAsInt();
            int maxRequests = details.get("maxRequests").getAsInt();
            rateLimitMsg.append("\n请求频率限制: ").append(maxRequests)
                       .append("次/").append(windowMs / 1000).append("秒");
        }
        
        if (details.has("retryAfter")) {
            int retryAfter = details.get("retryAfter").getAsInt();
            rateLimitMsg.append("，请").append(retryAfter).append("秒后重试");
        }
        
        return rateLimitMsg.toString();
    }
    
    /**
     * 格式化标准错误消息
     */
    private static String formatErrorMessage(String errorCode, String errorMessage) {
        if ("UNKNOWN_ERROR".equals(errorCode)) {
            return errorMessage;
        }
        return errorMessage + " (" + errorCode + ")";
    }
    
    /**
     * 处理批量请求中单个页面的错误
     * @param error 错误对象
     * @param pageName 页面名称
     * @param maxSuggestions 最大建议数量
     * @return 格式化的错误消息
     */
    public static String handleBatchPageError(JsonObject error, String pageName, int maxSuggestions) {
        if (error == null) {
            return "未知错误";
        }
        
        String errorCode = error.has("code") ? error.get("code").getAsString() : "UNKNOWN_ERROR";
        String errorMessage = error.has("message") ? error.get("message").getAsString() : "未知错误";
        
        // 处理PAGE_NOT_FOUND错误的suggestions（批量请求中显示更少建议）
        if ("PAGE_NOT_FOUND".equals(errorCode) && error.has("details")) {
            JsonObject details = error.getAsJsonObject("details");
            if (details != null && details.has("suggestions")) {
                JsonArray suggestions = details.getAsJsonArray("suggestions");
                if (suggestions.size() > 0) {
                    StringBuilder suggestMsg = new StringBuilder();
                    suggestMsg.append("页面不存在\n建议的相似页面：\n");
                    
                    int maxSuggestionsToShow = Math.min(maxSuggestions, suggestions.size());
                    for (int i = 0; i < maxSuggestionsToShow; i++) {
                        JsonObject suggestion = suggestions.get(i).getAsJsonObject();
                        if (suggestion.has("title")) {
                            suggestMsg.append("  • ").append(suggestion.get("title").getAsString()).append("\n");
                        }
                    }
                    
                    return suggestMsg.toString();
                }
            }
        }
        
        return formatErrorMessage(errorCode, errorMessage);
    }
}