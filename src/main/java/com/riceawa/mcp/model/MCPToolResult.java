package com.riceawa.mcp.model;

import com.riceawa.mcp.exception.MCPException;
import com.riceawa.mcp.util.MCPJsonUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP工具执行结果模型
 * 表示MCP工具调用的返回结果
 */
public class MCPToolResult {
    // 结果内容列表
    private List<MCPContent> content = new ArrayList<>();
    
    // 结构化内容（可选）
    private Object structuredContent;
    
    // 是否为错误结果
    private boolean isError = false;
    
    // 错误消息
    private String errorMessage;
    
    // 工具名称
    private String toolName;
    
    // 客户端名称
    private String clientName;

    public MCPToolResult() {
    }

    public MCPToolResult(List<MCPContent> content) {
        this.content = content != null ? new ArrayList<>(content) : new ArrayList<>();
    }

    /**
     * 创建成功结果
     */
    public static MCPToolResult success(List<MCPContent> content) {
        MCPToolResult result = new MCPToolResult(content);
        result.isError = false;
        return result;
    }

    /**
     * 创建错误结果
     */
    public static MCPToolResult error(String errorMessage) {
        MCPToolResult result = new MCPToolResult();
        result.isError = true;
        result.errorMessage = errorMessage;
        return result;
    }

    /**
     * 创建简单文本结果
     */
    public static MCPToolResult text(String text) {
        MCPContent content = new MCPContent();
        content.setType("text");
        content.setText(text);
        
        List<MCPContent> contentList = new ArrayList<>();
        contentList.add(content);
        
        return success(contentList);
    }

    // Getters and Setters
    public List<MCPContent> getContent() {
        return new ArrayList<>(content);
    }

    public void setContent(List<MCPContent> content) {
        this.content = content != null ? new ArrayList<>(content) : new ArrayList<>();
    }

    public void addContent(MCPContent content) {
        if (content != null) {
            this.content.add(content);
        }
    }

    public Object getStructuredContent() {
        return structuredContent;
    }

    public void setStructuredContent(Object structuredContent) {
        this.structuredContent = structuredContent;
    }

    public boolean isError() {
        return isError;
    }

    public void setError(boolean error) {
        isError = error;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        if (errorMessage != null) {
            this.isError = true;
        }
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    /**
     * 获取所有文本内容的合并结果
     */
    public String getTextContent() {
        StringBuilder sb = new StringBuilder();
        for (MCPContent c : content) {
            if ("text".equals(c.getType()) && c.getText() != null) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(c.getText());
            }
        }
        return sb.toString();
    }

    /**
     * 检查是否有内容
     */
    public boolean hasContent() {
        return !content.isEmpty() || structuredContent != null;
    }

    /**
     * 检查是否为成功结果
     */
    public boolean isSuccess() {
        return !isError;
    }

    @Override
    public String toString() {
        if (isError) {
            return String.format("MCPToolResult{error='%s'}", errorMessage);
        } else {
            return String.format("MCPToolResult{contentCount=%d, hasStructured=%s}", 
                               content.size(), structuredContent != null);
        }
    }

    // ==================== JSON序列化支持 ====================

    /**
     * 将当前对象序列化为JSON字符串
     */
    public String toJson() throws MCPException {
        return MCPJsonUtils.toJson(this);
    }

    /**
     * 从JSON字符串创建MCPToolResult对象
     */
    public static MCPToolResult fromJson(String json) throws MCPException {
        return MCPJsonUtils.fromJsonToToolResult(json);
    }

    /**
     * 创建当前对象的深度拷贝
     */
    public MCPToolResult deepCopy() throws MCPException {
        return fromJson(toJson());
    }
}