# 多版本构建策略

本文档描述了Luminous LLM Chat项目的多版本构建和分支管理策略。

## 🌟 分支策略

### Main分支 (`main`)
- **用途**: 专注于特定版本的开发（当前为Minecraft 1.21.7）
- **特点**: 
  - 稳定的开发环境
  - 快速迭代和测试
  - 使用默认的`gradle.properties`配置
- **工作流**: 使用现有的`build.yml`工作流进行单版本构建

### Multi-Version分支 (`multi-version`)
- **用途**: 多版本构建和发布
- **特点**:
  - 包含所有版本的配置文件
  - 支持批量构建多个Minecraft版本
  - 用于正式发布
- **工作流**: 使用新的`multi-version-build.yml`工作流

## 🔧 支持的版本

当前支持的Minecraft版本：

| 版本 | 配置文件 | 状态 |
|------|----------|------|
| 1.21.5 | `build_version/1.21/gradle-1.21.5.properties` | ✅ 支持 |
| 1.21.6 | `build_version/1.21/gradle-1.21.6.properties` | ✅ 支持 |
| 1.21.7 | `gradle.properties` (默认) | ✅ 主开发版本 |
| 1.21.8 | `build_version/1.21/gradle-1.21.8.properties` | ✅ 支持 |

## 🚀 使用方法

### 1. 设置多版本分支

使用提供的管理脚本：

```bash
# 给脚本执行权限
chmod +x scripts/manage-versions.sh

# 创建multi-version分支
./scripts/manage-versions.sh create-branch

# 推送到远程仓库
./scripts/manage-versions.sh push-branch
```

### 2. 本地测试构建

测试特定版本的构建：

```bash
# 列出所有可用版本
./scripts/manage-versions.sh list-versions

# 测试构建特定版本
./scripts/manage-versions.sh test-build 1.21.6
```

### 3. 触发多版本构建

#### 方法一：推送到multi-version分支
```bash
git checkout multi-version
git push origin multi-version
```

#### 方法二：手动触发GitHub Actions
1. 访问GitHub仓库的Actions页面
2. 选择"Multi-Version Build"工作流
3. 点击"Run workflow"
4. 选择要构建的版本（或选择"all"构建所有版本）

### 4. 同步main分支的更改

当main分支有新的开发内容时：

```bash
./scripts/manage-versions.sh sync-from-main
```

## 📦 构建产物

多版本构建会生成以下构件：

### 测试报告
- `test-reports-{version}`: 每个版本的测试报告

### Mod文件
- `mod-{version}`: 每个版本的mod jar文件
- `sources-{version}`: 每个版本的源码文件

### 发布包
当启用`create_release`选项时，会创建包含所有版本的GitHub Release。

## 🔄 开发工作流

### 日常开发
1. 在`main`分支进行功能开发
2. 使用单版本构建进行快速测试
3. 提交和推送更改

### 准备发布
1. 将main分支的更改同步到multi-version分支
2. 触发多版本构建
3. 验证所有版本都能正常构建
4. 创建发布

### 添加新版本支持
1. 在`build_version/1.21/`目录下添加新的配置文件
2. 更新`multi-version-build.yml`中的版本矩阵
3. 更新管理脚本中的版本列表
4. 测试新版本的构建

## 🛠️ 配置文件说明

每个版本的配置文件包含：

```properties
# Minecraft和Fabric版本
minecraft_version=1.21.x
yarn_mappings=1.21.x+build.x
fabric_version=x.xxx.x+1.21.x

# Mod属性
mod_version=1.5.4
maven_group=com.riceawa
archives_base_name=luminousLLMChat-fabric
```

## 🔍 故障排除

### 构建失败
1. 检查版本配置文件是否正确
2. 验证Minecraft版本和Fabric API版本的兼容性
3. 查看构建日志中的具体错误信息

### 分支同步问题
1. 确保工作目录干净（无未提交更改）
2. 手动解决合并冲突
3. 使用`git status`检查状态

### 版本配置错误
1. 对比工作版本的配置文件
2. 检查Fabric官网的版本兼容性
3. 验证依赖版本是否存在

## 📋 最佳实践

1. **保持main分支稳定**: 只在main分支进行经过测试的更改
2. **定期同步**: 定期将main分支的更改同步到multi-version分支
3. **测试优先**: 在推送到multi-version分支前先进行本地测试
4. **版本管理**: 为每个重要发布创建Git标签
5. **文档更新**: 添加新版本支持时及时更新文档

## 🤝 贡献指南

如需添加新版本支持或改进构建流程：

1. Fork仓库
2. 在feature分支进行更改
3. 测试所有相关版本
4. 提交Pull Request到main分支
5. 经过审核后合并到multi-version分支
