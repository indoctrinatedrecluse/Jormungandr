@echo off
setlocal
set PID=%1
set TARGET_DIR=%~2
set STAGING_DIR=%~3
set RELAUNCH_CMD=%~4

title Jormungandr Auto-Updater
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0jormungandr-updater.ps1" -PidToWait %PID% -TargetDir "%TARGET_DIR%" -StagingDir "%STAGING_DIR%" -RelaunchCmd "%RELAUNCH_CMD%"
endlocal
