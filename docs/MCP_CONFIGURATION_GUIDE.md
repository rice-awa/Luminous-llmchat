# MCP配置指南

## 什么是MCP

Model Context Protocol (MCP) 是一个开放标准，用于将AI助手与各种数据源和工具连接。通过MCP，Luminous LLM Chat可以访问文件系统、数据库、API和其他外部资源，大大扩展AI助手的能力。

## 配置结构

MCP配置位于主配置文件的`mcpConfig`部分：

```json
{
  "mcpConfig": {
    "enabled": true,
    "mcpServers": {
      // MCP服务器配置...
    },
    "connectionTimeoutMs": 30000,
    "requestTimeoutMs": 10000,
    "maxRetries": 3,
    "enableResourceCaching": true,
    "resourceCacheSize": 100,
    "resourceCacheTtlMinutes": 30,
    "defaultPermissionPolicy": "OP_ONLY",
    "enableToolChangeNotifications": true,
    "enableResourceChangeNotifications": true
  }
}
```

## 服务器配置

### 基本配置格式

MCP服务器使用键值对的形式配置，其中键是服务器的唯一标识符：

```json
{
  "mcpServers": {
    "server-name": {
      // 服务器配置...
    }
  }
}
```

### STDIO类型服务器

STDIO是最常见的MCP服务器类型，通过标准输入输出进行通信：

```json
{
  "mcpServers": {
    "filesystem": {
      "type": "stdio",
      "command": "uvx",
      "args": ["mcp-server-filesystem", "/allowed/path"],
      "env": {
        "CUSTOM_VAR": "value"
      },
      "enabled": true,
      "autoApprove": ["read_file", "list_directory"]
    }
  }
}
```

#### 配置字段说明

- **type**: 连接类型，默认为`stdio`
- **command**: 启动命令（必需）
- **args**: 命令参数列表
- **env**: 环境变量
- **enabled**: 是否启用该服务器，默认为`true`
- **autoApprove**: 自动批准的工具列表

### SSE类型服务器

Server-Sent Events (SSE) 类型通过HTTP连接进行通信：

```json
{
  "mcpServers": {
    "web-service": {
      "type": "sse",
      "url": "https://api.example.com/mcp",
      "enabled": true,
      "autoApprove": []
    }
  }
}
```

#### 配置字段说明

- **type**: 必须设置为`sse`
- **url**: SSE端点URL（必需）
- **enabled**: 是否启用该服务器
- **autoApprove**: 自动批准的工具列表

## 常用MCP服务器配置示例

### 文件系统访问

```json
{
  "mcpServers": {
    "filesystem": {
      "command": "uvx",
      "args": ["mcp-server-filesystem", "/home/user/documents"],
      "autoApprove": ["read_file", "list_directory"]
    }
  }
}
```

### Git操作

```json
{
  "mcpServers": {
    "git": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-git"],
      "env": {
        "GIT_AUTHOR_NAME": "AI Assistant",
        "GIT_AUTHOR_EMAIL": "ai@example.com"
      }
    }
  }
}
```

### 网络搜索

```json
{
  "mcpServers": {
    "brave-search": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-brave-search"],
      "env": {
        "BRAVE_API_KEY": "your-api-key-here"
      },
      "autoApprove": ["search"]
    }
  }
}
```

### 数据库访问

```json
{
  "mcpServers": {
    "sqlite": {
      "command": "uvx",
      "args": ["mcp-server-sqlite", "--db-path", "/path/to/database.sqlite"],
      "autoApprove": ["read_query"]
    }
  }
}
```

### Web爬取

```json
{
  "mcpServers": {
    "firecrawl": {
      "command": "npx",
      "args": ["-y", "firecrawl-mcp"],
      "env": {
        "FIRECRAWL_API_KEY": "your-firecrawl-api-key"
      },
      "autoApprove": ["scrape", "search"]
    }
  }
}
```

## 权限和安全

### 权限策略

- **OP_ONLY**: 只有OP用户可以使用MCP工具（默认）
- **ALL_PLAYERS**: 所有玩家都可以使用
- **WHITELIST**: 仅白名单中的玩家可以使用

### 自动批准工具

通过`autoApprove`数组可以指定哪些工具自动批准执行，无需每次确认：

```json
{
  "autoApprove": [
    "read_file",
    "list_directory", 
    "search",
    "read_query"
  ]
}
```

**注意**: 谨慎使用自动批准功能，特别是对于可能修改数据的工具。

## 高级配置

### 连接设置

```json
{
  "connectionTimeoutMs": 30000,    // 连接超时时间
  "requestTimeoutMs": 10000,       // 请求超时时间
  "maxRetries": 3                  // 最大重试次数
}
```

### 缓存设置

```json
{
  "enableResourceCaching": true,   // 启用资源缓存
  "resourceCacheSize": 100,        // 缓存大小
  "resourceCacheTtlMinutes": 30    // 缓存生存时间
}
```

### 通知设置

```json
{
  "enableToolChangeNotifications": true,     // 工具变化通知
  "enableResourceChangeNotifications": true  // 资源变化通知
}
```

## 故障排除

### 常见问题

1. **服务器启动失败**
   - 检查命令路径是否正确
   - 确认所需的依赖已安装
   - 检查环境变量设置

2. **权限被拒绝**
   - 确认用户有相应权限
   - 检查`defaultPermissionPolicy`设置
   - 验证工具是否在`autoApprove`列表中

3. **连接超时**
   - 增加`connectionTimeoutMs`值
   - 检查网络连接（对于SSE类型）
   - 确认服务器进程正常运行

### 调试模式

在开发和调试时，可以启用详细日志：

```json
{
  "logConfig": {
    "logLevel": "DEBUG",
    "enableMCPRequestLog": true,
    "logFullRequestBody": true,
    "logFullResponseBody": true
  }
}
```

## 性能优化

### 连接池配置

```json
{
  "concurrencySettings": {
    "maxConcurrentRequests": 20,
    "queueCapacity": 50,
    "requestTimeoutMs": 30000
  }
}
```

### 缓存优化

- 启用资源缓存以减少重复请求
- 根据使用模式调整缓存大小和TTL
- 对于不变的资源可以设置更长的TTL

## 安全最佳实践

1. **最小权限原则**: 只配置必需的MCP服务器
2. **环境变量安全**: 敏感信息（如API密钥）使用环境变量
3. **路径限制**: 文件系统访问限制在特定目录
4. **审计日志**: 启用详细的MCP操作日志
5. **定期审查**: 定期检查和更新MCP服务器配置

## 扩展开发

如果需要开发自定义MCP服务器，请参考：

- [MCP官方规范](https://spec.modelcontextprotocol.io/)
- [MCP SDK文档](https://github.com/modelcontextprotocol/)
- [社区MCP服务器示例](https://github.com/modelcontextprotocol/servers)

通过合理配置MCP服务器，可以大大扩展Luminous LLM Chat的功能，让AI助手能够访问和操作各种外部资源，提供更强大的智能服务。