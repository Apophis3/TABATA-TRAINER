@echo off
echo ========================================
echo CONTADOR DE DISPOSITIVOS GUARDADOS
echo ========================================
echo.
echo Este script cuenta los dispositivos que estan
echo guardados en la base de datos de la app.
echo NO necesita ejecutar la app.
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

REM Verificar que el dispositivo esta conectado
"%ADB_PATH%" devices | findstr /i "device" >nul
if errorlevel 1 (
    echo ERROR: No se detecto ningun dispositivo conectado.
    echo Conecta el dispositivo por USB o WiFi y vuelve a intentar.
    echo.
    pause
    exit /b 1
)

echo Conectado al dispositivo...
echo.

REM Verificar si la app esta instalada
echo Verificando si la app esta instalada...
"%ADB_PATH%" shell "pm list packages | grep com.tuapp.tabatatrainer" > "%TEMP%\app_check.txt"
findstr /i "com.tuapp.tabatatrainer" "%TEMP%\app_check.txt" >nul
if errorlevel 1 (
    echo.
    echo ERROR: La app TabataTrainer no esta instalada en el dispositivo.
    echo Instala la app primero y vuelve a intentar.
    echo.
    del "%TEMP%\app_check.txt" 2>nul
    pause
    exit /b 1
)
del "%TEMP%\app_check.txt" 2>nul
echo App instalada: OK
echo.

REM Ruta de la base de datos
set DB_PATH=/data/data/com.tuapp.tabatatrainer/databases/tabata_trainer_db

REM Verificar que la base de datos existe
echo Verificando base de datos...
"%ADB_PATH%" shell "test -f %DB_PATH% && echo OK || echo NO_EXISTS" > "%TEMP%\db_check.txt"
findstr /i "OK" "%TEMP%\db_check.txt" >nul
if errorlevel 1 (
    echo.
    echo ========================================
    echo BASE DE DATOS NO ENCONTRADA
    echo ========================================
    echo.
    echo La base de datos no existe todavia.
    echo.
    echo Esto significa que:
    echo   - La app nunca se ha ejecutado, O
    echo   - La app se ejecuto pero nunca se conectaron sensores
    echo.
    echo SOLUCION:
    echo   1. Ejecuta la app al menos una vez
    echo   2. Inicia un entrenamiento y conecta los sensores
    echo   3. Vuelve a ejecutar este script para ver los dispositivos guardados
    echo.
    echo ========================================
    echo.
    del "%TEMP%\db_check.txt" 2>nul
    pause
    exit /b 0
)
del "%TEMP%\db_check.txt" 2>nul
echo Base de datos encontrada: OK
echo.

echo Consultando base de datos...
echo.

REM Consultar dispositivos HR
echo Contando HR (Frecuencia Cardiaca)...
"%ADB_PATH%" shell "sqlite3 %DB_PATH% \"SELECT COUNT(*) FROM device_profiles WHERE sensorType='HEART_RATE';\"" > "%TEMP%\hr_count.txt"
set /p HR_TOTAL=<"%TEMP%\hr_count.txt"

REM Consultar dispositivos CAD
echo Contando CAD (Cadencia)...
"%ADB_PATH%" shell "sqlite3 %DB_PATH% \"SELECT COUNT(*) FROM device_profiles WHERE sensorType='CADENCE';\"" > "%TEMP%\cad_count.txt"
set /p CAD_TOTAL=<"%TEMP%\cad_count.txt"

REM Consultar detalles de HR
echo.
echo ========================================
echo RESUMEN DE DISPOSITIVOS GUARDADOS
echo ========================================
echo.
echo HR (Frecuencia Cardiaca): %HR_TOTAL% dispositivo(s)
echo CAD (Cadencia): %CAD_TOTAL% dispositivo(s)
echo.
echo ========================================
echo.

REM Mostrar detalles de cada dispositivo
echo DETALLES DE DISPOSITIVOS:
echo.

echo --- HR (Frecuencia Cardiaca) ---
"%ADB_PATH%" shell "sqlite3 %DB_PATH% \"SELECT '  - ' || deviceName || ' (' || protocolType || ', conectado ' || connectionCount || ' veces)' FROM device_profiles WHERE sensorType='HEART_RATE' ORDER BY lastConnected DESC;\"" > "%TEMP%\hr_details.txt"
type "%TEMP%\hr_details.txt"
if %HR_TOTAL% equ 0 echo   (ningun dispositivo guardado)
echo.

echo --- CAD (Cadencia) ---
"%ADB_PATH%" shell "sqlite3 %DB_PATH% \"SELECT '  - ' || deviceName || ' (' || protocolType || ', conectado ' || connectionCount || ' veces)' FROM device_profiles WHERE sensorType='CADENCE' ORDER BY lastConnected DESC;\"" > "%TEMP%\cad_details.txt"
type "%TEMP%\cad_details.txt"
if %CAD_TOTAL% equ 0 echo   (ningun dispositivo guardado)
echo.

REM Limpiar archivos temporales
del "%TEMP%\hr_count.txt" 2>nul
del "%TEMP%\cad_count.txt" 2>nul
del "%TEMP%\hr_details.txt" 2>nul
del "%TEMP%\cad_details.txt" 2>nul

echo ========================================
echo.
echo NOTA: Estos son los dispositivos que se han
echo conectado al menos una vez y estan guardados
echo en la base de datos de la app.
echo.
echo Si ves menos dispositivos de los esperados,
echo puede ser que algunos sensores nunca se hayan
echo conectado o que las pilas esten agotadas.
echo.
echo ========================================
echo.
pause
