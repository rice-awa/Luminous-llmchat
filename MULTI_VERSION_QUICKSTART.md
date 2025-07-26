# 🚀 多版本构建快速开始指南

本指南将帮助您快速设置和使用Luminous LLM Chat的多版本构建系统。

## 📋 概述

我们已经为您的项目设置了一个双分支策略：

- **`main`分支**: 专注于单一版本（Minecraft 1.21.7）的快速开发
- **`multi-version`分支**: 用于构建和发布多个Minecraft版本

## 🛠️ 快速设置

### 1. 创建multi-version分支

在PowerShell中运行：

```powershell
# 进入项目目录
cd "e:\MCJava\moddev\Luminous-llmchat"

# 创建multi-version分支
.\scripts\manage-versions.ps1 create-branch

# 推送到远程仓库
.\scripts\manage-versions.ps1 push-branch
```

或者使用Git命令：

```bash
git checkout main
git pull origin main
git checkout -b multi-version
git push -u origin multi-version
```

### 2. 测试本地构建

测试不同版本的构建：

```powershell
# 列出所有支持的版本
.\scripts\manage-versions.ps1 list-versions

# 测试构建Minecraft 1.21.6版本
.\scripts\manage-versions.ps1 test-build 1.21.6
```

## 🎯 使用场景

### 场景1：日常开发
在`main`分支进行开发，使用现有的构建流程：

```bash
git checkout main
# 进行代码修改
git add .
git commit -m "添加新功能"
git push origin main
```

这将触发单版本构建（Minecraft 1.21.7）。

### 场景2：多版本发布

#### 方法1：一键发布（推荐）
```powershell
# 完整的发布流程：自动同步main分支 + 推送 + 触发构建
.\scripts\manage-versions.ps1 release
```

#### 方法2：分步操作
```powershell
# 同步main分支的更改
.\scripts\manage-versions.ps1 sync-from-main

# 推送触发多版本构建（会自动再次同步main分支）
.\scripts\manage-versions.ps1 push-branch
```

### 场景3：手动触发多版本构建
1. 访问GitHub仓库的Actions页面
2. 选择"Multi-Version Build"工作流
3. 点击"Run workflow"
4. 选择要构建的版本：
   - 输入`all`构建所有版本
   - 输入`1.21.5,1.21.6`构建特定版本

## 📦 构建产物

### 单版本构建（main分支）
- `mod-jars`: 主要的mod文件
- `all-artifacts`: 包含源码的完整构件
- `test-reports`: 测试报告

### 多版本构建（multi-version分支）
- `mod-{version}`: 每个版本的mod文件（如`mod-1.21.6`）
- `sources-{version}`: 每个版本的源码文件
- `test-reports-{version}`: 每个版本的测试报告

## 🔧 配置说明

### 🔍 自动版本发现
系统现在会自动扫描`build_version`目录中的所有配置文件，无需手动维护版本列表！

查看当前支持的版本：
```powershell
.\scripts\manage-versions-simple.ps1 list-versions
```

示例输出：
```
Auto-scanning available Minecraft versions:
  * 1.21.8 (build_version\1.21\gradle-1.21.8.properties)
  * 1.21.7 (gradle.properties - default dev version)
  * 1.21.6 (build_version\1.21\gradle-1.21.6.properties)
  * 1.21.5 (build_version\1.21\gradle-1.21.5.properties)
  * 1.21.4 (build_version\1.21\gradle-1.21.4.properties)
  * 1.21.3 (build_version\1.21\gradle-1.21.3.properties)
  * 1.21.2 (build_version\1.21\gradle-1.21.2.properties)
  * 1.21.1 (build_version\1.21\gradle-1.21.1.properties)
Total discovered: 8 version configurations
```

### ➕ 添加新版本

#### 方法1：自动检测（推荐）
```powershell
# 自动检测并创建Minecraft 1.21.9的配置
.\scripts\add-version.ps1 -MinecraftVersion 1.21.9 -AutoDetect
```

#### 方法2：手动指定
```powershell
# 手动指定版本信息
.\scripts\add-version.ps1 -MinecraftVersion 1.21.9 `
  -YarnMappings "1.21.9+build.1" `
  -FabricVersion "0.130.0+1.21.9" `
  -LoaderVersion "0.16.14"
```

#### 方法3：手动创建
1. 在`build_version/1.21/`目录创建新的配置文件，命名为`gradle-{版本}.properties`
2. 复制`gradle.properties`的内容并修改版本信息

添加新版本后，系统会自动识别并包含在构建中，无需修改任何其他文件！

## ✨ 自动同步功能

管理脚本现在具有智能同步功能：

- **创建分支时**：自动从最新的main分支创建multi-version分支
- **推送分支时**：自动同步main分支的最新更改后再推送
- **发布流程**：一键完成同步、推送和触发构建的完整流程

这确保了multi-version分支始终包含main分支的最新更改！

## 🚨 常见问题

### Q: 构建失败怎么办？
A:
1. 检查GitHub Actions的构建日志
2. 本地测试构建：`.\scripts\manage-versions.ps1 test-build 1.21.x`
3. 验证版本配置文件是否正确

### Q: 如何同步main分支的更改？
A:
- **自动同步**：使用 `push-branch` 或 `release` 命令会自动同步
- **手动同步**：使用 `.\scripts\manage-versions.ps1 sync-from-main`

### Q: 可以只构建特定版本吗？
A: 可以！在GitHub Actions中手动触发时，在版本输入框中指定版本，如：`1.21.6,1.21.7`

### Q: 如果合并时出现冲突怎么办？
A: 脚本会提示您手动解决冲突，解决后运行：`git add . && git commit`

## 📚 更多信息

- 详细文档：[docs/MULTI_VERSION_STRATEGY.md](docs/MULTI_VERSION_STRATEGY.md)
- 管理脚本帮助：`.\scripts\manage-versions.ps1 help`

## 🎉 开始使用

现在您可以：

1. **继续在main分支开发** - 专注于Minecraft 1.21.7版本
2. **需要发布时切换到multi-version分支** - 构建所有支持的版本
3. **使用管理脚本** - 简化版本管理操作

祝您开发愉快！🎮
