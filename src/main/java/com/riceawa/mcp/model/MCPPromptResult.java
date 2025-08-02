package com.riceawa.mcp.model;

import com.riceawa.mcp.exception.MCPException;
import com.riceawa.mcp.util.MCPJsonUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP提示词执行结果模型
 * 表示MCP提示词调用的返回结果
 */
public class MCPPromptResult {
    // 结果描述
    private String description;
    
    // 消息列表
    private List<MCPMessage> messages = new ArrayList<>();
    
    // 提示词名称
    private String promptName;
    
    // 客户端名称
    private String clientName;

    public MCPPromptResult() {
    }

    public MCPPromptResult(String description, List<MCPMessage> messages) {
        this.description = description;
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
    }

    /**
     * 创建简单的提示词结果
     */
    public static MCPPromptResult simple(String description, String content) {
        MCPMessage message = new MCPMessage();
        message.setRole("user");
        message.setContent(MCPContent.text(content));
        
        List<MCPMessage> messages = new ArrayList<>();
        messages.add(message);
        
        return new MCPPromptResult(description, messages);
    }

    // Getters and Setters
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<MCPMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    public void setMessages(List<MCPMessage> messages) {
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
    }

    public void addMessage(MCPMessage message) {
        if (message != null) {
            this.messages.add(message);
        }
    }

    public String getPromptName() {
        return promptName;
    }

    public void setPromptName(String promptName) {
        this.promptName = promptName;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    /**
     * 获取所有消息的文本内容
     */
    public String getAllTextContent() {
        StringBuilder sb = new StringBuilder();
        for (MCPMessage message : messages) {
            if (message.getContent() != null && message.getContent().isText()) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(message.getContent().getText());
            }
        }
        return sb.toString();
    }

    /**
     * 检查是否有消息
     */
    public boolean hasMessages() {
        return !messages.isEmpty();
    }

    /**
     * 获取消息数量
     */
    public int getMessageCount() {
        return messages.size();
    }

    /**
     * 按角色获取消息
     */
    public List<MCPMessage> getMessagesByRole(String role) {
        return messages.stream()
                .filter(m -> role.equals(m.getRole()))
                .toList();
    }

    @Override
    public String toString() {
        return String.format("MCPPromptResult{description='%s', messageCount=%d}", 
                           description, messages.size());
    }

    // ==================== JSON序列化支持 ====================

    /**
     * 将当前对象序列化为JSON字符串
     */
    public String toJson() throws MCPException {
        return MCPJsonUtils.toJson(this);
    }

    /**
     * 从JSON字符串创建MCPPromptResult对象
     */
    public static MCPPromptResult fromJson(String json) throws MCPException {
        return MCPJsonUtils.fromJsonToPromptResult(json);
    }

    /**
     * 创建当前对象的深度拷贝
     */
    public MCPPromptResult deepCopy() throws MCPException {
        return fromJson(toJson());
    }
}