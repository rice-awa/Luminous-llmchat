package com.riceawa.llm.subagent.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 搜索洞察信息
 * 包含从搜索结果中提取的关键洞察和分析
 */
public class SearchInsights {
    
    private final List<String> keyFindings;
    private final List<String> trends;
    private final List<String> recommendations;
    private final String confidenceLevel;
    private final List<String> sources;
    private final String analysisMethod;
    private final long analysisTimeMs;
    
    /**
     * 构造函数
     * 
     * @param keyFindings 关键发现
     * @param trends 趋势分析
     * @param recommendations 建议
     * @param confidenceLevel 置信度
     * @param sources 信息来源
     * @param analysisMethod 分析方法
     * @param analysisTimeMs 分析时间
     */
    public SearchInsights(List<String> keyFindings, List<String> trends, 
                         List<String> recommendations, String confidenceLevel, 
                         List<String> sources, String analysisMethod, long analysisTimeMs) {
        this.keyFindings = keyFindings != null ? 
            Collections.unmodifiableList(new ArrayList<>(keyFindings)) : 
            Collections.emptyList();
        this.trends = trends != null ? 
            Collections.unmodifiableList(new ArrayList<>(trends)) : 
            Collections.emptyList();
        this.recommendations = recommendations != null ? 
            Collections.unmodifiableList(new ArrayList<>(recommendations)) : 
            Collections.emptyList();
        this.confidenceLevel = confidenceLevel != null ? confidenceLevel : "未知";
        this.sources = sources != null ? 
            Collections.unmodifiableList(new ArrayList<>(sources)) : 
            Collections.emptyList();
        this.analysisMethod = analysisMethod != null ? analysisMethod : "标准分析";
        this.analysisTimeMs = analysisTimeMs;
    }
    
    /**
     * 获取关键发现
     * 
     * @return 关键发现列表
     */
    public List<String> getKeyFindings() {
        return keyFindings;
    }
    
    /**
     * 获取趋势分析
     * 
     * @return 趋势分析列表
     */
    public List<String> getTrends() {
        return trends;
    }
    
    /**
     * 获取建议
     * 
     * @return 建议列表
     */
    public List<String> getRecommendations() {
        return recommendations;
    }
    
    /**
     * 获取置信度
     * 
     * @return 置信度描述
     */
    public String getConfidenceLevel() {
        return confidenceLevel;
    }
    
    /**
     * 获取信息来源
     * 
     * @return 信息来源列表
     */
    public List<String> getSources() {
        return sources;
    }
    
    /**
     * 获取分析方法
     * 
     * @return 分析方法描述
     */
    public String getAnalysisMethod() {
        return analysisMethod;
    }
    
    /**
     * 获取分析时间
     * 
     * @return 分析时间（毫秒）
     */
    public long getAnalysisTimeMs() {
        return analysisTimeMs;
    }
    
    /**
     * 检查是否有关键发现
     * 
     * @return 是否有关键发现
     */
    public boolean hasKeyFindings() {
        return !keyFindings.isEmpty();
    }
    
    /**
     * 检查是否有趋势分析
     * 
     * @return 是否有趋势分析
     */
    public boolean hasTrends() {
        return !trends.isEmpty();
    }
    
    /**
     * 检查是否有建议
     * 
     * @return 是否有建议
     */
    public boolean hasRecommendations() {
        return !recommendations.isEmpty();
    }
    
    /**
     * 获取洞察摘要
     * 
     * @return 洞察摘要
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("搜索洞察分析 (").append(confidenceLevel).append("置信度):\n");
        
        if (hasKeyFindings()) {
            summary.append("关键发现 (").append(keyFindings.size()).append("项)");
            if (hasTrends()) {
                summary.append(", 趋势分析 (").append(trends.size()).append("项)");
            }
            if (hasRecommendations()) {
                summary.append(", 建议 (").append(recommendations.size()).append("项)");
            }
        } else {
            summary.append("未发现关键信息");
        }
        
        return summary.toString();
    }
    
    /**
     * 获取详细的洞察报告
     * 
     * @return 详细报告
     */
    public String getDetailedReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("=== 搜索洞察分析报告 ===\n");
        report.append("分析方法: ").append(analysisMethod).append("\n");
        report.append("置信度: ").append(confidenceLevel).append("\n");
        report.append("分析时间: ").append(analysisTimeMs).append("ms\n\n");
        
