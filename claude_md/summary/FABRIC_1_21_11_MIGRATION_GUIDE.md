# Fabric 1.21.11 迁移修复指南

## 概述

本文档记录了将 Luminous LLM Chat 模组从旧版本迁移到 Fabric 1.21.11 所需的代码修改。

**目标版本信息：**
- Minecraft: 1.21.11
- Yarn Mappings: 1.21.11+build.4
- Fabric Loader: 0.18.4
- Fabric API: 0.141.3+1.21.11
- Loom: 1.15-SNAPSHOT

---

## 一、权限检查 API 变更

### 问题
`ServerCommandSource.hasPermissionLevel(int)` 方法已被移除。

### 原因
Minecraft 1.21.11 引入了新的权限系统，使用 `PermissionSource` 接口和 `PermissionPredicate` 替代了旧的整数权限级别检查。

### 修复方案

#### 方案一：使用 `CommandManager.requirePermissionLevel()` 静态方法（推荐）

```java
// 旧代码
.requires(source -> source.hasPermissionLevel(2))

// 新代码
.requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
```

#### 权限级别常量映射

| 旧权限级别 | 新常量 | 说明 |
|-----------|--------|------|
| 1 | `CommandManager.MODERATORS_CHECK` | 普通OP权限 |
| 2 | `CommandManager.GAMEMASTERS_CHECK` | 游戏管理员权限 |
| 3 | `CommandManager.ADMINS_CHECK` | 管理员权限 |
| 4 | `CommandManager.OWNERS_CHECK` | 服务器所有者权限 |

#### 方案二：直接使用 PermissionPredicate

```java
// 如果需要自定义权限检查
.requires(source -> source.getPermissions().test(...))
```

### 涉及文件

| 文件 | 行号 | 修改内容 |
|------|------|----------|
| `HistoryCommand.java` | 28 | 权限检查修改 |
| `LogCommand.java` | 23 | 权限检查修改 |
| `LLMChatCommand.java` | 988, 1924, 2205, 2243, 2273, 2414, 2441, 2495 | 权限检查修改 |

### 代码修改示例

**HistoryCommand.java 第28行：**
```java
// 修改前
.requires(source -> source.hasPermissionLevel(2))

// 修改后
.requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
```

---

## 二、Entity.getWorld() 方法重命名

### 问题
`Entity.getWorld()` 方法已被重命名为 `Entity.getEntityWorld()`。

### 原因
Fabric 1.21.9/1.21.10 版本中，为了一致性和避免歧义，将 `getWorld()` 重命名为 `getEntityWorld()`。

### 修复方案

```java
// 旧代码
ServerWorld world = (ServerWorld) player.getWorld();

// 新代码
ServerWorld world = (ServerWorld) player.getEntityWorld();
```

### 涉及文件

| 文件 | 行号 | 修改内容 |
|------|------|----------|
| `FunctionRegistry.java` | 250, 347, 348 | `getWorld()` → `getEntityWorld()` |
| `NearbyEntitiesFunction.java` | 75 | `getWorld()` → `getEntityWorld()` |
| `PlayerStatsFunction.java` | 86 | `getWorld()` → `getEntityWorld()` |
| `SetBlockFunction.java` | 125 | `getWorld()` → `getEntityWorld()` |
| `SummonEntityFunction.java` | 131 | `getWorld()` → `getEntityWorld()` |
| `TeleportPlayerFunction.java` | 117, 143 | `getWorld()` → `getEntityWorld()` |
| `WorldInfoFunction.java` | 48 | `getWorld()` → `getEntityWorld()` |

---

## 三、Entity.getPos() 方法移除

### 问题
`Entity.getPos()` 方法已被移除。

### 替代方案

根据使用场景，选择以下替代方法：

| 场景 | 替代方法 | 返回类型 |
|------|----------|----------|
| 获取方块位置 | `getBlockPos()` | `BlockPos` |
| 获取精确位置 | `getEyePos()` | `Vec3d` |
| 获取眼睛位置 | `getEyePos()` | `Vec3d` |
| 获取相机位置 | `getCameraPosVec(float)` | `Vec3d` |

### 修复方案

```java
// 旧代码
Vec3d playerPos = player.getPos();

// 新代码（如果需要精确坐标）
Vec3d playerPos = player.getEyePos();
// 或者
Vec3d playerPos = player.getPos(); // 如果Entity类仍保留此方法
// 或者使用
Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
```

