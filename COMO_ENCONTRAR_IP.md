# Cómo encontrar la IP del dispositivo para ADB WiFi

## El BSSID NO es la IP

El **BSSID** es la dirección MAC del router WiFi, **NO** la IP del dispositivo.

## Métodos para encontrar la IP del dispositivo

### Método 1: Desde el móvil (MÁS FÁCIL)

1. En el móvil, ve a: **Configuración → WiFi**
2. **Toca en la red WiFi** a la que estás conectado
3. Busca **"Dirección IP"** o **"IP address"**
4. Anota esa IP (ejemplo: `192.168.1.100`)
5. Usa esa IP con el puerto `5555` → `192.168.1.100:5555`

### Método 2: Si puedes conectar por USB una vez

1. **Conecta el móvil al PC por USB** (sin ANT+)
2. Ejecuta: `obtener_ip_dispositivo.bat`
3. Te mostrará la IP del dispositivo
4. Desconecta el USB y conecta el ANT+
5. Usa esa IP en `conectar_adb_wifi.bat`

### Método 3: Desde el router/módem

1. Accede a la configuración del router (normalmente `192.168.1.1` o `192.168.0.1`)
2. Busca la lista de dispositivos conectados
3. Encuentra tu móvil (por nombre o MAC)
4. Anota la IP asignada

### Método 4: Usando ADB (si puedes conectar por USB)

```bash
# Conectar por USB primero
adb devices

# Obtener IP
adb shell ip addr show wlan0 | findstr "inet "

# O más simple:
adb shell "ip addr show wlan0 | grep 'inet ' | awk '{print \$2}' | cut -d/ -f1"
```

## Formato correcto

La IP debe tener este formato:
- ✅ `192.168.1.100:5555` (IP:puerto)
- ✅ `192.168.0.50:5555`
- ❌ `192.168.1.26:6379` (puerto incorrecto, debe ser 5555)
- ❌ Solo `192.168.1.100` (falta el puerto)

## Nota importante

El puerto **5555** es el puerto estándar de ADB. Si usas otro puerto, puede que no funcione.

## Si nada funciona

Si no puedes encontrar la IP, puedes:
1. Usar una app de red en el móvil (como "Network Info II")
2. O crear una pantalla de debug en la app que muestre los logs directamente