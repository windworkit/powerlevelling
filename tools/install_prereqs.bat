@echo off
rem Installs Java 21 (Eclipse Temurin) and IntelliJ IDEA Community using winget.
rem Output goes to tools\logs\install.log so Claude can read it.
cd /d "%~dp0"
if not exist logs mkdir logs
set LOG=logs\install.log
echo ==== Install started %DATE% %TIME% ==== > %LOG%
echo Installing Java 21 (Temurin)... (a Windows admin prompt may appear - click Yes)
winget install --id EclipseAdoptium.Temurin.21.JDK -e --silent --accept-package-agreements --accept-source-agreements >> %LOG% 2>&1
echo Java exit code: %ERRORLEVEL% >> %LOG%
echo Installing IntelliJ IDEA Community... (a Windows admin prompt may appear - click Yes)
winget install --id JetBrains.IntelliJIDEA.Community -e --silent --accept-package-agreements --accept-source-agreements >> %LOG% 2>&1
echo IntelliJ exit code: %ERRORLEVEL% >> %LOG%
echo ==== Install finished %DATE% %TIME% ==== >> %LOG%
echo Done. You can close this window.
timeout /t 15
