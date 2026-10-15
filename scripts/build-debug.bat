@echo off
REM Builds the debug APK. Run from the repository root or pass -p.
setlocal
set ROOT=%~dp0..
if not exist "%ROOT%\.cache\bootstraps\bootstrap-aarch64.zip" (
  echo Fetching Termux bootstraps...
  powershell -ExecutionPolicy Bypass -File "%ROOT%\scripts\fetch-bootstraps.ps1"
)
gradle -p "%ROOT%" :app:assembleDebug --no-build-cache %*
