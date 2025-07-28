# Luminous LLM Chat Mod for Minecraft Fabric 1.21.7

一个让人眼前一亮的Minecraft Fabric模组，集成了LLM（大语言模型）聊天功能，支持多种AI服务和自定义功能。

## ✨ 核心功能

- 🤖 **多LLM服务支持** - OpenAI、OpenRouter、DeepSeek等，可扩展架构
- 💬 **智能上下文管理** - 基于字符长度的精确控制，智能压缩，对话恢复
- 📝 **提示词模板系统** - 内置多种预设，支持自定义模板
- � **Function Calling** - 13个内置游戏API，智能权限控制
- 📚 **历史记录管理** - 持久化存储，多格式导出，统计分析
- 📊 **完善日志系统** - 多级别分类日志，异步处理，文件轮转
- 📢 **AI聊天广播** - OP可控的全服广播功能
- ⚙️ **配置管理** - 热重载，游戏内切换Provider和模型
- 🧪 **测试框架** - 全面单元测试，质量保证

## 🚀 快速开始

### 1. 安装模组
将编译好的jar文件放入Minecraft的`mods`文件夹中。
> 你可以在Actions artifacts中获取编译好的jar文件，其中 Multi-Version Build 构建有多个版本的mod文件。

### 2. 配置API密钥
1. 首次使用时，尝试发送任意消息：`/llmchat 你好`
2. 系统会自动检测配置问题并显示详细的配置指导
3. 使用 `/llmchat setup` 查看配置向导
4. 编辑 `config/lllmchat/config.json` 文件，添加你的API密钥
5. 使用 `/llmchat reload` 重载配置（需要OP权限）

> 📖 **详细配置指南**: 查看 [配置指南](docs/CONFIGURATION_GUIDE.md) 了解完整的配置选项和多Provider设置

## 💬 基本使用

### 基本聊天
```bash
/llmchat 你好，请介绍一下Minecraft的基本玩法
```

### 常用命令
```bash
/llmchat clear                    # 清空聊天历史
/llmchat resume                   # 恢复最近的对话内容
/llmchat resume list              # 列出所有历史对话记录
/llmchat resume 2                 # 恢复指定ID的对话（如#2）
/llmchat template set creative    # 切换到创造模式助手模板
/llmchat help                     # 显示帮助信息
```

### 管理员命令（需要OP权限）
```bash
/llmchat provider switch openrouter    # 切换Provider
/llmchat model set gpt-4               # 设置模型
/llmchat broadcast enable              # 开启AI聊天广播
/llmchat reload                        # 重载配置文件
```

> 📖 **完整命令指南**: 查看 [命令指南](docs/COMMANDS_GUIDE.md) 了解所有可用命令和详细用法

## 📚 文档导航

### 功能详细文档
- 📖 [配置指南](docs/CONFIGURATION_GUIDE.md) - 完整的配置选项和多Provider设置
- 💻 [命令指南](docs/COMMANDS_GUIDE.md) - 所有可用命令和详细用法（包含Resume命令扩展功能）
- 🔧 [Function Calling开发](docs/FUNCTION_CALLING_DEVELOPMENT.md) - Function Calling功能详解
- 🛡️ [Function Call安全](docs/FUNCTION_CALL_SECURITY.md) - 权限控制和安全机制
- 🎮 [Function演示](docs/FUNCTION_DEMO.md) - 实际使用示例和效果展示
- 📢 [广播功能](docs/BROADCAST_FEATURE.md) - AI聊天广播功能详解
- 🧠 [上下文管理](docs/CONTEXT_MANAGEMENT.md) - 智能上下文管理和压缩
- 📊 [日志和历史](docs/LOGGING_AND_HISTORY.md) - 日志系统和历史记录管理
- 🧪 [测试指南](docs/TESTING_GUIDE.md) - 测试框架和质量保证
- 🎯 [使用演示](docs/DEMO_USAGE.md) - 实际使用场景和示例

## 🔧 Function Calling 功能概览

启用Function Calling后，AI可以主动调用游戏API获取实时信息和执行操作：

### 主要功能类别
- 🌍 **世界信息查询** - 获取世界状态、附近实体、天气时间等
- 👤 **玩家状态查询** - 查看生命值、背包、位置、状态效果等
- 🎮 **服务器信息** - 获取服务器状态、在线玩家、性能数据
- 💬 **交互功能** - 发送消息、广播通知等
- ⚡ **管理员功能** - 执行指令、设置方块、生成实体、控制环境（需OP权限）

### 使用示例
```bash
/llmchat 帮我查看一下当前的游戏状态
# AI会自动调用相关函数获取世界信息和玩家状态

/llmchat 附近有什么生物吗？
# AI会调用get_nearby_entities函数查询附近实体

/llmchat 我的背包里有什么物品？
# AI会调用get_inventory函数查看背包内容

/llmchat resume list
# 查看所有历史对话记录，选择要恢复的对话

/llmchat resume 3
# 恢复第3个历史对话，继续之前的讨论
```

