package com.riceawa.llm.logging;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;

/**
 * 文件轮转管理器
 */
public class FileRotationManager {
    private final Path logDirectory;
    private final LogConfig config;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public FileRotationManager(Path logDirectory, LogConfig config) {
        this.logDirectory = logDirectory;
        this.config = config;
        
        try {
            Files.createDirectories(logDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create log directory: " + logDirectory, e);
        }
    }

    /**
     * 检查文件是否需要轮转
     */
    public boolean shouldRotate(Path logFile) {
        if (!Files.exists(logFile)) {
            return false;
        }
        
        try {
            long fileSize = Files.size(logFile);
            return fileSize >= config.getMaxFileSize();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 执行文件轮转
     */
    public void rotateFile(Path logFile) throws IOException {
        if (!Files.exists(logFile)) {
            return;
        }

        String baseName = getBaseName(logFile);
        String timestamp = LocalDateTime.now().format(dateFormatter);
        Path rotatedFile = logDirectory.resolve(baseName + "." + timestamp + ".log");
        
        // 移动当前文件到轮转文件
        Files.move(logFile, rotatedFile);
        
        // 压缩轮转的文件
        compressFile(rotatedFile);
        
        // 清理旧文件
        cleanupOldFiles(baseName);
    }

    /**
     * 压缩文件
     */
    private void compressFile(Path file) {
        Path compressedFile = Paths.get(file.toString() + ".gz");
        
        try (FileInputStream fis = new FileInputStream(file.toFile());
             FileOutputStream fos = new FileOutputStream(compressedFile.toFile());
             GZIPOutputStream gzos = new GZIPOutputStream(fos)) {
            
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                gzos.write(buffer, 0, len);
            }
            
            // 删除原始文件
            Files.deleteIfExists(file);
            
        } catch (IOException e) {
            // 如果压缩失败，保留原始文件
            System.err.println("Failed to compress log file: " + file + ", error: " + e.getMessage());
        }
    }

    /**
     * 清理旧的日志文件
     */
    private void cleanupOldFiles(String baseName) {
        try {
            List<Path> oldFiles = getOldLogFiles(baseName);
            
            // 按修改时间排序，保留最新的文件
            oldFiles.sort((a, b) -> {
                try {
                    return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                } catch (IOException e) {
                    return 0;
                }
            });
            
            // 删除超过保留数量的文件
            if (oldFiles.size() > config.getMaxBackupFiles()) {
                for (int i = config.getMaxBackupFiles(); i < oldFiles.size(); i++) {
                    try {
                        Files.deleteIfExists(oldFiles.get(i));
                    } catch (IOException e) {
                        System.err.println("Failed to delete old log file: " + oldFiles.get(i));
                    }
                }
            }
            
            // 删除超过保留天数的文件
            cleanupByRetentionDays(baseName);
            
        } catch (IOException e) {
            System.err.println("Failed to cleanup old log files: " + e.getMessage());
        }
    }

    /**
     * 根据保留天数清理文件
     */
    private void cleanupByRetentionDays(String baseName) throws IOException {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(config.getRetentionDays());
        
        try (Stream<Path> files = Files.list(logDirectory)) {
            files.filter(file -> {
                String fileName = file.getFileName().toString();
                return fileName.startsWith(baseName + ".") && 
                       (fileName.endsWith(".log") || fileName.endsWith(".log.gz"));
            }).forEach(file -> {
                try {
                    LocalDateTime fileTime = Files.getLastModifiedTime(file)
                            .toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime();
                    
                    if (fileTime.isBefore(cutoffDate)) {
                        Files.deleteIfExists(file);
                    }
                } catch (IOException e) {
                    System.err.println("Failed to check/delete old log file: " + file);
                }
            });
        }
    }

    /**
     * 获取旧的日志文件列表
     */
    private List<Path> getOldLogFiles(String baseName) throws IOException {
        List<Path> oldFiles = new ArrayList<>();
        
        try (Stream<Path> files = Files.list(logDirectory)) {
            files.filter(file -> {
                String fileName = file.getFileName().toString();
                return fileName.startsWith(baseName + ".") && 
                       (fileName.endsWith(".log") || fileName.endsWith(".log.gz"));
            }).forEach(oldFiles::add);
        }
        
        return oldFiles;
    }

    /**
     * 获取文件的基础名称（不包含扩展名）
     */
    private String getBaseName(Path file) {
        String fileName = file.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    /**
     * 创建新的日志文件
     */
    public Path createNewLogFile(String baseName) throws IOException {
        Path logFile = logDirectory.resolve(baseName + ".log");
        if (!Files.exists(logFile)) {
            Files.createFile(logFile);
        }
        return logFile;
    }
}
