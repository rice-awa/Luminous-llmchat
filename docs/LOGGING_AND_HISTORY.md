# LLMChat 日志系统和历史记录功能

## 概述

LLMChat模组现在包含了完善的日志系统和增强的历史记录功能，提供详细的日志记录、性能监控和历史数据管理。

## 日志系统

### 功能特性

- **多级别日志**: DEBUG, INFO, WARN, ERROR
- **多类别日志**: 系统、聊天、错误、性能、审计
- **文件轮转**: 自动按大小和时间轮转日志文件
- **异步日志**: 不阻塞游戏运行的异步日志写入
- **JSON格式**: 结构化日志便于分析
- **压缩存储**: 自动压缩旧日志文件

### 日志类别

1. **系统日志** (`system.log`)
   - 模组启动/关闭
   - 配置变更
   - 组件初始化

2. **聊天日志** (`chat.log`)
   - 所有LLM聊天记录
   - 用户请求和AI响应
   - 会话保存记录

3. **错误日志** (`error.log`)
   - 异常和错误信息
   - 配置问题
   - 服务不可用

4. **性能日志** (`performance.log`)
   - API响应时间
   - 消息处理时间
   - 系统性能指标

5. **审计日志** (`audit.log`)
   - 管理员操作
   - 配置修改
   - 敏感操作记录

### 日志配置

日志配置集成在主配置文件中：

```json
{
  "logConfig": {
    "logLevel": "INFO",
    "enableFileLogging": true,
    "enableConsoleLogging": true,
    "enableJsonFormat": true,
    "maxFileSize": 10485760,
    "maxBackupFiles": 5,
    "retentionDays": 30,
    "enableAsyncLogging": true,
    "asyncQueueSize": 1000,
    "enableSystemLog": true,
    "enableChatLog": true,
    "enableErrorLog": true,
    "enablePerformanceLog": true,
    "enableAuditLog": true
  }
}
```

### 日志管理命令

#### `/llmlog level <level>`
设置日志级别
```
/llmlog level DEBUG
/llmlog level INFO
/llmlog level WARN
/llmlog level ERROR
```

#### `/llmlog status`
显示当前日志系统状态

#### `/llmlog enable <category>`
启用指定类别的日志
```
/llmlog enable system
/llmlog enable chat
/llmlog enable error
/llmlog enable performance
/llmlog enable audit
/llmlog enable file
/llmlog enable console
```

#### `/llmlog disable <category>`
禁用指定类别的日志

#### `/llmlog test`
生成测试日志消息

## 历史记录系统

### 增强功能

- **详细元数据**: 记录模型、提供商、token使用等信息
- **统计分析**: 生成详细的使用统计报告
- **多格式导出**: 支持JSON、CSV、TXT、HTML格式
- **高级搜索**: 支持关键词、时间范围搜索
- **性能监控**: 记录响应时间和资源使用

### 历史记录管理命令

#### `/llmhistory stats [player]`
显示玩家的聊天统计信息
```
/llmhistory stats
/llmhistory stats PlayerName
```

统计信息包括：
- 总会话数和消息数
- 用户消息vs AI回复数量
- 最常用的提示词模板
- 最活跃的时间段
- 平均每会话消息数

#### `/llmhistory export <player> <format>`
导出玩家的历史记录
```
/llmhistory export PlayerName json
/llmhistory export PlayerName csv
/llmhistory export PlayerName txt
/llmhistory export PlayerName html
```

#### `/llmhistory search <player> <keyword>`
搜索包含特定关键词的历史记录
```
/llmhistory search PlayerName "minecraft"
```

#### `/llmhistory clear <player>`
清除玩家的所有历史记录
```
/llmhistory clear PlayerName
```

## 文件结构

```
config/lllmchat/
├── config.json              # 主配置文件（包含日志配置）
├── logs/                     # 日志文件目录
│   ├── system.log           # 系统日志
│   ├── chat.log             # 聊天日志
│   ├── error.log            # 错误日志
│   ├── performance.log      # 性能日志
│   ├── audit.log            # 审计日志
│   └── *.log.gz             # 压缩的历史日志
├── history/                  # 历史记录目录
│   └── <player-uuid>.json   # 玩家历史记录
└── exports/                  # 导出文件目录
    └── *.{json,csv,txt,html} # 导出的历史记录
```

## 性能考虑

- **异步处理**: 日志写入不会阻塞游戏线程
- **文件轮转**: 防止日志文件过大影响性能
- **压缩存储**: 节省磁盘空间
- **配置灵活**: 可根据需要禁用不必要的日志类别

## 隐私和安全

- **敏感信息**: API密钥等敏感信息不会记录在日志中
- **访问控制**: 日志和历史记录管理需要管理员权限
- **数据保留**: 可配置日志和历史记录的保留时间
- **审计跟踪**: 所有管理操作都会记录在审计日志中

## 故障排除

### 常见问题

1. **日志文件未生成**
   - 检查配置目录权限
   - 确认文件日志已启用
   - 查看控制台错误信息

2. **日志文件过大**
   - 调整 `maxFileSize` 配置
   - 减少 `retentionDays`
   - 禁用不必要的日志类别

3. **性能影响**
   - 启用异步日志
   - 提高日志级别（减少DEBUG日志）
   - 调整 `asyncQueueSize`

### 日志分析

使用标准工具分析JSON格式的日志：
```bash
# 查看错误日志
grep "ERROR" config/lllmchat/logs/error.log

# 分析性能日志
jq '.response_time_ms' config/lllmchat/logs/performance.log

# 统计聊天频率
grep -c "Chat request" config/lllmchat/logs/chat.log
```

## 开发者信息

### 扩展日志系统

要添加新的日志类别：

1. 在 `LogConfig` 中添加配置选项
2. 在 `LogManager` 中添加便捷方法
3. 更新配置文件结构
4. 添加相应的命令支持

### 自定义导出格式

实现 `HistoryExporter` 接口来添加新的导出格式：

```java
public ExportResult exportToCustomFormat(List<ChatSession> sessions, Path exportFile) {
    // 实现自定义导出逻辑
}
```
