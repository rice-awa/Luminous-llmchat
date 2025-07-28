package com.riceawa.llm.history;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.context.ChatContext;
import com.riceawa.llm.logging.LogManager;
import com.riceawa.llm.service.TitleGenerationService;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
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

        // 检查是否有测试目录设置
        String testHistoryDir = System.getProperty("lllmchat.history.dir");
        if (testHistoryDir != null) {
            this.historyDir = Path.of(testHistoryDir);
        } else {
            this.historyDir = FabricLoader.getInstance()
                    .getConfigDir()
                    .resolve("lllmchat")
                    .resolve("history");
        }

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
     * 重置单例实例（仅用于测试）
     */
    public static synchronized void resetInstance() {
        instance = null;
    }

    /**
     * 保存聊天会话
     */
    public void saveSession(ChatContext context) {
        UUID playerId = context.getPlayerId();
        String sessionId = context.getSessionId();

        // 获取玩家的会话列表
        List<ChatSession> sessions = playerHistories.computeIfAbsent(playerId, k -> new ArrayList<>());

        // 查找是否已存在相同sessionId的会话
        ChatSession existingSession = null;
        int existingIndex = -1;
        for (int i = 0; i < sessions.size(); i++) {
            if (sessions.get(i).getSessionId().equals(sessionId)) {
                existingSession = sessions.get(i);
                existingIndex = i;
                break;
            }
        }

        // 创建新的会话对象
        ChatSession newSession = new ChatSession(
                sessionId,
                playerId,
                context.getMessages(),
                LocalDateTime.now(),
                context.getCurrentPromptTemplate()
        );

        if (existingSession != null) {
            // 更新现有会话，保留原有标题
            if (existingSession.getTitle() != null) {
                newSession.setTitle(existingSession.getTitle());
            }
            sessions.set(existingIndex, newSession);
            LogManager.getInstance().chat("Chat session updated for player " + playerId +
                    ", session: " + sessionId +
                    ", messages: " + context.getMessages().size());
        } else {
            // 添加新会话
            sessions.add(newSession);

            // 限制会话数量
            if (sessions.size() > maxSessionsPerPlayer) {
                sessions.remove(0);
            }

            LogManager.getInstance().chat("New chat session saved for player " + playerId +
                    ", session: " + sessionId +
                    ", messages: " + context.getMessages().size());
        }

        // 保存到文件
        saveToFile(playerId, sessions);

        // 异步生成标题（仅对新会话且没有标题的情况）
        if (existingSession == null || newSession.getTitle() == null) {
            generateTitleAsync(newSession, playerId, sessions);
        }
    }

    /**
     * 异步生成会话标题
     */
    private void generateTitleAsync(ChatSession session, UUID playerId, List<ChatSession> sessions) {
        TitleGenerationService titleService = TitleGenerationService.getInstance();

        // 检查是否应该生成标题
        if (!titleService.shouldGenerateTitle(session.getMessages())) {
            return;
        }

        // 异步生成标题
        titleService.generateTitle(session.getMessages())
            .thenAccept(title -> {
                if (title != null && !title.trim().isEmpty()) {
                    // 更新会话标题
                    session.setTitle(title);

                    // 重新保存到文件
                    saveToFile(playerId, sessions);

                    LogManager.getInstance().system("Generated title for session " +
                        session.getSessionId() + ": " + title);
                }
            })
            .exceptionally(throwable -> {
                LogManager.getInstance().system("Failed to generate title for session " +
                    session.getSessionId() + ": " + throwable.getMessage());
                return null;
            });
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
     * 通过索引获取玩家的会话（索引从1开始，1表示最新的会话）
     */
    public ChatSession getSessionByIndex(UUID playerId, int index) {
        List<ChatSession> sessions = loadPlayerHistory(playerId);
        if (sessions.isEmpty() || index < 1 || index > sessions.size()) {
            return null;
        }
        // 索引1对应最新的会话，所以需要从后往前数
        return sessions.get(sessions.size() - index);
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
            LogManager.getInstance().error("Failed to save chat history for player " + playerId, e);
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
            LogManager.getInstance().error("Failed to load chat history for player " + playerId, e);
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
        private String title; // 对话标题，可能为null（向后兼容）

        public ChatSession(String sessionId, UUID playerId, List<LLMMessage> messages,
                          LocalDateTime timestamp, String promptTemplate) {
            this(sessionId, playerId, messages, timestamp, promptTemplate, null);
        }

        public ChatSession(String sessionId, UUID playerId, List<LLMMessage> messages,
                          LocalDateTime timestamp, String promptTemplate, String title) {
            this.sessionId = sessionId;
            this.playerId = playerId;
            this.messages = new ArrayList<>(messages);
            this.timestamp = timestamp;
            this.promptTemplate = promptTemplate;
            this.title = title;
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

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        /**
         * 获取显示标题，如果没有标题则返回默认格式
         */
        public String getDisplayTitle() {
            if (title != null && !title.trim().isEmpty()) {
                return title;
            }
            // 默认标题格式：基于时间和消息数量
            return String.format("对话 %s (%d条消息)",
                getFormattedTimestamp(), messages.size());
        }

        public String getFormattedTimestamp() {
            return timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }
}
