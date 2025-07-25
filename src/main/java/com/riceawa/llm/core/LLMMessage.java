package com.riceawa.llm.core;

import com.google.gson.annotations.SerializedName;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 表示LLM对话中的一条消息
 */
public class LLMMessage {
    @SerializedName("id")
    private final String id;
    
    @SerializedName("role")
    private final MessageRole role;
    
    @SerializedName("content")
    private final String content;
    
    @SerializedName("timestamp")
    private final LocalDateTime timestamp;
    
    @SerializedName("metadata")
    private MessageMetadata metadata;

    @SerializedName("name")
    private String name;

    @SerializedName("tool_call_id")
    private String toolCallId;

    public LLMMessage(MessageRole role, String content) {
        this(UUID.randomUUID().toString(), role, content, LocalDateTime.now(), new MessageMetadata());
    }

    public LLMMessage(String id, MessageRole role, String content, LocalDateTime timestamp, MessageMetadata metadata) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
        this.metadata = metadata;
    }

    public LLMMessage(String id, MessageRole role, String content, LocalDateTime timestamp, MessageMetadata metadata) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
        this.metadata = metadata;
    }

    public String getId() {
        return id;
    }

    public MessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public MessageMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(MessageMetadata metadata) {
        this.metadata = metadata;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    /**
     * 消息角色枚举
     */
    public enum MessageRole {
        @SerializedName("system")
        SYSTEM("system"),
        
        @SerializedName("user")
        USER("user"),
        
        @SerializedName("assistant")
        ASSISTANT("assistant"),
        
        @SerializedName("function")
        FUNCTION("function"),

        @SerializedName("tool")
        TOOL("tool");

        private final String value;

        MessageRole(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 消息元数据
     */
    public static class MessageMetadata {
        @SerializedName("tokens")
        private Integer tokens;
        
        @SerializedName("model")
        private String model;
        
        @SerializedName("function_call")
        private FunctionCall functionCall;

        public MessageMetadata() {}

        public Integer getTokens() {
            return tokens;
        }

        public void setTokens(Integer tokens) {
            this.tokens = tokens;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public FunctionCall getFunctionCall() {
            return functionCall;
        }

        public void setFunctionCall(FunctionCall functionCall) {
            this.functionCall = functionCall;
        }
    }

    /**
     * Function Call 数据结构
     */
    public static class FunctionCall {
        @SerializedName("name")
        private String name;

        @SerializedName("arguments")
        private String arguments;

        @SerializedName("tool_call_id")
        private String toolCallId;

        public FunctionCall() {}

        public FunctionCall(String name, String arguments) {
            this.name = name;
            this.arguments = arguments;
        }

        public FunctionCall(String name, String arguments, String toolCallId) {
            this.name = name;
            this.arguments = arguments;
            this.toolCallId = toolCallId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getArguments() {
            return arguments;
        }

        public void setArguments(String arguments) {
            this.arguments = arguments;
        }

        public String getToolCallId() {
            return toolCallId;
        }

        public void setToolCallId(String toolCallId) {
            this.toolCallId = toolCallId;
        }
    }
}
