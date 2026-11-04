Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "Membuka Pan-ya Ya..." -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan

$exe = "target\dist\Pan-ya Ya\Pan-ya Ya.exe"
if (Test-Path $exe) {
    Start-Process $exe -ArgumentList $args
} else {
    & "target\runtime\bin\java.exe" -cp "target\panya-ya-1.0.0.jar;target\libs\*" app.panyaya.PanyaLauncher @args
}
