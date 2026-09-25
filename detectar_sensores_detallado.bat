@echo off
echo ========================================
echo DETECTOR DETALLADO DE SENSORES
echo ========================================
echo.
echo Este script muestra informacion detallada de:
echo   - Que sensores se detectan
echo   - Si son ANT+ o BLE
echo   - Nombres de dispositivos
echo   - Estados de conexion
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
echo INICIANDO DETECCION DETALLADA...
echo ========================================
echo.

REM Filtrar por tags relevantes y mostrar en tiempo real
"%ADB_PATH%" logcat -v time | findstr /i /c:"HR" /c:"Cadence" /c:"SensorManager" /c:"WorkoutService" /c:"ANT+" /c:"BLE" /c:"Connected" /c:"Scanning" /c:"SUCCESS" /c:"deviceName" /c:"Callback recibido" /c:"Verificando dispositivo"