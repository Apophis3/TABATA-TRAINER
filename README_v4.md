# TabataTrainer v4 Update

## Cambios en esta versión

### 🔧 Correcciones

1. **Tabata termina después del último WORK** (sin REST final)
   - El entrenamiento clásico Tabata termina cuando completas el último trabajo
   - Ya no hay descanso innecesario al final

2. **Detección BLE visible en ConfigScreen**
   - Al abrir la app verás el estado de los sensores BLE
   - Indicador verde = conectado
   - Indicador naranja = buscando
   - Indicador gris = no conectado
   - Botón de "refrescar" para reiniciar escaneo

3. **Reconexión BLE entre entrenamientos**
   - Los sensores se reconectan correctamente al iniciar un nuevo Tabata
   - Ya no es necesario reiniciar la app

4. **Sonidos corregidos** (de la versión anterior)
   - WARMUP inicio: start_warmup.mp3
   - WORK 1: pistol.mp3 (disparo de salida)
   - WORK 2+: lets_go.mp3 (motivación)
   - REST inicio: stop_rest.mp3
   - Countdown 3,2,1: countdown.mp3
   - Fin: finish_session.wav

## Archivos a reemplazar

```
app/src/main/java/com/tuapp/tabatatrainer/
├── sensor/
│   └── SensorManager.kt          ← REEMPLAZAR
├── service/
│   └── WorkoutService.kt       ← REEMPLAZAR
├── ui/config/
│   └── ConfigScreen.kt         ← REEMPLAZAR
└── util/
    └── SoundPlayer.kt          ← REEMPLAZAR
    
app/src/main/res/values/
└── strings.xml                 ← REEMPLAZAR
```

## Instalación

1. **Cierra Android Studio** completamente

2. **Copia los archivos** a sus ubicaciones:
   - `SensorManager.kt` → `app/src/main/java/com/tuapp/tabatatrainer/sensor/`
   - `WorkoutService.kt` → `app/src/main/java/com/tuapp/tabatatrainer/service/`
   - `ConfigScreen.kt` → `app/src/main/java/com/tuapp/tabatatrainer/ui/config/`
   - `SoundPlayer.kt` → `app/src/main/java/com/tuapp/tabatatrainer/util/`
   - `strings.xml` → `app/src/main/res/values/`

3. **Limpia y compila**:
   ```bash
   cd TabataTrainer
   ./gradlew clean
   ./gradlew assembleDebug
   ```

4. **Instala**:
   ```bash
   ./gradlew installDebug
   ```

## Flujo del Tabata corregido

```
WARMUP (14s) → WORK 1 → REST → WORK 2 → REST → ... → WORK N → FIN
                                                      ↑
                                              (sin REST después)
```

## Notas sobre BLE

- **HR tiene prioridad**: Se escanea primero el pulsómetro
- **Cadencia con delay**: El sensor de cadencia se busca 2 segundos después
- **Cadencia requiere movimiento**: Los sensores de cadencia solo transmiten cuando pedaleas
- **Reconexión automática**: Si pierdes conexión, se intenta reconectar

## Troubleshooting

**Si no detecta sensores:**
1. Verifica que Bluetooth está activado
2. Comprueba los permisos de la app (Bluetooth, Ubicación)
3. Asegúrate de que los sensores están encendidos y en modo pairing
4. Pulsa el botón de refrescar en la pantalla de configuración

**Si falla la compilación:**
```bash
./gradlew clean
./gradlew --stop
./gradlew assembleDebug
```
