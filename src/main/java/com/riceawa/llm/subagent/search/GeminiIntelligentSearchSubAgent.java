package com.riceawa.llm.subagent.search;

import com.riceawa.llm.config.IntelligentSearchConfig;
import com.riceawa.llm.config.PromptGeneratorManager;
import com.riceawa.llm.core.*;
import com.riceawa.llm.logging.LogManager;
import com.riceawa.llm.subagent.*;
import com.riceawa.llm.subagent.search.analysis.SearchResultAnalyzer;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Gemini智能搜索子代理
 * 继承BaseSubAgent，专门用于智能搜索功能
 * 
 * 特性：
 * - 独立的HTTP客户端和会话管理
 * - 仅配置googleSearch工具
 * - 支持多轮搜索和智能分析
 * - 完全的会话隔离
 */
public class GeminiIntelligentSearchSubAgent extends BaseSubAgent<IntelligentSearchTask, IntelligentSearchResult> {
    
    private static final String AGENT_TYPE = "INTELLIGENT_SEARCH";
    private static final String GOOGLE_SEARCH_TOOL = "googleSearch";
    
    // 独立的配置和服务
    private IntelligentSearchConfig searchConfig;
    private LLMConfig llmConfig;
    private PromptGeneratorManager promptManager;
    
    // 搜索结果分析器
    private SearchResultAnalyzer searchResultAnalyzer;
    
    // 搜索状态管理
    private volatile boolean isSearching = false;
    private volatile String currentSessionId;
    
    // 资源使用统计
    private long httpConnectionCount = 0;
    private long memoryUsageBytes = 0;
    
    /**
     * 构造函数
     */
    public GeminiIntelligentSearchSubAgent() {
        super(AGENT_TYPE);
        this.promptManager = PromptGeneratorManager.getInstance();
        this.currentSessionId = UUID.randomUUID().toString();
        
        LogManager.getInstance().system("Created GeminiIntelligentSearchSubAgent with session: " + currentSessionId);
    }
    
