# 内置变量系统总结

## 功能概述

为提示词模板系统添加了内置变量功能，允许模板自动获取玩家名、当前时间、坐标、游戏状态等动态信息，无需手动设置。

## 新增的内置变量

### 🧑 玩家信息
- `{{player}}` - 玩家名称
- `{{x}}`, `{{y}}`, `{{z}}` - 玩家当前坐标
- `{{health}}` - 玩家当前生命值
- `{{level}}` - 玩家经验等级
- `{{gamemode}}` - 游戏模式（生存模式、创造模式、冒险模式、观察者模式）

### ⏰ 时间信息
- `{{time}}` - 当前时间 (格式: yyyy-MM-dd HH:mm:ss)
- `{{date}}` - 当前日期 (格式: yyyy-MM-dd)
- `{{hour}}` - 当前小时 (0-23)
- `{{minute}}` - 当前分钟 (0-59)

### 🌍 世界信息
- `{{world}}` - 世界标识符
- `{{dimension}}` - 维度名称（主世界、下界、末地）
- `{{weather}}` - 天气状态（晴天、下雨、雷雨）

### 🖥️ 服务器信息
- `{{server}}` - 服务器名称

## 使用示例

### 基础使用
```bash
# 创建一个使用内置变量的模板
/llmchat template create personal_assistant

# 设置系统提示词
/llmchat template edit system 你好{{player}}！现在是{{time}}，你在{{dimension}}的坐标是({{x}}, {{y}}, {{z}})。我是你的专属AI助手。

# 设置用户消息前缀
/llmchat template edit prefix [{{player}}在{{dimension}}问]: 

# 预览模板（会显示所有变量的当前值）
/llmchat template preview

# 保存模板
/llmchat template save
```

### 高级使用示例
```bash
# 创建一个游戏状态感知的助手
/llmchat template create game_aware_assistant

# 设置详细的系统提示词
/llmchat template edit system 你是{{player}}的游戏助手。当前状态：时间{{time}}，位置{{dimension}}({{x}},{{y}},{{z}})，生命值{{health}}，等级{{level}}，游戏模式{{gamemode}}，天气{{weather}}。请根据玩家的当前状态提供相应的建议。

# 设置动态前缀
/llmchat template edit prefix [{{hour}}:{{minute}} {{player}}]: 

# 保存并应用
/llmchat template save
/llmchat template set game_aware_assistant
```

### 结合自定义变量
```bash
# 创建个性化助手
/llmchat template create my_custom_assistant

# 设置自定义变量
/llmchat template var set assistant_name 小明
/llmchat template var set specialty 建筑设计

# 设置混合使用内置和自定义变量的系统提示词
/llmchat template edit system 我是{{assistant_name}}，专长是{{specialty}}。现在是{{time}}，{{player}}你好！你现在在{{dimension}}，坐标({{x}}, {{y}}, {{z}})，有什么{{specialty}}方面的问题吗？

/llmchat template save
```

## 实际效果示例

当玩家Steve在主世界坐标(100, 64, 200)使用上述模板时：

**系统提示词渲染结果：**
```
我是小明，专长是建筑设计。现在是2025-01-15 14:30:25，Steve你好！你现在在主世界，坐标(100, 64, 200)，有什么建筑设计方面的问题吗？
```

**用户消息前缀渲染结果：**
```
[14:30 Steve]: 用户的问题内容
```

## 技术实现

### 核心修改

1. **PromptTemplate.java**
   - 修改 `renderTemplate` 方法支持玩家参数
   - 添加 `getBuiltinVariable` 方法处理内置变量
   - 添加 `getVariableValue` 方法统一处理内置和自定义变量

2. **变量优先级**
   - 内置变量优先级高于自定义变量
   - 如果自定义变量与内置变量同名，内置变量会覆盖自定义变量

3. **动态获取**
   - 内置变量在每次渲染时动态获取最新值
   - 确保信息的实时性和准确性

### 兼容性

- **向后兼容**：现有模板继续正常工作
- **渐进增强**：可以逐步在现有模板中添加内置变量
- **混合使用**：内置变量和自定义变量可以同时使用

## 用户体验改进

### 1. 预览功能增强
- 显示所有内置变量的当前值
- 区分内置变量和自定义变量
- 实时显示玩家当前状态

### 2. 编辑引导优化
- 在编辑菜单中说明内置变量的存在
- 提供内置变量的使用提示
- 更新帮助信息包含所有可用的内置变量

### 3. 智能提示
- 预览时显示变量的实际值和说明
- 帮助用户理解每个变量的含义和用途

## 安全性考虑

- **数据安全**：内置变量只获取游戏内公开信息
- **权限控制**：不暴露敏感的服务器信息
- **错误处理**：当玩家信息不可用时提供默认值

## 扩展性

系统设计支持轻松添加新的内置变量：
1. 在 `getBuiltinVariable` 方法中添加新的case
2. 更新帮助文档
3. 无需修改其他代码

## 使用建议

1. **合理使用**：根据模板用途选择合适的内置变量
2. **性能考虑**：避免在不需要时使用过多变量
3. **用户体验**：使用内置变量让AI回复更加个性化和情境化
4. **测试验证**：使用预览功能确认变量渲染效果

这个内置变量系统大大增强了提示词模板的动态性和实用性，让AI助手能够更好地感知和响应玩家的当前状态。
