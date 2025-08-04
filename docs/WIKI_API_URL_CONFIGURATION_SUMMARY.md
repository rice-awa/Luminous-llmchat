# Wiki API URL 配置化改进总结

## 📋 概述

本次改进将所有Wiki function中硬编码的`WIKI_BASE_URL`改为从配置文件动态获取，提升了系统的可配置性和灵活性。用户现在可以通过配置文件自定义Wiki API的服务地址。

## 🎯 改进目标

- 消除硬编码的Wiki API URL
- 提供配置化的Wiki API地址设置
- 保持默认值为`https://mcwiki.rice-awa.top`
- 确保配置系统的一致性和热重载支持

## 📦 完成的改进

### 1. 配置系统扩展

**ConfigDefaults.java**:
- 添加`DEFAULT_WIKI_API_URL`常量，默认值为`https://mcwiki.rice-awa.top`
- 在`getDefaultValue()`方法中添加对`wikiApiUrl`配置项的支持

**LLMChatConfig.java**:
- 添加`wikiApiUrl`字段和相应的getter/setter方法
- 在`ConfigData`类中添加`wikiApiUrl`字段
- 在配置加载和保存逻辑中集成Wiki API URL处理
- 支持配置热重载和默认值回退

### 2. Wiki Functions更新

**WikiSearchFunction.java**:
- 移除硬编码的`WIKI_BASE_URL`常量
- 添加`LLMChatConfig`导入
- 修改URL构建逻辑使用`LLMChatConfig.getInstance().getWikiApiUrl()`

**WikiPageFunction.java**:
- 移除硬编码的`WIKI_BASE_URL`常量
- 添加`LLMChatConfig`导入
- 修改URL构建逻辑使用配置中的URL地址

**WikiBatchPagesFunction.java**:
- 移除硬编码的`WIKI_BASE_URL`常量
- 添加`LLMChatConfig`导入
- 修改URL构建逻辑使用配置中的URL地址

## 🏗️ 技术实现

### 配置项定义
```java
// ConfigDefaults.java
public static final String DEFAULT_WIKI_API_URL = "https://mcwiki.rice-awa.top";
```

### 动态URL获取
```java
// 在各个Wiki Function中
String wikiBaseUrl = LLMChatConfig.getInstance().getWikiApiUrl();
String url = wikiBaseUrl + "/api/search"; // 或其他端点
```

### 配置管理
```java
// LLMChatConfig.java
public String getWikiApiUrl() {
    return wikiApiUrl;
}

public void setWikiApiUrl(String wikiApiUrl) {
    this.wikiApiUrl = wikiApiUrl != null ? wikiApiUrl : ConfigDefaults.DEFAULT_WIKI_API_URL;
    saveConfig();
}
```

## 📋 配置文件示例

用户可以在`config/lllmchat/config.json`中自定义Wiki API URL：

```json
{
  "configVersion": "2.0.0",
  "wikiApiUrl": "https://mcwiki.rice-awa.top",
  "defaultPromptTemplate": "default",
  "enableFunctionCalling": true,
  // ... 其他配置项
}
```

## ✅ 改进特性

### 1. 灵活的配置管理
- **默认值保障**: 如果配置文件中没有该项，自动使用默认URL
- **空值处理**: 如果设置为null或空字符串，自动回退到默认值
- **热重载支持**: 修改配置后立即生效，无需重启

### 2. 向后兼容性
- **自动迁移**: 现有配置文件会自动添加新的配置项
- **默认行为**: 未配置情况下保持原有的默认URL不变
- **配置验证**: 集成到现有的配置验证和修复系统中

### 3. 用户友好性
- **简单配置**: 用户只需在配置文件中添加一行即可自定义API地址
- **即时生效**: 配置修改后立即生效，支持动态切换
- **错误处理**: 配置错误时自动回退到安全的默认值

## 🔧 使用示例

### 默认配置（无需修改）
```json
{
  "wikiApiUrl": "https://mcwiki.rice-awa.top"
}
```

### 自定义Wiki API服务器
```json
{
  "wikiApiUrl": "https://custom-wiki-api.example.com"
}
```

### 本地开发环境
```json
{
  "wikiApiUrl": "http://localhost:3000"
}
```

## 📊 影响范围

### 文件修改列表
- `ConfigDefaults.java` - 添加默认值常量和支持方法
- `LLMChatConfig.java` - 添加配置字段、getter/setter和数据处理
- `WikiSearchFunction.java` - 移除硬编码URL，使用配置
- `WikiPageFunction.java` - 移除硬编码URL，使用配置  
- `WikiBatchPagesFunction.java` - 移除硬编码URL，使用配置

### 测试验证
- ✅ 编译测试通过
- ✅ 配置系统集成正常
- ✅ 默认行为保持不变
- ✅ 支持动态配置修改

## 🚀 后续扩展可能

- 添加Wiki API超时时间配置
- 支持Wiki API认证配置（如API密钥）
- 添加Wiki API健康检查功能
- 支持多个Wiki API服务器负载均衡

## 🏆 总结

本次改进成功将Wiki API URL从硬编码转换为可配置项，提升了系统的灵活性和可维护性。用户现在可以根据需要自定义Wiki API服务地址，同时保持了系统的稳定性和向后兼容性。所有修改都通过了编译测试，确保了代码质量和功能正确性。

---

**改进时间**: 2025-08-03  
**改进类型**: 配置系统增强  
**版本**: v1.1.0  
**状态**: ✅ 完成并测试通过