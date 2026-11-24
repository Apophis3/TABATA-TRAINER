# Plan 008 — Registro en bruto

Estado: **aprobado por el usuario el 2026-11-24** («Lo demás de plan.md, aprobado… Sigue con T-01»).

## 1. Lo comprobado en el código (2026-11-24, rama `spec-007-dual-signal-devices`, 1.6.143)
- `GpsManager` (`sensor/GpsManager.kt`): `LocationRequest` alta precisión, 1000 ms, mínimo 500 ms,
  `setMinUpdateDistanceMeters(1f)`, callback en el hilo principal; solo `result.lastLocation`; filtro
  precisión ≤ 15 m y velocidad > 0,7 m/s; parado con buena precisión reenvía la última posición válida con
  velocidad 0 y `timestamp = location.time` del punto nuevo. `onLocationAvailability` solo va al Logcat.
- `GpsReading` / `GpsPointEntity`: hora, lat, lon, altitud, velocidad, precisión (+ fase y ronda en Room). Sin
  rumbo, `elapsedRealtimeNanos`, proveedor, precisión vertical/velocidad/rumbo ni satélites.
- `FreeRideService` y `WorkoutService`: guardan si `isRunning && !isPaused`, búfer de 10; `stopRide` vacía,
  `onDestroy` no. `START_NOT_STICKY`. Ninguno sobrescribe `onTaskRemoved` ni `onTrimMemory`.
- No hay `WakeLock` (el permiso está declarado y sin uso), ni `PowerManager`, ni barómetro, ni batería del
  móvil, ni receptores de pantalla. Única medida "para que no lo maten": canal de notificación
  `IMPORTANCE_HIGH` en `FreeRideService`. `MainActivity` con `keepScreenOn="true"` (manifiesto).
- No hay otra petición de posiciones en la app (los mapas tienen `isMyLocationEnabled = false`).
- Manifiesto: ubicación precisa y aproximada, servicio en primer plano tipo `location|connectedDevice`;
  **sin** `ACCESS_BACKGROUND_LOCATION`.
- `FileProvider` con `cache-path`, `files-path` y `external-files-path` (vale para el zip en caché).
- Versiones: `compileSdk 37`, `targetSdk 36`, `minSdk 26`; `play-services-location 21.4.0`; Kotlin 2.4.20.
- `lifecycle-process` **no** está entre las dependencias → no se usa `ProcessLifecycleOwner`.

