@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "REPO_OWNER=rayo-alcantar"
set "REPO_NAME=rayoai_for_android"
set "REPO=%REPO_OWNER%/%REPO_NAME%"

set "SCRIPT_DIR=%~dp0"
set "APK_PATH=%SCRIPT_DIR%app\build\outputs\apk\github\release\app-github-release.apk"
set "NOTES_FILE=%SCRIPT_DIR%release-notes.txt"

where gh >nul 2>&1 || (
    echo ERROR: gh no esta en PATH.
    exit /b 1
)

if not exist "%APK_PATH%" (
    echo ERROR: APK no encontrado.
    exit /b 1
)

if not exist "%NOTES_FILE%" (
    echo ERROR: release-notes.txt no encontrado.
    exit /b 1
)

set "INTERACTIVE="
if "%~1"=="" (
    set "INTERACTIVE=1"
    set /p VERSION=Version ^(ej: 2026.2.13^):
    if "!VERSION!"=="" exit /b 1

    set /p CHANNEL=Canal ^(s=stable, b=beta^):
) else (
    if not "%~3"=="" (
        echo Uso: %~nx0 VERSION stable^|beta
        exit /b 1
    )
    if "%~2"=="" (
        echo Uso: %~nx0 VERSION stable^|beta
        exit /b 1
    )
    set "VERSION=%~1"
    set "CHANNEL=%~2"
)

if /I "%CHANNEL%"=="s" (
    set "CHANNEL_NAME=stable"
    set "RELEASE_FLAG="
) else if /I "%CHANNEL%"=="stable" (
    set "CHANNEL_NAME=stable"
    set "RELEASE_FLAG="
) else if /I "%CHANNEL%"=="b" (
    set "CHANNEL_NAME=beta"
    set "RELEASE_FLAG=--prerelease"
) else if /I "%CHANNEL%"=="beta" (
    set "CHANNEL_NAME=beta"
    set "RELEASE_FLAG=--prerelease"
) else (
    echo Canal invalido.
    exit /b 1
)

set "TAG=%VERSION%-%CHANNEL_NAME%"

gh release view "%TAG%" --repo "%REPO%" >nul 2>&1

if errorlevel 1 (
    gh release create "%TAG%" ^
        "%APK_PATH%" ^
        --repo "%REPO%" ^
        --title "%TAG%" ^
        --notes-file "%NOTES_FILE%" ^
        %RELEASE_FLAG%
) else (
    gh release upload "%TAG%" "%APK_PATH%" --repo "%REPO%" --clobber
    gh release edit "%TAG%" --repo "%REPO%" --notes-file "%NOTES_FILE%"

    if /I "%CHANNEL%"=="b" (
        gh release edit "%TAG%" --repo "%REPO%" --prerelease
    ) else (
        gh release edit "%TAG%" --repo "%REPO%" --latest
    )
)

echo.
echo Release publicada: %TAG%

if /I "%CHANNEL%"=="b" (
    echo Canal: beta / prerelease
) else (
    echo Canal: stable
)

if defined INTERACTIVE pause
endlocal
