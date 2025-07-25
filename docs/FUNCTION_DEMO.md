# LLM Chat Mod - 新增功能演示

## 功能概览

本次更新为LLM Chat Mod添加了6个强大的管理员功能，并完善了权限控制系统。

## 新增功能列表

### 1. 执行指令功能 (execute_command)
**权限**: 仅OP可用  
**功能**: 安全地执行Minecraft服务器指令  
**安全机制**: 指令黑名单保护

```json
{
  "command": "say Hello World",
  "silent": false
}
```

**黑名单保护的指令**:
- 服务器控制: stop, restart, shutdown
- 权限管理: op, deop
- 封禁管理: ban, ban-ip
- 存档管理: save-all, save-off
- 等等...

### 2. 设置方块功能 (set_block)
**权限**: 仅OP可用  
**功能**: 在指定位置设置方块  
**安全限制**: 最大距离100方块，Y坐标限制

```json
{
  "x": 100,
  "y": 64,
  "z": 200,
  "block_type": "diamond_block",
  "replace": true
}
```

### 3. 生成实体功能 (summon_entity)
**权限**: 仅OP可用  
**功能**: 在指定位置生成实体  
**安全限制**: 最大距离50方块，最大数量10个

```json
{
  "entity_type": "cow",
  "x": 0,
  "y": 100,
  "z": 0,
  "count": 5
}
```

### 4. 传送玩家功能 (teleport_player)
**权限**: 所有玩家可传送自己，OP可传送他人  
**功能**: 传送到坐标或其他玩家身边

```json
{
  "player_name": "Steve",
  "x": 0,
  "y": 100,
  "z": 0,
  "dimension": "overworld"
}
```

或传送到其他玩家身边：
```json
{
  "target_player": "Alex"
}
```

### 5. 天气控制功能 (control_weather)
**权限**: 仅OP可用  
**功能**: 控制指定世界的天气

```json
{
  "weather_type": "rain",
  "duration": 600,
  "world": "overworld"
}
```

支持的天气类型：
- clear: 晴朗
- rain: 下雨
- thunder: 雷雨

### 6. 时间控制功能 (control_time)
**权限**: 仅OP可用  
**功能**: 控制指定世界的时间

```json
{
  "time_type": "noon",
  "world": "overworld"
}
```

支持的时间类型：
- day: 白天
- night: 夜晚
- noon: 正午
- midnight: 午夜
- sunrise: 日出
- sunset: 日落
- specific: 指定时间（需要time_value参数）

## 权限控制系统

### 统一权限管理
新增的`PermissionHelper`工具类提供了统一的权限检查机制：

- **OP权限检查**: 统一的OP状态验证
- **功能权限**: 细粒度的功能访问控制
- **安全保护**: 多层安全检查机制

### 权限级别
1. **所有玩家**: 基础信息查询、传送自己
2. **OP玩家**: 所有功能，包括世界修改和管理员指令

### 安全机制
- **指令黑名单**: 禁止执行危险指令
- **距离限制**: 限制操作范围
- **数量限制**: 限制生成实体数量
- **参数验证**: 严格的输入验证

## 使用示例

### 场景1: 建筑辅助
```json
// 快速设置大量方块
{
  "function": "set_block",
  "arguments": {
    "x": 100,
    "y": 64,
    "z": 100,
    "block_type": "stone",
    "replace": true
  }
}
```

### 场景2: 活动管理
```json
// 生成活动用的动物
{
  "function": "summon_entity",
  "arguments": {
    "entity_type": "pig",
    "x": 0,
    "y": 100,
    "z": 0,
    "count": 10
  }
}
```

### 场景3: 环境控制
```json
// 设置活动天气
{
  "function": "control_weather",
  "arguments": {
    "weather_type": "clear",
    "duration": 3600,
    "world": "overworld"
  }
}
```

### 场景4: 玩家管理
```json
// 传送玩家到活动地点
{
  "function": "teleport_player",
  "arguments": {
    "player_name": "Steve",
    "x": 0,
    "y": 100,
    "z": 0
  }
}
```

## 安全考虑

### 1. 权限控制
- 所有管理员功能都需要OP权限
- 多层权限检查确保安全
- 详细的错误消息提供清晰反馈

### 2. 操作限制
- 距离限制防止远程破坏
- 数量限制防止服务器过载
- 坐标验证确保操作有效性

### 3. 指令安全
- 黑名单机制防止危险指令执行
- 受限制指令前缀保护
- 服务器控制台级别的安全检查

## 向后兼容性

- 所有现有功能保持完全兼容
- 现有函数的权限检查已统一更新
- 配置文件格式保持不变

## 测试覆盖

新功能包含全面的测试覆盖：
- 权限检查测试
- 参数验证测试
- 错误处理测试
- 安全机制测试

## 总结

本次更新大幅增强了LLM Chat Mod的功能性和安全性：

✅ **6个新的管理员功能**  
✅ **统一的权限管理系统**  
✅ **多层安全保护机制**  
✅ **全面的测试覆盖**  
✅ **详细的文档说明**  
✅ **向后兼容性保证**  

这些功能让AI助手能够更好地协助服务器管理和玩家互动，同时确保服务器的安全性和稳定性。
