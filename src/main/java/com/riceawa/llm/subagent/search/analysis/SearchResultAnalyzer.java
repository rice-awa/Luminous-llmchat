package com.riceawa.llm.subagent.search.analysis;

import com.riceawa.llm.config.IntelligentSearchConfig;
import com.riceawa.llm.core.*;
import com.riceawa.llm.logging.LogManager;
import com.riceawa.llm.subagent.search.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 搜索结果分析器
 * 负责解析搜索结果、提取信息、进行综合分析并生成结构化洞察
 */
public class SearchResultAnalyzer {
    
    private static final Pattern URL_PATTERN = Pattern.compile(
        "https?://[\\w\\-]+(\\.[\\w\\-]+)+[\\w\\-\\.,@?^=%&:/~\\+#]*"
    );
    
    private static final Pattern KEYWORD_PATTERN = Pattern.compile(
        "\\b(重要|关键|核心|主要|显著|突出|特别|非常重要|至关重要|关键点|核心是|主要体现)\\b"
    );
    
    private final IntelligentSearchConfig config;
    private final LLMService llmService;
    private final LLMConfig llmConfig;
    
    /**
     * 构造函数
     */
    public SearchResultAnalyzer(IntelligentSearchConfig config, LLMService llmService) {
        this.config = config;
        this.llmService = llmService;
        this.llmConfig = createLLMConfig();
    }
    
    /**
     * 创建LLM配置
     */
    private LLMConfig createLLMConfig() {
        LLMConfig cfg = new LLMConfig();
        cfg.setModel(config.getModel());
        cfg.setTemperature(0.3); // 降低温度以获得更一致的分析结果
        cfg.setMaxTokens(2048);
        cfg.setStream(false);
        return cfg;
    }
    
    /**
     * 分析搜索结果并生成洞察
     */
    public SearchResultAnalysis analyzeSearchResults(IntelligentSearchTask task, List<SearchRound> searchRounds) {
        long analysisStartTime = System.currentTimeMillis();
        
        try {
            LogManager.getInstance().system("Starting search result analysis for " + searchRounds.size() + " rounds");
            
            // 1. 解析和提取原始信息
            ExtractedInformation extractedInfo = extractInformationFromRounds(searchRounds);
            
            // 2. 生成综合分析提示词
            String analysisPrompt = generateComprehensiveAnalysisPrompt(task, extractedInfo);
            
            // 3. 执行深度分析
            ComprehensiveAnalysis comprehensiveAnalysis = performDeepAnalysis(analysisPrompt);
            
            // 4. 提取结构化洞察
            SearchInsights insights = extractStructuredInsights(comprehensiveAnalysis, analysisStartTime);
            
            // 5. 生成最终报告
            String finalReport = generateFinalReport(task, extractedInfo, comprehensiveAnalysis, insights);
            
            long totalAnalysisTime = System.currentTimeMillis() - analysisStartTime;
            
            return SearchResultAnalysis.success(
                extractedInfo,
                comprehensiveAnalysis,
                insights,
                finalReport,
                totalAnalysisTime,
                createAnalysisMetadata(task, searchRounds, extractedInfo)
            );
            
        } catch (Exception e) {
            LogManager.getInstance().error("Error during search result analysis", e);
            
            // 创建基础分析结果（当深度分析失败时）
            ExtractedInformation basicInfo = extractInformationFromRounds(searchRounds);
            SearchInsights basicInsights = createBasicInsights(basicInfo);
            String basicReport = generateBasicReport(task, searchRounds, basicInfo);
            
            long totalAnalysisTime = System.currentTimeMillis() - analysisStartTime;
            
            return SearchResultAnalysis.success(
                basicInfo,
                null,
                basicInsights,
                basicReport,
                totalAnalysisTime,
                createAnalysisMetadata(task, searchRounds, basicInfo)
            );
        }
    }
    
    /**
     * 从搜索轮次中提取信息
     */
    private ExtractedInformation extractInformationFromRounds(List<SearchRound> searchRounds) {
        ExtractedInformation.Builder builder = ExtractedInformation.builder();
        
        // 按轮次处理搜索结果
        for (SearchRound round : searchRounds) {
            if (round.isSuccessful() && round.getRawResult() != null) {
                builder.addSearchResult(round.getRoundNumber(), round.getQuery(), round.getRawResult());
                
                // 提取URL
                Set<String> urls = extractUrls(round.getRawResult());
                for (String url : urls) {
                    builder.addSource(url);
                }
                
                // 提取关键词
                Set<String> keywords = extractKeywords(round.getRawResult());
                for (String keyword : keywords) {
                    builder.addKeyword(keyword);
                }
                
                // 分析内容质量
                ContentQuality quality = assessContentQuality(round.getRawResult());
                builder.addContentQuality(round.getRoundNumber(), quality);
            }
        }
        
        // 生成搜索统计
        SearchStatistics stats = generateSearchStatistics(searchRounds);
        builder.searchStatistics(stats);
        
        return builder.build();
    }
    
