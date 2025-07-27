# 配置指南

## 配置文件位置

配置文件位于：`config/lllmchat/config.json`

## 完整配置示例

```json
{
  "configVersion": "1.5.1",
  "defaultPromptTemplate": "default",
  "defaultTemperature": 0.7,
  "defaultMaxTokens": 8192,
  "maxContextLength": 32768,
  "enableHistory": true,
  "enableFunctionCalling": true,
  "enableBroadcast": false,
  "historyRetentionDays": 30,
  "compressionModel": "gpt-3.5-turbo",
  "enableCompressionNotification": true,
  "enableGlobalContext": true,
  "globalContextPrompt": "=== 当前游戏环境信息 ===\n发起者：{{player_name}}\n当前时间：{{current_time}}\n在线玩家（{{player_count}}人）：{{online_players}}\n游戏版本：{{game_version}}",
  "currentProvider": "openai",
  "currentModel": "gpt-4",
  "providers": [
    {
      "name": "openai",
      "apiBaseUrl": "https://api.openai.com/v1",
      "apiKey": "your-openai-api-key-here",
      "models": [
        "gpt-3.5-turbo",
        "gpt-4",
        "gpt-4-turbo",
        "gpt-4o"
      ]
    },
    {
      "name": "openrouter",
      "apiBaseUrl": "https://openrouter.ai/api/v1",
      "apiKey": "your-openrouter-api-key-here",
      "models": [
        "openai/gpt-4",
        "anthropic/claude-3-opus",
        "google/gemini-pro"
      ]
    },
    {
      "name": "deepseek",
      "apiBaseUrl": "https://api.deepseek.com/v1",
      "apiKey": "your-deepseek-api-key-here",
      "models": [
        "deepseek-chat",
        "deepseek-reasoner"
      ]
    }
  ]
}
```

## 核心配置项

### 基础设置
| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `defaultPromptTemplate` | String | `"default"` | 默认提示词模板 |
| `defaultTemperature` | Double | `0.7` | 默认温度参数 |
| `defaultMaxTokens` | Integer | `8192` | 默认最大token数 |
| `maxContextLength` | Integer | `32768` | 最大上下文长度 |

### 功能开关
| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enableHistory` | Boolean | `true` | 启用历史记录保存 |
| `enableFunctionCalling` | Boolean | `true` | 启用Function Calling |
| `enableBroadcast` | Boolean | `false` | 启用AI聊天广播 |
| `enableGlobalContext` | Boolean | `true` | 启用全局上下文信息 |

### 上下文压缩配置
| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `compressionModel` | String | `""` | 压缩专用模型（空=使用当前模型） |
| `enableCompressionNotification` | Boolean | `true` | 启用压缩通知 |

### 历史记录配置
| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `historyRetentionDays` | Integer | `30` | 历史记录保留天数 |

### Provider配置
| 配置项 | 类型 | 说明 |
|--------|------|------|
| `currentProvider` | String | 当前使用的Provider |
| `currentModel` | String | 当前使用的模型 |
| `providers` | Array | Provider配置列表 |

## Provider配置详解

### Provider结构
```json
{
  "name": "provider-name",
  "apiBaseUrl": "https://api.example.com/v1",
  "apiKey": "your-api-key-here",
  "models": [
    "model-1",
    "model-2"
  ]
}
```

### 支持的Provider

#### OpenAI
```json
{
  "name": "openai",
  "apiBaseUrl": "https://api.openai.com/v1",
  "apiKey": "sk-...",
  "models": ["gpt-3.5-turbo", "gpt-4", "gpt-4-turbo", "gpt-4o"]
}
```

#### OpenRouter
```json
{
  "name": "openrouter",
  "apiBaseUrl": "https://openrouter.ai/api/v1",
  "apiKey": "sk-or-v1-...",
  "models": ["openai/gpt-4", "anthropic/claude-3-opus", "google/gemini-pro"]
}
```

#### DeepSeek
```json
{
  "name": "deepseek",
  "apiBaseUrl": "https://api.deepseek.com/v1",
  "apiKey": "sk-...",
  "models": ["deepseek-chat", "deepseek-reasoner"]
}
```

## 高级配置

### 并发设置
```json
{
  "concurrencySettings": {
    "maxConcurrentRequests": 10,
    "queueCapacity": 100,
    "requestTimeoutMs": 30000,
    "corePoolSize": 5,
    "maximumPoolSize": 20,
    "keepAliveTimeMs": 60000
  }
}
```

### 日志配置
```json
{
  "logConfig": {
    "enableSystemLog": true,
    "enableChatLog": true,
    "enableErrorLog": true,
    "enablePerformanceLog": true,
    "enableAuditLog": true,
    "logLevel": "INFO",
    "maxFileSize": 10485760,
    "maxFiles": 10,
    "retentionDays": 30,
    "compressionEnabled": true
  }
}
```

### 广播配置
```json
{
  "enableBroadcast": false,
  "broadcastPlayers": ["player1", "player2"]
}
```

## 配置最佳实践

### 成本优化
```json
{
  "currentModel": "gpt-4",           // 聊天用高质量模型
  "compressionModel": "gpt-3.5-turbo", // 压缩用经济模型
  "defaultMaxTokens": 4096           // 控制单次请求token数
}
```

### 性能优化
```json
{
  "maxContextLength": 16384,         // 根据需要调整上下文长度
  "enableCompressionNotification": false, // 高级用户可关闭通知
  "historyRetentionDays": 7          // 减少历史记录保留时间
}
```

### 安全配置
```json
{
  "enableFunctionCalling": true,     // 启用Function Calling
  "enableBroadcast": false,          // 默认关闭广播保护隐私
  "broadcastPlayers": []             // 空列表=全局广播，有内容=特定玩家
}
```

## 配置升级

### 自动升级
- 模组会自动检测配置版本
- 自动添加缺失的配置项
- 保留现有配置不变

### 手动升级
如果需要手动升级配置：
1. 备份现有配置文件
2. 删除配置文件让系统重新生成
3. 手动迁移自定义设置

### 配置验证
使用 `/llmchat setup` 命令检查配置状态：
- 验证API密钥有效性
- 检查Provider连接状态
- 显示配置建议

## 故障排除

### 常见问题
1. **API密钥无效**: 检查密钥格式和权限
2. **模型不支持**: 确认Provider支持指定模型
3. **连接超时**: 检查网络连接和API地址
4. **配置丢失**: 使用 `/llmchat reload` 重新加载

### 配置重置
如果配置出现问题：
1. 备份当前配置
2. 删除 `config/lllmchat/config.json`
3. 重启服务器或使用 `/llmchat reload`
4. 系统会生成默认配置
