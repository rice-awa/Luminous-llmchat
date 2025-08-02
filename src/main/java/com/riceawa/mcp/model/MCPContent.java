package com.riceawa.mcp.model;

import com.riceawa.mcp.exception.MCPException;
import com.riceawa.mcp.util.MCPJsonUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP内容模型
 * 表示MCP协议中的内容项，支持多种内容类型
 */
public class MCPContent {
    // 内容类型：text, image, audio, resource_link, resource
    private String type;
    
    // 文本内容
    private String text;
    
    // 二进制数据（base64编码）
    private String data;
    
    // MIME类型
    private String mimeType;
    
    // 资源URI（用于resource_link和resource类型）
    private String uri;
    
    // 内容注解信息
    private Map<String, Object> annotations = new HashMap<>();

    public MCPContent() {
    }

    public MCPContent(String type, String text) {
        this.type = type;
        this.text = text;
    }

    /**
     * 创建文本内容
     */
    public static MCPContent text(String text) {
        return new MCPContent("text", text);
    }

    /**
     * 创建图片内容
     */
    public static MCPContent image(String data, String mimeType) {
        MCPContent content = new MCPContent();
        content.type = "image";
        content.data = data;
        content.mimeType = mimeType;
        return content;
    }

    /**
     * 创建资源链接内容
     */
    public static MCPContent resourceLink(String uri) {
        MCPContent content = new MCPContent();
        content.type = "resource_link";
        content.uri = uri;
        return content;
    }

    /**
     * 创建资源内容
     */
    public static MCPContent resource(String uri, String text) {
        MCPContent content = new MCPContent();
        content.type = "resource";
        content.uri = uri;
        content.text = text;
        return content;
    }

    // Getters and Setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
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

    /**
     * 检查是否为文本类型
     */
    public boolean isText() {
        return "text".equals(type);
    }

    /**
     * 检查是否为图片类型
     */
    public boolean isImage() {
        return "image".equals(type);
    }

    /**
     * 检查是否为音频类型
     */
    public boolean isAudio() {
        return "audio".equals(type);
    }

    /**
     * 检查是否为资源链接类型
     */
    public boolean isResourceLink() {
        return "resource_link".equals(type);
    }

    /**
     * 检查是否为资源类型
     */
    public boolean isResource() {
        return "resource".equals(type);
    }

    /**
     * 检查内容是否有效
     */
    public boolean isValid() {
        if (type == null || type.trim().isEmpty()) {
            return false;
        }
        
        switch (type) {
            case "text":
                return text != null;
            case "image":
            case "audio":
                return data != null && mimeType != null;
            case "resource_link":
            case "resource":
                return uri != null && !uri.trim().isEmpty();
            default:
                return true; // 允许未知类型
        }
    }

    /**
     * 获取内容的字符串表示
     */
    public String getContentString() {
        switch (type != null ? type : "") {
            case "text":
                return text != null ? text : "";
            case "resource":
                return text != null ? text : "";
            case "resource_link":
                return uri != null ? uri : "";
            case "image":
            case "audio":
                return String.format("[%s: %s]", type, mimeType != null ? mimeType : "unknown");
            default:
                return String.format("[%s content]", type != null ? type : "unknown");
        }
    }

    @Override
    public String toString() {
        return String.format("MCPContent{type='%s', hasText=%s, hasData=%s, uri='%s'}", 
                           type, text != null, data != null, uri);
    }

    // ==================== JSON序列化支持 ====================

    /**
     * 将当前对象序列化为JSON字符串
     */
    public String toJson() throws MCPException {
        return MCPJsonUtils.toJson(this);
    }

    /**
     * 从JSON字符串创建MCPContent对象
     */
    public static MCPContent fromJson(String json) throws MCPException {
        return MCPJsonUtils.fromJsonToContent(json);
    }

    /**
     * 创建当前对象的深度拷贝
     */
    public MCPContent deepCopy() throws MCPException {
        return fromJson(toJson());
    }
}