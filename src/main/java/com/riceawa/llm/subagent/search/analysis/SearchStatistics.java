package com.riceawa.llm.subagent.search.analysis;

/**
 * 搜索统计信息
 */
public class SearchStatistics {
    
    private final int totalRounds;
    private final int successfulRounds;
    private final int totalResults;
    private final long totalSearchTime;
    private final double informationRichness;
    
    /**
     * 构造函数
     */
    public SearchStatistics(int totalRounds, int successfulRounds, int totalResults, 
                           long totalSearchTime, double informationRichness) {
        this.totalRounds = totalRounds;
        this.successfulRounds = successfulRounds;
        this.totalResults = totalResults;
        this.totalSearchTime = totalSearchTime;
        this.informationRichness = informationRichness;
    }
    
    /**
     * 获取总轮数
     */
    public int getTotalRounds() {
        return totalRounds;
    }
    
    /**
     * 获取成功轮数
     */
    public int getSuccessfulRounds() {
        return successfulRounds;
    }
    
    /**
     * 获取失败轮数
     */
    public int getFailedRounds() {
        return totalRounds - successfulRounds;
    }
    
    /**
     * 获取总结果数
     */
    public int getTotalResults() {
        return totalResults;
    }
    
    /**
     * 获取总搜索时间
     */
    public long getTotalSearchTime() {
        return totalSearchTime;
    }
    
    /**
     * 获取平均每轮时间
     */
    public double getAverageRoundTime() {
        return successfulRounds > 0 ? (double) totalSearchTime / successfulRounds : 0;
    }
    
    /**
     * 获取信息丰富度
     */
    public double getInformationRichness() {
        return informationRichness;
    }
    
    /**
     * 获取成功率
     */
    public double getSuccessRate() {
        return totalRounds > 0 ? (double) successfulRounds / totalRounds : 0;
    }
    
    /**
     * 获取平均每轮结果数
     */
    public double getAverageResultsPerRound() {
        return successfulRounds > 0 ? (double) totalResults / successfulRounds : 0;
    }
    
    @Override
    public String toString() {
        return String.format("SearchStatistics{totalRounds=%d, successfulRounds=%d, " +
                           "totalResults=%d, successRate=%.2f, informationRichness=%.2f}",
                           totalRounds, successfulRounds, totalResults, getSuccessRate(), informationRichness);
    }
}