# 🔄 分支同步指南

本指南详细说明如何在Luminous LLM Chat项目中同步main分支的更改到multi-version分支。

## 📋 概述

### 分支结构
- **main分支**: 主要开发分支，专注于默认版本（Minecraft 1.21.7）
- **multi-version分支**: 多版本构建分支，包含所有版本的构建配置

### 同步的重要性
- 确保multi-version分支包含最新的功能和修复
- 保持多版本构建的代码一致性
- 避免版本间的功能差异

## 🚀 标准同步流程

### 步骤1：准备工作
```bash
# 检查当前状态
git status

# 如果有未提交的更改，先提交或暂存
git add .
git commit -m "保存当前工作"

# 或者暂存更改
git stash
```

### 步骤2：更新main分支
```bash
# 切换到main分支
git checkout main

# 拉取最新更改
git pull origin main

# 查看最新的提交
git log --oneline -5
```

### 步骤3：同步到multi-version分支
```bash
# 切换到multi-version分支
git checkout multi-version

# 合并main分支的更改
git merge main

# 推送到远程仓库
git push origin multi-version
```

### 步骤4：验证同步
```bash
# 检查是否同步成功
git log --oneline -5

# 确认没有待同步的提交
git log --oneline HEAD..main
# 如果输出为空，说明已完全同步
```

## ⚠️ 处理合并冲突

### 识别冲突
```bash
# 合并时如果有冲突，Git会显示：
# Auto-merging file.txt
# CONFLICT (content): Merge conflict in file.txt
# Automatic merge failed; fix conflicts and then commit the result.

# 查看冲突文件
git status
```

### 解决冲突
```bash
# 1. 编辑冲突文件
# 文件中会包含冲突标记：
# <<<<<<< HEAD
# multi-version分支的内容
# =======
# main分支的内容
# >>>>>>> main

# 2. 手动编辑，保留需要的内容，删除冲突标记

# 3. 标记冲突已解决
git add <冲突文件名>

# 4. 完成合并
git commit

# 5. 推送更改
git push origin multi-version
```

### 常见冲突类型

#### 1. 版本配置冲突
```bash
# 通常发生在gradle.properties文件
# 解决方案：保留main分支的版本配置
```

#### 2. 代码功能冲突
```bash
# 发生在同一文件的同一位置有不同修改
# 解决方案：合并两个分支的功能，确保兼容性
```

#### 3. 依赖版本冲突
```bash
# 发生在build.gradle或其他依赖文件
# 解决方案：使用main分支的依赖版本
```

## 🛠️ 高级同步技巧

### 使用rebase进行清洁合并
```bash
# 如果希望保持线性历史
git checkout multi-version
git rebase main

# 如果有冲突，解决后继续
git add .
git rebase --continue

# 强制推送（注意：只在确定的情况下使用）
git push origin multi-version --force-with-lease
```

### 选择性同步特定提交
```bash
# 只同步特定的提交
git checkout multi-version
git cherry-pick <commit-hash>
git push origin multi-version
```

### 批量同步多个提交
```bash
# 同步从某个提交到main的所有提交
git checkout multi-version
git cherry-pick <start-commit>..<end-commit>
git push origin multi-version
```

## 📊 同步状态检查

### 检查分支差异
```bash
# 查看main分支领先multi-version的提交
git log --oneline multi-version..main

# 查看multi-version分支领先main的提交
git log --oneline main..multi-version

# 图形化查看分支关系
git log --graph --oneline --all
```

### 检查文件差异
```bash
# 比较两个分支的特定文件
git diff main..multi-version -- gradle.properties

# 查看所有不同的文件
git diff --name-only main..multi-version
```

## 🔧 自动化同步

### 使用Git Hooks
创建`.git/hooks/post-merge`脚本：
```bash
#!/bin/bash
# 在main分支合并后自动同步到multi-version

current_branch=$(git branch --show-current)
if [ "$current_branch" = "main" ]; then
    echo "检测到main分支更新，是否同步到multi-version分支？(y/N)"
    read -r response
    if [ "$response" = "y" ] || [ "$response" = "Y" ]; then
        git checkout multi-version
        git merge main
        git push origin multi-version
        git checkout main
        echo "同步完成！"
    fi
fi
```

### 使用别名简化命令
```bash
# 添加Git别名
git config alias.sync-mv '!f() { 
    git checkout main && 
    git pull origin main && 
    git checkout multi-version && 
    git merge main && 
    git push origin multi-version; 
}; f'

# 使用别名
git sync-mv
```

## 📅 同步计划建议

### 开发阶段
- **每日同步**: 如果main分支更新频繁
- **功能完成后**: 每个功能开发完成后同步
- **修复bug后**: 重要bug修复后立即同步

### 发布阶段
- **发布前**: 确保multi-version分支包含所有最新功能
- **版本标签**: 为重要版本创建Git标签
- **发布后**: 同步任何发布后的热修复

### 维护阶段
- **每周同步**: 定期维护，保持分支同步
- **月度检查**: 检查分支健康状况
- **季度清理**: 清理不需要的分支和标签

## 🚨 注意事项

### 避免的操作
- ❌ 不要在multi-version分支直接开发功能
- ❌ 不要强制推送除非确定安全
- ❌ 不要忽略合并冲突

### 最佳实践
- ✅ 定期同步，避免积累太多差异
- ✅ 同步前先备份重要更改
- ✅ 仔细检查合并结果
- ✅ 测试同步后的构建

## 🆘 故障排除

### 同步失败
```bash
# 重置到远程状态
git fetch origin
git reset --hard origin/multi-version

# 重新开始同步
git merge main
```

### 推送被拒绝
```bash
# 拉取远程更改
git pull origin multi-version

# 解决冲突后重新推送
git push origin multi-version
```

### 历史混乱
```bash
# 查看提交历史
git log --graph --oneline --all

# 如果需要，重新创建multi-version分支
git branch -D multi-version
git checkout -b multi-version main
git push origin multi-version --force
```

通过遵循这个指南，您可以安全、高效地保持分支同步，确保多版本构建系统的正常运行！
