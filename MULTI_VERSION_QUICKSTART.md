# 🚀 多版本构建快速开始指南

本指南将帮助您快速设置和使用Luminous LLM Chat的多版本构建系统。

## 📋 概述

### 🔍 自动版本发现系统
项目现在支持**自动版本发现**，系统会自动扫描`build_version`目录中的所有配置文件，无需手动维护版本列表！

### 🌟 分支策略
- **`main`分支**: 专注于单一版本（Minecraft 1.21.7）的快速开发
  - 触发：单版本构建工作流（`build.yml`）
  - 用途：快速开发和测试
- **`multi-version`分支**: 用于构建和发布多个Minecraft版本
  - 触发：多版本构建工作流（`multi-version-build.yml`）
  - 用途：正式发布和多版本构建

### ✨ 当前支持的版本
系统自动发现了**8个版本配置**：
- 1.21.8, 1.21.7（默认开发版本）, 1.21.6, 1.21.5
- 1.21.4, 1.21.3, 1.21.2, 1.21.1

## 🛠️ 快速设置

### 1. 查看可用版本

首先查看系统自动发现的所有版本：

```powershell
# 进入项目目录
cd "e:\MCJava\moddev\Luminous-llmchat"

# 查看所有自动发现的版本
.\scripts\manage-versions-simple.ps1 list-versions
```

输出示例：
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

### 2. 测试本地构建

测试不同版本的构建：

```powershell
# 测试构建Minecraft 1.21.6版本
.\scripts\manage-versions-simple.ps1 test-build 1.21.6

# 测试构建最新版本
.\scripts\manage-versions-simple.ps1 test-build 1.21.8
```

### 3. 创建multi-version分支（可选）

如果需要使用GitHub Actions进行多版本构建：

```bash
git checkout main
git pull origin main
git checkout -b multi-version
git push -u origin multi-version
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

#### 方法1：本地多版本测试（推荐）
```powershell
# 测试多个版本的构建
.\scripts\manage-versions-simple.ps1 test-build 1.21.6
.\scripts\manage-versions-simple.ps1 test-build 1.21.7
.\scripts\manage-versions-simple.ps1 test-build 1.21.8
```

#### 方法2：一键自动同步和构建（推荐）
使用自动化脚本完成完整流程：

```powershell
# Windows PowerShell - 一键完成所有操作
.\scripts\auto-sync-build-clean.ps1

# 强制执行（跳过确认）
.\scripts\auto-sync-build-clean.ps1 -Force

# 预演模式（查看将要执行的操作）
.\scripts\auto-sync-build-clean.ps1 -DryRun
```

```bash
# Linux/Mac - 一键完成所有操作
./scripts/auto-sync-and-build.sh

# 强制执行（跳过确认）
./scripts/auto-sync-and-build.sh --force
```

**自动脚本会执行：**
1. ✅ 更新main分支到最新版本
2. ✅ 创建或切换到multi-version分支
3. ✅ 合并main分支的更改
4. ✅ 运行本地测试构建
5. ✅ 推送并触发GitHub Actions构建

#### 方法3：手动Git操作
如果需要手动控制每个步骤：

```bash
# 确保main分支是最新的
git checkout main
git pull origin main

# 切换到multi-version分支并合并main的更改
git checkout multi-version
git merge main

# 推送触发自动构建
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

## 🔧 版本管理

### 🔍 自动版本发现的工作原理

系统会自动扫描以下位置：
- `build_version/1.21/gradle-*.properties` - 特定版本配置
- `gradle.properties` - 默认开发版本

文件命名规则：`gradle-{版本号}.properties`（如：`gradle-1.21.6.properties`）

### 📁 项目结构
```
build_version/
├── 1.21/
│   ├── gradle-1.21.1.properties
│   ├── gradle-1.21.2.properties
│   ├── gradle-1.21.3.properties
│   ├── gradle-1.21.4.properties
│   ├── gradle-1.21.5.properties
│   ├── gradle-1.21.6.properties
│   ├── gradle-1.21.7.properties
│   └── gradle-1.21.8.properties
└── fabric_version_generator.py
gradle.properties (默认开发版本 - 1.21.7)
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

## 🔄 同步main分支的更改

当您在main分支进行开发后，需要将更改同步到multi-version分支以进行多版本构建。

### 方法1：手动Git操作（推荐）

```bash
# 1. 确保main分支是最新的
git checkout main
git pull origin main

