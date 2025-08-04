# 模板切换系统提示词修复总结

## 问题描述

用户发现在使用热编辑功能创建的提示词模板后，切换到其他模板时，系统提示词没有发生改变。这导致AI的行为仍然按照之前的模板运行，而不是新切换的模板。

## 问题分析

通过代码分析发现了以下问题：

### 1. 系统提示词只在新会话时添加
在`LLMChatCommand.java`的聊天处理逻辑中：
```java
// 如果是新会话，添加系统提示词
if (chatContext.getMessageCount() == 0 && template != null) {
    String systemPrompt = template.renderSystemPromptWithContext(serverPlayer, config);
    if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
        chatContext.addSystemMessage(systemPrompt);
    }
}
```

这个逻辑只在`getMessageCount() == 0`时添加系统提示词，但是：

### 2. 模板切换时的问题
当用户切换模板时：

**情况1：有历史消息**
- 调用`createNewSessionWithHistory`创建新会话并复制历史消息
- 复制后`getMessageCount() > 0`，所以不会添加新的系统提示词
- 结果：使用旧模板的系统提示词或没有系统提示词

**情况2：没有历史消息**
- 直接设置新模板ID
- 下次发送消息时，由于已经有消息了，也不会添加系统提示词
- 结果：没有系统提示词

### 3. 历史消息复制问题
`createNewSessionWithHistory`方法会复制所有历史消息，包括旧的系统消息，这导致新会话仍然使用旧模板的系统提示词。

## 修复方案

### 1. 修复历史消息复制逻辑
在`ChatContextManager.java`中修改`createNewSessionWithHistory`方法：

```java
// 复制历史消息，但跳过旧的系统消息
List<LLMMessage> oldMessages = oldContext.getMessages();
for (LLMMessage message : oldMessages) {
    // 跳过系统消息，因为我们要使用新模板的系统提示词
    if (message.getRole() != LLMMessage.MessageRole.SYSTEM) {
        newContext.addMessage(message);
    }
}
```

**修复效果**：复制历史消息时跳过旧的系统消息，为新模板的系统提示词让路。

### 2. 增强系统提示词检测逻辑
在`LLMChatCommand.java`中修改聊天处理逻辑：

```java
// 检查是否需要添加或更新系统提示词
if (template != null) {
    boolean needsSystemPrompt = false;
    
    if (chatContext.getMessageCount() == 0) {
        // 新会话，需要添加系统提示词
        needsSystemPrompt = true;
    } else {
        // 检查是否有系统消息，如果没有则需要添加
        List<LLMMessage> messages = chatContext.getMessages();
        boolean hasSystemMessage = messages.stream()
            .anyMatch(msg -> msg.getRole() == LLMMessage.MessageRole.SYSTEM);
        
        if (!hasSystemMessage) {
            needsSystemPrompt = true;
        }
    }
    
    if (needsSystemPrompt) {
        String systemPrompt = template.renderSystemPromptWithContext(serverPlayer, config);
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            chatContext.addSystemMessage(systemPrompt);
        }
    }
}
```

**修复效果**：不仅检查消息数量，还检查是否存在系统消息，确保在需要时添加系统提示词。

### 3. 添加系统消息更新方法
在`ChatContext.java`中添加`updateSystemMessage`方法：

```java
/**
 * 更新或添加系统消息（用于模板切换）
 * 如果已存在系统消息，则替换第一个系统消息；否则在开头添加
 */
public void updateSystemMessage(String content) {
    synchronized (messages) {
        // 查找第一个系统消息
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getRole() == MessageRole.SYSTEM) {
                // 替换现有的系统消息
                messages.set(i, new LLMMessage(MessageRole.SYSTEM, content));
                invalidateCharacterCache();
                updateLastActivity();
                return;
            }
        }
        
        // 如果没有找到系统消息，在开头添加
        messages.add(0, new LLMMessage(MessageRole.SYSTEM, content));
        invalidateCharacterCache();
        updateLastActivity();
    }
}
```

