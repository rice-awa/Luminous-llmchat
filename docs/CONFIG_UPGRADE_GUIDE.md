# 配置文件升级指南

## 概述

LLM Chat 模组现在支持智能配置升级，能够自动检测旧版本配置文件并补齐新增的配置项，确保配置的向后兼容性和完整性。

## 🚀 新功能特性

### 1. 自动配置版本检测
- 自动识别配置文件版本
- 支持从旧版本平滑升级
- 保留用户自定义设置

### 2. 智能配置补齐
- 自动添加缺失的配置项
- 使用合理的默认值
- 验证配置项的有效性

### 3. 配置备份机制
- 自动备份损坏的配置文件
- 防止配置丢失
- 支持手动恢复

### 4. 配置状态监控
- 实时检查配置完整性
- 提供详细的状态信息
- 支持手动验证和修复

## 📋 配置版本历史

### 版本 1.5.1 (当前版本)
- 添加并发配置 (`concurrencySettings`)
- 增强的日志配置
- 改进的提供商配置
- Token统计功能

### 版本 1.5.0
- 基础配置结构
- 提供商管理
- 日志系统

### Legacy (无版本号)
- 早期配置格式
- 基本功能配置

## 🔧 使用方法

### 1. 查看配置状态
```
/llmchat config status
```

显示信息包括：
- 📋 配置版本信息
- 🔍 配置有效性状态
- ⚙️ 并发配置状态
- 🔧 提供商配置状态

### 2. 验证并修复配置
```
/llmchat config validate
```

功能：
- 检查配置完整性
- 自动补齐缺失项
- 修复无效配置
- 重新初始化服务

### 3. 重新加载配置
```
/llmchat reload
```

## 🔄 升级过程

### 自动升级流程
1. **检测版本**: 读取配置文件时自动检测版本
2. **执行升级**: 根据版本差异执行相应升级步骤
3. **补齐配置**: 添加缺失的配置项
4. **保存配置**: 自动保存升级后的配置
5. **重新初始化**: 重新加载服务和组件

### 升级示例

**旧配置 (Legacy)**:
```json
{
  "defaultTemperature": 0.7,
  "enableHistory": true,
  "providers": [...]
}
```

**升级后 (v1.5.1)**:
```json
{
  "configVersion": "1.5.1",
  "defaultTemperature": 0.7,
  "enableHistory": true,
  "concurrencySettings": {
    "maxConcurrentRequests": 10,
    "queueCapacity": 50,
    ...
  },
  "providers": [...]
}
```

## 🛡️ 安全机制

### 1. 配置备份
- 损坏的配置文件会自动备份
- 备份文件命名: `config.json.backup.{timestamp}`
- 可手动恢复备份文件

### 2. 默认值保护
- 所有新增配置项都有合理默认值
- 不会影响现有功能
- 确保系统稳定运行

### 3. 验证机制
- 配置加载时自动验证
- 启动时检查配置完整性
- 提供手动验证命令

## 📊 配置项说明

### 新增的并发配置
```json
{
  "concurrencySettings": {
    "maxIdleConnections": 20,        // HTTP连接池大小
    "keepAliveDurationMs": 300000,   // 连接保活时间
    "connectTimeoutMs": 30000,       // 连接超时
    "readTimeoutMs": 60000,          // 读取超时
    "writeTimeoutMs": 60000,         // 写入超时
    "maxConcurrentRequests": 10,     // 最大并发请求
    "queueCapacity": 50,             // 队列容量
    "requestTimeoutMs": 30000,       // 请求超时
    "corePoolSize": 5,               // 核心线程数
    "maximumPoolSize": 20,           // 最大线程数
    "keepAliveTimeMs": 60000,        // 线程保活时间
    "enableRetry": true,             // 启用重试
    "maxRetryAttempts": 3,           // 最大重试次数
    "retryDelayMs": 1000,            // 重试延迟
    "retryBackoffMultiplier": 2.0,   // 退避倍数
    "enableRateLimit": false,        // 启用速率限制
    "requestsPerMinute": 60,         // 每分钟请求数
    "requestsPerHour": 1000          // 每小时请求数
  }
}
```

## 🔍 故障排除

### 常见问题

1. **配置文件损坏**
   - 系统会自动备份并创建新配置
   - 检查 `config.json.backup.*` 文件
   - 可手动恢复备份

2. **升级失败**
   - 使用 `/llmchat config validate` 手动修复
   - 检查日志文件获取详细错误信息
   - 必要时删除配置文件重新生成

3. **配置项缺失**
   - 运行 `/llmchat config status` 检查状态
   - 使用 `/llmchat config validate` 补齐配置
   - 重新加载配置: `/llmchat reload`

### 手动升级步骤

如果自动升级失败，可以手动升级：

1. **备份当前配置**
   ```bash
   cp config/lllmchat/config.json config/lllmchat/config.json.manual.backup
   ```

2. **检查配置状态**
   ```
   /llmchat config status
   ```

3. **验证并修复**
   ```
   /llmchat config validate
   ```

4. **重新加载**
   ```
   /llmchat reload
   ```

## 📝 最佳实践

1. **定期检查配置状态**
   - 使用 `/llmchat config status` 定期检查
   - 关注配置有效性和版本信息

2. **及时升级配置**
   - 模组更新后运行 `/llmchat config validate`
   - 确保使用最新的配置特性

3. **备份重要配置**
   - 定期备份自定义配置
   - 保存API密钥等重要信息

4. **监控系统状态**
   - 使用 `/llmchat stats` 监控性能
   - 根据统计信息调整配置参数

## 🎯 总结

配置升级功能确保了：
- ✅ 向后兼容性 - 旧配置文件可以无缝升级
- ✅ 配置完整性 - 自动补齐缺失的配置项
- ✅ 系统稳定性 - 验证配置有效性
- ✅ 用户友好 - 提供详细的状态信息和修复工具

通过这些功能，用户可以放心升级模组版本，无需担心配置文件兼容性问题。
