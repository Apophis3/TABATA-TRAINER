@echo off
echo ========================================
echo HABILITAR DEPURACION WIFI
echo ========================================
echo.
echo IMPORTANTE: El dispositivo debe estar conectado por USB
echo.
echo Pasos:
echo 1. Conecta el movil al PC por USB (sin ANT+)
echo 2. En el movil: Configuracion ^> Opciones de desarrollador ^> Depuracion USB ^> Activar
echo 3. Presiona cualquier tecla cuando estes listo...
pause >nul
echo.

REM Buscar ADB
set ADB_PATH=
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
) else if exist "%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe" (
    set ADB_PATH=%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe
) else (
    echo ERROR: ADB no encontrado!
    pause
    exit /b 1
)

echo Verificando dispositivo conectado por USB...
"%ADB_PATH%" devices
echo.
echo Habilitando puerto TCP/IP 5555...
"%ADB_PATH%" tcpip 5555
echo.
echo ========================================
echo LISTO!
echo ========================================
echo.
echo Ahora puedes:
echo 1. Desconectar el USB del PC
echo 2. Conectar el ANT+ al USB del movil
echo 3. Ejecutar: .\conectar_adb_wifi_rapido.bat
echo.
pause