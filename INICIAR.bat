@echo off
chcp 65001 >nul
title RankUP Supremo - Servidor Minecraft
cd /d "%~dp0"

REM ================================================================
REM   Memoria RAM do servidor. 4G atende bem ate ~40 jogadores.
REM   Nunca coloque mais do que a RAM livre do seu computador.
REM ================================================================
set RAM_MIN=2G
set RAM_MAX=4G

REM ================================================================
REM   Reiniciar sozinho quando o servidor cair ou for desligado?
REM   sim = reinicia automaticamente (bom para deixar 24h ligado)
REM   nao = desliga de vez quando voce digitar "stop"
REM ================================================================
set AUTO_REINICIAR=nao

set "JAVA=%~dp0java\bin\java.exe"
if not exist "%JAVA%" (
    echo.
    echo  [ERRO] O Java nao foi encontrado na pasta "java".
    echo  Baixe e instale o Java 25: https://adoptium.net/temurin/releases/?version=25
    echo.
    pause
    exit /b 1
)

:iniciar
cls
echo.
echo   ===================================================
echo           R A N K U P   S U P R E M O
echo   ===================================================
echo     Para entrar: Minecraft ^> Multijogador ^> localhost
echo     Para desligar com seguranca: digite  stop
echo   ===================================================
echo.

"%JAVA%" -Xms%RAM_MIN% -Xmx%RAM_MAX% -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+ParallelRefProcEnabled -XX:+PerfDisableSharedMem -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1HeapRegionSize=8M -XX:G1HeapWastePercent=5 -XX:G1MaxNewSizePercent=40 -XX:G1MixedGCCountTarget=4 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1NewSizePercent=30 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=15 -XX:MaxGCPauseMillis=200 -XX:MaxTenuringThreshold=1 -XX:SurvivorRatio=32 -jar paper.jar --nogui

echo.
if /i "%AUTO_REINICIAR%"=="sim" (
    echo  O servidor foi desligado. Reiniciando em 10 segundos...
    echo  Para cancelar, feche esta janela.
    timeout /t 10
    goto iniciar
)
echo  O servidor foi desligado.
pause
