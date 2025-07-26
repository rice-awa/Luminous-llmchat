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
当准备发布时，切换到`multi-version`分支：

```bash
# 同步main分支的更改
.\scripts\manage-versions.ps1 sync-from-main

# 推送触发多版本构建
git push origin multi-version
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

### 支持的版本
当前配置支持以下Minecraft版本：

| 版本 | 配置文件 | Fabric API |
|------|----------|------------|
| 1.21.5 | `build_version/1.21/gradle-1.21.5.properties` | 0.128.1+1.21.5 |
| 1.21.6 | `build_version/1.21/gradle-1.21.6.properties` | 0.128.2+1.21.6 |
| 1.21.7 | `gradle.properties` | 0.129.0+1.21.7 |
| 1.21.8 | `build_version/1.21/gradle-1.21.8.properties` | 0.129.0+1.21.8 |

### 添加新版本
1. 在`build_version/1.21/`目录创建新的配置文件
2. 更新`.github/workflows/multi-version-build.yml`中的版本矩阵
3. 更新管理脚本中的版本列表

## 🚨 常见问题

### Q: 构建失败怎么办？
A: 
1. 检查GitHub Actions的构建日志
2. 本地测试构建：`.\scripts\manage-versions.ps1 test-build 1.21.x`
3. 验证版本配置文件是否正确

### Q: 如何同步main分支的更改？
A: 使用管理脚本：`.\scripts\manage-versions.ps1 sync-from-main`

### Q: 可以只构建特定版本吗？
A: 可以！在GitHub Actions中手动触发时，在版本输入框中指定版本，如：`1.21.6,1.21.7`

## 📚 更多信息

- 详细文档：[docs/MULTI_VERSION_STRATEGY.md](docs/MULTI_VERSION_STRATEGY.md)
- 管理脚本帮助：`.\scripts\manage-versions.ps1 help`

## 🎉 开始使用

现在您可以：

1. **继续在main分支开发** - 专注于Minecraft 1.21.7版本
2. **需要发布时切换到multi-version分支** - 构建所有支持的版本
3. **使用管理脚本** - 简化版本管理操作

祝您开发愉快！🎮
