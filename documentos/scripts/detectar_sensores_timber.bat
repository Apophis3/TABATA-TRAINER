@echo off
echo ========================================
echo DETECTOR DE SENSORES - SOLO TIMBER
echo ========================================
echo.
echo Este script muestra SOLO los mensajes Timber (logging de la app)
echo relacionados con sensores
echo.
echo Presiona Ctrl+C para detener
echo.

REM Buscar ADB
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

echo Limpiando logs anteriores...
"%ADB_PATH%" logcat -c
echo.
echo ========================================
echo INICIANDO DETECCION...
echo ========================================
echo.

REM Filtrar solo mensajes Timber (D = Debug)
"%ADB_PATH%" logcat Timber:D *:S | findstr /i /c:"HR" /c:"Cadence" /c:"ANT+" /c:"BLE" /c:"SensorManager" /c:"WorkoutService" /c:"Connected" /c:"SUCCESS" /c:"deviceName" /c:"SEARCH_TIMEOUT" /c:"Verificando" /c:"Dispositivo" /c:"DeviceNumber" /c:"Callback"