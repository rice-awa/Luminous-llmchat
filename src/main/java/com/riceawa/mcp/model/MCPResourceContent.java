package com.riceawa.mcp.model;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP资源内容模型
 * 表示从MCP服务器获取的资源内容
 */
public class MCPResourceContent {
    // 资源URI
    private String uri;
    
    // 文本内容
    private String text;
    
    // 二进制数据（base64编码）
    private String data;
    
    // MIME类型
    private String mimeType;
    
    // 内容注解信息
    private Map<String, Object> annotations = new HashMap<>();
    
    // 内容大小（字节）
    private long size = -1;
    
    // 最后修改时间
    private long lastModified = -1;

    public MCPResourceContent() {
    }

    public MCPResourceContent(String uri, String text) {
        this.uri = uri;
        this.text = text;
    }

    /**
     * 创建文本资源内容
     */
    public static MCPResourceContent text(String uri, String text) {
        MCPResourceContent content = new MCPResourceContent(uri, text);
        content.mimeType = "text/plain";
        return content;
    }

    /**
     * 创建二进制资源内容
     */
    public static MCPResourceContent binary(String uri, String data, String mimeType) {
        MCPResourceContent content = new MCPResourceContent();
        content.uri = uri;
        content.data = data;
        content.mimeType = mimeType;
        return content;
    }

    // Getters and Setters
    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Map<String, Object> getAnnotations() {
        return new HashMap<>(annotations);
    }

    public void setAnnotations(Map<String, Object> annotations) {
        this.annotations = annotations != null ? new HashMap<>(annotations) : new HashMap<>();
    }

    public void addAnnotation(String key, Object value) {
        if (key != null) {
            this.annotations.put(key, value);
        }
    }

    public Object getAnnotation(String key) {
        return annotations.get(key);
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    /**
     * 检查是否为文本内容
     */
    public boolean isTextContent() {
        return text != null;
    }

    /**
     * 检查是否为二进制内容
     */
    public boolean isBinaryContent() {
        return data != null;
    }

    /**
     * 检查内容是否有效
     */
    public boolean isValid() {
        return uri != null && !uri.trim().isEmpty() && (text != null || data != null);
    }

    /**
     * 获取内容的字符串表示
     */
    public String getContentString() {
        if (isTextContent()) {
            return text;
        } else if (isBinaryContent()) {
            return String.format("[Binary content: %s, size: %d bytes]", 
                               mimeType != null ? mimeType : "unknown", 
                               size > 0 ? size : data.length());
        } else {
            return "[Empty content]";
        }
    }

    /**
     * 获取内容长度
     */
    public int getContentLength() {
        if (isTextContent()) {
            return text.length();
        } else if (isBinaryContent()) {
            return data.length();
        } else {
            return 0;
        }
    }

    /**
     * 检查是否为空内容
     */
    public boolean isEmpty() {
        return !isTextContent() && !isBinaryContent();
    }

    @Override
    public String toString() {
        return String.format("MCPResourceContent{uri='%s', hasText=%s, hasData=%s, mimeType='%s'}", 
                           uri, text != null, data != null, mimeType);
    }
}