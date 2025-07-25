package com.riceawa.llm.history;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.context.ChatContext;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天历史记录管理器
 */
public class ChatHistory {
    private static ChatHistory instance;
    private final Gson gson;
    private final Path historyDir;
    private final Map<UUID, List<ChatSession>> playerHistories;
    private final int maxSessionsPerPlayer;

    private ChatHistory() {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .create();
        
        this.historyDir = FabricLoader.getInstance()
                .getConfigDir()
                .resolve("lllmchat")
                .resolve("history");
        
        this.playerHistories = new ConcurrentHashMap<>();
        this.maxSessionsPerPlayer = 100; // 每个玩家最多保存100个会话
        
        // 确保目录存在
        try {
            Files.createDirectories(historyDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create history directory", e);
        }
    }

    public static ChatHistory getInstance() {
        if (instance == null) {
            synchronized (ChatHistory.class) {
                if (instance == null) {
                    instance = new ChatHistory();
                }
            }
        }
        return instance;
    }

    /**
     * 保存聊天会话
     */
    public void saveSession(ChatContext context) {
        UUID playerId = context.getPlayerId();
        
        ChatSession session = new ChatSession(
                context.getSessionId(),
                playerId,
                context.getMessages(),
                LocalDateTime.now(),
                context.getCurrentPromptTemplate()
        );

        // 添加到内存缓存
        List<ChatSession> sessions = playerHistories.computeIfAbsent(playerId, k -> new ArrayList<>());
        sessions.add(session);
        
        // 限制会话数量
        if (sessions.size() > maxSessionsPerPlayer) {
            sessions.remove(0);
        }

        // 保存到文件
        saveToFile(playerId, sessions);
    }

    /**
     * 加载玩家的聊天历史
     */
    public List<ChatSession> loadPlayerHistory(UUID playerId) {
        List<ChatSession> sessions = playerHistories.get(playerId);
        if (sessions == null) {
            sessions = loadFromFile(playerId);
            if (sessions != null) {
                playerHistories.put(playerId, sessions);
            } else {
                sessions = new ArrayList<>();
                playerHistories.put(playerId, sessions);
            }
        }
        return new ArrayList<>(sessions);
    }

    /**
     * 获取玩家最近的会话
     */
    public ChatSession getLastSession(UUID playerId) {
        List<ChatSession> sessions = loadPlayerHistory(playerId);
        if (sessions.isEmpty()) {
            return null;
        }
        return sessions.get(sessions.size() - 1);
    }

    /**
     * 删除玩家的所有历史记录
     */
    public void clearPlayerHistory(UUID playerId) {
        playerHistories.remove(playerId);
        Path playerFile = getPlayerHistoryFile(playerId);
        try {
            Files.deleteIfExists(playerFile);
        } catch (IOException e) {
            // 忽略删除错误
        }
    }

    /**
     * 搜索历史记录
     */
    public List<ChatSession> searchHistory(UUID playerId, String keyword) {
        List<ChatSession> sessions = loadPlayerHistory(playerId);
        List<ChatSession> results = new ArrayList<>();
        
        String lowerKeyword = keyword.toLowerCase();
        for (ChatSession session : sessions) {
            for (LLMMessage message : session.getMessages()) {
                if (message.getContent().toLowerCase().contains(lowerKeyword)) {
                    results.add(session);
                    break;
                }
            }
        }
        
        return results;
    }

    /**
     * 保存到文件
     */
    private void saveToFile(UUID playerId, List<ChatSession> sessions) {
        Path playerFile = getPlayerHistoryFile(playerId);
        try (FileWriter writer = new FileWriter(playerFile.toFile())) {
            gson.toJson(sessions, writer);
        } catch (IOException e) {
            // 记录错误但不抛出异常
            System.err.println("Failed to save chat history for player " + playerId + ": " + e.getMessage());
        }
    }

    /**
     * 从文件加载
     */
    private List<ChatSession> loadFromFile(UUID playerId) {
        Path playerFile = getPlayerHistoryFile(playerId);
        if (!Files.exists(playerFile)) {
            return null;
        }

        try (FileReader reader = new FileReader(playerFile.toFile())) {
            Type listType = new TypeToken<List<ChatSession>>(){}.getType();
            return gson.fromJson(reader, listType);
        } catch (IOException e) {
            System.err.println("Failed to load chat history for player " + playerId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取玩家历史文件路径
     */
    private Path getPlayerHistoryFile(UUID playerId) {
        return historyDir.resolve(playerId.toString() + ".json");
    }

    /**
     * 聊天会话数据结构
     */
    public static class ChatSession {
        private final String sessionId;
        private final UUID playerId;
        private final List<LLMMessage> messages;
        private final LocalDateTime timestamp;
        private final String promptTemplate;

        public ChatSession(String sessionId, UUID playerId, List<LLMMessage> messages, 
                          LocalDateTime timestamp, String promptTemplate) {
            this.sessionId = sessionId;
            this.playerId = playerId;
            this.messages = new ArrayList<>(messages);
            this.timestamp = timestamp;
            this.promptTemplate = promptTemplate;
        }

        public String getSessionId() {
            return sessionId;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public List<LLMMessage> getMessages() {
            return new ArrayList<>(messages);
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public String getPromptTemplate() {
            return promptTemplate;
        }

        public String getFormattedTimestamp() {
            return timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }
}
