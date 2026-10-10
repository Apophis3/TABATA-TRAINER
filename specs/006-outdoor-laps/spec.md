# Spec 006 — Ruta Libre: cadencia/ritmo, vueltas AUTO/MANUAL y tabla de vueltas

## Objetivo
Mejorar la pantalla de actividad en exterior con GPS (**Ruta Libre**, `FreeRideService` + `FreeRideScreen`):
1. Hueco "cadencia" condicional (cadencia ↔ ritmo).
2. Vueltas fiables: sin vuelta fantasma, autolap exacto cada 1 km y vueltas manuales independientes.
3. Lista/tabla de vueltas legible (estilo por tipo, ritmo por vuelta, tabla completa).
4. Resumen de fin de sesión con métricas por vuelta manual.

## Alcance
- **Dentro:** `FreeRideService`, `FreeRideScreen`, `SessionDetailScreen`, `ExportUtils` (hoja de vueltas), Room (`LapEntity`), util/lógica pura nueva.
- **Fuera:** `WorkoutService` / Tabata. El Tabata no tiene "vueltas" (tiene rondas) y no se toca. Si se quiere el hueco cadencia/ritmo en Tabata con GPS → spec aparte (ver Q3).

## Hallazgos del análisis (estado actual del código)
| # | Hallazgo | Dónde |
|---|----------|-------|
| H-A | Las vueltas **solo existen en memoria** (`_laps`). `GpsDao.insertLap` existe pero **nadie lo llama**; el detalle de sesión no muestra vueltas. | `FreeRideService`, `GpsDao`, `SessionDetailScreen` |
| H-B | El Excel reconstruye las vueltas agrupando puntos GPS por `round = laps.size + 1` y decide AUTO/MANUAL por "distancia entre 900 y 1100 m" (una manual de 1 km sale como AUTO). | `ExportUtils.kt:547-578` |
| H-C | AUTO y MANUAL comparten una única numeración (`laps.size + 1`) y el contador `lapsCount`; la tarjeta "Vuelta N" de la página 3 muestra `laps.size + 1` (cuenta autolaps). | `FreeRideService.kt:581,628`, `FreeRideScreen.kt:533` |
| H-D | Autolap: dispara **una sola** vuelta aunque el GPS cruce 2 km de golpe (se pierde un km); la vuelta mide `total − lastAutoLap` (≈1.03 km, no 1.000); la "protección" de 2 s usa reloj de pared y enmascara duplicados en vez de evitarlos. | `FreeRideService.kt:774-794` |
| H-E | `addLap()` solo comprueba `isRunning`: acepta vueltas con distancia 0 y ~2 s (sin fix GPS, recién arrancado/reanudado). | `FreeRideService.kt:577` |
| H-F | **Hipótesis fantasma H1:** al pulsar Iniciar, la fila de botones cambia de `[Play]` a `[Stop][Vuelta][Pausa]` y el botón dorado de Vuelta (60 dp) queda justo bajo el dedo (donde estaba Play, 72 dp). Un doble toque o un rebote en iniciar/reanudar crea una vuelta de ~2 s con ceros. | `FreeRideScreen.kt:1307-1343` |
| H-G | **Hipótesis fantasma H2:** en el Excel, un `round` con 1-2 puntos GPS (vuelta manual + autolap casi simultáneos) sale como vuelta de 0 s / 0 m / 0 km/h. | `ExportUtils.kt` |
| H-H | **Hipótesis fantasma H3:** vuelta manual pulsada antes del primer fix GPS (distancia 0). Sin guarda mínima. | `FreeRideService.kt:577` |
| H-I | Todos los sensores de cadencia del proyecto son **CSC (BLE 0x1816) / ANT+ bike cadence = rpm de biela**. Los pasos y ppm de carrera salen del **podómetro del móvil** (`StepCounter`). No hay sensor de cadencia de carrera (RSC). | `BleManagers.kt`, `SensorManager.kt`, `StepCounter.kt` |
| H-J | No existe cálculo de ritmo (min/km) en ningún sitio. | — |
| H-K | `isCadenceConnected` queda `true` aunque el sensor deje de emitir (solo pasa a `false` en `Disconnected`/`Error`) → no sirve para "emitiendo datos". | `FreeRideService.kt:395-430` |
| H-L | `deleteSession` no borra `lap_records` (huérfanos). Hoy no se nota porque no se guarda nada. | `SessionDetailScreen.kt` (ViewModel) |
| H-M | `app/src/test` solo tiene JUnit (sin coroutines-test ni mockk). `androidTest` no existe, pero `compose.ui.test.junit4` ya está en `build.gradle.kts` → **no hace falta tocar Gradle**. | `app/build.gradle.kts` |

