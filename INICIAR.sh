#!/bin/sh
# RankUP Supremo - inicialização em Linux/macOS (hospedagens VPS).
# Requer Java 25 instalado (a pasta "java" incluída é só para Windows).
cd "$(dirname "$0")"

RAM_MIN=2G
RAM_MAX=4G

exec java -Xms$RAM_MIN -Xmx$RAM_MAX -Dfile.encoding=UTF-8 \
  -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+ParallelRefProcEnabled -XX:+PerfDisableSharedMem \
  -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1HeapRegionSize=8M -XX:G1HeapWastePercent=5 \
  -XX:G1MaxNewSizePercent=40 -XX:G1MixedGCCountTarget=4 -XX:G1MixedGCLiveThresholdPercent=90 \
  -XX:G1NewSizePercent=30 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:G1ReservePercent=20 \
  -XX:InitiatingHeapOccupancyPercent=15 -XX:MaxGCPauseMillis=200 -XX:MaxTenuringThreshold=1 \
  -XX:SurvivorRatio=32 -jar paper.jar --nogui