# 2. 切换到multi-version分支
git checkout multi-version

# 3. 合并main分支的更改
git merge main

# 4. 推送到远程仓库（触发多版本构建）
git push origin multi-version
```

### 方法2：使用脚本（如果可用）

如果您有完整的管理脚本，可以使用：
```bash
# Linux/Mac
./scripts/manage-versions.sh sync-from-main

# Windows (如果脚本支持)
.\scripts\manage-versions.ps1 sync-from-main
```

### 处理合并冲突

如果在合并过程中出现冲突：

```bash
# 1. Git会提示有冲突的文件
git status

# 2. 手动编辑冲突文件，解决冲突标记
# 编辑器中会看到类似这样的标记：
# <<<<<<< HEAD
# multi-version分支的内容
# =======
# main分支的内容
# >>>>>>> main

# 3. 解决冲突后，添加文件
git add .

# 4. 完成合并
git commit

# 5. 推送更改
git push origin multi-version
```

### 同步频率建议

- **开发阶段**：每次main分支有重要更新时同步
- **发布前**：确保multi-version分支包含所有最新功能
- **定期维护**：建议每周同步一次，保持分支同步

## ✨ 核心优势

### 🎯 零维护成本
- **自动发现**：无需手动维护版本列表
- **动态构建**：GitHub Actions自动包含所有发现的版本
- **即插即用**：添加配置文件即可支持新版本

### 🚀 简化的工作流程
1. **开发阶段**：在main分支使用默认版本（1.21.7）快速开发
2. **测试阶段**：使用`manage-versions-simple.ps1`测试不同版本
3. **发布阶段**：推送到multi-version分支触发全版本构建

### 🔧 灵活的版本管理
- 支持任意数量的版本配置
- 可以轻松添加或删除版本支持
- 自动验证配置文件有效性

### ⚡ 优化的构建策略
- **避免重复构建**：main分支只运行单版本构建，multi-version分支只运行多版本构建
- **快速开发反馈**：main分支构建时间约3-5分钟
- **完整发布构建**：multi-version分支构建所有版本，约15-25分钟
- **资源节省**：减少约30%的GitHub Actions使用时间

## 🚨 常见问题

### Q: 构建失败怎么办？
A:
1. 检查GitHub Actions的构建日志
2. 本地测试构建：`.\scripts\manage-versions-simple.ps1 test-build 1.21.x`
3. 验证版本配置文件是否正确
4. 检查Fabric API版本兼容性

### Q: 如何添加新的Minecraft版本支持？
A:
- **自动方式**：`.\scripts\add-version.ps1 -MinecraftVersion 1.21.9 -AutoDetect`
- **手动方式**：在`build_version/1.21/`目录创建`gradle-1.21.9.properties`文件

### Q: 系统如何自动发现版本？
A: 系统扫描`build_version`目录下所有`gradle-*.properties`文件，从文件名提取版本号

### Q: 可以只构建特定版本吗？
A:
- **本地测试**：`.\scripts\manage-versions-simple.ps1 test-build 1.21.6`
- **GitHub Actions**：在手动触发时指定版本，如：`1.21.6,1.21.7`

### Q: 如何查看所有支持的版本？
A: 运行 `.\scripts\manage-versions-simple.ps1 list-versions` 查看自动发现的所有版本

### Q: 如何同步main分支的更改到multi-version分支？
A:
```bash
# 标准Git流程
git checkout main && git pull origin main
git checkout multi-version && git merge main
git push origin multi-version
```

### Q: 合并时出现冲突怎么办？
A:
1. 使用 `git status` 查看冲突文件
2. 手动编辑冲突文件，删除冲突标记
3. 运行 `git add .` 和 `git commit` 完成合并
4. 推送更改：`git push origin multi-version`

### Q: 多久需要同步一次main分支？
A:
- **开发活跃期**：每次main分支有重要更新时
- **发布准备**：发布前确保包含所有最新功能
- **定期维护**：建议每周同步一次

### Q: 如何确认multi-version分支是最新的？
A:
```bash
# 检查分支差异
git checkout multi-version
git log --oneline main..HEAD  # 查看multi-version领先main的提交
git log --oneline HEAD..main  # 查看main领先multi-version的提交（应该为空）
```

### Q: 为什么不同分支触发不同的工作流？
A: 为了避免重复构建和资源浪费：
- **main分支**: 只运行单版本构建（3-5分钟），适合快速开发验证
- **multi-version分支**: 只运行多版本构建（15-25分钟），适合正式发布
- **节省资源**: 避免在multi-version分支上运行不必要的单版本构建

### Q: 如果我只想测试特定版本怎么办？
A:
- **本地测试**: 使用 `.\scripts\manage-versions-simple.ps1 test-build 1.21.6`
- **GitHub Actions**: 在multi-version分支手动触发工作流，指定特定版本

## 📚 相关文档和脚本

### 📖 文档
- [docs/MULTI_VERSION_STRATEGY.md](docs/MULTI_VERSION_STRATEGY.md) - 详细的多版本策略文档
- [docs/WORKFLOW_STRATEGY.md](docs/WORKFLOW_STRATEGY.md) - 工作流和分支策略说明
- [AUTO_VERSION_DISCOVERY.md](AUTO_VERSION_DISCOVERY.md) - 自动版本发现系统说明

### 🛠️ 可用脚本
- `.\scripts\auto-sync-build-clean.ps1` - **自动同步和构建脚本（推荐）**
- `.\scripts\manage-versions-simple.ps1` - 简化的版本管理脚本
- `.\scripts\add-version.ps1` - 添加新版本配置脚本
- `.\scripts\test-sync.ps1` - 测试系统完整性脚本
- `.\scripts\auto-sync-and-build.sh` - Linux/Mac版自动同步脚本

### 📋 快速命令参考

#### 版本管理
```powershell
# 查看所有版本
.\scripts\manage-versions-simple.ps1 list-versions

