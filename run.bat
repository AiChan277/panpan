@echo off
rem Starts Pan-ya Ya without building it. All logic is in run.ps1 (next to this file):
rem local build if there is one, otherwise the ready-made portable app (downloaded once).
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1" %*
if errorlevel 1 (
    echo.
    pause
)
endlocal
