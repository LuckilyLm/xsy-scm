@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Test-Label60x40.ps1" -Print
set SCALE_LABEL_EXIT=%errorlevel%
pause
exit /b %SCALE_LABEL_EXIT%
