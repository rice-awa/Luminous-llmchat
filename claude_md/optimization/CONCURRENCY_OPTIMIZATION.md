# LLM Chat 并发优化指南

## 概述

LLM Chat 模组已经过优化，支持多人游戏环境中的高并发LLM请求。本文档详细介绍了并发优化的特性和配置选项。

## 主要特性

### 1. 智能并发管理
- **请求队列**: 自动管理并发请求，避免API限制
- **线程池优化**: 专用线程池处理LLM请求，不阻塞游戏主线程
- **信号量控制**: 精确控制同时进行的API调用数量

### 2. 优化的HTTP连接
- **连接池**: 复用HTTP连接，减少建立连接的开销
- **超时配置**: 可配置的连接、读取、写入超时
- **Keep-Alive**: 长连接支持，提高性能

### 3. 智能重试机制
- **自动重试**: 网络错误和临时API错误自动重试
- **指数退避**: 重试间隔逐渐增加，避免过度请求
- **可配置**: 重试次数和策略完全可配置

### 4. 性能监控
- **实时统计**: 查看请求成功率、并发状态等
- **健康检查**: 系统健康状态监控
- **性能日志**: 详细的性能指标记录

## 配置选项

### HTTP连接池配置
```json
{
  "concurrencySettings": {
    "maxIdleConnections": 20,        // 最大空闲连接数
    "keepAliveDurationMs": 300000,   // 连接保活时间(5分钟)
    "connectTimeoutMs": 30000,       // 连接超时(30秒)
    "readTimeoutMs": 60000,          // 读取超时(60秒)
    "writeTimeoutMs": 60000          // 写入超时(60秒)
  }
}
```

### 并发控制配置
```json
{
  "concurrencySettings": {
    "maxConcurrentRequests": 10,     // 最大并发请求数
    "queueCapacity": 50,             // 请求队列容量
    "requestTimeoutMs": 30000        // 请求超时时间
  }
}
```

### 线程池配置
```json
{
  "concurrencySettings": {
    "corePoolSize": 5,               // 核心线程数
    "maximumPoolSize": 20,           // 最大线程数
    "keepAliveTimeMs": 60000         // 线程保活时间
  }
}
```

### 重试配置
```json
{
  "concurrencySettings": {
    "enableRetry": true,             // 启用重试
    "maxRetryAttempts": 3,           // 最大重试次数
    "retryDelayMs": 1000,            // 初始重试延迟
    "retryBackoffMultiplier": 2.0    // 退避倍数
  }
}
```

## 性能调优建议

### 小型服务器 (1-10人)
```json
{
  "maxConcurrentRequests": 5,
  "queueCapacity": 20,
  "corePoolSize": 3,
  "maximumPoolSize": 10,
  "maxIdleConnections": 10
}
```

### 中型服务器 (10-50人)
```json
{
  "maxConcurrentRequests": 10,
  "queueCapacity": 50,
  "corePoolSize": 5,
  "maximumPoolSize": 20,
  "maxIdleConnections": 20
}
```

### 大型服务器 (50+人)
```json
{
  "maxConcurrentRequests": 20,
  "queueCapacity": 100,
  "corePoolSize": 10,
  "maximumPoolSize": 40,
  "maxIdleConnections": 30
}
```

## 监控和诊断

### 查看统计信息
使用命令 `/llmchat stats` 查看实时统计：
- 总请求数和成功率
- 当前活跃和排队的请求
- 线程池状态
- 系统健康状态

### 性能日志
启用性能日志记录详细的请求信息：
```json
{
  "logConfig": {
    "enablePerformanceLogging": true
  }
}
```

### 健康检查
系统会自动监控以下指标：
- 并发请求数是否超限
- 队列使用率是否过高
- 线程池是否正常运行

## 故障排除

### 常见问题

1. **请求超时**
   - 增加 `requestTimeoutMs` 和 `readTimeoutMs`
   - 检查网络连接和API服务状态

2. **队列满载**
   - 增加 `queueCapacity`
   - 增加 `maxConcurrentRequests`
   - 检查API响应速度

3. **高失败率**
   - 检查API密钥和配置
   - 启用重试机制
   - 查看错误日志

### 性能优化

1. **提高并发性能**
   - 根据服务器规模调整并发参数
   - 优化网络连接配置
   - 使用更快的API服务

2. **减少延迟**
   - 减少重试延迟
   - 使用地理位置更近的API端点
   - 优化连接池配置

## 最佳实践

1. **配置调优**
   - 根据实际使用情况调整并发参数
   - 定期监控统计信息
   - 根据API提供商限制调整配置

2. **监控和维护**
   - 定期查看 `/llmchat stats`
   - 监控日志文件
   - 及时处理异常情况

3. **资源管理**
   - 合理设置线程池大小
   - 避免过度并发导致资源浪费
   - 定期重启以清理资源

## 技术细节

### 架构设计
- **ConcurrencyManager**: 核心并发管理器
- **优化的OkHttpClient**: 高性能HTTP客户端
- **CompletableFuture**: 异步编程模型
- **信号量控制**: 精确的并发限制

### 线程安全
- 所有并发操作都是线程安全的
- 使用原子操作进行统计
- 无锁设计提高性能

### 内存管理
- 自动清理过期连接
- 合理的队列大小限制
- 及时释放资源
