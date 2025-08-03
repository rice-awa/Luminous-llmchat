# MCP协议迁移指南：从2024-11-05到2025-06-18

## 概述

本指南说明如何将MCP客户端从旧版协议(2024-11-05)迁移到最新版协议(2025-06-18)。

## 主要变更

### 1. 传输架构变更

**旧版 (HTTP+SSE)**:
```
GET /sse        -> 建立SSE连接
POST /messages  -> 发送JSON-RPC消息
```

**新版 (Streamable HTTP)**:
```
POST /mcp  -> 统一端点，支持JSON或SSE响应
GET /mcp   -> 可选的SSE监听端点
```

### 2. 协议版本更新

**旧版**:
```json
{
  "protocolVersion": "2024-11-05"
}
```

**新版**:
```json
{
  "protocolVersion": "2025-06-18"
}
```

并且必须包含HTTP头：
```http
MCP-Protocol-Version: 2025-06-18
```

### 3. 端点配置变更

**旧版配置**:
```json
{
  "url": "https://example.com/sse"
}
```

**新版配置**:
```json
{
  "url": "https://example.com"
}
```

## 代码迁移步骤

### 步骤1: 更新协议版本

```java
// 旧版
String initRequest = "{\n" +
    "  \"params\": {\n" +
    "    \"protocolVersion\": \"2024-11-05\",\n" +
    "    ...\n" +
    "  }\n" +
    "}";

// 新版
String initRequest = "{\n" +
    "  \"params\": {\n" +
    "    \"protocolVersion\": \"2025-06-18\",\n" +
    "    ...\n" +
    "  }\n" +
    "}";

// 添加协议版本头
connection.setRequestProperty("MCP-Protocol-Version", "2025-06-18");
```

### 步骤2: 更新端点逻辑

```java
// 旧版 - 分离端点
String sseUrl = baseUrl + "/sse";
String messageUrl = baseUrl + "/messages";

// 新版 - 统一端点
String mcpEndpoint = baseUrl;  // 直接使用基础URL
```

### 步骤3: 更新请求头

```java
// 旧版
connection.setRequestProperty("Accept", "text/event-stream");

// 新版 - 支持多种响应类型
connection.setRequestProperty("Accept", "application/json, text/event-stream");
connection.setRequestProperty("MCP-Protocol-Version", "2025-06-18");
```

### 步骤4: 实现响应类型处理

```java
// 新版需要处理两种响应类型
String contentType = connection.getContentType();

if (contentType != null && contentType.startsWith("text/event-stream")) {
    // 处理SSE流响应
    handleSSEResponse(connection);
} else if (contentType != null && contentType.startsWith("application/json")) {
    // 处理JSON响应
    handleJSONResponse(connection);
} else {
    throw new IllegalStateException("未知的响应类型: " + contentType);
}
```

### 步骤5: 添加会话管理

```java
// 提取会话ID
String sessionId = connection.getHeaderField("Mcp-Session-Id");
if (sessionId != null) {
    // 在后续请求中包含会话ID
    futureConnection.setRequestProperty("Mcp-Session-Id", sessionId);
}
```

### 步骤6: 实现会话终止

```java
// 新增会话终止功能
public void terminateSession() throws Exception {
    if (sessionId == null) return;
    
    HttpURLConnection deleteConnection = (HttpURLConnection) new URL(mcpEndpoint).openConnection();
    deleteConnection.setRequestMethod("DELETE");
    deleteConnection.setRequestProperty("MCP-Protocol-Version", "2025-06-18");
    deleteConnection.setRequestProperty("Mcp-Session-Id", sessionId);
    
    int responseCode = deleteConnection.getResponseCode();
    // 处理响应...
}
```

## 向后兼容实现

如果需要同时支持新旧协议，可以实现向后兼容：

```java
public void connectWithFallback() throws Exception {
    try {
        // 首先尝试新协议
        connectWithNewProtocol();
    } catch (Exception e) {
        if (isProtocolError(e)) {
            // 降级到旧协议
            connectWithOldProtocol();
        } else {
            throw e;
        }
    }
}

private boolean isProtocolError(Exception e) {
    return e.getMessage().contains("Unsupported protocol version") ||
           e.getMessage().contains("404") ||
           e.getMessage().contains("405");
}
```

## 配置文件迁移

### 旧版配置
```json
{
  "mcpServers": {
    "example-server": {
      "url": "https://example.com/sse",
      "type": "sse"
    }
  }
}
```

### 新版配置
```json
{
  "mcpServers": {
    "example-server": {
      "url": "https://example.com",
      "type": "sse",
      "protocolVersion": "2025-06-18"
    }
  }
}
```

## 测试迁移

### 测试检查列表

- [ ] 协议版本正确设置为2025-06-18
- [ ] HTTP头包含MCP-Protocol-Version
- [ ] 使用统一MCP端点
- [ ] 支持JSON和SSE两种响应类型
- [ ] 会话管理正常工作
- [ ] 错误处理覆盖新状态码
- [ ] 向后兼容机制（如果需要）

### 验证命令

```bash
# 检查协议版本
curl -X POST https://your-server.com/mcp \
  -H "Content-Type: application/json" \
  -H "MCP-Protocol-Version: 2025-06-18" \
  -d '{"jsonrpc":"2.0","method":"initialize","params":{"protocolVersion":"2025-06-18"},"id":"test"}'
```

## 常见问题

### Q: 服务器返回404错误
A: 检查是否还在使用旧的/sse或/messages端点，应该使用统一的MCP端点。

### Q: 协议版本不匹配错误
A: 确保请求中的protocolVersion和HTTP头中的MCP-Protocol-Version都设置为2025-06-18。

### Q: 重定向循环
A: 这通常是端点配置错误导致的，确保URL配置指向正确的MCP端点。

### Q: 会话管理问题
A: 检查是否正确提取和使用Mcp-Session-Id头。

## 最佳实践

1. **渐进式迁移**: 先在测试环境验证新协议
2. **日志监控**: 增加详细的调试日志
3. **错误处理**: 实现健壮的错误处理机制
4. **配置验证**: 添加配置有效性检查
5. **文档更新**: 更新API文档和用户指南

---
*迁移指南版本: 1.0*  
*最后更新: 2025-08-03*