@echo off
rem Points JAVA_HOME at an installed JDK 21 (Temurin) even if this window started before the install.
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do set "JAVA_HOME=%%~fD"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
