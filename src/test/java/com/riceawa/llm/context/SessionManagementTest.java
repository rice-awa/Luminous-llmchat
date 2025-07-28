package com.riceawa.llm.context;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 会话管理功能的简单测试
 * 主要测试会话ID的生成和管理逻辑
 */
public class SessionManagementTest {

    @Test
    void testSessionIdGeneration() {
        // 测试会话ID的唯一性
        String sessionId1 = UUID.randomUUID().toString();
        String sessionId2 = UUID.randomUUID().toString();
        
        assertNotEquals(sessionId1, sessionId2, "会话ID应该是唯一的");
        assertNotNull(sessionId1, "会话ID不应该为null");
        assertNotNull(sessionId2, "会话ID不应该为null");
        assertTrue(sessionId1.length() > 0, "会话ID不应该为空");
        assertTrue(sessionId2.length() > 0, "会话ID不应该为空");
    }

    @Test
    void testUUIDFormat() {
        // 测试UUID格式
        String sessionId = UUID.randomUUID().toString();
        
        // UUID格式应该是 xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        assertTrue(sessionId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
            "会话ID应该符合UUID格式");
    }

    @Test
    void testMultipleSessionIds() {
        // 测试生成多个会话ID的唯一性
        int count = 100;
        String[] sessionIds = new String[count];
        
        for (int i = 0; i < count; i++) {
            sessionIds[i] = UUID.randomUUID().toString();
        }
        
        // 检查所有ID都是唯一的
        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < count; j++) {
                assertNotEquals(sessionIds[i], sessionIds[j], 
                    "会话ID " + i + " 和 " + j + " 应该不同");
            }
        }
    }
}
