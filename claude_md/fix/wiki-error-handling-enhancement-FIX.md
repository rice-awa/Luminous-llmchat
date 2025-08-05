# Wiki函数错误处理完善 - FIX

## 📋 修复概述

根据API文档，完善了所有Wiki函数调用工具的错误处理机制，特别是针对`PAGE_NOT_FOUND`错误时的`suggestions`字段处理，以及统一了所有错误码的处理逻辑。

## 🔧 修复内容

### 1. 新增统一错误处理工具类
- **文件**: `WikiErrorHandler.java`
- **功能**: 
  - 统一处理各种Wiki API错误响应格式
  - 支持`PAGE_NOT_FOUND`时的相似页面建议
  - 处理`RATE_LIMIT_EXCEEDED`错误的详细信息
  - 格式化标准错误消息

### 2. WikiSearchFunction 完善
- **文件**: `src/main/java/com/riceawa/llm/function/impl/WikiSearchFunction.java`
- **改进**:
  - 使用`WikiErrorHandler`统一处理错误
  - 支持显示搜索失败时的相似页面建议
  - 正确提取并显示错误码

### 3. WikiPageFunction 完善  
- **文件**: `src/main/java/com/riceawa/llm/function/impl/WikiPageFunction.java`
- **改进**:
  - 增强HTTP错误响应解析
  - 支持页面不存在时显示相似页面建议
  - 统一错误消息格式

### 4. WikiBatchPagesFunction 完善
- **文件**: `src/main/java/com/riceawa/llm/function/impl/WikiBatchPagesFunction.java`
- **改进**:
  - 改进批量请求的整体错误处理
  - 每个页面的详细错误处理
  - 支持频率限制错误的详细信息显示
  - 批量请求中显示精简的相似页面建议

## 🆕 新增功能

### 页面不存在时的建议功能
当查询的页面不存在时，现在会显示：
```
页面 "不存在的页面" 不存在

建议的相似页面：
• 相似页面1
• 相似页面2
• 相似页面3
```

### 错误码标准化
所有错误现在都会显示错误码：
```
Wiki搜索失败: 页面不存在 (PAGE_NOT_FOUND)  
Wiki页面获取失败: 请求过于频繁，请稍后再试 (RATE_LIMIT_EXCEEDED)
```

### 频率限制详细信息
对于`RATE_LIMIT_EXCEEDED`错误，会显示：
```
请求过于频繁，请稍后再试
请求频率限制: 100次/60秒，请30秒后重试
```

## 🔄 API错误码处理支持

现在支持处理以下API文档中定义的错误码：

| 错误码 | 处理方式 |
|--------|----------|
| `PAGE_NOT_FOUND` | 显示相似页面建议 |
| `RATE_LIMIT_EXCEEDED` | 显示频率限制详情和重试时间 |
| `INVALID_PARAMETERS` | 标准错误消息格式 |
| `SEARCH_ERROR` | 标准错误消息格式 |
| `PARSE_ERROR` | 标准错误消息格式 |
| `HTML_FETCH_ERROR` | 标准错误消息格式 |
| `NETWORK_ERROR` | 标准错误消息格式 |
| `TIMEOUT_ERROR` | 标准错误消息格式 |

## 📊 技术细节

### WikiErrorHandler类方法

```java
// 通用错误处理
public static String handleError(JsonObject error, String contextInfo)

// 批量请求中的页面错误处理
public static String handleBatchPageError(JsonObject error, String pageName, int maxSuggestions)
```

### 错误响应解析
- 正确提取`error.code`字段，直接返回原始code值（如`"PAGE_NOT_FOUND"`）
- 解析`error.details`中的结构化信息
- 支持`suggestions`数组的处理
- 支持`retryAfter`、`windowMs`等频率限制字段

## ✅ 验证结果

- ✅ 编译成功，无语法错误
- ✅ 所有Wiki函数使用统一的错误处理逻辑
- ✅ 支持API文档中定义的所有错误格式
- ✅ 错误消息更加用户友好和详细

## 🎯 使用效果

用户在使用Wiki函数时，现在会得到更详细和有用的错误信息：

1. **页面不存在时**：会自动显示相似页面建议，帮助用户找到正确的页面
2. **频率限制时**：会显示具体的限制信息和建议的重试时间
3. **其他错误时**：会显示标准化的错误码和消息，便于问题排查

这些改进大大提升了Wiki函数的用户体验和错误诊断能力。