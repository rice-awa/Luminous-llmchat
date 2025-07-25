# LLMChatMod Function Calling 测试指南

## 测试概述

本项目包含了全面的单元测试和集成测试，用于验证Function Calling功能的正确性和可靠性。

## 测试结构

```
src/test/java/com/riceawa/llm/
├── core/                           # 核心类测试
│   ├── LLMConfigTest.java         # LLM配置测试
│   └── LLMMessageTest.java        # 消息类测试
├── function/                       # 函数相关测试
│   ├── FunctionRegistryTest.java  # 函数注册表测试
│   └── impl/                      # 具体函数实现测试
│       └── WorldInfoFunctionTest.java
├── service/                        # 服务层测试
│   └── OpenAIServiceTest.java     # OpenAI服务测试
├── integration/                    # 集成测试
│   └── FunctionCallingIntegrationTest.java
└── AllTestsSuite.java             # 测试套件
```

## 测试覆盖范围

### 1. 核心类测试 (core/)

#### LLMConfigTest
- ✅ 默认配置验证
- ✅ Setter/Getter方法测试
- ✅ ToolDefinition创建和序列化
- ✅ FunctionDefinition构造函数测试
- ✅ 复杂工具配置测试

#### LLMMessageTest
- ✅ 基本消息创建
- ✅ 消息角色值验证
- ✅ Tool消息支持
- ✅ FunctionCall数据结构
- ✅ MessageMetadata功能
- ✅ 消息序列化/反序列化

### 2. 函数功能测试 (function/)

#### FunctionRegistryTest
- ✅ 函数注册/注销
- ✅ 工具定义生成
- ✅ 函数执行
- ✅ 权限控制
- ✅ 错误处理

#### WorldInfoFunctionTest
- ✅ 基本世界信息获取
- ✅ 详细信息模式
- ✅ 不同天气条件
- ✅ 时间计算
- ✅ 维度识别
- ✅ 异常处理

### 3. 服务层测试 (service/)

#### OpenAIServiceTest
- ✅ 基本聊天请求
- ✅ Function Calling请求
- ✅ Tool消息处理
- ✅ 错误响应处理
- ✅ 服务可用性检查
- ✅ 使用MockWebServer模拟API

### 4. 集成测试 (integration/)

#### FunctionCallingIntegrationTest
- ✅ 完整的Tool Calling流程
- ✅ 工具定义生成
- ✅ 函数执行验证
- ✅ 端到端测试

## 运行测试

### 前提条件

确保已安装Java 21和Gradle。

### 运行所有测试

```bash
./gradlew test
```

### 运行特定测试类

```bash
# 运行核心类测试
./gradlew test --tests "com.riceawa.llm.core.*"

# 运行函数测试
./gradlew test --tests "com.riceawa.llm.function.*"

# 运行服务测试
./gradlew test --tests "com.riceawa.llm.service.*"

# 运行集成测试
./gradlew test --tests "com.riceawa.llm.integration.*"
```

### 运行单个测试

```bash
./gradlew test --tests "com.riceawa.llm.core.LLMConfigTest"
```

### 生成测试报告

```bash
./gradlew test jacocoTestReport
```

测试报告将生成在 `build/reports/tests/test/index.html`

## 测试特性

### 1. Mock框架使用

- **Mockito**: 用于模拟Minecraft相关对象
- **MockWebServer**: 用于模拟HTTP API调用
- **JUnit 5**: 现代化的测试框架

### 2. 测试数据

测试使用了真实的OpenAI API响应格式，确保与实际API的兼容性：

```json
{
    "id": "chatcmpl-123",
    "model": "gpt-4o", 
    "choices": [{
        "message": {
            "role": "assistant",
            "tool_calls": [{
                "id": "call_abc123",
                "type": "function",
                "function": {
                    "name": "get_weather",
                    "arguments": "{\"location\": \"Beijing\"}"
                }
            }]
        }
    }]
}
```

### 3. 边界条件测试

- 空参数处理
- 无效参数验证
- 权限检查
- 异常情况处理
- 网络错误模拟

## 测试最佳实践

### 1. 测试命名

测试方法使用描述性命名：
- `testBasicWorldInfo()` - 测试基本功能
- `testDetailedWorldInfo()` - 测试详细模式
- `testErrorHandling()` - 测试错误处理

### 2. 测试结构

每个测试遵循AAA模式：
- **Arrange**: 设置测试数据和mock
- **Act**: 执行被测试的方法
- **Assert**: 验证结果

### 3. Mock使用

合理使用Mock对象，避免测试Minecraft内部实现：

```java
@Mock
private PlayerEntity mockPlayer;

@Mock 
private MinecraftServer mockServer;

@BeforeEach
void setUp() {
    when(mockPlayer.getWorld()).thenReturn(mockWorld);
    when(mockWorld.getDifficulty()).thenReturn(Difficulty.NORMAL);
}
```

## 持续集成

测试可以集成到CI/CD流程中：

```yaml
# GitHub Actions示例
- name: Run Tests
  run: ./gradlew test

- name: Generate Test Report
  run: ./gradlew jacocoTestReport

- name: Upload Coverage
  uses: codecov/codecov-action@v1
```

## 故障排除

### 常见问题

1. **测试超时**: 增加MockWebServer的响应时间
2. **Mock对象未正确设置**: 检查@BeforeEach中的mock配置
3. **序列化问题**: 验证JSON格式和Gson配置

### 调试技巧

1. 使用`@DisplayName`为测试添加描述性名称
2. 在测试中添加日志输出
3. 使用IDE的调试功能逐步执行

## 测试覆盖率目标

- **行覆盖率**: > 80%
- **分支覆盖率**: > 70%
- **方法覆盖率**: > 90%

## 贡献指南

添加新功能时，请确保：

1. 为新功能编写对应的单元测试
2. 更新集成测试以覆盖新的交互场景
3. 保持测试覆盖率不低于现有水平
4. 遵循现有的测试命名和结构约定

## 总结

这套测试框架确保了Function Calling功能的：
- **正确性**: 验证功能按预期工作
- **可靠性**: 测试各种边界条件和错误场景
- **兼容性**: 确保与OpenAI API格式的兼容
- **可维护性**: 清晰的测试结构便于维护和扩展
