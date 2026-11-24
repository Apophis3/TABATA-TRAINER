# Spec 008 — Registro en bruto (GPS, barómetro, estado de la app y batería)

Estado: **`plan.md` aprobado por el usuario (2026-11-24)**. Rama: `spec-008-raw-logging` (desde la punta de `spec-007-dual-signal-devices`).

## Problema
Para probar ideas de otra app (TrackCan) hace falta saber cómo se comporta de verdad el móvil del usuario
(Samsung Galaxy S22 Ultra, SM-S908B; el modelo queda además en `meta.json`) en una ruta: cada cuánto llegan posiciones, qué
huecos hay, qué pasa con la pantalla apagada, cuánto gasta. Hoy no se puede saber, porque la app solo guarda
puntos **ya filtrados y retocados**:

- `GpsManager` pide posiciones con distancia mínima de 1 m: parado o casi parado no llegan puntos.
- Solo usa `result.lastLocation`: si un resultado trae varias posiciones, el resto se pierde.
- Descarta puntos con precisión > 15 m o velocidad ≤ 0,7 m/s; parado y con buena precisión **reenvía la última
  posición válida con velocidad 0 y la hora del punto nuevo** (y ese punto se guarda en Room).
- De cada punto guarda solo hora, lat, lon, altitud, velocidad, precisión, fase y ronda.
- `FreeRideService` y `WorkoutService` solo guardan en marcha y sin pausa, en bloques de 10 puntos.
- No hay WakeLock, ni barómetro, ni batería, ni pantalla, ni ahorro/Doze registrados.

## Objetivo
Guardar **todo lo que da el móvil, sin filtrar, sin corregir y sin interpretar**, en archivos de texto
aparte, una carpeta por sesión. Lo que la app hace hoy (vueltas, estadísticas, mapa, exportaciones, Room)
no cambia.

## Requisitos
- **R1 — Interruptor.** Un interruptor «Registro bruto», **apagado por defecto**, en dos sitios que
  comparten el mismo ajuste: la pantalla de **Ruta Libre** (solo antes de Iniciar) y Config del Tabata
  (cambio del 2026-11-24: antes solo estaba en Config),
  guardado en el móvil. Vale para todas las sesiones siguientes: Ruta Libre siempre y Tabata solo con GPS
  activado. Cambiarlo con una sesión en marcha no afecta a esa sesión (se decide al empezar).
- **R2 — Apagado = como hoy.** Con el interruptor apagado no se abre ningún archivo, no se pide ninguna
  posición extra, no se registra ningún sensor ni receptor. La app se comporta exactamente igual que antes.
- **R3 — Ventana de registro.** Desde que empieza la sesión (Iniciar) hasta que se para (Detener) o el
  servicio se destruye, **incluidas las pausas** (la pausa y la reanudación quedan como eventos). Sin filtrar
  significa también no dejar de escribir en pausa.
- **R4 — Dónde.** Almacenamiento interno de la app: `files/rawlog/<sessionId>/`. Mismo `sessionId` que Room.
- **R5 — Formato común (CSV).** Cabecera en la primera línea; separador `;`; decimales con punto (siempre,
  aunque el móvil esté en español); una fila por dato. **Un dato que el móvil no da se deja vacío, nunca 0.**
  Toda fila empieza por `epoch_ms;elapsed_ns`: hora de reloj y tiempo desde el arranque del móvil
  (`SystemClock.elapsedRealtimeNanos`) **en el momento en que la app recibe el dato**, para cruzar archivos
  aunque el reloj cambie. Un texto con `;`, comillas o saltos de línea va entre comillas dobles y las comillas
  internas se duplican (CSV estándar, sin perder nada).
- **R6 — Escritura.** Con búfer; se vacía cada 5 s y al parar. Los eventos importantes (pantalla, servicio,
  sesión, ubicación disponible, error) se vacían al instante. Si falla el almacenamiento, la app sigue sin
  ventanas ni avisos: se anota `write_error` en `events.csv` si aún se puede y se deja de escribir ese archivo.
- **R7 — Sin cálculos.** No se filtra, no se suaviza, no se convierte presión en altitud, no se hacen medias.
  Lo único que se descarta son muestras de barómetro de más (R9), y se cuenta cuántas.
