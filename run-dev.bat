@echo off
rem Developer start: compiles and runs the current source with Maven (needs JDK 25 + Maven).
rem To run WITHOUT building, use run.bat instead.
setlocal
cd /d "%~dp0"
if exist "%USERPROFILE%\tools\jdk-25.0.5+8\bin\java.exe" (
    set "JAVA_HOME=%USERPROFILE%\tools\jdk-25.0.5+8"
    set "PATH=%USERPROFILE%\tools\jdk-25.0.5+8\bin;%PATH%"
)
if exist "%USERPROFILE%\tools\apache-maven-3.9.16\bin\mvn.cmd" (
    set "PATH=%USERPROFILE%\tools\apache-maven-3.9.16\bin;%PATH%"
)

echo ===================================================
echo Menjalankan Pan-ya Ya via Maven (Java 25)
echo ===================================================

where mvn >nul 2>nul
if errorlevel 1 (
    echo Maven was not found. Install JDK 25 and Maven 3.9+, or use run.bat to start
    echo the ready-made app without building.
    pause
    exit /b 1
)
call mvn javafx:run %*
if errorlevel 1 pause
endlocal
