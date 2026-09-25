@echo off
echo ========================================
echo LOGCAT - SOLO MENSAJES TIMBER (MEJOR FORMATO)
echo ========================================
echo.
echo Presiona Ctrl+C para detener
echo.
adb logcat -c
adb logcat Timber:D *:S