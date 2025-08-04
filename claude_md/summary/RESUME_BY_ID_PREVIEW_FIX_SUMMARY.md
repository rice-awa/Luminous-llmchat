# Resume子命令ID恢复对话预览修复总结

## 问题描述

在使用 `resume 数字id` 恢复对话时，不会显示前三条对话预览，而使用 `resume`（不带参数）恢复上次对话时会正常显示预览。

## 问题分析

通过代码分析发现：

1. **`handleResume` 方法**（第237行）：正确调用了 `showMessagePreview(player, historyMessages, "上次对话")` 来显示消息预览
2. **`handleResumeById` 方法**：缺少对 `showMessagePreview` 方法的调用

## 修复方案

在 `handleResumeById` 方法中添加消息预览功能，保持与 `handleResume` 方法的一致性。

### 修改位置

文件：`src/main/java/com/riceawa/llm/command/LLMChatCommand.java`
方法：`handleResumeById`（第314-378行）

### 具体修改

在第359-363行之间添加消息预览调用：

```java
// 修改前
player.sendMessage(Text.literal("已恢复对话 #" + sessionId + ": " + targetSession.getDisplayTitle() +
    "，共 " + historyMessages.size() + " 条消息").formatted(Formatting.GREEN), false);

LogManager.getInstance().chat("Player " + player.getName().getString() +
    " resumed chat session #" + sessionId + " with " + historyMessages.size() + " messages");

// 修改后
player.sendMessage(Text.literal("✅ 已恢复对话 #" + sessionId + ": " + targetSession.getDisplayTitle() +
    "，共 " + historyMessages.size() + " 条消息").formatted(Formatting.GREEN), false);

// 显示消息预览
showMessagePreview(player, historyMessages, "对话 #" + sessionId);

LogManager.getInstance().chat("Player " + player.getName().getString() +
    " resumed chat session #" + sessionId + " with " + historyMessages.size() + " messages");
```

## 修复效果

修复后，使用 `/llmchat resume <数字>` 恢复指定对话时，将会：

1. 显示恢复成功的消息（带✅图标）
2. **显示最近5条对话内容预览**，包括：
   - 消息序号
   - 角色图标（🙋你、🤖AI、⚙️系统）
   - 消息内容（超过150字符会智能截断）
3. 记录日志

### 预览功能特性

- **显示数量**：最多显示最后5条消息
- **内容截断**：消息超过150字符时智能截断（优先在句号、问号、感叹号后截断）
- **角色区分**：不同角色使用不同颜色和图标
- **会话信息**：显示会话标识（如"对话 #2"）

## 测试建议

1. 使用 `/llmchat resume list` 查看历史对话列表
2. 使用 `/llmchat resume <数字>` 恢复指定对话
3. 验证是否显示了对话预览
4. 对比 `/llmchat resume`（不带参数）的预览效果，确保一致性

## 相关文件

- `src/main/java/com/riceawa/llm/command/LLMChatCommand.java` - 主要修改文件
- `showMessagePreview` 方法 - 消息预览实现（第642-713行）

## 兼容性

此修复不会影响现有功能，只是增强了用户体验，使两种resume命令的行为保持一致。
