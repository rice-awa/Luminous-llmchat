package com.riceawa.llm.core;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * LLM API响应结果
 */
public class LLMResponse {
    @SerializedName("id")
    private String id;
    
    @SerializedName("model")
    private String model;
    
    @SerializedName("choices")
    private List<Choice> choices;
    
    @SerializedName("usage")
    private Usage usage;
    
    @SerializedName("error")
    private String error;

    public LLMResponse() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<Choice> getChoices() {
        return choices;
    }

    public void setChoices(List<Choice> choices) {
        this.choices = choices;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    /**
     * 获取第一个选择的消息内容
     */
    public String getContent() {
        if (choices != null && !choices.isEmpty()) {
            Choice firstChoice = choices.get(0);
            if (firstChoice.getMessage() != null) {
                return firstChoice.getMessage().getContent();
            }
        }
        return null;
    }

    /**
     * 检查响应是否成功
     */
    public boolean isSuccess() {
        return error == null && choices != null && !choices.isEmpty();
    }

    /**
     * 响应选择项
     */
    public static class Choice {
        @SerializedName("index")
        private int index;
        
        @SerializedName("message")
        private LLMMessage message;
        
        @SerializedName("finish_reason")
        private String finishReason;

        public Choice() {}

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public LLMMessage getMessage() {
            return message;
        }

        public void setMessage(LLMMessage message) {
            this.message = message;
        }

        public String getFinishReason() {
            return finishReason;
        }

        public void setFinishReason(String finishReason) {
            this.finishReason = finishReason;
        }
    }

    /**
     * Token使用统计
     */
    public static class Usage {
        @SerializedName("prompt_tokens")
        private int promptTokens;
        
        @SerializedName("completion_tokens")
        private int completionTokens;
        
        @SerializedName("total_tokens")
        private int totalTokens;

        public Usage() {}

        public int getPromptTokens() {
            return promptTokens;
        }

        public void setPromptTokens(int promptTokens) {
            this.promptTokens = promptTokens;
        }

        public int getCompletionTokens() {
            return completionTokens;
        }

        public void setCompletionTokens(int completionTokens) {
            this.completionTokens = completionTokens;
        }

        public int getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(int totalTokens) {
            this.totalTokens = totalTokens;
        }
    }
}
