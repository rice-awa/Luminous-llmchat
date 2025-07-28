package com.riceawa.llm.history;

import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.core.LLMMessage.MessageRole;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 聊天历史记录测试
 */
public class ChatHistoryTest {
    
    @Test
    void testChatSessionWithTitle() {
        UUID playerId = UUID.randomUUID();
        String sessionId = "test-session";
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(new LLMMessage(MessageRole.USER, "你好"));
        messages.add(new LLMMessage(MessageRole.ASSISTANT, "你好！有什么可以帮助你的吗？"));
        
        LocalDateTime timestamp = LocalDateTime.now();
        String promptTemplate = "default";
        String title = "简单问候对话";
        
        ChatHistory.ChatSession session = new ChatHistory.ChatSession(
            sessionId, playerId, messages, timestamp, promptTemplate, title);
        
        assertEquals(sessionId, session.getSessionId());
        assertEquals(playerId, session.getPlayerId());
        assertEquals(2, session.getMessages().size());
        assertEquals(timestamp, session.getTimestamp());
        assertEquals(promptTemplate, session.getPromptTemplate());
        assertEquals(title, session.getTitle());
        assertEquals(title, session.getDisplayTitle());
    }
    
    @Test
    void testChatSessionWithoutTitle() {
        UUID playerId = UUID.randomUUID();
        String sessionId = "test-session";
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(new LLMMessage(MessageRole.USER, "测试消息"));
        
        LocalDateTime timestamp = LocalDateTime.now();
        String promptTemplate = "default";
        
        ChatHistory.ChatSession session = new ChatHistory.ChatSession(
            sessionId, playerId, messages, timestamp, promptTemplate);
        
        assertNull(session.getTitle());
        assertNotNull(session.getDisplayTitle());
        assertTrue(session.getDisplayTitle().contains("对话"));
        assertTrue(session.getDisplayTitle().contains("1条消息"));
    }
    
    @Test
    void testChatSessionSetTitle() {
        UUID playerId = UUID.randomUUID();
        String sessionId = "test-session";
        List<LLMMessage> messages = new ArrayList<>();
        LocalDateTime timestamp = LocalDateTime.now();
        String promptTemplate = "default";
        
        ChatHistory.ChatSession session = new ChatHistory.ChatSession(
            sessionId, playerId, messages, timestamp, promptTemplate);
        
        assertNull(session.getTitle());
        
        String newTitle = "新的对话标题";
        session.setTitle(newTitle);
        
        assertEquals(newTitle, session.getTitle());
        assertEquals(newTitle, session.getDisplayTitle());
    }
    
    @Test
    void testDisplayTitleWithEmptyTitle() {
        UUID playerId = UUID.randomUUID();
        String sessionId = "test-session";
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(new LLMMessage(MessageRole.USER, "消息1"));
        messages.add(new LLMMessage(MessageRole.ASSISTANT, "回复1"));
        messages.add(new LLMMessage(MessageRole.USER, "消息2"));
        
        LocalDateTime timestamp = LocalDateTime.now();
        String promptTemplate = "default";
        
        ChatHistory.ChatSession session = new ChatHistory.ChatSession(
            sessionId, playerId, messages, timestamp, promptTemplate);
        
        session.setTitle(""); // 设置空标题
        
        String displayTitle = session.getDisplayTitle();
        assertTrue(displayTitle.contains("对话"));
        assertTrue(displayTitle.contains("3条消息"));
    }
}
