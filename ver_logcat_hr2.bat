@echo off
echo ========================================
echo FILTRANDO LOGCAT POR HR2
echo ========================================
echo.
echo Presiona Ctrl+C para detener
echo.
adb logcat -c
adb logcat | findstr /i "HR2 hr2 SensorManager"