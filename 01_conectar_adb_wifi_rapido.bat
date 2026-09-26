@echo off
setlocal EnableDelayedExpansion
echo ========================================
echo CONECTAR ADB POR WIFI (auto mDNS)
echo ========================================
echo.

REM --- Buscar ADB ---
set ADB_PATH=
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
) else if exist "%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe" (
    set ADB_PATH=%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe
) else if exist "%ANDROID_HOME%\platform-tools\adb.exe" (
    set ADB_PATH=%ANDROID_HOME%\platform-tools\adb.exe
) else (
    where adb >nul 2>&1
    if !ERRORLEVEL! EQU 0 (
        set ADB_PATH=adb
    ) else (
        echo ERROR: ADB no encontrado.
        pause
        exit /b 1
    )
)
echo ADB: %ADB_PATH%
echo.

REM --- Arrancar server ---
"%ADB_PATH%" start-server >nul 2>&1

REM --- Si ya hay un dispositivo WiFi conectado, salir OK ---
for /f "tokens=1" %%D in ('""%ADB_PATH%" devices" ^| findstr /R ":.*device$"') do (
    echo Ya conectado: %%D
    "%ADB_PATH%" devices
    echo.
    pause
    exit /b 0
)

REM --- Descubrir por mDNS (Android 11+, Depuracion inalambrica ON) ---
echo Buscando dispositivos por mDNS...
set FOUND=
for /f "tokens=1,2,3" %%A in ('""%ADB_PATH%" mdns services" 2^>nul ^| findstr /C:"_adb-tls-connect._tcp"') do (
    if not defined FOUND set FOUND=%%C
)

if defined FOUND (
    echo Encontrado: !FOUND!
    "%ADB_PATH%" connect !FOUND!
) else (
    echo No se encontro por mDNS. Introduce IP:puerto manualmente.
    echo   ^(Movil: Ajustes ^> Opciones dev ^> Depuracion inalambrica ^> "Direccion IP y puerto"^)
    set /p TARGET=IP:puerto =
    if not defined TARGET (
        echo Cancelado.
        pause
        exit /b 1
    )
    "%ADB_PATH%" connect !TARGET!
)

echo.
echo Verificando...
"%ADB_PATH%" devices
"%ADB_PATH%" devices | findstr /R ":.*device$" >nul
if !ERRORLEVEL! EQU 0 (
    echo.
    echo ========================================
    echo CONEXION EXITOSA
    echo ========================================
) else (
    echo.
    echo ========================================
    echo ERROR EN LA CONEXION
    echo ========================================
    echo   - Depuracion inalambrica activada?
    echo   - Misma red WiFi PC y movil?
    echo   - Primera vez: ejecuta 00_habilitar_wifi_debug.bat para emparejar.
)
echo.
pause
