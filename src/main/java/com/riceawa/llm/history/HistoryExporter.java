package com.riceawa.llm.history;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.riceawa.llm.core.LLMMessage;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 历史记录导出功能
 */
public class HistoryExporter {
    private final Gson gson;
    private final Path exportDir;
    private final DateTimeFormatter fileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final DateTimeFormatter displayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public HistoryExporter() {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .create();
        
        this.exportDir = FabricLoader.getInstance()
                .getConfigDir()
                .resolve("lllmchat")
                .resolve("exports");
        
        try {
            Files.createDirectories(exportDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create export directory", e);
        }
    }

    /**
     * 导出格式枚举
     */
    public enum ExportFormat {
        JSON("json"),
        CSV("csv"),
        TXT("txt"),
        HTML("html");

        private final String extension;

        ExportFormat(String extension) {
            this.extension = extension;
        }

        public String getExtension() {
            return extension;
        }
    }

    /**
     * 导出玩家的所有历史记录
     */
    public ExportResult exportPlayerHistory(UUID playerId, String playerName, ExportFormat format) {
        List<ChatHistory.ChatSession> sessions = ChatHistory.getInstance().loadPlayerHistory(playerId);
        return exportSessions(sessions, playerName, format, "player_" + playerName);
    }

    /**
     * 导出指定时间范围的历史记录
     */
    public ExportResult exportByDateRange(UUID playerId, String playerName, 
                                        LocalDateTime startDate, LocalDateTime endDate, 
                                        ExportFormat format) {
        List<ChatHistory.ChatSession> allSessions = ChatHistory.getInstance().loadPlayerHistory(playerId);
        List<ChatHistory.ChatSession> filteredSessions = allSessions.stream()
                .filter(session -> {
                    LocalDateTime sessionTime = session.getTimestamp();
                    return !sessionTime.isBefore(startDate) && !sessionTime.isAfter(endDate);
                })
                .collect(Collectors.toList());
        
        String fileName = String.format("player_%s_%s_to_%s", 
                playerName, 
                startDate.format(fileNameFormatter), 
                endDate.format(fileNameFormatter));
        
        return exportSessions(filteredSessions, playerName, format, fileName);
    }

    /**
     * 导出包含特定关键词的历史记录
     */
    public ExportResult exportByKeyword(UUID playerId, String playerName, 
                                      String keyword, ExportFormat format) {
        List<ChatHistory.ChatSession> sessions = ChatHistory.getInstance().searchHistory(playerId, keyword);
        String fileName = String.format("player_%s_keyword_%s", playerName, keyword.replaceAll("[^a-zA-Z0-9]", "_"));
        return exportSessions(sessions, playerName, format, fileName);
    }

    /**
     * 导出会话列表
     */
    private ExportResult exportSessions(List<ChatHistory.ChatSession> sessions, String playerName, 
                                      ExportFormat format, String baseFileName) {
        if (sessions.isEmpty()) {
            return new ExportResult(false, "No sessions to export", null);
        }

        try {
            String fileName = baseFileName + "_" + LocalDateTime.now().format(fileNameFormatter) + "." + format.getExtension();
            Path exportFile = exportDir.resolve(fileName);

            switch (format) {
                case JSON:
                    exportToJson(sessions, exportFile);
                    break;
                case CSV:
                    exportToCsv(sessions, exportFile);
                    break;
                case TXT:
                    exportToTxt(sessions, exportFile);
                    break;
                case HTML:
                    exportToHtml(sessions, playerName, exportFile);
                    break;
                default:
                    return new ExportResult(false, "Unsupported export format: " + format, null);
            }

            return new ExportResult(true, "Export completed successfully", exportFile);
            
        } catch (IOException e) {
            return new ExportResult(false, "Export failed: " + e.getMessage(), null);
        }
    }

    /**
     * 导出为JSON格式
     */
    private void exportToJson(List<ChatHistory.ChatSession> sessions, Path exportFile) throws IOException {
        try (FileWriter writer = new FileWriter(exportFile.toFile(), StandardCharsets.UTF_8)) {
            gson.toJson(sessions, writer);
        }
    }

    /**
     * 导出为CSV格式
     */
    private void exportToCsv(List<ChatHistory.ChatSession> sessions, Path exportFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(exportFile, StandardCharsets.UTF_8))) {
            // CSV头部
            writer.println("SessionId,PlayerId,Timestamp,PromptTemplate,MessageCount,UserMessages,AssistantMessages");
            
            for (ChatHistory.ChatSession session : sessions) {
                List<LLMMessage> messages = session.getMessages();
                long userMessages = messages.stream().filter(m -> m.getRole() == LLMMessage.MessageRole.USER).count();
                long assistantMessages = messages.stream().filter(m -> m.getRole() == LLMMessage.MessageRole.ASSISTANT).count();
                
                writer.printf("%s,%s,%s,%s,%d,%d,%d%n",
                        escapeCsv(session.getSessionId()),
                        session.getPlayerId().toString(),
                        session.getTimestamp().format(displayFormatter),
                        escapeCsv(session.getPromptTemplate()),
                        messages.size(),
                        userMessages,
                        assistantMessages);
            }
        }
    }

    /**
     * 导出为TXT格式
     */
    private void exportToTxt(List<ChatHistory.ChatSession> sessions, Path exportFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(exportFile, StandardCharsets.UTF_8))) {
            writer.println("=== LLM Chat History Export ===");
            writer.println("Export Time: " + LocalDateTime.now().format(displayFormatter));
            writer.println("Total Sessions: " + sessions.size());
            writer.println();

            for (int i = 0; i < sessions.size(); i++) {
                ChatHistory.ChatSession session = sessions.get(i);
                writer.println("--- Session " + (i + 1) + " ---");
                writer.println("Session ID: " + session.getSessionId());
                writer.println("Player ID: " + session.getPlayerId());
                writer.println("Timestamp: " + session.getTimestamp().format(displayFormatter));
                writer.println("Prompt Template: " + session.getPromptTemplate());
                writer.println("Message Count: " + session.getMessages().size());
                writer.println();
                
                writer.println("Messages:");
                for (LLMMessage message : session.getMessages()) {
                    writer.println("  [" + message.getRole() + "] " + message.getContent());
                }
                writer.println();
            }
        }
    }

    /**
     * 导出为HTML格式
     */
    private void exportToHtml(List<ChatHistory.ChatSession> sessions, String playerName, Path exportFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(exportFile, StandardCharsets.UTF_8))) {
            writer.println("<!DOCTYPE html>");
            writer.println("<html><head>");
            writer.println("<meta charset='UTF-8'>");
            writer.println("<title>LLM Chat History - " + escapeHtml(playerName) + "</title>");
            writer.println("<style>");
            writer.println("body { font-family: Arial, sans-serif; margin: 20px; }");
            writer.println(".session { border: 1px solid #ccc; margin: 10px 0; padding: 15px; border-radius: 5px; }");
            writer.println(".session-header { background: #f5f5f5; padding: 10px; margin: -15px -15px 10px -15px; border-radius: 5px 5px 0 0; }");
            writer.println(".message { margin: 5px 0; padding: 8px; border-radius: 3px; }");
            writer.println(".user { background: #e3f2fd; }");
            writer.println(".assistant { background: #f3e5f5; }");
            writer.println(".system { background: #fff3e0; }");
            writer.println("</style>");
            writer.println("</head><body>");
            
            writer.println("<h1>LLM Chat History - " + escapeHtml(playerName) + "</h1>");
            writer.println("<p>Export Time: " + LocalDateTime.now().format(displayFormatter) + "</p>");
            writer.println("<p>Total Sessions: " + sessions.size() + "</p>");

            for (int i = 0; i < sessions.size(); i++) {
                ChatHistory.ChatSession session = sessions.get(i);
                writer.println("<div class='session'>");
                writer.println("<div class='session-header'>");
                writer.println("<h3>Session " + (i + 1) + "</h3>");
                writer.println("<p><strong>ID:</strong> " + escapeHtml(session.getSessionId()) + "</p>");
                writer.println("<p><strong>Time:</strong> " + session.getTimestamp().format(displayFormatter) + "</p>");
                writer.println("<p><strong>Template:</strong> " + escapeHtml(session.getPromptTemplate()) + "</p>");
                writer.println("</div>");
                
                for (LLMMessage message : session.getMessages()) {
                    String cssClass = message.getRole().toString().toLowerCase();
                    writer.println("<div class='message " + cssClass + "'>");
                    writer.println("<strong>" + message.getRole() + ":</strong> " + escapeHtml(message.getContent()));
                    writer.println("</div>");
                }
                writer.println("</div>");
            }
            
            writer.println("</body></html>");
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /**
     * 导出结果类
     */
    public static class ExportResult {
        private final boolean success;
        private final String message;
        private final Path exportFile;

        public ExportResult(boolean success, String message, Path exportFile) {
            this.success = success;
            this.message = message;
            this.exportFile = exportFile;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Path getExportFile() { return exportFile; }
    }
}
