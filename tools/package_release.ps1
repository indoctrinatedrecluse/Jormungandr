param(
    [string]$Tag = "v1.4.0"
)

$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$tag = $Tag
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Packaging Jormungandr Release: $tag" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$distDir = Join-Path $ProjectRoot "release-dist"
if (Test-Path $distDir) {
    Remove-Item $distDir -Recurse -Force
}
New-Item -ItemType Directory -Path $distDir -Force | Out-Null

# 1. Modular Plugin Package
Write-Host "Compiling plugins and preparing sandbox..." -ForegroundColor Yellow
& "$ProjectRoot\gradlew.bat" prepareSandbox buildPlugin --no-daemon

$pluginZip = Get-ChildItem -Path "$ProjectRoot\modules\platform-shell\build\distributions\*.zip" | Select-Object -First 1
if (-not $pluginZip) {
    Write-Error "Could not locate built plugin distribution zip!"
}

$destPlugin = Join-Path $distDir "Jormungandr-Plugin-$tag.zip"
Copy-Item -Path $pluginZip.FullName -Destination $destPlugin -Force
$pluginSize = [math]::Round((Get-Item $destPlugin).Length / 1MB, 2)
Write-Host "[OK] Packaged plugin: $destPlugin ($pluginSize MB)" -ForegroundColor Green

# 2. Standalone Portable Distribution
$ideaHome = Get-ChildItem -Path "$env:USERPROFILE\.gradle\caches\8.11.1\transforms\*\transformed\ideaIC-*-win" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
$sandboxPlugins = Get-ChildItem -Path "$ProjectRoot\modules\platform-shell\build\idea-sandbox\IC-*\plugins" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1

if (-not $ideaHome -or -not $sandboxPlugins) {
    Write-Error "Could not locate transformed ideaIC home or sandbox plugins!"
}

Write-Host "Found IntelliJ Platform Home: $($ideaHome.FullName)" -ForegroundColor Gray
Write-Host "Found Sandbox Plugins: $($sandboxPlugins.FullName)" -ForegroundColor Gray

$stagingRoot = Join-Path $distDir "staging"
$stagingDir = Join-Path $stagingRoot "Jormungandr-$tag"
New-Item -ItemType Directory -Path $stagingDir -Force | Out-Null
$resourcesDir = (Resolve-Path "$ProjectRoot\modules\platform-shell\src\main\resources").Path

Write-Host "Copying IntelliJ platform base into staging..." -ForegroundColor Yellow
Copy-Item -Path "$($ideaHome.FullName)\*" -Destination $stagingDir -Recurse -Force

Write-Host "Deploying Jormungandr plugins into distribution..." -ForegroundColor Yellow
$bundledPluginsDir = Join-Path $stagingDir "plugins"
$targetPluginsDir = Join-Path $stagingDir "jormungandr-plugins"
$portablePluginsDir = Join-Path $stagingDir "portable-data\plugins"
New-Item -ItemType Directory -Path $targetPluginsDir -Force | Out-Null
New-Item -ItemType Directory -Path $portablePluginsDir -Force | Out-Null

Get-ChildItem -Path $sandboxPlugins.FullName -Directory | ForEach-Object {
    $pluginName = $_.Name
    Write-Host "  -> Deploying plugin: $pluginName" -ForegroundColor Cyan
    Copy-Item -Path $_.FullName -Destination (Join-Path $bundledPluginsDir $pluginName) -Recurse -Force
    Copy-Item -Path $_.FullName -Destination (Join-Path $targetPluginsDir $pluginName) -Recurse -Force
    Copy-Item -Path $_.FullName -Destination (Join-Path $portablePluginsDir $pluginName) -Recurse -Force
}

Write-Host "Copying bundled samples directory into staging..." -ForegroundColor Yellow
$samplesSource = Join-Path $ProjectRoot "samples"
if (Test-Path $samplesSource) {
    $targetSamples = Join-Path $stagingDir "samples"
    Copy-Item -Path $samplesSource -Destination $targetSamples -Recurse -Force
    Write-Host "[OK] Bundled samples into $targetSamples" -ForegroundColor Green
}

Write-Host "Deploying smart delta auto-updater binaries..." -ForegroundColor Yellow
$updaterBat = Join-Path $ProjectRoot "installer\jormungandr-updater.bat"
$updaterPs1 = Join-Path $ProjectRoot "installer\jormungandr-updater.ps1"
Copy-Item -Path $updaterBat -Destination (Join-Path $stagingDir "bin\jormungandr-updater.bat") -Force
Copy-Item -Path $updaterPs1 -Destination (Join-Path $stagingDir "bin\jormungandr-updater.ps1") -Force

