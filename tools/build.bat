@echo off
rem Builds the mod. Output goes to tools\logs\build.log so Claude can read it.
cd /d "%~dp0"
if not exist logs mkdir logs
call _findjava.bat
cd ..
echo ==== Build started %DATE% %TIME% (JAVA_HOME=%JAVA_HOME%) ==== > tools\logs\build.log
call gradlew.bat build --console=plain >> tools\logs\build.log 2>&1
echo BUILD EXIT CODE: %ERRORLEVEL% >> tools\logs\build.log
echo Build finished. Exit code %ERRORLEVEL%.
timeout /t 10
