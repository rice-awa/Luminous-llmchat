# LLM Chat 配置向导

## 概述

LLM Chat 模组提供了智能的配置检查和用户引导功能，让首次使用变得更加简单。

## 首次使用体验

### 自动检测配置问题

当您首次尝试使用AI聊天功能时：

```
/llmchat 你好
```

如果配置不完整，系统会自动显示友好的引导信息：

```
=== 欢迎使用 LLM Chat! ===
看起来这是您第一次使用AI聊天功能。
在开始使用之前，需要配置AI服务提供商的API密钥。

📋 配置步骤：
1. 打开配置文件: config/lllmchat/config.json
2. 选择一个AI服务提供商（OpenAI、OpenRouter、DeepSeek等）
3. 将对应的 'apiKey' 字段替换为您的真实API密钥
4. 使用 /llmchat reload 重新加载配置

💡 提示：
- 使用 /llmchat provider list 查看所有可用的服务提供商
- 使用 /llmchat help 查看所有可用命令
```

## 配置向导命令

### 使用配置向导

```
/llmchat setup
```

配置向导会显示：
- 当前配置状态
- 配置文件位置
- 可用的服务提供商及其状态
- 详细的配置步骤

示例输出：
```
=== LLM Chat 配置向导 ===

❌ 当前配置状态: 需要配置
• 当前服务提供商 'openai' 的API密钥仍为默认占位符，需要设置真实的API密钥

📋 配置文件位置:
config/lllmchat/config.json

🔧 可用的服务提供商:
• openai - ❌ 需要配置API密钥
• openrouter - ❌ 需要配置API密钥
• deepseek - ❌ 需要配置API密钥

💡 快速配置步骤:
1. 选择一个AI服务提供商（推荐OpenAI或DeepSeek）
2. 获取对应的API密钥
3. 编辑配置文件，替换 'your-xxx-api-key-here' 为真实密钥
4. 使用 /llmchat reload 重新加载配置
5. 使用 /llmchat 你好 测试功能

📚 更多帮助: /llmchat help
```

## 配置验证

### 重载配置时的自动验证

使用 `/llmchat reload` 重新加载配置后，系统会自动验证配置并给出反馈：

**配置正常时：**
```
配置已重新加载
✅ 配置验证通过，AI聊天功能可正常使用
当前服务提供商: openai
当前模型: gpt-3.5-turbo
```

**配置有问题时：**
```
配置已重新加载
⚠️ 配置验证失败，请检查以下问题:
• 当前服务提供商 'openai' 的API密钥仍为默认占位符，需要设置真实的API密钥
使用 /llmchat setup 查看配置向导
```

## 配置检查机制

### 自动检查项目

系统会自动检查以下配置项：

1. **服务提供商配置**
   - 是否存在有效的服务提供商
   - 当前选择的服务提供商是否存在

2. **API密钥验证**
   - API密钥是否为空
   - API密钥是否仍为默认占位符（包含 "your-" 或 "-api-key-here"）

3. **模型配置**
   - 是否设置了当前使用的模型
   - 当前模型是否被服务提供商支持

### 首次使用检测

系统通过以下方式检测是否为首次使用：
- 检查所有服务提供商的API密钥是否都是默认占位符
- 如果所有API密钥都未配置，则认为是首次使用

## 常见配置问题

### 问题1：API密钥为默认值
**现象：** "API密钥仍为默认占位符"
**解决：** 编辑配置文件，将 `"your-xxx-api-key-here"` 替换为真实的API密钥

### 问题2：服务提供商不存在
**现象：** "当前选择的服务提供商不存在"
**解决：** 使用 `/llmchat provider list` 查看可用的服务提供商，然后使用 `/llmchat provider switch <provider>` 切换

### 问题3：模型不支持
**现象：** "当前模型不被服务提供商支持"
**解决：** 使用 `/llmchat model list` 查看支持的模型，然后使用 `/llmchat model set <model>` 设置

## 推荐配置流程

1. **首次安装后**
   ```
   /llmchat setup  # 查看配置向导
   ```

2. **编辑配置文件**
   - 打开 `config/lllmchat/config.json`
   - 选择一个服务提供商（如 OpenAI）
   - 替换对应的API密钥

3. **重载并验证**
   ```
   /llmchat reload  # 重载配置
   ```

4. **测试功能**
   ```
   /llmchat 你好  # 测试AI聊天
   ```

## 获取API密钥

### OpenAI
1. 访问 https://platform.openai.com/api-keys
2. 创建新的API密钥
3. 复制密钥到配置文件

### DeepSeek
1. 访问 https://platform.deepseek.com/api-keys
2. 创建新的API密钥
3. 复制密钥到配置文件

### OpenRouter
1. 访问 https://openrouter.ai/keys
2. 创建新的API密钥
3. 复制密钥到配置文件

## 故障排除

如果遇到问题，请按以下顺序检查：

1. 使用 `/llmchat setup` 查看配置状态
2. 检查配置文件语法是否正确（JSON格式）
3. 确认API密钥有效且有足够余额
4. 使用 `/llmchat reload` 重新加载配置
5. 查看服务器日志获取详细错误信息

## 相关命令

- `/llmchat setup` - 显示配置向导
- `/llmchat reload` - 重新加载配置（仅OP）
- `/llmchat provider list` - 查看可用的服务提供商
- `/llmchat model list` - 查看支持的模型
- `/llmchat help` - 显示帮助信息
