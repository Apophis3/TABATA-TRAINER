@echo off
setlocal EnableDelayedExpansion
echo ========================================
echo EMPAREJAR ADB POR WIFI (primera vez)
echo ========================================
echo.
echo Solo necesitas hacer esto UNA VEZ por PC.
echo Despues, usa 01_conectar_adb_wifi_rapido.bat directamente.
echo.
echo En el MOVIL:
echo   1. Ajustes ^> Opciones de desarrollador ^> Depuracion inalambrica (ON)
echo   2. Toca "Vincular dispositivo con codigo de vinculacion"
echo   3. Deja abierto el cuadro con IP:puerto y codigo de 6 digitos.
echo.
pause

REM --- Buscar ADB ---
set ADB_PATH=
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
) else if exist "%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe" (
    set ADB_PATH=%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe
) else if exist "%ANDROID_HOME%\platform-tools\adb.exe" (
    set ADB_PATH=%ANDROID_HOME%\platform-tools\adb.exe
) else (
    echo ERROR: ADB no encontrado.
    pause
    exit /b 1
)
echo ADB: %ADB_PATH%
echo.

"%ADB_PATH%" start-server >nul 2>&1

set /p PAIR_TARGET=IP:puerto DEL CUADRO DE EMPAREJAR (ej 192.168.1.30:34337) =
if not defined PAIR_TARGET (
    echo Cancelado.
    pause
    exit /b 1
)

"%ADB_PATH%" pair %PAIR_TARGET%
if !ERRORLEVEL! NEQ 0 (
    echo.
    echo ERROR: fallo el emparejamiento. Verifica IP:puerto y codigo.
    pause
    exit /b 1
)

echo.
echo ========================================
echo EMPAREJADO CORRECTAMENTE
echo ========================================
echo Ahora ejecuta 01_conectar_adb_wifi_rapido.bat para conectar.
echo.
pause
