# Scripts para Detectar Sensores

## Problema
Necesitas saber qué sensores se detectan y si son ANT+ o BLE.

## Scripts Disponibles

### 1. `detectar_sensores.bat` - Detección Básica
Muestra todos los eventos relacionados con sensores (HR1, HR2, CAD) y su protocolo.

**Uso:**
```powershell
.\detectar_sensores.bat
```

**Qué muestra:**
- Eventos de conexión ANT+ y BLE
- Nombres de dispositivos
- Estados de conexión
- Errores de detección

### 2. `detectar_sensores_detallado.bat` - Detección Detallada
Muestra información más completa con timestamps y todos los eventos.

**Uso:**
```powershell
.\detectar_sensores_detallado.bat
```

**Qué muestra:**
- Todos los eventos con timestamps
- Callbacks de ANT+
- Escaneos BLE
- Verificaciones de dispositivos
- Estados de conexión detallados

### 3. `resumen_sensores.bat` - Resumen Limpio
Muestra solo los eventos de conexión exitosa, más fácil de leer.

**Uso:**
```powershell
.\resumen_sensores.bat
```

**Qué muestra:**
- Solo conexiones exitosas
- Protocolo (ANT+ o BLE)
- Nombres de dispositivos
- Resumen claro y conciso

## Cómo Interpretar los Logs

### HR1 (Primera Frecuencia Cardiaca)

**ANT+ detectado:**
```
❤️ ANT+ HR: SUCCESS - Conectando...
❤️ ANT+ HR: Conectado: [Nombre del dispositivo]
```

**BLE detectado:**
```
BLE HR: Conectado: [Nombre del dispositivo]
```

### HR2 (Segunda Frecuencia Cardiaca)

**ANT+ detectado:**
```
❤️2 ANT+ HR2: SUCCESS - Conectando...
❤️2 ANT+ HR2: Conectado: [Nombre del dispositivo]
```

**BLE detectado:**
```
BLE HR2: Conectado: [Nombre del dispositivo]
```

**No detectado:**
```
❤️2 ANT+ HR2: Resultado: SEARCH_TIMEOUT
```

### CAD (Cadencia)

**ANT+ detectado:**
```
🚴 ANT+ Cadence: SUCCESS - Conectando...
🚴 ANT+ Cadence: Conectado: [Nombre del dispositivo]
```

**BLE detectado:**
```
BLE Cadence: Conectado: [Nombre del dispositivo]
```

## Ejemplo de Salida Esperada

Si tienes 3 HR y 1 CAD, deberías ver algo como:

```
HR1: ANT+ - "Garmin HR"
HR2: ANT+ - "Polar HR"
HR3: (No detectado o timeout)
CAD: ANT+ - "Garmin Cadence"
```

O si algunos son BLE:

```
HR1: ANT+ - "Garmin HR"
HR2: BLE - "Polar H10"
HR3: (No detectado)
CAD: ANT+ - "Garmin Cadence"
```

## Solución de Problemas

### No aparece ningún sensor
1. Verifica que la app esté ejecutándose
2. Verifica que hayas iniciado un entrenamiento (Tabata o Ruta)
3. Verifica que los sensores estén encendidos y en rango

### Solo aparece HR1
- HR2 puede tardar más en detectarse
- Verifica que tengas un segundo sensor HR diferente
- Busca mensajes de "SEARCH_TIMEOUT" para HR2

### Aparece "Mismo dispositivo que HR1"
- HR2 está detectando el mismo sensor que HR1
- Necesitas un segundo sensor HR diferente
- El filtrado inteligente está funcionando correctamente

## Comandos Rápidos

```powershell
# Ver todos los eventos
.\detectar_sensores.bat

# Ver solo resumen de conexiones
.\resumen_sensores.bat

# Ver información detallada
.\detectar_sensores_detallado.bat
```