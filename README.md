# Luminous LLM Chat Mod for Minecraft Fabric 1.21.7

一个让人眼前一亮的Minecraft Fabric模组，集成了LLM（大语言模型）聊天功能，支持多种AI服务和自定义功能。

## 功能特性

### 🤖 LLM集成
- 支持 OpenAI 请求格式
- 可扩展的服务架构，易于添加其他LLM服务（Claude、Gemini等）
- 异步请求处理，不阻塞游戏运行

### 💬 智能聊天
- 上下文感知的对话系统
- 自动管理对话历史和上下文长度
- 支持多玩家独立会话

### 📝 提示词模板系统
- 内置多种预设模板（默认助手、创造助手、生存助手、红石工程师、模组助手）
- 支持自定义模板和变量替换
- 动态模板切换

### 📚 历史记录管理
- 持久化存储聊天记录
- 支持历史记录搜索
- 自动清理过期记录

### 🔧 Function Calling（预留）
- 可扩展的函数调用框架
- 内置示例函数（获取时间、玩家信息、天气等）
- 权限控制和安全检查

### ⚙️ 配置管理
- 完整的配置文件系统
- 支持多Provider配置和管理
- 配置文件热重载（/llmchat reload）
- 游戏内切换Provider和模型
- 支持OpenRouter、DeepSeek、OpenAI等多种API服务

## 安装和配置

### 1. 安装模组
将编译好的jar文件放入Minecraft的`mods`文件夹中。

### 2. 配置API密钥
首次运行后，在`config/lllmchat/config.json`中配置你的API密钥。

支持多个API提供商的配置：

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
      "models": [
        "google/gemini-2.5-pro-preview",
        "anthropic/claude-sonnet-4",
        "anthropic/claude-3.5-sonnet",
        "anthropic/claude-3.7-sonnet:thinking"
      ]
    },
    {
      "name": "deepseek",
      "apiBaseUrl": "https://api.deepseek.com/chat/completions",
      "apiKey": "sk-xxx",
      "models": ["deepseek-chat", "deepseek-reasoner"]
    },
    {
      "name": "openai",
      "apiBaseUrl": "https://api.openai.com/v1/chat/completions",
      "apiKey": "sk-xxx",
      "models": ["gpt-3.5-turbo", "gpt-4", "gpt-4-turbo", "gpt-4o"]
    }
  ]
}
```

## 使用方法

### 初次配置
1. 编辑 `config/lllmchat/config.json`
2. 配置至少一个Provider
3. 设置 `currentProvider` 和 `currentModel`
4. 使用 `/llmchat reload` 重载配置

### 基本聊天
```
/llmchat 你好，请介绍一下Minecraft的基本玩法
```

### 管理命令
```
/llmchat clear                          # 清空聊天历史
/llmchat template list                  # 列出所有可用模板
/llmchat template set creative          # 切换到创造模式助手模板
/llmchat provider list                  # 列出所有配置的providers
/llmchat provider switch openrouter    # 切换到指定的provider
/llmchat model list                     # 列出当前provider支持的模型
/llmchat model list deepseek           # 列出指定provider支持的模型
/llmchat model set deepseek-chat       # 设置当前使用的模型
/llmchat reload                         # 热重载配置文件
/llmchat help                          # 显示帮助信息
```

### 提示词模板

#### 内置模板
- `default` - 通用AI助手
- `creative` - 创造模式建筑助手
- `survival` - 生存模式专家
- `redstone` - 红石电路工程师
- `mod` - 模组使用助手

#### 自定义模板
可以在`config/lllmchat/prompt_templates.json`中添加自定义模板：

```json
{
  "my_template": {
    "id": "my_template",
    "name": "我的自定义模板",
    "description": "自定义助手模板",
    "system_prompt": "你是一个专业的{{specialty}}助手...",
    "user_prompt_prefix": "请帮我：",
    "user_prompt_suffix": "",
    "variables": {
      "specialty": "建筑"
    },
    "enabled": true
  }
}
```

## 开发和扩展

### 添加新的LLM服务
实现`LLMService`接口：

```java
public class MyLLMService implements LLMService {
    @Override
    public CompletableFuture<LLMResponse> chat(List<LLMMessage> messages, LLMConfig config) {
        // 实现你的API调用逻辑
    }
    
    // 实现其他必需方法...
}
```

### 添加自定义函数
实现`LLMFunction`接口：

```java
public class MyFunction implements LLMFunction {
    @Override
    public String getName() {
        return "my_function";
    }
    
    @Override
    public FunctionResult execute(PlayerEntity player, MinecraftServer server, JsonObject arguments) {
        // 实现你的函数逻辑
        return FunctionResult.success("执行成功");
    }
    
    // 实现其他必需方法...
}
```

然后在模组初始化时注册：
```java
FunctionRegistry.getInstance().registerFunction(new MyFunction());
```

## 项目结构

```
src/main/java/com/riceawa/
├── llm/
│   ├── core/           # 核心接口和数据结构
│   ├── service/        # LLM服务实现
│   ├── context/        # 聊天上下文管理
│   ├── history/        # 历史记录管理
│   ├── template/       # 提示词模板系统
│   ├── function/       # Function Calling框架
│   ├── config/         # 配置管理
│   └── command/        # 命令处理
└── Lllmchat.java      # 主模组类
```

## 依赖项

- Fabric API
- OkHttp3 (HTTP客户端)
- Gson (JSON处理)
- Typesafe Config (配置管理)

## 许可证

本项目采用 CC0-1.0 许可证。

## 贡献

欢迎提交Issue和Pull Request来改进这个模组！

## 注意事项

1. 请确保你有有效的API密钥才能使用LLM功能
2. API调用可能产生费用，请注意使用量
3. 建议在生产环境中启用适当的权限控制
4. Function Calling功能默认关闭，需要手动启用

## 更新日志

### v1.1.0
- 🔥 新增多Provider配置支持
- 🔥 支持配置文件热重载（/llmchat reload）
- 🔥 游戏内切换Provider和模型
- 🔥 支持OpenRouter、DeepSeek等多种API服务
- ✨ 向后兼容旧版配置格式
- ✨ 新增Provider管理命令
- ✨ 新增模型管理命令

### v1.0.0
- 初始版本发布
- 支持OpenAI GPT系列模型
- 完整的聊天上下文管理
- 提示词模板系统
- 历史记录存储
- Function Calling框架（预留）
- 配置管理系统