    /**
     * 提取URL
     */
    private Set<String> extractUrls(String content) {
        Set<String> urls = new LinkedHashSet<>();
        if (content == null || content.trim().isEmpty()) {
            return urls;
        }
        
        Matcher matcher = URL_PATTERN.matcher(content);
        while (matcher.find()) {
            String url = matcher.group();
            if (url.length() < 500) { // 避免过长的URL
                urls.add(url);
            }
        }
        
        return urls;
    }
    
    /**
     * 提取关键词
     */
    private Set<String> extractKeywords(String content) {
        Set<String> keywords = new LinkedHashSet<>();
        if (content == null || content.trim().isEmpty()) {
            return keywords;
        }
        
        // 使用关键词模式查找重要信息
        Matcher matcher = KEYWORD_PATTERN.matcher(content);
        while (matcher.find()) {
            // 提取关键词后面的内容
            int start = matcher.end();
            int end = Math.min(start + 100, content.length());
            String context = content.substring(start, end).trim();
            
            // 提取第一个句子或短语
            String[] sentences = context.split("[。！？.!?]");
            if (sentences.length > 0 && sentences[0].trim().length() > 0) {
                keywords.add(sentences[0].trim());
            }
        }
        
        return keywords;
    }
    
    /**
     * 评估内容质量
     */
    private ContentQuality assessContentQuality(String content) {
        if (content == null || content.trim().isEmpty()) {
            return ContentQuality.POOR;
        }
        
        int length = content.length();
        int sentenceCount = content.split("[。！？.!?]").length;
        double avgSentenceLength = length / Math.max(sentenceCount, 1);
        
        // 基于长度、句子数量、平均句子长度评估质量
        if (length < 100) {
            return ContentQuality.POOR;
        } else if (length < 500 || sentenceCount < 3) {
            return ContentQuality.FAIR;
        } else if (length < 1000 || avgSentenceLength < 20) {
            return ContentQuality.GOOD;
        } else {
            return ContentQuality.EXCELLENT;
        }
    }
    
    /**
     * 生成搜索统计
     */
    private SearchStatistics generateSearchStatistics(List<SearchRound> searchRounds) {
        int totalRounds = searchRounds.size();
        int successfulRounds = (int) searchRounds.stream().filter(SearchRound::isSuccessful).count();
        int totalResults = searchRounds.stream()
            .filter(SearchRound::isSuccessful)
            .mapToInt(SearchRound::getResultCount)
            .sum();
        long totalSearchTime = searchRounds.stream()
            .mapToLong(SearchRound::getProcessingTimeMs)
            .sum();
        
        // 计算信息丰富度
        double informationRichness = calculateInformationRichness(searchRounds);
        
        return new SearchStatistics(totalRounds, successfulRounds, totalResults, 
                                 totalSearchTime, informationRichness);
    }
    
    /**
     * 计算信息丰富度
     */
    private double calculateInformationRichness(List<SearchRound> searchRounds) {
        if (searchRounds.isEmpty()) {
            return 0.0;
        }
        
        double totalScore = 0.0;
        int count = 0;
        
        for (SearchRound round : searchRounds) {
            if (round.isSuccessful()) {
                String content = round.getRawResult();
                if (content != null) {
                    // 基于内容长度、结构、多样性计算丰富度
                    double lengthScore = Math.min(content.length() / 1000.0, 1.0);
                    double structureScore = content.contains("\n") ? 0.3 : 0.1;
                    double diversityScore = calculateContentDiversity(content);
                    
                    totalScore += (lengthScore + structureScore + diversityScore) / 3.0;
                    count++;
                }
            }
        }
        
        return count > 0 ? totalScore / count : 0.0;
    }
    
    /**
     * 计算内容多样性
     */
    private double calculateContentDiversity(String content) {
        // 简单的词汇多样性计算
        String[] words = content.toLowerCase().split("[\\s\\p{Punct}]+");
        Set<String> uniqueWords = new HashSet<>(Arrays.asList(words));
        
        return words.length > 0 ? (double) uniqueWords.size() / words.length : 0.0;
    }
    
