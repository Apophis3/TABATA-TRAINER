# Plan 009 — Ruta entera, confirmación de vuelta y zancada por velocidad

## 1. R1 — Ruta aligerada (`util/map/RouteSimplifier`)
- Kotlin puro, sin clases de Android. Recibe `MapPoint` uno a uno y mantiene:
  - `kept`: puntos fijos ya decididos (el primero siempre).
  - `window`: puntos desde el último fijo.
- Algoritmo de «ventana abierta» (tipo Douglas-Peucker incremental): al llegar un punto nuevo `p`, si algún punto
  de la ventana se aleja más de 2 m del segmento «último fijo → p», el último punto de la ventana pasa a fijo y la
  ventana empieza en él. La ventana tiene tope (`MAX_WINDOW` = 200): al llenarse, su último punto pasa a fijo, así
  el coste por punto está acotado.
- Distancias en metros con proyección equirectangular local (sobra para tramos de cientos de metros).
- `points()` = `kept` + último punto recibido. `reset()` vacía todo.
- Con la salida del 28-nov (10 598 puntos) quedan del orden de 400.

## 2. Servicio (`FreeRideService`)
- `addMapPoint` usa `RouteSimplifier` y publica `routeSimplifier.points()` en `_mapPoints` (mismo `StateFlow`
  `mapPoints: StateFlow<List<MapPoint>>`; cambia el contenido, no el tipo). Se quita el tope `MAX_MAP_POINTS`.
- `startRide` y `resetToIdle` llaman a `routeSimplifier.reset()` donde hoy vacían `_mapPoints`.
- **Nuevo** `lapMarks: StateFlow<List<LapMark>>` (vuelta manual N + lat/lon) y **nuevo**
  `lapEvents: SharedFlow<LapMark>` (para el aviso de la pantalla), como `kmAlerts`.
- `addLap()`, solo cuando la vuelta se guarda: añade `LapMark` con la última posición conocida (si la hay), emite el
  evento y vibra corto (`VibrationEffect.createOneShot(80, DEFAULT_AMPLITUDE)`, mismo patrón que `vibrateKmAlert`).
- `startRide` y `resetToIdle` vacían `lapMarks`.
- **R4:** `StrideSpeedBins` (Kotlin puro) recibe en cada tic de 1 s del temporizador (`updateSteps`, que solo corre en
  marcha y sin pausa) los pasos totales y la distancia total; cierra un bloque cada 10 s y lo suma a su tramo.
  `saveFinalStats` guarda `strideSpeedBins.encode()` en la sesión. Se resetea en `startRide`.

## 3. UI (`FreeRideScreen`)
- `MapPage`/`RealTimeGoogleMap` reciben `lapMarks` y dibujan un `Marker` amarillo (`HUE_YELLOW`) por vuelta.
- Botón «Ver todo» (`Icons.Default.ZoomOutMap`, ya está `material-icons-extended`) arriba a la derecha, por debajo
  de la barra de estado.
  - Estado `overview` (`rememberSaveable`). Al activarlo: `newLatLngBounds(ruta + posición, 120 px)` animado.
  - `MapFollowPolicy.decide(…, isOverview)` devuelve `NONE` si `isOverview` (nuevo parámetro con valor por defecto
    `false`, los tests existentes no cambian).
  - Mientras `overview`, los movimientos de cámara **no** actualizan `userZoom` (`MapFollowPolicy.shouldRecordUserZoom`).
  - Al desactivarlo: se borra `lastUserInteractionMs` y la cámara vuelve a la posición con `targetZoom(userZoom)`.
  - Límites: `MapFollowPolicy.canShowOverview(puntos)`: hacen falta ≥ 2 puntos distintos.
- Aviso «🏁 Vuelta N»: en el `Box` raíz, igual que el banner de km. Escala con `Animatable` (1,3 → 1 en ~0,5 s) y se
  oculta a los 1,5 s. Cubre todas las páginas y orientaciones sin tocar los 6 botones de vuelta.

## 4. R4 — Persistencia (Room 10 → 11)
- Una columna nueva **nullable** `strideBySpeed TEXT` en `workout_sessions` (`ALTER TABLE … ADD COLUMN`, igual que la
  migración 8→9 que ya funcionó). Sin tabla nueva: menos riesgo de que Room rechace el esquema al abrir.
- Formato propio, sin dependencias: `tramo:pasos:distancia_m:segundos` separados por `;`, decimales con punto
  (`Locale.ROOT`). Ej.: `2:354:216.0:280;5:1414:1000.0:705`. `tramo` = km/h inferior del tramo.
- Se borra con la sesión (es una columna de la propia sesión).
- `DatabaseMigrations.MIGRATION_10_11` + `AppDatabase.version = 11`.

## 5. Detalle de sesión
- `StrideSpeedBins.decode(texto)` → tramos; `StrideBySpeedCard` debajo de las métricas del podómetro, solo si hay
  tramos con ≥ 100 pasos. Filas: «2–3 km/h · 0,61 m · 354 pasos · 4:40».

## 6. Impacto (Clave 3)
- `NavGraph`: sin cambios.
- `FreeRideService`: no cambia su ciclo de vida (crear/arrancar/parar/destruir). Solo añade dos flujos
  (`lapMarks`, `lapEvents`), cambia el contenido de `mapPoints` y añade un campo al guardado final. Los flujos viven
  en el servicio como `kmAlerts`; la pantalla los recoge con `collectAsState`/`LaunchedEffect(service)` y se
  cancelan al salir, sin fugas.
- `WorkoutService` y `SensorManager`: sin cambios.
- Room: migración 10→11 aditiva, sin pérdida de datos.

## 7. Tests (unitarios, sin Android)
- `RouteSimplifierTest`: recta → 2 puntos; esquina → se conserva; primero y último siempre; ruido < 2 m se quita;
  tope de ventana; reset.
- `MapFollowPolicyTest` (+): `overview` bloquea el seguimiento; no se guarda zoom en `overview`; `canShowOverview`.
- `StrideSpeedBinsTest`: tramos (1 km/h y 2 km/h desde 7), bloque de 10 s, parado y pocos pasos no cuentan,
  codificar/decodificar ida y vuelta, texto vacío o roto → lista vacía, filtro de 100 pasos.
- `LapMigrationTest` (+): cadena hasta 11 y la columna nueva de 10→11 coincide con la entidad.
