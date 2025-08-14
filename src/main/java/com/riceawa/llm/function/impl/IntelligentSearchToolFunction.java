package com.riceawa.llm.function.impl;

import com.google.gson.JsonObject;
import com.riceawa.llm.subagent.SubAgentResult;
import com.riceawa.llm.subagent.SubAgentTask;
import com.riceawa.llm.subagent.search.IntelligentSearchTask;
import com.riceawa.llm.subagent.search.SearchStrategy;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * 智能搜索工具函数
 * 继承SubAgentToolFunction，专门处理搜索请求
 */
public class IntelligentSearchToolFunction extends SubAgentToolFunction {
    
    @Override
    public String getName() {
        return "intelligent_search";
    }
    
    @Override
    public String getDescription() {
        return "执行智能搜索任务，支持多轮搜索和深度分析";
    }
    
    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        
        JsonObject properties = new JsonObject();
        
        // 查询参数
        JsonObject query = new JsonObject();
        query.addProperty("type", "string");
        query.addProperty("description", "搜索查询");
        properties.add("query", query);
        
        // 最大搜索轮数
        JsonObject maxRounds = new JsonObject();
        maxRounds.addProperty("type", "integer");
        maxRounds.addProperty("description", "最大搜索轮数");
        maxRounds.addProperty("minimum", 1);
        maxRounds.addProperty("maximum", 5);
        maxRounds.addProperty("default", 3);
        properties.add("maxRounds", maxRounds);
        
        // 搜索策略
        JsonObject strategy = new JsonObject();
        strategy.addProperty("type", "string");
        strategy.addProperty("description", "搜索策略");
        com.google.gson.JsonArray enumValues = new com.google.gson.JsonArray();
        enumValues.add("QUICK");
        enumValues.add("COMPREHENSIVE");
        enumValues.add("DEEP_DIVE");
        strategy.add("enum", enumValues);
        strategy.addProperty("default", "COMPREHENSIVE");
        properties.add("strategy", strategy);
        
        // 超时时间
        JsonObject timeout = new JsonObject();
        timeout.addProperty("type", "integer");
        timeout.addProperty("description", "搜索超时时间（毫秒）");
        timeout.addProperty("minimum", 1000);
        timeout.addProperty("maximum", 300000);
        timeout.addProperty("default", 120000);
        properties.add("timeout", timeout);
        
        schema.add("properties", properties);
        
        // 必需参数
        schema.add("required", new com.google.gson.JsonArray());
        
        return schema;
    }
    
    @Override
    public boolean hasPermission(PlayerEntity player) {
        // 所有玩家都可以使用搜索功能
        return true;
    }
    
    @Override
    public boolean isEnabled() {
        // 默认启用搜索功能
        return true;
    }
    
    @Override
    public String getCategory() {
        return "search";
    }
    
    @Override
    protected String validateArguments(JsonObject arguments) {
        if (arguments == null) {
            return "参数不能为空";
        }
        
        if (!arguments.has("query")) {
            return "缺少必需参数: query";
        }
        
        String query = arguments.get("query").getAsString();
        if (query == null || query.trim().isEmpty()) {
            return "查询参数不能为空";
        }
        
        // 验证maxRounds参数
        if (arguments.has("maxRounds")) {
            int maxRounds = arguments.get("maxRounds").getAsInt();
            if (maxRounds < 1 || maxRounds > 5) {
                return "maxRounds参数必须在1-5之间";
            }
        }
        
        // 验证strategy参数
        if (arguments.has("strategy")) {
            String strategy = arguments.get("strategy").getAsString();
            try {
                SearchStrategy.valueOf(strategy);
            } catch (IllegalArgumentException e) {
                return "无效的搜索策略: " + strategy;
            }
        }
        
        // 验证timeout参数
        if (arguments.has("timeout")) {
            int timeout = arguments.get("timeout").getAsInt();
            if (timeout < 1000 || timeout > 300000) {
                return "timeout参数必须在1000-300000毫秒之间";
            }
        }
        
        return null; // 验证通过
    }
    
    @Override
    protected SubAgentTask<? extends SubAgentResult> createSubAgentTask(
        PlayerEntity player, JsonObject arguments, String taskId) {
        
        String query = arguments.get("query").getAsString();
        int maxRounds = arguments.has("maxRounds") ? 
            arguments.get("maxRounds").getAsInt() : 3;
        String strategyStr = arguments.has("strategy") ? 
            arguments.get("strategy").getAsString() : "COMPREHENSIVE";
        long timeout = arguments.has("timeout") ? 
            arguments.get("timeout").getAsLong() : 120000L;
        
        SearchStrategy strategy = SearchStrategy.valueOf(strategyStr);
        
        // 创建参数映射
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("playerName", player.getName().getString());
        parameters.put("taskId", taskId);
        
        // 创建智能搜索任务
        return new IntelligentSearchTask(
            player.getUuidAsString(), // requesterId
            query,                    // originalQuery
            maxRounds,                // maxSearchRounds
            strategy,                 // strategy
            timeout,                  // timeoutMs
            parameters                // parameters
        );
    }
}