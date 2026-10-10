# Plan 006 — Ruta Libre: cadencia/ritmo, vueltas AUTO/MANUAL y tabla de vueltas

## Estrategia general
- **Una tarea = un cambio independiente, compilable y probable por separado.** Se implementa una, el usuario compila/prueba, y solo entonces se pasa a la siguiente.
- **Lógica pura, testeable sin Android.** Toda regla nueva (ritmo, selección cadencia/ritmo, autolap, guardas de vuelta, métricas por bloque, formato de fila) vive en clases Kotlin puras (sin `Context`, sin reloj implícito: el tiempo entra por parámetro). `FreeRideService` y `FreeRideScreen` solo las llaman. Esto permite tests JVM con **solo JUnit** (ya disponible), sin añadir dependencias (Clave 2).
- **Reutilizar, no duplicar:** portrait y landscape comparten las mismas funciones/composables de fila de vuelta y de hueco cadencia/ritmo (hoy `LandscapeLapsPage` duplica lógica).

## Clases puras nuevas (paquete `com.tuapp.tabatatrainer.util.laps`)
| Clase | Responsabilidad | Tarea |
|---|---|---|
| `PaceCalculator` | ritmo (s/km) desde ventana móvil de muestras (t, distancia); rango 2:00–30:00/km; `format(secPerKm)` → `mm:ss` o `--` | T-01 |
| `CadenceDisplayPolicy` | decide `CADENCE` vs `PACE` según `paired`, `lastSampleAtMs`, `nowMs`, `CADENCE_STALE_MS=5000` | T-01 |
| `LapGuard` | valida una vuelta manual candidata (solo tiempo: ≥5 s; la distancia no cuenta) | T-02 |
| `AutoLapTracker` | cruces de 1000 m con interpolación temporal; devuelve 0..n `AutoLapCrossing(km, atMs)`; sin estado de reloj | T-03 |
| `KmAlertGate` | emite el aviso de cada km una sola vez (recuerda último km avisado) | T-03 |
| `LapBook` (+ `LapType`, `CadenceSource`, `LapRecord`) | las dos series (AUTO/MANUAL), numeración independiente (`typeNumber`) y orden global (`order`), ventana contigua de cada manual, "última manual registrada"; el reloj de pared lo aporta el llamador | T-04 |
| `LapRowFormatter` | fila de lista: etiqueta A/M + nº de su serie, distancia, tiempo, ritmo (`--` si dist 0 o > 60:00/km), estilo derivado de `type`; incluye cálculo de contraste WCAG para el test | T-05 |
| `LapExportRows` | filas de la hoja "Laps" del Excel a partir de `LapEntity` (tipo = `type` persistido, `--` en vez de 0 sin dato) | T-06 |
| `SessionDeletion` | borrado de sesión con sus hijos (vueltas, GPS, lecturas) y la sesión al final; lo usan Historial y Detalle (no hay claves foráneas) | T-06 |
| `LapBlockMetrics` | pasos y desnivel de una ventana = diferencia de contadores acumulados (`BlockCounters`) al cerrar − al abrir; origen de la cadencia (`BIKE_SENSOR` si el sensor de pedal emitió en la ventana, `PEDOMETER` si hay pasos, si no `NONE`); `cadencePpm(steps, duración, fuente)` = pasos ÷ minutos, `null` con sensor de bici o sin pasos. Ritmo, FC y velocidad ya salen de `LapRecord`/`LapRowFormatter`. En un salto de GPS de varios km las AUTO de ese salto quedan sin pasos/desnivel (`--`) | T-07 |
| `LapDuration` | duración de una vuelta guardada: distancia ÷ velocidad media (tiempo de ruta, sin pausas); en serie en el sitio (0 m) usa el reloj de pared. Lo usan el Excel y el resumen | T-08 |
| `SessionLapSummaryModel` | qué se muestra por vuelta manual en el detalle: tiempo, distancia, ritmo, vel., FC media/máx, pasos, cadencia ppm (ítem omitido si la fuente es `BIKE_SENSOR`), desnivel+; `--` sin dato | T-08 |
| `LapTableModel` | celdas de la tabla de la ventana 4: nº global (columna fija) + tipo, km, tiempo, ritmo, FC, FC máx, pasos, cadencia (ppm; rpm del sensor si `BIKE_SENSOR`, nunca ppm con sensor de bici) y desnivel+; `--` sin dato; estilo AUTO/MANUAL de `LapRowFormatter`; la más reciente primero | T-09 |
| `CurrentLapModel` | textos de la vuelta manual en curso (ventana 3): ritmo medio de la vuelta, pasos, cadencia (ppm de la vuelta; rpm si el sensor de bici está activo) y desnivel+; `--` sin dato | T-10 |

