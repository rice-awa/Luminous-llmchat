package com.riceawa.llm.function.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Wiki API错误处理工具类
 * 统一处理各种Wiki API错误响应格式
 */
public class WikiErrorHandler {
    
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