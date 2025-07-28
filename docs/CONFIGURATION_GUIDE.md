# LLMChat 配置指南

## 🎯 概述

LLMChat 采用智能配置系统，支持多Provider自动切换、配置验证和故障恢复。本指南将帮助您完成配置和优化。

## 📁 配置文件位置

配置文件位于：`config/lllmchat/config.json`

## 🚀 快速开始

### 首次使用
1. 启动服务器，系统会自动生成默认配置
2. 使用 `/llmchat setup` 检查配置状态
3. 编辑配置文件，设置您的API密钥
4. 使用 `/llmchat reload` 重新加载配置

### 智能配置检测
系统会自动：
- 检测API密钥有效性
- 在配置失效时自动切换到可用Provider
- 验证配置完整性并自动修复

## 📋 完整配置示例

```json
{
  "configVersion": "2.0.0",
  "defaultPromptTemplate": "default",
  "defaultTemperature": 0.7,
  "defaultMaxTokens": 8192,
  "maxContextCharacters": 60000,
  "enableHistory": true,
  "enableFunctionCalling": false,
  "enableBroadcast": false,
  "historyRetentionDays": 30,
  "compressionModel": "",
  "enableCompressionNotification": true,
  "enableGlobalContext": true,
  "globalContextPrompt": "=== 当前游戏环境信息 ===\n发起者：{{player_name}}\n当前时间：{{current_time}}\n在线玩家（{{player_count}}人）：{{online_players}}\n游戏版本：{{game_version}}",
  "currentProvider": "openai",
  "currentModel": "gpt-4o",
  "providers": [
    {
      "name": "openai",
      "apiBaseUrl": "https://api.openai.com/v1",
      "apiKey": "your-api-key-here",
      "models": [
        "gpt-3.5-turbo",
        "gpt-4",
        "gpt-4-turbo",
        "gpt-4o",
        "gpt-4o-mini"
      ]
    },
    {
      "name": "openrouter",
      "apiBaseUrl": "https://openrouter.ai/api/v1",
      "apiKey": "your-api-key-here",
      "models": [
        "anthropic/claude-3.5-sonnet",
        "google/gemini-2.5-pro-preview",
        "anthropic/claude-sonnet-4",
        "openai/gpt-4o",
        "meta-llama/llama-3.1-405b-instruct"
      ]
    },
    {
      "name": "deepseek",
      "apiBaseUrl": "https://api.deepseek.com/v1",
      "apiKey": "your-api-key-here",
      "models": [
        "deepseek-chat",
        "deepseek-reasoner"
      ]
    },
    {
      "name": "anthropic",
      "apiBaseUrl": "https://api.anthropic.com/v1",
      "apiKey": "your-api-key-here",
      "models": [
        "claude-3.5-sonnet",
        "claude-3-opus",
        "claude-3-haiku"
      ]
    },
    {
      "name": "google",
      "apiBaseUrl": "https://generativelanguage.googleapis.com/v1beta",
      "apiKey": "your-api-key-here",
      "models": [
        "gemini-2.5-pro-preview",
        "gemini-1.5-pro",
        "gemini-1.5-flash"
      ]
    }
  ]
}
```

## ⚙️ 核心配置项

### 🔧 基础设置
| 配置项 | 类型 | 默认值 | 说明 | 验证范围 |
|--------|------|--------|------|----------|
| `defaultPromptTemplate` | String | `"default"` | 默认提示词模板 | - |
| `defaultTemperature` | Double | `0.7` | 默认温度参数 | 0.0 - 2.0 |
| `defaultMaxTokens` | Integer | `8192` | 默认最大token数 | 1 - 1,000,000 |
| `maxContextCharacters` | Integer | `100000` | 最大上下文字符数 | 1 - 1,000,000 |

