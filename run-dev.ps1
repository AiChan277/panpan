$env:JAVA_HOME = "$env:USERPROFILE\tools\jdk-25.0.5+8"
$env:Path = "$env:JAVA_HOME\bin;$env:USERPROFILE\tools\apache-maven-3.9.16\bin;$env:Path"

Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "Menjalankan Pan-ya Ya via Maven (Java 25)..." -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan

mvn javafx:run @args
