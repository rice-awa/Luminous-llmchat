# 🤖 自动同步和构建脚本使用指南

本指南介绍如何使用自动化脚本来完成从main分支同步到multi-version分支并触发构建的完整流程。

## 📋 脚本概述

### 🎯 功能
自动化脚本会按顺序执行以下操作：
1. **检查Git状态** - 处理未提交的更改
2. **更新main分支** - 拉取最新代码
3. **确保multi-version分支存在** - 如不存在则创建
4. **合并main到multi-version** - 同步最新更改
5. **运行本地测试** - 验证构建正常
6. **推送并触发构建** - 启动GitHub Actions

### 📁 脚本文件
- `scripts/auto-sync-build-clean.ps1` - Windows PowerShell版本（推荐）
- `scripts/auto-sync-and-build.sh` - Linux/Mac Bash版本

## 🚀 快速开始

### Windows (PowerShell)
```powershell
# 基本使用
.\scripts\auto-sync-build-clean.ps1

# 强制执行（跳过确认）
.\scripts\auto-sync-build-clean.ps1 -Force

# 预演模式（查看将要执行的操作）
.\scripts\auto-sync-build-clean.ps1 -DryRun
```

### Linux/Mac (Bash)
```bash
# 基本使用
./scripts/auto-sync-and-build.sh

# 强制执行（跳过确认）
./scripts/auto-sync-and-build.sh --force

# 预演模式（查看将要执行的操作）
./scripts/auto-sync-and-build.sh --dry-run
```

## 🔧 参数选项

### PowerShell版本参数
| 参数 | 说明 | 示例 |
|------|------|------|
| `-Force` | 强制执行，跳过所有确认提示 | `-Force` |
| `-DryRun` | 预演模式，显示操作但不执行 | `-DryRun` |
| `-SkipTests` | 跳过本地测试构建 | `-SkipTests` |
| `-Verbose` | 显示详细输出信息 | `-Verbose` |
| `-TestVersion` | 指定测试版本 | `-TestVersion 1.21.6` |

### Bash版本参数
| 参数 | 说明 | 示例 |
|------|------|------|
| `-f, --force` | 强制执行，跳过所有确认提示 | `--force` |
| `-d, --dry-run` | 预演模式，显示操作但不执行 | `--dry-run` |
| `-s, --skip-tests` | 跳过本地测试构建 | `--skip-tests` |
| `-v, --verbose` | 显示详细输出信息 | `--verbose` |
| `-t, --test-version` | 指定测试版本 | `--test-version 1.21.6` |
| `-h, --help` | 显示帮助信息 | `--help` |

## 📖 使用场景

### 场景1：日常开发后的发布
```powershell
# 开发完成后，一键同步并构建
.\scripts\auto-sync-and-build.ps1
```

### 场景2：快速发布（跳过测试）
```powershell
# 紧急发布，跳过本地测试
.\scripts\auto-sync-and-build.ps1 -SkipTests -Force
```

### 场景3：检查将要执行的操作
```powershell
# 预演模式，查看将要执行什么
.\scripts\auto-sync-and-build.ps1 -DryRun -Verbose
```

### 场景4：使用特定版本测试
```powershell
# 使用1.21.6版本进行本地测试
.\scripts\auto-sync-and-build.ps1 -TestVersion 1.21.6 -Verbose
```

## 🔍 执行流程详解

### 步骤1：检查Git状态
```
🔄 检查工作目录状态
├── 如果有未提交更改
│   ├── 显示更改列表
│   ├── 询问是否继续（除非使用-Force）
│   └── 自动暂存更改
└── 继续下一步
```

### 步骤2：更新main分支
```
🔄 步骤 1/5: 更新main分支
├── 保存当前分支信息
├── 切换到main分支
├── 拉取最新更改 (git pull origin main)
├── 显示最新提交（如果使用-Verbose）
└── ✅ main分支已更新到最新版本
```

### 步骤3：确保multi-version分支存在
```
🔄 步骤 2/5: 检查multi-version分支
├── 检查分支是否存在
├── 如果不存在
│   ├── 询问是否创建（除非使用-Force）
│   ├── 创建分支 (git checkout -b multi-version)
│   └── 推送到远程 (git push -u origin multi-version)
└── ✅ multi-version分支已准备就绪
```

### 步骤4：合并main到multi-version
```
🔄 步骤 3/5: 合并main分支到multi-version
├── 切换到multi-version分支
├── 检查是否需要合并
├── 执行合并 (git merge main --no-edit)
├── 如果有冲突
│   ├── 显示冲突解决指导
│   └── 退出等待手动解决
└── ✅ 已成功合并main分支的更改
```

