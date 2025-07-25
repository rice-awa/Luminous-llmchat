# LLM Chat Mod - Function Call 安全指南

## 概述

本文档详细说明了LLM Chat Mod中Function Call功能的安全机制和权限控制系统。

## 新增功能

### 1. 统一权限管理系统

#### PermissionHelper 工具类
- **位置**: `src/main/java/com/riceawa/llm/function/PermissionHelper.java`
- **功能**: 统一处理所有LLM函数的权限检查
- **特性**:
  - 统一的OP权限检查
  - 指令黑名单保护
  - 细粒度权限控制
  - 安全错误消息生成

#### 权限级别
1. **所有玩家**: 基础信息查询（时间、天气、自己的状态等）
2. **OP玩家**: 所有功能，包括世界修改和管理员指令
3. **特殊权限**: 某些功能允许玩家操作自己但需要OP权限操作他人

### 2. 新增管理员功能

#### ExecuteCommandFunction - 执行服务器指令
- **权限**: 仅OP可用
- **安全机制**:
  - 指令黑名单保护
  - 禁止执行危险指令（stop、op、ban等）
  - 限制特殊指令前缀（execute、forceload等）
- **参数**:
  - `command`: 要执行的指令
  - `silent`: 是否静默执行

#### SetBlockFunction - 设置方块
- **权限**: 仅OP可用
- **安全限制**:
  - 最大操作距离：100方块
  - Y坐标范围：-64到320
  - 可选择是否替换现有方块
- **参数**:
  - `x`, `y`, `z`: 坐标
  - `block_type`: 方块类型
  - `replace`: 是否替换现有方块

#### SummonEntityFunction - 生成实体
- **权限**: 仅OP可用
- **安全限制**:
  - 最大生成距离：50方块
  - 最大生成数量：10个
  - 自动添加随机偏移避免重叠
- **参数**:
  - `entity_type`: 实体类型
  - `x`, `y`, `z`: 坐标
  - `count`: 生成数量

#### TeleportPlayerFunction - 传送玩家
- **权限**: 
  - 所有玩家可传送自己
  - OP可传送任何玩家
- **功能**:
  - 传送到指定坐标
  - 传送到其他玩家身边
  - 跨维度传送
- **参数**:
  - `player_name`: 要传送的玩家（可选）
  - `target_player`: 传送目标玩家
  - `x`, `y`, `z`: 目标坐标
  - `dimension`: 目标维度

#### WeatherControlFunction - 天气控制
- **权限**: 仅OP可用
- **功能**: 控制指定世界的天气
- **参数**:
  - `weather_type`: 天气类型（clear/rain/thunder）
  - `duration`: 持续时间（秒）
  - `world`: 目标世界

#### TimeControlFunction - 时间控制
- **权限**: 仅OP可用
- **功能**: 控制指定世界的时间
- **参数**:
  - `time_type`: 时间类型（day/night/noon/midnight/sunrise/sunset/specific）
  - `time_value`: 具体时间值（仅specific类型）
  - `world`: 目标世界

## 安全机制

### 1. 指令黑名单
以下指令被禁止通过LLM执行：
- 服务器控制: `stop`, `restart`, `shutdown`
- 权限管理: `op`, `deop`
- 白名单管理: `whitelist`
- 封禁管理: `ban`, `ban-ip`, `pardon`, `pardon-ip`
- 存档管理: `save-all`, `save-off`, `save-on`
- 配置管理: `reload`
- 调试命令: `debug`, `perf`, `jfr`
- 数据包管理: `datapack`
- 函数执行: `function`（避免递归）

### 2. 受限制的指令前缀
- `execute`: 执行命令
- `forceload`: 强制加载区块
- `worldborder`: 世界边界
- `difficulty`: 难度设置
- `gamerule`: 游戏规则

### 3. 距离和数量限制
- **方块设置**: 最大距离100方块
- **实体生成**: 最大距离50方块，最大数量10个
- **坐标验证**: Y坐标限制在-64到320之间

### 4. 权限检查层级
1. **函数级权限**: `hasPermission()` 方法的基础检查
2. **操作级权限**: 在 `execute()` 方法中的具体权限验证
3. **参数验证**: 对所有输入参数进行安全验证
4. **黑名单检查**: 对危险操作进行额外保护

## 使用示例

### 执行安全指令
```json
{
  "command": "say Hello World",
  "silent": false
}
```

### 设置方块
```json
{
  "x": 100,
  "y": 64,
  "z": 200,
  "block_type": "diamond_block",
  "replace": true
}
```

### 生成实体
```json
{
  "entity_type": "cow",
  "x": 0,
  "y": 100,
  "z": 0,
  "count": 5
}
```

### 传送玩家
```json
{
  "player_name": "Steve",
  "x": 0,
  "y": 100,
  "z": 0,
  "dimension": "overworld"
}
```

### 控制天气
```json
{
  "weather_type": "rain",
  "duration": 600,
  "world": "overworld"
}
```

### 控制时间
```json
{
  "time_type": "noon",
  "world": "overworld"
}
```

## 测试覆盖

### 单元测试
- `PermissionHelperTest`: 权限管理系统测试
- `AdminFunctionsTest`: 管理员功能测试
- 现有测试已更新以使用统一权限系统

### 测试覆盖范围
- 权限检查逻辑
- 参数验证
- 错误处理
- 安全限制
- 黑名单保护

## 配置建议

### 生产环境
- 确保只有可信任的玩家拥有OP权限
- 定期审查OP玩家列表
- 监控LLM函数调用日志
- 考虑添加额外的审计日志

### 开发环境
- 可以临时调整距离和数量限制进行测试
- 使用测试账号验证权限控制
- 测试各种边界条件和错误情况

## 更新日志

### v1.1.0 - 新增管理员功能
- 添加了6个新的管理员功能函数
- 实现了统一的权限管理系统
- 增强了安全保护机制
- 更新了所有现有函数的权限检查
- 添加了全面的单元测试覆盖

## 注意事项

1. **权限控制**: 所有新功能都严格限制为OP权限
2. **安全第一**: 多层安全检查确保系统安全
3. **向后兼容**: 现有功能保持完全兼容
4. **测试覆盖**: 新功能都有对应的单元测试
5. **文档完整**: 提供详细的使用说明和安全指南
