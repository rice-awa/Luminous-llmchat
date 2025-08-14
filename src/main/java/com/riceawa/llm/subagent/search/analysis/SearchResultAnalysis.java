package com.riceawa.llm.subagent.search.analysis;

import com.riceawa.llm.subagent.search.SearchInsights;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 搜索结果分析结果
 * 包含完整分析过程的所有结果
 */
public class SearchResultAnalysis {
    
    private final boolean success;
    private final String error;
    private final long analysisTimeMs;
    private final ExtractedInformation extractedInformation;
    private final ComprehensiveAnalysis comprehensiveAnalysis;
    private final SearchInsights insights;
    private final String finalReport;
    private final Map<String, Object> metadata;
    
    /**
     * 构造函数
     */
    public SearchResultAnalysis(boolean success, String error, long analysisTimeMs,
                               ExtractedInformation extractedInformation,
                               ComprehensiveAnalysis comprehensiveAnalysis,
                               SearchInsights insights, String finalReport,
                               Map<String, Object> metadata) {
        this.success = success;
        this.error = error;
        this.analysisTimeMs = analysisTimeMs;
        this.extractedInformation = extractedInformation;
        this.comprehensiveAnalysis = comprehensiveAnalysis;
        this.insights = insights;
        this.finalReport = finalReport;
        this.metadata = metadata != null ? metadata : Map.of();
    }
    
    /**
     * 创建成功的分析结果
     */
    public static SearchResultAnalysis success(ExtractedInformation extractedInformation,
                                              ComprehensiveAnalysis comprehensiveAnalysis,
                                              SearchInsights insights,
                                              String finalReport,
                                              long analysisTimeMs,
                                              Map<String, Object> metadata) {
        return new SearchResultAnalysis(true, null, analysisTimeMs, extractedInformation,
                                       comprehensiveAnalysis, insights, finalReport, metadata);
    }
    
    /**
     * 创建失败的分析结果
     */
    public static SearchResultAnalysis failure(String error, long analysisTimeMs,
                                              Map<String, Object> metadata) {
        return new SearchResultAnalysis(false, error, analysisTimeMs, null,
                                       null, null, null, metadata);
    }
    
    /**
     * 检查是否成功
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * 获取错误信息
     */
    public String getError() {
        return error;
    }
    
    /**
     * 获取分析时间
     */
    public long getAnalysisTimeMs() {
        return analysisTimeMs;
    }
    
    /**
     * 获取提取的信息
     */
    public ExtractedInformation getExtractedInformation() {
        return extractedInformation;
    }
    
    /**
     * 获取综合分析结果
     */
    public ComprehensiveAnalysis getComprehensiveAnalysis() {
        return comprehensiveAnalysis;
    }
    
    /**
     * 获取搜索洞察
     */
    public SearchInsights getInsights() {
        return insights;
    }
    
    /**
     * 获取最终报告
     */
    public String getFinalReport() {
        return finalReport;
    }
    
    /**
     * 获取元数据
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    /**
     * 获取分析摘要
     */
    public String getSummary() {
        if (!success) {
            return String.format("搜索结果分析失败: %s", error);
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("搜索结果分析完成 (").append(analysisTimeMs).append("ms)\n");
        
        if (insights != null) {
            summary.append("关键发现: ").append(insights.getKeyFindings().size()).append("项\n");
            summary.append("趋势分析: ").append(insights.getTrends().size()).append("项\n");
            summary.append("建议: ").append(insights.getRecommendations().size()).append("项\n");
            summary.append("置信度: ").append(insights.getConfidenceLevel());
        }
        
        return summary.toString();
    }
    
    /**
     * 获取详细的分析结果数据
     */
    public Map<String, Object> getDetailedData() {
        if (!success) {
            return Map.of(
                "success", false,
                "error", error,
                "analysisTimeMs", analysisTimeMs
            );
        }
        
        return Map.of(
            "success", true,
            "analysisTimeMs", analysisTimeMs,
            "extractedInformation", Map.of(
                "totalResults", extractedInformation.getSearchResults().size(),
                "uniqueKeywords", extractedInformation.getKeywords().size(),
                "uniqueSources", extractedInformation.getSources().size(),
                "statistics", Map.of(
                    "totalRounds", extractedInformation.getSearchStatistics().getTotalRounds(),
                    "successfulRounds", extractedInformation.getSearchStatistics().getSuccessfulRounds(),
                    "totalResults", extractedInformation.getSearchStatistics().getTotalResults(),
                    "informationRichness", extractedInformation.getSearchStatistics().getInformationRichness()
                )
            ),
            "insights", insights != null ? Map.of(
                "keyFindings", insights.getKeyFindings(),
                "trends", insights.getTrends(),
                "recommendations", insights.getRecommendations(),
                "confidenceLevel", insights.getConfidenceLevel(),
                "analysisMethod", insights.getAnalysisMethod(),
                "analysisTimeMs", insights.getAnalysisTimeMs()
            ) : null,
            "comprehensiveAnalysis", comprehensiveAnalysis != null ? Map.of(
                "keyFindings", comprehensiveAnalysis.getKeyFindings(),
                "trends", comprehensiveAnalysis.getTrends(),
                "deepInsights", comprehensiveAnalysis.getDeepInsights(),
                "recommendations", comprehensiveAnalysis.getRecommendations(),
                "reliabilityAssessment", comprehensiveAnalysis.getReliabilityAssessment(),
                "confidenceLevel", comprehensiveAnalysis.getConfidenceLevel()
            ) : null,
            "metadata", metadata
        );
    }
    
    /**
     * 检查是否有深度分析
     */
    public boolean hasDeepAnalysis() {
        return comprehensiveAnalysis != null && !comprehensiveAnalysis.getDeepInsights().isEmpty();
    }
    
    /**
     * 检查是否有洞察
     */
    public boolean hasInsights() {
        return insights != null && (insights.hasKeyFindings() || insights.hasTrends() || insights.hasRecommendations());
    }
    
    /**
     * 获取关键词列表
     */
    public List<String> getKeywords() {
        if (extractedInformation != null) {
            return new ArrayList<>(extractedInformation.getKeywords());
        }
        return Collections.emptyList();
    }
    
    /**
     * 获取信息来源列表
     */
    public List<String> getSources() {
        if (extractedInformation != null) {
            return new ArrayList<>(extractedInformation.getSources());
        }
        return Collections.emptyList();
    }
}