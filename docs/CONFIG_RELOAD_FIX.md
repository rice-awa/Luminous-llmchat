# 配置重载修复

## 问题描述

用户报告修改配置文件中的`maxContextCharacters`值后，使用`/llmchat reload`命令重载配置没有效果，仍然使用原来的配置值，无法应用新的值。

## 问题分析

通过代码分析发现问题出现在`LLMChatConfig.reload()`方法中：

### 原有实现
```java
public void reload() {
    loadConfig();  // 只重新加载配置到内存
}
```

### 问题所在
1. `reload()`方法只调用了`loadConfig()`来重新读取配置文件
2. 但是没有通知`ChatContextManager`更新现有的上下文实例
3. 已存在的`ChatContext`实例仍然使用旧的`maxContextCharacters`值

### 对比正常设置
当通过`setMaxContextCharacters()`方法设置时：
```java
public void setMaxContextCharacters(int maxContextCharacters) {
    this.maxContextCharacters = maxContextCharacters;
    
    if (!isInitializing) {
        saveConfig();
        
        // 更新现有的上下文实例 ✅
        try {
            ChatContextManager.getInstance().updateMaxContextLength();
        } catch (Exception e) {
            System.err.println("Failed to update existing contexts: " + e.getMessage());
        }
    }
}
```

可以看到，`setMaxContextCharacters()`会调用`ChatContextManager.updateMaxContextLength()`来更新现有的上下文实例，但`reload()`方法缺少这一步。

## 解决方案

### 修复reload()方法
```java
public void reload() {
    loadConfig();
    
    // 重载配置后，更新现有的上下文实例
    if (!isInitializing) {
        try {
            com.riceawa.llm.context.ChatContextManager.getInstance().updateMaxContextLength();
        } catch (Exception e) {
            System.err.println("Failed to update existing contexts after reload: " + e.getMessage());
        }
    }
}
```

### 添加调试信息
为了更好地跟踪配置更新过程，添加了调试输出：

1. **配置加载时的调试信息**：
```java
// 在applyConfigData方法中
if (data.maxContextCharacters != null) {
    this.maxContextCharacters = data.maxContextCharacters;
    System.out.println("Loaded maxContextCharacters from config: " + this.maxContextCharacters);
}
```

2. **上下文更新时的调试信息**：
```java
// 在ChatContext.setMaxContextCharacters方法中
public void setMaxContextCharacters(int maxContextCharacters) {
    System.out.println("ChatContext[" + sessionId + "] updating maxContextCharacters from " + 
        this.maxContextCharacters + " to " + maxContextCharacters);
    this.maxContextCharacters = maxContextCharacters;
    invalidateCharacterCache();
    updateLastActivity();
}
```

## 修复流程

### 修复前的流程
```
用户修改配置文件 → /llmchat reload → loadConfig() → 配置内存更新 
                                                    ↓
                                            现有ChatContext实例未更新 ❌
```

### 修复后的流程
```
用户修改配置文件 → /llmchat reload → loadConfig() → 配置内存更新
                                                    ↓
                                    ChatContextManager.updateMaxContextLength()
                                                    ↓
                                            所有ChatContext实例更新 ✅
```

## 相关方法说明

### ChatContextManager.updateMaxContextLength()
```java
public void updateMaxContextLength() {
    LLMChatConfig config = LLMChatConfig.getInstance();
    int newMaxContextCharacters = config.getMaxContextCharacters();

    for (ChatContext context : contexts.values()) {
        context.setMaxContextCharacters(newMaxContextCharacters);
    }
    LogManager.getInstance().system("Updated max context characters to " + newMaxContextCharacters +
        " for " + contexts.size() + " active contexts");
}
```

这个方法会：
1. 获取最新的配置值
2. 遍历所有活跃的上下文实例
3. 更新每个实例的`maxContextCharacters`值
4. 记录更新日志

## 测试验证

创建了`TestConfigReload.java`测试文件来验证修复效果：

### 测试场景
1. **基本配置更新测试**：验证通过`setMaxContextCharacters()`设置值是否正确
2. **重载功能测试**：验证`reload()`后现有上下文是否更新
3. **多实例更新测试**：验证多个上下文实例是否都能正确更新

### 预期结果
- 配置重载后，所有现有的`ChatContext`实例都应该使用新的`maxContextCharacters`值
- 控制台应该显示相应的调试信息，确认更新过程

## 使用说明

### 修改配置文件
1. 编辑`config/lllmchat/config.json`文件
2. 修改`maxContextCharacters`的值
3. 保存文件

### 重载配置
在游戏中执行：
```
/llmchat reload
```

### 验证更新
重载后，你应该能看到类似的控制台输出：
```
Loaded maxContextCharacters from config: 80000
ChatContext[xxx-xxx-xxx] updating maxContextCharacters from 60000 to 80000
Updated max context characters to 80000 for 2 active contexts
```

## 注意事项

1. **权限要求**：`/llmchat reload`命令需要OP权限
2. **实时生效**：配置重载后立即对所有现有对话生效
3. **数据安全**：重载不会影响现有的对话历史，只更新限制值
4. **调试信息**：修复版本会在控制台输出更多调试信息，便于问题排查

## 向后兼容性

此修复完全向后兼容：
- 不改变任何现有的API接口
- 不影响配置文件格式
- 不改变用户的使用方式

修复后，配置重载功能将正常工作，用户修改配置文件后可以通过`/llmchat reload`命令立即应用新的配置值。
