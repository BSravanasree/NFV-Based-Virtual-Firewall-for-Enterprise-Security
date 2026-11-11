@echo off
rem NEXUS NFV - publish the local stack through a free Cloudflare Quick Tunnel.
rem Keep this window open while sharing the link. Stop with stop-nexus-public.bat.
setlocal
cd /d "%~dp0"
title NEXUS NFV public access
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\nexus-public.ps1" start
set EXITCODE=%ERRORLEVEL%
echo.
pause
exit /b %EXITCODE%