## Requisitos

### R1 — Hueco cadencia/ritmo condicional
- RF-1.1: en Ruta Libre (portrait, landscape métricas y landscape vueltas) el hueco "Cadencia" muestra **cadencia** si hay sensor emparejado **y** emitiendo (último dato ≤ `CADENCE_STALE_MS` = 5 s); en cualquier otro caso muestra **ritmo** (mm:ss, "/km") en ese mismo hueco, con la etiqueta cambiada ("Ritmo").
- RF-1.2: conmutación en vivo en ambos sentidos (caída del sensor → ritmo; vuelta → cadencia) sin esperar a otra acción del usuario.
- RF-1.3: ritmo = tiempo/distancia sobre una ventana móvil de 10 s de movimiento. Válido de 2:00/km a 30:00/km (carrera y caminata). Fuera de rango o velocidad < 1 km/h → `--`. Nunca "0:00".
- RF-1.4: la unidad de la cadencia sigue la fuente (ver Q1).

### R2 — Vuelta fantasma
- RF-2.1: **diagnosticar la causa raíz antes de parchear** (T-02, con Logcat real). Prohibido tapar solo con un filtro de duración en la UI.
- RF-2.2: iniciar, pausar, reanudar y finalizar **no crean** ninguna vuelta. Parar la sesión **no cierra** una vuelta abierta.
- RF-2.3: una vuelta **manual** con duración < 5 s **no se crea ni se persiste**; se avisa con un aviso breve ("Vuelta ignorada: dura menos de 5 s"). **Solo cuenta el tiempo, nunca la distancia**: en series explosivas o ejercicios en el sitio el GPS no se mueve y una vuelta de 10–20 s con 0 m es válida (decisión del usuario 2026-10-10; su ritmo se muestra "--" por R6).

### R3 — Autolap de 1 km
- RF-3.1: autolap en cada múltiplo de 1000 m de la **distancia total acumulada GPS** y solo ahí. Cada AUTO mide exactamente 1000 m (el tiempo del cruce se interpola entre la muestra anterior y la actual).
- RF-3.2: si una muestra cruza varios km a la vez, se crean todos los autolaps intermedios (ninguno se pierde).
- RF-3.3: al finalizar no se crea un autolap parcial.
- RF-3.4: aviso al cruzar cada km (km 1, 2, 3…): sonido + vibración + banner en pantalla ("KM 3 · 5:42/km"), **una sola vez por km**.

### R4 — Vueltas manuales independientes
- RF-4.1: el usuario marca vueltas manuales de cualquier longitud. Una manual no reinicia, desplaza ni cancela el autolap.
- RF-4.2: dos series con numeración propia: AUTO #1, #2… y MANUAL #1, #2… (`type` = `AUTO` | `MANUAL`).
- RF-4.3: la manual cubre desde la manual anterior (o el inicio) hasta el instante de pulsar, con su distancia y tiempo reales. La última manual marcada siempre queda registrada y visible.
- RF-4.4: la tarjeta "Vuelta" de la página 3 pasa a contar **solo manuales** (la vuelta manual en curso).

### R5 — Estilo por tipo en la lista
- RF-5.1: AUTO: tenue, color secundario, peso normal. MANUAL: resaltada, **negrita**, color de acento distinto.
- RF-5.2: el estilo sale **solo de `lap.type`** (no de la posición ni de la distancia).
- RF-5.3: contraste ≥ 4.5:1 en tema claro y oscuro. Refuerzo no cromático: etiqueta "A"/"M" y, en manuales, icono bandera.

### R6 — Ritmo por vuelta
- RF-6.1: cada fila muestra `nº · distancia · tiempo · ritmo`; ritmo = tiempo de **esa** vuelta / distancia de **esa** vuelta, formato `mm:ss/km`.
- RF-6.2: distancia 0 o sin dato → `--` (nunca `0:00`).
- RF-6.3: vale para AUTO y MANUAL (en manuales con su distancia real).

### R7 — Resumen final: métricas por vuelta manual
- RF-7.1: en el detalle de sesión (pantalla de resumen al terminar), cada vuelta manual muestra: tiempo, distancia, FC media y máx, **pasos del bloque**, **cadencia media de carrera (ppm)**, **ritmo medio**, velocidad media, desnivel+.
- RF-7.2: todo se calcula sobre la **ventana temporal de esa vuelta manual**.
- RF-7.3: pasos de la vuelta = pasos entre su inicio y su fin. La suma de pasos de todas las manuales ≤ pasos totales de la sesión.
- RF-7.4: cadencia del bloque = pasos del bloque / duración del bloque en minutos.
- RF-7.5: la cadencia (ppm) **solo** en actividades de carrera/caminata. Si la fuente de cadencia es un sensor de bici (rpm) → no se muestra ese dato (no se mezcla rpm de biela con ppm de zancada).
- RF-7.6: sin dato de pasos → `--`, nunca 0.