### 🎛️ 功能开关
| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enableHistory` | Boolean | `true` | 启用历史记录保存 |
| `enableFunctionCalling` | Boolean | `false` | 启用Function Calling |
| `enableBroadcast` | Boolean | `false` | 启用AI聊天广播 |
| `enableGlobalContext` | Boolean | `true` | 启用全局上下文信息 |

### 🗜️ 上下文压缩配置
| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `compressionModel` | String | `""` | 压缩专用模型（空=使用当前模型） |
| `enableCompressionNotification` | Boolean | `true` | 启用压缩通知 |

### 📚 历史记录配置
| 配置项 | 类型 | 默认值 | 说明 | 验证范围 |
|--------|------|--------|------|----------|
| `historyRetentionDays` | Integer | `30` | 历史记录保留天数 | 1 - 365 |

### 🔌 Provider配置
| 配置项 | 类型 | 说明 |
|--------|------|------|
| `currentProvider` | String | 当前使用的Provider（自动选择） |
| `currentModel` | String | 当前使用的模型（自动选择） |
| `providers` | Array | Provider配置列表 |

> **💡 智能提示**: 系统会自动验证配置值的有效性，无效值会自动重置为默认值。

## 🔌 Provider配置详解

### 🏗️ Provider结构
```json
{
  "name": "provider-name",           // Provider唯一标识
  "apiBaseUrl": "https://api.example.com/v1",  // API基础URL
  "apiKey": "your-api-key-here",     // API密钥
  "models": [                        // 支持的模型列表
    "model-1",
    "model-2"
  ]
}
```

### 🤖 支持的Provider

#### OpenAI
```json
{
  "name": "openai",
  "apiBaseUrl": "https://api.openai.com/v1",
  "apiKey": "sk-...",
  "models": [
    "gpt-3.5-turbo",
    "gpt-4",
    "gpt-4-turbo",
    "gpt-4o",
    "gpt-4o-mini"
  ]
}
```

#### OpenRouter (多模型聚合)
```json
{
  "name": "openrouter",
  "apiBaseUrl": "https://openrouter.ai/api/v1",
  "apiKey": "sk-or-v1-...",
  "models": [
    "anthropic/claude-3.5-sonnet",
    "google/gemini-2.5-pro-preview",
    "anthropic/claude-sonnet-4",
    "openai/gpt-4o",
    "meta-llama/llama-3.1-405b-instruct"
  ]
}
```

#### DeepSeek
```json
{
  "name": "deepseek",
  "apiBaseUrl": "https://api.deepseek.com/v1",
  "apiKey": "sk-...",
  "models": [
    "deepseek-chat",
    "deepseek-reasoner"
  ]
}
```

#### Anthropic
```json
{
  "name": "anthropic",
  "apiBaseUrl": "https://api.anthropic.com/v1",
  "apiKey": "sk-ant-...",
  "models": [
    "claude-3.5-sonnet",
    "claude-3-opus",
    "claude-3-haiku"
  ]
}
```

#### Google AI
```json
{
  "name": "google",
  "apiBaseUrl": "https://generativelanguage.googleapis.com/v1beta",
  "apiKey": "AIza...",
  "models": [
    "gemini-2.5-pro-preview",
    "gemini-1.5-pro",
    "gemini-1.5-flash"
  ]
}
```

### 🔍 API密钥检测
系统会自动检测以下无效密钥：
- `your-api-key-here` 等占位符
- 包含 `placeholder`、`example` 的密钥
- 太短的 `sk-` 开头密钥（< 20字符）
- 空值或null值

## 🚀 智能配置特性

### 🔄 自动故障切换
系统具备智能故障切换能力：
- **自动检测**: 检测当前Provider和Model的有效性
- **智能切换**: 配置失效时自动切换到可用的Provider
- **无缝体验**: 用户无感知的配置修复

### 📊 配置状态监控
使用命令检查配置状态：
```bash
/llmchat setup    # 显示详细配置报告
/llmchat reload   # 重新加载并验证配置
```

配置报告示例：
```
Provider配置状态: 2/5 有效
✅ 有效的Provider列表:
  - openai: 5个模型可用
  - deepseek: 2个模型可用
⚠️ 无效的Provider列表:
  - openrouter: API密钥为占位符，需要设置真实密钥
  - anthropic: API密钥为占位符，需要设置真实密钥
  - google: API密钥为占位符，需要设置真实密钥
