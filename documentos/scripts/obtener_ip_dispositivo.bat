@echo off
echo ========================================
echo OBTENER IP DEL DISPOSITIVO
echo ========================================
echo.
echo Si puedes conectar el movil por USB una vez (sin ANT+):
echo.
echo 1. Conecta el movil al PC por USB
echo 2. Ejecuta este script
echo 3. Te mostrara la IP del dispositivo
echo.
echo.
echo Conectando por USB primero...
adb devices
echo.
echo Obteniendo IP del dispositivo...
for /f "tokens=2 delims=:" %%a in ('adb shell ip addr show wlan0 ^| findstr "inet "') do (
    set IP=%%a
    set IP=!IP:~1!
    echo.
    echo ========================================
    echo IP DEL DISPOSITIVO: !IP!
    echo ========================================
    echo.
    echo Ahora puedes:
    echo 1. Desconectar el USB
    echo 2. Conectar el ANT+
    echo 3. Ejecutar: conectar_adb_wifi.bat
    echo 4. Usar la IP: !IP!
    goto :end
)
echo.
echo No se pudo obtener la IP. Verifica que el dispositivo este conectado por USB.
:end
pause