package com.riceawa.llm.config;

import com.riceawa.llm.subagent.search.SearchStrategy;
import java.util.HashMap;
import java.util.Map;

/**
 * 搜索子代理提示词生成器
 * 根据IntelligentSearchConfig动态生成系统提示词
 */
public class SearchPromptGenerator implements PromptGenerator<IntelligentSearchConfig> {
    
    @Override
    public String generateSystemPrompt(IntelligentSearchConfig config) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个专业的智能搜索分析助手。你的任务是进行多轮搜索并提供深度分析。\n\n");
        
        // 添加搜索轮数信息
        int effectiveRounds = config.getEffectiveMaxSearchRounds();
        prompt.append(String.format("你最多可以进行%d次搜索来获得全面的信息。\n", effectiveRounds));
        
        // 添加搜索策略
        prompt.append(getStrategyPrompt(config.getStrategy())).append("\n");
        
        // 添加分析深度
        prompt.append(getAnalysisDepthPrompt(config.getAnalysisDepth())).append("\n");
        
        // 添加语言设置
        prompt.append(String.format("请使用%s语言进行回复和分析。\n", config.getLanguage()));
        
        // 添加约束
        prompt.append("\n重要约束：\n");
        prompt.append("- 你只能使用googleSearch工具进行搜索\n");
        prompt.append("- 每次搜索都要基于前一次的结果进行优化\n");
        prompt.append("- 避免重复搜索相同的内容\n");
        prompt.append("- 最终提供综合性的分析报告\n");
        
        return prompt.toString().trim();
    }
    
    @Override
    public Class<IntelligentSearchConfig> getSupportedConfigType() {
        return IntelligentSearchConfig.class;
    }
    
    @Override
    public String getGeneratorName() {
        return "SearchPromptGenerator";
    }
    
    /**
     * 根据搜索策略获取相应的提示词
     */
    private String getStrategyPrompt(SearchStrategy strategy) {
        switch (strategy) {
            case QUICK:
                return "采用快速搜索策略，重点关注最相关和最重要的信息。你需要在有限的搜索次数内获得核心信息。";
            case COMPREHENSIVE:
                return "采用全面搜索策略，从多个角度收集信息。你应该进行多轮搜索以获得完整的信息覆盖。";
            case DEEP_DIVE:
                return "采用深度搜索策略，进行详细的信息挖掘和分析。你应该从多个维度深入探索主题。";
            default:
                return "采用全面搜索策略，从多个角度收集信息。你应该进行多轮搜索以获得完整的信息覆盖。";
        }
    }
    
    /**
     * 根据分析深度获取相应的提示词
     */
    private String getAnalysisDepthPrompt(double analysisDepth) {
        if (analysisDepth <= 0.4) {
            return "请提供基础的信息总结和要点提取。";
        } else if (analysisDepth <= 0.7) {
            return "请提供中等深度的分析，包括关键洞察和趋势识别。";
        } else {
            return "请提供深度分析和洞察，包括详细的趋势分析、影响评估和专业建议。";
        }
    }
}