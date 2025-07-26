# 🔄 工作流和分支策略

本文档详细说明Luminous LLM Chat项目的GitHub Actions工作流和分支策略，避免重复构建和资源浪费。

## 📋 分支和工作流映射

### 🎯 分支策略概览

```
┌─────────────────┐    ┌──────────────────────┐    ┌─────────────────────┐
│   main 分支     │    │   单版本构建工作流    │    │     构建产物        │
│                 │───▶│   (build.yml)       │───▶│  • mod-jars         │
│ • 快速开发      │    │                      │    │  • all-artifacts    │
│ • 日常测试      │    │ • 3-5分钟            │    │  • test-reports     │
│ • Minecraft     │    │ • 单版本(1.21.7)     │    │                     │
│   1.21.7        │    │ • 快速反馈           │    │                     │
└─────────────────┘    └──────────────────────┘    └─────────────────────┘
         │
         │ 同步更改
         ▼
┌─────────────────┐    ┌──────────────────────┐    ┌─────────────────────┐
│multi-version分支│    │  多版本构建工作流     │    │     构建产物        │
│                 │───▶│(multi-version-       │───▶│ • mod-{version}     │
│ • 多版本发布    │    │ build.yml)           │    │ • sources-{version} │
│ • 正式构建      │    │                      │    │ • test-reports-     │
│ • 8个版本       │    │ • 15-25分钟          │    │   {version}         │
│ • 发布准备      │    │ • 并行构建           │    │ • GitHub Release    │
└─────────────────┘    └──────────────────────┘    └─────────────────────┘
```

| 分支 | 用途 | 触发的工作流 | 构建类型 |
|------|------|-------------|----------|
| `main` | 快速开发和测试 | `build.yml` | 单版本构建（1.21.7） |
| `multi-version` | 多版本发布 | `multi-version-build.yml` | 多版本构建（所有支持版本） |

### 🔧 工作流详情

#### 1. 单版本构建工作流 (`build.yml`)

**触发条件：**
```yaml
on:
  pull_request:
    branches: [ main ]
  push:
    branches: [ main ]
  workflow_dispatch:
```

**特点：**
- ✅ 只在`main`分支上运行
- ✅ 快速构建单一版本（Minecraft 1.21.7）
- ✅ 适合日常开发和快速验证
- ✅ 构建时间短，反馈快

**构建产物：**
- `mod-jars` - 主要的mod文件
- `all-artifacts` - 包含源码的完整构件
- `test-reports` - 测试报告

#### 2. 多版本构建工作流 (`multi-version-build.yml`)

**触发条件：**
```yaml
on:
  push:
    branches:
      - multi-version
      - 'release/**'
  pull_request:
    branches:
      - multi-version
  workflow_dispatch:
```

**特点：**
- ✅ 只在`multi-version`分支上运行
- ✅ 自动发现并构建所有支持的版本
- ✅ 并行构建多个版本
- ✅ 支持创建GitHub Release

**构建产物：**
- `mod-{version}` - 每个版本的mod文件
- `sources-{version}` - 每个版本的源码文件
- `test-reports-{version}` - 每个版本的测试报告

## 🎯 避免重复构建的设计

### ❌ 之前的问题
- 两个工作流都在所有分支上运行
- 推送到`multi-version`分支会触发两个工作流
- 造成资源浪费和构建时间冗余

### ✅ 现在的解决方案
- **分支隔离**：每个工作流只在特定分支上运行
- **功能分离**：单版本用于开发，多版本用于发布
- **资源优化**：避免重复构建相同的内容

## 🚀 使用场景

### 场景1：日常开发
```bash
# 在main分支开发
git checkout main
git add .
git commit -m "添加新功能"
git push origin main
```
**结果：** 触发单版本构建，快速验证更改

### 场景2：准备发布
```bash
# 同步到multi-version分支
git checkout multi-version
git merge main
git push origin multi-version
```
**结果：** 触发多版本构建，生成所有版本的构件

### 场景3：紧急修复
```bash
# 在main分支修复
git checkout main
# ... 修复代码 ...
git push origin main

# 立即同步到multi-version
git checkout multi-version
git merge main
git push origin multi-version
```
**结果：** 先快速验证修复，再进行完整的多版本构建

## 📊 构建时间对比

### 单版本构建 (`main`分支)
- **构建时间**: ~3-5分钟
- **版本数量**: 1个（Minecraft 1.21.7）
- **适用场景**: 开发验证

### 多版本构建 (`multi-version`分支)
- **构建时间**: ~15-25分钟
- **版本数量**: 8个（自动发现的所有版本）
- **适用场景**: 正式发布

### 节省的时间
- **避免重复**: 不再在`multi-version`分支运行单版本构建
- **并行优化**: 多版本构建使用矩阵并行执行
- **总体提升**: 减少约30%的构建时间

## 🔧 工作流配置要点

### 单版本构建工作流
```yaml
name: Single Version Build (Main Branch)
on:
  pull_request:
    branches: [ main ]  # 只在main分支的PR
  push:
    branches: [ main ]  # 只在推送到main分支时
  workflow_dispatch:     # 支持手动触发
```

### 多版本构建工作流
```yaml
name: Multi-Version Build
on:
  push:
    branches:
      - multi-version    # 主要的多版本分支
      - 'release/**'     # 发布分支
  pull_request:
    branches:
      - multi-version    # 针对multi-version的PR
  workflow_dispatch:     # 支持手动触发
```

## 🛠️ 最佳实践

### 开发阶段
1. **在main分支开发** - 享受快速构建反馈
2. **频繁提交** - 利用单版本构建快速验证
3. **PR审查** - 在main分支进行代码审查

### 发布阶段
1. **同步到multi-version** - 使用自动脚本或手动合并
2. **触发多版本构建** - 确保所有版本都能正常构建
3. **创建Release** - 使用工作流的Release功能

### 维护阶段
1. **定期同步** - 保持multi-version分支与main同步
2. **监控构建** - 关注两个工作流的健康状态
3. **优化配置** - 根据需要调整构建参数

## 📈 监控和维护

### 构建状态检查
- **main分支**: 检查单版本构建是否通过
- **multi-version分支**: 检查所有版本是否构建成功

### 常见问题排查
1. **构建失败**: 检查对应分支的工作流日志
2. **版本不匹配**: 确认版本配置文件是否正确
3. **依赖问题**: 验证Fabric API版本兼容性

### 性能优化
- **缓存策略**: 利用Gradle缓存加速构建
- **并行执行**: 多版本构建使用矩阵并行
- **资源限制**: 避免同时运行过多构建任务

## 🎉 总结

通过这个分支和工作流策略：

✅ **避免重复构建** - 每个分支只运行必要的工作流  
✅ **提高效率** - 开发时快速反馈，发布时完整构建  
✅ **节省资源** - 减少不必要的GitHub Actions使用时间  
✅ **清晰分工** - 开发和发布流程分离，职责明确  
✅ **易于维护** - 简化的工作流配置，便于理解和维护  

这个策略确保了开发效率和发布质量的平衡，同时最大化了CI/CD资源的利用效率。