## Contratos que cambian (autorizado por esta spec — Clave 2)
1. **Room:** `AppDatabase.version` 9 → 10 con `MIGRATION_9_10` en `DatabaseMigrations.ALL_MIGRATIONS`. Cambios solo sobre `lap_records` (hoy siempre vacía: nadie llama a `insertLap`, por lo que la migración es segura):
   - `ALTER TABLE lap_records ADD COLUMN type TEXT NOT NULL DEFAULT 'MANUAL'` — `AUTO` | `MANUAL`.
   - `ALTER TABLE lap_records ADD COLUMN typeNumber INTEGER NOT NULL DEFAULT 0` — número dentro de su serie.
   - `ALTER TABLE lap_records ADD COLUMN steps INTEGER` — pasos del bloque (nullable).
   - `ALTER TABLE lap_records ADD COLUMN cadenceSource TEXT` — `PEDOMETER` | `BIKE_SENSOR` | `NONE`.
   - `LapEntity` añade `type`, `typeNumber`, `steps`, `cadenceSource`; `lapNumber` pasa a ser el orden global de creación (para ordenar). Resto de columnas sin cambios.
   - `type` y `cadenceSource` se guardan como texto (`LapType.name` / `CadenceSource.name`), sin `TypeConverter`. `startTimestamp`/`endTimestamp` son reloj de pared (para una AUTO, el fin se aproxima al instante de procesar el cruce; para la primera vuelta de cada serie, el inicio es el de la ruta, por lo que una pausa queda dentro de la ventana). `elevationGain` y `steps` quedan `null` hasta T-07.
   - `GpsDao`: sin cambios de firma (`insertLap`, `getLapsForSession*`, `deleteLapsForSession` ya existen). Se **empieza a usar** `insertLap` (T-04) y `deleteLapsForSession` (T-06).
2. **`FreeRideService` (contrato público):**
   - `LapData` añade `type: LapType` y `typeNumber: Int`, `steps: Int?`, `elevationGain: Float`, `cadenceSource`. `isAutoLap` se mantiene como propiedad derivada (`type == AUTO`) para no romper la UI existente hasta T-05.
   - `FreeRideStats` añade `paceSecPerKm: Int?`, `cadenceActive: Boolean` y `manualLapsCount`; `lapsCount` se conserva (= total) para compatibilidad.
   - *(T-10)* `FreeRideStats` añade `currentLapSteps: Int?` y `currentLapElevationGainM: Float?` = pasos y desnivel acumulados desde que se abrió la vuelta manual en curso (mismos contadores que `LapBlockMetrics` usa al cerrarla); `null` = sin dato. Se refrescan cada segundo junto a `updateSteps()` y se reinician al cerrar la vuelta. Sin cambios en el ciclo de vida del servicio.
   - Nuevo `SharedFlow<KmAlert>` (km, ritmo) para el aviso de R3 (T-03).
   - `addLap()` mantiene firma; ahora puede **rechazar** (devuelve `Boolean`/emite aviso "Vuelta ignorada") — T-02.
3. **`SensorManager` / lectores de cadencia:** **sin cambios de contrato.** El instante del último dato de cadencia se mide en `FreeRideService` (donde ya se recoge el flujo `CadenceReading`), no en `SensorManager`.
4. **`NavGraph.kt`:** sin cambios en esta spec (R8 = 4ª página del pager, ver abajo).
5. **Gradle:** sin dependencias nuevas.

