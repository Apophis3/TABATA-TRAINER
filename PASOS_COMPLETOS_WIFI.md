# Pasos Completos para Conectar por WiFi con ANT+

## Problema
El móvil está conectado al USB del ANT+, no puedes conectarlo al PC para ver Logcat.

## Solución Paso a Paso

### FASE 1: Habilitar Depuración WiFi (Solo una vez, o cuando se reinicie el móvil)

1. **Conecta el móvil al PC por USB** (sin ANT+ conectado)
2. **En el móvil:**
   - Ve a: **Configuración → Opciones de desarrollador**
   - Activa: **Depuración USB**
   - Activa: **Depuración por WiFi** (si está disponible)
3. **En el PC, ejecuta:**
   ```powershell
   .\habilitar_wifi_debug.bat
   ```
   - Debería aparecer: `restarting in TCP mode port: 5555`
4. **Desconecta el USB** del PC
5. **Conecta el ANT+** al USB del móvil

### FASE 2: Conectar por WiFi

1. **Asegúrate de que el móvil y PC estén en la misma red WiFi**
2. **Obtén la IP del móvil:**
   - En el móvil: **Configuración → WiFi → Toca en tu red WiFi**
   - Busca **"Dirección IP"** (ejemplo: `192.168.1.26`)
3. **En el PC, ejecuta:**
   ```powershell
   .\conectar_adb_wifi_rapido.bat
   ```
   - Debería aparecer: `connected to 192.168.1.26:5555`

### FASE 3: Ver los Logs

Ahora puedes ejecutar:

```powershell
# Solo logs de HR2
.\ver_logcat_hr2_wifi.bat

# Todos los sensores
.\ver_logcat_completo.bat
```

## Solución de Problemas

### El icono de WiFi no aparece en Android Studio
- **Solución:** Usa los scripts `.bat` directamente desde PowerShell
- El icono de WiFi solo aparece si el dispositivo está conectado por USB
- Los scripts funcionan igual de bien

### Error: "cannot connect to 192.168.1.26:5555"
- **Causa:** El puerto 5555 no está habilitado
- **Solución:** Ejecuta `.\habilitar_wifi_debug.bat` con el USB conectado primero

### Error: "ADB no encontrado"
- Los scripts buscan ADB automáticamente
- Si no lo encuentra, busca `adb.exe` en: `%LOCALAPPDATA%\Android\Sdk\platform-tools\`

### No aparecen logs
- Verifica que hayas ejecutado `.\conectar_adb_wifi_rapido.bat` primero
- Verifica que el dispositivo aparezca en `adb devices`
- Prueba con `.\ver_logcat_completo.bat` para ver todos los logs

## Comandos Rápidos

```powershell
# 1. Habilitar WiFi (con USB conectado)
.\habilitar_wifi_debug.bat

# 2. Conectar por WiFi (con ANT+ conectado)
.\conectar_adb_wifi_rapido.bat

# 3. Ver logs HR2
.\ver_logcat_hr2_wifi.bat
```

## Qué buscar en los logs de HR2

- `❤️2 SensorManager: createHeartRateFlow2() iniciado` - HR2 se inicia
- `❤️2 ANT+ HR2: Callback recibido` - HR2 detecta dispositivo
- `❤️2 HR2: ✅ Dispositivo diferente detectado` - HR2 se conecta ✅
- `❤️2 ANT+ HR2: Mismo dispositivo que HR1` - HR2 rechazado ❌
- `❤️2 ANT+ HR2 Data recibido: X bpm` - HR2 recibiendo datos ✅