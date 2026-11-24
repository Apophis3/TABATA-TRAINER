# Traspaso — Spec 008 (registro en bruto)

Documento para continuar en un hilo nuevo (texto de arranque: el que da el agente en el chat).

## Siguiente (2026-11-24): T-06 Prueba de campo (solo cuando el usuario lo diga)
- **T-05 hecha** (1.6.157, 327/327): zip de f2dc38ee abierto en el PC (A6 bien), un solo zip en la caché,
  sin tarjeta en sesiones sin carpeta, y borrar la sesión 2cb8865f borró su carpeta (R14).

## Anterior: T-05 Exportar
- **Hecho (sin compilar ni commit):** plan §6.2. `RawLogExporter`: `exportZip(id, cacheDir)` (borra los
  `rawlog_*.zip` de primer nivel y crea `rawlog_<id>.zip`), `clearOldZips`, `zipName`, `formatSize` («1,2 MB»).
  Lo da Hilt (`AppModule`). `SessionDetailViewModel`: `rawLog` (ruta y tamaño, `null` sin carpeta) y
  `buildRawLogZip()` en `Dispatchers.IO`; tarjeta «Registro bruto» tras Exportar con «Compartir registro bruto»
  (`application/zip` por el `FileProvider`). R14: `SessionDeletion.deleteWithChildren(deleteRawLog = …)`
  obligatorio, antes de borrar la sesión; lo pasan Historial y Detalle. Tests +4 → **327**.
- **Falta (usuario):** test, instalar y la prueba (compartir dos veces, abrir el zip en el PC, borrar una sesión
  con carpeta). El agente comprueba por `adb run-as` la caché (un solo zip) y `files/rawlog/` tras borrar.
- **Sin tocar:** `GpsManager`, entidades Room, `NavGraph.kt`, servicios, `SensorManager`, Gradle.

## Anterior: arranque de T-05 Exportar
- **T-04 hecha** (probada 323/323, commit). Ver abajo. Visto en la prueba: saltos de GPS al empezar inflan el
  ritmo y la distancia de la app; anotado para la spec 009 (plan §11), no se toca en esta.

## Anterior: T-04 `baro.csv` y `battery.csv`
- **Hecho:** T-03 con commit (1b27f34, 310/310). T-04 con código y tests escritos, **sin compilar ni commit**:
  `rawlog/RawBaroRecorder.kt`, `RawBaroSource.kt`, `RawBatteryRecorder.kt`, `RawBatterySource.kt`; en
  `RawSessionLogger` las tres fuentes y `baro`/`battery` en `meta.json` (plan §5.1 y §6.1). Suite esperada **323**.
- **Falta (usuario):** test, instalar y Ruta de 5 min con 2 min de pantalla apagada. El agente lee `baro.csv`,
  `battery.csv`, `events.csv` y `meta.json` por `adb run-as` y Logcat «RawLog».
- Prueba (1.6.155, S22 Ultra, sesión f2dc38ee, 4,5 min, pantalla apagada 1 min 46 s): suite **323/323**.
  `baro.csv` 269 filas, una por segundo también con la pantalla apagada (ningún hueco > 1,5 s); el sensor dio
  ~10 muestras/s aunque se pidieron 5 (2683 recibidas, 2414 descartadas). `battery.csv` 8 filas: start, 4
  periodic (cada 60,0 s también con pantalla apagada), screen_off, screen_on, stop (la última). `current_now_ua`
  entre −257 y −1959: en este Samsung parece venir en mA, no en µA (se guarda crudo, R-4). `gps_fused` sin huecos
  > 1,5 s. 0 errores; `meta.json` cuadra con los archivos.

## Anterior: T-03 `gps_fused.csv`
- **Hecho:** T-01 y T-02 con commit (d53298f), suite **301/301**. T-03 con código escrito y **sin compilar ni
  commit**: `rawlog/RawFusedRecorder.kt` (Kotlin puro), `rawlog/RawFusedSource.kt` (petición Fused propia y
  `Location → LocationFix`), y en `RawSessionLogger` `startSources`/`stopSources` + `raw_gps_params` y `fused` en
  `meta.json`. Detalles fijados en `plan.md` §4.1. Tests +9 (`RawFusedRecorderTest`) → suite esperada **310**.
