# Luminous LLM Chat Auto Sync and Build Script
# Automatically complete: Update main branch -> Merge to multi-version -> Trigger build

param(
    [switch]$Force,
    [switch]$DryRun,
    [switch]$SkipTests,
    [switch]$Verbose,
    [string]$TestVersion = "1.21.7"
)

# Color output functions
function Write-Info {
    param([string]$Message)
    Write-Host "i  $Message" -ForegroundColor Blue
}

function Write-Success {
    param([string]$Message)
    Write-Host "v $Message" -ForegroundColor Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "!  $Message" -ForegroundColor Yellow
}

function Write-Error {
    param([string]$Message)
    Write-Host "x $Message" -ForegroundColor Red
}

function Write-Step {
    param([string]$Message)
    Write-Host "-> $Message" -ForegroundColor Cyan
}

# Check Git status
function Test-GitStatus {
    $status = git status --porcelain
    if ($status) {
        Write-Warning "Working directory has uncommitted changes:"
        git status --short
        
        if (-not $Force) {
            $continue = Read-Host "Continue? This will stash current changes (y/N)"
            if ($continue -ne "y" -and $continue -ne "Y") {
                Write-Info "Operation cancelled"
                exit 1
            }
        }
        
        Write-Info "Stashing current changes..."
        if (-not $DryRun) {
            git stash push -m "Auto-sync script stash $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
        }
        Write-Success "Uncommitted changes have been stashed"
    }
}

# Check if branch exists
function Test-BranchExists {
    param([string]$BranchName)
    
    $exists = git show-ref --verify --quiet "refs/heads/$BranchName"
    return $LASTEXITCODE -eq 0
}

# Update main branch
function Update-MainBranch {
    Write-Step "Step 1/5: Update main branch"
    
    $currentBranch = git branch --show-current
    Write-Info "Current branch: $currentBranch"
    
    Write-Info "Switching to main branch..."
    if (-not $DryRun) {
        git checkout main
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Cannot switch to main branch"
            return $false
        }
    }
    
    Write-Info "Pulling latest changes from main branch..."
    if (-not $DryRun) {
        git pull origin main
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Cannot pull latest changes from main branch"
            return $false
        }
    }
    
    if ($Verbose) {
        Write-Info "Latest commits on main branch:"
        if (-not $DryRun) {
            git log --oneline -5
        } else {
            Write-Host "  [DRY RUN] Would show latest 5 commits"
        }
    }
    
    Write-Success "Main branch updated to latest version"
    return $true
}

# Ensure multi-version branch exists
function Ensure-MultiVersionBranch {
    Write-Step "Step 2/5: Check multi-version branch"
    
    if (Test-BranchExists "multi-version") {
        Write-Success "multi-version branch already exists"
        return $true
    }
    
    Write-Warning "multi-version branch does not exist, creating..."
    if (-not $Force) {
        $create = Read-Host "Create multi-version branch? (y/N)"
        if ($create -ne "y" -and $create -ne "Y") {
            Write-Error "multi-version branch is required to continue"
            return $false
        }
    }
    
    if (-not $DryRun) {
        git checkout -b multi-version
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Cannot create multi-version branch"
            return $false
        }
        
        git push -u origin multi-version
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Cannot push multi-version branch to remote"
            return $false
        }
    }
    
    Write-Success "multi-version branch created"
    return $true
}

# Merge main branch to multi-version
function Merge-MainToMultiVersion {
    Write-Step "Step 3/5: Merge main branch to multi-version"
    
    Write-Info "Switching to multi-version branch..."
    if (-not $DryRun) {
        git checkout multi-version
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Cannot switch to multi-version branch"
            return $false
        }
    }
    
    if (-not $DryRun) {
        $behindCommits = git rev-list --count HEAD..main
        if ($behindCommits -eq "0") {
            Write-Success "multi-version branch is up to date, no merge needed"
            return $true
        }
        Write-Info "Need to merge $behindCommits commits"
    }
    
    Write-Info "Merging main branch changes..."
    if (-not $DryRun) {
        git merge main --no-edit
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Merge conflicts occurred, please resolve conflicts manually and re-run script"
            Write-Info "Steps to resolve conflicts:"
            Write-Info "1. Edit conflict files, resolve conflict markers"
            Write-Info "2. git add ."
            Write-Info "3. git commit"
            Write-Info "4. Re-run this script"
            return $false
        }
    }
    
    Write-Success "Successfully merged main branch changes"
    return $true
}

