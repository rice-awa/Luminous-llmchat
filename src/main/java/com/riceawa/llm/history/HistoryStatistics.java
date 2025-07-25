package com.riceawa.llm.history;

import com.riceawa.llm.core.LLMMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 历史记录统计功能
 */
public class HistoryStatistics {
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 生成玩家统计报告
     */
    public PlayerStatistics generatePlayerStatistics(UUID playerId) {
        List<ChatHistory.ChatSession> sessions = ChatHistory.getInstance().loadPlayerHistory(playerId);
        return new PlayerStatistics(playerId, sessions);
    }

    /**
     * 生成全局统计报告
     */
    public GlobalStatistics generateGlobalStatistics() {
        // 这里需要扩展ChatHistory来支持获取所有玩家的统计
        // 暂时返回空统计
        return new GlobalStatistics();
    }

    /**
     * 玩家统计数据类
     */
    public static class PlayerStatistics {
        private final UUID playerId;
        private final int totalSessions;
        private final int totalMessages;
        private final int userMessages;
        private final int assistantMessages;
        private final int systemMessages;
        private final LocalDateTime firstSessionTime;
        private final LocalDateTime lastSessionTime;
        private final Map<String, Integer> templateUsage;
        private final Map<String, Integer> dailyActivity;
        private final Map<String, Integer> hourlyActivity;
        private final double averageMessagesPerSession;
        private final long totalChatTimeMs;
        private final String mostUsedTemplate;
        private final String mostActiveDay;
        private final String mostActiveHour;

        public PlayerStatistics(UUID playerId, List<ChatHistory.ChatSession> sessions) {
            this.playerId = playerId;
            this.totalSessions = sessions.size();
            
            if (sessions.isEmpty()) {
                this.totalMessages = 0;
                this.userMessages = 0;
                this.assistantMessages = 0;
                this.systemMessages = 0;
                this.firstSessionTime = null;
                this.lastSessionTime = null;
                this.templateUsage = new HashMap<>();
                this.dailyActivity = new HashMap<>();
                this.hourlyActivity = new HashMap<>();
                this.averageMessagesPerSession = 0;
                this.totalChatTimeMs = 0;
                this.mostUsedTemplate = null;
                this.mostActiveDay = null;
                this.mostActiveHour = null;
                return;
            }

            // 计算消息统计
            int totalMsgs = 0;
            int userMsgs = 0;
            int assistantMsgs = 0;
            int systemMsgs = 0;

            for (ChatHistory.ChatSession session : sessions) {
                for (LLMMessage message : session.getMessages()) {
                    totalMsgs++;
                    switch (message.getRole()) {
                        case USER:
                            userMsgs++;
                            break;
                        case ASSISTANT:
                            assistantMsgs++;
                            break;
                        case SYSTEM:
                            systemMsgs++;
                            break;
                    }
                }
            }

            this.totalMessages = totalMsgs;
            this.userMessages = userMsgs;
            this.assistantMessages = assistantMsgs;
            this.systemMessages = systemMsgs;

            // 时间统计
            this.firstSessionTime = sessions.stream()
                    .map(ChatHistory.ChatSession::getTimestamp)
                    .min(LocalDateTime::compareTo)
                    .orElse(null);
            
            this.lastSessionTime = sessions.stream()
                    .map(ChatHistory.ChatSession::getTimestamp)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);

            // 模板使用统计
            this.templateUsage = sessions.stream()
                    .collect(Collectors.groupingBy(
                            session -> session.getPromptTemplate() != null ? session.getPromptTemplate() : "unknown",
                            Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                    ));

            // 日活跃度统计
            this.dailyActivity = sessions.stream()
                    .collect(Collectors.groupingBy(
                            session -> session.getTimestamp().toLocalDate().toString(),
                            Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                    ));

            // 小时活跃度统计
            this.hourlyActivity = sessions.stream()
                    .collect(Collectors.groupingBy(
                            session -> String.format("%02d:00", session.getTimestamp().getHour()),
                            Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                    ));

            // 计算平均值
            this.averageMessagesPerSession = totalSessions > 0 ? (double) totalMessages / totalSessions : 0;

            // 计算总聊天时间（简化计算，基于会话数量）
            this.totalChatTimeMs = totalSessions * 60000L; // 假设每个会话平均1分钟

            // 找出最常用的模板
            this.mostUsedTemplate = templateUsage.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);

