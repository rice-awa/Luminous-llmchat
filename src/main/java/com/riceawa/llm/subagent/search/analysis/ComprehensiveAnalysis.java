package com.riceawa.llm.subagent.search.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 综合分析结果
 * 包含从LLM深度分析中提取的结构化信息
 */
public class ComprehensiveAnalysis {
    
    private final List<String> keyFindings;
    private final List<String> trends;
    private final List<String> deepInsights;
    private final List<String> recommendations;
    private final String reliabilityAssessment;
    private final String confidenceLevel;
    private final String confidenceReason;
    
    /**
     * 构造函数
     */
    public ComprehensiveAnalysis(List<String> keyFindings, List<String> trends, 
                               List<String> deepInsights, List<String> recommendations,
                               String reliabilityAssessment, String confidenceLevel, 
                               String confidenceReason) {
        this.keyFindings = Collections.unmodifiableList(new ArrayList<>(keyFindings));
        this.trends = Collections.unmodifiableList(new ArrayList<>(trends));
        this.deepInsights = Collections.unmodifiableList(new ArrayList<>(deepInsights));
        this.recommendations = Collections.unmodifiableList(new ArrayList<>(recommendations));
        this.reliabilityAssessment = reliabilityAssessment;
        this.confidenceLevel = confidenceLevel;
        this.confidenceReason = confidenceReason;
    }
    
    /**
     * 获取关键发现
     */
    public List<String> getKeyFindings() {
        return keyFindings;
    }
    
    /**
     * 获取趋势
     */
    public List<String> getTrends() {
        return trends;
    }
    
    /**
     * 获取深度洞察
     */
    public List<String> getDeepInsights() {
        return deepInsights;
    }
    
    /**
     * 获取建议
     */
    public List<String> getRecommendations() {
        return recommendations;
    }
    
    /**
     * 获取可靠性评估
     */
    public String getReliabilityAssessment() {
        return reliabilityAssessment;
    }
    
    /**
     * 获取置信度
     */
    public String getConfidenceLevel() {
        return confidenceLevel;
    }
    
    /**
     * 获取置信度原因
     */
    public String getConfidenceReason() {
        return confidenceReason;
    }
    
    /**
     * 构建器类
     */
    public static class Builder {
        private final List<String> keyFindings = new ArrayList<>();
        private final List<String> trends = new ArrayList<>();
        private final List<String> deepInsights = new ArrayList<>();
        private final List<String> recommendations = new ArrayList<>();
        private String reliabilityAssessment;
        private String confidenceLevel;
        private String confidenceReason;
        
        public Builder keyFinding(String finding) {
            if (finding != null && !finding.trim().isEmpty()) {
                this.keyFindings.add(finding.trim());
            }
            return this;
        }
        
        public Builder trend(String trend) {
            if (trend != null && !trend.trim().isEmpty()) {
                this.trends.add(trend.trim());
            }
            return this;
        }
        
        public Builder deepInsight(String insight) {
            if (insight != null && !insight.trim().isEmpty()) {
                this.deepInsights.add(insight.trim());
            }
            return this;
        }
        
        public Builder recommendation(String recommendation) {
            if (recommendation != null && !recommendation.trim().isEmpty()) {
                this.recommendations.add(recommendation.trim());
            }
            return this;
        }
        
        public Builder reliabilityAssessment(String assessment) {
            this.reliabilityAssessment = assessment;
            return this;
        }
        
        public Builder confidenceLevel(String level) {
            this.confidenceLevel = level;
            return this;
        }
        
        public Builder confidenceReason(String reason) {
            this.confidenceReason = reason;
            return this;
        }
        
        public ComprehensiveAnalysis build() {
            return new ComprehensiveAnalysis(
                keyFindings,
                trends,
                deepInsights,
                recommendations,
                reliabilityAssessment,
                confidenceLevel != null ? confidenceLevel : "中",
                confidenceReason
            );
        }
    }
    
    /**
     * 创建构建器
     */
    public static Builder builder() {
        return new Builder();
    }
}