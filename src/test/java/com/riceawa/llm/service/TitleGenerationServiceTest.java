package com.riceawa.llm.service;

import com.riceawa.llm.core.LLMMessage;
import com.riceawa.llm.core.LLMMessage.MessageRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 标题生成服务测试
 */
public class TitleGenerationServiceTest {
    
    private TitleGenerationService titleService;
    
    @BeforeEach
    void setUp() {
        titleService = TitleGenerationService.getInstance();
    }
    
    @Test
    void testShouldGenerateTitle_WithValidMessages() {
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(new LLMMessage(MessageRole.USER, "你好，请帮我解释一下Java中的多态性"));
        messages.add(new LLMMessage(MessageRole.ASSISTANT, "多态性是面向对象编程的重要特性..."));
        
        assertTrue(titleService.shouldGenerateTitle(messages));
    }
    
    @Test
    void testShouldGenerateTitle_WithInsufficientMessages() {
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(new LLMMessage(MessageRole.USER, "你好"));
        
        assertFalse(titleService.shouldGenerateTitle(messages));
    }
    
    @Test
    void testShouldGenerateTitle_WithOnlySystemMessages() {
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(new LLMMessage(MessageRole.SYSTEM, "你是一个有用的助手"));
        messages.add(new LLMMessage(MessageRole.SYSTEM, "请回答用户的问题"));
        
        assertFalse(titleService.shouldGenerateTitle(messages));
    }
    
    @Test
    void testShouldGenerateTitle_WithMixedMessages() {
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(new LLMMessage(MessageRole.SYSTEM, "你是一个有用的助手"));
        messages.add(new LLMMessage(MessageRole.USER, "什么是机器学习？"));
        messages.add(new LLMMessage(MessageRole.ASSISTANT, "机器学习是人工智能的一个分支..."));
        
        assertTrue(titleService.shouldGenerateTitle(messages));
    }
    
    @Test
    void testShouldGenerateTitle_WithEmptyList() {
        List<LLMMessage> messages = new ArrayList<>();
        
        assertFalse(titleService.shouldGenerateTitle(messages));
    }
    
    @Test
    void testShouldGenerateTitle_WithNullList() {
        assertFalse(titleService.shouldGenerateTitle(null));
    }
}
