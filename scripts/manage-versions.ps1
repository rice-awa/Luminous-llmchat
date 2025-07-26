# Luminous LLM Chat 版本管理脚本 (PowerShell版本)
# 用于管理多版本构建和分支策略

param(
    [Parameter(Position=0)]
    [string]$Command = "help",
    
    [Parameter(Position=1)]
    [string]$Version = ""
)

# 颜色函数
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

# 检查Git状态
function Test-GitStatus {
    $status = git status --porcelain
    if ($status) {
        Write-Warning "工作目录有未提交的更改，请先提交或暂存更改"
        git status --short
        $continue = Read-Host "是否继续？(y/N)"
        if ($continue -ne "y" -and $continue -ne "Y") {
            exit 1
        }
    }
}

# 创建multi-version分支
function New-MultiVersionBranch {
    Write-Info "创建multi-version分支..."
    
    # 检查分支是否已存在
    $branchExists = git show-ref --verify --quiet refs/heads/multi-version
    if ($LASTEXITCODE -eq 0) {
        Write-Warning "multi-version分支已存在"
        $reset = Read-Host "是否要重置该分支？(y/N)"
        if ($reset -eq "y" -or $reset -eq "Y") {
            git branch -D multi-version
            Write-Success "已删除旧的multi-version分支"
        } else {
            Write-Info "使用现有的multi-version分支"
            return
        }
    }
    
    # 从main分支创建multi-version分支
    git checkout main
    git pull origin main
    git checkout -b multi-version
    
    Write-Success "已创建multi-version分支"
}

# 推送multi-version分支
function Push-MultiVersionBranch {
    Write-Info "推送multi-version分支到远程仓库..."
    
    git push -u origin multi-version
    
    Write-Success "multi-version分支已推送到远程仓库"
}

# 列出可用版本
function Show-Versions {
    Write-Info "可用的Minecraft版本:"
    Write-Host "  • 1.21.5 (build_version/1.21/gradle-1.21.5.properties)"
    Write-Host "  • 1.21.6 (build_version/1.21/gradle-1.21.6.properties)"
    Write-Host "  • 1.21.7 (gradle.properties - 默认开发版本)"
    Write-Host "  • 1.21.8 (build_version/1.21/gradle-1.21.8.properties)"
}

# 本地测试构建
function Test-BuildVersion {
    param([string]$Version)
    
    if (-not $Version) {
        Write-Error "请指定要测试的版本"
        Show-Versions
        return
    }
    
    Write-Info "测试构建Minecraft $Version..."
    
    # 备份当前gradle.properties
    Copy-Item "gradle.properties" "gradle.properties.backup"
    
    try {
        # 根据版本选择配置文件
        switch ($Version) {
            "1.21.5" {
                Copy-Item "build_version/1.21/gradle-1.21.5.properties" "gradle.properties"
            }
            "1.21.6" {
                Copy-Item "build_version/1.21/gradle-1.21.6.properties" "gradle.properties"
            }
            "1.21.7" {
                # 使用默认配置
            }
            "1.21.8" {
                Copy-Item "build_version/1.21/gradle-1.21.8.properties" "gradle.properties"
            }
            default {
                Write-Error "不支持的版本: $Version"
                Show-Versions
                return
            }
        }
        
        Write-Info "当前构建配置:"
        Select-String -Path "gradle.properties" -Pattern "(minecraft_version|mod_version|fabric_version)" | ForEach-Object { $_.Line }
        
        # 运行构建
        Write-Info "开始构建..."
        & .\gradlew.bat clean build
        
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Minecraft $Version 构建成功！"
            Get-ChildItem "build/libs/" | Format-Table Name, Length, LastWriteTime
        } else {
            Write-Error "Minecraft $Version 构建失败"
        }
    }
    finally {
        # 恢复原配置
        Move-Item "gradle.properties.backup" "gradle.properties" -Force
        Write-Info "已恢复原始gradle.properties配置"
    }
}

# 同步main分支的更改到multi-version分支
function Sync-FromMain {
    Write-Info "将main分支的更改同步到multi-version分支..."
    
    # 保存当前分支
    $currentBranch = git branch --show-current
    
    # 切换到main分支并拉取最新更改
    git checkout main
    git pull origin main
    
    # 切换到multi-version分支并合并main的更改
    git checkout multi-version
    git merge main --no-edit
    
    Write-Success "已将main分支的更改合并到multi-version分支"
    
    # 如果原来不在multi-version分支，切换回原分支
    if ($currentBranch -ne "multi-version") {
        git checkout $currentBranch
    }
}

# 显示帮助信息
function Show-Help {
    Write-Host "Luminous LLM Chat 版本管理脚本 (PowerShell版本)"
    Write-Host ""
    Write-Host "用法: .\manage-versions.ps1 [命令] [参数]"
    Write-Host ""
    Write-Host "命令:"
    Write-Host "  create-branch    创建multi-version分支"
    Write-Host "  push-branch      推送multi-version分支到远程"
    Write-Host "  list-versions    列出所有可用版本"
    Write-Host "  test-build <版本> 本地测试构建指定版本"
    Write-Host "  sync-from-main   将main分支的更改同步到multi-version分支"
    Write-Host "  help             显示此帮助信息"
    Write-Host ""
    Write-Host "示例:"
    Write-Host "  .\manage-versions.ps1 create-branch"
    Write-Host "  .\manage-versions.ps1 test-build 1.21.6"
    Write-Host "  .\manage-versions.ps1 sync-from-main"
}

# 检查是否在项目根目录
if (-not (Test-Path "gradle.properties") -or -not (Test-Path "build.gradle")) {
    Write-Error "请在项目根目录运行此脚本"
    exit 1
}

# 主逻辑
switch ($Command.ToLower()) {
    "create-branch" {
        Test-GitStatus
        New-MultiVersionBranch
    }
    "push-branch" {
        Push-MultiVersionBranch
    }
    "list-versions" {
        Show-Versions
    }
    "test-build" {
        Test-BuildVersion $Version
    }
    "sync-from-main" {
        Test-GitStatus
        Sync-FromMain
    }
    default {
        Show-Help
    }
}
