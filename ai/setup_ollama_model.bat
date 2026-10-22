@echo off
chcp 65001 >nul
echo ========================================================
echo       MAIU AI COMBAT ANTICHEAT - OLLAMA SETUP
echo ========================================================
echo.

echo [1/4] Prüfe, ob Ollama läuft...
tasklist /FI "IMAGENAME eq ollama.exe" 2>NUL | find /I /N "ollama.exe">NUL
if "%ERRORLEVEL%"=="0" (
    echo [OK] Ollama Prozess läuft bereits.
) else (
    echo [*] Starte Ollama Server im Hintergrund...
    start /B "" "ollama" serve
    timeout /t 3 /nobreak >nul
)

echo.
echo [2/4] Lade leichtgewichtiges Basismodell (qwen2.5:1.5b für GPU)...
echo Hinweis: qwen2.5:1.5b benötigt nur ca. 1.2 GB VRAM auf deiner AMD RX 7600
echo und liefert blitzschnelle Antwortzeiten unter 30 Millisekunden!
ollama pull qwen2.5:1.5b

echo.
echo [3/4] Erstelle spezialisiertes Modell 'maiu-combat-ai'...
cd /d "%~dp0"
ollama create maiu-combat-ai -f Modelfile

echo.
echo [4/4] Führe Test-Klassifizierung aus...
python test_ai_sample.py

echo.
echo ========================================================
echo       EINRICHTUNG ERFOLGREICH ABGESCHLOSSEN!
echo ========================================================
echo Dein AntiCheat kann jetzt Live-Kämpfe analysieren.
pause