- **Sin tocar:** `GpsManager`, Room, `NavGraph.kt`, servicios, `SensorManager`, Gradle.
- **Falta (lo hace el usuario):** `./gradlew test` (310) e instalar; Ruta de 5–10 min andando con el interruptor
  encendido. El agente lee la carpeta por `adb run-as`, Logcat «RawLog» y compara filas por minuto de
  `gps_fused.csv` con `gps_points` de Room (A5). Si va bien, marcar T-03 `[x]` y, si lo pide, commit.
- Prueba 1 (1.6.153, S22 Ultra, sesión f04787cd, 6,1 min): suite 310/310; 532 posiciones, 0 errores,
  ningún hueco > 1,5 s, lote siempre 1; parado y en pausa sigue escribiendo (Room 0 en esos tramos); rumbo vacío
  en 67 filas (parado), nunca 0; petición quitada al detener. **A5 dudoso:** en marcha Room recibió 82,5 puntos/min
  y 76 % de intervalos < 0,9 s, frente a ~68/min y ~37 % en las rutas del 22 y 23-11 sin esta petición (R-1).
  Falta la misma prueba con el interruptor apagado para confirmarlo.
  Prueba 2, mismo guion con el interruptor apagado (sesión bf552a55): sin carpeta nueva (R2 bien). Room en marcha
  72/min y 49 % < 0,9 s, frente a 82,5/min y 76 % con el interruptor encendido. Andando (~1,3 m/s): ~1,05 puntos/s
  apagado frente a ~1,2 encendido (+14 %). A ~3 m/s: igual (~1,55/s en los dos). **R-1 confirmado a paso de
  andar:** la petición propia hace que la app reciba más puntos. Aceptado por el usuario (petición activa;
  R8, plan §8 R-1 y `observer_effect` en `meta.json`). **T-03 hecha.**
- **Siguiente tarea:** T-04 (`baro.csv` y `battery.csv`), solo cuando el usuario lo diga.

## Anterior: arranque de T-03
- **Hecho:** T-01 (formato, escritor, exportador) y T-02 (interruptor, registrador, `events.csv`, `meta.json`),
  ambas con commit. Suite **301/301** (1.6.151). T-02 probada en el S22 Ultra: interruptor apagado → sin carpeta;
  encendido → `files/rawlog/<sessionId>/` con `events.csv` y `meta.json`, 0 errores.
- **Interruptor «Registro bruto»** en dos sitios con el mismo ajuste: Ruta Libre (solo antes de Iniciar) y Config
  del Tabata. Vale para Ruta Libre y Tabata con GPS. Componente común `ui/components/RawLogToggle.kt`.
- **Qué falta hoy:** la carpeta solo tiene eventos; no se guarda ninguna posición. T-03 añade `gps_fused.csv`:
  **todas** las posiciones que da Fused, sin filtrar ni corregir, una fila por posición.
- **Cómo (ya aprobado, `plan.md` §4):** `RawFusedSource` con petición propia `PRIORITY_HIGH_ACCURACY`, 1000 ms,
  mínimo 500 ms, sin distancia mínima, sin espera de precisión, sin lotes; callback en el hilo «RawLog»; una fila
  por `result.locations` con `batch_index`/`batch_size`; cada campo con su `hasX()` (si no, vacío); `is_mock`;
  `onLocationAvailability` → evento `location_available`. La conversión `Location → LocationFix` es lo único con
  Android; la fila ya está en `RawRows.kt` (`LocationFix`, `RawFiles.GPS_FUSED`) y tiene tests.
- **Reutilizar:** `RawSessionLogger` (`open`/`finish` en el hilo «RawLog», ahí se arrancan y paran las fuentes),
  `RawSessionCore.openWriter(name, header)` y `event(...)`, tipos `source_started` / `source_error` /
  `location_available` ya definidos. `play-services-location` ya está en Gradle (no añadir nada).
- **Prohibido:** tocar `GpsManager` (filtros, `lastLocation`), Room, `NavGraph.kt`, el ciclo de vida de los
  servicios o el contrato de `SensorManager`; añadir dependencias; calcular o filtrar nada. Si hace falta, parar y
  preguntar.
- **Evidencia que pedirá el usuario (A5):** Ruta de 5–10 min andando con el interruptor encendido; filas por minuto
  de `gps_fused.csv` frente a `gps_points` de Room. El agente lee Logcat «RawLog» y la carpeta por `adb run-as`.
