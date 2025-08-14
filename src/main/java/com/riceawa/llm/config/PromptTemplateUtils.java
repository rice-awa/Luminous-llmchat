package com.riceawa.llm.config;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示词模板工具类
 * 提供模板变量替换和验证功能
 */
public class PromptTemplateUtils {
    
    // 模板变量的正则表达式 ${variableName}
    private static final Pattern TEMPLATE_VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    
    // 最大提示词长度
    private static final int MAX_PROMPT_LENGTH = 4000;
    
    // 最小提示词长度
    private static final int MIN_PROMPT_LENGTH = 10;
    
    /**
     * 替换模板中的变量
     * 
     * @param template 模板字符串
     * @param variables 变量映射
     * @return 替换后的字符串
     */
    public static String replaceVariables(String template, Map<String, String> variables) {
        if (template == null || template.trim().isEmpty()) {
            return template;
        }
        
        if (variables == null || variables.isEmpty()) {
            return template;
        }
        
        Matcher matcher = TEMPLATE_VARIABLE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String variableName = matcher.group(1);
            String replacement = variables.get(variableName);
            
            if (replacement != null) {
                // 转义特殊字符
                replacement = Matcher.quoteReplacement(replacement);
                matcher.appendReplacement(result, replacement);
            } else {
                // 如果没有找到对应的变量，保留原始模板变量
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    /**
     * 验证提示词模板
     * 
     * @param template 模板字符串
     * @return 验证结果
     */
    public static TemplateValidationResult validateTemplate(String template) {
        if (template == null) {
            return new TemplateValidationResult(false, "模板不能为null");
        }
        
        String trimmed = template.trim();
        if (trimmed.isEmpty()) {
            return new TemplateValidationResult(false, "模板不能为空");
        }
        
        if (trimmed.length() < MIN_PROMPT_LENGTH) {
            return new TemplateValidationResult(false, 
                String.format("模板长度不能少于%d个字符", MIN_PROMPT_LENGTH));
        }
        
        if (trimmed.length() > MAX_PROMPT_LENGTH) {
            return new TemplateValidationResult(false, 
                String.format("模板长度不能超过%d个字符", MAX_PROMPT_LENGTH));
        }
        
        // 检查模板变量语法
        Matcher matcher = TEMPLATE_VARIABLE_PATTERN.matcher(template);
        while (matcher.find()) {
            String variableName = matcher.group(1);
            if (variableName.trim().isEmpty()) {
                return new TemplateValidationResult(false, "发现空的模板变量名");
            }
            
            // 检查变量名是否包含非法字符
            if (!variableName.matches("[a-zA-Z0-9_]+")) {
                return new TemplateValidationResult(false, 
                    String.format("模板变量名'%s'包含非法字符，只允许字母、数字和下划线", variableName));
            }
        }
        
        return new TemplateValidationResult(true, "模板验证通过");
    }
    
    /**
     * 提取模板中的所有变量名
     * 
     * @param template 模板字符串
     * @return 变量名数组
     */
    public static String[] extractVariableNames(String template) {
        if (template == null || template.trim().isEmpty()) {
            return new String[0];
        }
        
        Matcher matcher = TEMPLATE_VARIABLE_PATTERN.matcher(template);
        java.util.List<String> variables = new java.util.ArrayList<>();
        
        while (matcher.find()) {
            String variableName = matcher.group(1);
            if (!variables.contains(variableName)) {
                variables.add(variableName);
            }
        }
        
        return variables.toArray(new String[0]);
    }
    
    /**
     * 检查模板是否包含指定的变量
     * 
     * @param template 模板字符串
     * @param variableName 变量名
     * @return 是否包含该变量
     */
    public static boolean containsVariable(String template, String variableName) {
        if (template == null || variableName == null) {
            return false;
        }
        
        String[] variables = extractVariableNames(template);
        for (String var : variables) {
            if (var.equals(variableName)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 创建标准的搜索提示词模板
     * 
     * @return 标准搜索模板
     */
    public static String createStandardSearchTemplate() {
        StringBuilder template = new StringBuilder();
        
        template.append("你是一个专业的智能搜索分析助手。你的任务是进行多轮搜索并提供深度分析。\n\n");
        template.append("搜索配置：\n");
        template.append("- 最大搜索轮数：${maxRounds}\n");
        template.append("- 搜索策略：${strategy}\n");
        template.append("- 分析深度：${analysisDepth}\n");
        template.append("- 语言设置：${language}\n\n");
        
        template.append("${strategyDescription}\n\n");
        template.append("${analysisDescription}\n\n");
        
        template.append("搜索流程：\n");
        template.append("1. 分析用户查询，制定搜索策略\n");
        template.append("2. 根据每轮搜索结果优化下一轮搜索关键词\n");
        template.append("3. 整合所有搜索信息，提供结构化分析\n");
        template.append("4. 在达到最大搜索次数前获得最全面的信息\n\n");
        
        template.append("重要约束：\n");
        template.append("- 你只能使用googleSearch工具进行搜索\n");
        template.append("- 每次搜索都要基于前一次的结果进行优化\n");
        template.append("- 避免重复搜索相同的内容\n");
        template.append("- 最终提供综合性的分析报告\n");
        
        return template.toString();
    }
    
    /**
     * 创建简化的搜索提示词模板
     * 
     * @return 简化搜索模板
     */
    public static String createSimpleSearchTemplate() {
        StringBuilder template = new StringBuilder();
        
        template.append("你是一个智能搜索助手。\n");
        template.append("最大搜索次数：${maxRounds}\n");
        template.append("搜索策略：${strategy}\n");
        template.append("请使用googleSearch工具进行搜索并提供分析。");
        
        return template.toString();
    }
    
    /**
     * 模板验证结果类
     */
    public static class TemplateValidationResult {
        private final boolean valid;
        private final String message;
        
        public TemplateValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getMessage() {
            return message;
        }
        
        @Override
        public String toString() {
            return String.format("TemplateValidationResult{valid=%s, message='%s'}", valid, message);
        }
    }
}