package com.riceawa.llm.subagent.search;

import com.riceawa.llm.subagent.SubAgentResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能搜索结果
 * 继承SubAgentResult，专门用于智能搜索功能的结果
 */
public class IntelligentSearchResult extends SubAgentResult {
    
    private final String finalAnalysis;
    private final String structuredSummary;
    private final List<SearchRound> searchRounds;
    private final int totalSearchRounds;
    private final SearchInsights insights;
    private final String originalQuery;
    private final SearchStrategy strategy;
    
    /**
     * 构造函数
     * 
     * @param success 是否成功
     * @param error 错误信息
     * @param totalProcessingTimeMs 总处理时间
     * @param metadata 元数据
     * @param finalAnalysis 最终分析
     * @param structuredSummary 结构化摘要
     * @param searchRounds 搜索轮次列表
     * @param insights 搜索洞察
     * @param originalQuery 原始查询
     * @param strategy 搜索策略
     */
    public IntelligentSearchResult(boolean success, String error, long totalProcessingTimeMs, 
                                 Map<String, Object> metadata, String finalAnalysis, 
                                 String structuredSummary, List<SearchRound> searchRounds, 
                                 SearchInsights insights, String originalQuery, SearchStrategy strategy) {
        super(success, error, totalProcessingTimeMs, metadata);
        
        this.finalAnalysis = finalAnalysis;
        this.structuredSummary = structuredSummary;
        this.searchRounds = searchRounds != null ? new ArrayList<>(searchRounds) : new ArrayList<>();
        this.totalSearchRounds = this.searchRounds.size();
        this.insights = insights;
        this.originalQuery = originalQuery;
        this.strategy = strategy;
    }
    
    /**
     * 创建成功的搜索结果
     * 
     * @param finalAnalysis 最终分析
     * @param structuredSummary 结构化摘要
     * @param searchRounds 搜索轮次列表
     * @param insights 搜索洞察
     * @param originalQuery 原始查询
     * @param strategy 搜索策略
     * @param totalProcessingTimeMs 总处理时间
     * @param metadata 元数据
     * @return 成功的搜索结果
     */
    public static IntelligentSearchResult success(String finalAnalysis, String structuredSummary, 
                                                List<SearchRound> searchRounds, SearchInsights insights, 
                                                String originalQuery, SearchStrategy strategy, 
                                                long totalProcessingTimeMs, Map<String, Object> metadata) {
        return new IntelligentSearchResult(true, null, totalProcessingTimeMs, metadata, 
                                         finalAnalysis, structuredSummary, searchRounds, 
                                         insights, originalQuery, strategy);
    }
    
    /**
     * 创建失败的搜索结果
     * 
     * @param error 错误信息
     * @param originalQuery 原始查询
     * @param strategy 搜索策略
     * @param searchRounds 已完成的搜索轮次
     * @param totalProcessingTimeMs 总处理时间
     * @param metadata 元数据
     * @return 失败的搜索结果
     */
    public static IntelligentSearchResult failure(String error, String originalQuery, 
                                                SearchStrategy strategy, List<SearchRound> searchRounds, 
                                                long totalProcessingTimeMs, Map<String, Object> metadata) {
        return new IntelligentSearchResult(false, error, totalProcessingTimeMs, metadata, 
                                         null, null, searchRounds, null, originalQuery, strategy);
    }
    
    @Override
    public String getSummary() {
        if (!isSuccess()) {
            return String.format("搜索失败: %s (查询: \"%s\")", getError(), originalQuery);
        }
        
        if (structuredSummary != null && !structuredSummary.trim().isEmpty()) {
            return structuredSummary;
        }
        
        return String.format("完成%d轮搜索，查询: \"%s\"", totalSearchRounds, originalQuery);
    }
    
