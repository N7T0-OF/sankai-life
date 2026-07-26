@echo off
REM ===========================================================================
REM  SANKAI LIFE - installe le dernier APK sur un telephone branche en USB.
REM  Active d'abord le "Debogage USB" dans les options developpeur du telephone.
REM ===========================================================================
title Sankai Life - Installation sur telephone
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\04-installer-telephone.ps1"
echo.
echo ---------------------------------------------------------------------------
pause
