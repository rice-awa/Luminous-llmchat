package com.riceawa.mcp.model;

/**
 * MCP消息模型
 * 表示MCP提示词结果中的单个消息
 */
public class MCPMessage {
    // 消息角色：user, assistant, system
    private String role;
    
    // 消息内容
    private MCPContent content;

    public MCPMessage() {
    }

    public MCPMessage(String role, MCPContent content) {
        this.role = role;
        this.content = content;
    }

    /**
     * 创建用户消息
     */
    public static MCPMessage user(String text) {
        return new MCPMessage("user", MCPContent.text(text));
    }

    /**
     * 创建助手消息
     */
    public static MCPMessage assistant(String text) {
        return new MCPMessage("assistant", MCPContent.text(text));
    }

    /**
     * 创建系统消息
     */
    public static MCPMessage system(String text) {
        return new MCPMessage("system", MCPContent.text(text));
    }

    // Getters and Setters
    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public MCPContent getContent() {
        return content;
    }

    public void setContent(MCPContent content) {
        this.content = content;
    }

    /**
     * 检查是否为用户消息
     */
    public boolean isUserMessage() {
        return "user".equals(role);
    }

    /**
     * 检查是否为助手消息
     */
    public boolean isAssistantMessage() {
        return "assistant".equals(role);
    }

    /**
     * 检查是否为系统消息
     */
    public boolean isSystemMessage() {
        return "system".equals(role);
    }

    /**
     * 获取消息的文本内容
     */
    public String getTextContent() {
        return content != null && content.isText() ? content.getText() : "";
    }

    /**
     * 检查消息是否有效
     */
    public boolean isValid() {
        return role != null && !role.trim().isEmpty() && 
               content != null && content.isValid();
    }

    @Override
    public String toString() {
        return String.format("MCPMessage{role='%s', content='%s'}", 
                           role, content != null ? content.getContentString() : "null");
    }
}