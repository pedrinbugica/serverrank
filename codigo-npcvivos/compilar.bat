@echo off
REM Recompila o plugin NpcVivos (requer JDK 25). Desligue o servidor antes.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0compilar.ps1"
pause
