# TabataTrainer v3 - Actualización

## Cambios incluidos

### 🔊 Lógica de sonidos corregida

| Fase | Momento | Sonido |
|------|---------|--------|
| Calentamiento | Inicio | `start_warmup.mp3` (14 seg) |
| Work (W1) | Inicio primera ronda | `pistol.mp3` |
| Work (W2, W3...) | Inicio siguientes rondas | `lets_go.mp3` |
| Work | Mitad (si ≥20 seg) | `beep.mp3` |
| Work | Últimos 3, 2, 1 seg | `countdown.mp3` |
| Rest | Inicio | `stop_rest.mp3` |
| Rest | Últimos 3, 2, 1 seg | `countdown.mp3` |
| Finish | Fin de sesión | `finish_session.wav` |

### ⚙️ Configuración por defecto
- Calentamiento: **14 segundos** (duración de start_warmup.mp3)
- Trabajo: 20 segundos
- Descanso: 10 segundos
- Rondas: 8

### 📱 Nombre de la app
- Cambiado a: **"Tabata Trainer S. CELIS"**

### 🚴 Sensores
- HR tiene prioridad (se conecta primero)
- Cadencia inicia 2 segundos después para evitar conflictos BLE

---

## Instrucciones de instalación

### 1. Reemplazar archivos Kotlin

| Archivo | Destino |
|---------|---------|
| `ConfigScreen.kt` | `app/src/main/java/com/tuapp/tabatatrainer/ui/config/` |
| `WorkoutService.kt` | `app/src/main/java/com/tuapp/tabatatrainer/service/` |
| `SoundPlayer.kt` | `app/src/main/java/com/tuapp/tabatatrainer/util/` |

### 2. Reemplazar strings.xml

| Archivo | Destino |
|---------|---------|
| `strings.xml` | `app/src/main/res/values/` |

### 3. Verificar archivos de audio

Asegúrate de tener estos archivos en `app/src/main/res/raw/`:
- `beep.mp3`
- `countdown.mp3`
- `countdown_10.mp3`
- `finish_session.wav`
- `lets_go.mp3`
- `pistol.mp3`
- `start_warmup.mp3`
- `stop_rest.mp3`

### 4. Compilar e instalar

```bash
./gradlew clean
./gradlew assembleDebug
./gradlew installDebug
```

---

## Notas sobre la cadencia

Si el sensor de cadencia no se detecta:

1. **El sensor debe estar activo** - Muchos sensores de cadencia solo transmiten cuando detectan movimiento (pedaleo)
2. **No conectado a otra app** - Desconecta de Garmin Connect, Strava, etc.
3. **Bluetooth activo** - Verifica que BT esté encendido
4. **Delay de 2 segundos** - El escaneo de cadencia inicia 2 seg después del HR para evitar conflictos

El sensor HR tiene prioridad. Si solo puede conectarse uno, será el HR.
