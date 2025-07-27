# Help命令优化演示

## 📋 功能概述

优化后的help命令支持分层显示，提供更好的用户体验：

1. **主help命令** - 显示一级子命令概览
2. **子命令help** - 显示具体功能的详细帮助

## 🎯 使用示例

### 1. 主help命令
```bash
/llmchat help
```

**显示效果：**
```
=== LLM Chat 帮助 ===

📝 基本命令:
  /llmchat <消息> - 发送消息给AI助手
  /llmchat clear - 清空聊天历史
  /llmchat resume - 恢复上次对话内容

🔧 功能模块 (使用 /llmchat <模块> help 查看详细帮助):
  template - 提示词模板管理
  provider - AI服务提供商管理
  model - AI模型管理
  broadcast - AI聊天广播功能

⚙️ 系统命令:
  /llmchat setup - 显示配置向导
  /llmchat stats - 显示系统统计信息
  /llmchat reload - 重载配置 (仅OP)

💡 提示: 使用 /llmchat <子命令> help 查看具体功能的详细帮助
```

### 2. 子命令详细帮助

#### Template模块帮助
```bash
/llmchat template help
```

**显示效果：**
```
=== 提示词模板管理 ===

📝 可用命令:
  /llmchat template list - 列出所有可用的提示词模板
  /llmchat template set <模板ID> - 切换到指定的提示词模板

💡 说明:
  • 提示词模板定义了AI的角色和行为风格
  • 内置模板包括: default, creative, survival, redstone, mod等
  • 可在 config/lllmchat/prompt_templates.json 中自定义模板
```

#### Provider模块帮助
```bash
/llmchat provider help
```

**显示效果：**
```
=== AI服务提供商管理 ===

📡 可用命令:
  /llmchat provider list - 列出所有配置的AI服务提供商
  /llmchat provider switch <provider> - 切换到指定的服务提供商 (仅OP)

💡 说明:
  • 支持多个AI服务: OpenAI, OpenRouter, DeepSeek等
  • 每个provider需要配置API密钥和支持的模型
  • 切换provider会自动设置为该provider的第一个模型
```

#### Model模块帮助
```bash
/llmchat model help
```

**显示效果：**
```
=== AI模型管理 ===

🤖 可用命令:
  /llmchat model list - 列出当前provider支持的所有模型
  /llmchat model list <provider> - 列出指定provider支持的模型
  /llmchat model set <模型名> - 设置当前使用的AI模型 (仅OP)

💡 说明:
  • 不同模型有不同的能力和成本
  • 高级模型(如GPT-4)质量更好但成本更高
  • 可配置专用压缩模型来优化成本
```

#### Broadcast模块帮助
```bash
/llmchat broadcast help
```

**显示效果：**
```
=== AI聊天广播功能 ===

📢 基本命令:
  /llmchat broadcast enable - 开启AI聊天广播 (仅OP)
  /llmchat broadcast disable - 关闭AI聊天广播 (仅OP)
  /llmchat broadcast status - 查看当前广播状态

👥 玩家管理:
  /llmchat broadcast player help - 查看玩家管理命令详情

💡 说明:
  • 开启后，AI对话将对全服玩家可见
  • 可以设置特定玩家列表进行精确控制
  • 默认关闭以保护玩家隐私
```

#### Broadcast Player子模块帮助
```bash
/llmchat broadcast player help
```

**显示效果：**
```
=== 广播玩家管理 ===

👥 可用命令:
  /llmchat broadcast player add <玩家名> - 添加玩家到广播列表 (仅OP)
  /llmchat broadcast player remove <玩家名> - 从广播列表移除玩家 (仅OP)
  /llmchat broadcast player list - 查看当前广播玩家列表
  /llmchat broadcast player clear - 清空广播玩家列表 (仅OP)

💡 广播模式说明:
  • 列表为空: 广播所有玩家的AI对话 (全局模式)
  • 列表不为空: 只广播列表中玩家的AI对话 (特定玩家模式)
  • 可以根据需要灵活控制广播范围
```

## ✨ 优化特点

### 1. 分层设计
- **主help**: 提供功能模块概览，避免信息过载
- **子help**: 提供具体功能的详细说明

### 2. 用户友好
- **清晰分类**: 基本命令、功能模块、系统命令分类显示
- **图标标识**: 使用emoji图标增强可读性
- **提示引导**: 明确告知如何获取详细帮助

### 3. 信息完整
- **命令说明**: 每个命令都有清晰的功能描述
- **权限标识**: 明确标注需要OP权限的命令
- **使用提示**: 提供实用的使用建议和说明

### 4. 易于维护
- **模块化**: 每个功能模块有独立的help方法
- **一致性**: 所有help信息遵循统一的格式规范
- **可扩展**: 新增功能时可以轻松添加对应的help信息

## 🔧 技术实现

### 命令注册结构
```java
.then(CommandManager.literal("template")
    .then(CommandManager.literal("list")
            .executes(LLMChatCommand::handleListTemplates))
    .then(CommandManager.literal("set")
            .then(CommandManager.argument("template", StringArgumentType.word())
                    .executes(LLMChatCommand::handleSetTemplate)))
    .then(CommandManager.literal("help")
            .executes(LLMChatCommand::handleTemplateHelp)))
```

### Help方法实现
每个子命令都有对应的help处理方法：
- `handleTemplateHelp()` - 模板管理帮助
- `handleProviderHelp()` - 服务提供商帮助
- `handleModelHelp()` - 模型管理帮助
- `handleBroadcastHelp()` - 广播功能帮助
- `handleBroadcastPlayerHelp()` - 广播玩家管理帮助

## 🎯 用户体验提升

1. **降低学习成本**: 新用户可以逐步了解功能，不会被大量信息淹没
2. **提高使用效率**: 有经验的用户可以快速找到需要的具体命令
3. **减少错误操作**: 清晰的权限标识和功能说明减少误操作
4. **增强可发现性**: 分层结构帮助用户发现更多功能
