# Traspaso — Spec 008 (registro en bruto)

Documento para continuar en un hilo nuevo (texto de arranque: el que da el agente en el chat).

## Siguiente (2026-11-24): T-03 `gps_fused.csv`
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
