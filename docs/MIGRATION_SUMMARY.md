# 配置系统重构总结

## 概述
已成功将LLM Chat模组的配置系统从旧版单一API配置重构为新版多Provider配置系统，并移除了所有旧版配置相关代码。

## 主要变更

### 1. 配置结构变更
**移除的旧版字段：**
- `openaiApiKey`
- `openaiBaseUrl` 
- `defaultModel`
- `defaultService`
- `customApiKeys`
- `customBaseUrls`

**新增的Provider配置：**
- `providers[]` - Provider配置数组
- `currentProvider` - 当前使用的Provider
- `currentModel` - 当前使用的模型

### 2. 新版配置格式
```json
{
  "defaultPromptTemplate": "default",
  "defaultTemperature": 0.7,
  "defaultMaxTokens": 2048,
  "maxContextLength": 4000,
  "enableHistory": true,
  "enableFunctionCalling": false,
  "historyRetentionDays": 30,
  "currentProvider": "openrouter",
  "currentModel": "anthropic/claude-3.5-sonnet",
  "providers": [
    {
      "name": "openrouter",
      "apiBaseUrl": "https://openrouter.ai/api/v1/chat/completions",
      "apiKey": "sk-xxx",
      "models": ["model1", "model2"]
    }
  ]
}
```

### 3. 代码变更

#### LLMChatConfig.java
- 移除所有旧版配置字段和相关方法
- 移除向后兼容性代码（`migrateFromLegacyConfig`）
- 简化ConfigData内部类
- 保留Provider相关的配置管理方法

#### LLMServiceManager.java
- 移除`initializeLegacyServices`方法
- 简化`initializeServices`方法，只使用Provider配置
- 保留Provider动态管理功能

#### LLMChatCommand.java
- 移除旧版命令：`/llmchat config model` 和 `/llmchat config service`
- 移除对应的处理方法：`handleSetModel` 和 `handleSetService`
- 更新帮助信息，移除旧版命令说明
- 修改聊天处理逻辑，要求用户必须设置模型

### 4. 新增功能
- **配置热重载**：`/llmchat reload`
- **Provider管理**：
  - `/llmchat provider list` - 列出所有Provider
  - `/llmchat provider switch <provider>` - 切换Provider
- **模型管理**：
  - `/llmchat model list [provider]` - 列出模型
  - `/llmchat model set <model>` - 设置当前模型

### 5. 支持的API服务
- OpenRouter
- DeepSeek
- OpenAI
- 本地API服务
- 任何OpenAI兼容的API服务

## 使用指南


### 运行时管理
1. 使用 `/llmchat provider list` 查看可用Provider
2. 使用 `/llmchat provider switch <provider>` 切换Provider
3. 使用 `/llmchat model list` 查看当前Provider的模型
4. 使用 `/llmchat model set <model>` 设置模型

## 注意事项
- 旧版配置格式不再支持，需要手动迁移到新格式
- 必须设置当前模型才能使用聊天功能
- 所有Provider必须配置有效的API密钥和模型列表
- 支持配置文件热重载，无需重启游戏

## 兼容性
- ✅ 支持多种API服务
- ✅ 配置文件热重载
- ✅ 游戏内动态切换
- ❌ 不再支持旧版配置格式
- ❌ 移除了旧版命令
