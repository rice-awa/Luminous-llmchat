package com.riceawa.mcp.model;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP资源模型
 * 表示从MCP服务器获取的资源信息
 */
public class MCPResource {
    // 资源URI
    private String uri;
    
    // 资源名称
    private String name;
    
    // 资源标题
    private String title;
    
    // 资源描述
    private String description;
    
    // MIME类型
    private String mimeType;
    
    // 所属客户端名称
    private String clientName;
    
    // 资源注解信息
    private Map<String, Object> annotations = new HashMap<>();

    public MCPResource() {
    }

    public MCPResource(String uri, String name) {
        this.uri = uri;
        this.name = name;
    }

    // Getters and Setters
    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
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
     * 获取显示名称
     */
    public String getDisplayName() {
        if (title != null && !title.trim().isEmpty()) {
            return title;
        }
        if (name != null && !name.trim().isEmpty()) {
            return name;
        }
        return uri;
    }

    /**
     * 检查资源是否有效
     */
    public boolean isValid() {
        return uri != null && !uri.trim().isEmpty();
    }

    /**
     * 检查是否为文本资源
     */
    public boolean isTextResource() {
        return mimeType != null && mimeType.startsWith("text/");
    }

    /**
     * 检查是否为图片资源
     */
    public boolean isImageResource() {
        return mimeType != null && mimeType.startsWith("image/");
    }

    /**
     * 检查是否为音频资源
     */
    public boolean isAudioResource() {
        return mimeType != null && mimeType.startsWith("audio/");
    }

    /**
     * 检查是否为视频资源
     */
    public boolean isVideoResource() {
        return mimeType != null && mimeType.startsWith("video/");
    }

    @Override
    public String toString() {
        return String.format("MCPResource{uri='%s', name='%s', client='%s'}", 
                           uri, name, clientName);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        MCPResource resource = (MCPResource) obj;
        return uri != null ? uri.equals(resource.uri) : resource.uri == null;
    }

    @Override
    public int hashCode() {
        return uri != null ? uri.hashCode() : 0;
    }
}