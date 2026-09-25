@echo off
echo ========================================
echo CONECTAR ADB POR WIFI
echo ========================================
echo.
echo OPCION 1: Si puedes conectar el movil por USB una vez
echo ----------------------------------------
echo 1. Conecta el movil al PC por USB (sin ANT+)
echo 2. En el movil: Configuracion ^> Opciones de desarrollador ^> Depuracion por WiFi ^> Activar
echo 3. Ejecuta: adb tcpip 5555
echo 4. Desconecta el USB y conecta el ANT+
echo 5. Ejecuta este script
echo.
echo OPCION 2: Si NO puedes conectar por USB
echo ----------------------------------------
echo 1. En el movil: Configuracion ^> WiFi ^> Toca en tu red WiFi conectada
echo 2. Busca "Direccion IP" (ejemplo: 192.168.1.100)
echo 3. Anota esa IP
echo 4. Ejecuta este script e introduce la IP
echo.
echo.
set /p IP="Introduce la IP del dispositivo (ejemplo: 192.168.1.26): "
echo.
echo Conectando a %IP%:5555...
adb connect %IP%:5555
echo.
echo Verificando conexion...
adb devices
echo.
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo CONEXION EXITOSA!
    echo ========================================
    echo Ahora puedes ejecutar:
    echo   - ver_logcat_hr2_wifi.bat
    echo   - ver_logcat_completo.bat
) else (
    echo.
    echo ========================================
    echo ERROR EN LA CONEXION
    echo ========================================
    echo Verifica que:
    echo   1. El movil y PC estan en la misma red WiFi
    echo   2. La IP es correcta
    echo   3. La depuracion por WiFi esta activada
)
echo.
pause