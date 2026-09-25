# TabataTrainer - Actualización v2

## Cambios incluidos

### 🔊 Sonidos mejorados
- Nuevos archivos de audio con nombres correctos para Android
- Sonidos para: beep, countdown, work, rest, finish, lets_go, start_warmup, pistol, stop_rest

### 📊 Pantalla final mejorada
- Tiempo total de la sesión
- Tiempo total en trabajo (WORK)
- Tiempo total en descanso (REST)
- Tiempo de calentamiento
- Rondas completadas vs total
- Indicador si se completó o se paró antes

### 🔧 Mejoras técnicas
- WorkoutService con integración de sonidos
- Seguimiento de tiempo por fase
- Base de datos actualizada (versión 3)

---

## Instrucciones de instalación

### 1. Archivos de audio
Copia TODOS los archivos de la carpeta `res_raw/` a:
```
app/src/main/res/raw/
```

**IMPORTANTE:** Elimina los archivos antiguos (.ogg) y usa solo los nuevos (.mp3)

### 2. Archivos Kotlin
Reemplaza estos archivos:

| Archivo | Destino |
|---------|---------|
| `SoundPlayer.kt` | `app/src/main/java/com/tuapp/tabatatrainer/util/` |
| `Entities.kt` | `app/src/main/java/com/tuapp/tabatatrainer/data/local/` |
| `AppDatabase.kt` | `app/src/main/java/com/tuapp/tabatatrainer/data/local/` |
| `WorkoutService.kt` | `app/src/main/java/com/tuapp/tabatatrainer/service/` |
| `WorkoutScreen.kt` | `app/src/main/java/com/tuapp/tabatatrainer/ui/workout/` |

### 3. Desinstalar y reinstalar
**IMPORTANTE:** Como la base de datos cambió de versión, debes:

1. Desinstalar la app del teléfono
2. Compilar: `./gradlew clean && ./gradlew assembleDebug`
3. Instalar: `./gradlew installDebug`

---

## Archivos de audio incluidos

| Archivo | Uso |
|---------|-----|
| `beep.mp3` | Beep corto |
| `countdown_beep.mp3` | Cuenta atrás (últimos 3 segundos) |
| `countdown_10.mp3` | Cuenta atrás 10 segundos (disponible para uso futuro) |
| `work.mp3` | Inicio de fase WORK |
| `rest.mp3` | Inicio de fase REST |
| `stop_rest.mp3` | Fin de descanso / siguiente ronda |
| `finish.mp3` | Fin de sesión |
| `lets_go.mp3` | Motivación (disponible para uso futuro) |
| `start_warmup.mp3` | Inicio de calentamiento |
| `pistol.mp3` | Disparo de salida (disponible para uso futuro) |

---

## Problema de cadencia

Si el sensor de cadencia no se detecta, verifica:

1. **El sensor está encendido** y activo (moviendo los pedales)
2. **Bluetooth activado** en el teléfono
3. **Permisos concedidos** (Bluetooth + Ubicación)
4. **El sensor no está conectado** a otra app (Garmin, Strava, etc.)

El sensor de cadencia usa el servicio UUID `00001816` (Cycling Speed and Cadence).
Algunos sensores solo transmiten cuando detectan movimiento.

---

## Próximos pasos sugeridos

1. [ ] Añadir selección manual de sensores
2. [ ] Historial de entrenamientos
3. [ ] Gráficas de progreso
4. [ ] Exportar a GPX/TCX
5. [ ] ANT+ (requiere SDK especial)