### 涉及文件

| 文件 | 行号 | 修改内容 |
|------|------|----------|
| `NearbyEntitiesFunction.java` | 69 | `getPos()` 替换 |
| `SummonEntityFunction.java` | 110 | `getPos()` 替换 |
| `TeleportPlayerFunction.java` | 116 | `getPos()` 替换 |

### 注意事项
需要根据具体使用场景判断使用哪个替代方法。如果只是需要玩家的大致位置用于距离计算，`getBlockPos()` 可能更合适。

---

## 四、PlayerEntity.getServer() 方法移除

### 问题
`PlayerEntity.getServer()` 方法已被移除。

### 替代方案

#### 对于 ServerPlayerEntity

`ServerPlayerEntity` 类中有一个 `server` 字段可以直接访问，但它是私有的。推荐使用以下方式：

```java
// 方案一：通过 ServerWorld 获取（推荐）
MinecraftServer server = player.getEntityWorld().getServer();

// 方案二：对于 ServerPlayerEntity，可以访问其 server 字段
// 需要使用 Accessor 或检查是否有公共方法
```

#### 对于 PlayerEntity（可能是客户端玩家）

```java
// 需要先判断是否是 ServerPlayerEntity
if (player instanceof ServerPlayerEntity serverPlayer) {
    MinecraftServer server = serverPlayer.getServerWorld().getServer();
}
```

### 涉及文件

| 文件 | 行号 | 修改内容 |
|------|------|----------|
| `LLMChatCommand.java` | 1342, 1358, 1372, 1437, 1597, 1648, 1763, 1776 | `getServer()` 替换 |
| `FunctionRegistry.java` | 186 | `getServer()` 替换 |

---

## 五、ServerCommandSource 构造函数变更

### 问题
`ServerCommandSource` 构造函数签名已更改，不再接受 `int permissionLevel` 参数。

### 原代码（ExecuteCommandFunction.java 第112-122行）
```java
ServerCommandSource captureSource = new ServerCommandSource(
    outputCapture,
    consoleSource.getPosition(),
    consoleSource.getRotation(),
    consoleSource.getWorld(),
    4, // 最高权限级别 - 此参数已无效
    consoleSource.getName(),
    consoleSource.getDisplayName(),
    consoleSource.getServer(),
    consoleSource.getEntity()
);
```

### 修复方案

使用 `CommandManager.createSource()` 方法或使用现有的命令源：

```java
// 方案一：使用现有命令源
ServerCommandSource captureSource = consoleSource.withOutput(outputCapture);

// 方案二：使用 CommandManager.createSource() 创建高权限源
ServerCommandSource captureSource = CommandManager.createSource(
    CommandManager.OWNERS_CHECK.toPredicate()
);

// 方案三：通过 with 方法链式调用修改
ServerCommandSource captureSource = consoleSource
    .withOutput(outputCapture)
    .withPermissionLevel(4); // 如果此方法存在
```

### 涉及文件

| 文件 | 行号 | 修改内容 |
|------|------|----------|
| `ExecuteCommandFunction.java` | 112-122 | 构造函数重构 |

---

## 六、CommandManager.executeWithPrefix() 方法移除

### 问题
`CommandManager.executeWithPrefix()` 方法已被移除。

### 替代方案

```java
// 旧代码
server.getCommandManager().executeWithPrefix(captureSource, command);

// 新代码 - 使用 parseAndExecute()
server.getCommandManager().parseAndExecute(captureSource, command);

// 或者使用 execute() 配合 ParseResults
var parseResults = server.getCommandManager().getDispatcher().parse(command, captureSource);
server.getCommandManager().execute(parseResults, command);
```

### 涉及文件

| 文件 | 行号 | 修改内容 |
|------|------|----------|
| `ExecuteCommandFunction.java` | 140 | `executeWithPrefix` 替换 |

---

## 七、ServerWorld.getSpawnPos() 方法变更

### 问题
`ServerWorld.getSpawnPos()` 方法已被移除或更改。

### 替代方案

