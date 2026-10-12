param(
    [int]$PidToWait,
    [string]$TargetDir,
    [string]$StagingDir,
    [string]$RelaunchCmd
)

$ErrorActionPreference = "Continue"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Jörmungandr Smart Delta Auto-Updater" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Wait for parent IDE process to terminate
if ($PidToWait -gt 0) {
    Write-Host "Waiting for Jörmungandr (PID: $PidToWait) to release files..." -ForegroundColor Yellow
    $maxWaitSec = 45
    $waited = 0
    while ($waited -lt $maxWaitSec) {
        $proc = Get-Process -Id $PidToWait -ErrorAction SilentlyContinue
        if (-not $proc) {
            break
        }
        Start-Sleep -Milliseconds 500
        $waited += 0.5
    }
    # Safety delay for Windows OS to release all file handles
    Start-Sleep -Seconds 1
}

$backupDir = Join-Path $StagingDir "backup"
New-Item -ItemType Directory -Path $backupDir -Force | Out-Null

$updateSucceeded = $true
$newVersion = $null

try {
    # 2. Inspect patch-plan.json (Delta Patch workflow)
    $patchPlanFile = Join-Path $StagingDir "patch-plan.json"
    if (Test-Path $patchPlanFile) {
        Write-Host "Loading patch plan: $patchPlanFile" -ForegroundColor Green
        try {
            $plan = Get-Content $patchPlanFile -Raw | ConvertFrom-Json
            $newVersion = $plan.toVersion
            
            # Snapshot files scheduled for deletion or modification
            Write-Host "Creating pre-update backup snapshot..." -ForegroundColor DarkGray
            if ($plan.files) {
                foreach ($f in $plan.files) {
                    $rel = $f.path
                    $origPath = Join-Path $TargetDir $rel
                    if (Test-Path $origPath) {
                        $bakDest = Join-Path $backupDir $rel
                        New-Item -ItemType Directory -Path (Split-Path $bakDest -Parent) -Force | Out-Null
                        Copy-Item -Path $origPath -Destination $bakDest -Force -ErrorAction SilentlyContinue
                    }
                }
            }

            # Process deletions
            if ($plan.deletions) {
                foreach ($relDel in $plan.deletions) {
                    $delPath = Join-Path $TargetDir $relDel
                    if (Test-Path $delPath) {
                        $bakDest = Join-Path $backupDir $relDel
                        New-Item -ItemType Directory -Path (Split-Path $bakDest -Parent) -Force | Out-Null
                        Copy-Item -Path $delPath -Destination $bakDest -Force -ErrorAction SilentlyContinue
                        Write-Host "[-] Deleting obsolete file: $relDel" -ForegroundColor DarkGray
                        Remove-Item -Path $delPath -Force -Recurse -ErrorAction SilentlyContinue
                    }
                }
            }
        } catch {
            Write-Warning "Could not parse patch-plan.json: $_"
        }

        # Apply delta file updates
        $filesDir = Join-Path $StagingDir "files"
        if (Test-Path $filesDir) {
            Write-Host "Applying delta file updates to $TargetDir..." -ForegroundColor Yellow
            Copy-Item -Path "$filesDir\*" -Destination $TargetDir -Recurse -Force
            Write-Host "[+] Patch files successfully applied!" -ForegroundColor Green
        }
    } else {
        # Full Portable Distribution extraction workflow
        Write-Host "Detecting full distribution package structure in staging..." -ForegroundColor Yellow
        $innerDir = Get-ChildItem -Path $StagingDir -Directory | Where-Object { 
            ($_.Name -like "Jormungandr-v*") -or (Test-Path (Join-Path $_.FullName "bin")) 
        } | Select-Object -First 1

        $sourceDir = if ($innerDir) { $innerDir.FullName } else { $StagingDir }

        # Detect new version from inner version file or folder name
        $versionMarker = Join-Path $sourceDir "jormungandr-version.txt"
        if (Test-Path $versionMarker) {
            $newVersion = (Get-Content $versionMarker -Raw).Trim()
        } elseif ($innerDir -and ($innerDir.Name -match "Jormungandr-v([0-9.]+)")) {
            $newVersion = $Matches[1]
        }

        Write-Host "Applying full distribution updates from $sourceDir to $TargetDir..." -ForegroundColor Yellow
        Get-ChildItem -Path $sourceDir | Where-Object { 
            $_.Name -ne "backup" -and $_.Name -ne "patch-plan.json" 
        } | ForEach-Object {
            Copy-Item -Path $_.FullName -Destination (Join-Path $TargetDir $_.Name) -Recurse -Force
        }
        Write-Host "[+] Full distribution files successfully applied!" -ForegroundColor Green
    }

    # 3. Synchronize version string metadata files
    if ($newVersion) {
        $cleanVer = $newVersion.TrimStart('v').TrimStart('V')
        Write-Host "Updating version tracking metadata to v$cleanVer..." -ForegroundColor Green
        
        # Write jormungandr-version.txt in IDE root and bin/
        Set-Content -Path (Join-Path $TargetDir "jormungandr-version.txt") -Value "$cleanVer" -Encoding UTF8 -Force
        $binDir = Join-Path $TargetDir "bin"
        if (Test-Path $binDir) {
            Set-Content -Path (Join-Path $binDir "jormungandr-version.txt") -Value "$cleanVer" -Encoding UTF8 -Force
        }

        # Update product-info.json if present
        $productInfo = Join-Path $TargetDir "product-info.json"
        if (Test-Path $productInfo) {
            try {
                $piContent = Get-Content $productInfo -Raw -Encoding UTF8
                $piJson = $piContent | ConvertFrom-Json
                $piJson | Add-Member -MemberType NoteProperty -Name "jormungandrVersion" -Value $cleanVer -Force
                $piUpdated = $piJson | ConvertTo-Json -Depth 10
                Set-Content -Path $productInfo -Value $piUpdated -Encoding UTF8 -Force
            } catch {
                Write-Warning "Could not update product-info.json version metadata: $_"
            }
        }
    }
} catch {
    $updateSucceeded = $false
    Write-Host "[FAIL] Update error encountered: $_" -ForegroundColor Red
    Write-Host "Initiating automatic rollback to preserve system integrity..." -ForegroundColor Yellow
    if (Test-Path $backupDir) {
        Copy-Item -Path "$backupDir\*" -Destination $TargetDir -Recurse -Force -ErrorAction SilentlyContinue
        Write-Host "[OK] Rollback complete. Original version restored." -ForegroundColor Green
    }
}

# 4. Clean up staging directory
Write-Host "Cleaning up update staging..." -ForegroundColor DarkGray
Remove-Item -Path $StagingDir -Recurse -Force -ErrorAction SilentlyContinue

# 5. Relaunch IDE
if ($RelaunchCmd -and (Test-Path $RelaunchCmd)) {
    Write-Host "Relaunching Jörmungandr: $RelaunchCmd" -ForegroundColor Cyan
    Start-Process -FilePath $RelaunchCmd
}

if ($updateSucceeded) {
    Write-Host "Update completed successfully! Jörmungandr is now at v$newVersion" -ForegroundColor Green
} else {
    Write-Host "Update failed but system was safely rolled back." -ForegroundColor Yellow
}
