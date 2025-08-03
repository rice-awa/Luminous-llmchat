# MCP配置修复总结

## 问题描述

在MCP系统中存在以下问题：
1. 配置文件读取逻辑有误，导致无法正确解析MCP服务器配置
2. SSE连接出现格式错误，导致连接失败
3. 配置文件中包含过多复杂配置项，增加了维护难度

## 修复内容

### 1. 修复配置解析逻辑

**文件**: `src/main/java/com/riceawa/llm/config/LLMChatConfig.java`

**修复内容**:
- 修复了`parseMcpConfig`方法中的JSON解析逻辑
- 为`MCPConfigParser.parseServerDictionary`方法提供正确的JSON结构
- 增加了详细的配置解析日志输出
- 改进了错误处理机制

**关键修改**:
```java
// 为解析器提供完整的JSON结构
JsonObject fullJson = new JsonObject();
fullJson.add("mcpServers", mcpConfigJson.get("mcpServers"));
var serverMap = MCPConfigParser.parseServerDictionary(fullJson);
```

### 2. 改进SSE连接稳定性

**文件**: `src/main/java/com/riceawa/mcp/client/MCPSseClient.java`

**修复内容**:
- 增加了连接重试机制（最多3次重试）
- 延长了请求超时时间（从10秒增加到30秒）
- 增加了递增延迟重试策略
- 改进了错误日志记录
- 优化了健康检查机制

**关键修改**:
```java
// 增加重试机制
for (int i = 0; i < maxRetries; i++) {
    try {
        result = mcpClient.initialize();
        if (result != null) {
            break;
        }
    } catch (Exception e) {
        // 递增延迟重试
        Thread.sleep(1000 * (i + 1));
    }
}
```

### 3. 简化MCP配置结构

**修改的文件**:
- `src/main/java/com/riceawa/mcp/config/MCPConfig.java`
- `src/main/java/com/riceawa/mcp/config/MCPServerConfig.java`
- `src/main/java/com/riceawa/mcp/config/MCPConfigParser.java`
- `run/config/lllmchat/config.json`

**简化内容**:
- 移除了复杂的缓存配置选项
- 简化了权限配置
- 移除了变更通知功能
- 精简了服务器配置项

**配置文件优化**:
```json
"mcpConfig": {
  "enabled": true,
  "mcpServers": {
    "minecraft-wiki-fetch": {
      "name": "minecraft-wiki-fetch",
      "type": "sse",
      "url": "https://qddzxm-8000.csb.app/sse",
      "enabled": true,
      "description": "Minecraft Wiki查询服务"
    }
  },
  "connectionTimeoutMs": 30000,
  "requestTimeoutMs": 30000,
  "maxRetries": 3,
  "defaultPermissionPolicy": "OP_ONLY"
}
```

## 核心功能保留

简化后的MCP系统保留了以下核心功能：
1. **基本连接管理** - 支持SSE和STDIO连接类型
2. **工具调用** - 支持调用MCP服务器提供的工具
3. **资源读取** - 支持读取MCP服务器提供的资源
4. **权限控制** - 基本的权限策略控制
5. **错误处理** - 改进的连接错误处理和重试机制

## 测试建议

1. **重启游戏** - 重新启动Minecraft客户端以加载新配置
2. **检查连接** - 使用`/llmchat mcp status`命令检查MCP系统状态
3. **测试重连** - 使用`/llmchat mcp reload`命令测试配置重载
4. **功能验证** - 测试MCP工具调用功能是否正常工作

## 预期结果

修复后应该能够看到：
- MCP配置正确加载，显示1个已配置的服务器
- SSE连接稳定，减少连接错误
- 系统日志更清晰，便于问题诊断
- 配置文件更简洁，便于维护

## 后续优化建议

1. **监控连接质量** - 定期检查MCP连接的稳定性
2. **优化超时设置** - 根据网络状况调整超时参数
3. **扩展功能** - 根据需要逐步添加高级功能
4. **文档完善** - 补充MCP配置和使用文档