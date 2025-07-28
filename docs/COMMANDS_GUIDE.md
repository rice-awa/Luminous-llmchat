# 命令使用指南

## 基础命令（所有玩家可用）

### 聊天命令
```bash
/llmchat <消息>                     # 发送消息给AI助手
/llmchat clear                      # 清空聊天历史
/llmchat resume                     # 恢复最近的对话内容
/llmchat resume list                # 列出所有历史对话记录
/llmchat resume <数字>              # 恢复指定ID的对话（如 resume 2）
/llmchat help                       # 显示帮助信息
```

### 模板管理
```bash
/llmchat template list              # 列出所有可用模板
/llmchat template set <模板名>       # 切换到指定模板
```

### 信息查询
```bash
/llmchat provider list              # 列出所有配置的providers
/llmchat model list                 # 列出当前provider支持的模型
/llmchat model list <provider>      # 列出指定provider支持的模型
/llmchat broadcast status           # 查看AI聊天广播状态
/llmchat broadcast player list      # 查看广播玩家列表
/llmchat setup                      # 显示配置向导
/llmchat stats                      # 显示使用统计
```

## 管理员命令（仅OP可用）

### Provider和模型管理
```bash
/llmchat provider switch <provider> # 切换到指定的provider
/llmchat model set <模型名>          # 设置当前使用的模型
/llmchat reload                     # 热重载配置文件
```

### 广播功能管理
```bash
/llmchat broadcast enable           # 开启AI聊天广播
/llmchat broadcast disable          # 关闭AI聊天广播
/llmchat broadcast player add <玩家> # 添加玩家到广播列表
/llmchat broadcast player remove <玩家> # 从广播列表移除玩家
/llmchat broadcast player clear     # 清空广播玩家列表
```

### 日志管理命令
```bash
/llmlog level <级别>                # 设置日志级别 (DEBUG/INFO/WARN/ERROR)
/llmlog status                      # 显示日志系统状态
/llmlog enable <类别>               # 启用指定类别日志
/llmlog disable <类别>              # 禁用指定类别日志
/llmlog test                        # 生成测试日志
```

### 历史记录管理命令
```bash
/llmhistory stats [player]          # 显示玩家统计信息
/llmhistory export <player> <格式>   # 导出玩家历史记录
/llmhistory search <player> <关键词> # 搜索历史记录
/llmhistory clear <player>          # 清除玩家历史记录
```

## 使用示例

### 基本聊天
```bash
/llmchat 你好，请介绍一下Minecraft的基本玩法
```

### Function Calling 智能交互
启用Function Calling后，AI可以主动调用游戏API获取实时信息：

```bash
/llmchat 帮我查看一下当前的游戏状态
# AI会自动调用get_world_info和get_player_stats函数

/llmchat 附近有什么生物吗？
# AI会调用get_nearby_entities函数查询附近实体

/llmchat 我的背包里有什么物品？
# AI会调用get_inventory函数查看背包内容

/llmchat 帮我给所有玩家发个消息说服务器要重启了
# AI会调用send_message函数发送广播消息（需要OP权限）
```

### 对话恢复
```bash
# 恢复最近的对话
/llmchat resume
# 自动恢复最近一次对话的所有内容，包括上下文和提示词模板

# 查看历史对话列表
/llmchat resume list
# 显示所有历史对话记录，包括：
# - 会话ID（#1, #2, #3等，最新的是#1）
# - 会话标题（AI自动生成）
# - 时间戳和消息数量
# - 使用的提示词模板

# 恢复指定对话
/llmchat resume 1    # 恢复最新的对话
/llmchat resume 2    # 恢复第二新的对话
/llmchat resume 3    # 恢复第三新的对话
# 通过数字ID精确选择要恢复的历史对话
```

**使用示例**：
```bash
# 1. 查看历史记录
/llmchat resume list

# 输出示例：
# === 历史对话记录 ===
# 共找到 3 个会话
#
# #1 Minecraft助手欢迎玩家并提供帮助
#    时间: 2025-07-28 14:59:06
#    消息数: 3 条
#
# #2 关于建筑技巧的讨论
#    时间: 2025-07-28 13:45:22
#    消息数: 8 条   模板: creative
#
# #3 生存模式攻略咨询
#    时间: 2025-07-28 12:30:15
#    消息数: 12 条

# 2. 恢复指定对话
/llmchat resume 2
# 输出：已恢复对话 #2: 关于建筑技巧的讨论，共 8 条消息
```

**注意事项**：
- 只能在当前对话为空时使用resume命令
- 如果当前有正在进行的对话，需要先使用 `/llmchat clear` 清空
- 恢复对话时会自动恢复该对话使用的提示词模板
- 会话ID按时间倒序排列，最新的对话是#1

### 模板切换
```bash
/llmchat template list              # 查看所有可用模板
/llmchat template set creative      # 切换到创造模式助手模板
```

### 广播功能
```bash
# 开启广播模式（仅OP）
/llmchat broadcast enable

# 当广播开启时，所有玩家的AI对话将对全服可见：
# 玩家Steve发送消息
/llmchat 你好，AI助手

# 全服看到：
[Steve 问AI] 你好，AI助手
[AI正在为 Steve 思考...]
[AI回复给 Steve] 你好！我是AI助手，有什么可以帮助你的吗？

# 关闭广播模式
/llmchat broadcast disable
```

### 管理员功能示例
```bash
# 切换Provider和模型
/llmchat provider switch openrouter
/llmchat model set deepseek-chat

# 管理广播玩家列表
/llmchat broadcast player add Steve
/llmchat broadcast player remove Alex
/llmchat broadcast player list

# 重载配置
/llmchat reload
```

## 权限说明

### 基础权限
- 所有玩家都可以使用基本聊天功能
- 所有玩家都可以查看信息和状态
- 所有玩家都可以管理自己的模板和对话

### OP权限
- Provider和模型管理
- 广播功能控制
- 配置文件重载
- 日志系统管理
- 历史记录管理
- Function Calling中的管理员功能

### Function Calling权限
- **基础信息查询**: 所有玩家可用
- **查询其他玩家信息**: 需要OP权限或查询自己
- **服务器性能信息**: 需要OP权限
- **发送广播消息**: 需要OP权限
- **管理员功能**: 仅OP可用（执行指令、设置方块、生成实体、控制天气时间等）

## 注意事项

1. **resume命令限制**: 只能在当前对话为空时使用，如果当前有对话需要先clear
2. **广播功能**: 会增加聊天频道消息量，建议合理使用
3. **管理员命令**: 需要OP权限，请谨慎使用
4. **Function Calling**: 某些功能需要特定权限，请查看具体说明
5. **配置重载**: 修改配置后建议使用reload命令应用更改
