@echo off
echo ========================================
echo DETECTOR DE SENSORES ANT+ Y BLE
echo ========================================
echo.
echo Este script muestra todos los sensores detectados
echo y si son ANT+ o BLE
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
echo INICIANDO DETECCION DE SENSORES...
echo ========================================
echo.
echo Buscando:
echo   - HR1 (Frecuencia Cardiaca 1)
echo   - HR2 (Frecuencia Cardiaca 2)
echo   - CAD (Cadencia)
echo.
echo ========================================
echo.

"%ADB_PATH%" logcat | findstr /i /c:"ANT+ HR" /c:"ANT+ HR2" /c:"ANT+ Cadence" /c:"BLE HR" /c:"BLE CAD" /c:"Connected" /c:"SUCCESS" /c:"deviceName" /c:"SensorManager" /c:"WorkoutService" /c:"createHeartRateFlow" /c:"createCadenceFlow" /c:"SEARCH_TIMEOUT" /c:"Verificando dispositivo" /c:"Dispositivo conectado" /c:"DeviceNumber"