        if (hasKeyFindings()) {
            report.append("关键发现:\n");
            for (int i = 0; i < keyFindings.size(); i++) {
                report.append(String.format("%d. %s\n", i + 1, keyFindings.get(i)));
            }
            report.append("\n");
        }
        
        if (hasTrends()) {
            report.append("趋势分析:\n");
            for (int i = 0; i < trends.size(); i++) {
                report.append(String.format("• %s\n", trends.get(i)));
            }
            report.append("\n");
        }
        
        if (hasRecommendations()) {
            report.append("建议:\n");
            for (int i = 0; i < recommendations.size(); i++) {
                report.append(String.format("→ %s\n", recommendations.get(i)));
            }
            report.append("\n");
        }
        
        if (!sources.isEmpty()) {
            report.append("信息来源:\n");
            for (String source : sources) {
                report.append("- ").append(source).append("\n");
            }
        }
        
        return report.toString();
    }
    
    @Override
    public String toString() {
        return "SearchInsights{" +
                "keyFindings=" + keyFindings.size() +
                ", trends=" + trends.size() +
                ", recommendations=" + recommendations.size() +
                ", confidenceLevel='" + confidenceLevel + '\'' +
                ", sources=" + sources.size() +
                '}';
    }
    
    /**
     * 构建器类
     */
    public static class Builder {
        private List<String> keyFindings = new ArrayList<>();
        private List<String> trends = new ArrayList<>();
        private List<String> recommendations = new ArrayList<>();
        private String confidenceLevel = "中等";
        private List<String> sources = new ArrayList<>();
        private String analysisMethod = "标准分析";
        private long analysisTimeMs = 0;
        
        public Builder keyFinding(String finding) {
            if (finding != null && !finding.trim().isEmpty()) {
                this.keyFindings.add(finding.trim());
            }
            return this;
        }
        
        public Builder keyFindings(List<String> findings) {
            if (findings != null) {
                for (String finding : findings) {
                    keyFinding(finding);
                }
            }
            return this;
        }
        
        public Builder trend(String trend) {
            if (trend != null && !trend.trim().isEmpty()) {
                this.trends.add(trend.trim());
            }
            return this;
        }
        
        public Builder trends(List<String> trends) {
            if (trends != null) {
                for (String trend : trends) {
                    trend(trend);
                }
            }
            return this;
        }
        
        public Builder recommendation(String recommendation) {
            if (recommendation != null && !recommendation.trim().isEmpty()) {
                this.recommendations.add(recommendation.trim());
            }
            return this;
        }
        
        public Builder recommendations(List<String> recommendations) {
            if (recommendations != null) {
                for (String recommendation : recommendations) {
                    recommendation(recommendation);
                }
            }
            return this;
        }
        
        public Builder confidenceLevel(String confidenceLevel) {
            this.confidenceLevel = confidenceLevel;
            return this;
        }
        
        public Builder source(String source) {
            if (source != null && !source.trim().isEmpty()) {
                this.sources.add(source.trim());
            }
            return this;
        }
        
        public Builder sources(List<String> sources) {
            if (sources != null) {
                for (String source : sources) {
                    source(source);
                }
            }
            return this;
        }
        
        public Builder analysisMethod(String analysisMethod) {
            this.analysisMethod = analysisMethod;
            return this;
        }
        
        public Builder analysisTimeMs(long analysisTimeMs) {
            this.analysisTimeMs = analysisTimeMs;
            return this;
        }
        
        public SearchInsights build() {
            return new SearchInsights(keyFindings, trends, recommendations, 
                                    confidenceLevel, sources, analysisMethod, analysisTimeMs);
        }
    }
    
    /**
     * 创建构建器
     * 
     * @return 构建器实例
     */
    public static Builder builder() {
        return new Builder();
    }
}