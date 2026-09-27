@echo off
echo ========================================
echo FILTRANDO LOGCAT POR HR2 (WIFI)
echo ========================================
echo.

REM Buscar ADB en ubicaciones comunes
set ADB_PATH=
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
) else if exist "%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe" (
    set ADB_PATH=%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe
) else if exist "%ANDROID_HOME%\platform-tools\adb.exe" (
    set ADB_PATH=%ANDROID_HOME%\platform-tools\adb.exe
) else (
    set ADB_PATH=adb
)

echo Asegurate de haber ejecutado primero: .\conectar_adb_wifi_rapido.bat
echo.
echo Presiona Ctrl+C para detener
echo.
"%ADB_PATH%" logcat -c
"%ADB_PATH%" logcat | findstr /i "HR2 hr2 SensorManager"