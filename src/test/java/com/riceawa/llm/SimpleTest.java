package com.riceawa.llm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 最简单的测试，确保测试框架正常工作
 */
public class SimpleTest {
    
    @Test
    void testBasicAssertion() {
        assertTrue(true);
        assertEquals(1, 1);
        assertNotNull("test");
    }
    
    @Test
    void testStringOperations() {
        String test = "Hello World";
        assertEquals(11, test.length());
        assertTrue(test.contains("World"));
        assertFalse(test.isEmpty());
    }
    
    @Test
    void testMathOperations() {
        assertEquals(4, 2 + 2);
        assertEquals(6, 2 * 3);
        assertTrue(5 > 3);
        assertFalse(2 > 5);
    }
}
