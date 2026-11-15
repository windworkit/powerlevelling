@echo off
rem Launches Minecraft with the mod loaded. Output goes to tools\logs\run.log so Claude can read it.
cd /d "%~dp0"
if not exist logs mkdir logs
call _findjava.bat
cd ..
echo ==== runClient started %DATE% %TIME% (JAVA_HOME=%JAVA_HOME%) ==== > tools\logs\run.log
call gradlew.bat runClient --console=plain >> tools\logs\run.log 2>&1
echo RUN EXIT CODE: %ERRORLEVEL% >> tools\logs\run.log
