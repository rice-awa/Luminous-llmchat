package com.riceawa.llm;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * 测试套件，运行所有LLM相关的测试
 */
@Suite
@SuiteDisplayName("LLM Chat Mod Test Suite")
@SelectPackages({
    "com.riceawa.llm.core",
    "com.riceawa.llm.function", 
    "com.riceawa.llm.service",
    "com.riceawa.llm.integration"
})
public class AllTestsSuite {
    // 测试套件类，不需要实现内容
}