## 2. Paquete nuevo `com.tuapp.tabatatrainer.rawlog`
| Clase | Qué hace | Android |
|---|---|---|
| `RawRows` | Datos simples (`LocationFix`, `BaroSample`, `BatterySample`, `RawEvent`, `GnssSat`) y funciones que los convierten en una fila CSV. Cabeceras como constantes. | No (Kotlin puro, testeable) |
| `RawCsvFormat` | Números con `Locale.ROOT`, vacío si falta, comillas CSV para textos. | No |
| `RawFileWriter` | Un archivo: crea con cabecera, añade filas a un búfer en memoria, vacía cada 5 s, al instante si se pide, y al cerrar. Si falla una escritura, marca el archivo como roto y lo dice una vez (callback). Nunca lanza excepciones. Reloj inyectado para los tests. | Solo `java.io` (testeable con carpeta temporal) |
| `RawLogSettings` | Interruptor en `SharedPreferences` (`raw_log`, clave `enabled`, por defecto `false`). | Sí |
| `RawLogToggle` (en `ui/components/`) | Interruptor común con su propio `RawLogToggleViewModel` (`@HiltViewModel`, lee y guarda `RawLogSettings`). Se usa en dos sitios con el mismo ajuste (2026-11-24): Ruta Libre, solo antes de Iniciar (versión compacta; vertical: bajo «PREPARADO»; horizontal: bajo el cronómetro), y Config del Tabata (tarjeta). Sin tocar `FreeRideService`, `ConfigViewModel` ni `NavGraph.kt`. | Sí |
| `RawSessionLogger` | `@Singleton` (Hilt). Cada llamada lleva el nombre del servicio (§3.1): `serviceCreated(service)`, `start(service, sessionId, activityType)`, `pause(service)`, `resume(service)`, `stop(service, reason)`, `serviceDestroyed(service)`, `taskRemoved(service)`, `trimMemory(service, level)`. Crea un `HandlerThread` propio («RawLog») por sesión, arranca las fuentes y escribe `meta.json`. Si el interruptor está apagado al llamar a `start`, no hace nada y el resto de llamadas tampoco. | Sí |
| `RawSessionCore` | Reglas de la sesión sin Android: abrir carpeta y `events.csv`, qué servicio manda, eventos, parada, `write_error`, filas por archivo (§3.1). | No (testeable) |
| `RawFusedSource` | Petición Fused propia (§4). | Sí |
| `RawBaroSource` | Barómetro con `android.hardware.SensorManager` (no el `SensorManager` de la app). | Sí |
| `RawBatterySource` | Lectura de batería cada 60 s y a demanda. | Sí |
| (receptores del sistema) | Receptores de pantalla, Doze, Doze ligero, ahorro; estado inicial de energía y permisos. En T-02 van dentro de `RawSessionLogger` (no hay clase `RawSystemSource` aparte). | Sí |
| `AppVisibilityTracker` | Cuenta actividades visibles con `registerActivityLifecycleCallbacks` en `TabataApp`; avisa al registrador de primer/segundo plano. Sin dependencias nuevas. | Sí |
| `RawLogExporter` | Tamaño de la carpeta, zip con `java.util.zip` en `cacheDir/rawlog_<id>.zip`, borrar carpeta. Comprimir **no** toca la carpeta `rawlog/<sessionId>/`: solo se borra al borrar la sesión (R14). Lo único temporal es el `.zip` de la caché; en T-05 se borran los `rawlog_*.zip` viejos antes de crear uno nuevo (no justo después de compartir, porque la app que lo recibe lo lee más tarde). | `java.io` (testeable) |

Por qué un `HandlerThread` propio: todas las fuentes y la escritura van en un solo hilo → sin bloqueos, sin
tocar el hilo principal (la UI y el GPS de la app no esperan al disco) y sin carreras entre archivos.

## 3. Ciclo de una sesión con el interruptor encendido
1. `service_created`: el servicio llama a `logger.serviceCreated(...)` en `onCreate`. Como aún no hay sesión,
   el registrador guarda la hora (epoch y elapsed) de cada servicio vivo en memoria y, al empezar, escribe un
   `service_created` por servicio (en orden de tiempo, con su hora real) antes de `session_start`.
2. `start`: `FreeRideService.startRide()` / `WorkoutService.startWorkout()` (este solo con GPS activado) llaman
   a `logger.start(sessionId, "FREE_RIDE" | "TABATA")` **después** de crear su `sessionId` y sin cambiar nada
   más. El registrador lee el interruptor; si está apagado, termina aquí.
3. Pausa/reanudación/parada: una línea en `pauseRide/resumeRide/stopRide` (y equivalentes de Tabata) →
   `session_pause`, `session_resume`, `session_stop` + `logger.stop()`.
4. `onDestroy` de los servicios: `service_destroyed` y `logger.stop()` si sigue abierto (cierre sin Detener).
5. `stop`: quita la petición de posiciones, el barómetro, los receptores y la tarea de 60 s; vacía y cierra
   archivos; completa `meta.json`; termina el `HandlerThread`. Llamarlo dos veces no hace nada.

Si el proceso muere de golpe se pierden como mucho los últimos 5 s (lo aceptado en el pedido).

