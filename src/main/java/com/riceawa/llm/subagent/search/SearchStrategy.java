package com.riceawa.llm.subagent.search;

/**
 * 搜索策略枚举
 * 定义不同的搜索深度和方式
 */
public enum SearchStrategy {
    /**
     * 快速搜索策略
     * - 1-2轮搜索
     * - 重点关注最相关的信息
     * - 适用于简单查询
     */
    QUICK("快速搜索", 1, 2, 0.5),
    
    /**
     * 全面搜索策略
     * - 2-3轮搜索
     * - 从多个角度收集信息
     * - 适用于一般查询
     */
    COMPREHENSIVE("全面搜索", 2, 3, 0.7),
    
    /**
     * 深度搜索策略
     * - 3-5轮搜索
     * - 进行详细的信息挖掘和分析
     * - 适用于复杂查询
     */
    DEEP_DIVE("深度搜索", 3, 5, 0.9);
    
    private final String displayName;
    private final int minRounds;
    private final int maxRounds;
    private final double analysisDepth;
    
    SearchStrategy(String displayName, int minRounds, int maxRounds, double analysisDepth) {
        this.displayName = displayName;
        this.minRounds = minRounds;
        this.maxRounds = maxRounds;
        this.analysisDepth = analysisDepth;
    }
    
    /**
     * 获取显示名称
     * 
     * @return 显示名称
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * 获取最小搜索轮数
     * 
     * @return 最小轮数
     */
    public int getMinRounds() {
        return minRounds;
    }
    
    /**
     * 获取最大搜索轮数
     * 
     * @return 最大轮数
     */
    public int getMaxRounds() {
        return maxRounds;
    }
    
    /**
     * 获取分析深度
     * 
     * @return 分析深度（0.0-1.0）
     */
    public double getAnalysisDepth() {
        return analysisDepth;
    }
    
    /**
     * 根据查询复杂度推荐搜索策略
     * 
     * @param queryLength 查询长度
     * @param hasSpecificRequirements 是否有特定要求
     * @return 推荐的搜索策略
     */
    public static SearchStrategy recommendStrategy(int queryLength, boolean hasSpecificRequirements) {
        if (queryLength < 20 && !hasSpecificRequirements) {
            return QUICK;
        } else if (queryLength > 100 || hasSpecificRequirements) {
            return DEEP_DIVE;
        } else {
            return COMPREHENSIVE;
        }
    }
}