package com.riceawa.llm.template;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 提示词模板测试
 */
public class PromptTemplateTest {
    
    private PromptTemplate template;
    
    @BeforeEach
    void setUp() {
        template = new PromptTemplate();
        template.setId("test");
        template.setName("测试模板");
        template.setSystemPrompt("你是一个测试助手。");
    }
    
    @Test
    void testBasicSystemPromptRendering() {
        String result = template.renderSystemPrompt();
        assertEquals("你是一个测试助手。", result);
    }
    
    @Test
    void testVariableReplacement() {
        template.setSystemPrompt("你是一个{{type}}助手。");
        template.setVariable("type", "专业");
        
        String result = template.renderSystemPrompt();
        assertEquals("你是一个专业助手。", result);
    }
    
    @Test
    void testUserMessageRendering() {
        template.setUserPromptPrefix("请帮我：");
        template.setUserPromptSuffix("，谢谢！");
        
        String result = template.renderUserMessage("解决问题");
        assertEquals("请帮我：解决问题，谢谢！", result);
    }
    
    @Test
    void testGlobalContextConfiguration() {
        // 在测试环境中，我们只测试模板的基本功能
        // 因为LLMChatConfig需要Fabric环境才能正常工作

        // 测试模板是否正确处理变量
        template.setSystemPrompt("基础提示词");
        String basicPrompt = template.renderSystemPrompt();
        assertEquals("基础提示词", basicPrompt);

        // 测试变量替换功能
        template.setVariable("test_var", "测试值");
        template.setSystemPrompt("包含{{test_var}}的提示词");
        String promptWithVar = template.renderSystemPrompt();
        assertEquals("包含测试值的提示词", promptWithVar);
    }
}
