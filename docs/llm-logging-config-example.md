# LLM日志配置示例

## 基本配置

以下是LLM日志记录的配置选项和推荐设置：

### 1. 启用LLM请求日志

```java
// 在LogConfig中的配置
private boolean enableLLMRequestLog = true;  // 启用LLM请求日志
```

### 2. 控制日志内容详细程度

```java
// 是否记录完整的请求体（包含所有消息和配置）
private boolean logFullRequestBody = true;

// 是否记录完整的响应体（包含原始API响应）
private boolean logFullResponseBody = true;

// 最大日志内容长度（字符数）
private int maxLogContentLength = 10000;
```

### 3. 安全配置

```java
// 是否脱敏敏感数据（如API密钥）
private boolean sanitizeSensitiveData = true;
```

## 配置场景

### 场景1：开发和调试环境
```java
// 完整日志记录，便于调试
enableLLMRequestLog = true;
logFullRequestBody = true;
logFullResponseBody = true;
maxLogContentLength = 50000;  // 较大的内容限制
sanitizeSensitiveData = true;
```

### 场景2：生产环境
```java
// 平衡性能和可观测性
enableLLMRequestLog = true;
logFullRequestBody = true;
logFullResponseBody = false;  // 不记录完整响应体以节省空间
maxLogContentLength = 5000;   // 较小的内容限制
sanitizeSensitiveData = true;
```

### 场景3：高性能环境
```java
// 最小化日志记录
enableLLMRequestLog = true;
logFullRequestBody = false;   // 只记录元数据
logFullResponseBody = false;
maxLogContentLength = 1000;
sanitizeSensitiveData = true;
```

### 场景4：合规审计环境
```java
// 完整记录所有信息
enableLLMRequestLog = true;
logFullRequestBody = true;
logFullResponseBody = true;
maxLogContentLength = 100000; // 不限制内容长度
sanitizeSensitiveData = true;
```

## 日志文件管理

### 文件轮转配置
```java
// 日志文件大小限制（字节）
private int maxFileSize = 50 * 1024 * 1024; // 50MB

// 保留的备份文件数量
private int maxBackupFiles = 10;

// 日志保留天数
private int retentionDays = 30;
```

### 异步处理配置
```java
// 启用异步日志处理
private boolean enableAsyncLogging = true;

// 异步队列大小
private int asyncQueueSize = 2000;
```

## 使用代码示例

### 1. 手动记录LLM请求日志

```java
// 创建请求日志
LLMRequestLogEntry requestLog = LLMLogUtils.createRequestLogBuilder("req_123")
    .serviceName("OpenAI")
    .playerName("PlayerName")
    .playerUuid("uuid-string")
    .messages(messageList)
    .config(llmConfig)
    .rawRequestJson(requestJson)
    .requestUrl("https://api.openai.com/v1/chat/completions")
    .estimatedTokens(150)
    .build();

// 记录日志
LLMLogUtils.logRequest(requestLog);
```

### 2. 手动记录LLM响应日志

```java
// 创建响应日志
LLMResponseLogEntry responseLog = LLMLogUtils.createResponseLogBuilder("resp_456", "req_123")
    .httpStatusCode(200)
    .success(true)
    .llmResponse(response)
    .rawResponseJson(responseJson)
    .responseTimeMs(1500)
    .usage(120, 80, 200)  // prompt, completion, total tokens
    .build();

// 记录日志
LLMLogUtils.logResponse(responseLog);
```

### 3. 使用带上下文的LLM服务

```java
// 创建上下文
LLMContext context = LLMContext.builder()
    .playerName("PlayerName")
    .playerUuid("uuid-string")
    .sessionId("session-123")
    .metadata("server", "MyServer")
    .build();

// 调用LLM服务（自动记录日志）
llmService.chat(messages, config, context)
    .thenAccept(response -> {
        // 处理响应
    });
```

## 日志查询和分析

### 1. 按时间范围查询
```bash
# 查询今天的LLM请求日志
grep "$(date +%Y-%m-%d)" config/lllmchat/logs/llm_request.log
```

### 2. 按玩家查询
```bash
# 查询特定玩家的请求
grep "\"player_name\":\"PlayerName\"" config/lllmchat/logs/llm_request.log
```

### 3. 按模型查询
```bash
# 查询使用特定模型的请求
grep "\"model\":\"gpt-3.5-turbo\"" config/lllmchat/logs/llm_request.log
```

### 4. 统计token使用
```bash
# 提取token使用信息
grep -o "\"total_tokens\":[0-9]*" config/lllmchat/logs/llm_request.log
```

## 性能优化建议

### 1. 磁盘空间管理
- 定期清理旧日志文件
- 根据需要调整`maxBackupFiles`和`retentionDays`
- 监控日志目录的磁盘使用情况

### 2. 内存使用优化
- 适当设置`maxLogContentLength`避免记录过大内容
- 启用异步日志处理（`enableAsyncLogging = true`）
- 调整异步队列大小（`asyncQueueSize`）

### 3. 网络和I/O优化
- 在高并发环境下考虑禁用完整响应体记录
- 使用SSD存储提高日志写入性能
- 考虑将日志写入到独立的磁盘分区

## 故障排查

### 1. 日志文件未生成
- 检查`enableLLMRequestLog`是否为true
- 确认日志目录权限正确
- 查看控制台是否有错误信息

### 2. 日志内容不完整
- 检查`maxLogContentLength`设置
- 确认`logFullRequestBody`和`logFullResponseBody`配置
- 查看是否有异常导致日志记录中断

### 3. 性能问题
- 考虑禁用完整内容记录
- 增大异步队列大小
- 检查磁盘I/O性能

## 监控和告警

### 1. 日志文件大小监控
```bash
# 监控日志文件大小
du -h config/lllmchat/logs/llm_request.log
```

### 2. 错误率监控
```bash
# 统计失败的请求
grep "\"success\":false" config/lllmchat/logs/llm_request.log | wc -l
```

### 3. 响应时间监控
```bash
# 提取响应时间信息
grep -o "\"response_time_ms\":[0-9]*" config/lllmchat/logs/llm_request.log
```

这些配置和使用示例可以帮助你根据具体需求优化LLM日志记录功能。
