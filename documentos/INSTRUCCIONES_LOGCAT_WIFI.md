# Cómo ver Logcat con ANT+ conectado (USB ocupado)

## Problema
El móvil está conectado al USB del ANT+, por lo que no puedes conectarlo al PC para ver el Logcat.

## Solución: ADB por WiFi

### Paso 1: Habilitar depuración por WiFi (solo la primera vez)

1. **Conecta el móvil al PC por USB** (sin el ANT+ conectado)
2. En el móvil, ve a: **Configuración → Opciones de desarrollador → Depuración por WiFi**
3. **Activa** la opción "Depuración por WiFi"
4. **Anota la IP y puerto** que aparece (ejemplo: `192.168.1.100:5555`)
5. **Desconecta el USB** del PC

### Paso 2: Conectar por WiFi

1. **Conecta el ANT+ al USB** del móvil
2. En el PC, ejecuta: `conectar_adb_wifi.bat`
3. Introduce la IP que anotaste (ejemplo: `192.168.1.100:5555`)
4. Debería aparecer: `connected to 192.168.1.100:5555`

### Paso 3: Ver los logs

Ahora puedes ejecutar cualquiera de estos scripts:

- **`ver_logcat_hr2_wifi.bat`** - Solo logs de HR2
- **`ver_logcat_completo.bat`** - Todos los sensores
- **`ver_logcat_timber.bat`** - Solo mensajes Timber

## Alternativa: Android Studio

Si usas Android Studio:

1. Conecta el dispositivo por WiFi (paso 1 y 2)
2. En Android Studio, el dispositivo debería aparecer en la lista de dispositivos
3. Abre la pestaña **Logcat**
4. Filtra por: `HR2` o `SensorManager`

## Notas importantes

- El móvil y el PC deben estar en la **misma red WiFi**
- La depuración por WiFi se desactiva al reiniciar el móvil (hay que volver a activarla)
- Si no funciona, verifica que el firewall no esté bloqueando el puerto 5555

## Qué buscar en los logs

Busca estos mensajes clave:

- `❤️2 SensorManager: createHeartRateFlow2() iniciado` - HR2 se inicia
- `❤️2 ANT+ HR2: Callback recibido` - HR2 detecta dispositivo
- `❤️2 HR2: Verificando dispositivo ANT+` - Comparando con HR1
- `❤️2 HR2: ✅ Dispositivo diferente detectado` - HR2 se conecta ✅
- `❤️2 ANT+ HR2: Mismo dispositivo que HR1` - HR2 rechazado ❌
- `❤️2 ANT+ HR2 Data recibido: X bpm` - HR2 recibiendo datos ✅
- `❤️2 WorkoutService: HR2 recibido: X bpm` - Datos llegando a la UI ✅