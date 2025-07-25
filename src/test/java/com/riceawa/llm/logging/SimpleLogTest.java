package com.riceawa.llm.logging;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 简单的日志系统测试
 */
public class SimpleLogTest {
    
    @Test
    void testLogLevel() {
        LogLevel debug = LogLevel.DEBUG;
        LogLevel info = LogLevel.INFO;
        LogLevel warn = LogLevel.WARN;
        LogLevel error = LogLevel.ERROR;
        
        assertTrue(debug.shouldLog(debug));
        assertTrue(debug.shouldLog(info));
        assertTrue(debug.shouldLog(warn));
        assertTrue(debug.shouldLog(error));
        
        assertFalse(info.shouldLog(debug));
        assertTrue(info.shouldLog(info));
        assertTrue(info.shouldLog(warn));
        assertTrue(info.shouldLog(error));
        
        assertFalse(warn.shouldLog(debug));
        assertFalse(warn.shouldLog(info));
        assertTrue(warn.shouldLog(warn));
        assertTrue(warn.shouldLog(error));
        
        assertFalse(error.shouldLog(debug));
        assertFalse(error.shouldLog(info));
        assertFalse(error.shouldLog(warn));
        assertTrue(error.shouldLog(error));
    }
    
    @Test
    void testLogEntry() {
        LogEntry entry = new LogEntry.Builder()
                .level(LogLevel.INFO)
                .category("test")
                .message("Test message")
                .metadata("key1", "value1")
                .metadata("key2", 123)
                .build();
        
        assertEquals(LogLevel.INFO, entry.getLevel());
        assertEquals("test", entry.getCategory());
        assertEquals("Test message", entry.getMessage());
        assertEquals("value1", entry.getMetadata().get("key1"));
        assertEquals(123, entry.getMetadata().get("key2"));
        
        String formatted = entry.toFormattedString();
        assertTrue(formatted.contains("INFO"));
        assertTrue(formatted.contains("test"));
        assertTrue(formatted.contains("Test message"));
        
        String json = entry.toJsonString();
        assertTrue(json.contains("\"level\":\"INFO\""));
        assertTrue(json.contains("\"category\":\"test\""));
        assertTrue(json.contains("\"message\":\"Test message\""));
    }
    
    @Test
    void testLogConfig() {
        LogConfig config = LogConfig.createDefault();
        
        assertTrue(config.isValid());
        assertTrue(config.isEnableFileLogging());
        assertTrue(config.isEnableConsoleLogging());
        assertEquals(LogLevel.INFO, config.getLogLevel());
        
        assertTrue(config.isCategoryEnabled("system"));
        assertTrue(config.isCategoryEnabled("chat"));
        
        config.setEnableSystemLog(false);
        assertFalse(config.isCategoryEnabled("system"));
    }
    
    @Test
    void testLogLevelFromString() {
        assertEquals(LogLevel.DEBUG, LogLevel.fromString("DEBUG"));
        assertEquals(LogLevel.INFO, LogLevel.fromString("INFO"));
        assertEquals(LogLevel.WARN, LogLevel.fromString("WARN"));
        assertEquals(LogLevel.ERROR, LogLevel.fromString("ERROR"));
        
        assertEquals(LogLevel.DEBUG, LogLevel.fromString("debug"));
        assertEquals(LogLevel.INFO, LogLevel.fromString("invalid"));
        assertEquals(LogLevel.INFO, LogLevel.fromString(null));
    }
}
