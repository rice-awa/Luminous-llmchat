# 添加新版本配置的脚本
# 自动创建新的Minecraft版本配置文件

param(
    [Parameter(Mandatory=$true)]
    [string]$MinecraftVersion,
    
    [string]$YarnMappings = "",
    [string]$FabricVersion = "",
    [string]$LoaderVersion = "",
    [switch]$AutoDetect,
    [switch]$Force
)

function Write-Info {
    param([string]$Message)
    Write-Host "ℹ️  $Message" -ForegroundColor Blue
}

function Write-Success {
    param([string]$Message)
    Write-Host "✅ $Message" -ForegroundColor Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "⚠️  $Message" -ForegroundColor Yellow
}

function Write-Error {
    param([string]$Message)
    Write-Host "❌ $Message" -ForegroundColor Red
}

function Get-FabricVersionInfo {
    param([string]$McVersion)
    
    Write-Info "正在获取Minecraft $McVersion 的Fabric版本信息..."
    
    try {
        # 获取Fabric API版本（从Modrinth API）
        $fabricApiUrl = "https://api.modrinth.com/v2/project/fabric-api/version"
        $fabricApiParams = @{
            game_versions = @($McVersion)
            loaders = @("fabric")
        }
        
        $fabricApiResponse = Invoke-RestMethod -Uri $fabricApiUrl -Body $fabricApiParams -Method Get -TimeoutSec 10
        $fabricApiVersion = if ($fabricApiResponse.Count -gt 0) { $fabricApiResponse[0].version_number } else { $null }
        
        # 获取Fabric Loader和Yarn版本（从Fabric Meta API）
        $metaUrl = "https://meta.fabricmc.net/v2/versions"
        $metaResponse = Invoke-RestMethod -Uri $metaUrl -Method Get -TimeoutSec 10
        
        # 获取最新稳定的Loader版本
        $loaderVersion = ($metaResponse.loader | Where-Object { $_.stable } | Select-Object -First 1).version
        
        # 获取对应Minecraft版本的Yarn映射
        $yarnMappings = ($metaResponse.mappings | Where-Object { $_.gameVersion -eq $McVersion } | Select-Object -First 1).version
        
        return @{
            FabricApi = $fabricApiVersion
            Loader = $loaderVersion
            Yarn = $yarnMappings
        }
    }
    catch {
        Write-Warning "自动获取版本信息失败: $_"
        return $null
    }
}

function New-VersionConfig {
    param(
        [string]$McVersion,
        [string]$Yarn,
        [string]$FabricApi,
        [string]$Loader
    )
    
    # 确定输出目录和文件名
    $majorVersion = ($McVersion -split '\.')[0..1] -join '.'
    $outputDir = "build_version\$majorVersion"
    $outputFile = "$outputDir\gradle-$McVersion.properties"
    
    # 检查文件是否已存在
    if ((Test-Path $outputFile) -and -not $Force) {
        Write-Error "配置文件已存在: $outputFile"
        Write-Info "使用 -Force 参数覆盖现有文件"
        return $false
    }
    
    # 创建输出目录
    if (-not (Test-Path $outputDir)) {
        New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
        Write-Info "创建目录: $outputDir"
    }
    
    # 读取模板文件
    if (-not (Test-Path "gradle.properties")) {
        Write-Error "模板文件不存在: gradle.properties"
        return $false
    }
    
    $templateContent = Get-Content "gradle.properties" -Raw
    
    # 替换版本信息
    $newContent = $templateContent
    $newContent = $newContent -replace "minecraft_version=.*", "minecraft_version=$McVersion"
    
    if ($Yarn) {
        $newContent = $newContent -replace "yarn_mappings=.*", "yarn_mappings=$Yarn"
    }
    
    if ($FabricApi) {
        $newContent = $newContent -replace "fabric_version=.*", "fabric_version=$FabricApi"
    }
    
    if ($Loader) {
        $newContent = $newContent -replace "loader_version=.*", "loader_version=$Loader"
    }
    
    # 写入新文件
    $newContent | Set-Content -Path $outputFile -Encoding UTF8
    
    Write-Success "已创建配置文件: $outputFile"
    
    # 显示配置内容
    Write-Info "配置内容:"
    Get-Content $outputFile | Where-Object { $_ -match "(minecraft_version|yarn_mappings|fabric_version|loader_version)=" } | ForEach-Object {
        Write-Host "  $_" -ForegroundColor Gray
    }
    
    return $true
}

function Show-Help {
    Write-Host "添加新版本配置脚本"
    Write-Host ""
    Write-Host "用法: .\add-version.ps1 -MinecraftVersion <版本> [选项]"
    Write-Host ""
    Write-Host "参数:"
    Write-Host "  -MinecraftVersion <版本>  Minecraft版本号 (必需)"
    Write-Host "  -YarnMappings <版本>      Yarn映射版本"
    Write-Host "  -FabricVersion <版本>     Fabric API版本"
    Write-Host "  -LoaderVersion <版本>     Fabric Loader版本"
    Write-Host "  -AutoDetect               自动检测版本信息"
    Write-Host "  -Force                    覆盖现有文件"
    Write-Host ""
    Write-Host "示例:"
    Write-Host "  .\add-version.ps1 -MinecraftVersion 1.21.9 -AutoDetect"
    Write-Host "  .\add-version.ps1 -MinecraftVersion 1.21.9 -YarnMappings '1.21.9+build.1' -FabricVersion '0.130.0+1.21.9'"
}

# 主逻辑
if (-not $MinecraftVersion) {
    Show-Help
    exit 1
}

# 验证Minecraft版本格式
if ($MinecraftVersion -notmatch '^\d+\.\d+\.\d+$') {
    Write-Error "无效的Minecraft版本格式: $MinecraftVersion"
    Write-Info "版本格式应为: x.y.z (如: 1.21.9)"
    exit 1
}

Write-Info "为Minecraft $MinecraftVersion 创建版本配置..."

# 自动检测版本信息
if ($AutoDetect) {
    $versionInfo = Get-FabricVersionInfo -McVersion $MinecraftVersion
    
    if ($versionInfo) {
        if (-not $YarnMappings -and $versionInfo.Yarn) {
            $YarnMappings = $versionInfo.Yarn
            Write-Info "自动检测到Yarn映射: $YarnMappings"
        }
        
        if (-not $FabricVersion -and $versionInfo.FabricApi) {
            $FabricVersion = $versionInfo.FabricApi
            Write-Info "自动检测到Fabric API: $FabricVersion"
        }
        
        if (-not $LoaderVersion -and $versionInfo.Loader) {
            $LoaderVersion = $versionInfo.Loader
            Write-Info "自动检测到Fabric Loader: $LoaderVersion"
        }
    } else {
        Write-Warning "自动检测失败，请手动指定版本信息"
    }
}

# 创建配置文件
$success = New-VersionConfig -McVersion $MinecraftVersion -Yarn $YarnMappings -FabricApi $FabricVersion -Loader $LoaderVersion

if ($success) {
    Write-Success "🎉 版本配置创建完成！"
    Write-Info ""
    Write-Info "接下来您可以:"
    Write-Info "• 测试构建: .\scripts\manage-versions.ps1 test-build $MinecraftVersion"
    Write-Info "• 查看所有版本: .\scripts\manage-versions.ps1 list-versions"
    Write-Info "• 多版本构建: .\scripts\manage-versions.ps1 release"
} else {
    Write-Error "版本配置创建失败"
    exit 1
}
