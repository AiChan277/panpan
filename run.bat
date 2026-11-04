@echo off
setlocal
echo ===================================================
echo Membuka Pan-ya Ya...
echo ===================================================

if exist "target\dist\Pan-ya Ya\Pan-ya Ya.exe" (
    start "" "target\dist\Pan-ya Ya\Pan-ya Ya.exe" %*
) else (
    "target\runtime\bin\java.exe" -cp "target\panya-ya-1.0.0.jar;target\libs\*" app.panyaya.PanyaLauncher %*
)
endlocal
