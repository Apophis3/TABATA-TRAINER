@echo off
echo ========================================
echo DETECTOR DE SENSORES - SOLO APP
echo ========================================
echo.
echo Este script muestra SOLO los logs de la app TabataTrainer
echo relacionados con sensores (HR1, HR2, CAD)
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
echo Filtrando SOLO logs de: com.tuapp.tabatatrainer
echo.
echo Buscando:
echo   - HR1 (Frecuencia Cardiaca 1)
echo   - HR2 (Frecuencia Cardiaca 2)  
echo   - CAD (Cadencia)
echo   - Protocolo: ANT+ o BLE
echo.
echo ========================================
echo.

REM Filtrar por package name y luego por palabras clave de sensores
"%ADB_PATH%" logcat -s "com.tuapp.tabatatrainer:*" | findstr /i /c:"HR" /c:"Cadence" /c:"ANT+" /c:"BLE" /c:"SensorManager" /c:"WorkoutService" /c:"Connected" /c:"SUCCESS" /c:"deviceName" /c:"SEARCH_TIMEOUT" /c:"Verificando" /c:"Dispositivo conectado" /c:"DeviceNumber" /c:"Callback recibido"