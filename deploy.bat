@echo off
setlocal
rem Publication now reuses a verified Actions artifact; never upload/clobber a local APK.
rem Example: deploy.bat --tag X.Y.Z-stable --run-id RUN_ID --destinations both --status completed --send-for-review
pushd "%~dp0" || exit /b 1
python -m scripts.distribution dispatch %*
set "RESULT=%ERRORLEVEL%"
popd
exit /b %RESULT%
