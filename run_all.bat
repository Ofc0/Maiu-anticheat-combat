@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
title MaiuAntiCheat - Master Control Suite

echo ===============================================================================
echo                🛡️  MAIU COMBAT ANTICHEAT (AI-POWERED)
echo             Full Automated Suite: Setup, Build & Launch System
echo ===============================================================================
echo.

:: 1. Check & Start Ollama Neural Service
echo [1/3] Initializing Neural AI Engine (Ollama)...
tasklist /FI "IMAGENAME eq ollama.exe" 2>NUL | find /I /N "ollama.exe" >NUL
if "%ERRORLEVEL%"=="0" (
    echo       [OK] Ollama daemon is already running on port 11434.
) else (
    echo       [*] Launching Ollama background daemon...
    start /B "" "ollama" serve
    timeout /t 3 /nobreak >nul
)

:: Check if specialized model exists
ollama list | find /I "maiu-combat-ai" >NUL
if "%ERRORLEVEL%"=="0" (
    echo       [OK] Neural model 'maiu-combat-ai' is ready in VRAM.
) else (
    echo       [*] Setting up 'maiu-combat-ai' with Qwen 2.5 3B parameters...
    cd /d "%~dp0\ai"
    call ollama create maiu-combat-ai -f Modelfile
    cd /d "%~dp0"
)
echo.

:: 2. Compile Plugin with Gradle ShadowJar
echo [2/3] Building Plugin with PacketEvents & Neural Arbiter...
cd /d "%~dp0\plugin"
call gradlew.bat shadowJar --no-daemon
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Gradle compilation failed. Please check build logs.
    pause
    exit /b %ERRORLEVEL%
)

cd /d "%~dp0"
if not exist "dist" mkdir "dist"
copy /Y "plugin\build\libs\MaiuCombatAC-2.0.0.jar" "dist\MaiuCombatAC.jar" >nul
copy /Y "plugin\build\libs\MaiuCombatAC-2.0.0.jar" "server\plugins\MaiuCombatAC.jar" >nul
echo       [OK] Successfully built: dist\MaiuCombatAC.jar
echo       [OK] Automatically deployed to server\plugins\MaiuCombatAC.jar
echo.

:: 3. Launch Minecraft Server
echo [3/3] Starting Minecraft Purpur 1.20.4 Server...
echo ===============================================================================
echo  AntiCheat Commands available in console / in-game (OP):
echo    - /maiu status          » Check AI connection & GPU inference latency
echo    - /maiu inspect <user>  » Detailed combat metrics & AI verdict
echo    - /maiu debug <user>    » Real-time live HUD in actionbar
echo    - /maiu bot <user>      » Spawn anti-cheat decoy trap
echo ===============================================================================
echo.

cd /d "%~dp0\server"
java -Xms2G -Xmx4G -jar server.jar --nogui
pause