    @Override
    protected CompletableFuture<IntelligentSearchResult> performExecution(IntelligentSearchTask task) {
        LogManager.getInstance().system("Starting intelligent search execution for task: " + task.getTaskId());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 配置代理
                configureAgent(task);
                
                // 更新资源使用统计
                updateResourceUsage(memoryUsageBytes, (int) httpConnectionCount);
                
                // 执行智能搜索
                return executeIntelligentSearch(task);
                
            } catch (Exception e) {
                LogManager.getInstance().error("Error during intelligent search execution", e);
                return createErrorResult("搜索执行失败: " + e.getMessage(), 
                                       System.currentTimeMillis() - task.getStartTime());
            }
        });
    }
    
    /**
     * 执行智能搜索
     */
    private IntelligentSearchResult executeIntelligentSearch(IntelligentSearchTask task) {
        long startTime = System.currentTimeMillis();
        isSearching = true;
        
        try {
            LogManager.getInstance().system("Executing intelligent search for query: " + task.getOriginalQuery());
            
            // 生成系统提示词
            String systemPrompt = generateSystemPrompt(task);
            
            // 创建消息列表
            List<LLMMessage> messages = new ArrayList<>();
            messages.add(new LLMMessage(LLMMessage.MessageRole.SYSTEM, systemPrompt));
            messages.add(new LLMMessage(LLMMessage.MessageRole.USER, task.getOriginalQuery()));
            
            // 执行搜索会话
            IntelligentSearchResult result = executeSearchSession(task, messages);
            
            long totalTime = System.currentTimeMillis() - startTime;
            LogManager.getInstance().system("Intelligent search completed in " + totalTime + "ms");
            
            return result;
            
        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            LogManager.getInstance().error("Intelligent search failed", e);
            
            return createErrorResult("智能搜索失败: " + e.getMessage(), totalTime);
        } finally {
            isSearching = false;
        }
    }
    
    /**
     * 执行搜索会话
     */
    private IntelligentSearchResult executeSearchSession(IntelligentSearchTask task, List<LLMMessage> messages) {
        List<SearchRound> searchRounds = new ArrayList<>();
        String currentQuery = task.getOriginalQuery();
        
        // 执行多轮搜索
        while (task.canContinueSearch()) {
            try {
                // 开始新的搜索轮次
                task.startNewRound(currentQuery);
                
                LogManager.getInstance().system("Starting search round " + task.getCurrentSearchRound() + 
                                              " with query: " + currentQuery);
                
                // 执行单轮搜索
                SearchRound round = executeSingleSearchRound(task, messages, currentQuery);
                
                if (round != null) {
                    searchRounds.add(round);
                    task.completeCurrentRound(round);
                    
                    // 检查是否需要继续搜索
                    if (!round.isSuccessful() || !round.hasNextRound()) {
                        break;
                    }
                    
                    // 准备下一轮搜索
                    currentQuery = generateNextQuery(task, round);
                    if (currentQuery == null || currentQuery.trim().isEmpty()) {
                        break;
                    }
                } else {
                    break;
                }
                
            } catch (Exception e) {
                LogManager.getInstance().error("Error in search round " + task.getCurrentSearchRound(), e);
                
                // 创建失败的搜索轮次
                SearchRound failedRound = SearchRound.failure(
                    task.getCurrentSearchRound(),
                    currentQuery,
                    "搜索轮次失败: " + e.getMessage(),
                    0
                );
                searchRounds.add(failedRound);
                task.completeCurrentRound(failedRound);
                break;
            }
        }
        
        // 开始分析阶段
        task.startAnalysis();
        
        // 分析搜索结果
        return analyzeSearchResults(task, searchRounds);
    }
    
    /**
     * 执行单轮搜索
     */
    private SearchRound executeSingleSearchRound(IntelligentSearchTask task, List<LLMMessage> messages, String query) {
        long roundStartTime = System.currentTimeMillis();
        
        try {
            // 增加连接计数
            httpConnectionCount++;
            updateResourceUsage(memoryUsageBytes, (int) httpConnectionCount);
            
            // 发送搜索请求
            CompletableFuture<LLMResponse> future = llmService.chat(messages, llmConfig);
            
            // 等待响应（带超时）
            LLMResponse response = future.get(searchConfig.getEffectiveRoundTimeoutMs(), TimeUnit.MILLISECONDS);
            
            long processingTime = System.currentTimeMillis() - roundStartTime;
            
            if (response.isSuccess()) {
                String content = response.getContent();
                
                // 更新消息历史
                if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                    messages.add(response.getChoices().get(0).getMessage());
                }
                
                // 分析响应内容，确定是否需要下一轮搜索
                String nextQueryReason = analyzeForNextRound(task, content);
                
                return SearchRound.success(
                    task.getCurrentSearchRound(),
                    query,
                    content,
                    processingTime,
                    nextQueryReason,
                    extractResultCount(content)
                );
            } else {
                return SearchRound.failure(
                    task.getCurrentSearchRound(),
                    query,
                    "API响应失败: " + response.getError(),
                    processingTime
                );
            }
            
        } catch (TimeoutException e) {
            long processingTime = System.currentTimeMillis() - roundStartTime;
            return SearchRound.failure(
                task.getCurrentSearchRound(),
                query,
                "搜索超时",
                processingTime
            );
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - roundStartTime;
            return SearchRound.failure(
                task.getCurrentSearchRound(),
                query,
                "搜索异常: " + e.getMessage(),
                processingTime
            );
        }
    }
    
    /**
     * 分析搜索结果并生成最终报告
     */
    private IntelligentSearchResult analyzeSearchResults(IntelligentSearchTask task, List<SearchRound> searchRounds) {
        long analysisStartTime = System.currentTimeMillis();
        
        try {
            LogManager.getInstance().system("Analyzing search results for " + searchRounds.size() + " rounds");
            
            // 估算内存使用
            long estimatedMemory = estimateMemoryUsage(searchRounds);
            memoryUsageBytes = estimatedMemory;
            updateResourceUsage(memoryUsageBytes, (int) httpConnectionCount);
            
            // 使用新的搜索结果分析器
            com.riceawa.llm.subagent.search.analysis.SearchResultAnalysis analysisResult =
                searchResultAnalyzer.analyzeSearchResults(task, searchRounds);
            
            long analysisTime = System.currentTimeMillis() - analysisStartTime;
            task.completeAnalysis();
            
            if (analysisResult.isSuccess()) {
                // 提取分析结果
                SearchInsights insights = analysisResult.getInsights();
                String finalReport = analysisResult.getFinalReport();
                
                // 生成结构化摘要
                String structuredSummary = generateStructuredSummaryFromAnalysis(task, searchRounds, analysisResult);
                
                long totalTime = System.currentTimeMillis() - task.getStartTime();
                
                return IntelligentSearchResult.success(
                    finalReport,
                    structuredSummary,
                    searchRounds,
                    insights,
                    task.getOriginalQuery(),
                    task.getStrategy(),
                    totalTime,
                    createEnhancedResultMetadata(task, searchRounds, analysisResult)
                );
            } else {
                // 分析失败，但搜索成功
                String basicSummary = generateBasicSummary(task, searchRounds);
                long totalTime = System.currentTimeMillis() - task.getStartTime();
                
                return IntelligentSearchResult.success(
                    "深度分析阶段失败，但基础搜索数据已收集完成: " + analysisResult.getError(),
                    basicSummary,
                    searchRounds,
                    null,
                    task.getOriginalQuery(),
                    task.getStrategy(),
                    totalTime,
                    createResultMetadata(task, searchRounds)
                );
            }
            
        } catch (Exception e) {
            LogManager.getInstance().error("Error during search result analysis", e);
            
            // 分析失败，返回基本结果
            String basicSummary = generateBasicSummary(task, searchRounds);
            long totalTime = System.currentTimeMillis() - task.getStartTime();
            
            return IntelligentSearchResult.success(
                "分析过程中出现错误: " + e.getMessage(),
                basicSummary,
                searchRounds,
                null,
                task.getOriginalQuery(),
                task.getStrategy(),
                totalTime,
                createResultMetadata(task, searchRounds)
            );
        }
    }
    
    @Override
    protected IntelligentSearchResult createResult() {
        return IntelligentSearchResult.success(
            "",
            "",
            new ArrayList<>(),
            null,
            "",
            SearchStrategy.COMPREHENSIVE,
            0,
            new HashMap<>()
        );
    }
    
    @Override
    protected IntelligentSearchResult createErrorResult(String error, long processingTime) {
        return IntelligentSearchResult.failure(
            error,
            "",
            SearchStrategy.COMPREHENSIVE,
            new ArrayList<>(),
            processingTime,
            new HashMap<>()
        );
    }
    
    @Override
    protected void configureAgent(IntelligentSearchTask task) {
        // 从任务参数中获取配置
        Map<String, Object> parameters = task.getParameters();
        if (parameters != null && parameters.containsKey("searchConfig")) {
            this.searchConfig = (IntelligentSearchConfig) parameters.get("searchConfig");
        } else {
            this.searchConfig = IntelligentSearchConfig.createDefault();
        }
        
        // 配置LLM
        configureLLMConfig();
        
        // 初始化搜索结果分析器
        if (searchConfig.isEnableDeepAnalysis()) {
            this.searchResultAnalyzer = new SearchResultAnalyzer(searchConfig, llmService);
        }
        
        LogManager.getInstance().system("Configured search agent with model: " + searchConfig.getModel() + 
                                      ", max rounds: " + searchConfig.getMaxSearchRounds() +
                                      ", deep analysis: " + searchConfig.isEnableDeepAnalysis());
    }
    
    /**
     * 配置LLM配置
     */
    private void configureLLMConfig() {
        this.llmConfig = new LLMConfig();
        this.llmConfig.setModel(searchConfig.getModel());
        this.llmConfig.setTemperature(0.7);
        this.llmConfig.setMaxTokens(4096);
        this.llmConfig.setStream(false);
        
        // 配置仅包含googleSearch工具
        List<LLMConfig.ToolDefinition> tools = new ArrayList<>();
        LLMConfig.FunctionDefinition googleSearchFunction = new LLMConfig.FunctionDefinition(
            GOOGLE_SEARCH_TOOL,
            "执行Google搜索",
            new HashMap<>()
        );
        tools.add(new LLMConfig.ToolDefinition(googleSearchFunction));
        
        this.llmConfig.setTools(tools);
        this.llmConfig.setToolChoice("auto");
        
        LogManager.getInstance().system("Configured LLM with googleSearch tool only");
    }
    
    /**
     * 生成系统提示词
     */
    private String generateSystemPrompt(IntelligentSearchTask task) {
        if (searchConfig.isEnableDynamicPrompt()) {
            return promptManager.generateSystemPrompt(searchConfig, task.getOriginalQuery());
        } else {
            return searchConfig.getSystemPromptTemplate();
        }
    }
    
    /**
     * 生成分析提示词
     */
    private String generateAnalysisPrompt(IntelligentSearchTask task, String searchResults) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个专业的信息分析师。请对以下搜索结果进行深度分析：\n\n");
        prompt.append("原始查询: ").append(task.getOriginalQuery()).append("\n");
        prompt.append("搜索策略: ").append(task.getStrategy().getDisplayName()).append("\n");
        prompt.append("搜索轮数: ").append(task.getCurrentSearchRound()).append("\n\n");
        prompt.append("搜索结果:\n").append(searchResults).append("\n\n");
        prompt.append("请提供:\n");
        prompt.append("1. 关键发现和洞察\n");
        prompt.append("2. 趋势分析\n");
        prompt.append("3. 实用建议\n");
        prompt.append("4. 信息来源评估\n");
        
        return prompt.toString();
    }
    
    /**
     * 分析是否需要下一轮搜索
     */
    private String analyzeForNextRound(IntelligentSearchTask task, String content) {
        // 简单的启发式规则判断是否需要继续搜索
        if (task.getCurrentSearchRound() >= task.getMaxSearchRounds()) {
            return null;
        }
        
        // 检查内容是否包含"需要更多信息"等关键词
        String lowerContent = content.toLowerCase();
        if (lowerContent.contains("需要更多") || lowerContent.contains("进一步搜索") || 
            lowerContent.contains("additional search") || lowerContent.contains("more information")) {
            return "内容表明需要更多信息";
        }
        
        // 根据策略决定
        switch (task.getStrategy()) {
            case QUICK:
                return null; // 快速策略通常只搜索一轮
            case COMPREHENSIVE:
                return task.getCurrentSearchRound() < 2 ? "全面搜索策略需要多轮搜索" : null;
            case DEEP_DIVE:
                return task.getCurrentSearchRound() < 3 ? "深度搜索策略需要详细探索" : null;
            default:
                return null;
        }
    }
    
    /**
     * 生成下一轮搜索查询
     */
    private String generateNextQuery(IntelligentSearchTask task, SearchRound lastRound) {
        if (!lastRound.hasNextRound()) {
            return null;
        }
        
        // 基于上一轮结果生成更具体的查询
        String originalQuery = task.getOriginalQuery();
        int roundNumber = task.getCurrentSearchRound();
        
        // 简单的查询优化策略
        switch (roundNumber) {
            case 1:
                return originalQuery + " 详细信息";
            case 2:
                return originalQuery + " 最新动态";
            case 3:
                return originalQuery + " 深度分析";
            default:
                return originalQuery + " 补充信息";
        }
    }
    
    /**
     * 提取结果数量
     */
    private int extractResultCount(String content) {
        // 简单的结果数量估算
        if (content == null || content.trim().isEmpty()) {
            return 0;
        }
        
        // 基于内容长度估算结果数量
        int length = content.length();
        if (length < 100) {
            return 1;
        } else if (length < 500) {
            return 2;
        } else if (length < 1000) {
            return 3;
        } else {
            return Math.min(5, length / 200);
        }
    }
    
    /**
     * 提取搜索洞察
     */
    private SearchInsights extractInsights(String analysisContent, long analysisTime) {
        SearchInsights.Builder builder = SearchInsights.builder()
            .analysisTimeMs(analysisTime)
            .analysisMethod("LLM深度分析")
            .confidenceLevel("高");
        
        // 简单的内容解析提取洞察
        if (analysisContent != null && !analysisContent.trim().isEmpty()) {
            String[] lines = analysisContent.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("关键发现") || line.startsWith("Key Finding")) {
                    builder.keyFinding(line);
                } else if (line.startsWith("趋势") || line.startsWith("Trend")) {
                    builder.trend(line);
                } else if (line.startsWith("建议") || line.startsWith("Recommendation")) {
                    builder.recommendation(line);
                }
            }
        }
        
        return builder.build();
    }
    
    /**
     * 生成结构化摘要（从分析结果）
     */
    private String generateStructuredSummaryFromAnalysis(IntelligentSearchTask task, List<SearchRound> rounds, 
                                                        com.riceawa.llm.subagent.search.analysis.SearchResultAnalysis analysis) {
        StringBuilder summary = new StringBuilder();
        
        summary.append("智能搜索摘要\n");
        summary.append("查询: ").append(task.getOriginalQuery()).append("\n");
        summary.append("策略: ").append(task.getStrategy().getDisplayName()).append("\n");
        summary.append("搜索轮数: ").append(rounds.size()).append("/").append(task.getMaxSearchRounds()).append("\n");
        summary.append("成功轮数: ").append(task.getSuccessfulRounds()).append("\n");
        summary.append("总耗时: ").append(task.getExecutionTime()).append("ms\n");
        
        if (analysis.getExtractedInformation() != null) {
            com.riceawa.llm.subagent.search.analysis.SearchStatistics stats = 
                analysis.getExtractedInformation().getSearchStatistics();
            summary.append(String.format("信息丰富度: %.2f\n", stats.getInformationRichness()));
        }
        
        summary.append("分析耗时: ").append(analysis.getAnalysisTimeMs()).append("ms\n\n");
        
        if (analysis.getInsights() != null) {
            SearchInsights insights = analysis.getInsights();
            summary.append("洞察摘要:\n");
            
            if (insights.hasKeyFindings()) {
                summary.append("• 关键发现: ").append(insights.getKeyFindings().size()).append("项\n");
            }
            if (insights.hasTrends()) {
                summary.append("• 趋势分析: ").append(insights.getTrends().size()).append("项\n");
            }
            if (insights.hasRecommendations()) {
                summary.append("• 建议: ").append(insights.getRecommendations().size()).append("项\n");
            }
            summary.append("• 置信度: ").append(insights.getConfidenceLevel()).append("\n");
        }
        
        return summary.toString();
    }
    
    /**
     * 生成基本摘要（当分析失败时）
     */
    private String generateBasicSummary(IntelligentSearchTask task, List<SearchRound> rounds) {
        StringBuilder summary = new StringBuilder();
        
        summary.append("搜索完成，共").append(rounds.size()).append("轮\n");
        summary.append("成功: ").append(task.getSuccessfulRounds()).append("轮\n");
        summary.append("总耗时: ").append(task.getExecutionTime()).append("ms\n");
        
        for (SearchRound round : rounds) {
            if (round.isSuccessful()) {
                summary.append("\n第").append(round.getRoundNumber()).append("轮: ");
                summary.append(round.getQuery()).append(" (").append(round.getProcessingTimeMs()).append("ms)");
            }
        }
        
        return summary.toString();
    }
    
    /**
     * 创建结果元数据
     */
    private Map<String, Object> createResultMetadata(IntelligentSearchTask task, List<SearchRound> rounds) {
        Map<String, Object> metadata = new HashMap<>();
        
        metadata.put("agentId", getAgentId());
        metadata.put("sessionId", currentSessionId);
        metadata.put("strategy", task.getStrategy().name());
        metadata.put("maxRounds", task.getMaxSearchRounds());
        metadata.put("actualRounds", rounds.size());
        metadata.put("successfulRounds", task.getSuccessfulRounds());
        metadata.put("model", searchConfig.getModel());
        metadata.put("language", searchConfig.getLanguage());
        metadata.put("analysisDepth", searchConfig.getAnalysisDepth());
        
        return metadata;
    }
    
    /**
     * 创建增强的结果元数据（包含分析结果）
     */
    private Map<String, Object> createEnhancedResultMetadata(IntelligentSearchTask task, List<SearchRound> rounds, 
                                                              com.riceawa.llm.subagent.search.analysis.SearchResultAnalysis analysis) {
        Map<String, Object> metadata = createResultMetadata(task, rounds);
        
        // 添加分析相关的元数据
        metadata.put("analysisTimeMs", analysis.getAnalysisTimeMs());
        metadata.put("analysisSuccess", analysis.isSuccess());
        metadata.put("hasDeepAnalysis", analysis.hasDeepAnalysis());
        metadata.put("hasInsights", analysis.hasInsights());
        
        // 添加关键词和来源信息
        metadata.put("keywordCount", analysis.getKeywords().size());
        metadata.put("sourceCount", analysis.getSources().size());
        
        // 添加分析方法的元数据
        metadata.put("analysisMethod", "SearchResultAnalyzer");
        metadata.put("enableDeepAnalysis", searchConfig.isEnableDeepAnalysis());
        
        // 合并分析器的元数据
        metadata.putAll(analysis.getMetadata());
        
        return metadata;
    }
    
    @Override
    protected void performCleanup() {
        LogManager.getInstance().system("Cleaning up GeminiIntelligentSearchSubAgent: " + getAgentId());
        
        isSearching = false;
        currentSessionId = null;
        searchConfig = null;
        llmConfig = null;
        searchResultAnalyzer = null;
        
        // 重置资源统计
        httpConnectionCount = 0;
        memoryUsageBytes = 0;
        
        super.performCleanup();
    }
    
    /**
     * 估算内存使用量
     */
    private long estimateMemoryUsage(List<SearchRound> searchRounds) {
        long memory = 0;
        
        // 基础内存开销
        memory += 1024 * 1024; // 1MB基础开销
        
        // 搜索轮次数据
        for (SearchRound round : searchRounds) {
            if (round.getContent() != null) {
                memory += round.getContent().length() * 2; // 估算字符串内存使用
            }
        }
        
        // 配置对象
        if (searchConfig != null) {
            memory += 1024; // 估算配置对象内存
        }
        
        if (llmConfig != null) {
            memory += 1024; // 估算LLM配置对象内存
        }
        
        return memory;
    }
    
    @Override
    public boolean isAvailable() {
        return super.isAvailable() && !isSearching && searchConfig != null;
    }
    
    /**
     * 获取搜索配置
     */
    public IntelligentSearchConfig getSearchConfig() {
        return searchConfig;
    }
    
    /**
     * 获取当前会话ID
     */
    public String getCurrentSessionId() {
        return currentSessionId;
    }
    
    /**
     * 检查是否正在搜索
     */
    public boolean isSearching() {
        return isSearching;
    }
}