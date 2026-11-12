# Developer start: compiles and runs the current source with Maven (needs JDK 25 + Maven).
# To run WITHOUT building, use run.ps1 / run.bat instead.
$jdk = "$env:USERPROFILE\tools\jdk-25.0.5+8"
$maven = "$env:USERPROFILE\tools\apache-maven-3.9.16"
if (Test-Path "$jdk\bin\java.exe") {
    $env:JAVA_HOME = $jdk
    $env:Path = "$jdk\bin;$env:Path"
}
if (Test-Path "$maven\bin\mvn.cmd") { $env:Path = "$maven\bin;$env:Path" }

Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "Menjalankan Pan-ya Ya via Maven (Java 25)..." -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Host "Maven was not found. Install JDK 25 and Maven 3.9+, or use run.ps1 to start the ready-made app without building." -ForegroundColor Red
    exit 1
}
Push-Location $PSScriptRoot
try { mvn javafx:run @args } finally { Pop-Location }
