@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM =====================================
REM Config
REM =====================================
set "REPO_OWNER=rayo-alcantar"
set "REPO_NAME=rayoai_for_android"
set "REPO=%REPO_OWNER%/%REPO_NAME%"

set "APK_PATH=C:\Users\angel_hmv3h04\Documents\git\RayoAI\app\build\outputs\apk\github\release\app-github-release.apk"
set "NOTES_FILE=release-notes.txt"

where gh >nul 2>&1 || (echo ERROR: gh no esta en PATH.& exit /b 1)

if not exist "%APK_PATH%" (
  echo ERROR: APK no encontrado.
  exit /b 1
)

if not exist "%NOTES_FILE%" (
  echo ERROR: release-notes.txt no encontrado.
  exit /b 1
)

REM =====================================
REM Inputs
REM =====================================
set /p VERSION=Version (ej: 2026.2.13): 
if "%VERSION%"=="" exit /b 1

set /p CHANNEL=Canal (s=stable, b=beta): 
if /I "%CHANNEL%"=="s" (
  set "CHANNEL_NAME=stable"
) else if /I "%CHANNEL%"=="b" (
  set "CHANNEL_NAME=beta"
) else (
  echo Canal invalido.
  exit /b 1
)

set "TAG=%VERSION%-%CHANNEL_NAME%"

REM =====================================
REM Crear release en repo publico
REM =====================================
gh release view "%TAG%" --repo "%REPO%" >nul 2>&1
if errorlevel 1 (
  gh release create "%TAG%" "%APK_PATH%" --repo "%REPO%" --title "%TAG%" --notes-file "%NOTES_FILE%"
) else (
  gh release upload "%TAG%" "%APK_PATH%" --repo "%REPO%" --clobber
)

echo.
echo Release publicada: %TAG%
pause
endlocal
