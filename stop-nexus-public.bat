@echo off
rem NEXUS NFV - stop the Cloudflare Quick Tunnel started by start-nexus-public.bat.
rem Docker containers and the MySQL data are NOT stopped or deleted.
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\nexus-public.ps1" stop
echo.
echo To stop the application too (data is kept):  docker compose --profile lab stop
pause