### 3.1 Detalles fijados al hacer T-02
- **Dos servicios a la vez.** Config enlaza `WorkoutService` aunque se esté en una Ruta, así que los dos pueden
  estar vivos. Cada llamada lleva el nombre del servicio (`FreeRideService` / `WorkoutService`). Pausa,
  reanudación y parada solo cuentan si vienen del servicio de la sesión abierta. `service_created`,
  `service_destroyed`, `task_removed` y `trim_memory` de **cualquiera** de los dos se anotan (detalle con el
  nombre), pero solo el servicio de la sesión la cierra al destruirse.
- **Motivo de parada** en el detalle de `session_stop` y en `meta.json`: `user` (Detener), `completed` (Tabata
  terminado), `service_destroyed`, `replaced` (empezó otra sesión sin parar la anterior).
- **Estado al empezar.** Tras `session_start` se anota una vez el estado de partida con los mismos tipos que los
  cambios y `,initial` al final del detalle: `permissions`, `ignoring_battery_optimizations`, `power_save`,
  `device_idle`, `device_light_idle` (Android 13+), `screen_on`/`screen_off` y `app_foreground`/`app_background`.
  Ejemplo: `screen_on;interactive=true,initial`. Las filas sin `,initial` son cambios reales.
- **Todos los eventos se vacían al instante** (son pocos; R6 pide al menos los importantes).
- **Hilo.** Uno «RawLog» por sesión: nace en `start` y termina en `stop`. Con el interruptor apagado no se crea.
- **Núcleo sin Android** (`RawSessionCore`): reglas de sesión, archivos y `write_error`, probado en la JVM. La
  parte Android (`RawSessionLogger`) solo cambia de hilo, lee el estado del móvil, registra receptores y escribe
  `meta.json`.
- **`meta.json` en T-02:** móvil, versión de la app, sesión, servicio, inicio/fin, motivo, zona horaria,
  `wakelock: false`, parámetros GPS de la app (1000/500/1 m, copiados a mano porque las constantes de
  `GpsManager` son privadas y no se toca), filas por archivo y errores. GPS propio, barómetro y batería se añaden
  en T-03/T-04.

## 4. `gps_fused.csv`
- `LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)`, `setMinUpdateIntervalMillis(500)` (el
  mismo que la app, para no pedir más rápido que hoy), **sin** `setMinUpdateDistanceMeters` (0),
  `setWaitForAccurateLocation(false)`, sin retraso de lotes (`maxUpdateDelay` 0). Callback en el hilo «RawLog».
- `onLocationResult`: recorre `result.locations` y escribe una fila por posición (`batch_index`, `batch_size`).
- Campo a campo con su `hasX()`: altitud, precisión vertical, horizontal, velocidad, precisión de velocidad,
  rumbo, precisión de rumbo; si `hasX()` es falso → vacío. `is_mock`: `isMock` (Android 12+) o
  `isFromMockProvider`. `loc_time_ms = time`, `loc_elapsed_ns = elapsedRealtimeNanos`, `provider`.
- `onLocationAvailability` → evento `location_available` con vaciado inmediato.
- La conversión `Location → LocationFix` es la única parte con Android; el formato de la fila se prueba sin él.

### 4.1 Detalles fijados al hacer T-03
- **Dos clases.** `RawFusedRecorder` (Kotlin puro, probado en la JVM): abre `gps_fused.csv`, una fila por posición,
  eventos y contadores. `RawFusedSource` (Android): la petición, la conversión y quitarla. La crea
  `RawSessionLogger` solo al abrir la sesión (interruptor encendido); con el interruptor apagado no se crea el
  cliente de Fused (R2).
- **Distancia mínima** puesta a 0 de forma explícita (`setMinUpdateDistanceMeters(0f)`) y `setMaxUpdateDelayMillis(0)`.
- **Orden al empezar:** permiso (fino o aproximado) → petición → `source_started` con los parámetros
  (`file=gps_fused.csv;priority=…;interval_ms=1000;min_interval_ms=500;min_distance_m=0;max_update_delay_ms=0;wait_for_accurate=false`).
  Sin permiso: `source_error` `reason=no_location_permission` y **sin** archivo. Si Fused rechaza la petición más
  tarde: `source_error` `reason=request_failed: …` (llega al hilo «RawLog»).
