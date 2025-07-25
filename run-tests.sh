#!/bin/bash

# LLMChatMod Function Calling 测试运行脚本

echo "🚀 开始运行 LLMChatMod Function Calling 测试..."
echo "=================================================="

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "❌ 错误: 未找到Java环境，请安装Java 21"
    exit 1
fi

# 检查Gradle
if [ ! -f "./gradlew" ]; then
    echo "❌ 错误: 未找到gradlew，请确保在项目根目录运行此脚本"
    exit 1
fi

# 给gradlew执行权限
chmod +x ./gradlew

echo "📋 运行测试选项:"
echo "1. 运行所有测试"
echo "2. 运行核心类测试"
echo "3. 运行函数测试"
echo "4. 运行服务测试"
echo "5. 运行集成测试"
echo "6. 生成测试报告"
echo "7. 运行特定测试类"

read -p "请选择 (1-7): " choice

case $choice in
    1)
        echo "🧪 运行所有测试..."
        ./gradlew test
        ;;
    2)
        echo "🧪 运行核心类测试..."
        ./gradlew test --tests "com.riceawa.llm.core.*"
        ;;
    3)
        echo "🧪 运行函数测试..."
        ./gradlew test --tests "com.riceawa.llm.function.*"
        ;;
    4)
        echo "🧪 运行服务测试..."
        ./gradlew test --tests "com.riceawa.llm.service.*"
        ;;
    5)
        echo "🧪 运行集成测试..."
        ./gradlew test --tests "com.riceawa.llm.integration.*"
        ;;
    6)
        echo "📊 生成测试报告..."
        ./gradlew test jacocoTestReport
        echo "📄 测试报告生成在: build/reports/tests/test/index.html"
        echo "📄 覆盖率报告生成在: build/reports/jacoco/test/html/index.html"
        ;;
    7)
        echo "可用的测试类:"
        echo "- com.riceawa.llm.core.LLMConfigTest"
        echo "- com.riceawa.llm.core.LLMMessageTest"
        echo "- com.riceawa.llm.function.FunctionRegistryTest"
        echo "- com.riceawa.llm.function.impl.WorldInfoFunctionTest"
        echo "- com.riceawa.llm.service.OpenAIServiceTest"
        echo "- com.riceawa.llm.integration.FunctionCallingIntegrationTest"
        echo ""
        read -p "请输入测试类名: " test_class
        echo "🧪 运行测试类: $test_class"
        ./gradlew test --tests "$test_class"
        ;;
    *)
        echo "❌ 无效选择"
        exit 1
        ;;
esac

# 检查测试结果
if [ $? -eq 0 ]; then
    echo ""
    echo "✅ 测试运行完成!"
    echo "📄 详细报告请查看: build/reports/tests/test/index.html"
else
    echo ""
    echo "❌ 测试运行失败，请检查错误信息"
    exit 1
fi

echo ""
echo "🎯 测试统计信息:"
echo "- 测试框架: JUnit 5 + Mockito"
echo "- 覆盖模块: 核心类、函数系统、服务层、集成测试"
echo "- 测试类型: 单元测试 + 集成测试"
echo "- API兼容性: OpenAI Function Calling API"
echo ""
echo "📚 更多信息请参考 TESTING_GUIDE.md"