```java
// 旧代码
BlockPos spawnPos = world.getSpawnPos();

// 新代码 - 使用 getSpawnPoint()
WorldProperties.SpawnPoint spawnPoint = world.getSpawnPoint();
BlockPos spawnPos = spawnPoint.pos(); // 或 spawnPoint.getPos()，根据API确定

// 或者直接从 WorldProperties 获取
BlockPos spawnPos = world.getLevelProperties().getSpawnPoint();
```

### 涉及文件

| 文件 | 行号 | 修改内容 |
|------|------|----------|
| `WorldInfoFunction.java` | 94, 95 | `getSpawnPos()` 替换 |

---

## 八、MinecraftServer.isPvpEnabled() 方法移除

### 问题
`MinecraftServer.isPvpEnabled()` 方法已被移除。

### 替代方案

```java
// 旧代码
boolean isPvp = server.isPvpEnabled();

// 新代码 - 从 ServerWorld 的 GameRules 获取
boolean isPvp = server.getOverworld().getGameRules().getBoolean(GameRules.PVP);

// 或者
boolean isPvp = server.getGameRules().getBoolean(GameRules.PVP);
```

### 涉及文件

| 文件 | 行号 | 修改内容 |
|------|------|----------|
| `ServerInfoFunction.java` | 59 | `isPvpEnabled()` 替换 |

---

## 九、需要添加的导入

修改后可能需要添加以下导入：

```java
// 权限相关
import net.minecraft.command.permission.PermissionSource;
import net.minecraft.command.permission.PermissionPredicate;
import net.minecraft.command.permission.PermissionCheck;

// 或者使用 GameRules
import net.minecraft.world.GameRules;
```

---

## 十、完整修改清单

### 按文件分类

#### 1. HistoryCommand.java ✅
- [x] 第28行: `hasPermissionLevel(2)` → `CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK)`

#### 2. LogCommand.java ✅
- [x] 第23行: `hasPermissionLevel(2)` → `CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK)`

#### 3. LLMChatCommand.java ✅
- [x] 第988, 1924, 2205, 2243, 2273, 2414, 2441, 2495行: 权限检查修改
- [x] 第1342, 1358, 1372, 1437, 1597, 1648, 1763, 1776行: `getServer()` 替换为 `getEntityWorld().getServer()`

#### 4. FunctionRegistry.java ✅
- [x] 第186行: `getServer()` 替换
- [x] 第250行: `getWorld()` → `getEntityWorld()`
- [x] 第347, 348行: `getWorld()` → `getEntityWorld()`

#### 5. NearbyEntitiesFunction.java ✅
- [x] 第69行: `getPos()` 替换
- [x] 第75行: `getWorld()` → `getEntityWorld()`

#### 6. PlayerStatsFunction.java ✅
- [x] 第86行: `getWorld()` → `getEntityWorld()`

#### 7. ServerInfoFunction.java ✅
- [x] 第59行: `isPvpEnabled()` 替换（GameRules API 重构，移除 PvP 显示）
- [x] GameRules 包路径更新为 `net.minecraft.world.rule.GameRules`

#### 8. SetBlockFunction.java ✅
- [x] 第125行: `getWorld()` → `getEntityWorld()`

#### 9. SummonEntityFunction.java ✅
- [x] 第110行: `getPos()` 替换
- [x] 第131行: `getWorld()` → `getEntityWorld()`

#### 10. TeleportPlayerFunction.java ✅
- [x] 第116行: `getPos()` 替换
- [x] 第117, 143行: `getWorld()` → `getEntityWorld()`

#### 11. WorldInfoFunction.java ✅
- [x] 第48行: `getWorld()` → `getEntityWorld()`
- [x] 第94, 95行: `getSpawnPos()` 替换为 `getSpawnPoint().getPos()`

#### 12. ExecuteCommandFunction.java ✅
- [x] 第112-122行: `ServerCommandSource` 构造函数重构 → 使用 `withOutput()` 方法
- [x] 第140行: `executeWithPrefix` 替换为 `parseAndExecute()`

#### 13. TemplateEditor.java ✅ (新增修复)
- [x] player.getWorld() → EntityHelper.getWorld(player)

---

## 十一、验证步骤

完成修改后，请执行以下验证步骤：

1. **编译检查**
   ```bash
   ./gradlew compileJava
   ```

2. **运行测试**
   ```bash
   ./gradlew test
   ```