    /**
     * 生成综合分析提示词
     */
    private String generateComprehensiveAnalysisPrompt(IntelligentSearchTask task, ExtractedInformation extractedInfo) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("你是一个专业的信息分析师。请对以下搜索结果进行深度综合分析：\n\n");
        
        prompt.append("=== 原始查询 ===\n");
        prompt.append(task.getOriginalQuery()).append("\n\n");
        
        prompt.append("=== 搜索策略 ===\n");
        prompt.append(task.getStrategy().getDisplayName()).append("\n\n");
        
        prompt.append("=== 搜索轮次统计 ===\n");
        SearchStatistics stats = extractedInfo.getSearchStatistics();
        prompt.append(String.format("总轮数: %d, 成功轮数: %d, 总结果数: %d, 信息丰富度: %.2f\n",
                                  stats.getTotalRounds(), stats.getSuccessfulRounds(), 
                                  stats.getTotalResults(), stats.getInformationRichness()));
        prompt.append("\n");
        
        prompt.append("=== 搜索结果摘要 ===\n");
        for (ExtractedInformation.SearchResult result : extractedInfo.getSearchResults()) {
            prompt.append(String.format("第%d轮查询: %s\n", result.getRoundNumber(), result.getQuery()));
            prompt.append(String.format("内容长度: %d字符, 质量评估: %s\n", 
                                      result.getContentLength(), result.getQuality()));
            prompt.append("---\n");
        }
        prompt.append("\n");
        
        prompt.append("=== 关键信息 ===\n");
        if (!extractedInfo.getKeywords().isEmpty()) {
            prompt.append("重要关键词:\n");
            for (String keyword : extractedInfo.getKeywords()) {
                prompt.append("• ").append(keyword).append("\n");
            }
            prompt.append("\n");
        }
        
