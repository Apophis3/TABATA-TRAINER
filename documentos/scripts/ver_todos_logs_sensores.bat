@echo off
echo ========================================
echo VER TODOS LOS LOGS DE SENSORES
echo ========================================
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

REM Verificar conexion
echo Verificando conexion ADB...
"%ADB_PATH%" devices | findstr /i "device" >nul
if errorlevel 1 (
    echo ERROR: No hay dispositivos conectados.
    echo Conecta el dispositivo por USB o WiFi primero.
    echo.
    pause
    exit /b 1
)

echo Dispositivo conectado: OK
echo.

echo Limpiando logs anteriores...
"%ADB_PATH%" logcat -c
echo.
echo ========================================
echo MOSTRANDO TODOS LOS LOGS DE LA APP...
echo ========================================
echo.
echo Este script muestra TODOS los logs de la app
echo sin filtrar. Deberias ver mensajes cuando se
echo detectan sensores.
echo.
echo Presiona Ctrl+C para detener
echo.
echo ========================================
echo.

REM Mostrar logs de las clases de la app - Timber usa nombres de clases como tags
REM Buscar por tags específicos de las clases de la app
"%ADB_PATH%" logcat SensorManager:D WorkoutService:D ConfigViewModel:D *:S | findstr /i /c:"SensorManager" /c:"WorkoutService" /c:"ConfigViewModel" /c:"HR" /c:"Cadence" /c:"ANT+" /c:"BLE" /c:"SUCCESS" /c:"Conectado" /c:"Connected" /c:"Dispositivo" /c:"deviceName" /c:"DeviceNumber" /c:"❤️" /c:"🚴" /c:"✅" /c:"🔌"
