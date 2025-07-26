# 全局补充信息提示词功能

## 功能概述

全局补充信息提示词功能允许在每个AI对话的系统提示词末尾自动添加当前游戏环境的上下文信息，包括：

- 发起者玩家名称
- 当前时间
- 在线玩家列表和数量
- 游戏版本信息
- 模组版本信息

## 配置说明

### 配置文件位置
`config/lllmchat/config.json`

### 新增配置项

```json
{
  "enableGlobalContext": true,
  "globalContextPrompt": "=== 当前游戏环境信息 ===\n发起者：{{player_name}}\n当前时间：{{current_time}}\n在线玩家（{{player_count}}人）：{{online_players}}\n游戏版本：{{game_version}}"
}
```

### 配置项说明

- `enableGlobalContext`: 是否启用全局上下文功能（默认：true）
- `globalContextPrompt`: 全局上下文提示词模板

### 支持的变量

在 `globalContextPrompt` 中可以使用以下变量：

- `{{player_name}}` - 发起对话的玩家名称
- `{{current_time}}` - 当前时间（格式：yyyy-MM-dd HH:mm:ss）
- `{{online_players}}` - 在线玩家列表（最多显示10个）
- `{{player_count}}` - 在线玩家数量
- `{{game_version}}` - Minecraft版本
- `{{mod_version}}` - 模组版本

## 使用示例

### 默认配置效果

当玩家 `Steve` 在有3个在线玩家的服务器上发起对话时，AI会收到如下系统提示词：

```
你是一个有用的AI助手，在Minecraft游戏中为玩家提供帮助。请用中文回答问题，保持友好和有帮助的态度。

=== 当前游戏环境信息 ===
发起者：Steve
当前时间：2025-01-26 15:30:45
在线玩家（3人）：Steve, Alex, Notch
游戏版本：Minecraft 1.21.7
```

### 自定义配置示例

```json
{
  "enableGlobalContext": true,
  "globalContextPrompt": "当前环境：{{player_name}}正在{{game_version}}中游戏，服务器有{{player_count}}个玩家在线。时间：{{current_time}}"
}
```

效果：
```
你是一个有用的AI助手，在Minecraft游戏中为玩家提供帮助。请用中文回答问题，保持友好和有帮助的态度。

当前环境：Steve正在Minecraft 1.21.7中游戏，服务器有3个玩家在线。时间：2025-01-26 15:30:45
```

## API说明

### 新增方法

#### LLMChatConfig
- `isEnableGlobalContext()` - 获取是否启用全局上下文
- `setEnableGlobalContext(boolean)` - 设置是否启用全局上下文
- `getGlobalContextPrompt()` - 获取全局上下文提示词模板
- `setGlobalContextPrompt(String)` - 设置全局上下文提示词模板

#### PromptTemplate
- `renderSystemPromptWithContext(ServerPlayerEntity, LLMChatConfig)` - 渲染带全局上下文的系统提示词

## 注意事项

1. 全局上下文信息会在每次新对话开始时生成，不会在对话过程中更新
2. 在线玩家列表最多显示10个玩家，超过时会显示"..."
3. 如果服务器信息获取失败，会使用默认值
4. 可以通过设置 `enableGlobalContext: false` 来禁用此功能

## 兼容性

- 向后兼容：现有的 `renderSystemPrompt()` 方法仍然可用
- 新功能：只有使用 `renderSystemPromptWithContext()` 方法才会包含全局上下文
- 配置升级：现有配置文件会自动添加默认的全局上下文配置
