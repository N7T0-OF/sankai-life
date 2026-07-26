@echo off
REM ===========================================================================
REM  SANKAI LIFE - genere l'APK + l'AAB signes pour le Google Play Store.
REM  La cle de signature est creee automatiquement au premier lancement.
REM ===========================================================================
title Sankai Life - Compilation release
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\02-compiler-release.ps1"
echo.
echo ---------------------------------------------------------------------------
pause
