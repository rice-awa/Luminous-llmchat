# 测试同步功能的脚本
# 用于验证main分支的更改是否正确同步到multi-version分支

param(
    [switch]$Verbose
)

function Write-TestInfo {
    param([string]$Message)
    Write-Host "🔍 $Message" -ForegroundColor Cyan
}

function Write-TestSuccess {
    param([string]$Message)
    Write-Host "✅ $Message" -ForegroundColor Green
}

function Write-TestError {
    param([string]$Message)
    Write-Host "❌ $Message" -ForegroundColor Red
}

function Test-BranchSync {
    Write-TestInfo "测试分支同步功能..."
    
    # 检查当前Git状态
    $gitStatus = git status --porcelain
    if ($gitStatus) {
        Write-TestError "工作目录不干净，请先提交或暂存更改"
        return $false
    }
    
    # 保存当前分支
    $originalBranch = git branch --show-current
    Write-TestInfo "原始分支: $originalBranch"
    
    try {
        # 检查main分支的最新提交
        git checkout main | Out-Null
        $mainCommit = git rev-parse HEAD
        $mainCommitMsg = git log -1 --oneline
        Write-TestInfo "main分支最新提交: $mainCommitMsg"
        
        # 检查multi-version分支是否存在
        $branchExists = git show-ref --verify --quiet refs/heads/multi-version
        if ($LASTEXITCODE -ne 0) {
            Write-TestError "multi-version分支不存在"
            return $false
        }
        
        # 切换到multi-version分支
        git checkout multi-version | Out-Null
        $multiVersionCommit = git rev-parse HEAD
        $multiVersionCommitMsg = git log -1 --oneline
        Write-TestInfo "multi-version分支当前提交: $multiVersionCommitMsg"
        
        # 检查是否需要同步
        if ($mainCommit -eq $multiVersionCommit) {
            Write-TestSuccess "分支已同步，无需更新"
            return $true
        }
        
        # 检查multi-version分支是否包含main分支的更改
        $mergeBase = git merge-base main multi-version
        if ($mergeBase -eq $mainCommit) {
            Write-TestSuccess "multi-version分支已包含main分支的所有更改"
            return $true
        } else {
            Write-TestInfo "multi-version分支需要同步main分支的更改"
            Write-TestInfo "合并基点: $(git log -1 --oneline $mergeBase)"
            return $false
        }
    }
    finally {
        # 切换回原始分支
        if ($originalBranch -and $originalBranch -ne "") {
            git checkout $originalBranch | Out-Null
        }
    }
}

# 自动发现可用版本（复制自manage-versions.ps1）
function Get-AvailableVersions {
    $versions = @{}

    # 扫描build_version目录下的配置文件
    if (Test-Path "build_version") {
        $configFiles = Get-ChildItem -Path "build_version" -Recurse -Filter "gradle-*.properties"
        foreach ($file in $configFiles) {
            # 从文件名提取版本号 (gradle-1.21.5.properties -> 1.21.5)
            if ($file.Name -match "gradle-([0-9]+\.[0-9]+\.[0-9]+)\.properties") {
                $version = $matches[1]
                $versions[$version] = $file.FullName
            }
        }
    }

    # 添加默认版本（使用根目录的gradle.properties）
    if (Test-Path "gradle.properties") {
        $content = Get-Content "gradle.properties"
        $minecraftVersionLine = $content | Where-Object { $_ -match "minecraft_version=" }
        if ($minecraftVersionLine) {
            $defaultVersion = ($minecraftVersionLine -split "=")[1]
            $versions[$defaultVersion] = "gradle.properties"
        }
    }

    return $versions
}

function Test-ConfigFiles {
    Write-TestInfo "自动发现并测试版本配置文件..."

    # 自动发现版本
    $discoveredVersions = Get-AvailableVersions

    if ($discoveredVersions.Count -eq 0) {
        Write-TestError "未发现任何版本配置文件"
        return $false
    }

    Write-TestInfo "发现 $($discoveredVersions.Count) 个版本配置文件"

    $allValid = $true

    foreach ($versionEntry in $discoveredVersions.GetEnumerator()) {
        $version = $versionEntry.Key
        $file = $versionEntry.Value

        # 转换为相对路径显示
        $relativePath = $file -replace [regex]::Escape((Get-Location).Path + "\"), ""

        if (Test-Path $file) {
            $content = Get-Content $file
            $minecraftVersion = $content | Where-Object { $_ -match "minecraft_version=" }
            $modVersion = $content | Where-Object { $_ -match "mod_version=" }
            $fabricVersion = $content | Where-Object { $_ -match "fabric_version=" }

            if ($minecraftVersion -and $modVersion -and $fabricVersion) {
                Write-TestSuccess "版本 $version 配置文件有效 ($relativePath)"
                if ($Verbose) {
                    Write-Host "  $minecraftVersion" -ForegroundColor Gray
                    Write-Host "  $modVersion" -ForegroundColor Gray
                    Write-Host "  $fabricVersion" -ForegroundColor Gray
                }
            } else {
                Write-TestError "版本 $version 配置文件缺少必要属性 ($relativePath)"
                $allValid = $false
            }
        } else {
            Write-TestError "版本 $version 配置文件不存在: $relativePath"
            $allValid = $false
        }
    }

    return $allValid
}

function Test-WorkflowFiles {
    Write-TestInfo "测试GitHub工作流文件..."
    
    $workflows = @(
        "build.yml",
        "multi-version-build.yml"
    )
    
    $allValid = $true
    
    foreach ($workflow in $workflows) {
        $path = ".github/workflows/$workflow"
        if (Test-Path $path) {
            Write-TestSuccess "工作流文件存在: $workflow"
        } else {
            Write-TestError "工作流文件不存在: $workflow"
            $allValid = $false
        }
    }
    
    return $allValid
}

# 主测试函数
function Start-Tests {
    Write-Host "🧪 开始测试多版本构建系统..." -ForegroundColor Yellow
    Write-Host "=================================" -ForegroundColor Yellow
    
    $results = @()
    
    # 测试分支同步
    $syncResult = Test-BranchSync
    $results += @{Name="分支同步"; Result=$syncResult}
    
    # 测试配置文件
    $configResult = Test-ConfigFiles
    $results += @{Name="配置文件"; Result=$configResult}
    
    # 测试工作流文件
    $workflowResult = Test-WorkflowFiles
    $results += @{Name="工作流文件"; Result=$workflowResult}
    
    # 显示测试结果
    Write-Host ""
    Write-Host "📊 测试结果:" -ForegroundColor Yellow
    Write-Host "=============" -ForegroundColor Yellow
    
    $allPassed = $true
    foreach ($result in $results) {
        if ($result.Result) {
            Write-TestSuccess "$($result.Name): 通过"
        } else {
            Write-TestError "$($result.Name): 失败"
            $allPassed = $false
        }
    }
    
    Write-Host ""
    if ($allPassed) {
        Write-TestSuccess "🎉 所有测试通过！多版本构建系统已就绪"
    } else {
        Write-TestError "❌ 部分测试失败，请检查上述错误"
    }
    
    return $allPassed
}

# 检查是否在项目根目录
if (-not (Test-Path "gradle.properties") -or -not (Test-Path "build.gradle")) {
    Write-TestError "请在项目根目录运行此脚本"
    exit 1
}

# 运行测试
$testResult = Start-Tests
if (-not $testResult) {
    exit 1
}
