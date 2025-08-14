package com.riceawa.llm.subagent.search;

import com.riceawa.llm.subagent.SubAgentTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能搜索任务
 * 继承SubAgentTask，专门用于智能搜索功能
 */
public class IntelligentSearchTask extends SubAgentTask<IntelligentSearchResult> {
    
    private final String originalQuery;
    private final int maxSearchRounds;
    private final SearchStrategy strategy;
    
    private int currentSearchRound;
    private List<SearchRound> searchHistory;
    private String currentQuery;
    private boolean analysisCompleted;
    
    /**
     * 构造函数
     * 
     * @param requesterId 请求者ID
     * @param originalQuery 原始查询
     * @param maxSearchRounds 最大搜索轮数
     * @param strategy 搜索策略
     * @param timeoutMs 超时时间（毫秒）
     * @param parameters 额外参数
     */
    public IntelligentSearchTask(String requesterId, String originalQuery, 
                               int maxSearchRounds, SearchStrategy strategy, 
                               long timeoutMs, Map<String, Object> parameters) {
        super(requesterId, timeoutMs, parameters);
        
        this.originalQuery = originalQuery;
        this.maxSearchRounds = maxSearchRounds;
        this.strategy = strategy != null ? strategy : SearchStrategy.COMPREHENSIVE;
        this.currentSearchRound = 0;
        this.searchHistory = new ArrayList<>();
        this.currentQuery = originalQuery;
        this.analysisCompleted = false;
    }
    
    /**
     * 便捷构造函数
     * 
     * @param requesterId 请求者ID
     * @param originalQuery 原始查询
     * @param maxSearchRounds 最大搜索轮数
     * @param strategy 搜索策略
     * @param timeoutMs 超时时间（毫秒）
     */
    public IntelligentSearchTask(String requesterId, String originalQuery, 
                               int maxSearchRounds, SearchStrategy strategy, long timeoutMs) {
        this(requesterId, originalQuery, maxSearchRounds, strategy, timeoutMs, null);
    }
    
    @Override
    public String getTaskType() {
        return "INTELLIGENT_SEARCH";
    }
    
    @Override
    public Map<String, Object> getTaskSpecificData() {
        Map<String, Object> data = new HashMap<>();
        data.put("originalQuery", originalQuery);
        data.put("maxSearchRounds", maxSearchRounds);
        data.put("strategy", strategy.name());
        data.put("currentSearchRound", currentSearchRound);
        data.put("searchHistorySize", searchHistory.size());
        data.put("currentQuery", currentQuery);
        data.put("analysisCompleted", analysisCompleted);
        return data;
    }
    
    /**
     * 开始新的搜索轮次
     * 
     * @param query 搜索查询
     */
    public void startNewRound(String query) {
        this.currentSearchRound++;
        this.currentQuery = query;
        setStatus(com.riceawa.llm.subagent.SubAgentTaskStatus.EXECUTING);
    }
    
    /**
     * 完成当前搜索轮次
     * 
     * @param searchRound 搜索轮次结果
     */
    public void completeCurrentRound(SearchRound searchRound) {
        if (searchRound != null) {
            searchHistory.add(searchRound);
        }
    }
    
    /**
     * 开始分析阶段
     */
    public void startAnalysis() {
        setStatus(com.riceawa.llm.subagent.SubAgentTaskStatus.ANALYZING);
    }
    
    /**
     * 完成分析阶段
     */
    public void completeAnalysis() {
        this.analysisCompleted = true;
    }
    
    /**
     * 检查是否可以继续搜索
     * 
     * @return 是否可以继续搜索
     */
    public boolean canContinueSearch() {
        return currentSearchRound < maxSearchRounds && !isTimeout();
    }
    
    /**
     * 检查是否达到最大搜索轮数
     * 
     * @return 是否达到最大轮数
     */
    public boolean hasReachedMaxRounds() {
        return currentSearchRound >= maxSearchRounds;
    }
    
    /**
     * 获取搜索进度百分比
     * 
     * @return 进度百分比（0-100）
     */
    public int getProgressPercentage() {
        if (maxSearchRounds <= 0) {
            return 0;
        }
        
        int progress = (int) ((double) currentSearchRound / maxSearchRounds * 80); // 搜索占80%
        
        if (analysisCompleted) {
            progress = 100; // 分析完成占100%
        } else if (getStatus() == com.riceawa.llm.subagent.SubAgentTaskStatus.ANALYZING) {
            progress = Math.max(progress, 80); // 分析阶段至少80%
        }
        
        return Math.min(progress, 100);
    }
    
    /**
     * 获取任务状态描述
     * 
     * @return 状态描述
     */
    public String getStatusDescription() {
        switch (getStatus()) {
            case PENDING:
                return "等待开始搜索";
            case PROCESSING:
                return "准备搜索";
            case EXECUTING:
                return String.format("正在进行第%d轮搜索 (共%d轮)", currentSearchRound, maxSearchRounds);
            case ANALYZING:
                return "正在分析搜索结果";
            case COMPLETED:
                return "搜索和分析已完成";
            case FAILED:
                return "搜索失败: " + getErrorMessage();
            case TIMEOUT:
                return "搜索超时";
            case MAX_ROUNDS_REACHED:
                return "已达到最大搜索轮数";
            default:
                return "未知状态";
        }
    }
    
    // Getters
    public String getOriginalQuery() {
        return originalQuery;
    }
    
    public int getMaxSearchRounds() {
        return maxSearchRounds;
    }
    
    public SearchStrategy getStrategy() {
        return strategy;
    }
    
    public int getCurrentSearchRound() {
        return currentSearchRound;
    }
    
    public List<SearchRound> getSearchHistory() {
        return new ArrayList<>(searchHistory);
    }
    
    public String getCurrentQuery() {
        return currentQuery;
    }
    
    public boolean isAnalysisCompleted() {
        return analysisCompleted;
    }
    
    /**
     * 获取最后一轮搜索结果
     * 
     * @return 最后一轮搜索结果，如果没有则返回null
     */
    public SearchRound getLastSearchRound() {
        return searchHistory.isEmpty() ? null : searchHistory.get(searchHistory.size() - 1);
    }
    
    /**
     * 获取成功的搜索轮数
     * 
     * @return 成功的搜索轮数
     */
    public int getSuccessfulRounds() {
        return (int) searchHistory.stream().filter(SearchRound::isSuccessful).count();
    }
    
    /**
     * 获取总搜索时间
     * 
     * @return 总搜索时间（毫秒）
     */
    public long getTotalSearchTime() {
        return searchHistory.stream().mapToLong(SearchRound::getProcessingTimeMs).sum();
    }
}