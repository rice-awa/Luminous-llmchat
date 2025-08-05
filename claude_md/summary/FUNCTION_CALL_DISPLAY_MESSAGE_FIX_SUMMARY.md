# 函数调用提示信息显示修复总结

## 概述

修复了在LLM进行多次函数调用时，提示信息（content）没有被正确显示的问题。根据OpenAI工具调用API规范，当LLM同时返回content和tool_calls时，应该先显示content说明，然后执行函数调用。

## 问题描述

### 原始问题
用户发现在LLM进行多次函数调用时，日志中显示LLM返回了类似"让我重新获取红石粉的详细信息："这样的提示信息，但是这些信息没有在游戏中显示给玩家。

### 技术细节
在OpenAI工具调用API中，当LLM需要调用函数时，可能会同时返回：
- `content`: LLM给出的解释性文本，说明它将要做什么
- `tool_calls`: 实际的函数调用

原来的代码只处理了`tool_calls`，而忽略了`content`的显示。

## 修复内容

### 文件修改
- **文件**: `src/main/java/com/riceawa/llm/command/LLMChatCommand.java`
- **修改方法**: 
  - `handleLLMResponse()` (第1412行)
  - `handleLLMResponseWithRecursion()` (第1612行)

### 具体改动

#### 1. 修改了响应处理逻辑
**之前的逻辑**:
```java
// 检查是否有function call
if (message.getMetadata() != null && message.getMetadata().getFunctionCall() != null) {
    handleFunctionCall(...);
} else {
    // 处理普通文本响应
    String content = message.getContent();
    // ...
}
```

**修改后的逻辑**:
```java
// 先检查是否有content需要显示（LLM的提示信息）
String content = message.getContent();
boolean hasContent = content != null && !content.trim().isEmpty();

// 检查是否有函数调用
boolean hasFunctionCall = message.getMetadata() != null && message.getMetadata().getFunctionCall() != null;

if (hasContent) {
    // 显示LLM的提示信息
    // 根据广播设置发送消息
}

if (hasFunctionCall) {
    // 如果有content，先将其添加到上下文
    if (hasContent) {
        chatContext.addAssistantMessage(content);
    }
    // 处理函数调用
} else {
    // 处理纯文本响应的情况
}
```

#### 2. 确保了递归和非递归场景的一致性
修改了两个关键方法：
- `handleLLMResponse`: 处理非递归情况
- `handleLLMResponseWithRecursion`: 处理递归情况（多轮函数调用）

#### 3. 保持了消息广播功能
修复后的代码仍然支持：
- 根据配置决定是否广播消息
- 将提示信息添加到聊天上下文
- 保存会话历史

## 效果示例

### 修复前
```
[玩家询问关于红石粉的信息]
正在执行函数: wiki_page
[函数执行结果显示]
```

### 修复后  
```
[玩家询问关于红石粉的信息]
[AI] 让我重新获取红石粉的详细信息：
正在执行函数: wiki_page
[函数执行结果显示]
```

## 技术改进

### 1. 更好的用户体验
- 用户现在可以看到AI在执行函数前的思考过程
- 提供了更清晰的交互反馈

### 2. 符合API规范
- 正确实现了OpenAI工具调用API的响应处理
- 确保了content和tool_calls的正确显示顺序

### 3. 保持向后兼容
- 不影响现有的函数调用功能
- 不改变聊天历史的存储格式
- 保持与所有现有配置的兼容性

## 代码质量

### 1. 代码复用
- 两个方法使用了相同的处理逻辑
- 确保了一致的行为表现

### 2. 错误处理
- 保留了原有的错误处理机制
- 添加了对空content的检查

### 3. 上下文管理
- 正确地将提示信息添加到聊天上下文
- 保证了对话历史的完整性

## 测试建议

### 1. 基本功能测试
- 测试只有content没有函数调用的情况
- 测试只有函数调用没有content的情况  
- 测试同时有content和函数调用的情况

### 2. 递归调用测试
- 测试多轮函数调用中的提示信息显示
- 验证每轮调用的提示信息都能正确显示

### 3. 广播功能测试
- 验证广播开启时提示信息的显示
- 验证广播关闭时提示信息的显示

## 总结

这次修复解决了一个重要的用户体验问题，让LLM的思考过程对用户更加透明。修改严格遵循了OpenAI API规范，确保了功能的正确性和稳定性。同时保持了代码的一致性和向后兼容性。

修复的核心思想是将content和tool_calls视为独立但相关的响应元素，分别处理它们的显示和执行，从而提供完整的用户交互体验。