    @Override
    public Map<String, Object> getDetailedData() {
        Map<String, Object> data = new HashMap<>();
        
        data.put("originalQuery", originalQuery);
        data.put("strategy", strategy != null ? strategy.name() : "UNKNOWN");
        data.put("totalSearchRounds", totalSearchRounds);
        data.put("success", isSuccess());
        
        if (isSuccess()) {
            data.put("finalAnalysis", finalAnalysis);
            data.put("structuredSummary", structuredSummary);
            
            if (insights != null) {
                data.put("insights", Map.of(
                    "keyFindings", insights.getKeyFindings(),
                    "trends", insights.getTrends(),
                    "recommendations", insights.getRecommendations(),
                    "confidenceLevel", insights.getConfidenceLevel(),
                    "sources", insights.getSources()
                ));
            }
        } else {
            data.put("error", getError());
        }
        
        // 搜索轮次统计
        if (!searchRounds.isEmpty()) {
            int successfulRounds = (int) searchRounds.stream().filter(SearchRound::isSuccessful).count();
            long totalSearchTime = searchRounds.stream().mapToLong(SearchRound::getProcessingTimeMs).sum();
            
            data.put("searchStatistics", Map.of(
                "totalRounds", totalSearchRounds,
                "successfulRounds", successfulRounds,
                "failedRounds", totalSearchRounds - successfulRounds,
                "totalSearchTime", totalSearchTime,
                "averageRoundTime", totalSearchRounds > 0 ? totalSearchTime / totalSearchRounds : 0
            ));
        }
        
        return data;
    }
    
    /**
     * 获取完整的搜索报告
     * 
     * @return 完整的搜索报告
     */
    public String getFullReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("=== 智能搜索报告 ===\n");
        report.append("原始查询: ").append(originalQuery).append("\n");
        report.append("搜索策略: ").append(strategy != null ? strategy.getDisplayName() : "未知").append("\n");
        report.append("总处理时间: ").append(getTotalProcessingTimeMs()).append("ms\n");
        report.append("搜索状态: ").append(isSuccess() ? "成功" : "失败").append("\n\n");
        
        if (!isSuccess()) {
            report.append("错误信息: ").append(getError()).append("\n");
            return report.toString();
        }
        
        // 搜索轮次详情
        if (!searchRounds.isEmpty()) {
            report.append("=== 搜索轮次详情 ===\n");
            for (SearchRound round : searchRounds) {
                report.append(round.getSummary()).append("\n");
            }
            report.append("\n");
        }
        
        // 结构化摘要
        if (structuredSummary != null && !structuredSummary.trim().isEmpty()) {
            report.append("=== 结构化摘要 ===\n");
            report.append(structuredSummary).append("\n\n");
        }
        
        // 搜索洞察
        if (insights != null) {
            report.append(insights.getDetailedReport()).append("\n");
        }
        
        // 最终分析
        if (finalAnalysis != null && !finalAnalysis.trim().isEmpty()) {
            report.append("=== 最终分析 ===\n");
            report.append(finalAnalysis).append("\n");
        }
        
        return report.toString();
    }
    
    // Getters
    public String getFinalAnalysis() {
        return finalAnalysis;
    }
    
    public String getStructuredSummary() {
        return structuredSummary;
    }
    
    public List<SearchRound> getSearchRounds() {
        return new ArrayList<>(searchRounds);
    }
    
    public int getTotalSearchRounds() {
        return totalSearchRounds;
    }
    
    public SearchInsights getInsights() {
        return insights;
    }
    
    public String getOriginalQuery() {
        return originalQuery;
    }
    
    public SearchStrategy getStrategy() {
        return strategy;
    }
    
    /**
     * 检查是否有搜索洞察
     * 
     * @return 是否有搜索洞察
     */
    public boolean hasInsights() {
        return insights != null && insights.hasKeyFindings();
    }
    
    /**
     * 获取成功的搜索轮数
     * 
     * @return 成功的搜索轮数
     */
    public int getSuccessfulRounds() {
        return (int) searchRounds.stream().filter(SearchRound::isSuccessful).count();
    }
    
    /**
     * 获取失败的搜索轮数
     * 
     * @return 失败的搜索轮数
     */
    public int getFailedRounds() {
        return totalSearchRounds - getSuccessfulRounds();
    }
    
    /**
     * 获取总搜索时间
     * 
     * @return 总搜索时间（毫秒）
     */
    public long getTotalSearchTime() {
        return searchRounds.stream().mapToLong(SearchRound::getProcessingTimeMs).sum();
    }
    
    /**
     * 获取平均每轮搜索时间
     * 
     * @return 平均每轮搜索时间（毫秒）
     */
    public long getAverageRoundTime() {
        return totalSearchRounds > 0 ? getTotalSearchTime() / totalSearchRounds : 0;
    }
}