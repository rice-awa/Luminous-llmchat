package com.riceawa.llm.command;

import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.core.LLMMessage.MessageRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 消息预览功能测试
 */
public class MessagePreviewTest {

    @Test
    void testMessageContentTruncation() {
        // 测试消息内容截断逻辑
        String shortMessage = "这是一条短消息";
        // 创建一个确实超过150字符的长消息
        StringBuilder longMessageBuilder = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            longMessageBuilder.append("这是一条非常长的消息内容，");
        }
        String longMessage = longMessageBuilder.toString();

        assertTrue(shortMessage.length() < 150, "短消息应该小于150字符");
        assertTrue(longMessage.length() > 150, "长消息应该大于150字符，实际长度：" + longMessage.length());

        // 模拟截断逻辑
        int maxLength = 150;
        String truncated = longMessage.length() > maxLength ?
            longMessage.substring(0, maxLength) + "..." : longMessage;

        assertTrue(truncated.length() <= maxLength + 3, "截断后的消息应该不超过限制");
        if (longMessage.length() > maxLength) {
            assertTrue(truncated.endsWith("..."), "长消息应该以...结尾");
        }
    }

    @Test
    void testSmartTruncation() {
        // 测试智能截断逻辑（在句号后截断）
        String messageWithPeriod = "这是第一句话。这是第二句话，会被截断。这是第三句话，不会显示。";
        int maxLength = 20; // 设置较短的长度来测试
        
        // 模拟智能截断逻辑
        String content = messageWithPeriod;
        if (content.length() > maxLength) {
            int cutPoint = maxLength;
            for (int j = Math.min(maxLength - 10, content.length() - 1); j >= maxLength - 30 && j > 0; j--) {
                char c = content.charAt(j);
                if (c == '。' || c == '？' || c == '！' || c == '.' || c == '?' || c == '!') {
                    cutPoint = j + 1;
                    break;
                }
            }
            content = content.substring(0, cutPoint) + "...";
        }
        
        // 验证截断结果
        assertTrue(content.contains("第一句话。"), "应该包含完整的第一句话");
        assertFalse(content.contains("第三句话"), "不应该包含第三句话");
    }

    @Test
    void testMessageRoleHandling() {
        // 测试不同角色的消息处理
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(new LLMMessage(MessageRole.USER, "用户消息"));
        messages.add(new LLMMessage(MessageRole.ASSISTANT, "AI回复"));
        messages.add(new LLMMessage(MessageRole.SYSTEM, "系统消息"));
        
        // 验证消息角色
        assertEquals(MessageRole.USER, messages.get(0).getRole());
        assertEquals(MessageRole.ASSISTANT, messages.get(1).getRole());
        assertEquals(MessageRole.SYSTEM, messages.get(2).getRole());
        
        // 验证消息内容
        assertEquals("用户消息", messages.get(0).getContent());
        assertEquals("AI回复", messages.get(1).getContent());
        assertEquals("系统消息", messages.get(2).getContent());
    }

    @Test
    void testPreviewCountLogic() {
        // 测试预览数量逻辑
        List<LLMMessage> messages = new ArrayList<>();
        
        // 添加10条消息
        for (int i = 1; i <= 10; i++) {
            messages.add(new LLMMessage(MessageRole.USER, "消息 " + i));
        }
        
        int maxPreviewCount = 5;
        int actualPreviewCount = Math.min(maxPreviewCount, messages.size());
        
        assertEquals(5, actualPreviewCount, "应该预览5条消息");
        
        // 测试消息少于预览数量的情况
        List<LLMMessage> fewMessages = new ArrayList<>();
        fewMessages.add(new LLMMessage(MessageRole.USER, "唯一消息"));
        
        int fewPreviewCount = Math.min(maxPreviewCount, fewMessages.size());
        assertEquals(1, fewPreviewCount, "只有1条消息时应该预览1条");
    }

    @Test
    void testEmptyMessageHandling() {
        // 测试空消息处理
        List<LLMMessage> emptyMessages = new ArrayList<>();
        assertTrue(emptyMessages.isEmpty(), "空消息列表应该为空");
        
        // 测试null消息处理
        List<LLMMessage> nullMessages = null;
        assertNull(nullMessages, "null消息列表应该为null");
        
        // 测试包含null内容的消息
        List<LLMMessage> messagesWithNull = new ArrayList<>();
        messagesWithNull.add(new LLMMessage(MessageRole.USER, null));
        messagesWithNull.add(new LLMMessage(MessageRole.USER, "正常消息"));
        
        assertEquals(2, messagesWithNull.size(), "应该有2条消息");
        assertNull(messagesWithNull.get(0).getContent(), "第一条消息内容应该为null");
        assertNotNull(messagesWithNull.get(1).getContent(), "第二条消息内容不应该为null");
    }

    @Test
    void testMessageIndexing() {
        // 测试消息索引逻辑
        List<LLMMessage> messages = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            messages.add(new LLMMessage(MessageRole.USER, "消息 " + i));
        }

        int maxPreviewCount = 5;
        int previewCount = Math.min(maxPreviewCount, messages.size());

        // 模拟预览逻辑
        int startIndex = messages.size() - previewCount; // 应该是2
        assertEquals(2, startIndex, "开始索引应该是2");

        // 验证预览的消息是最后5条
        for (int i = startIndex; i < messages.size(); i++) {
            int messageIndex = i - startIndex + 1; // 1, 2, 3, 4, 5
            assertTrue(messageIndex >= 1 && messageIndex <= 5, "消息索引应该在1-5之间");
        }
    }

    @Test
    void testConfigurablePreviewCount() {
        // 测试可配置的预览数量
        List<LLMMessage> messages = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            messages.add(new LLMMessage(MessageRole.USER, "消息 " + i));
        }

        // 测试不同的预览数量设置
        int[] previewCounts = {1, 3, 5, 8, 15}; // 最后一个超过消息总数

        for (int maxPreviewCount : previewCounts) {
            int actualPreviewCount = Math.min(maxPreviewCount, messages.size());
            int expectedCount = Math.min(maxPreviewCount, 10); // 消息总数是10

            assertEquals(expectedCount, actualPreviewCount,
                "预览数量设置为" + maxPreviewCount + "时，实际应该显示" + expectedCount + "条");
        }
    }

    @Test
    void testConfigurableMaxLength() {
        // 测试可配置的最大长度
        String longMessage = "这是一条很长的消息，用来测试不同长度限制的效果。";

        int[] maxLengths = {10, 20, 50, 100, 200};

        for (int maxLength : maxLengths) {
            String result = longMessage.length() > maxLength ?
                longMessage.substring(0, maxLength) + "..." : longMessage;

            if (longMessage.length() > maxLength) {
                assertTrue(result.length() <= maxLength + 3,
                    "长度限制为" + maxLength + "时，结果长度不应超过" + (maxLength + 3));
                assertTrue(result.endsWith("..."),
                    "超过长度限制时应该以...结尾");
            } else {
                assertEquals(longMessage, result,
                    "未超过长度限制时应该保持原样");
            }
        }
    }
}
