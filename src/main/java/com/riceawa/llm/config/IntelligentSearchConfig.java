package com.riceawa.llm.config;

import com.riceawa.llm.subagent.search.SearchStrategy;

/**
 * 智能搜索子代理配置类
 */
public class IntelligentSearchConfig extends SubAgentTypeConfig {
    
    // 搜索特定配置
    private String model = "gemini-2.5-flash";
    private int maxSearchRounds = 3;
    private int maxResultsPerRound = 5;
    private long roundTimeoutMs = 30000; // 每轮30秒超时
    private String language = "zh-CN";
    private boolean enableSafeSearch = true;
    private boolean enableDeepAnalysis = true;
    private SearchStrategy strategy = SearchStrategy.COMPREHENSIVE;
    private double analysisDepth = 0.8; // 0.0-1.0
    
    // 系统提示词配置
    private String systemPromptTemplate = "你是一个专业的智能搜索分析助手。你的任务是进行多轮搜索并提供深度分析。";
    private boolean enableDynamicPrompt = true;
    
    /**
     * 构造函数
     */
    public IntelligentSearchConfig() {
        super("INTELLIGENT_SEARCH");
        // 设置搜索子代理特定的默认值
        this.maxConcurrentInstances = 3;
        this.instanceTimeoutMs = 120000; // 2分钟总超时
        this.maxRetries = 2;
    }
    
    /**
     * 创建默认配置
     */
    public static IntelligentSearchConfig createDefault() {
        return new IntelligentSearchConfig();
    }
    
    /**
     * 验证搜索特定配置
     */
    @Override
    protected boolean validateSpecificConfig() {
        if (model == null || model.trim().isEmpty()) {
            return false;
        }
        
        if (maxSearchRounds <= 0 || maxSearchRounds > 10) {
            return false;
        }
        
        if (maxResultsPerRound <= 0 || maxResultsPerRound > 20) {
            return false;
        }
        
        if (roundTimeoutMs <= 0 || roundTimeoutMs > 120000) { // 最大2分钟每轮
            return false;
        }
        
        if (language == null || language.trim().isEmpty()) {
            return false;
        }
        
        if (analysisDepth < 0.0 || analysisDepth > 1.0) {
            return false;
        }
        
        if (strategy == null) {
            return false;
        }
        
        if (systemPromptTemplate == null || systemPromptTemplate.trim().isEmpty()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 获取配置摘要
     */
    @Override
    public String getConfigSummary() {
        return String.format("%s, Model: %s, MaxRounds: %d, Strategy: %s, AnalysisDepth: %.1f",
                super.getConfigSummary(), model, maxSearchRounds, strategy, analysisDepth);
    }
    
    /**
     * 根据策略调整搜索轮数
     */
    public int getEffectiveMaxSearchRounds() {
        switch (strategy) {
            case QUICK:
                return Math.min(maxSearchRounds, 2);
            case COMPREHENSIVE:
                return maxSearchRounds;
            case DEEP_DIVE:
                return Math.max(maxSearchRounds, 3);
            default:
                return maxSearchRounds;
        }
    }
    
    /**
     * 根据策略调整轮次超时
     */
    public long getEffectiveRoundTimeoutMs() {
        switch (strategy) {
            case QUICK:
                return Math.min(roundTimeoutMs, 20000); // 快速策略最多20秒每轮
            case DEEP_DIVE:
                return Math.max(roundTimeoutMs, 45000); // 深度策略至少45秒每轮
            default:
                return roundTimeoutMs;
        }
    }
    
    // Getters and Setters
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        if (model != null && !model.trim().isEmpty()) {
            this.model = model.trim();
        }
    }
    
    public int getMaxSearchRounds() {
        return maxSearchRounds;
    }
    
    public void setMaxSearchRounds(int maxSearchRounds) {
        if (maxSearchRounds > 0 && maxSearchRounds <= 10) {
            this.maxSearchRounds = maxSearchRounds;
        }
    }
    
    public int getMaxResultsPerRound() {
        return maxResultsPerRound;
    }
    
    public void setMaxResultsPerRound(int maxResultsPerRound) {
        if (maxResultsPerRound > 0 && maxResultsPerRound <= 20) {
            this.maxResultsPerRound = maxResultsPerRound;
        }
    }
    
    public long getRoundTimeoutMs() {
        return roundTimeoutMs;
    }
    
    public void setRoundTimeoutMs(long roundTimeoutMs) {
        if (roundTimeoutMs > 0 && roundTimeoutMs <= 120000) {
            this.roundTimeoutMs = roundTimeoutMs;
        }
    }
    
    public String getLanguage() {
        return language;
    }
    
    public void setLanguage(String language) {
        if (language != null && !language.trim().isEmpty()) {
            this.language = language.trim();
        }
    }
    
    public boolean isEnableSafeSearch() {
        return enableSafeSearch;
    }
    
    public void setEnableSafeSearch(boolean enableSafeSearch) {
        this.enableSafeSearch = enableSafeSearch;
    }
    
    public boolean isEnableDeepAnalysis() {
        return enableDeepAnalysis;
    }
    
    public void setEnableDeepAnalysis(boolean enableDeepAnalysis) {
        this.enableDeepAnalysis = enableDeepAnalysis;
    }
    
    public SearchStrategy getStrategy() {
        return strategy;
    }
    
    public void setStrategy(SearchStrategy strategy) {
        if (strategy != null) {
            this.strategy = strategy;
        }
    }
    
    public double getAnalysisDepth() {
        return analysisDepth;
    }
    
    public void setAnalysisDepth(double analysisDepth) {
        if (analysisDepth >= 0.0 && analysisDepth <= 1.0) {
            this.analysisDepth = analysisDepth;
        }
    }
    
    public String getSystemPromptTemplate() {
        return systemPromptTemplate;
    }
    
    public void setSystemPromptTemplate(String systemPromptTemplate) {
        if (systemPromptTemplate != null && !systemPromptTemplate.trim().isEmpty()) {
            this.systemPromptTemplate = systemPromptTemplate.trim();
        }
    }
    
    public boolean isEnableDynamicPrompt() {
        return enableDynamicPrompt;
    }
    
    public void setEnableDynamicPrompt(boolean enableDynamicPrompt) {
        this.enableDynamicPrompt = enableDynamicPrompt;
    }
}