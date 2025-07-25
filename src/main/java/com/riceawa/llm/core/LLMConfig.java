package com.riceawa.llm.core;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * LLM请求配置
 */
public class LLMConfig {
    @SerializedName("model")
    private String model;
    
    @SerializedName("temperature")
    private Double temperature;
    
    @SerializedName("max_tokens")
    private Integer maxTokens;
    
    @SerializedName("top_p")
    private Double topP;
    
    @SerializedName("frequency_penalty")
    private Double frequencyPenalty;
    
    @SerializedName("presence_penalty")
    private Double presencePenalty;
    
    @SerializedName("stop")
    private List<String> stop;
    
    @SerializedName("stream")
    private Boolean stream;
    
    @SerializedName("functions")
    private List<FunctionDefinition> functions;
    
    @SerializedName("function_call")
    private String functionCall;

    public LLMConfig() {
        // 默认配置
        this.temperature = 0.7;
        this.maxTokens = 2048;
        this.stream = false;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public void setFrequencyPenalty(Double frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
    }

    public Double getPresencePenalty() {
        return presencePenalty;
    }

    public void setPresencePenalty(Double presencePenalty) {
        this.presencePenalty = presencePenalty;
    }

    public List<String> getStop() {
        return stop;
    }

    public void setStop(List<String> stop) {
        this.stop = stop;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }

    public List<FunctionDefinition> getFunctions() {
        return functions;
    }

    public void setFunctions(List<FunctionDefinition> functions) {
        this.functions = functions;
    }

    public String getFunctionCall() {
        return functionCall;
    }

    public void setFunctionCall(String functionCall) {
        this.functionCall = functionCall;
    }

    /**
     * Function定义
     */
    public static class FunctionDefinition {
        @SerializedName("name")
        private String name;
        
        @SerializedName("description")
        private String description;
        
        @SerializedName("parameters")
        private Object parameters;

        public FunctionDefinition() {}

        public FunctionDefinition(String name, String description, Object parameters) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
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

        public Object getParameters() {
            return parameters;
        }

        public void setParameters(Object parameters) {
            this.parameters = parameters;
        }
    }
}
