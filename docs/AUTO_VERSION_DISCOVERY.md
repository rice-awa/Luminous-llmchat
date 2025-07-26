# 🔍 自动版本发现系统

Luminous LLM Chat项目现在支持自动版本发现，无需手动维护版本列表！

## ✨ 主要特性

### 1. 自动扫描版本配置
- 自动扫描`build_version`目录下的所有`gradle-*.properties`文件
- 从文件名自动提取版本号（如：`gradle-1.21.6.properties` → `1.21.6`）
- 自动包含默认开发版本（`gradle.properties`）

### 2. 智能版本管理
- GitHub Actions工作流自动发现所有可用版本
- 管理脚本自动识别版本配置
- 无需修改工作流或脚本代码

### 3. 简化的版本添加流程
- 只需在`build_version`目录添加新的配置文件
- 系统自动识别并包含在构建中
- 支持自动检测Fabric版本信息

## 🚀 使用方法

### 查看可用版本
```powershell
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

### 测试特定版本构建
```powershell
.\scripts\manage-versions-simple.ps1 test-build 1.21.6
```

### 添加新版本（自动检测）
```powershell
.\scripts\add-version.ps1 -MinecraftVersion 1.21.9 -AutoDetect
```

### 添加新版本（手动指定）
```powershell
.\scripts\add-version.ps1 -MinecraftVersion 1.21.9 `
  -YarnMappings "1.21.9+build.1" `
  -FabricVersion "0.130.0+1.21.9" `
  -LoaderVersion "0.16.14"
```

## 🔧 技术实现

### GitHub Actions工作流
```yaml
- name: Auto-discover versions from build_version directory
  id: discover-versions
  run: |
    # 扫描build_version目录下的所有.properties文件
    while IFS= read -r -d '' file; do
      filename=$(basename "$file")
      if [[ $filename =~ gradle-([0-9]+\.[0-9]+\.[0-9]+)\.properties ]]; then
        version="${BASH_REMATCH[1]}"
        discovered_versions["$version"]="$file"
      fi
    done < <(find build_version -name "gradle-*.properties" -type f -print0)
```

### PowerShell脚本
```powershell
function Get-AvailableVersions {
    $versions = @{}
    
    if (Test-Path "build_version") {
        $configFiles = Get-ChildItem -Path "build_version" -Recurse -Filter "gradle-*.properties"
        foreach ($file in $configFiles) {
            if ($file.Name -match "gradle-([0-9]+\.[0-9]+\.[0-9]+)\.properties") {
                $version = $matches[1]
                $versions[$version] = $file.FullName
            }
        }
    }
    
    return $versions
}
```

## 📁 文件结构

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
gradle.properties (默认开发版本)
```

## 🎯 优势

### 1. 零维护成本
- 添加新版本时无需修改工作流文件
- 无需更新脚本中的版本列表
- 自动识别所有可用版本

### 2. 灵活性
- 支持任意版本号格式（只要符合x.y.z模式）
- 可以轻松添加或删除版本
- 支持多个主版本系列

### 3. 可靠性
- 基于文件系统扫描，不依赖外部配置
- 自动验证版本配置文件存在性
- 提供详细的错误信息和建议

## 🔄 工作流程

### 开发者添加新版本
1. 创建新的配置文件：`build_version/1.21/gradle-1.21.9.properties`
2. 系统自动识别新版本
3. GitHub Actions自动包含在构建矩阵中
4. 管理脚本自动显示新版本

### 构建系统处理
1. 扫描`build_version`目录
2. 提取版本号和配置文件路径
3. 生成动态构建矩阵
4. 并行构建所有版本

## 📊 当前状态

- ✅ 自动版本发现已实现
- ✅ GitHub Actions工作流已更新
- ✅ PowerShell管理脚本已更新
- ✅ Linux/Mac管理脚本已更新
- ✅ 版本添加脚本已创建
- ✅ 测试脚本已更新

## 🎉 总结

通过自动版本发现系统，Luminous LLM Chat项目现在可以：

1. **自动识别**所有可用的Minecraft版本配置
2. **动态生成**GitHub Actions构建矩阵
3. **简化版本管理**，只需添加配置文件即可
4. **提高可维护性**，减少手动维护工作

这个系统让多版本构建变得更加简单和可靠！🚀