## Impacto (Clave 3 — servicios y navegación)
- **Ciclo de vida de `FreeRideService`:** no se altera `onCreate/onStartCommand/onDestroy`, ni el binder, ni el foreground. Se añaden estado puro (`LapBook`, `AutoLapTracker`, último instante de cadencia) y un `SharedFlow`. Riesgo de fuga: los trackers son objetos sin referencias a `Context`; se **reinician en `startRide()`** igual que los contadores actuales y se liberan con el servicio.
- **Persistencia:** `stopRide()` → `saveFinalStats()` pasa a escribir también las vueltas (T-04) dentro de la misma coroutine/transacción de cierre de sesión; si falla, la sesión se guarda igualmente (principio de resiliencia de la constitución).
- **Aviso por km (T-03):** `FreeRideService` inyecta `SoundPlayer` (singleton existente, sonido `Sound.BEEP`, sin recursos nuevos) y usa `Vibrator`/`VibratorManager` con el permiso `VIBRATE` ya declarado. El **servicio** dispara sonido y vibración (así suenan con la pantalla apagada) y emite `SharedFlow<KmAlert>`; la pantalla solo pinta el banner (4 s). No se toca el pipeline de audio del Tabata. El autolap deja de usar `totalDistanceMeters` de `_stats` (valor desfasado) y el reloj de pared de 2 s: lo gobierna `AutoLapTracker` con tiempo de cronómetro interpolado.
- **Navegación/back stack:** R8 es una 4ª página del pager interno de `FreeRideScreen` (estado local, `pageCount` 3 → 4, `PageIndicatorBar` con 4 puntos/pestañas, también en landscape). No añade destinos ni altera el back stack. Cero cambios en `NavGraph`.
- **`WorkoutService`/Tabata:** sin cambios.

## Evaluación R8 — ¿pantalla dedicada de vueltas o tercera ventana con tabla desplazable?
Contexto de uso: móvil en marcha, una mano, sol, movimiento. Hoy la página 3 mezcla botón "NUEVA VUELTA", 5 tarjetas y un historial desplegable.

| Criterio | A) Pantalla/overlay dedicado | B) Tabla desplazable en la 3ª ventana |
|---|---|---|
| Columnas completas (10) | Cabe con scroll horizontal y toda la altura | Compite con tarjetas y botón; poco alto útil |
| Densidad | Máxima (toda la pantalla es tabla) | Limitada (comparte espacio) |
| Gestos | Scroll H (columna nº fija) + scroll V independiente, sin competir con el pager | El scroll horizontal de la tabla **choca** con el swipe horizontal del `HorizontalPager` |
| Acceso en marcha | 1 toque desde botón "Vueltas" visible en métricas/mapa/vueltas; Atrás cierra | Dos swipes para llegar; pero ya está "ahí" |
| Riesgo | Más UI nueva; hay que cuidar Atrás | Gestos ambiguos (se cambia de página sin querer) |
| Cambios de navegación | Ninguno si es overlay | Ninguno |

**Decisión final (usuario, Q4): variante de A con 4 ventanas.** El `HorizontalPager` de `FreeRideScreen` pasa de 3 a 4 páginas: 1 Métricas · 2 Mapa · 3 Vueltas resumida (últimas vueltas con ritmo y pasos) · 4 Tabla completa de vueltas. No es un overlay ni una ruta nueva: `NavGraph` no cambia. El botón de vuelta manual está al alcance en las 4 ventanas (RF-8.5): las ventanas 1 y 3 ya traen el suyo dentro de la página (`FreeRideControls*` / «NUEVA VUELTA»), así que la implementación añade **un botón flotante fuera del pager** (`FreeRideScreen`, abajo-izquierda, solo con la ruta en curso y sin pausa) que se dibuja únicamente en las ventanas 2 (Mapa, por encima de la superposición de métricas) y 4 (Tabla). Usa el mismo `onLap` (guarda de 1 s tras Iniciar y mínimo 5 s). No se refactoriza la botonera existente de las ventanas 1 y 3.

