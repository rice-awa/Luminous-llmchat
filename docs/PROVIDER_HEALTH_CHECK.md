# Provider健康检查功能

## 🎯 功能概述

新增的Provider健康检查功能可以在重载配置文件时自动进行异步检测任务，发送最小token测试连接来验证服务是否正常，并在provider list命令中显示实时的可用状态。

## ✨ 主要特性

### 1. 异步健康检查
- 🔄 **自动触发**: 配置重载时自动启动健康检查
- ⚡ **异步执行**: 不阻塞主线程，后台进行检测
- 🎯 **最小测试**: 发送单个"test"消息，使用1个token进行连接测试
- ⏱️ **智能缓存**: 5分钟缓存时间，避免频繁检测

### 2. 详细状态分类
- 🟢 **在线**: 连接正常，API响应成功
- 🔴 **离线**: 连接失败，包含具体错误信息
- ⚠️ **配置错误**: API密钥无效、URL错误等配置问题
- 🔐 **认证错误**: API密钥认证失败
- 🌐 **网络错误**: 连接超时、网络不可达
- 📊 **API错误**: 服务端返回错误
- 🚫 **速率限制**: 请求频率超限

### 3. 增强的显示界面
- 📡 **实时状态**: provider list显示最新的健康状态
- 🕐 **检测时间**: 显示最后检测的具体时间
- 💬 **错误详情**: 失败时显示具体的错误原因
- 🎨 **彩色标识**: 不同状态使用不同颜色区分

## 🚀 使用方法

### 1. 重载配置触发检测
```bash
/llmchat reload
```
执行后会看到：
```
🔄 正在重载配置...
✅ 配置已重载
Provider health check completed for 3 providers
```

### 2. 查看Provider状态
```bash
/llmchat provider list
```
显示效果：
```
🔍 正在检测Provider状态...
📡 Provider状态报告:
Provider配置状态: 2/3 有效
✅ Provider状态列表:
  - openai: 🟢 在线 (检测时间: 14:32:15)
  - openrouter: 🔴 离线 (检测时间: 14:32:16) - 连接失败: HTTP 401: Unauthorized
  - deepseek: ⚠️ 配置无效 - API密钥为占位符，需要设置真实密钥

📌 当前使用: openai / gpt-3.5-turbo
```

## 🔧 技术实现

### 核心组件

#### 1. ProviderHealthChecker
- **位置**: `src/main/java/com/riceawa/llm/service/ProviderHealthChecker.java`
- **功能**: 
  - 异步健康检查执行
  - 结果缓存管理
  - 错误分类处理

#### 2. HealthStatus类
```java
public static class HealthStatus {
    private final boolean healthy;           // 是否健康
    private final String message;            // 状态消息
    private final ErrorType errorType;       // 错误类型
    private final LocalDateTime checkTime;   // 检测时间
}
```

#### 3. 错误类型枚举
```java
public enum ErrorType {
    NONE,           // 无错误
    CONFIG_ERROR,   // 配置错误
    AUTH_ERROR,     // 认证错误
    NETWORK_ERROR,  // 网络错误
    RATE_LIMIT_ERROR, // 速率限制
    MODEL_ERROR,    // 模型错误
    API_ERROR,      // API错误
    UNKNOWN_ERROR   // 未知错误
}
```

### 集成点

#### 1. 配置重载集成
- `LLMChatConfig.reload()` - 触发健康检查
- `LLMServiceManager.reload()` - 服务重载时检查

#### 2. ProviderManager增强
- `checkAllProvidersHealth()` - 检查所有provider
- `checkProviderHealth(String)` - 检查单个provider
- `getDetailedConfigurationReport()` - 获取详细状态报告

#### 3. 命令界面更新
- `handleListProviders()` - 显示实时健康状态
- 异步获取状态，避免阻塞用户界面

## 📊 性能特性

### 1. 缓存机制
- **缓存时间**: 5分钟
- **缓存策略**: 基于provider名称的键值缓存
- **过期检查**: 自动检查缓存是否过期

### 2. 超时控制
- **连接超时**: 10秒
- **请求超时**: 基于现有的并发设置
- **优雅降级**: 超时时显示网络错误

### 3. 资源优化
- **最小请求**: 仅发送1个token的测试消息
- **连接复用**: 使用现有的HTTP客户端连接池
- **异步执行**: 不阻塞主线程和用户操作

## 🛡️ 错误处理

### 1. 配置验证
- API密钥有效性检查
- URL格式验证
- 模型列表完整性检查

### 2. 网络异常处理
- 连接超时处理
- DNS解析失败处理
- SSL证书错误处理

### 3. API错误分类
- HTTP状态码分析
- 错误消息解析
- 认证失败识别

## 🔮 未来扩展

### 1. 健康检查增强
- [ ] 支持自定义检查间隔
- [ ] 添加健康检查历史记录
- [ ] 支持webhook通知

### 2. 监控功能
- [ ] 添加健康检查统计
- [ ] 支持导出健康报告
- [ ] 集成监控面板

### 3. 自动故障转移
- [ ] 自动切换到健康的provider
- [ ] 智能负载均衡
- [ ] 故障恢复检测

## 📝 配置示例

```json
{
  "providers": [
    {
      "name": "openai",
      "apiBaseUrl": "https://api.openai.com/v1",
      "apiKey": "sk-your-real-api-key-here",
      "models": ["gpt-3.5-turbo", "gpt-4"]
    },
    {
      "name": "openrouter",
      "apiBaseUrl": "https://openrouter.ai/api/v1",
      "apiKey": "sk-or-your-real-api-key-here",
      "models": ["anthropic/claude-3.5-sonnet"]
    }
  ]
}
```

## 🎉 总结

Provider健康检查功能为LLM Chat模组提供了企业级的服务监控能力，让管理员能够实时了解各个AI服务提供商的状态，及时发现和解决连接问题，确保用户获得最佳的AI聊天体验。