- **R8 — `gps_fused.csv`.** Una petición de posiciones propia (Fused), mismo intervalo que hoy (1000 ms, mínimo
  500 ms, alta precisión) pero **sin distancia mínima y sin filtros**, recorriendo **todas** las posiciones de
  cada resultado. Columnas:
  `epoch_ms;elapsed_ns;loc_time_ms;loc_elapsed_ns;provider;lat;lon;alt_m;vert_acc_m;h_acc_m;speed_ms;speed_acc_ms;bearing_deg;bearing_acc_deg;is_mock;batch_index;batch_size`
  (`batch_index` empieza en 0; `batch_size` = posiciones de ese resultado).
  **Efecto observador (aceptado por el usuario el 2026-11-24):** Fused junta esta petición con la de la app, así
  que con el interruptor encendido la app puede recibir más puntos. Medido en el S22 Ultra con el mismo guion:
  **+14 % andando** (~1,05 → ~1,2 puntos/s en Room) e **igual a ~3 m/s** (~1,55/s en los dos). Se acepta porque
  el interruptor es solo para pruebas y va apagado por defecto. La petición sigue siendo **activa** (no pasiva):
  el registro mide lo que el móvil da a una petición propia de 1 s sin distancia mínima, que es como grabará
  TrackCan; en pasivo, con la pantalla apagada no se sabría si un corte es del móvil o de la petición de Tabata.
  Queda en `meta.json` (`observer_effect`).
- **R9 — `baro.csv`.** Sensor de presión del móvil, ≈ 1 fila por segundo. Si el sensor da más, se escribe una
  muestra cuando han pasado al menos 950 ms (hora del sensor) desde la última escrita; las demás se descartan
  sin promediar y su número va a `meta.json`. Columnas:
  `epoch_ms;elapsed_ns;sensor_ts_ns;pressure_hpa;accuracy`
  (`sensor_ts_ns` y `accuracy` son la hora y la fiabilidad que da el propio sensor; añadidas porque son datos
  en bruto que ya vienen con la muestra). Si el móvil no tiene barómetro, no se crea el archivo y queda en
  `meta.json`.
- **R10 — `events.csv`.** `epoch_ms;elapsed_ns;type;detail`. Tipos:
  - Sesión: `session_start`, `session_pause`, `session_resume`, `session_stop`.
  - Servicio: `service_created` (se guarda con su hora real aunque ocurra antes de empezar la sesión),
    `service_destroyed`, `task_removed`, `trim_memory` (detalle: nivel).
  - Pantalla: `screen_on`, `screen_off` (detalle: `interactive=true/false`).
  - Energía: `device_idle` (Doze profundo), `device_light_idle` (Doze ligero, Android 13+),
    `power_save` (ahorro), `ignoring_battery_optimizations`. Al empezar y cada vez que cambian (el último no
    tiene aviso del sistema: se vuelve a mirar en cada encendido de pantalla y se anota solo si cambió).
  - App: `app_foreground`, `app_background`.
  - Ubicación: `location_available` (detalle: `true/false`), `permissions` al empezar
    (`fine=…;coarse=…;background=…`). La app no pide ubicación en segundo plano, así que `background=false`
    es lo esperado; no se añade ese permiso.
  - Fuentes: `source_started` / `source_error` (detalle: archivo y motivo), `write_error`.
- **R11 — `battery.csv`.** Cada 60 s (lo mejor posible: sin alarmas ni WakeLock, así que si el móvil duerme
  la fila no sale y eso **es** el dato), en cada encendido/apagado de pantalla, al empezar y al parar. Columnas
  (en inglés por la constitución; equivalencia con lo pedido):
  `epoch_ms;elapsed_ns;level_pct;charge_counter_uah;current_now_ua;temp_dc;status;plugged;screen_on;trigger`
  = nivel_pct, carga_uAh, corriente_uA, temperatura_dC, estado_carga, (enchufe), pantalla_encendida, (motivo).
  Valores tal como los da Android, sin cambiar unidades ni signo (en Samsung la corriente puede venir en
  otras unidades o signo; se anota en `meta.json`).
- **R12 — `meta.json`.** Se escribe al empezar y se completa al parar: modelo, fabricante y versión de
  Android; versión de la app (`versionName` y `versionCode`); `sessionId`; tipo de actividad; parámetros GPS
  usados (intervalo, mínimo, prioridad, distancia mínima 0) y los de la app (1000/500/1 m) para comparar;
  barómetro (sí/no, nombre, fabricante, rango, resolución, retraso mínimo); hora de inicio y fin; contadores
  (posiciones recibidas, muestras de barómetro recibidas/escritas/descartadas, filas por archivo, errores);
  lista de archivos.
