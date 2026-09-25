# Instrucciones para Depuración WiFi con ANT+ conectado

## Problema
El móvil está conectado al USB del ANT+, por lo que no puedes conectarlo al PC para ver el Logcat.

## Solución: ADB por WiFi

### Paso 1: Habilitar depuración por WiFi en el móvil

**IMPORTANTE:** Primero debes conectar el móvil por USB una vez para habilitar esto.

1. **Conecta el móvil al PC por USB** (sin ANT+)
2. En el móvil: **Configuración → Opciones de desarrollador → Depuración por WiFi**
3. **Activa** la opción "Depuración por WiFi"
4. **Anota la IP y puerto** que aparece (ejemplo: `192.168.1.26:5555`)
5. En el PC, ejecuta: `adb tcpip 5555`
6. **Desconecta el USB** del PC
7. **Conecta el ANT+** al USB del móvil

### Paso 2: Conectar por WiFi desde el PC

1. Ejecuta en PowerShell: `.\conectar_adb_wifi_rapido.bat`
2. Debería aparecer: `connected to 192.168.1.26:5555`
3. Si aparece error, verifica:
   - Móvil y PC en la misma red WiFi
   - Depuración por WiFi activada
   - Firewall no bloqueando puerto 5555

### Paso 3: Ver los logs

Ahora puedes ejecutar:

- **`.\ver_logcat_hr2_wifi.bat`** - Solo logs de HR2
- **`.\ver_logcat_completo.bat`** - Todos los sensores

## Comandos manuales (si prefieres)

```powershell
# Conectar
.\conectar_adb_wifi_rapido.bat

# Ver logs HR2
.\ver_logcat_hr2_wifi.bat

# Ver todos los sensores
.\ver_logcat_completo.bat
```

## Qué buscar en los logs

Busca estos mensajes clave para HR2:

- `❤️2 SensorManager: createHeartRateFlow2() iniciado` - HR2 se inicia
- `❤️2 ANT+ HR2: Callback recibido` - HR2 detecta dispositivo
- `❤️2 HR2: Verificando dispositivo ANT+` - Comparando con HR1
- `❤️2 HR2: ✅ Dispositivo diferente detectado` - HR2 se conecta ✅
- `❤️2 ANT+ HR2: Mismo dispositivo que HR1` - HR2 rechazado ❌
- `❤️2 ANT+ HR2 Data recibido: X bpm` - HR2 recibiendo datos ✅
- `❤️2 WorkoutService: HR2 recibido: X bpm` - Datos llegando a la UI ✅

## Solución de problemas

### Error: "cannot connect to 192.168.1.26:5555"
- Verifica que el móvil y PC estén en la misma red WiFi
- Verifica que la depuración por WiFi esté activada
- Intenta desactivar y reactivar la depuración por WiFi

### Error: "ADB no encontrado"
- Los scripts buscan ADB automáticamente en ubicaciones comunes
- Si no lo encuentra, busca `adb.exe` en tu instalación de Android SDK
- Normalmente está en: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`

### No aparecen logs
- Asegúrate de haber ejecutado primero `.\conectar_adb_wifi_rapido.bat`
- Verifica que el dispositivo aparezca en `adb devices`
- Prueba con `.\ver_logcat_completo.bat` para ver todos los logs