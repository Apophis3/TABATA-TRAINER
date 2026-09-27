@echo off
echo ========================================
echo RESUMEN CLARO DE SENSORES DETECTADOS
echo ========================================
echo.
echo Este script muestra un resumen organizado:
echo.
echo   HR1: [Protocolo] - [Nombre]
echo   HR2: [Protocolo] - [Nombre] o "No detectado"
echo   CAD: [Protocolo] - [Nombre]
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
echo ESPERANDO DETECCION...
echo ========================================
echo.
echo Buscando eventos de conexion...
echo.

REM Filtrar y mostrar eventos clave de forma clara
"%ADB_PATH%" logcat | findstr /i /c:"ANT+ HR: SUCCESS" /c:"ANT+ HR2: SUCCESS" /c:"ANT+ Cadence: SUCCESS" /c:"BLE HR: Conectado" /c:"BLE HR2: Conectado" /c:"BLE Cadence: Conectado" /c:"deviceName" /c:"Connected" /c:"WorkoutService: Conectado" /c:"SEARCH_TIMEOUT"