> 📖 **详细功能文档**: 查看 [Function演示](docs/FUNCTION_DEMO.md) 和 [Function Call安全](docs/FUNCTION_CALL_SECURITY.md) 了解完整功能列表和安全机制

## 🧪 测试和开发

### 运行测试
```bash
./gradlew test                    # 运行所有测试
./gradlew test jacocoTestReport   # 生成测试报告
```

### 质量指标
- **代码质量得分**: 76.7% (B级标准)
- **测试覆盖率**: 核心功能100%覆盖
- **API兼容性**: 完全符合OpenAI标准

> 📖 **开发指南**: 查看 [测试指南](docs/TESTING_GUIDE.md) 了解完整的测试框架和开发规范

## 🧠 高级功能

### 智能上下文管理
- **字符长度精确控制** - 基于实际字符数量而非消息数量，更精确的上下文管理
- **完整消息压缩** - 压缩完整消息（如1/2的消息），保持消息完整性
- **智能压缩算法** - AI驱动的上下文压缩，保留重要信息
- **对话恢复** - 使用 `/llmchat resume` 快速恢复上次对话
- **压缩通知** - 友好的用户提示，可配置开启/关闭
- **成本优化** - 支持配置专用压缩模型降低费用

### 增强的历史记录管理
- **智能会话列表** - 使用 `/llmchat resume list` 查看所有历史对话
- **精确对话恢复** - 通过数字ID（如 `/llmchat resume 2`）恢复指定对话
- **会话标题显示** - AI自动生成的对话标题，便于识别内容
- **时间和统计信息** - 显示对话时间、消息数量和使用的模板

> ⚠️ **重要配置提醒**: 建议将 `maxContextCharacters` 设置为比模型默认上下文长度低的值，以确保系统有足够空间进行压缩和处理。例如，对于支持128k上下文的模型，建议设置为100,000字符。

> 📖 **详细功能文档**: 查看 [上下文管理](docs/CONTEXT_MANAGEMENT.md) 了解完整的上下文管理功能

## 📋 项目信息

### 依赖项
- Fabric API
- OkHttp3 (HTTP客户端)
- Gson (JSON处理)
- Typesafe Config (配置管理)

### 许可证
本项目采用 [MIT](./LICENSE) 许可证。

### 贡献
欢迎提交Issue和Pull Request来改进这个模组！

### ⚠️ 重要提醒
1. 请确保你有有效的API密钥才能使用LLM功能
2. API调用可能产生费用，请注意使用量
3. 管理员功能需要OP权限，包括执行指令、设置方块、生成实体等
4. AI聊天广播默认关闭，OP可根据需要开启
5. 建议配置专用压缩模型（如gpt-3.5-turbo）降低费用
6. **重要**: 请将 `maxContextCharacters` 设置为比模型默认上下文长度低的值，为压缩和处理预留空间
6. 建议运行测试验证功能正确性：`./gradlew test`

## 📝 更新日志

### v1.6.1 (2025-07-28) - 最新版本
- 🔥 **Resume命令功能扩展** - 全面升级的历史对话管理系统
- ✨ 新增 `/llmchat resume list` - 列出所有历史对话记录，显示标题和详细信息
- ✨ 新增 `/llmchat resume <数字>` - 通过简单的数字ID精确恢复指定对话
- 🎯 **智能会话索引** - 最新对话为#1，直观的数字ID系统
- 📋 **丰富的会话信息** - 显示AI生成的标题、时间戳、消息数量和模板
- 🛡️ **完善的错误处理** - 友好的用户提示和异常处理机制
- 📚 **完整文档支持** - 新增详细的使用指南和优化总结文档

### v1.6.0 (2025-07-27)
- 🔥 **智能上下文管理升级** - 60k默认上下文长度，智能压缩替代简单删除
- 🔥 **压缩通知系统** - 友好的用户提示，可配置开启/关闭
- 🔥 **自定义压缩模型** - 支持配置专用压缩模型，优化成本控制
- ✨ 新增 `/llmchat resume` 命令 - 快速恢复上次对话内容
- 💰 成本优化：聊天用高质量模型，压缩用经济模型
- 🛡️ 优化聊天逻辑和providers切换逻辑

### v1.5.0 (2025-07-25)
- 🔥 **强大的管理员功能** - 6个新的管理员专用Function Calling功能
- 🔥 **统一权限管理系统** - PermissionHelper工具类，多层安全保护
- 🔥 **执行指令功能** - 安全地执行服务器指令，指令黑名单保护
- ✨ 新增世界操作功能：设置方块、生成实体、传送玩家
- ✨ 新增环境控制功能：天气控制、时间控制

### v1.4.0 (2025-07-25)
- 🔥 **完善的日志系统** - 多级别、分类、异步日志记录
- 🔥 **增强的历史记录管理** - 统计分析、多格式导出、高级搜索
- 🔥 **性能监控** - 详细的API响应时间和资源使用监控

### 历史版本
查看完整的版本历史和详细更新内容，请参考项目的Git提交记录。