- **Hora de llegada:** una por resultado; todas las posiciones del lote llevan la misma (R5). Un resultado vacío
  cuenta pero no escribe filas. Las filas van al búfer de 5 s; solo los eventos se vacían al instante.
- **`location_available`:** detalle `true`/`false` a secas, tal como dice R10 (no `available=…`).
- **Parada:** la petición se quita en todas las salidas (`user`, `completed`, `service_destroyed`, `replaced`), antes
  de anotar `session_stop`, y lo dice una línea de Logcat «Fused: petición quitada». En pausa **no** se quita (R3).
- **`meta.json`:** + `raw_gps_params` (fuente, prioridad, 1000/500/0 m, sin lotes, sin espera) junto a
  `app_gps_params`, y `fused` (`started`, `results_received`, `locations_received`, `error`).
  Tras la prueba: + `observer_effect` y `observer_effect_note` (§8 R-1).

## 5. `baro.csv`
- `getDefaultSensor(Sensor.TYPE_PRESSURE)`; `registerListener(listener, sensor, 200_000 µs, 0 µs, handler)`:
  ritmo pedido **200 ms** (Android lo toma como orientación) y **sin lotes**, para ver si con la pantalla
  apagada se cortan las muestras en vez de esconderlo en lotes.
- Una fila si `event.timestamp − último_escrito ≥ 950 ms`. Se cuentan recibidas, escritas y descartadas (a
  `meta.json`).
- Por qué 200 ms y no 1 s (decisión del usuario tras T-01): con el sensor a 1 Hz, una muestra que llega 40 ms
  tarde seguida de otra 40 ms pronto deja 920 ms entre ellas y el filtro de 950 ms perdería una. Pidiéndolas más
  rápido siempre hay una muestra disponible pasados los 950 ms, y el archivo queda en una por segundo.

### 5.1 Detalles fijados al hacer T-04
- **Dos clases**, como Fused: `RawBaroRecorder` (Kotlin puro: archivo, filtro de 950 ms, eventos, datos del
  sensor) y `RawBaroSource` (Android: `android.hardware.SensorManager`, no el `SensorManager` de la app).
- **Sin barómetro:** `source_error` `reason=no_pressure_sensor` y **sin** archivo; `meta.json` → `baro.available=false`.
  Si Android no acepta el registro: `source_error` `reason=register_listener_false` (o la excepción).
- **`source_started`:** `file=baro.csv;sampling_period_us=200000;max_report_latency_us=0;min_gap_ms=950`.
- Filas al búfer de 5 s (como `gps_fused`). `accuracy` de cada muestra tal cual.
- **`meta.json`:** + `baro` (`available`, `name`, `vendor`, `max_range_hpa`, `resolution_hpa`, `min_delay_us`,
  `fifo_max_event_count`, lo pedido, `min_gap_ms`, `samples_received/written/discarded`, `error`).
- El sensor se quita al parar, antes de anotar `session_stop`, en todas las salidas. En pausa **no** (R3).

- Batería: `level_pct` = `BatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)` (Android ya lo da en %,
  así no se divide nada). Del `ACTION_BATTERY_CHANGED` pegajoso: temperatura en décimas de °C, `status` y
  `plugged` como números de Android. `getIntProperty(CHARGE_COUNTER)` y `getLongProperty(CURRENT_NOW)`;
  `Integer.MIN_VALUE`/`Long.MIN_VALUE` → vacío. Tarea de 60 s con `postDelayed` en el hilo «RawLog» (se
  retrasa si el móvil duerme: es lo que se quiere ver).
