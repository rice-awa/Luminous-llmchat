package com.riceawa.llm.template;

import com.google.gson.annotations.SerializedName;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示词模板
 */
public class PromptTemplate {
    @SerializedName("id")
    private String id;
    
    @SerializedName("name")
    private String name;
    
    @SerializedName("description")
    private String description;
    
    @SerializedName("system_prompt")
    private String systemPrompt;
    
    @SerializedName("user_prompt_prefix")
    private String userPromptPrefix;
    
    @SerializedName("user_prompt_suffix")
    private String userPromptSuffix;
    
    @SerializedName("variables")
    private Map<String, String> variables;
    
    @SerializedName("enabled")
    private boolean enabled;

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    public PromptTemplate() {
        this.variables = new HashMap<>();
        this.enabled = true;
    }

    public PromptTemplate(String id, String name, String description, String systemPrompt) {
        this();
        this.id = id;
        this.name = name;
        this.description = description;
        this.systemPrompt = systemPrompt;
    }

    /**
     * 渲染系统提示词
     */
    public String renderSystemPrompt() {
        return renderTemplate(systemPrompt);
    }

    /**
     * 渲染用户消息
     */
    public String renderUserMessage(String userInput) {
        StringBuilder result = new StringBuilder();
        
        if (userPromptPrefix != null && !userPromptPrefix.isEmpty()) {
            result.append(renderTemplate(userPromptPrefix));
        }
        
        result.append(userInput);
        
        if (userPromptSuffix != null && !userPromptSuffix.isEmpty()) {
            result.append(renderTemplate(userPromptSuffix));
        }
        
        return result.toString();
    }

    /**
     * 渲染模板，替换变量
     */
    private String renderTemplate(String template) {
        if (template == null) {
            return "";
        }

        String result = template;
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        
        while (matcher.find()) {
            String variableName = matcher.group(1);
            String variableValue = variables.getOrDefault(variableName, "");
            result = result.replace("{{" + variableName + "}}", variableValue);
        }
        
        return result;
    }

    /**
     * 设置变量
     */
    public void setVariable(String name, String value) {
        variables.put(name, value);
    }

    /**
     * 获取变量
     */
    public String getVariable(String name) {
        return variables.get(name);
    }

    /**
     * 移除变量
     */
    public void removeVariable(String name) {
        variables.remove(name);
    }

    /**
     * 清空所有变量
     */
    public void clearVariables() {
        variables.clear();
    }

    /**
     * 复制模板
     */
    public PromptTemplate copy() {
        PromptTemplate copy = new PromptTemplate();
        copy.id = this.id;
        copy.name = this.name;
        copy.description = this.description;
        copy.systemPrompt = this.systemPrompt;
        copy.userPromptPrefix = this.userPromptPrefix;
        copy.userPromptSuffix = this.userPromptSuffix;
        copy.variables = new HashMap<>(this.variables);
        copy.enabled = this.enabled;
        return copy;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getUserPromptPrefix() {
        return userPromptPrefix;
    }

    public void setUserPromptPrefix(String userPromptPrefix) {
        this.userPromptPrefix = userPromptPrefix;
    }

    public String getUserPromptSuffix() {
        return userPromptSuffix;
    }

    public void setUserPromptSuffix(String userPromptSuffix) {
        this.userPromptSuffix = userPromptSuffix;
    }

    public Map<String, String> getVariables() {
        return new HashMap<>(variables);
    }

    public void setVariables(Map<String, String> variables) {
        this.variables = new HashMap<>(variables);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
