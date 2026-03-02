@echo off
REM pull-restart.bat — Git pull and restart the server if changes were detected.
REM Usage: pull-restart.bat [branch]

cd /d "%~dp0"

REM Get current commit hash
for /f "delims=" %%i in ('git rev-parse HEAD') do set BEFORE=%%i

REM Get branch name
if "%~1"=="" (
    for /f "delims=" %%b in ('git rev-parse --abbrev-ref HEAD') do set BRANCH=%%b
) else (
    set BRANCH=%~1
)

echo [pull-restart] Pulling branch: %BRANCH% ...
git pull origin %BRANCH%

for /f "delims=" %%i in ('git rev-parse HEAD') do set AFTER=%%i

if "%BEFORE%"=="%AFTER%" (
    echo [pull-restart] No changes — server not restarted.
    exit /b 0
)

echo [pull-restart] Changes detected. Restarting server...

REM Find and kill process on port 4567
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":4567" ^| findstr "LISTENING"') do (
    echo [pull-restart] Stopping old server ^(PID %%p^)...
    taskkill /PID %%p /F >nul 2>&1
    timeout /t 2 /nobreak >nul
)

echo [pull-restart] Compiling and starting server...
start "WebRecorder" mvn compile exec:java
echo [pull-restart] Server started in new window.
