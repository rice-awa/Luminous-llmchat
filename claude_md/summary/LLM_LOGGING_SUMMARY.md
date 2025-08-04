# LLM日志记录系统优化 - 实现总结

## 概述

本次优化为LLMChatMod添加了完整的LLM原始请求和响应日志记录功能，支持结构化JSON数据记录，便于调试、分析和监控LLM API的使用情况。

## 新增功能

### 1. LLM请求日志记录
- **完整请求信息**：记录所有发送到LLM API的原始请求数据
- **上下文信息**：包含玩家信息、会话ID、服务器信息等
- **请求参数**：模型配置、消息列表、估算token数等
- **HTTP详情**：请求URL、请求头（脱敏处理）、请求体

### 2. LLM响应日志记录
- **完整响应信息**：记录LLM API返回的原始响应数据
- **性能指标**：响应时间、HTTP状态码、成功/失败状态
- **Token使用情况**：提示token、完成token、总token数
- **响应内容**：AI生成的回复内容、完成原因等

### 3. 结构化JSON格式
- **标准化格式**：所有日志都以结构化JSON格式存储
- **易于解析**：便于后续的数据分析和处理
- **完整关联**：请求和响应通过ID关联，便于追踪

## 新增文件

### 核心日志类
1. **LLMRequestLogEntry.java** - LLM请求日志条目
   - 记录完整的请求信息和上下文
   - 支持JSON序列化和格式化输出

2. **LLMResponseLogEntry.java** - LLM响应日志条目
   - 记录完整的响应信息和性能指标
   - 包含token使用统计

3. **LLMLogUtils.java** - LLM日志工具类
   - 提供便捷的日志记录方法
   - 敏感信息脱敏处理
   - JSON序列化工具

4. **LLMContext.java** - LLM上下文信息类
   - 传递玩家信息、会话信息等上下文
   - 支持扩展元数据

## 修改的文件

### 1. LogConfig.java
- 添加LLM请求日志配置选项
- 支持控制是否记录完整请求/响应体
- 添加内容长度限制和敏感数据脱敏配置

### 2. LogManager.java
- 添加`llmRequest()`方法用于记录LLM日志
- 支持`llm_request`日志类别
- 自动创建LLM请求日志文件

### 3. LLMService.java
- 添加带上下文信息的`chat()`方法重载
- 支持传递玩家和会话信息

### 4. OpenAIService.java
- 实现详细的请求/响应日志记录
- 记录HTTP请求和响应的完整信息
- 包含性能指标和错误处理

### 5. LLMChatCommand.java
- 使用带上下文的chat方法
- 传递玩家和会话信息到LLM服务

## 配置选项

新增的日志配置选项：

```java
// 是否启用LLM请求日志
private boolean enableLLMRequestLog = true;

// 是否记录完整的请求体
private boolean logFullRequestBody = true;

// 是否记录完整的响应体
private boolean logFullResponseBody = true;

// 最大日志内容长度（超过会被截断）
private int maxLogContentLength = 10000;

// 是否脱敏敏感数据（如API密钥）
private boolean sanitizeSensitiveData = true;
```

## 日志文件结构

### 请求日志示例
```json
{
  "request_id": "req_abc123",
  "timestamp": "2024-01-15T10:30:45.123",
  "player_name": "PlayerName",
  "player_uuid": "uuid-string",
  "service_name": "OpenAI",
  "model": "gpt-3.5-turbo",
  "messages": [...],
  "config": {...},
  "raw_request_json": "{...}",
  "request_url": "https://api.openai.com/v1/chat/completions",
  "request_headers": {
    "Authorization": "Bearer ***MASKED***",
    "Content-Type": "application/json"
  },
  "context_message_count": 5,
  "estimated_tokens": 150
}
```

### 响应日志示例
```json
{
  "response_id": "resp_def456",
  "request_id": "req_abc123",
  "timestamp": "2024-01-15T10:30:47.456",
  "response_time_ms": 2333,
  "http_status_code": 200,
  "success": true,
  "llm_response": {...},
  "raw_response_json": "{...}",
  "content": "AI的回复内容",
  "model": "gpt-3.5-turbo",
  "usage": {
    "prompt_tokens": 120,
    "completion_tokens": 80,
    "total_tokens": 200
  },
  "finish_reason": "stop"
}
```

## 安全特性

### 敏感信息脱敏
- **API密钥**：自动将Authorization头中的Bearer token替换为`***MASKED***`
- **配置化**：可通过`sanitizeSensitiveData`配置开关
- **扩展性**：支持添加更多需要脱敏的字段

### 内容长度控制
- **可配置限制**：通过`maxLogContentLength`控制最大日志内容长度
- **自动截断**：超长内容会被截断并添加`[TRUNCATED]`标记
- **性能保护**：避免超大响应影响日志性能

## 使用场景

### 1. 调试和故障排查
- 查看完整的API请求和响应
- 分析请求失败的原因
- 检查token使用情况

### 2. 性能监控
- 监控API响应时间
- 分析token消耗趋势
- 识别性能瓶颈

### 3. 使用分析
- 统计用户使用模式
- 分析热门功能
- 优化提示词模板

### 4. 合规和审计
- 记录所有AI交互
- 支持数据审计要求
- 便于问题追溯

## 日志文件位置

LLM请求日志文件位置：
```
config/lllmchat/logs/llm_request.log
config/lllmchat/logs/llm_request.log.1
config/lllmchat/logs/llm_request.log.2
...
```

## 性能考虑

### 异步处理
- 日志记录使用异步队列，不影响主线程性能
- 支持配置队列大小和处理策略

### 文件轮转
- 支持按文件大小自动轮转
- 可配置保留文件数量和保留天数

### 内存优化
- 大内容自动截断
- 及时释放临时对象

## 后续扩展

### 1. 日志分析工具
- 可开发专门的日志分析工具
- 支持按时间、玩家、模型等维度统计

### 2. 实时监控
- 可集成监控系统
- 支持实时告警和指标展示

### 3. 数据导出
- 支持导出为CSV、Excel等格式
- 便于数据分析和报告生成

## 总结

本次优化大幅提升了LLMChatMod的可观测性和可维护性，为后续的功能优化和问题排查提供了强有力的支持。通过结构化的日志记录，开发者和管理员可以更好地了解系统的运行状况和用户的使用情况。