        if (!extractedInfo.getSources().isEmpty()) {
            prompt.append("信息来源:\n");
            for (String source : extractedInfo.getSources()) {
                prompt.append("- ").append(source).append("\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("=== 分析要求 ===\n");
        prompt.append("请基于以上信息提供:\n");
        prompt.append("1. 关键发现：从搜索结果中提取的最重要的3-5个发现\n");
        prompt.append("2. 趋势分析：识别出的趋势、模式或发展方向\n");
        prompt.append("3. 深度洞察：基于搜索结果的深度分析和独特见解\n");
        prompt.append("4. 实用建议：基于分析结果的具体建议\n");
        prompt.append("5. 信息可靠性评估：对信息来源和内容可靠性的评估\n");
        prompt.append("6. 置信度评估：对分析结果的置信度（高/中/低）及原因\n\n");
        
        prompt.append("请以结构化的JSON格式回复，包含以下字段:\n");
        prompt.append("{\n");
        prompt.append("  \"keyFindings\": [\"发现1\", \"发现2\", ...],\n");
        prompt.append("  \"trends\": [\"趋势1\", \"趋势2\", ...],\n");
        prompt.append("  \"deepInsights\": [\"洞察1\", \"洞察2\", ...],\n");
        prompt.append("  \"recommendations\": [\"建议1\", \"建议2\", ...],\n");
        prompt.append("  \"reliabilityAssessment\": \"可靠性评估\",\n");
        prompt.append("  \"confidenceLevel\": \"高/中/低\",\n");
        prompt.append("  \"confidenceReason\": \"置信度原因\"\n");
        prompt.append("}\n");
        
        return prompt.toString();
    }
    
    /**
     * 执行深度分析
     */
    private ComprehensiveAnalysis performDeepAnalysis(String analysisPrompt) throws Exception {
        // 创建分析消息
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(new LLMMessage(LLMMessage.MessageRole.SYSTEM, 
            "你是一个专业的信息分析师，擅长从搜索结果中提取关键信息并进行深度分析。"));
        messages.add(new LLMMessage(LLMMessage.MessageRole.USER, analysisPrompt));
        
        // 执行分析
        CompletableFuture<LLMResponse> future = llmService.chat(messages, llmConfig);
        LLMResponse response = future.get(config.getEffectiveRoundTimeoutMs(), TimeUnit.MILLISECONDS);
        
        if (response.isSuccess()) {
            String analysisContent = response.getContent();
            return parseComprehensiveAnalysis(analysisContent);
        } else {
            throw new RuntimeException("深度分析失败: " + response.getError());
        }
    }
    
    /**
     * 解析综合分析结果
     */
    private ComprehensiveAnalysis parseComprehensiveAnalysis(String analysisContent) {
        // 这里简化处理，实际应该解析JSON格式的响应
        // 由于LLM可能不会严格返回JSON，这里使用文本解析
        
        ComprehensiveAnalysis.Builder builder = ComprehensiveAnalysis.builder();
        
        // 简单的文本解析逻辑
        String[] lines = analysisContent.split("\n");
        String currentSection = null;
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            // 识别不同部分
            if (line.contains("关键发现") || line.contains("keyFindings")) {
                currentSection = "findings";
            } else if (line.contains("趋势") || line.contains("trends")) {
                currentSection = "trends";
            } else if (line.contains("洞察") || line.contains("insights")) {
                currentSection = "insights";
            } else if (line.contains("建议") || line.contains("recommendations")) {
                currentSection = "recommendations";
            } else if (line.contains("可靠性") || line.contains("reliability")) {
                currentSection = "reliability";
            } else if (line.contains("置信度") || line.contains("confidence")) {
                currentSection = "confidence";
            } else if (currentSection != null) {
                // 添加到对应部分
                switch (currentSection) {
                    case "findings":
                        builder.keyFinding(cleanListItem(line));
                        break;
                    case "trends":
                        builder.trend(cleanListItem(line));
                        break;
                    case "insights":
                        builder.deepInsight(cleanListItem(line));
                        break;
                    case "recommendations":
                        builder.recommendation(cleanListItem(line));
                        break;
                    case "reliability":
                        builder.reliabilityAssessment(line);
                        break;
                    case "confidence":
                        if (line.contains("高")) {
                            builder.confidenceLevel("高");
                        } else if (line.contains("中")) {
                            builder.confidenceLevel("中");
                        } else if (line.contains("低")) {
                            builder.confidenceLevel("低");
                        }
                        builder.confidenceReason(line);
                        break;
                }
            }
        }
        
        return builder.build();
    }
    
    /**
     * 清理列表项格式
     */
    private String cleanListItem(String item) {
        // 移除列表标记（如 • - 1. 2. 等）
        return item.replaceAll("^[•\\-\\*\\d\\.\\s]+", "").trim();
    }
    
    /**
     * 提取结构化洞察
     */
    private SearchInsights extractStructuredInsights(ComprehensiveAnalysis analysis, long analysisStartTime) {
        long analysisTime = System.currentTimeMillis() - analysisStartTime;
        
        SearchInsights.Builder builder = SearchInsights.builder()
            .analysisTimeMs(analysisTime)
            .analysisMethod("LLM深度综合分析")
            .confidenceLevel(analysis.getConfidenceLevel() != null ? analysis.getConfidenceLevel() : "中");
        
        // 添加关键发现
        for (String finding : analysis.getKeyFindings()) {
            builder.keyFinding(finding);
        }
        
        // 添加趋势
        for (String trend : analysis.getTrends()) {
            builder.trend(trend);
        }
        
        // 添加建议
        for (String recommendation : analysis.getRecommendations()) {
            builder.recommendation(recommendation);
        }
        
        // 添加可靠性评估作为来源
        if (analysis.getReliabilityAssessment() != null) {
            builder.source("可靠性评估: " + analysis.getReliabilityAssessment());
        }
        
        return builder.build();
    }
    
    /**
     * 生成最终报告
     */
    private String generateFinalReport(IntelligentSearchTask task, ExtractedInformation extractedInfo, 
                                     ComprehensiveAnalysis analysis, SearchInsights insights) {
        StringBuilder report = new StringBuilder();
        
        report.append("=== 智能搜索分析报告 ===\n\n");
        
        // 基本信息部分
        report.append("【基本信息】\n");
        report.append("原始查询: ").append(task.getOriginalQuery()).append("\n");
        report.append("搜索策略: ").append(task.getStrategy().getDisplayName()).append("\n");
        
        SearchStatistics stats = extractedInfo.getSearchStatistics();
        report.append(String.format("搜索统计: 共%d轮搜索，成功%d轮，获得%d个结果\n",
                                  stats.getTotalRounds(), stats.getSuccessfulRounds(), stats.getTotalResults()));
        report.append(String.format("信息丰富度: %.2f/1.0\n", stats.getInformationRichness()));
        report.append("\n");
        
        // 关键发现部分
        if (insights.hasKeyFindings()) {
            report.append("【关键发现】\n");
            for (int i = 0; i < insights.getKeyFindings().size(); i++) {
                report.append(String.format("%d. %s\n", i + 1, insights.getKeyFindings().get(i)));
            }
            report.append("\n");
        }
        
        // 趋势分析部分
        if (insights.hasTrends()) {
            report.append("【趋势分析】\n");
            for (String trend : insights.getTrends()) {
                report.append("• ").append(trend).append("\n");
            }
            report.append("\n");
        }
        
        // 深度洞察部分
        if (!analysis.getDeepInsights().isEmpty()) {
            report.append("【深度洞察】\n");
            for (String insight : analysis.getDeepInsights()) {
                report.append("→ ").append(insight).append("\n");
            }
            report.append("\n");
        }
        
        // 建议部分
        if (insights.hasRecommendations()) {
            report.append("【实用建议】\n");
            for (String recommendation : insights.getRecommendations()) {
                report.append("✓ ").append(recommendation).append("\n");
            }
            report.append("\n");
        }
        
        // 可靠性评估
        if (analysis.getReliabilityAssessment() != null) {
            report.append("【可靠性评估】\n");
            report.append(analysis.getReliabilityAssessment()).append("\n\n");
        }
        
        // 置信度信息
        report.append("【分析置信度】\n");
        report.append("置信度: ").append(insights.getConfidenceLevel()).append("\n");
        if (analysis.getConfidenceReason() != null) {
            report.append("原因: ").append(analysis.getConfidenceReason()).append("\n");
        }
        report.append("\n");
        
        // 分析元数据
        report.append("【分析元数据】\n");
        report.append(String.format("分析方法: %s\n", insights.getAnalysisMethod()));
        report.append(String.format("分析耗时: %dms\n", insights.getAnalysisTimeMs()));
        report.append(String.format("分析深度: %.1f\n", config.getAnalysisDepth()));
        
        return report.toString();
    }
    
    /**
     * 创建基础洞察（当深度分析失败时）
     */
    private SearchInsights createBasicInsights(ExtractedInformation extractedInfo) {
        SearchInsights.Builder builder = SearchInsights.builder()
            .analysisTimeMs(0)
            .analysisMethod("基础信息提取")
            .confidenceLevel("低");
        
        // 添加关键词作为关键发现
        for (String keyword : extractedInfo.getKeywords()) {
            builder.keyFinding(keyword);
        }
        
        // 添加基本信息
        SearchStatistics stats = extractedInfo.getSearchStatistics();
        builder.recommendation(String.format("基于%d轮搜索结果，建议进一步深入研究", stats.getTotalRounds()));
        
        return builder.build();
    }
    
    /**
     * 生成基础报告（当深度分析失败时）
     */
    private String generateBasicReport(IntelligentSearchTask task, List<SearchRound> searchRounds, 
                                      ExtractedInformation extractedInfo) {
        StringBuilder report = new StringBuilder();
        
        report.append("=== 搜索结果基础报告 ===\n\n");
        report.append("原始查询: ").append(task.getOriginalQuery()).append("\n");
        report.append("搜索策略: ").append(task.getStrategy().getDisplayName()).append("\n\n");
        
        report.append("搜索轮次:\n");
        for (SearchRound round : searchRounds) {
            if (round.isSuccessful()) {
                report.append(String.format("第%d轮: %s (%d个结果)\n", 
                                          round.getRoundNumber(), round.getQuery(), round.getResultCount()));
            }
        }
        
        report.append("\n关键词信息:\n");
        for (String keyword : extractedInfo.getKeywords()) {
            report.append("• ").append(keyword).append("\n");
        }
        
        report.append("\n注意: 深度分析失败，此报告仅包含基础信息提取结果。");
        
        return report.toString();
    }
    
    /**
     * 创建分析元数据
     */
    private Map<String, Object> createAnalysisMetadata(IntelligentSearchTask task, List<SearchRound> searchRounds, 
                                                     ExtractedInformation extractedInfo) {
        Map<String, Object> metadata = new HashMap<>();
        
        metadata.put("analysisDepth", config.getAnalysisDepth());
        metadata.put("analysisMethod", "深度综合分析");
        metadata.put("enableDeepAnalysis", config.isEnableDeepAnalysis());
        metadata.put("searchStrategy", task.getStrategy().name());
        
        SearchStatistics stats = extractedInfo.getSearchStatistics();
        metadata.put("informationRichness", stats.getInformationRichness());
        metadata.put("totalRounds", stats.getTotalRounds());
        metadata.put("successfulRounds", stats.getSuccessfulRounds());
        metadata.put("totalResults", stats.getTotalResults());
        metadata.put("uniqueKeywords", extractedInfo.getKeywords().size());
        metadata.put("uniqueSources", extractedInfo.getSources().size());
        
        return metadata;
    }
}