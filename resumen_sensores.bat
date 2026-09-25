@echo off
echo ========================================
echo RESUMEN DE SENSORES DETECTADOS
echo ========================================
echo.
echo Este script muestra un resumen claro de:
echo   - HR1: Protocolo y nombre
echo   - HR2: Protocolo y nombre (si se detecta)
echo   - CAD: Protocolo y nombre
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
echo ESPERANDO DETECCION DE SENSORES...
echo ========================================
echo.
echo Buscando eventos de conexion...
echo.

REM Filtrar solo eventos de conexion exitosa
"%ADB_PATH%" logcat | findstr /i /c:"HR: Conectado" /c:"HR2: Conectado" /c:"Cadence: Conectado" /c:"Connected" /c:"SUCCESS" /c:"deviceName" /c:"ANT+" /c:"BLE" /c:"WorkoutService: Conectado"