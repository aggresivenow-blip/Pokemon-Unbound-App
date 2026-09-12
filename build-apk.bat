@echo off
setlocal
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle was not found. Open this folder in Android Studio and use Build -^> Build APK(s),
  echo or use the included GitHub Actions workflow for a one-click cloud build.
  exit /b 1
)
python tools\prepare_data.py || exit /b 1
gradle --no-daemon :app:assembleDebug || exit /b 1
echo.
echo APK: app\build\outputs\apk\debug\app-debug.apk
