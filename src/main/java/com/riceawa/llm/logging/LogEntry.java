package com.riceawa.llm.logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.HashMap;

/**
 * 日志条目数据类
 */
public class LogEntry {
    private final LocalDateTime timestamp;
    private final LogLevel level;
    private final String category;
    private final String message;
    private final String thread;
    private final Map<String, Object> metadata;
    private final Throwable throwable;

    private LogEntry(Builder builder) {
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
        this.level = builder.level;
        this.category = builder.category;
        this.message = builder.message;
        this.thread = builder.thread != null ? builder.thread : Thread.currentThread().getName();
        this.metadata = new HashMap<>(builder.metadata);
        this.throwable = builder.throwable;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public LogLevel getLevel() {
        return level;
    }

    public String getCategory() {
        return category;
    }

    public String getMessage() {
        return message;
    }

    public String getThread() {
        return thread;
    }

    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }

    public Throwable getThrowable() {
        return throwable;
    }

    /**
     * 格式化为可读的字符串
     */
    public String toFormattedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))).append("] ");
        sb.append("[").append(level.getName()).append("] ");
        sb.append("[").append(category).append("] ");
        sb.append("[").append(thread).append("] ");
        sb.append(message);
        
        if (!metadata.isEmpty()) {
            sb.append(" | Metadata: ").append(metadata);
        }
        
        if (throwable != null) {
            sb.append("\n").append(getStackTrace(throwable));
        }
        
        return sb.toString();
    }

    /**
     * 格式化为JSON字符串
     */
    public String toJsonString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"timestamp\":\"").append(timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\",");
        sb.append("\"level\":\"").append(level.getName()).append("\",");
        sb.append("\"category\":\"").append(escapeJson(category)).append("\",");
        sb.append("\"message\":\"").append(escapeJson(message)).append("\",");
        sb.append("\"thread\":\"").append(escapeJson(thread)).append("\"");
        
        if (!metadata.isEmpty()) {
            sb.append(",\"metadata\":{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escapeJson(entry.getKey())).append("\":\"")
                  .append(escapeJson(String.valueOf(entry.getValue()))).append("\"");
                first = false;
            }
            sb.append("}");
        }
        
        if (throwable != null) {
            sb.append(",\"exception\":\"").append(escapeJson(getStackTrace(throwable))).append("\"");
        }
        
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    private String getStackTrace(Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.getClass().getName()).append(": ").append(t.getMessage()).append("\n");
        for (StackTraceElement element : t.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }
        if (t.getCause() != null) {
            sb.append("Caused by: ").append(getStackTrace(t.getCause()));
        }
        return sb.toString();
    }

    public static class Builder {
        private LocalDateTime timestamp;
        private LogLevel level;
        private String category;
        private String message;
        private String thread;
        private Map<String, Object> metadata = new HashMap<>();
        private Throwable throwable;

        public Builder level(LogLevel level) {
            this.level = level;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder thread(String thread) {
            this.thread = thread;
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }

        public Builder throwable(Throwable throwable) {
            this.throwable = throwable;
            return this;
        }

        public LogEntry build() {
            if (level == null) throw new IllegalArgumentException("Log level is required");
            if (category == null) throw new IllegalArgumentException("Category is required");
            if (message == null) throw new IllegalArgumentException("Message is required");
            return new LogEntry(this);
        }
    }
}