3. **游戏内测试**
   - 测试所有命令权限是否正常
   - 测试 Function Calling 功能
   - 测试世界信息获取
   - 测试玩家传送功能

---

## 十二、参考资源

- [Fabric 官方文档 - Porting to 1.21.11](https://docs.fabricmc.net/develop/porting/current)
- [Fabric 博客 - Minecraft 1.21.11](https://fabricmc.net/2025/12/05/12111.html)
- [Yarn 1.21.11 API 文档](https://maven.fabricmc.net/docs/yarn-1.21.11+build.4/)
- [NeoForge 迁移指南](https://github.com/ChampionAsh5357/neoforgedev-primer) (第三方参考)

---

## 十三、额外发现的问题和修复（2026-02-20 补充）

### 问题十：ServerCommandSource 构造函数权限参数类型不兼容

**问题描述：**
在 ExecuteCommandFunction.java 中，使用 `CommandManager.requirePermissionLevel(CommandManager.OWNERS_CHECK)` 作为构造函数参数会导致类型不兼容错误。

**错误信息：**
```
错误: 不兼容的类型: 不存在类型变量T的实例, 以使PermissionSourcePredicate<T>与PermissionPredicate一致
```

**修复方案：**
使用 `ServerCommandSource.withOutput()` 方法替代手动创建新的 ServerCommandSource：

```java
// 旧代码（不兼容）
ServerCommandSource captureSource = new ServerCommandSource(
    outputCapture,
    consoleSource.getPosition(),
    consoleSource.getRotation(),
    consoleSource.getWorld(),
    CommandManager.requirePermissionLevel(CommandManager.OWNERS_CHECK), // 类型不兼容
    consoleSource.getName(),
    consoleSource.getDisplayName(),
    consoleSource.getServer(),
    consoleSource.getEntity()
);

// 新代码（推荐）
ServerCommandSource captureSource = consoleSource.withOutput(outputCapture);
```

### 问题十一：GameRules 包路径变更

**问题描述：**
`net.minecraft.world.GameRules` 已在 1.21.11 中移动到 `net.minecraft.world.rule.GameRules`。

**修复方案：**
```java
// 旧导入
import net.minecraft.world.GameRules;

// 新导入
import net.minecraft.world.rule.GameRules;
```

### 问题十二：GameRules API 变更（已修复）

**问题描述：**
GameRules 在 1.21.11 中 API 发生了变化。`getBoolean(GameRules.PVP)` 方法已被移除，需要使用新的 `getValue(GameRule<T>)` 方法。

**影响：**
- ServerInfoFunction.java 中获取 PvP 状态的代码需要更新

**修复方案：**
使用新的 `getValue()` 方法替代旧的 `getBoolean()` 方法：

```java
// 旧代码（已弃用）
boolean isPvp = server.getOverworld().getGameRules().getBoolean(GameRules.PVP);

// 新代码（1.21.11 兼容）
boolean isPvp = server.getOverworld().getGameRules().getValue(GameRules.PVP);
```

**重要说明：**
- `GameRules.PVP` 静态字段仍然存在，类型为 `GameRule<Boolean>`
- 只是获取值的方法从 `getBoolean()` 改为 `getValue()`
- 这个变化适用于所有 GameRules 的布尔值和整数值获取

**涉及文件：**

| 文件 | 行号 | 修改内容 |
|------|------|----------|
| `ServerInfoFunction.java` | 60-62 | PvP 状态获取代码修复 |

### 问题十三：PlayerEntity.getWorld() 方法移除

**问题描述：**
`PlayerEntity.getWorld()` 方法已被移除，需要使用其他方式获取世界。

**修复方案：**
使用 EntityHelper 工具类：

```java
// 旧代码
World world = player.getWorld();

// 新代码
World world = EntityHelper.getWorld(player);
```

---

## 十四、注意事项

1. **备份代码**: 在进行修改前，请确保已备份当前代码

2. **渐进式修改**: 建议按文件逐步修改，每修改一个文件后进行编译测试

3. **API 稳定性**: 1.21.11 是较新版本，部分 API 可能在后续版本中再次变更

4. **测试覆盖**: 确保所有功能都有相应的测试用例

5. **Mixin 检查**: 如果项目中有 Mixin 类，需要检查是否也需要相应更新

---

*文档生成时间: 2026-02-20*
*目标版本: Fabric 1.21.11*
