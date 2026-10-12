param(
    [string]$BaselineZip,
    [string]$BaselineDir,
    [string]$TargetDir,
    [string]$FromVersion = "1.2.0",
    [string]$ToVersion = "1.3.0",
    [string]$OutputDir
)

$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path "$PSScriptRoot\..").Path

if (-not $OutputDir) {
    $OutputDir = Join-Path $ProjectRoot "release-dist"
}
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Generating Smart Delta Patch: v$FromVersion -> v$ToVersion" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$tempDir = Join-Path ([System.IO.Path]::GetTempPath()) "jormungandr_patch_gen_$([System.Guid]::NewGuid().ToString().Substring(0,8))"
$baselineExtractDir = Join-Path $tempDir "baseline"
$patchStagingDir = Join-Path $tempDir "patch_staging"
$patchFilesDir = Join-Path $patchStagingDir "files"

New-Item -ItemType Directory -Path $baselineExtractDir -Force | Out-Null
New-Item -ItemType Directory -Path $patchFilesDir -Force | Out-Null

try {
    # 1. Resolve baseline directory
    if (-not $BaselineDir) {
        if ($BaselineZip -and (Test-Path $BaselineZip)) {
            Write-Host "Extracting baseline archive: $BaselineZip..." -ForegroundColor Yellow
            & 7z x -y -mmt=8 $BaselineZip "-o$baselineExtractDir" | Out-Null
            $innerDir = Get-ChildItem -Path $baselineExtractDir -Directory | Select-Object -First 1
            if ($innerDir -and (Test-Path (Join-Path $innerDir.FullName "bin"))) {
                $BaselineDir = $innerDir.FullName
            } else {
                $BaselineDir = $baselineExtractDir
            }
        } else {
            Write-Error "Baseline archive or directory not found: $BaselineZip"
        }
    }

    Write-Host "Baseline Directory: $BaselineDir" -ForegroundColor Gray
    Write-Host "Target Directory:   $TargetDir" -ForegroundColor Gray

    # 2. Index baseline files with SHA-256
    Write-Host "Computing SHA-256 hashes for baseline files..." -ForegroundColor Yellow
    $baselineHashes = @{}
    Get-ChildItem -Path $BaselineDir -Recurse -File | ForEach-Object {
        $rel = $_.FullName.Substring($BaselineDir.Length).TrimStart('\', '/')
        $hash = (Get-FileHash -Path $_.FullName -Algorithm SHA256).Hash
        $baselineHashes[$rel] = @{
            Hash = $hash
            Size = $_.Length
        }
    }
    Write-Host "  Indexed $($baselineHashes.Count) baseline files." -ForegroundColor Gray

    # 3. Index target files and find additions and modifications
    Write-Host "Scanning target files for additions and modifications..." -ForegroundColor Yellow
    $addedOrModified = @()
    $targetHashes = @{}
    Get-ChildItem -Path $TargetDir -Recurse -File | ForEach-Object {
        $rel = $_.FullName.Substring($TargetDir.Length).TrimStart('\', '/')
        $hash = (Get-FileHash -Path $_.FullName -Algorithm SHA256).Hash
        $targetHashes[$rel] = @{
            Hash = $hash
            Size = $_.Length
        }

        $isDifferent = $false
        if (-not $baselineHashes.ContainsKey($rel)) {
            $isDifferent = $true
            Write-Host "  [+] Added: $rel" -ForegroundColor Green
        } elseif ($baselineHashes[$rel].Hash -ne $hash) {
            $isDifferent = $true
            Write-Host "  [*] Modified: $rel" -ForegroundColor Cyan
        }

        if ($isDifferent) {
            $dest = Join-Path $patchFilesDir $rel
            New-Item -ItemType Directory -Path (Split-Path $dest -Parent) -Force | Out-Null
            Copy-Item -Path $_.FullName -Destination $dest -Force
            $addedOrModified += @{
                path = $rel.Replace('\', '/')
                sha256 = $hash
                size = $_.Length
            }
        }
    }

    # 4. Find deleted files
    $deletions = @()
    foreach ($rel in $baselineHashes.Keys) {
        if (-not $targetHashes.ContainsKey($rel)) {
            Write-Host "  [-] Deleted: $rel" -ForegroundColor Red
            $deletions += $rel.Replace('\', '/')
        }
    }

    Write-Host "------------------------------------------------------------" -ForegroundColor DarkGray
    Write-Host "Delta Summary:" -ForegroundColor Yellow
    Write-Host "  Added/Modified files: $($addedOrModified.Count)" -ForegroundColor Green
    Write-Host "  Deleted files:        $($deletions.Count)" -ForegroundColor Red
    Write-Host "------------------------------------------------------------" -ForegroundColor DarkGray

    # 5. Write patch-plan.json
    $plan = @{
        formatVersion = 1
        fromVersion = $FromVersion
        toVersion = $ToVersion
        createdAt = (Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ")
        files = $addedOrModified
        deletions = $deletions
    }
    $planJson = $plan | ConvertTo-Json -Depth 5
    Set-Content -Path (Join-Path $patchStagingDir "patch-plan.json") -Value $planJson -Encoding UTF8

    # 6. Compress patch package into zip
    $patchZipName = "Jormungandr-Patch-v$FromVersion-to-v$ToVersion.zip"
    $patchZipPath = Join-Path $OutputDir $patchZipName
    if (Test-Path $patchZipPath) {
        Remove-Item $patchZipPath -Force
    }

    Write-Host "Compressing smart delta patch package to $patchZipPath..." -ForegroundColor Yellow
    Push-Location $patchStagingDir
    try {
        & 7z a -tzip -mx=7 -mmt=8 $patchZipPath "*" | Out-Null
    } finally {
        Pop-Location
    }

    if (Test-Path $patchZipPath) {
        $patchSizeMB = [math]::Round((Get-Item $patchZipPath).Length / 1MB, 2)
        Write-Host "[OK] Smart Delta Patch created successfully: $patchZipPath ($patchSizeMB MB)" -ForegroundColor Green
    } else {
        Write-Error "Failed to produce delta patch: $patchZipPath"
    }
} finally {
    Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
}