            // 找出最活跃的日期
            this.mostActiveDay = dailyActivity.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);

            // 找出最活跃的小时
            this.mostActiveHour = hourlyActivity.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
        }

        // Getters
        public UUID getPlayerId() { return playerId; }
        public int getTotalSessions() { return totalSessions; }
        public int getTotalMessages() { return totalMessages; }
        public int getUserMessages() { return userMessages; }
        public int getAssistantMessages() { return assistantMessages; }
        public int getSystemMessages() { return systemMessages; }
        public LocalDateTime getFirstSessionTime() { return firstSessionTime; }
        public LocalDateTime getLastSessionTime() { return lastSessionTime; }
        public Map<String, Integer> getTemplateUsage() { return new HashMap<>(templateUsage); }
        public Map<String, Integer> getDailyActivity() { return new HashMap<>(dailyActivity); }
        public Map<String, Integer> getHourlyActivity() { return new HashMap<>(hourlyActivity); }
        public double getAverageMessagesPerSession() { return averageMessagesPerSession; }
        public long getTotalChatTimeMs() { return totalChatTimeMs; }
        public String getMostUsedTemplate() { return mostUsedTemplate; }
        public String getMostActiveDay() { return mostActiveDay; }
        public String getMostActiveHour() { return mostActiveHour; }

        /**
         * 生成统计报告文本
         */
        public String generateReport() {
            StringBuilder report = new StringBuilder();
            report.append("=== 玩家聊天统计报告 ===\n");
            report.append("玩家ID: ").append(playerId).append("\n");
            report.append("总会话数: ").append(totalSessions).append("\n");
            report.append("总消息数: ").append(totalMessages).append("\n");
            report.append("  - 用户消息: ").append(userMessages).append("\n");
            report.append("  - AI回复: ").append(assistantMessages).append("\n");
            report.append("  - 系统消息: ").append(systemMessages).append("\n");
            report.append("平均每会话消息数: ").append(String.format("%.1f", averageMessagesPerSession)).append("\n");
            
            if (firstSessionTime != null) {
                report.append("首次聊天时间: ").append(firstSessionTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
            }
            if (lastSessionTime != null) {
                report.append("最近聊天时间: ").append(lastSessionTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
            }
            
            if (mostUsedTemplate != null) {
                report.append("最常用模板: ").append(mostUsedTemplate).append(" (").append(templateUsage.get(mostUsedTemplate)).append("次)\n");
            }
            
            if (mostActiveDay != null) {
                report.append("最活跃日期: ").append(mostActiveDay).append(" (").append(dailyActivity.get(mostActiveDay)).append("次会话)\n");
            }
            
            if (mostActiveHour != null) {
                report.append("最活跃时段: ").append(mostActiveHour).append(" (").append(hourlyActivity.get(mostActiveHour)).append("次会话)\n");
            }

            // 模板使用详情
            if (!templateUsage.isEmpty()) {
                report.append("\n模板使用统计:\n");
                templateUsage.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .forEach(entry -> report.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("次\n"));
            }

            // 每日活跃度（最近7天）
            if (!dailyActivity.isEmpty()) {
                report.append("\n最近活跃度:\n");
                dailyActivity.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByKey().reversed())
                        .limit(7)
                        .forEach(entry -> report.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("次会话\n"));
            }

            return report.toString();
        }
    }

    /**
     * 全局统计数据类
     */
    public static class GlobalStatistics {
        private final int totalPlayers;
        private final int totalSessions;
        private final int totalMessages;
        private final LocalDateTime oldestSession;
        private final LocalDateTime newestSession;
        private final Map<String, Integer> templatePopularity;

        public GlobalStatistics() {
            // 暂时使用默认值，后续可以扩展
            this.totalPlayers = 0;
            this.totalSessions = 0;
            this.totalMessages = 0;
            this.oldestSession = null;
            this.newestSession = null;
            this.templatePopularity = new HashMap<>();
        }

        // Getters
        public int getTotalPlayers() { return totalPlayers; }
        public int getTotalSessions() { return totalSessions; }
        public int getTotalMessages() { return totalMessages; }
        public LocalDateTime getOldestSession() { return oldestSession; }
        public LocalDateTime getNewestSession() { return newestSession; }
        public Map<String, Integer> getTemplatePopularity() { return new HashMap<>(templatePopularity); }

        public String generateReport() {
            StringBuilder report = new StringBuilder();
            report.append("=== 全局聊天统计报告 ===\n");
            report.append("总玩家数: ").append(totalPlayers).append("\n");
            report.append("总会话数: ").append(totalSessions).append("\n");
            report.append("总消息数: ").append(totalMessages).append("\n");
            // 可以添加更多全局统计信息
            return report.toString();
        }
    }
}
