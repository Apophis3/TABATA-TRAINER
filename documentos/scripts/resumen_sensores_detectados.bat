@echo off
setlocal enabledelayedexpansion
echo ========================================
echo DETECTOR DE SENSORES EN TIEMPO REAL
echo ========================================
echo.
echo Muestra sensores detectados en tiempo real
echo Al presionar Ctrl+C veras el resumen final
echo.
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

REM Archivo temporal
set TEMP_FILE=%TEMP%\sensores_%RANDOM%.txt
del "%TEMP_FILE%" 2>nul

echo ========================================
echo DETECTANDO SENSORES...
echo ========================================
echo.
echo Inicia la app y comienza un entrenamiento.
echo Los sensores apareceran aqui cuando se detecten.
echo Presiona Ctrl+C para detener y ver resumen.
echo.
echo ========================================
echo.

REM Capturar logs de las clases de la app en archivo
start /b "" cmd /c ""%ADB_PATH%" logcat SensorManager:D WorkoutService:D ConfigViewModel:D *:S >> "%TEMP_FILE%" 2>&1"

REM Mostrar TODOS los logs de SensorManager y WorkoutService en tiempo real
REM Filtrar para mostrar mensajes relevantes de conexion y datos
"%ADB_PATH%" logcat SensorManager:D WorkoutService:D ConfigViewModel:D *:S | findstr /i /c:"SUCCESS" /c:"Conectando" /c:"Conectado" /c:"Connected" /c:"recibido" /c:"recibida" /c:"HR recibido" /c:"HR2 recibido" /c:"Cadencia recibida" /c:"ANT+ HR" /c:"ANT+ HR2" /c:"ANT+ Cadence" /c:"BLE HR" /c:"BLE Cadence" /c:"Data recibido" /c:"Buscando sensor" /c:"Conectando a sensor" /c:"bpm" /c:"rpm"

REM Cuando se presiona Ctrl+C, continuar aqui
echo.
echo ========================================
echo ANALIZANDO RESULTADOS...
echo ========================================
echo.

REM Detener proceso de logcat
taskkill /F /IM adb.exe >nul 2>&1
timeout /t 1 /nobreak >nul

REM Contar dispositivos - buscar patrones mas flexibles
set HR_ANT=0
set HR_BLE=0
set CAD_ANT=0
set CAD_BLE=0

REM Contar HR ANT+ - buscar "ANT+ HR" o "HR" con "SUCCESS" o "ANT+"
for /f "delims=" %%i in ('findstr /i /c:"ANT+ HR" /c:"ANT+ HR2" "%TEMP_FILE%" 2^>nul ^| findstr /i /c:"SUCCESS" /c:"Conectando" /c:"Connected"') do (
    set /a HR_ANT+=1
)

REM Contar HR BLE - buscar "BLE HR" o "HR" con "Conectado" o "Connected"
for /f "delims=" %%i in ('findstr /i /c:"BLE HR" /c:"BLE HR2" "%TEMP_FILE%" 2^>nul ^| findstr /i /c:"Conectado" /c:"Connected" /c:"Estado de conexion"') do (
    set /a HR_BLE+=1
)

REM Contar CAD ANT+ - buscar "ANT+ Cadence" o "Cadence" con "SUCCESS"
for /f "delims=" %%i in ('findstr /i /c:"ANT+ Cadence" /c:"ANT+ Cad" "%TEMP_FILE%" 2^>nul ^| findstr /i /c:"SUCCESS" /c:"Conectando" /c:"Connected"') do (
    set /a CAD_ANT+=1
)

REM Contar CAD BLE - buscar "BLE Cadence" o "Cadence" con "Conectado"
for /f "delims=" %%i in ('findstr /i /c:"BLE Cadence" /c:"BLE Cad" "%TEMP_FILE%" 2^>nul ^| findstr /i /c:"Conectado" /c:"Connected"') do (
    set /a CAD_BLE+=1
)

set /a TOTAL_HR=%HR_ANT%+%HR_BLE%
set /a TOTAL_CAD=%CAD_ANT%+%CAD_BLE%

echo ========================================
echo RESUMEN DE DISPOSITIVOS DETECTADOS
echo ========================================
echo.
echo HR (Frecuencia Cardiaca):
echo   - ANT+: %HR_ANT% dispositivo(s)
echo   - BLE: %HR_BLE% dispositivo(s)
echo   - TOTAL HR: %TOTAL_HR% dispositivo(s)
echo.
echo CAD (Cadencia):
echo   - ANT+: %CAD_ANT% dispositivo(s)
echo   - BLE: %CAD_BLE% dispositivo(s)
echo   - TOTAL CAD: %TOTAL_CAD% dispositivo(s)
echo.
echo ========================================
echo.

REM Mostrar dispositivos detectados - mostrar mas lineas para debug
echo ULTIMOS EVENTOS DE SENSORES (para debug):
echo.
echo --- HR ---
findstr /i /c:"HR" "%TEMP_FILE%" 2>nul | findstr /i /c:"SUCCESS" /c:"Conectado" /c:"Connected" /c:"deviceName" /c:"DeviceNumber" | findstr /v /c:"WorkoutService: HR recibido" | findstr /v /c:"WorkoutService: HR2 recibido"
echo.
echo --- CAD ---
findstr /i /c:"Cadence" /c:"Cad" "%TEMP_FILE%" 2>nul | findstr /i /c:"SUCCESS" /c:"Conectado" /c:"Connected" /c:"deviceName" /c:"DeviceNumber" | findstr /v /c:"WorkoutService: Cadencia recibida"
echo.

REM Si no hay resultados, mostrar algunos logs para debug
if %TOTAL_HR% equ 0 if %TOTAL_CAD% equ 0 (
    echo.
    echo NOTA: No se encontraron eventos de conexion.
    echo Mostrando ultimos logs de la app para debug:
    echo.
    powershell -Command "Get-Content '%TEMP_FILE%' -Tail 20"
    echo.
)

REM Limpiar
del "%TEMP_FILE%" 2>nul

pause
