# 🚀 快速同步指南

一键完成从main分支同步到multi-version分支并触发构建的完整流程。

## 💡 最简单的使用方法

### Windows用户
```powershell
# 进入项目目录
cd "e:\MCJava\moddev\Luminous-llmchat"

# 一键完成所有操作
.\scripts\auto-sync-build-clean.ps1
```

### Linux/Mac用户
```bash
# 进入项目目录
cd /path/to/Luminous-llmchat

# 一键完成所有操作
./scripts/auto-sync-and-build.sh
```

## 🔍 预览模式（推荐首次使用）

在实际执行前，先查看将要执行的操作：

```powershell
# Windows - 预览模式
.\scripts\auto-sync-build-clean.ps1 -DryRun
```

```bash
# Linux/Mac - 预览模式
./scripts/auto-sync-and-build.sh --dry-run
```

## ⚡ 快速执行（跳过确认）

如果您确定要执行所有操作：

```powershell
# Windows - 强制执行
.\scripts\auto-sync-build-clean.ps1 -Force
```

```bash
# Linux/Mac - 强制执行
./scripts/auto-sync-and-build.sh --force
```

## 🎯 脚本会自动完成

1. ✅ **检查Git状态** - 自动处理未提交的更改
2. ✅ **更新main分支** - 拉取最新代码
3. ✅ **创建/切换multi-version分支** - 确保分支存在
4. ✅ **合并main到multi-version** - 同步最新更改
5. ✅ **运行本地测试** - 验证构建正常（可跳过）
6. ✅ **推送并触发构建** - 启动GitHub Actions

## 📊 执行结果示例

```
Luminous LLM Chat Auto Sync and Build Script
==================================================
-> Step 1/5: Update main branch
i  Current branch: main
i  Switching to main branch...
i  Pulling latest changes from main branch...
v Main branch updated to latest version

-> Step 2/5: Check multi-version branch
v multi-version branch already exists

-> Step 3/5: Merge main branch to multi-version
i  Switching to multi-version branch...
i  Need to merge 2 commits
i  Merging main branch changes...
v Successfully merged main branch changes

-> Step 4/5: Run local test build
i  Running local test build (version: 1.21.7)...
v Local test build successful

-> Step 5/5: Push and trigger GitHub Actions build
i  Pushing multi-version branch to remote repository...
v Pushed multi-version branch, GitHub Actions will automatically start multi-version build
i  View build status: https://github.com/rice-awa/Luminous-llmchat/actions

==================================================
v Auto sync and build process completed!

i  Completed operations:
  v Updated main branch to latest version
  v Merged main branch to multi-version branch
  v Ran local test build
  v Pushed multi-version branch to trigger GitHub Actions

i  Next steps:
  • View GitHub Actions build status
  • Wait for all version builds to complete
  • Download build artifacts or create Release
==================================================
```

## 🛠️ 常用选项

| 选项 | Windows | Linux/Mac | 说明 |
|------|---------|-----------|------|
| 预览模式 | `-DryRun` | `--dry-run` | 查看将要执行的操作 |
| 强制执行 | `-Force` | `--force` | 跳过所有确认提示 |
| 跳过测试 | `-SkipTests` | `--skip-tests` | 跳过本地测试构建 |
| 详细输出 | `-Verbose` | `--verbose` | 显示详细信息 |
| 指定测试版本 | `-TestVersion 1.21.6` | `--test-version 1.21.6` | 使用特定版本测试 |

## ⚠️ 注意事项

1. **确保在项目根目录运行脚本**
2. **如果有未提交的更改，脚本会自动暂存**
3. **如果出现合并冲突，需要手动解决后重新运行**
4. **首次使用建议先用预览模式查看操作**

## 🆘 遇到问题？

### 合并冲突
```
x Merge conflicts occurred, please resolve conflicts manually and re-run script
i  Steps to resolve conflicts:
1. Edit conflict files, resolve conflict markers
2. git add .
3. git commit
4. Re-run this script
```

**解决方法：**
1. 手动编辑冲突文件
2. 删除Git冲突标记（`<<<<<<<`, `=======`, `>>>>>>>`）
3. 运行 `git add .` 和 `git commit`
4. 重新执行脚本

### 本地测试失败
```
x Local test build failed
Continue with push? (y/N):
```

**解决方法：**
- 输入 `N` 停止流程，修复问题后重新运行
- 输入 `y` 继续推送（如果确定问题不影响构建）
- 或者使用 `-SkipTests` 参数跳过测试

## 🎉 就是这么简单！

现在您只需要一个命令就能完成从开发到构建的完整流程：

```powershell
# Windows
.\scripts\auto-sync-build-clean.ps1

# Linux/Mac  
./scripts/auto-sync-and-build.sh
```

开发愉快！🎮
