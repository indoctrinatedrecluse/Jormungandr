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
try {
    # 2. Inspect patch-plan.json and snapshot files for backup
    $patchPlanFile = Join-Path $StagingDir "patch-plan.json"
    if (Test-Path $patchPlanFile) {
        Write-Host "Loading patch plan: $patchPlanFile" -ForegroundColor Green
        try {
            $plan = Get-Content $patchPlanFile -Raw | ConvertFrom-Json
            
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
    }

    # 3. Copy/overwrite new and modified files
    $filesDir = Join-Path $StagingDir "files"
    if (Test-Path $filesDir) {
        Write-Host "Applying delta file updates to $TargetDir..." -ForegroundColor Yellow
        Copy-Item -Path "$filesDir\*" -Destination $TargetDir -Recurse -Force
        Write-Host "[+] Patch files successfully applied!" -ForegroundColor Green
    } else {
        # Fallback: if files were unpacked directly in StagingDir (excluding patch-plan.json and backup)
        Get-ChildItem -Path $StagingDir | Where-Object { $_.Name -ne "patch-plan.json" -and $_.Name -ne "backup" } | ForEach-Object {
            Copy-Item -Path $_.FullName -Destination (Join-Path $TargetDir $_.Name) -Recurse -Force
        }
        Write-Host "[+] Patch applied successfully!" -ForegroundColor Green
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
    Write-Host "Update completed successfully!" -ForegroundColor Green
} else {
    Write-Host "Update failed but system was safely rolled back." -ForegroundColor Yellow
}
