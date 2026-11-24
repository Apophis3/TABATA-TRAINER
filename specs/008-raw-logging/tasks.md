# Tasks 008 — Registro en bruto

Reglas: una tarea cada vez, en orden. Cada tarea deja la app compilable y, con el interruptor apagado, igual que
antes. No empezar la siguiente hasta que el usuario confirme la anterior con evidencia. El agente **no** ejecuta
Gradle ni `adb install` (Clave 5). **Ninguna tarea empieza hasta que el usuario apruebe `plan.md`.**

- [x] T-01 **Formato y escritor (sin conectar a nada).** `rawlog/RawCsvFormat`, `RawRows` (datos, cabeceras y
  filas), `RawFileWriter` (búfer, 5 s, vaciado inmediato, cierre, error sin excepción), decimador de barómetro y
  `RawLogExporter` (tamaño, zip, borrar) en Kotlin puro. Tests: `RawCsvFormatTest`, `RawRowsTest`,
  `RawFileWriterTest`, `BaroDecimatorTest`, `RawLogExporterTest`.
  Evidencia: `./gradlew test` en verde con el número de tests.
  Hecha (2026-11-24): 284/284 en verde, confirmado por el usuario y en los informes XML.
- [x] T-02 **Interruptor, registrador y eventos.** `RawLogSettings` + interruptor «Registro bruto» en
  Ruta Libre antes de Iniciar y en Config del Tabata (mismo ajuste; añadido a Ruta Libre el 2026-11-24);
  `RawSessionLogger` con su hilo; `events.csv` (sesión, servicio, pantalla, Doze, ahorro, optimización de batería,
  permisos, primer/segundo plano) y `meta.json`. Llamadas en `FreeRideService`, `WorkoutService` y `TabataApp`
  (plan §7, incluidos `onTaskRemoved` y `onTrimMemory` si se aprueban).
  Evidencia: Logcat de una Ruta corta con el interruptor encendido (iniciar, pausar, reanudar, apagar y
  encender pantalla, detener) y el listado de la carpeta; otra con el interruptor apagado sin carpeta nueva.
  Hecha (2026-11-24): 301/301; Ruta en S22 Ultra con el interruptor apagado sin carpeta y encendido con
  `events.csv` (16 filas) y `meta.json` completos, 0 errores. Interruptor en Ruta Libre y Config comprobado (1.6.151).
- [x] T-03 **`gps_fused.csv`.** `RawFusedSource` (petición propia, todas las posiciones, `location_available`).
  Evidencia: Ruta de 5–10 min andando; filas por minuto de `gps_fused.csv` y de `gps_points` (A5).
  Código hecho (2026-11-24, sin compilar): `RawFusedRecorder` + `RawFusedSource`, conectados en `RawSessionLogger`;
  `RawFusedRecorderTest` +9 → suite esperada **310**.
  Prueba 1 (1.6.153, S22 Ultra, sesión f04787cd, 6,1 min): suite 310/310; 532 posiciones, 0 errores,
  ningún hueco > 1,5 s, lote siempre 1; parado y en pausa sigue escribiendo (Room 0 en esos tramos); rumbo vacío
  en 67 filas (parado), nunca 0; petición quitada al detener. **A5 dudoso:** en marcha Room recibió 82,5 puntos/min
  y 76 % de intervalos < 0,9 s, frente a ~68/min y ~37 % en las rutas del 22 y 23-11 sin esta petición (R-1).
  Falta la misma prueba con el interruptor apagado para confirmarlo.
  Prueba 2, mismo guion con el interruptor apagado (sesión bf552a55): sin carpeta nueva (R2 bien). Room en marcha
  72/min y 49 % < 0,9 s, frente a 82,5/min y 76 % con el interruptor encendido. Andando (~1,3 m/s): ~1,05 puntos/s
  apagado frente a ~1,2 encendido (+14 %). A ~3 m/s: igual (~1,55/s en los dos). **R-1 confirmado a paso de
  andar:** la petición propia hace que la app reciba más puntos.
  Hecha (2026-11-24): el usuario acepta el efecto (petición activa; R8 y plan §8 actualizados; `observer_effect`
  en `meta.json`, añadido después de la prueba).
- [x] T-04 **`baro.csv` y `battery.csv`.** `RawBaroSource`, `RawBatterySource` (60 s + pantalla + inicio/fin).
  Evidencia: Ruta de 5 min con un apagado de pantalla de 2 min; cabeceras y primeras filas de ambos.
  Estado (2026-11-24): código y tests escritos (`RawBaroRecorder/Source`, `RawBatteryRecorder/Source`, +13 tests →
  **323**); **sin compilar ni probar** (lo hace el usuario).
  Prueba (1.6.155, S22 Ultra, sesión f2dc38ee, 4,5 min, pantalla apagada 1 min 46 s): suite **323/323**.
  `baro.csv` 269 filas, una por segundo también con la pantalla apagada (ningún hueco > 1,5 s); el sensor dio
  ~10 muestras/s aunque se pidieron 5 (2683 recibidas, 2414 descartadas). `battery.csv` 8 filas: start, 4
  periodic (cada 60,0 s también con pantalla apagada), screen_off, screen_on, stop (la última). `current_now_ua`
  entre −257 y −1959: en este Samsung parece venir en mA, no en µA (se guarda crudo, R-4). `gps_fused` sin huecos
  > 1,5 s. 0 errores; `meta.json` cuadra con los archivos.
- [x] T-05 **Exportar.** Bloque en el detalle de sesión: ruta, tamaño y «Compartir registro bruto» (zip por el
  `FileProvider`). Borrado de la carpeta con la sesión (R14, aprobado).
  Evidencia: el zip abierto en el PC.
  Estado (2026-11-24): código y tests escritos (plan §6.2), **sin compilar ni probar**. `RawLogExporter` +
  `exportZip`/`clearOldZips`/`zipName`/`formatSize`; tarjeta «Registro bruto» en el detalle; `SessionDeletion`
  con `deleteRawLog` en Historial y Detalle. Tests +4 → suite esperada **327**.
  Prueba parcial (1.6.157, S22 Ultra): suite **327/327**. Zip de f2dc38ee compartido a Proton Drive y abierto
  en el PC: 5 archivos (`baro` 269 filas, `battery` 8, `events` 17, `gps_fused` 383, `meta.json`), `;` y punto
  decimal (A6 bien). En la caché del móvil queda un solo `rawlog_*.zip`. Falta: tarjeta oculta en una sesión sin
  carpeta y borrado de una sesión con carpeta (R14).
  Hecha (2026-11-24): sin tarjeta en la sesión 19:59 (sin carpeta), confirmado por el usuario. Borrada la sesión
  2cb8865f (18:11): su carpeta ya no está en `files/rawlog/` y Room no tiene ni la sesión ni sus puntos (R14 bien).
- [ ] T-06 **Prueba de campo (usuario).** Paseo y salida con 30 min de pantalla apagada (A1–A6). La parte de
  pantalla apagada se hace **dos veces**: sin excluir la app del ahorro de batería y excluyéndola a mano en
  Ajustes; `events.csv` debe traer `ignoring_battery_optimizations` al empezar en las dos.
  Evidencia: zip de la sesión y Logcat; el agente analiza los CSV y lo resume.
- [ ] T-07 *(fase 2)* **GNSS puro.** `gps_gnss.csv` (`LocationManager` GPS) y `gnss_sats.csv` (un satélite por
  fila, una instantánea por segundo). Evidencia: salida de 10 min y comparación Fused vs GPS solo.
