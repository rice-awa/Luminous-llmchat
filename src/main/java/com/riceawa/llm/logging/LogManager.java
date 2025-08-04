package com.riceawa.llm.logging;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 日志管理器 - 核心日志系统
 */
public class LogManager {
    private static LogManager instance;
    private static final Logger FALLBACK_LOGGER = LoggerFactory.getLogger(LogManager.class);
    
    private final Path logDirectory;
    private final LogConfig config;
    private final FileRotationManager rotationManager;
    private final ExecutorService asyncExecutor;
    private final BlockingQueue<LogEntry> logQueue;
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
    
    // 日志文件映射
    private final Map<String, Path> logFiles = new ConcurrentHashMap<>();

    private LogManager(LogConfig config) {
        this.config = config;
        this.logDirectory = FabricLoader.getInstance()
                .getConfigDir()
                .resolve("lllmchat")
                .resolve("logs");
        
        this.rotationManager = new FileRotationManager(logDirectory, config);
        
        // 初始化异步日志队列和执行器
        this.logQueue = new ArrayBlockingQueue<>(config.getAsyncQueueSize());
        this.asyncExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LLMChat-Logger");
            t.setDaemon(true);
            return t;
        });
        
        // 启动异步日志处理
        if (config.isEnableAsyncLogging()) {
            startAsyncLogging();
        }
        
        // 初始化日志文件
        initializeLogFiles();
    }

    public static synchronized LogManager getInstance() {
        if (instance == null) {
            instance = new LogManager(LogConfig.createDefault());
        }
        return instance;
    }

    public static synchronized LogManager getInstance(LogConfig config) {
        if (instance == null) {
            instance = new LogManager(config);
        }
        return instance;
    }

    /**
     * 记录系统日志
     */
    public void system(String message) {
        log(LogLevel.INFO, "system", message);
    }

    public void system(String message, Object... args) {
        log(LogLevel.INFO, "system", String.format(message, args));
    }

    /**
     * 记录聊天日志
     */
    public void chat(String message) {
        log(LogLevel.INFO, "chat", message);
    }

    public void chat(String message, Map<String, Object> metadata) {
        log(LogLevel.INFO, "chat", message, metadata);
    }

    /**
     * 记录错误日志
     */
    public void error(String message) {
        log(LogLevel.ERROR, "error", message);
    }

    public void error(String message, Throwable throwable) {
        log(LogLevel.ERROR, "error", message, throwable);
    }

    /**
     * 记录性能日志
     */
    public void performance(String message, Map<String, Object> metadata) {
        log(LogLevel.INFO, "performance", message, metadata);
    }

    /**
     * 记录审计日志
     */
    public void audit(String message, Map<String, Object> metadata) {
        log(LogLevel.INFO, "audit", message, metadata);
    }

    /**
     * 记录LLM请求日志
     */
    public void llmRequest(String message) {
        log(LogLevel.INFO, "llm_request", message);
    }

    public void llmRequest(String message, String jsonData) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("json_data", jsonData);
        log(LogLevel.INFO, "llm_request", message, metadata);
    }

    public void llmRequest(String message, Map<String, Object> metadata) {
        log(LogLevel.INFO, "llm_request", message, metadata);
    }

    /**
     * 通用日志记录方法
     */
    public void log(LogLevel level, String category, String message) {
        log(level, category, message, null, null);
    }

    public void log(LogLevel level, String category, String message, Map<String, Object> metadata) {
        log(level, category, message, metadata, null);
    }

    public void log(LogLevel level, String category, String message, Throwable throwable) {
        log(level, category, message, null, throwable);
    }

    public void log(LogLevel level, String category, String message, Map<String, Object> metadata, Throwable throwable) {
        if (isShutdown.get()) {
            return;
        }

        // 检查日志级别和类别是否启用
        if (!config.getLogLevel().shouldLog(level) || !config.isCategoryEnabled(category)) {
            return;
        }

        LogEntry.Builder builder = new LogEntry.Builder()
                .level(level)
                .category(category)
                .message(message);

        if (metadata != null) {
            builder.metadata(metadata);
        }

        if (throwable != null) {
            builder.throwable(throwable);
        }

        LogEntry entry = builder.build();

        // 控制台日志
        if (config.isEnableConsoleLogging()) {
            logToConsole(entry);
        }

        // 文件日志
        if (config.isEnableFileLogging()) {
            if (config.isEnableAsyncLogging()) {
                // 异步日志
                if (!logQueue.offer(entry)) {
                    // 队列满了，直接写入
                    writeToFile(entry);
                }
            } else {
                // 同步日志
                writeToFile(entry);
            }
        }
    }

    /**
     * 初始化日志文件
     */
    private void initializeLogFiles() {
        try {
            Files.createDirectories(logDirectory);
            
            // 创建各类日志文件
            if (config.isEnableSystemLog()) {
                logFiles.put("system", rotationManager.createNewLogFile("system"));
            }
            if (config.isEnableChatLog()) {
                logFiles.put("chat", rotationManager.createNewLogFile("chat"));
            }
            if (config.isEnableErrorLog()) {
                logFiles.put("error", rotationManager.createNewLogFile("error"));
            }
            if (config.isEnablePerformanceLog()) {
                logFiles.put("performance", rotationManager.createNewLogFile("performance"));
            }
            if (config.isEnableAuditLog()) {
                logFiles.put("audit", rotationManager.createNewLogFile("audit"));
            }
            if (config.isEnableLLMRequestLog()) {
                logFiles.put("llm_request", rotationManager.createNewLogFile("llm_request"));
            }
            
        } catch (IOException e) {
            FALLBACK_LOGGER.error("Failed to initialize log files", e);
        }
    }

    /**
     * 启动异步日志处理
     */
    private void startAsyncLogging() {
        asyncExecutor.submit(() -> {
            while (!isShutdown.get() || !logQueue.isEmpty()) {
                try {
                    LogEntry entry = logQueue.poll(1, TimeUnit.SECONDS);
                    if (entry != null) {
                        writeToFile(entry);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    FALLBACK_LOGGER.error("Error in async logging", e);
                }
            }
        });
    }

    /**
     * 写入控制台
     */
    private void logToConsole(LogEntry entry) {
        try {
            String formattedMessage = entry.toFormattedString();
            
            switch (entry.getLevel()) {
                case DEBUG:
                    FALLBACK_LOGGER.debug(formattedMessage);
                    break;
                case INFO:
                    FALLBACK_LOGGER.info(formattedMessage);
                    break;
                case WARN:
                    FALLBACK_LOGGER.warn(formattedMessage);
                    break;
                case ERROR:
                    if (entry.getThrowable() != null) {
                        FALLBACK_LOGGER.error(formattedMessage, entry.getThrowable());
                    } else {
                        FALLBACK_LOGGER.error(formattedMessage);
                    }
                    break;
            }
        } catch (Exception e) {
            // 避免日志记录本身出错
            System.err.println("Failed to log to console: " + e.getMessage());
        }
    }

    /**
     * 写入文件
     */
    private void writeToFile(LogEntry entry) {
        try {
            Path logFile = logFiles.get(entry.getCategory());
            if (logFile == null) {
                logFile = logFiles.get("system"); // 默认使用系统日志文件
            }
            
            if (logFile == null) {
                return;
            }

            // 检查是否需要轮转
            if (rotationManager.shouldRotate(logFile)) {
                rotationManager.rotateFile(logFile);
                // 重新创建日志文件
                logFile = rotationManager.createNewLogFile(getBaseName(logFile));
                logFiles.put(entry.getCategory(), logFile);
            }

            // 写入日志
            String logContent = config.isEnableJsonFormat() ? 
                    entry.toJsonString() + "\n" : 
                    entry.toFormattedString() + "\n";
            
            Files.write(logFile, logContent.getBytes(StandardCharsets.UTF_8), 
                       StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                       
        } catch (IOException e) {
            FALLBACK_LOGGER.error("Failed to write log to file", e);
        }
    }

    private String getBaseName(Path file) {
        String fileName = file.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    /**
     * 更新配置
     */
    public void updateConfig(LogConfig newConfig) {
        // 这里可以实现配置热更新逻辑
        FALLBACK_LOGGER.info("Log configuration updated");
    }

    /**
     * 关闭日志管理器
     */
    public void shutdown() {
        isShutdown.set(true);
        
        if (asyncExecutor != null && !asyncExecutor.isShutdown()) {
            asyncExecutor.shutdown();
            try {
                if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    asyncExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                asyncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        FALLBACK_LOGGER.info("Log manager shutdown completed");
    }
}
