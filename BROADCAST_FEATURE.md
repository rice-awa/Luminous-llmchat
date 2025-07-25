# AI聊天广播功能

## 功能概述

新增了AI聊天广播功能，允许OP控制是否将玩家与AI的聊天内容广播给全服玩家。

## 配置项

在 `config/lllmchat/config.json` 中新增了 `enableBroadcast` 配置项：

```json
{
  "defaultPromptTemplate": "default",
  "defaultTemperature": 0.7,
  "defaultMaxTokens": 8192,
  "maxContextLength": 8192,
  "enableHistory": true,
  "enableFunctionCalling": true,
  "enableBroadcast": false,
  "historyRetentionDays": 30,
  "currentProvider": "openai",
  "currentModel": "gpt-3.5-turbo",
  "providers": [...]
}
```

- `enableBroadcast`: 布尔值，控制是否广播AI聊天内容
  - `false` (默认): AI回复只对发起者可见
  - `true`: AI聊天内容广播给全服玩家

## 命令使用

### 开启广播 (仅OP)
```
/llmchat broadcast enable
```
- 只有OP可以执行此命令
- 开启后会向全服广播通知消息
- 配置文件会自动更新

### 关闭广播 (仅OP)
```
/llmchat broadcast disable
```
- 只有OP可以执行此命令
- 关闭后会向全服广播通知消息
- 配置文件会自动更新

### 查看广播状态
```
/llmchat broadcast status
```
- 所有玩家都可以查看当前广播状态
- 显示当前是否开启广播以及说明

## 广播效果

### 关闭广播时 (默认)
- 玩家发送: `/llmchat 你好`
- 只有发起者看到: `正在思考...`
- 只有发起者看到: `[AI] 你好！我是AI助手，有什么可以帮助你的吗？`

### 开启广播时
- 玩家Steve发送: `/llmchat 你好`
- 全服看到: `[Steve 问AI] 你好`
- 全服看到: `[AI正在为 Steve 思考...]`
- 全服看到: `[AI回复给 Steve] 你好！我是AI助手，有什么可以帮助你的吗？`

## 权限控制

- **开启/关闭广播**: 需要OP权限 (权限等级2)
- **查看广播状态**: 所有玩家可用
- **使用AI聊天**: 所有玩家可用

## 使用场景

1. **教学演示**: OP可以开启广播，向其他玩家展示AI功能
2. **公共讨论**: 让AI参与全服讨论，所有玩家都能看到对话
3. **娱乐互动**: 创造有趣的全服AI互动体验
4. **隐私保护**: 默认关闭，保护玩家与AI的私人对话

## 注意事项

1. 广播功能会增加聊天频道的消息量
2. 建议在合适的时机开启，避免刷屏
3. Function Calling的结果不会被广播，只有AI的文本回复会被广播
4. 配置更改会立即生效并保存到配置文件
