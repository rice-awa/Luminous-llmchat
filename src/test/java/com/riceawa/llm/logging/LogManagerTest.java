package com.riceawa.llm.logging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LogManager测试类
 */
public class LogManagerTest {
    
    @TempDir
    Path tempDir;
    
    private LogConfig testConfig;
    
    @BeforeEach
    void setUp() {
        testConfig = LogConfig.createDefault();
        testConfig.setEnableFileLogging(true);
        testConfig.setEnableConsoleLogging(false); // 测试时禁用控制台输出
        testConfig.setLogLevel(LogLevel.DEBUG);
    }
    
    @Test
    void testLogLevels() {
        LogManager logManager = LogManager.getInstance(testConfig);
        
        // 测试不同级别的日志
        logManager.log(LogLevel.DEBUG, "test", "Debug message");
        logManager.log(LogLevel.INFO, "test", "Info message");
        logManager.log(LogLevel.WARN, "test", "Warn message");
        logManager.log(LogLevel.ERROR, "test", "Error message");
        
        // 验证日志级别过滤
        testConfig.setLogLevel(LogLevel.WARN);
        logManager.updateConfig(testConfig);
        
        // 这些应该被过滤掉
        logManager.log(LogLevel.DEBUG, "test", "Should be filtered");
        logManager.log(LogLevel.INFO, "test", "Should be filtered");
        
        // 这些应该被记录
        logManager.log(LogLevel.WARN, "test", "Should be logged");
        logManager.log(LogLevel.ERROR, "test", "Should be logged");
    }
    
    @Test
    void testLogCategories() {
        LogManager logManager = LogManager.getInstance(testConfig);
        
        // 测试不同类别的日志
        logManager.system("System message");
        logManager.chat("Chat message");
        logManager.error("Error message");
        logManager.performance("Performance message", Map.of("time", 100));
        logManager.audit("Audit message", Map.of("user", "test"));
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
        
        // 测试格式化输出
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
        
        // 测试类别启用/禁用
        assertTrue(config.isCategoryEnabled("system"));
        assertTrue(config.isCategoryEnabled("chat"));
        
        config.setEnableSystemLog(false);
        assertFalse(config.isCategoryEnabled("system"));
    }
    
    @Test
    void testLogWithException() {
        LogManager logManager = LogManager.getInstance(testConfig);
        
        Exception testException = new RuntimeException("Test exception");
        logManager.error("Error with exception", testException);
        
        LogEntry entry = new LogEntry.Builder()
                .level(LogLevel.ERROR)
                .category("error")
                .message("Error with exception")
                .throwable(testException)
                .build();
        
        assertEquals(testException, entry.getThrowable());
        
        String formatted = entry.toFormattedString();
        assertTrue(formatted.contains("RuntimeException"));
        assertTrue(formatted.contains("Test exception"));
    }
}