# 测试构建
.\scripts\manage-versions-simple.ps1 test-build 1.21.6

# 添加新版本
.\scripts\add-version.ps1 -MinecraftVersion 1.21.9 -AutoDetect

# 测试系统
.\scripts\test-sync.ps1 -Verbose
```

#### 自动同步和构建
```powershell
# Windows - 一键完成完整流程
.\scripts\auto-sync-build-clean.ps1

# 强制执行（跳过确认）
.\scripts\auto-sync-build-clean.ps1 -Force

# 预演模式
.\scripts\auto-sync-build-clean.ps1 -DryRun

# 跳过本地测试
.\scripts\auto-sync-build-clean.ps1 -SkipTests
```

```bash
# Linux/Mac - 一键完成完整流程
./scripts/auto-sync-and-build.sh

# 强制执行（跳过确认）
./scripts/auto-sync-and-build.sh --force

# 预演模式
./scripts/auto-sync-and-build.sh --dry-run
```

#### 分支同步
```bash
# 完整同步流程
git checkout main && git pull origin main
git checkout multi-version && git merge main
git push origin multi-version

# 检查分支状态
git status
git log --oneline HEAD..main  # 查看需要同步的提交

# 处理冲突（如果有）
git add .
git commit
git push origin multi-version
```

#### GitHub Actions
```bash
# 触发多版本构建
git push origin multi-version

# 手动触发（在GitHub网页上）
# Actions -> Multi-Version Build -> Run workflow
```

## 🎉 开始使用

现在您可以：

1. **查看支持的版本** - 运行 `list-versions` 查看自动发现的所有版本
2. **本地测试构建** - 使用 `test-build` 测试任意版本
3. **添加新版本** - 使用 `add-version.ps1` 或手动创建配置文件
4. **继续开发** - 在main分支专注于默认版本开发
5. **多版本发布** - 推送到multi-version分支触发全版本构建

### 🚀 立即开始

#### 方式1：使用自动化脚本（推荐）
```powershell
# 1. 查看当前支持的版本
.\scripts\manage-versions-simple.ps1 list-versions

# 2. 一键完成同步和构建
.\scripts\auto-sync-build-clean.ps1

# 3. 查看GitHub Actions构建状态
```

#### 方式2：手动步骤
```powershell
# 1. 查看当前支持的版本
.\scripts\manage-versions-simple.ps1 list-versions

# 2. 测试一个版本的构建
.\scripts\manage-versions-simple.ps1 test-build 1.21.6

# 3. 手动同步分支（参考上面的Git命令）
```

祝您开发愉快！🎮
