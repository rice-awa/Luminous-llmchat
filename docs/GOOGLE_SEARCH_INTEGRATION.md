# Google搜索集成功能

## 功能概述

本系统支持根据模型类型自动启用Google搜索功能。当使用Gemini模型且启用联网搜索时，系统会自动在请求中添加`googleSearch`工具。

## 支持的模型

系统会自动识别以下格式的Gemini模型：
- `gemini-1.5-pro`
- `gemini-2.5-flash`
- `gemini-2.5-flash-preview-05-20`
- `google/gemini-2.5-pro-preview`
- `GEMINI-2.0-FLASH`（大小写不敏感）

## 配置要求

1. **启用联网搜索**：在配置中设置 `enableWebSearch: true`
2. **使用Gemini模型**：模型名称需要包含"gemini"关键字

## 工具定义

当条件满足时，系统会自动添加以下简化的工具定义：

```json
{
  "type": "function",
  "function": {
    "name": "googleSearch"
  }
}
```

注意：为了避免API兼容性问题，我们使用了最简化的工具定义，只包含必要的`name`字段。

## 使用示例

### API请求示例

```bash
curl --location "https://x666.me/v1/chat/completions" \
  --header "authorization: Bearer your-api-key" \
  --header "Content-Type: application/json" \
  --data '{
    "messages": [{
      "role": "user",
      "content": "关于openai和超级碗相关的新闻有什么"
    }],
    "model": "gemini-2.5-flash-preview-05-20",
    "stream": false,
    "tools": [{
      "type": "function",
      "function": {
        "name": "googleSearch"
      }
    }]
  }'
```

### 配置示例

```json
{
  "enableWebSearch": true,
  "model": "gemini-1.5-pro",
  "providers": [
    {
      "name": "gemini-provider",
      "baseUrl": "https://x666.me/v1",
      "apiKey": "your-api-key",
      "models": ["gemini-1.5-pro", "gemini-2.5-flash"]
    }
  ]
}
```

## 工作流程

1. **模型检测**：系统检查当前使用的模型是否为Gemini模型
2. **配置检查**：验证是否启用了联网搜索功能
3. **工具添加**：如果两个条件都满足，自动添加`googleSearch`工具到请求中
4. **请求发送**：包含工具定义的请求被发送到API端点

## 注意事项

- 只有Gemini模型支持Google搜索功能
- 需要确保API提供商支持工具调用功能
- 搜索功能的具体实现依赖于API提供商的支持
- 建议在生产环境中测试工具调用的响应格式

## 故障排除

### 工具未被添加
1. 检查模型名称是否包含"gemini"
2. 确认`enableWebSearch`配置为`true`
3. 验证模型识别逻辑是否正确

### API调用失败
1. 确认API提供商支持工具调用
2. 检查API密钥是否有效
3. 验证请求格式是否正确

## 测试

运行以下命令测试Google搜索集成功能：

```bash
./gradlew test --tests TestWebSearchIntegration
```

测试覆盖：
- Gemini模型识别
- Google搜索工具结构验证
- 配置逻辑测试
- 工具数组构建测试