**修复效果**：提供了更新系统消息的能力，可以在模板切换时正确更新系统提示词。

### 4. 完善模板切换逻辑
在`LLMChatCommand.java`的`handleSetTemplate`方法中：

```java
if (currentContext.getMessageCount() > 0) {
    // 如果有历史消息，创建新会话并复制历史
    contextManager.createNewSessionWithHistory(player.getUuid(), templateId);
    
    // 获取新的上下文并添加系统提示词
    ChatContext newContext = contextManager.getContext(player);
    PromptTemplate template = templateManager.getTemplate(templateId);
    if (template != null) {
        LLMChatConfig config = LLMChatConfig.getInstance();
        String systemPrompt = template.renderSystemPromptWithContext((ServerPlayerEntity) player, config);
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            newContext.updateSystemMessage(systemPrompt);
        }
    }
    
    player.sendMessage(Text.literal("已切换到模板并创建新会话，历史消息已复制").formatted(Formatting.GREEN), false);
} else {
    // 如果没有历史消息，直接设置模板并更新系统提示词
    currentContext.setCurrentPromptTemplate(templateId);
    
    PromptTemplate template = templateManager.getTemplate(templateId);
    if (template != null) {
        LLMChatConfig config = LLMChatConfig.getInstance();
        String systemPrompt = template.renderSystemPromptWithContext((ServerPlayerEntity) player, config);
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            currentContext.updateSystemMessage(systemPrompt);
        }
    }
    
    player.sendMessage(Text.literal("已切换到模板").formatted(Formatting.GREEN), false);
}
```

**修复效果**：无论是否有历史消息，都会正确设置新模板的系统提示词。

## 修复后的工作流程

### 场景1：新会话切换模板
1. 用户执行`/llmchat template set new_template`
2. 检测到没有历史消息，直接设置模板
3. 调用`updateSystemMessage`设置新模板的系统提示词
4. 用户发送消息时，使用新模板的系统提示词

### 场景2：有历史消息时切换模板
1. 用户执行`/llmchat template set new_template`
2. 检测到有历史消息，创建新会话
3. 复制历史消息（跳过旧的系统消息）
4. 调用`updateSystemMessage`设置新模板的系统提示词
5. 用户发送消息时，使用新模板的系统提示词

### 场景3：使用热编辑创建的模板
1. 用户通过热编辑系统创建自定义模板
2. 模板保存到`PromptTemplateManager`
3. 用户切换到该模板时，按照上述流程正确应用系统提示词
4. AI行为符合新模板的设定

## 测试验证

### 测试步骤
1. 创建一个自定义模板：
   ```bash
   /llmchat template create test_template
   /llmchat template edit system 你是一个测试助手，请在回复前说"测试模式："
   /llmchat template save
   ```

2. 切换到自定义模板：
   ```bash
   /llmchat template set test_template
   ```

3. 发送消息验证系统提示词生效：
   ```bash
   /llmchat 你好
   ```
   预期：AI回复前会说"测试模式："

4. 切换到其他模板：
   ```bash
   /llmchat template set default
   ```

5. 再次发送消息验证模板切换：
   ```bash
   /llmchat 你好
   ```
   预期：AI不再说"测试模式："，使用默认模板行为

## 兼容性

- **向后兼容**：不影响现有的模板系统
- **性能影响**：最小，只在模板切换时执行额外逻辑
- **数据安全**：不会丢失历史消息，只是正确处理系统消息

## 总结

这次修复解决了模板切换时系统提示词不更新的关键问题，确保了：

1. **正确的模板应用**：切换模板后AI行为立即改变
2. **历史消息保护**：切换模板时不会丢失用户的对话历史
3. **系统消息管理**：正确处理系统消息的添加、更新和替换
4. **用户体验提升**：模板切换功能按预期工作，提供一致的体验

修复后，用户可以放心使用热编辑功能创建个性化模板，并在不同模板间自由切换，每次切换都会立即生效。
