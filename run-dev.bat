@echo off
setlocal
set "JAVA_HOME=%USERPROFILE%\tools\jdk-25.0.5+8"
set "PATH=%USERPROFILE%\tools\jdk-25.0.5+8\bin;%USERPROFILE%\tools\apache-maven-3.9.16\bin;%PATH%"

echo ===================================================
echo Menjalankan Pan-ya Ya via Maven (Java 25)
echo ===================================================

mvn javafx:run %*
endlocal
