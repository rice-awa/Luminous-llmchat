package com.riceawa.llm.logging;

/**
 * 日志级别枚举
 */
public enum LogLevel {
    DEBUG(0, "DEBUG"),
    INFO(1, "INFO"),
    WARN(2, "WARN"),
    ERROR(3, "ERROR");

    private final int level;
    private final String name;

    LogLevel(int level, String name) {
        this.level = level;
        this.name = name;
    }

    public int getLevel() {
        return level;
    }

    public String getName() {
        return name;
    }

    /**
     * 检查当前级别是否应该记录指定级别的日志
     */
    public boolean shouldLog(LogLevel targetLevel) {
        return this.level <= targetLevel.level;
    }

    /**
     * 从字符串解析日志级别
     */
    public static LogLevel fromString(String levelStr) {
        if (levelStr == null) {
            return INFO;
        }
        
        try {
            return LogLevel.valueOf(levelStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return INFO;
        }
    }
}