- Pantalla: `ACTION_SCREEN_ON/OFF` registrados en el contexto de la aplicación; detalle con
  `PowerManager.isInteractive`. Cada cambio de pantalla también escribe una fila de batería.
- Energía: `ACTION_DEVICE_IDLE_MODE_CHANGED`, `ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED` (Android 13+),
  `ACTION_POWER_SAVE_MODE_CHANGED` → el valor nuevo. `isIgnoringBatteryOptimizations` al empezar y en cada
  encendido de pantalla (solo si cambió).
- Permisos al empezar: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`
  (granted sí/no).
- `onTrimMemory(level)` y `onTaskRemoved` de los servicios → eventos (vaciado inmediato).
- Fallo de almacenamiento (decisión del usuario tras T-01): **nunca** una ventana, un aviso ni un Toast. El
  `onError` de cada `RawFileWriter` escribe una fila `write_error` en `events.csv` (detalle: archivo y motivo,
  vaciado inmediato) y una línea en Logcat (etiqueta `RawLog`). Si el que falla es el propio `events.csv`, solo
  la línea de Logcat. La sesión de la app sigue igual.
- `meta.json`: escrito a mano con `org.json.JSONObject` (incluido en Android, no es dependencia nueva).
  Versión de la app con `packageManager.getPackageInfo` (no depende de `BuildConfig`). Nota fija:
  «current_now_ua: valor crudo de BatteryManager; en Samsung la unidad y el signo pueden variar».

### 6.1 Detalles fijados al hacer T-04 (batería)
- **Dos clases:** `RawBatteryRecorder` (Kotlin puro) y `RawBatterySource` (Android).
- **Cuándo:** `start` al abrir, `periodic` cada 60 s (`postDelayed` en el hilo «RawLog», sin alarma ni WakeLock),
  `screen_on`/`screen_off` desde el mismo receptor que escribe el evento de pantalla, y `stop` al parar, **antes**
  de cerrar la sesión (así queda escrita).
- Cada fila se vacía **al instante** (son pocas y así no se pierden si el móvil duerme).
- `screen_on` de cada fila: `PowerManager.isInteractive` en ese momento. Datos ausentes → vacío.
- **`source_started`:** `file=battery.csv;period_ms=60000;triggers=start,periodic,screen_on,screen_off,stop`.
- **`meta.json`:** + `battery` (`period_ms`, `started`, `rows_by_trigger`, `note` con el aviso de la corriente).
- Línea de Logcat al cerrar: + `baro=escritas/recibidas` y `bateria={motivo=filas}`.

### 6.2 Detalles fijados al hacer T-05 (exportar y borrar)
- **`RawLogExporter`** (Kotlin puro) gana: `zipName(id)` = `rawlog_<id>.zip`; `clearOldZips(cacheDir)` borra
  solo los archivos de primer nivel de la caché que empiezan por `rawlog_` y acaban en `.zip` (nada más de la
  caché); `exportZip(id, cacheDir)` = borrar los viejos y crear el nuevo; `formatSize(bytes)` en español
  («850 B», «12,4 KB», «1,2 MB»). Lo da Hilt (`AppModule`, `@Singleton`, raíz `filesDir/rawlog`).
- **Detalle de sesión:** el `SessionDetailViewModel` calcula al cargar `RawLogInfo` (ruta completa de la
  carpeta y tamaño) en `Dispatchers.IO`; `null` si no hay carpeta → no se dibuja nada. Tarjeta «Registro
  bruto» tras la de Exportar: ruta, tamaño y botón «Compartir registro bruto» (desactivado mientras comprime).
  El zip se crea en el ViewModel (`Dispatchers.IO`); la pantalla solo lo comparte con
  `ExportUtils.shareFile(context, zip, "application/zip")`. Si falla: Toast «Error al crear el zip».
- **R14:** `SessionDeletion.deleteWithChildren` gana `deleteRawLog` (obligatorio, sin valor por defecto) y lo
  llama **antes** de borrar la sesión, como los demás hijos (si algo falla, la sesión sigue visible). Lo pasan
  los dos sitios que borran: Historial y Detalle. El borrado de la carpeta va en `Dispatchers.IO`.
- Tests: `RawLogExporterTest` +4 (nombre, limpieza sin tocar otros archivos, `exportZip` deja un solo zip,
  tamaño en español) y `LapExportTest` ajustado (5 pasos, `rawlog` antes de `session`) → suite **327**.

## 7. Impacto en servicios y navegación (Clave 3, Graph Lineage)
- `NavGraph.kt`: **no se toca.** La pantalla de detalle de sesión ya existe; solo gana un bloque.
- `FreeRideService` / `WorkoutService` — cambios exactos:
  - `onCreate`: + `rawLogger.serviceCreated(name)` (solo guarda una hora en memoria).
  - `startRide/startWorkout`, `pause*/resume*/stop*`: + una llamada al registrador al final, sin cambiar el orden
    ni el estado.
  - `onDestroy`: + `rawLogger.serviceDestroyed(name)` antes de `super.onDestroy()`.
  - **Nuevos** `onTaskRemoved(rootIntent)` y `onTrimMemory(level)`: llaman a `super` y anotan el evento.
    No paran, no reinician ni cambian `START_NOT_STICKY`. Hoy esos métodos son los de `Service` por defecto
    (no hacen nada), así que el comportamiento no cambia. **Necesita tu aprobación expresa.**
- Fugas: el registrador usa solo el contexto de la aplicación (nunca el del servicio ni de la actividad);
  receptores, sensor y petición de posiciones se quitan en `stop`; el hilo termina con `quitSafely`.
  Si el servicio muere sin Detener, `onDestroy` llama a `stop`.
- `TabataApp`: + registro de `AppVisibilityTracker` (solo cuenta `onStart/onStop` de actividades).
- `SensorManager` de la app: **sin cambios.** `GpsManager`: **sin cambios.**

## 8. Riesgos
- **R-1 — La petición extra podría cambiar lo que recibe la app.** Fused junta las peticiones de la app. En tus
  rutas de septiembre, la mitad de los intervalos entre filas de `gps_points` ya son de menos de 0,9 s (el
  mínimo de 500 ms deja que lleguen más de una por segundo), sin otra petición en la app. Con el mismo
  intervalo y mínimo no debería cambiar, pero no se puede asegurar: se comprueba con A5 (filas por minuto con
  el interruptor encendido y apagado).
  **Medido (2026-11-24, S22 Ultra, mismo guion de 6 min encendido y apagado):** en marcha, Room recibió
  82,5 puntos/min con el interruptor encendido frente a 72 apagado (76 % frente a 49 % de intervalos < 0,9 s).
  Andando (~1,3 m/s): ~1,2 puntos/s frente a ~1,05 (**+14 %**). A ~3 m/s: ~1,55/s en los dos (**igual**).
  **Decisión del usuario:** se acepta (interruptor solo para pruebas, apagado por defecto) y se mantiene la
  petición activa (ver R8). `meta.json` lo dice con `observer_effect` = `peticion_activa_1000ms_sin_distancia_minima`.
- **R-2 — Sin WakeLock a propósito.** Con la pantalla apagada pueden faltar filas de barómetro y batería y
  llegar tarde las posiciones. Eso es lo que se quiere medir; un WakeLock lo taparía. Se dice en `meta.json`
  (`wakelock: false`).
- **R-3 — Batería y espacio.** El barómetro gasta poco y el GPS ya está encendido. Tamaño estimado por hora:
  `gps_fused` ≈ 0,6 MB, `baro` ≈ 0,25 MB, resto < 0,1 MB; `gnss_sats` (fase 2) ≈ 5–8 MB.
- **R-4 — Corriente en Samsung.** Unidades/signo dudosos: se guarda crudo (R11).

## 9. Tests (JVM, sin Android)
- `RawCsvFormatTest`: punto decimal aunque `Locale.getDefault()` sea español; vacío para nulos; comillas y
  saltos de línea en `detail`; separador `;`.
- `RawRowsTest`: cada cabecera tiene tantas columnas como su fila; filas de ejemplo exactas (fused con todos
  los campos y con campos ausentes; baro; batería; evento).
- `RawFileWriterTest`: cabecera una sola vez; nada escrito antes de 5 s; vaciado a los 5 s con reloj falso;
  vaciado inmediato; cierre vacía; sesión sin eventos deja `events.csv` solo con cabecera; carpeta sin
  permiso de escritura → no lanza y avisa una vez.
- `BaroDecimatorTest`: con muestras cada 200 ms escribe una por segundo; con 1 Hz con retrasos de ±40 ms no
  pierde ninguna; cuenta descartadas.
- `RawLogExporterTest`: el zip contiene los archivos con el mismo contenido; tamaño de carpeta.
- T-04: `RawBaroRecorderTest` (+7) y `RawBatteryRecorderTest` (+6) → suite **323**.
- Suite esperada: **243 + los nuevos** (se dirá el número exacto en cada tarea).

## 10. Decisiones del usuario (2026-11-24)
1. `plan.md` aprobado: sin WakeLock a propósito, una fila por satélite, barómetro con mínimo de 950 ms y
   descartadas contadas, vacío y nunca 0, comprobación A5.
2. `onTaskRemoved` y `onTrimMemory`: sí, solo para anotar el evento.
3. R14: sí, al borrar una sesión se borra su carpeta `rawlog`.
4. Rama `spec-008-raw-logging` desde la punta de `spec-007-dual-signal-devices`.
5. Móvil: S22 Ultra (SM-S908B). Corregido «S22+» en las notas del proyecto.
6. A2 se hace dos veces (sin y con exclusión del ahorro de batería, cambiada a mano). Sin botón para pedirla.
7. Los defectos anotados (GPX, búfer de 9 puntos, `WAKE_LOCK` sin uso) quedan para una spec posterior.

## 11. Propuesta para la spec 009 (NO se hace en esta)
Con los datos de esta spec, probablemente convenga en `GpsManager`:
- Quitar la distancia mínima de 1 m (o dejarla en 0) y decidir "parado" con los datos, no con la petición.
- Recorrer `result.locations` en vez de solo `lastLocation`.
- No inventar puntos: hoy, parado, se guarda la posición vieja con la hora nueva. Mejor guardar el punto real
  y marcarlo como "parado" o no guardarlo.
- Revisar los umbrales 15 m y 0,7 m/s con la distribución real de precisión y velocidad.
- Guardar en Room rumbo, `elapsedRealtimeNanos` y precisión vertical (requiere migración).
- Vaciar el búfer de puntos en `onDestroy`.
- Decidir WakeLock / exclusión de ahorro solo si los datos de pantalla apagada muestran cortes.
- Arreglar GPX: omitir `<ele>` sin altitud y llevar la velocidad a `<extensions>`.
- **Saltos al empezar (visto en T-04, 2026-11-24):** el usuario ve un ritmo de ~2:00/km andando en los primeros
  segundos. Causa medida: Fused da un salto de posición (sesión f2dc38ee, a los 23–25 s: 41 m y 17 m en 2 s con
  su propia velocidad en 1,0–1,4 m/s y la precisión empeorando de 5 a 8 m); la app lo suma a la distancia y el
  ritmo de 10 s sale a ~2:00. También en f04787cd (11–16 s, ~60 m). La sesión total sale ~15 % larga.
  Propuesta: no sumar un tramo cuya velocidad implícita supere con mucho la que da el GPS (o la precisión).