- **R13 — Exportar.** En el detalle de la sesión, si existe su carpeta: la ruta y el tamaño, y un botón
  «Compartir registro bruto» que comprime la carpeta en un `.zip` (en la caché de la app) y lo comparte con el
  `FileProvider` que ya existe. Sin carpeta, no se muestra nada.
- **R14 — Borrado.** Al borrar una sesión desde la app se borra también su carpeta de registro bruto
  *(aprobado por el usuario 2026-11-24)*.
- **R15 (fase 2, tarea aparte) — GNSS puro.** `gps_gnss.csv`: posiciones del proveedor GPS solo
  (`LocationManager`, 1000 ms, sin distancia mínima), mismas columnas que R8. Y `gnss_sats.csv`: una
  instantánea por segundo, **una fila por satélite** (`epoch_ms;elapsed_ns;snapshot;svid;constellation;cn0_dbhz;used_in_fix;elevation_deg;azimuth_deg;has_almanac;has_ephemeris`).
  Se cambia la "media de C/N0 y nº de satélites" pedida por los datos por satélite, porque una media ya es un
  cálculo (R7); visibles, usados y media se sacan después de este archivo.

## Fuera de alcance (no se toca en esta spec)
- Filtros y parámetros de `GpsManager`, uso de `lastLocation`, guardado en Room, vueltas, mapa,
  estadísticas, exportación Excel/GPX.
- Room (sin migración), `NavGraph.kt`, contrato del `SensorManager` de la app, dependencias de Gradle.
- WakeLock, exclusión del ahorro de batería, permisos nuevos. **No** se añade ningún botón para pedir la
  exclusión del ahorro de batería: el usuario la cambia a mano en los Ajustes de Android.
- **Defectos vistos, anotados para otra spec:**
  - `ExportUtils.exportToGpx` escribe `<ele>0.0</ele>` cuando no hay altitud (inventa un 0) y pone `<speed>`
    directamente dentro de `<trkpt>` (no es GPX 1.1 válido: debería ir en `<extensions>` o no ir).
  - `FreeRideService.onDestroy` no vacía el búfer de puntos GPS (solo lo hace `stopRide`): con un cierre del
    servicio sin Detener se pierden hasta 9 puntos. `WorkoutService` igual con su búfer de 10.
  - El permiso `WAKE_LOCK` está declarado en el manifiesto pero no se usa.

## Restricciones
- No escribir código hasta que el usuario apruebe `plan.md`.
- Una tarea `T-xx` cada vez (`tasks.md`).
- El agente no ejecuta Gradle ni `adb install`: compila, instala y prueba el usuario.
- Cambios en los servicios: solo llamadas al registrador y dos métodos nuevos que solo anotan
  (`onTaskRemoved`, `onTrimMemory`, ver plan §7), aprobados por el usuario el 2026-11-24.

## Aceptación (la pone el usuario en un paseo y en una salida)
- A1 — `gps_fused.csv` tiene casi una fila por segundo en marcha y se ven los huecos reales, sin rellenar.
- A2 — Con la pantalla apagada 30 min: `events.csv` tiene la hora exacta del apagado y del encendido,
  `battery.csv` el consumo (o los huecos donde no hubo filas) y `gps_fused.csv` muestra si siguieron llegando
  posiciones. **La prueba se hace dos veces:** una con la app sin excluir del ahorro de batería y otra
  excluyéndola a mano en Ajustes de Android. En las dos, `events.csv` recoge
  `ignoring_battery_optimizations` al empezar (`false` en la primera, `true` en la segunda).
- A3 — `baro.csv` tiene datos continuos con la pantalla encendida (y se ve qué pasa con ella apagada).
- A4 — Con el interruptor apagado la app se comporta igual que antes: suite de tests en verde y ninguna
  carpeta `rawlog/` nueva.
- A5 — Con el interruptor encendido, `gps_points` de Room tiene un número de filas por minuto parecido al de
  una sesión anterior equivalente (medido el 2026-11-24: +14 % andando e igual a ~3 m/s; aceptado, ver R8 y plan §8 riesgo R-1).
- A6 — El `.zip` compartido se abre en el PC y los CSV se leen con `;` y punto decimal.
