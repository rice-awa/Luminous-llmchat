# MCP SSE 连接问题分析与修复方案

## 问题描述
MCP SSE 客户端连接失败，出现以下错误：
- `Invalid SSE response. Status code: 200` - 收到 HTML 响应而非 SSE 流
- `Client failed to initialize by explicit API call` - 初始化失败
- 持续重试但无法建立连接

## 根本原因分析

### 1. 协议版本不匹配
- **问题位置**: `HttpClientSseClientTransport.java:65`
- **问题描述**: 传输层硬编码协议版本为 `"2024-11-05"`
- **影响**: 与客户端请求的 `"2025-06-18"` 版本不匹配

### 2. 端点路径错误
- **问题**: 连接到了 CodeSandbox 的 HTML 页面而非 SSE 端点
- **原因**: 缺少正确的 SSE 端点路径处理
- **影响**: 收到 HTML 响应而非 SSE 数据流

### 3. 响应验证不足
- **问题**: 没有验证服务器返回的 Content-Type
- **影响**: 无法区分 HTML 响应和有效的 SSE 流

## 修复方案

### 方案一：修复协议版本不匹配（推荐）

```java
// 在 MCPSseClient.java 中修改传输层创建
McpClientTransport transport = HttpClientSseClientTransport.builder(config.getUrl())
    .customizeClient(clientBuilder -> 
        clientBuilder.followRedirects(java.net.http.HttpClient.Redirect.NORMAL))
    .customizeRequest(requestBuilder -> {
        requestBuilder
            .header("MCP-Protocol-Version", "2024-11-05") // 使用服务端支持的版本
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache");
    })
    .sseEndpoint("/sse") // 明确指定 SSE 端点
    .build();
```

### 方案二：增强响应验证

```java
private boolean validateSseResponse(java.net.http.HttpResponse<String> response) {
    String contentType = response.headers().firstValue("Content-Type").orElse("");
    
    // 验证是否为 SSE 响应
    if (!contentType.contains("text/event-stream")) {
        logger.error("服务器返回了错误的 Content-Type: {}, 期望: text/event-stream", contentType);
        return false;
    }
    
    // 验证响应体是否为 HTML
    if (response.body().startsWith("<!doctype html>") || 
        response.body().startsWith("<html>")) {
        logger.error("服务器返回了 HTML 响应而非 SSE 流");
        return false;
    }
    
    return true;
}
```

### 方案三：改进错误处理和重试机制

```java
// 在连接失败时提供更详细的错误信息
if (errorMsg.contains("Invalid SSE response")) {
    logger.error("MCP 服务器返回无效的 SSE 响应: {}", config.getName());
    logger.info("可能的原因:");
    logger.info("1. 服务器端点不是 SSE 端点");
    logger.info("2. 协议版本不匹配");
    logger.info("3. 服务器未正确实现 MCP SSE 协议");
    logger.info("建议检查服务器配置: {}", config.getUrl());
}
```

## 实施建议

1. **立即修复**: 更新协议版本为服务端支持的版本
2. **增强验证**: 添加响应类型验证，避免处理 HTML 响应
3. **改进日志**: 提供更详细的错误信息和解决方案
4. **配置优化**: 允许用户配置协议版本和端点路径

## 预期效果

修复后应该能够：
- 正确连接到 MCP SSE 服务器
- 接收有效的 SSE 数据流
- 避免连接到错误的端点
- 提供清晰的错误信息

## 测试验证

修复后需要测试：
1. 连接成功的场景
2. 错误端点的场景
3. 协议版本不匹配的场景
4. 网络异常的场景