Write-Host "Locating Python interpreter for distribution patching..." -ForegroundColor Yellow
$pythonCandidates = @(
    "C:\Users\RECLUSE\AppData\Local\Python\bin\python.exe",
    "${env:LOCALAPPDATA}\Python\bin\python.exe",
    "${env:LOCALAPPDATA}\Programs\Python\Python314\python.exe",
    "${env:LOCALAPPDATA}\Programs\Python\Python313\python.exe",
    "${env:LOCALAPPDATA}\Programs\Python\Python312\python.exe",
    "${env:LOCALAPPDATA}\Programs\Python\Python311\python.exe",
    "${env:LOCALAPPDATA}\Programs\Python\Python310\python.exe",
    "py.exe",
    "python.exe"
)
$resolvedPython = $null
foreach ($cand in $pythonCandidates) {
    if (Test-Path $cand) {
        $resolvedPython = $cand
        break
    } elseif (Get-Command $cand -ErrorAction SilentlyContinue) {
        $found = (Get-Command $cand).Source
        if ($found -notlike "*WindowsApps*") {
            $resolvedPython = $found
            break
        }
    }
}

if (-not $resolvedPython) {
    Write-Error "Could not locate a valid Python interpreter to run patch_portable_ide.py!"
}

Write-Host "Using Python: $resolvedPython" -ForegroundColor Green
Write-Host "Patching portable distribution via patch_portable_ide.py..." -ForegroundColor Yellow
& $resolvedPython "$ProjectRoot\tools\patch_portable_ide.py" "$stagingDir" "$resourcesDir" "$tag"
if ($LASTEXITCODE -ne 0) {
    Write-Error "patch_portable_ide.py failed with exit code $LASTEXITCODE!"
}

# Post-patch verification: verify essential files exist
$criticalFiles = @(
    (Join-Path $stagingDir "jormungandr.bat"),
    (Join-Path $stagingDir "Jormungandr.bat"),
    (Join-Path $stagingDir "bin\jormungandr.bat"),
    (Join-Path $stagingDir "bin\idea.properties"),
    (Join-Path $stagingDir "bin\idea64.exe.vmoptions"),
    (Join-Path $stagingDir "bin\idea.exe"),
    (Join-Path $stagingDir "bin\idea.exe.vmoptions"),
    (Join-Path $stagingDir "bin\jormungandr64.exe"),
    (Join-Path $stagingDir "lib\jormungandr-bootstrap.jar")
)
foreach ($cf in $criticalFiles) {
    if (-not (Test-Path $cf)) {
        Write-Error "CRITICAL: Required distribution file missing after patching: $cf"
    }
}
Write-Host "[OK] All critical launch and branding files verified successfully in staging!" -ForegroundColor Green

# Compress with 7-Zip (fast multi-threaded deflate)
$portableZip = Join-Path $distDir "Jormungandr-$tag-windows-x64.zip"
$threadCount = [Math]::Min([Environment]::ProcessorCount, 16)
Write-Host "Compressing standalone portable package to $portableZip ($threadCount threads, fast deflate)..." -ForegroundColor Yellow

# Archive from stagingRoot so the archive contains Jormungandr-$tag/... folder
Push-Location $stagingRoot
try {
    $relZip = "..\Jormungandr-$tag-windows-x64.zip"
    & 7z a -tzip -mx=3 -mmt=$threadCount $relZip "Jormungandr-$tag"
} finally {
    Pop-Location
}

if (Test-Path $portableZip) {
    $zipSize = [math]::Round((Get-Item $portableZip).Length / 1MB, 2)
    Write-Host "[OK] Standalone portable package created successfully: $portableZip ($zipSize MB)" -ForegroundColor Green
} else {
    Write-Error "Failed to produce $portableZip"
}

# 3. Smart Delta Patch Package
$baselineZip = Join-Path $ProjectRoot "build\baseline\Jormungandr-v1.3.0-windows-x64.zip"
$fromVer = "1.3.0"
if (-not (Test-Path $baselineZip)) {
    $baselineZip = Join-Path $ProjectRoot "build\baseline\Jormungandr-v1.2.0-windows-x64.zip"
    $fromVer = "1.2.0"
}
if (Test-Path $baselineZip) {
    $toVer = $tag.TrimStart('v')
    Write-Host "Generating Smart Delta Patch against v$fromVer baseline..." -ForegroundColor Yellow
    & "$ProjectRoot\tools\generate_patch.ps1" -BaselineZip $baselineZip -TargetDir $stagingDir -FromVersion $fromVer -ToVersion $toVer -OutputDir $distDir
}

# 4. Turnkey Windows Installer (.exe via Inno Setup)
Write-Host "Compiling Windows Installer executable (.exe)..." -ForegroundColor Yellow
$installerVersion = $tag.TrimStart('v')
& "$ProjectRoot\tools\build_installer.ps1" -SourceDir $stagingDir -Version $installerVersion -OutputDir $distDir

$installerExe = Join-Path $distDir "Jormungandr-Setup-v$installerVersion-x64.exe"
if (Test-Path $installerExe) {
    $exeSize = [math]::Round((Get-Item $installerExe).Length / 1MB, 2)
    Write-Host "[OK] Turnkey Windows installer created successfully: $installerExe ($exeSize MB)" -ForegroundColor Green
} else {
    Write-Error "Failed to produce Windows installer $installerExe"
}

# Clean staging directory to reclaim disk space
Remove-Item -Path $stagingRoot -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Packaging Complete for $tag!" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Cyan
