# Luminous LLM Chat 自动同步和构建脚本
# 自动完成：更新main分支 -> 合并到multi-version -> 触发构建

param(
    [switch]$Force,           # 强制执行，跳过确认
    [switch]$DryRun,          # 预演模式，不实际执行
    [switch]$SkipTests,       # 跳过本地测试
    [switch]$Verbose,         # 详细输出
    [string]$TestVersion = "1.21.7"
)

# 颜色输出函数
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

function Write-Step {
    param([string]$Message)
    Write-Host "🔄 $Message" -ForegroundColor Cyan
}

# 检查Git状态
function Test-GitStatus {
    $status = git status --porcelain
    if ($status) {
        Write-Warning "工作目录有未提交的更改："
        git status --short
        
        if (-not $Force) {
            $continue = Read-Host "是否继续？这将暂存当前更改 (y/N)"
            if ($continue -ne "y" -and $continue -ne "Y") {
                Write-Info "操作已取消"
                exit 1
            }
        }
        
        Write-Info "暂存当前更改..."
        if (-not $DryRun) {
            git stash push -m "Auto-sync script stash $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
        }
        Write-Success "已暂存未提交的更改"
    }
}

# 检查分支是否存在
function Test-BranchExists {
    param([string]$BranchName)
    
    $exists = git show-ref --verify --quiet "refs/heads/$BranchName"
    return $LASTEXITCODE -eq 0
}

# 更新main分支
function Update-MainBranch {
    Write-Step "步骤 1/5: 更新main分支"
    
    # 保存当前分支
    $currentBranch = git branch --show-current
    Write-Info "当前分支: $currentBranch"
    
    # 切换到main分支
    Write-Info "切换到main分支..."
    if (-not $DryRun) {
        git checkout main
        if ($LASTEXITCODE -ne 0) {
            Write-Error "无法切换到main分支"
            return $false
        }
    }
    
    # 拉取最新更改
    Write-Info "拉取main分支的最新更改..."
    if (-not $DryRun) {
        git pull origin main
        if ($LASTEXITCODE -ne 0) {
            Write-Error "无法拉取main分支的最新更改"
            return $false
        }
    }
    
    # 显示最新提交
    if ($Verbose) {
        Write-Info "main分支最新提交："
        if (-not $DryRun) {
            git log --oneline -5
        } else {
            Write-Host "  [DRY RUN] 将显示最新5个提交"
        }
    }
    
    Write-Success "main分支已更新到最新版本"
    return $true
}

# 检查并创建multi-version分支
function Ensure-MultiVersionBranch {
    Write-Step "步骤 2/5: 检查multi-version分支"
    
    if (Test-BranchExists "multi-version") {
        Write-Success "multi-version分支已存在"
        return $true
    }
    
    Write-Warning "multi-version分支不存在，正在创建..."
    if (-not $Force) {
        $create = Read-Host "是否创建multi-version分支？(y/N)"
        if ($create -ne "y" -and $create -ne "Y") {
            Write-Error "需要multi-version分支才能继续"
            return $false
        }
    }
    
    if (-not $DryRun) {
        git checkout -b multi-version
        if ($LASTEXITCODE -ne 0) {
            Write-Error "无法创建multi-version分支"
            return $false
        }
        
        git push -u origin multi-version
        if ($LASTEXITCODE -ne 0) {
            Write-Error "无法推送multi-version分支到远程"
            return $false
        }
    }
    
    Write-Success "multi-version分支已创建"
    return $true
}

# 合并main分支到multi-version
function Merge-MainToMultiVersion {
    Write-Step "步骤 3/5: 合并main分支到multi-version"
    
    # 切换到multi-version分支
    Write-Info "切换到multi-version分支..."
    if (-not $DryRun) {
        git checkout multi-version
        if ($LASTEXITCODE -ne 0) {
            Write-Error "无法切换到multi-version分支"
            return $false
        }
    }
    
    # 检查是否需要合并
    if (-not $DryRun) {
        $behindCommits = git rev-list --count HEAD..main
        if ($behindCommits -eq "0") {
            Write-Success "multi-version分支已是最新，无需合并"
            return $true
        }
        Write-Info "需要合并 $behindCommits 个提交"
    }
    
    # 合并main分支
    Write-Info "合并main分支的更改..."
    if (-not $DryRun) {
        git merge main --no-edit
        if ($LASTEXITCODE -ne 0) {
            Write-Error "合并过程中出现冲突，请手动解决冲突后重新运行脚本"
            Write-Info "解决冲突的步骤："
            Write-Info "1. 编辑冲突文件，解决冲突标记"
            Write-Info "2. git add ."
            Write-Info "3. git commit"
            Write-Info "4. 重新运行此脚本"
            return $false
        }
    }
    
    Write-Success "已成功合并main分支的更改"
    return $true
}

