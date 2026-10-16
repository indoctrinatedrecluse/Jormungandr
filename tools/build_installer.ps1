param(
    [string]$SourceDir,
    [string]$Version = "1.6.0",
    [string]$OutputDir
)

$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path "$PSScriptRoot\..").Path

Write-Host "=== Building Jörmungandr Windows Installer (v$Version) ===" -ForegroundColor Cyan

# Locate ISCC.exe
$isccCandidates = @(
    "iscc",
    "${env:LOCALAPPDATA}\Programs\Inno Setup 6\ISCC.exe",
    "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe",
    "${env:ProgramFiles}\Inno Setup 6\ISCC.exe"
)

$isccPath = $null
foreach ($candidate in $isccCandidates) {
    if (Get-Command $candidate -ErrorAction SilentlyContinue) {
        $isccPath = (Get-Command $candidate).Source
        break
    } elseif (Test-Path $candidate) {
        $isccPath = $candidate
        break
    }
}

if (-not $isccPath) {
    Write-Warning "Inno Setup Compiler (ISCC.exe) was not found on your system."
    Write-Host "To compile the Windows installer executable (Jormungandr-Setup-v$Version-x64.exe):" -ForegroundColor Yellow
    Write-Host "  1. Install Inno Setup 6: winget install JRSoftware.InnoSetup -e  OR  choco install innosetup"
    Write-Host "  2. Rerun: ./tools/build_installer.ps1"
    exit 0
}

Write-Host "Found Inno Setup Compiler: $isccPath" -ForegroundColor Green

$issFile = Join-Path $ProjectRoot "installer\jormungandr_setup.iss"
if (-not $OutputDir) {
    $OutputDir = Join-Path $ProjectRoot "dist"
}
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

$isccArgs = @($issFile, "/DMyAppVersion=$Version")
if ($SourceDir) {
    $isccArgs += "/DSourceDir=$SourceDir"
}
if ($OutputDir) {
    $isccArgs += "/O$OutputDir"
}

Write-Host "Compiling $issFile with args: $($isccArgs -join ' ')..." -ForegroundColor Cyan
& $isccPath @isccArgs

$compiledExe = Join-Path $ProjectRoot "dist\Jormungandr-Setup-v$Version-x64.exe"
$targetExe = Join-Path $OutputDir "Jormungandr-Setup-v$Version-x64.exe"
if ((Test-Path $compiledExe) -and ($compiledExe -ne $targetExe)) {
    Copy-Item $compiledExe $targetExe -Force
}

Write-Host "=== Installer build finished successfully! Check $OutputDir ===" -ForegroundColor Green
