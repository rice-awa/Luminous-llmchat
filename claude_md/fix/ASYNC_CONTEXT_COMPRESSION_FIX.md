# 异步上下文压缩修复

## 问题描述

原有的上下文压缩管理存在以下问题：

1. **压缩时机错误**：压缩在对话开始前（`addMessage`时）同步进行，会阻塞用户操作
2. **通知机制失效**：即使配置`enableCompressionNotification`为`true`也不会发送提示信息
3. **重复通知逻辑**：在`LLMChatCommand`中存在重复的压缩通知代码，且逻辑有误

## 解决方案

### 1. 修改压缩时机

**修改前**：
- 在`ChatContext.addMessage()`中同步调用`trimContext()`
- 压缩过程阻塞消息添加操作

**修改后**：
- 移除`addMessage()`中的同步压缩调用
- 在对话结束后（AI响应完成后）检测是否需要压缩
- 使用异步任务执行压缩，不阻塞用户操作

### 2. 修复通知机制

**修改前**：
- `ChatContextManager.findPlayerByUuid()`方法返回`null`
- 通知永远无法发送给玩家

**修改后**：
- 在`ContextEventListener`接口中添加带`PlayerEntity`参数的方法
- 在`LLMChatCommand`中直接传递`ServerPlayerEntity`实例
- 确保通知能够正确发送给玩家

### 3. 清理重复代码

**修改前**：
- `LLMChatCommand`中有重复的压缩通知逻辑（第561-565行）
- 逻辑错误：检查的是`getMaxContextLength()`而不是字符长度限制

**修改后**：
- 移除重复的通知代码
- 统一在`checkAndNotifyCompression()`方法中处理

## 技术实现

### 核心修改文件

1. **ChatContext.java**
   - 移除`addMessage()`中的同步压缩
   - 添加`scheduleCompressionIfNeeded()`方法
   - 添加`compressContextAsync()`异步压缩方法
   - 添加`setCurrentPlayer()`方法用于通知
   - 扩展`ContextEventListener`接口

2. **ChatContextManager.java**
   - 添加`getScheduler()`方法供异步任务使用
   - 更新`CompressionNotificationListener`支持直接玩家通知

3. **LLMChatCommand.java**
   - 移除重复的压缩通知逻辑
   - 添加`checkAndNotifyCompression()`方法
   - 在所有对话结束点调用压缩检查

### 异步压缩流程

```
用户发送消息 → AI响应 → 对话结束 → 检查是否需要压缩 → 异步压缩任务
                                    ↓
                            发送压缩开始通知 → 执行压缩 → 发送压缩完成通知
```

### 通知机制改进

```
压缩触发 → 设置当前玩家实体 → 调用事件监听器 → 直接发送通知给玩家
```

## 配置说明

压缩通知功能通过以下配置控制：

```json
{
  "enableCompressionNotification": true  // 启用压缩通知
}
```

当启用时，玩家会收到以下通知：
- 压缩开始：`⚠️ 已达到最大上下文长度，您的之前上下文将被压缩`
- 压缩成功：`✅ 上下文压缩完成，对话历史已优化`
- 压缩失败：`⚠️ 上下文压缩失败，已删除部分旧消息`

## 测试验证

创建了`TestAsyncContextCompression.java`测试文件，包含以下测试用例：

1. **异步压缩触发测试**：验证压缩能够正确异步触发
2. **不需要压缩时的测试**：验证未超过限制时不会触发压缩
3. **多次调用不重叠测试**：验证多次压缩调用不会产生竞态条件

## 优势

1. **性能提升**：压缩不再阻塞用户操作
2. **用户体验改善**：及时的压缩通知让用户了解系统状态
3. **代码简化**：移除重复逻辑，提高可维护性
4. **线程安全**：使用`volatile`标记和同步机制防止并发问题

## 向后兼容性

- 保留原有的同步压缩方法作为fallback
- 配置项保持不变
- API接口向后兼容（使用default方法扩展接口）