### 步骤5：运行本地测试
```
🔄 步骤 4/5: 运行本地测试构建
├── 检查是否跳过测试（-SkipTests）
├── 查找测试脚本
├── 运行测试构建
├── 如果测试失败
│   ├── 询问是否继续推送
│   └── 根据选择继续或退出
└── ✅ 本地测试构建成功
```

### 步骤6：推送并触发构建
```
🔄 步骤 5/5: 推送并触发GitHub Actions构建
├── 推送multi-version分支 (git push origin multi-version)
├── 显示GitHub Actions链接
└── ✅ 已推送，GitHub Actions将自动开始多版本构建
```

## 🎯 输出示例

### 成功执行的输出
```
🚀 Luminous LLM Chat 自动同步和构建脚本
==================================================
🔄 步骤 1/5: 更新main分支
ℹ️  当前分支: main
ℹ️  切换到main分支...
ℹ️  拉取main分支的最新更改...
✅ main分支已更新到最新版本

🔄 步骤 2/5: 检查multi-version分支
✅ multi-version分支已存在

🔄 步骤 3/5: 合并main分支到multi-version
ℹ️  切换到multi-version分支...
ℹ️  需要合并 3 个提交
ℹ️  合并main分支的更改...
✅ 已成功合并main分支的更改

🔄 步骤 4/5: 运行本地测试构建
ℹ️  运行本地测试构建（版本: 1.21.7）...
✅ 本地测试构建成功

🔄 步骤 5/5: 推送并触发GitHub Actions构建
ℹ️  推送multi-version分支到远程仓库...
✅ 已推送multi-version分支，GitHub Actions将自动开始多版本构建
ℹ️  查看构建状态: https://github.com/rice-awa/Luminous-llmchat/actions

==================================================
✅ 🎉 自动同步和构建流程完成！

ℹ️  已完成的操作：
  ✅ 更新了main分支到最新版本
  ✅ 合并main分支到multi-version分支
  ✅ 运行了本地测试构建
  ✅ 推送multi-version分支触发GitHub Actions

ℹ️  接下来：
  • 查看GitHub Actions构建状态
  • 等待所有版本构建完成
  • 下载构建产物或创建Release
==================================================
```

## ⚠️ 错误处理

### 常见错误和解决方案

#### 1. 合并冲突
```
❌ 合并过程中出现冲突，请手动解决冲突后重新运行脚本
ℹ️  解决冲突的步骤：
1. 编辑冲突文件，解决冲突标记
2. git add .
3. git commit
4. 重新运行此脚本
```

**解决方案：**
1. 手动编辑冲突文件
2. 删除Git冲突标记（`<<<<<<<`, `=======`, `>>>>>>>`）
3. 保留需要的代码
4. 运行 `git add .` 和 `git commit`
5. 重新执行脚本

#### 2. 本地测试失败
```
❌ 本地测试构建失败
是否继续推送？(y/N):
```

**解决方案：**
- 输入 `N` 停止流程，修复问题后重新运行
- 输入 `y` 继续推送（如果确定问题不影响构建）

#### 3. 推送失败
```
❌ 推送multi-version分支失败
```

**解决方案：**
1. 检查网络连接
2. 确认有推送权限
3. 手动运行 `git push origin multi-version`

## 🔧 自定义配置

### 修改默认测试版本
编辑脚本文件，修改默认值：
```powershell
# PowerShell版本
[string]$TestVersion = "1.21.8"  # 改为你想要的版本
```

```bash
# Bash版本
TEST_VERSION="1.21.8"  # 改为你想要的版本
```

### 添加自定义检查
在脚本中添加自定义验证逻辑：
```powershell
# 在Test-LocalBuild函数中添加
if (Test-Path "custom-check.ps1") {
    & .\custom-check.ps1
}
```

## 📚 相关文档

- [MULTI_VERSION_QUICKSTART.md](../MULTI_VERSION_QUICKSTART.md) - 多版本构建快速指南
- [BRANCH_SYNC_GUIDE.md](BRANCH_SYNC_GUIDE.md) - 手动分支同步指南
- [AUTO_VERSION_DISCOVERY.md](../AUTO_VERSION_DISCOVERY.md) - 自动版本发现系统

## 🎉 总结

自动同步和构建脚本让您可以：

✅ **一键完成**完整的同步和构建流程  
✅ **自动处理**常见的Git操作  
✅ **智能检测**并处理各种状态  
✅ **提供反馈**详细的执行信息  
✅ **支持预演**查看将要执行的操作  
✅ **灵活配置**各种执行选项  

使用这个脚本，您可以专注于开发，而不用担心复杂的分支同步和构建流程！🚀