# 运行本地测试
function Test-LocalBuild {
    Write-Step "步骤 4/5: 运行本地测试构建"
    
    if ($SkipTests) {
        Write-Info "跳过本地测试（使用了 -SkipTests 参数）"
        return $true
    }
    
    Write-Info "运行本地测试构建（版本: $TestVersion）..."
    
    if (-not $DryRun) {
        # 检查测试脚本是否存在
        if (Test-Path "scripts\manage-versions-simple.ps1") {
            & .\scripts\manage-versions-simple.ps1 test-build $TestVersion
            if ($LASTEXITCODE -ne 0) {
                Write-Error "本地测试构建失败"
                $continue = Read-Host "是否继续推送？(y/N)"
                if ($continue -ne "y" -and $continue -ne "Y") {
                    return $false
                }
            } else {
                Write-Success "本地测试构建成功"
            }
        } else {
            Write-Warning "未找到测试脚本，跳过本地测试"
        }
    } else {
        Write-Host "  [DRY RUN] 将运行本地测试构建"
    }
    
    return $true
}

# 推送并触发构建
function Push-AndTriggerBuild {
    Write-Step "步骤 5/5: 推送并触发GitHub Actions构建"
    
    Write-Info "推送multi-version分支到远程仓库..."
    if (-not $DryRun) {
        git push origin multi-version
        if ($LASTEXITCODE -ne 0) {
            Write-Error "推送multi-version分支失败"
            return $false
        }
    }
    
    Write-Success "已推送multi-version分支，GitHub Actions将自动开始多版本构建"
    
    # 显示GitHub Actions链接
    $repoUrl = git config --get remote.origin.url
    if ($repoUrl -match "github\.com[:/](.+)/(.+)\.git") {
        $owner = $matches[1]
        $repo = $matches[2]
        $actionsUrl = "https://github.com/$owner/$repo/actions"
        Write-Info "查看构建状态: $actionsUrl"
    }
    
    return $true
}

# 显示摘要
function Show-Summary {
    param([bool]$Success)
    
    Write-Host ""
    Write-Host "=" * 50 -ForegroundColor Gray
    
    if ($Success) {
        Write-Success "🎉 自动同步和构建流程完成！"
        Write-Host ""
        Write-Info "已完成的操作："
        Write-Host "  ✅ 更新了main分支到最新版本"
        Write-Host "  ✅ 合并main分支到multi-version分支"
        if (-not $SkipTests) {
            Write-Host "  ✅ 运行了本地测试构建"
        }
        Write-Host "  ✅ 推送multi-version分支触发GitHub Actions"
        Write-Host ""
        Write-Info "接下来："
        Write-Host "  • 查看GitHub Actions构建状态"
        Write-Host "  • 等待所有版本构建完成"
        Write-Host "  • 下载构建产物或创建Release"
    } else {
        Write-Error "❌ 自动同步和构建流程失败"
        Write-Host ""
        Write-Info "请检查上述错误信息并手动解决问题"
    }
    
    Write-Host "=" * 50 -ForegroundColor Gray
}

# 显示帮助信息
function Show-Help {
    Write-Host "Luminous LLM Chat 自动同步和构建脚本"
    Write-Host ""
    Write-Host "用法: .\auto-sync-and-build.ps1 [选项]"
    Write-Host ""
    Write-Host "选项:"
    Write-Host "  -Force          强制执行，跳过确认提示"
    Write-Host "  -DryRun         预演模式，显示将要执行的操作但不实际执行"
    Write-Host "  -SkipTests      跳过本地测试构建"
    Write-Host "  -Verbose        显示详细输出"
    Write-Host "  -TestVersion    指定用于测试的版本 (默认: 1.21.7)"
    Write-Host ""
    Write-Host "示例:"
    Write-Host "  .\auto-sync-and-build.ps1                    # 标准执行"
    Write-Host "  .\auto-sync-and-build.ps1 -Force             # 强制执行，跳过确认"
    Write-Host "  .\auto-sync-and-build.ps1 -DryRun            # 预演模式"
    Write-Host "  .\auto-sync-and-build.ps1 -SkipTests         # 跳过本地测试"
    Write-Host "  .\auto-sync-and-build.ps1 -TestVersion 1.21.6 # 使用特定版本测试"
}

# 主函数
function Main {
    # 检查是否在项目根目录
    if (-not (Test-Path "gradle.properties") -or -not (Test-Path "build.gradle")) {
        Write-Error "请在项目根目录运行此脚本"
        exit 1
    }
    
    Write-Host "🚀 Luminous LLM Chat 自动同步和构建脚本" -ForegroundColor Yellow
    Write-Host "=" * 50 -ForegroundColor Gray
    
    if ($DryRun) {
        Write-Warning "预演模式：将显示要执行的操作但不实际执行"
    }
    
    # 执行流程
    $success = $true
    
    try {
        # 检查Git状态
        Test-GitStatus
        
        # 执行各个步骤
        if (-not (Update-MainBranch)) { $success = $false }
        if ($success -and -not (Ensure-MultiVersionBranch)) { $success = $false }
        if ($success -and -not (Merge-MainToMultiVersion)) { $success = $false }
        if ($success -and -not (Test-LocalBuild)) { $success = $false }
        if ($success -and -not (Push-AndTriggerBuild)) { $success = $false }
        
    } catch {
        Write-Error "执行过程中发生错误: $_"
        $success = $false
    }
    
    # 显示摘要
    Show-Summary $success
    
    if (-not $success) {
        exit 1
    }
}

# 检查参数
if ($args -contains "-h" -or $args -contains "--help" -or $args -contains "help") {
    Show-Help
    exit 0
}

# 运行主函数
Main