# Run local test
function Test-LocalBuild {
    Write-Step "Step 4/5: Run local test build"
    
    if ($SkipTests) {
        Write-Info "Skipping local tests (used -SkipTests parameter)"
        return $true
    }
    
    Write-Info "Running local test build (version: $TestVersion)..."
    
    if (-not $DryRun) {
        if (Test-Path "scripts\manage-versions-simple.ps1") {
            & .\scripts\manage-versions-simple.ps1 test-build $TestVersion
            if ($LASTEXITCODE -ne 0) {
                Write-Error "Local test build failed"
                $continue = Read-Host "Continue with push? (y/N)"
                if ($continue -ne "y" -and $continue -ne "Y") {
                    return $false
                }
            } else {
                Write-Success "Local test build successful"
            }
        } else {
            Write-Warning "Test script not found, skipping local tests"
        }
    } else {
        Write-Host "  [DRY RUN] Would run local test build"
    }
    
    return $true
}

# Push and trigger build
function Push-AndTriggerBuild {
    Write-Step "Step 5/5: Push and trigger GitHub Actions build"
    
    Write-Info "Pushing multi-version branch to remote repository..."
    if (-not $DryRun) {
        git push origin multi-version
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Failed to push multi-version branch"
            return $false
        }
    }
    
    Write-Success "Pushed multi-version branch, GitHub Actions will automatically start multi-version build"
    
    $repoUrl = git config --get remote.origin.url
    if ($repoUrl -match "github\.com[:/](.+)/(.+)\.git") {
        $owner = $matches[1]
        $repo = $matches[2]
        $actionsUrl = "https://github.com/$owner/$repo/actions"
        Write-Info "View build status: $actionsUrl"
    }
    
    return $true
}

# Show summary
function Show-Summary {
    param([bool]$Success)
    
    Write-Host ""
    Write-Host "=" * 50 -ForegroundColor Gray
    
    if ($Success) {
        Write-Success "Auto sync and build process completed!"
        Write-Host ""
        Write-Info "Completed operations:"
        Write-Host "  v Updated main branch to latest version"
        Write-Host "  v Merged main branch to multi-version branch"
        if (-not $SkipTests) {
            Write-Host "  v Ran local test build"
        }
        Write-Host "  v Pushed multi-version branch to trigger GitHub Actions"
        Write-Host ""
        Write-Info "Next steps:"
        Write-Host "  • View GitHub Actions build status"
        Write-Host "  • Wait for all version builds to complete"
        Write-Host "  • Download build artifacts or create Release"
    } else {
        Write-Error "Auto sync and build process failed"
        Write-Host ""
        Write-Info "Please check the error messages above and resolve issues manually"
    }
    
    Write-Host "=" * 50 -ForegroundColor Gray
}

# Show help
function Show-Help {
    Write-Host "Luminous LLM Chat Auto Sync and Build Script"
    Write-Host ""
    Write-Host "Usage: .\auto-sync-build-clean.ps1 [options]"
    Write-Host ""
    Write-Host "Options:"
    Write-Host "  -Force          Force execution, skip confirmation prompts"
    Write-Host "  -DryRun         Preview mode, show operations but don't execute"
    Write-Host "  -SkipTests      Skip local test build"
    Write-Host "  -Verbose        Show detailed output"
    Write-Host "  -TestVersion    Specify version for testing (default: 1.21.7)"
    Write-Host ""
    Write-Host "Examples:"
    Write-Host "  .\auto-sync-build-clean.ps1                    # Standard execution"
    Write-Host "  .\auto-sync-build-clean.ps1 -Force             # Force execution"
    Write-Host "  .\auto-sync-build-clean.ps1 -DryRun            # Preview mode"
    Write-Host "  .\auto-sync-build-clean.ps1 -SkipTests         # Skip local tests"
}

# Main function
function Main {
    if (-not (Test-Path "gradle.properties") -or -not (Test-Path "build.gradle")) {
        Write-Error "Please run this script from project root directory"
        exit 1
    }
    
    Write-Host "Luminous LLM Chat Auto Sync and Build Script" -ForegroundColor Yellow
    Write-Host "=" * 50 -ForegroundColor Gray
    
    if ($DryRun) {
        Write-Warning "Preview mode: Will show operations but not execute them"
    }
    
    $success = $true
    
    try {
        Test-GitStatus
        
        if (-not (Update-MainBranch)) { $success = $false }
        if ($success -and -not (Ensure-MultiVersionBranch)) { $success = $false }
        if ($success -and -not (Merge-MainToMultiVersion)) { $success = $false }
        if ($success -and -not (Test-LocalBuild)) { $success = $false }
        if ($success -and -not (Push-AndTriggerBuild)) { $success = $false }
        
    } catch {
        Write-Error "Error occurred during execution: $_"
        $success = $false
    }
    
    Show-Summary $success
    
    if (-not $success) {
        exit 1
    }
}

# Check parameters
if ($args -contains "-h" -or $args -contains "--help" -or $args -contains "help") {
    Show-Help
    exit 0
}

# Run main function
Main
