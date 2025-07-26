# Luminous LLM Chat Version Management Script (PowerShell)
# Used for managing multi-version builds and branch strategy

param(
    [Parameter(Position=0)]
    [string]$Command = "help",
    
    [Parameter(Position=1)]
    [string]$Version = ""
)

# Auto-discover available versions
function Get-AvailableVersions {
    $versions = @{}
    
    # Scan build_version directory for config files
    if (Test-Path "build_version") {
        $configFiles = Get-ChildItem -Path "build_version" -Recurse -Filter "gradle-*.properties"
        foreach ($file in $configFiles) {
            # Extract version from filename (gradle-1.21.5.properties -> 1.21.5)
            if ($file.Name -match "gradle-([0-9]+\.[0-9]+\.[0-9]+)\.properties") {
                $version = $matches[1]
                $versions[$version] = $file.FullName
            }
        }
    }
    
    # Add default version (using root gradle.properties)
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

# List available versions
function Show-Versions {
    Write-Host "Auto-scanning available Minecraft versions:" -ForegroundColor Blue
    
    $versions = Get-AvailableVersions
    
    if ($versions.Count -eq 0) {
        Write-Host "No version configuration files found" -ForegroundColor Yellow
        return
    }
    
    # Sort and display versions
    $sortedVersions = $versions.GetEnumerator() | Sort-Object { [Version]$_.Key } -Descending
    
    foreach ($version in $sortedVersions) {
        $relativePath = $version.Value -replace [regex]::Escape((Get-Location).Path + "\"), ""
        if ($version.Value -eq "gradle.properties") {
            Write-Host "  * $($version.Key) ($relativePath - default dev version)" -ForegroundColor Green
        } else {
            Write-Host "  * $($version.Key) ($relativePath)"
        }
    }
    
    Write-Host "Total discovered: $($versions.Count) version configurations" -ForegroundColor Blue
}

# Test build version
function Test-BuildVersion {
    param([string]$Version)
    
    if (-not $Version) {
        Write-Host "Please specify version to test" -ForegroundColor Red
        Show-Versions
        return
    }
    
    # Get available versions
    $availableVersions = Get-AvailableVersions
    
    if (-not $availableVersions.ContainsKey($Version)) {
        Write-Host "Unsupported version: $Version" -ForegroundColor Red
        Write-Host "Available versions:" -ForegroundColor Blue
        Show-Versions
        return
    }
    
    Write-Host "Testing build for Minecraft $Version..." -ForegroundColor Blue
    
    # Backup current gradle.properties
    Copy-Item "gradle.properties" "gradle.properties.backup"
    
    try {
        # Get config file for version
        $configFile = $availableVersions[$Version]
        
        if ($configFile -ne "gradle.properties") {
            Write-Host "Using config file: $configFile" -ForegroundColor Blue
            Copy-Item $configFile "gradle.properties"
        } else {
            Write-Host "Using default config file: gradle.properties" -ForegroundColor Blue
        }
        
        Write-Host "Current build configuration:" -ForegroundColor Blue
        Select-String -Path "gradle.properties" -Pattern "(minecraft_version|mod_version|fabric_version)" | ForEach-Object { $_.Line }
        
        # Run build
        Write-Host "Starting build..." -ForegroundColor Blue
        & .\gradlew.bat clean build
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Minecraft $Version build successful!" -ForegroundColor Green
            Get-ChildItem "build/libs/" | Format-Table Name, Length, LastWriteTime
        } else {
            Write-Host "Minecraft $Version build failed" -ForegroundColor Red
        }
    }
    finally {
        # Restore original config
        Move-Item "gradle.properties.backup" "gradle.properties" -Force
        Write-Host "Restored original gradle.properties configuration" -ForegroundColor Blue
    }
}

# Show help
function Show-Help {
    Write-Host "Luminous LLM Chat Version Management Script (PowerShell)"
    Write-Host ""
    Write-Host "Usage: .\manage-versions-simple.ps1 [command] [parameters]"
    Write-Host ""
    Write-Host "Commands:"
    Write-Host "  list-versions    List all available versions"
    Write-Host "  test-build <ver> Test build specific version locally"
    Write-Host "  help             Show this help information"
    Write-Host ""
    Write-Host "Examples:"
    Write-Host "  .\manage-versions-simple.ps1 list-versions"
    Write-Host "  .\manage-versions-simple.ps1 test-build 1.21.6"
}

# Check if in project root directory
if (-not (Test-Path "gradle.properties") -or -not (Test-Path "build.gradle")) {
    Write-Host "Please run this script from project root directory" -ForegroundColor Red
    exit 1
}

# Main logic
switch ($Command.ToLower()) {
    "list-versions" {
        Show-Versions
    }
    "test-build" {
        Test-BuildVersion $Version
    }
    default {
        Show-Help
    }
}