### R8 — Pantalla de vueltas (evaluación en `plan.md`; propuesta: adoptar)
- RF-8.1: tabla con **nº · tipo · distancia · tiempo · ritmo · FC media · FC máx · pasos · cadencia · desnivel**.
- RF-8.2: scroll horizontal con la columna nº fija a la izquierda; scroll vertical independiente.
- RF-8.3: máxima densidad (tipografía compacta, filas estrechas). Estilo AUTO tenue / MANUAL negrita (R5).
- RF-8.4: la tabla es la **ventana 4** del pager (acceso por swipe o por la barra de pestañas en un toque). La **ventana 3** se mantiene como resumen de las últimas vueltas, enriquecida con ritmo y pasos. *(Sustituido por RF-8.7 tras la prueba de T-09.)*
- RF-8.5: el botón de vuelta manual de la botonera inferior sigue visible y operativo en las 4 ventanas. *(Implementación T-09: las ventanas 1 y 3 conservan su botón; un botón flotante fuera del pager cubre las ventanas 2 y 4.)*
- RF-8.6: el scroll horizontal de la tabla no impide cambiar de ventana: en los bordes de la tabla el gesto pasa al pager.
- RF-8.7 *(decisión del usuario tras probar T-09)*: la **ventana 3 ya no lleva «Historial de Vueltas»** (está en la tabla de la ventana 4). Pasa a ser el **panel de la vuelta manual en curso**: nº de vuelta, tiempo, distancia, ritmo medio de la vuelta, velocidad, FC, pasos, cadencia (ppm; rpm del sensor si hay sensor de bici activo, nunca mezclados) y desnivel+, todos de la ventana de ESA vuelta, más una franja con la **última vuelta manual** cerrada (tiempo, ritmo, FC, pasos). Sin dato → `--`, nunca 0.

## Persistencia (soporte de R4/R7/R8)
- RF-P1: las vueltas se guardan en `lap_records` al terminar la sesión (ambas series, íntegras). Esquema nuevo en `plan.md` (migración 9 → 10, autorizada por esta spec).
- RF-P2: borrar una sesión borra sus vueltas. El Excel usa las vueltas persistidas y su `type` (se elimina la heurística de 900–1100 m).

## Preguntas abiertas (se resuelven al empezar la tarea afectada)
- **Q1 — RESUELTA (usuario, 2026-10-10):** hay dos cadencias distintas que no se mezclan. (a) **Cadencia de bici** = sensor CSC/ANT+ de biela, unidad **rpm**, valor en vivo en el hueco "Cadencia". (b) **Cadencia de carrera** = pasos del podómetro, unidad **ppm**, calculada como media del tramo (pasos del bloque ÷ minutos del bloque) por vuelta; no se muestra en vivo en el hueco (ahí, sin sensor, va el ritmo).
- **Q2 (T-07) — RESUELTA (adoptada la propuesta, 2026-10-10):** cómo saber que la actividad es de bici para ocultar la cadencia ppm (RF-7.5). Propuesta: la vuelta se marca `cadenceSource = BIKE_SENSOR` si el CSC/ANT+ estuvo emitiendo dentro de su ventana; entonces no se muestra ppm. Pendiente de confirmar al empezar T-07.
- **Q3 — RESUELTA (usuario):** el hueco cadencia/ritmo **no** se extiende al Tabata con GPS por ahora.
- **Q4 — RESUELTA (usuario):** el `HorizontalPager` de `FreeRideScreen` pasa de 3 a **4 ventanas** (1 Métricas · 2 Mapa · 3 Vueltas resumida · 4 Tabla de vueltas). Sin ruta nueva en `NavGraph`. La botonera inferior con el botón de vuelta manual **permanece fija y visible en las 4 ventanas**.

## Definition of Done (toda tarea)
1. Código + tests nuevos escritos. 2. El **usuario** compila, instala y ejecuta la **suite completa** `./gradlew test` (y `connectedAndroidTest` cuando la tarea añada tests instrumentados) — el agente no ejecuta Gradle ni `adb install` (Clave 5). 3. El usuario aporta evidencia (resultado de tests / Logcat). 4. Se marca la tarea en `tasks.md` y se anota en `00_estado_proyecto_y_memoria.md`.