Mitigación del conflicto de gestos (scroll horizontal de la tabla vs. swipe del pager): columna nº fija, `horizontalScroll` anidado que cede al pager al llegar al borde (nested scroll), `PageIndicatorBar` pulsable (4 entradas, objetivo táctil mínimo 40 dp, `animateScrollToPage`) para saltar de ventana en un toque. Implementación de la tabla (`ui/freeride/LapTable.kt`): una `LazyColumn` para el scroll vertical y un único `ScrollState` horizontal compartido por la cabecera y todas las filas (la columna nº queda fuera de ese scroll), y la ventana 4 es la última (solo hay conflicto en su borde izquierdo). Si en la prueba real el gesto resulta ambiguo, plan B: desactivar el swipe del pager solo mientras la ventana 4 está activa y navegar con la barra de pestañas. Se valida en T-09 con el usuario.

## Diagnóstico R2 (T-02) — método
1. Añadir logging temporal etiquetado `LapDiag` en `addLap()`, `addAutoLap()`, `startRide()`, `pauseRide()`, `resumeRide()`, `stopRide()`: origen (manual/auto), `isRunning`, `distanceMeters`, duración, timestamp de pared, y *quién* la llamó (stack corto).
2. El usuario reproduce: iniciar → pausar → reanudar → finalizar, y también iniciar con doble toque, sin esperar fix GPS. Aporta `adb logcat -s LapDiag`.
3. Con la evidencia se confirma cuál de H1 (botón bajo el dedo), H2 (agrupación Excel) o H3 (sin fix GPS) es la causa raíz. Se corrige **en el origen** (servicio: `LapGuard`; UI: layout/debounce del botón) y se retira el logging de depuración.
4. Test de regresión de la causa confirmada + tests de `LapGuard`.

## Estrategia de tests por tarea (JUnit puro en `app/src/test`; Compose UI test en `app/src/androidTest` solo donde se indica)
| Tarea | Tests |
|---|---|
| T-01 | `PaceCalculatorTest` (5:30, 6:00, <1 km/h → `--`, fuera de rango), `CadenceDisplayPolicyTest` (sensor emitiendo → cadencia; sin sensor → ritmo; cae por silencio >5 s → ritmo; vuelve → cadencia) |
| T-02 | `LapGuardTest` (0–4 s → rechazada; 5, 10, 20 s con 0 m → aceptada); `LapLifecycleTest`: iniciar/pausar/reanudar/finalizar no producen vueltas (sobre `LapBook`) |
| T-03 | `AutoLapTrackerTest` (5 km → 5 autolaps exactos de 1000 m; salto de 2,4 km → 2 autolaps; sin parcial al parar), `KmAlertGateTest` (una vez por km, no repite) |
| T-04 | `LapBookTest` (10 km con manual en 3,5 km → 10 AUTO + manuales sin alterarlos; manuales encadenadas cubren intervalos contiguos; última manual registrada con dist/tiempo reales); `LapMigrationTest` (esquema 10 coherente) |
| T-05 | `LapRowFormatterTest` (estilo por `type`, ritmo 5:30 y 6:00, distancia 0 → `--`, contraste ≥ 4.5:1); sin androidTest (la pantalla tiene fondo oscuro fijo) |
| T-06 | `LapExportTest` (hoja de vueltas usa `type` persistido), borrado de sesión borra vueltas |
| T-07 | `LapBlockMetricsTest` (pasos y cadencia del bloque; suma pasos manuales ≤ total; sin pasos → `--`; fuente bici → cadencia oculta) |
| T-08 | androidTest/JVM de `SessionLapSummaryModel` (qué campos se muestran por vuelta manual) |
| T-09 | androidTest: 20 vueltas, scroll horizontal con columna nº fija, scroll vertical independiente |
| T-10 | `CurrentLapModelTest` (JVM): ritmo de la vuelta, `--` sin dato, ppm vs rpm, desnivel 0 es dato real |

Al terminar cada tarea el **usuario** ejecuta la **suite completa** (`./gradlew test`, y `connectedAndroidTest` si la tarea añade tests instrumentados) y aporta el resultado.