```

## ⚙️ 高级配置

### 🔀 并发设置
```json
{
  "concurrencySettings": {
    "maxConcurrentRequests": 10,    // 最大并发请求数
    "queueCapacity": 100,           // 队列容量
    "requestTimeoutMs": 30000,      // 请求超时时间(毫秒)
    "corePoolSize": 5,              // 核心线程池大小
    "maximumPoolSize": 20,          // 最大线程池大小
    "keepAliveTimeMs": 60000        // 线程保活时间(毫秒)
  }
}
```

### 📝 日志配置
```json
{
  "logConfig": {
    "enableSystemLog": true,        // 启用系统日志
    "enableChatLog": true,          // 启用聊天日志
    "enableErrorLog": true,         // 启用错误日志
    "enablePerformanceLog": true,   // 启用性能日志
    "enableAuditLog": true,         // 启用审计日志
    "logLevel": "INFO",             // 日志级别
    "maxFileSize": 10485760,        // 最大文件大小(字节)
    "maxFiles": 10,                 // 最大文件数量
    "retentionDays": 30,            // 保留天数
    "compressionEnabled": true      // 启用压缩
  }
}
```

### 📢 广播配置
```json
{
  "enableBroadcast": false,         // 启用广播功能
  "broadcastPlayers": [             // 广播目标玩家
    "player1",
    "player2"
  ]
}
```
> **注意**: 空数组表示全局广播，有内容表示仅向指定玩家广播

## 💡 配置最佳实践

### 💰 成本优化策略
```json
{
  "currentModel": "gpt-4o",              // 主聊天用高质量模型
  "compressionModel": "gpt-4o-mini",     // 压缩用经济模型
  "defaultMaxTokens": 4096,              // 控制单次请求token数
  "maxContextCharacters": 50000          // 适中的上下文长度
}
```

### ⚡ 性能优化策略
```json
{
  "maxContextCharacters": 80000,         // 根据需要调整上下文长度
  "enableCompressionNotification": false, // 高级用户可关闭通知
  "historyRetentionDays": 7,             // 减少历史记录保留时间
  "defaultTemperature": 0.5              // 降低温度提高响应速度
}
```

### 🔒 安全配置策略
```json
{
  "enableFunctionCalling": false,        // 谨慎启用Function Calling
  "enableBroadcast": false,              // 默认关闭广播保护隐私
  "broadcastPlayers": [],                // 空列表=全局广播，有内容=特定玩家
  "enableGlobalContext": true            // 启用上下文信息
}
```

### 🎯 多Provider配置策略
```json
{
  "providers": [
    {
      "name": "primary",
      "apiKey": "your-primary-key",      // 主要Provider
      "models": ["gpt-4o", "gpt-4"]
    },
    {
      "name": "backup",
      "apiKey": "your-backup-key",       // 备用Provider
      "models": ["gpt-3.5-turbo"]
    },
    {
      "name": "economic",
      "apiKey": "your-economic-key",     // 经济型Provider
      "models": ["deepseek-chat"]
    }
  ]
}
```
> **💡 提示**: 系统会自动在Provider间切换，确保服务可用性

## 🔄 智能配置管理

### 🤖 自动配置修复
系统具备强大的自动修复能力：
- **配置验证**: 启动时自动验证所有配置项
- **默认值恢复**: 无效配置自动重置为默认值
- **Provider切换**: 失效Provider自动切换到可用选项
- **配置完整性**: 缺失配置项自动补充

### 📋 配置检查命令
```bash
/llmchat setup     # 显示详细配置状态报告
/llmchat reload    # 重新加载并验证配置
/llmchat config    # 显示当前配置摘要
```

### 🔧 配置修复流程
1. **自动检测**: 系统启动时自动检测配置问题
2. **智能修复**: 自动修复可修复的问题
3. **用户提示**: 显示需要用户手动处理的问题
4. **配置保存**: 修复后自动保存配置

## 🛠️ 故障排除

### ❓ 常见问题及解决方案

#### 🔑 API密钥问题
| 问题 | 原因 | 解决方案 |
|------|------|----------|
| "API密钥为占位符" | 使用默认占位符密钥 | 设置真实的API密钥 |
| "API密钥太短" | 密钥格式不正确 | 检查密钥完整性 |
| "认证失败" | 密钥无效或过期 | 重新生成API密钥 |

#### 🤖 模型问题
| 问题 | 原因 | 解决方案 |
|------|------|----------|
| "模型不支持" | Provider不支持指定模型 | 检查模型列表或切换Provider |
| "模型访问被拒绝" | 账户权限不足 | 升级账户或使用其他模型 |
| "模型已废弃" | 使用了已停用的模型 | 更新到最新模型 |

#### 🌐 网络问题
| 问题 | 原因 | 解决方案 |
|------|------|----------|
| "连接超时" | 网络连接问题 | 检查网络连接和防火墙 |
| "API地址无法访问" | URL错误或服务不可用 | 验证API基础URL |
| "请求频率限制" | 超出API调用限制 | 降低请求频率或升级账户 |

### 🔄 配置重置方案

#### 方案1: 软重置（推荐）
```bash
/llmchat reload    # 重新加载配置，保留自定义设置
```

#### 方案2: 配置修复
```bash
/llmchat setup     # 查看问题
# 手动编辑配置文件修复问题
/llmchat reload    # 重新加载
```

#### 方案3: 完全重置
```bash
# 1. 备份配置（可选）
cp config/lllmchat/config.json config/lllmchat/config.json.backup

# 2. 删除配置文件
rm config/lllmchat/config.json

# 3. 重新加载（会生成默认配置）
/llmchat reload
```

### 🆘 紧急恢复
如果系统完全无法工作：
1. **停止服务器**
2. **删除整个配置目录**: `rm -rf config/lllmchat/`
3. **重启服务器**
4. **系统会自动生成全新的默认配置**
5. **重新设置API密钥**

## 📞 获取帮助

### 🔍 诊断信息收集
遇到问题时，请收集以下信息：
- 配置文件内容（隐藏API密钥）
- `/llmchat setup` 命令输出
- 服务器日志中的错误信息
- 使用的Minecraft和模组版本

### 📚 更多资源
- **GitHub Issues**: 报告Bug和功能请求
- **Wiki文档**: 详细的使用说明
- **社区论坛**: 用户交流和经验分享
