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
- [ ] T-03 **`gps_fused.csv`.** `RawFusedSource` (petición propia, todas las posiciones, `location_available`).
  Evidencia: Ruta de 5–10 min andando; filas por minuto de `gps_fused.csv` y de `gps_points` (A5).
- [ ] T-04 **`baro.csv` y `battery.csv`.** `RawBaroSource`, `RawBatterySource` (60 s + pantalla + inicio/fin).
  Evidencia: Ruta de 5 min con un apagado de pantalla de 2 min; cabeceras y primeras filas de ambos.
- [ ] T-05 **Exportar.** Bloque en el detalle de sesión: ruta, tamaño y «Compartir registro bruto» (zip por el
  `FileProvider`). Borrado de la carpeta con la sesión (R14, aprobado).
  Evidencia: el zip abierto en el PC.
- [ ] T-06 **Prueba de campo (usuario).** Paseo y salida con 30 min de pantalla apagada (A1–A6). La parte de
  pantalla apagada se hace **dos veces**: sin excluir la app del ahorro de batería y excluyéndola a mano en
  Ajustes; `events.csv` debe traer `ignoring_battery_optimizations` al empezar en las dos.
  Evidencia: zip de la sesión y Logcat; el agente analiza los CSV y lo resume.
- [ ] T-07 *(fase 2)* **GNSS puro.** `gps_gnss.csv` (`LocationManager` GPS) y `gnss_sats.csv` (un satélite por
  fila, una instantánea por segundo). Evidencia: salida de 10 min y comparación Fused vs GPS solo.
