@echo off
echo ========================================
echo CONECTAR ADB POR WIFI (RAPIDO)
echo ========================================
echo.

REM Buscar ADB en ubicaciones comunes
set ADB_PATH=
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
) else if exist "%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe" (
    set ADB_PATH=%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe
) else if exist "%ANDROID_HOME%\platform-tools\adb.exe" (
    set ADB_PATH=%ANDROID_HOME%\platform-tools\adb.exe
) else (
    REM Intentar usar adb del PATH
    where adb >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        set ADB_PATH=adb
    ) else (
        echo ERROR: ADB no encontrado!
        echo.
        echo Busca adb.exe en tu instalacion de Android SDK y actualiza este script.
        echo Ubicaciones comunes:
        echo   - %LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
        echo   - %USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe
        pause
        exit /b 1
    )
)

echo ADB encontrado en: %ADB_PATH%
echo.
echo Conectando a 192.168.1.28:5555...
"%ADB_PATH%" connect 192.168.1.28:5555
timeout /t 2 /nobreak >nul
echo.
echo Verificando conexion...
"%ADB_PATH%" devices
echo.
"%ADB_PATH%" devices | findstr "192.168.1.28" >nul
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
    echo   2. La depuracion por WiFi esta activada en el movil
    echo   3. El firewall no esta bloqueando el puerto 5555
)
echo.
pause