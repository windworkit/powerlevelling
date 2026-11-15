@echo off
rem Packs the whole src folder into _sync\src.zip so Claude can read files that are nested too deep to open directly.
rem Double-click this after Cline (or you) changed files, then tell Claude.
cd /d "%~dp0.."
if not exist _sync mkdir _sync
powershell -NoProfile -Command "Compress-Archive -Path 'src' -DestinationPath '_sync\src.zip' -Force"
echo Packed src into _sync\src.zip
timeout /t 5
