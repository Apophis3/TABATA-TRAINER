# Estado del proyecto y memoria de sesiones — Tabata Trainer

Bitácora de trabajo. Cada sesión se anota aquí: qué se tocó, por qué, en qué quedó
y qué falta. Complementa a `01_historico_errores.md` (errores resueltos) y a la
memoria privada de Claude. **Lo más reciente arriba.**

---

## 2026-11-21 — Spec 007 · T-03: prueba real 1.6.111 y arreglo del tirón ANT+

### Contexto
El usuario probó la 1.6.111 (T-03) con las dos bandas; no tiene los cadenciómetros a mano. Logcat leído por adb.

### Visto
- Dos bandas en HR1/HR2 por ANT+ con la búsqueda compartida; HR2 pasó de BLE a ANT+ en 16 s sin cambiar de hueco.
- Tirón de ~1 s cada 30 s: el cierre de la búsqueda ANT+ (`MultiDeviceSearch.close()`) bloqueaba el hilo principal.
- Al arrancar, ADAPTER_NOT_DETECTED durante 1–2 min (también antes de T-03): por eso una banda tarda en pasar a ANT+.
- Ruta: un solo "HR" y una cadencia → T-05. Detalle de sesión sin C2 → fuera de alcance (Room).

### Hecho
- `plan.md` §4 anotado. `AntRadio`: cierre en segundo plano y en orden; `awaitClosed()` antes de abrir otra
  búsqueda y antes de `requestAccess` en `HeartRateHub` y `CadenceHub`.

### Validación (1.6.113)
- Suite 187/187 verde (22 clases). Logcat: el cierre de la búsqueda ya no bloquea el hilo principal; ANT+ a
  los 3 s del arranque; dos bandas en HR1/HR2 por ANT+; un corte ANT+→BLE de 14 s en HR2 sin cambiar de hueco.
- T-02 y T-03 marcadas `[x]`. A T-07: cadencia real y HR2 apagado en Home al quitar una banda.

### Pendiente
- Commit (cuando lo pida el usuario). Siguiente tarea: T-04, cuando el usuario la pida.

---

## 2026-11-21 — Spec 007 · T-03: cadencia (CadenceHub y radio ANT+ compartida)

### Contexto
T-02 con prueba real OK y el arreglo de batería ya en el commit `9c89ebb` (falta compilarlo). El usuario pidió
empezar T-03 sin compilar ni instalar.

### Hecho
- `plan.md` §4 ampliado antes del código: 0 rpm válido, emparejado con ceros, radio ANT+ compartida.
- `sensor/AntRadio.kt`: una única búsqueda ANT+ para pulso y cadencia (la búsqueda ocupa la radio; dos
  búsquedas a la vez se pisaban). `HeartRateHub` pasa a usarla.
- `sensor/CadenceHub.kt` (espejo de `HeartRateHub`) + `sensor/CscParser.kt`. C1/C2 con las mismas reglas de
  huecos que HR1/HR2; parado = 0 rpm sin desconectar.
- `PairingPolicy`: solo ceros no empareja por valores (evita juntar dos cadenciómetros distintos parados).
- `SensorManager` queda como fachada (contrato igual). Borrado `BleManagers.kt` y todo el código viejo de
  cadencia y autoconexión (≈2 400 líneas). Room sin tocar.
- Tests: `CadenceSlotAllocatorTest`, `CadencePairingTest`, `CscParserTest`.
- Primera pasada de la suite (usuario): los tests nuevos de cadencia OK; fallaba uno de T-02
  (`una ANT caida un momento no cede su hueco…`). Causa: la cuarentena de 30 s, al cumplirse sin hueco libre,
  se reiniciaba, y el aparato nunca llegaba a usar el hueco que quedaba libre. Corregido en
  `DualSlotAllocator.assignPending` (la espera se borra solo al dar hueco).

### Pendiente
- Commit de T-03 (con el arreglo de cuarentena) hecho a petición del usuario; traspaso listo para hilo nuevo.
- Usuario: repetir la suite y prueba real (Logcat `🚴` y `💓`; el agente los lee por adb): un cadenciómetro (ANT+ y/o BLE) en
  C1; parar de pedalear → 0 rpm en ~3 s sin perder el hueco; dos cadenciómetros → C1 y C2; las dos bandas de
  pulso siguen conectando con la búsqueda compartida. Con eso, cerrar T-02 y T-03.

---

## 2026-11-21 — Spec 007 · T-02: reparto de huecos (DualSlotAllocator)

### Contexto
T-01 validada por el usuario: compila, suite OK y prueba real con dos bandas puestas una tras otra → HR1 y
HR2, las dos por ANT+. Esperaba ya la hoja de dispositivos (es T-06) y HR1/HR2 en Ruta (T-05); pidió seguir
con T-02. Pregunta abierta: abrir la hoja también tocando HR1/HR2/C1/C2 en Tabata (además de Home).

### Hecho
- `sensor/DualSlotAllocator.kt` (lógica pura, común a pulso y cadencia): sale de `HeartRateHub.tick()` el
  reparto de huecos, la cuarentena, el relevo y la elección de fuente. Novedades:
  - Hueco preferido (lo asigna el usuario desde la hoja, T-06; T-02 no lo guarda solo). Reserva blanda.
  - ANT+ desplaza a BLE: con 2 aparatos ANT+ ningún hueco se queda en un aparato solo-BLE (en el mismo
    hueco). Un hueco ANT+ caído no pasa a un solo-BLE antes de 60 s (evita saltos en búsquedas ANT+).
  - `SlotStatus` por hueco (aparato, alias, señal, protocolo, valor, batería) → `HeartRateHub.slotStatus()`.
- `HeartRateHub`: usa el asignador; sin observadores para las radios a los 5 s pero recuerda los huecos
  55 s más (R9). `restart()` sigue siendo parada completa. Contrato de `SensorManager` intacto.
- `DeviceDirectory` (interfaz) en `SensorDeviceRegistry.kt`, implementada por `DeviceBook` y el registro.
- Tests: `DualSlotAllocatorTest` (dual en un hueco, caída/vuelta ANT+, dos bandas con BLE antes → ANT+,
  ANT caída no cede hueco, hueco preferido, reserva blanda, reanudar tras parar radios).

### Pendiente
- Usuario: compilar, pasar la suite de tests y prueba real (Logcat `💓`): dos bandas encendiendo antes los
  BLE; una banda dual en reposo solo en HR1; salir 20–30 s de las pantallas con sensores sin que cambien los
  huecos.

### Después (misma fecha)
- Commits: spec 006 T-11 en `spec-006-outdoor-laps`; spec 007 T-01/T-02 en la rama nueva
  `spec-007-dual-signal-devices`. Traspaso para hilo nuevo en `specs/007-dual-signal-devices/traspaso.md`.
- Decisión del usuario (R8.1–R8.3 en la spec): la hoja "Dispositivos" se abre tocando la barra de sensores en
  todas las pantallas (Home, Tabata, Ruta también en marcha, Config) con una barra común `SensorBar`; la
  flecha de refrescar sigue reconectando. Va en T-06.
- Prueba real de T-02 (usuario): ANT+↔BLE, dos bandas, poner y quitar → todo bien. Fallo: tras quitar una
  banda, Home seguía mostrando HR2 activo con batería. Causa: Home deduce HR2/C2 "conectado" de la batería
  y el asignador mantenía la batería de las señales del aparato aunque estuvieran desconectadas. Arreglo en
  `DualSlotAllocator.pickSource`: sin fuente → batería null; la batería prestada solo de señales conectadas.
  Test `aparato quitado deja el hueco sin bateria`. El estado real por hueco en Home sigue siendo T-06.

---

## 2026-11-21 — Spec 007 · T-01: aparatos de doble señal (registro genérico + emparejado seguro)

### Contexto
El usuario ve un solo pulsómetro en HR1 y HR2: la app detecta señales (ANT+ y BLE), no aparatos. Pide un
aparato = un hueco (HR1/HR2, C1/C2), ANT+ siempre preferente, ver hueco y señal en toda pantalla, que aguante
dos bandas a la vez y el mismo criterio para la cadencia. Decisiones: emparejado automático (solo si es seguro)
+ manual; gestión en hoja desde el bloque de sensores de Home; hueco fijo por aparato; Ruta con HR1/HR2 y
C1/C2 separados.

### Hecho
- Documentación: `specs/007-dual-signal-devices/{spec,plan,tasks}.md` (diagnóstico completo, 7 tareas).
- T-01 (código): `sensor/SensorDeviceRegistry.kt` sustituye a `HrDeviceRegistry.kt` — `DeviceBook` para pulso
  y cadencia, fichas de una señal, hueco preferido, manual manda, separar bloquea la pareja, formato v2 que
  migra el de la spec 003 (mismo archivo de preferencias). `sensor/PairingPolicy.kt`: guarda el vínculo solo
  si el nº ANT+ va en el nombre BLE o si durante 30 s solo se ven 1 ANT+ y 1 BLE sin pareja; si no, solo en
  la sesión (pareja mutua sin empate, también en reposo hasta 4 bpm). Al arrancar, el registro vuelca en
  Logcat (`💓🚴 Registro`) lo guardado, para detectar vínculos erróneos heredados de la spec 003.
- Conocido y anotado en T-02: con dos bandas indistinguibles los huecos aún pueden quedar en BLE (o
  BLE:A + ANT:A); no hacer todavía la prueba de dos bandas.
- `HeartRateHub`: solo cambian el tipo del registro y `detectTwins` (ahora usa `PairingPolicy`). Huecos,
  cuarentena y relevo sin tocar (T-02).
- Tests: `DeviceBookTest`, `PairingPolicyTest` (sustituyen a `HrDeviceBookTest`).

### Pendiente
- Usuario: compilar y `./gradlew test`. Siguiente tarea (T-02, huecos) solo tras su confirmación.
- Los cambios de la spec 006 (T-11, mapa) siguen sin confirmar en la rama `spec-006-outdoor-laps`; decidir si
  se confirman antes de abrir la rama `spec-007-dual-signal-devices`.

---

## 2026-11-21 — Ruta Libre: valor de Ritmo solapado en la ventana Métricas

### Contexto
Captura en exterior caminando: la celda «Ritmo» mostraba `10:15` partido en dos líneas encima de sí
mismo y la unidad `/km` en vertical letra a letra (el valor a 55 sp no cabía en media pantalla).

### Hecho
- `FreeRideScreen.LapMetricCardNoIcon`: el valor pasa a `BasicText` de una línea con
  `TextAutoSize.StepBased` (de 55 sp baja hasta 16 sp si no cabe); la unidad se mide primero y no se
  parte. Afecta a todas las celdas que usan esa tarjeta (Métricas y tablas de vuelta), solo si no caben.
- Lógica sin cambios (`CadenceDisplayPolicy`): con sensor de cadencia emitiendo → «Cadencia» en rpm;
  sin sensor o >5 s sin datos → «Ritmo» en min/km del GPS (caminar o correr). Validado por el usuario.
- Franja de pasos (Pasos · Pasos/min · Zancada) recortada en portrait: se veían los números pero no las
  etiquetas. `MetricsPage`: las 4 tarjetas van en un `BoxWithConstraints` con weight, la franja se mide
  antes y el tamaño del número de las tarjetas se calcula con el alto sobrante (28–55 sp). Etiquetas
  de la franja a 12 sp y más contraste. Landscape sin cambio de layout (ya tenía scroll).

### Estado
- Validado por el usuario en el S22+ (portrait): celda Ritmo y franja de pasos se leen enteras.

---

## 2026-11-21 — Spec 006 · T-11 (R9): mapa en vivo sin zoom

### Contexto
Prueba en exterior: en la ventana Mapa, durante la actividad, el mapa queda a escala mundial y no deja
hacer zoom; en el resumen final el track se ve bien. El usuario pide ir fallo a fallo y con tests.

### Hecho
- Causa en código: `RealTimeGoogleMap` lanzaba `animate(newLatLng)` en cada fix (~1 s), que corta el
  pellizco en curso y conserva cualquier zoom alejado; +/- y logo tapados por la franja de métricas.
- Nuevo `util/map/MapFollowPolicy.kt` (+ `MapFollowPolicyTest`): no sigue mientras el usuario toca ni
  10 s después; luego centra con su zoom; si la cámara está en un zoom que el usuario no eligió
  (p. ej. mundial) restaura el suyo o 16 si nunca tocó.
- `FreeRideScreen`: `move` instantáneo según la política, detección de gesto/+/- por
  `cameraMoveStartedReason`, `contentPadding` inferior de 172 dp. Fuera `savedZoom`/`savedPosition`.
- Log temporal `MapDiag` (zoom al aparecer el mapa, zoom que deja el usuario, restauraciones) para
  confirmar la causa del zoom mundial en Logcat; retirar al validar.
- Spec R9 (RF-9.1…9.4) y tarea T-11 añadidas. Pager, `NavGraph` y `FreeRideService` sin tocar.

### Pendiente
- Usuario: compilar, `./gradlew test`, instalar y probar zoom durante una ruta; aportar Logcat `MapDiag`.
- Detectado y no tocado (fallo aparte): la etiqueta «Mapa» del indicador de páginas se solapa con el
  tiempo de la franja de métricas.

---

## 2026-10-10 — Spec 006: Ruta Libre (cadencia/ritmo, vueltas AUTO/MANUAL, tabla de vueltas)

### Contexto
Brief del usuario R1–R8 para la pantalla de actividad exterior con GPS. Sesión de
**análisis y planificación**: no se ha tocado código Kotlin.

### Hecho
- Creada `specs/006-outdoor-laps/` con `spec.md`, `plan.md` y `tasks.md` (T-01…T-09
  secuenciales e independientes + mejoras M-01…M-05 pendientes de aprobación).
- Hallazgos clave: las vueltas **nunca se persisten** (`insertLap` sin llamadas);
  numeración AUTO/MANUAL compartida; autolap pierde km si el GPS salta y mide ≈1,03 km;
  no existe ritmo en ningún sitio; la cadencia de sensor es de bici (rpm), los pasos
  salen del podómetro; Excel infiere AUTO/MANUAL por distancia 900–1100 m.
- Vuelta fantasma: hipótesis H1 (botón Vuelta bajo el dedo al pulsar Iniciar), H2
  (Excel), H3 (sin fix GPS). Se confirma con Logcat en T-02.

### Decisiones del usuario
- Q1: dos cadencias distintas — bici (sensor, rpm, en vivo) y carrera (pasos del podómetro,
  ppm, media por tramo/vuelta). Q3: sin hueco cadencia/ritmo en Tabata con GPS por ahora.
- Q4/R8: el pager de FreeRideScreen pasa a 4 ventanas (4ª = tabla de vueltas); botonera
  inferior con la vuelta manual fija en todas.

### T-10 (código escrito; SIN PROBAR en dispositivo)
- Petición del usuario tras probar T-09: la ventana 3 seguía mostrando «Historial de Vueltas» (colapsado, sin pasos a la
  vista). Ahora es el panel de la vuelta manual en curso: Vuelta · Tiempo · Distancia · Ritmo · Velocidad · FC · Pasos ·
  Cadencia · Desnivel+, más franja «Última vuelta» (tiempo, ritmo, FC, pasos). El historial se quita (vive en la ventana 4).
- `FreeRideStats` + `currentLapSteps` y `currentLapElevationGainM` (se refrescan cada segundo; `FreeRideService`
  sin cambios de ciclo de vida). Nuevo `util/laps/CurrentLapModel.kt` (+ `CurrentLapModelTest`).
- `LapHistoryList` queda sin uso en pantalla (se conserva el fichero; candidata a limpieza si el usuario lo aprueba).
- Pendiente: usuario compila, pasa la suite y prueba la ventana 3 (portrait y landscape) con una ruta con 1–2 vueltas.
- Checklist estructurado de pruebas en exterior (GPS + ANT+) para validar T-04…T-08 y T-10:
  `specs/006-outdoor-laps/checklist-pruebas-exterior.md`. El usuario va marcando y responde por fase.

### T-09 (validada por el usuario: compiló y probó, todo bien)
- Pager de 3 → 4 ventanas en `FreeRideScreen` (portrait y landscape): 1 Métricas · 2 Mapa · 3 Vueltas · 4 Tabla.
  Sin cambios en `NavGraph` ni en el ciclo de vida de `FreeRideService`.
- Nuevo `util/laps/LapTableModel.kt` (+ `LapTableModelTest`, incluye caso de 20 vueltas): celdas de la tabla; `--` sin
  dato; cadencia ppm (pasos ÷ min de esa vuelta), con sensor de bici se muestra rpm y nunca ppm; estilo R5.
- Nuevo `ui/freeride/LapTable.kt`: columna nº fija + 9 columnas con scroll horizontal compartido (un `ScrollState`
  para cabecera y filas) y scroll vertical independiente (`LazyColumn`). El relevo al pager en los bordes es el
  comportamiento por defecto de `horizontalScroll` (nested scroll): SIN VALIDAR en dispositivo.
- `PageIndicatorBar`: 4 entradas pulsables (toque = salto a la ventana).
- Botón de vuelta flotante fuera del pager en las ventanas Mapa y Tabla (las ventanas 1 y 3 ya tenían el suyo).
- `LapHistoryList` (ventana 3): nueva columna "Pasos".
- Plan B si el gesto es ambiguo: desactivar el swipe del pager solo en la ventana 4 y navegar con la barra.
- No se ha creado androidTest (no existe el módulo; M-04): el test de 20 vueltas es JVM sobre el modelo.
- Pendiente: usuario compila, pasa la suite y prueba T-04…T-09 juntas (especialmente: arrastrar la tabla en
  vertical y horizontal, relevo al pager en el borde izquierdo, toque en la barra de pestañas, botón de vuelta en
  Mapa y Tabla, tabla con ≥20 vueltas, landscape).

### T-08 (código escrito; SIN PROBAR en dispositivo)
- Nuevo `util/laps/SessionLapSummaryModel.kt` (+ `SessionLapSummaryModelTest`): por cada vuelta MANUAL, tiempo,
  distancia, ritmo medio, vel. media, FC media/máx, pasos, cadencia (ppm) y desnivel+; `--` sin dato (nunca 0);
  la cadencia ppm se omite si la fuente es `BIKE_SENSOR`; las AUTO no llevan resumen.
- Nuevo `util/laps/LapDuration.kt`: duración de la vuelta guardada = distancia ÷ vel. media (tiempo de ruta, sin
  pausas); 0 m → reloj de pared. Ahora también lo usa el Excel (antes: fin − inicio de reloj de pared, que
  incluía pausas) — afecta a la columna "Tiempo (s)" de la hoja Laps de T-06.
- `SessionDetailScreen`: nueva tarjeta `ManualLapsSummaryCard` ("Vueltas manuales (n)") tras las métricas GPS,
  visible solo si hay manuales. Usa `laps` que ya expone `SessionDetailViewModel` desde T-06 (sin cambios en el VM).
- Pendiente: usuario compila, pasa la suite y prueba T-04…T-08 juntas (finalizar ruta con 2–3 manuales y abrir el
  detalle desde el Historial; probar también una ruta antigua sin vueltas guardadas: no debe salir la tarjeta).

### T-07 (código escrito; SIN PROBAR en dispositivo)
- Q2 resuelta con la propuesta de la spec: `cadenceSource = BIKE_SENSOR` si el sensor de pedal emitió dentro de la
  ventana de la vuelta; si no, `PEDOMETER` si hay pasos; si no, `NONE`.
- Nuevo `util/laps/LapBlockMetrics.kt` (+ `LapBlockMetricsTest`): pasos y desnivel de la ventana = diferencia de
  contadores acumulados (`BlockCounters`); `cadencePpm(steps, duración, fuente)` = pasos ÷ minutos, `null`
  con sensor de bici o sin pasos (`--`, nunca 0). Las ventanas contiguas no cuentan nada dos veces.
- `FreeRideService` (solo estado interno, sin tocar el ciclo de vida): foto de contadores al abrirse la ventana
  de cada serie (`manualWindowStartCounters`, `autoWindowStartCounters`), `stepsSeen` (0 pasos ≠ sin podómetro),
  `currentCounters()`. `LapData.steps/elevationGain/cadenceSource` ya salen rellenos y se guardan en `lap_records`.
  Sin cambios de esquema Room ni de contratos.
- Límites conocidos: (1) las AUTO se procesan al llegar el fix GPS, así que los pasos de su frontera llevan el
  retraso de ese fix (~1 s); las MANUAL son exactas (instante del botón). (2) En un hueco de GPS que cruza varios
  km de golpe, esas AUTO quedan sin pasos/desnivel. (3) Sin altitud GPS el desnivel queda sin dato, no 0.
- Aún no se muestra en pantalla: la UI llega en T-08 (resumen) y T-09 (tabla).
- Pendiente: usuario compila, ejecuta la suite y prueba T-04…T-07 juntas (en una carrera con vueltas manuales,
  comprobar en Logcat/BD que `steps` de cada manual suma ≤ pasos totales).

### T-06 (código escrito; SIN PROBAR en dispositivo)
- Excel: `ExportUtils.exportToExcel(..., laps)` recibe las vueltas persistidas; la hoja "Laps" sale de
  `LapExportRows` (tipo Auto/Manual = `type` guardado; fuera la heurística 900–1100 m y la agrupación por
  `gpsPoints.round`). Columnas: Lap #, Tipo, Nº en su serie, Tiempo, Dist. m/km, Vel. media/máx, HR media/máx,
  Cadencia media (rpm), Pasos; `--` si no hay dato. Sesiones sin vueltas guardadas → no hay hoja "Laps".
  Tiempo de la vuelta = fin − inicio en reloj de pared (incluye pausas si las hubo).
- Borrado: nuevo `SessionDeletion` (vueltas → GPS → lecturas → sesión). Lo usan `SessionDetailViewModel` y
  `HistoryViewModel` (que antes solo borraba la fila de la sesión y dejaba huérfanos GPS/lecturas/vueltas).
  `HistoryViewModel` ahora inyecta también `GpsDao` y `SensorReadingDao` (ya provistos por Hilt).
- `SessionDetailViewModel` expone `laps` (la reutilizará T-08).
- Tests nuevos: `LapExportTest`. Pendiente: usuario compila, ejecuta la suite y prueba T-04+T-05+T-06 juntas
  (finalizar una ruta con autos y manuales → exportar Excel → comprobar hoja "Laps"; borrar desde Historial y
  desde Detalle).

### T-05 (código escrito; SIN PROBAR en dispositivo)
- Nuevo `util/laps/LapRowFormatter.kt` (+ `LapRowFormatterTest`): fila = etiqueta A/M + nº de su serie ·
  distancia · tiempo · ritmo; ritmo de ESA vuelta, `--` si distancia 0 o > 60:00/km; estilo solo
  por `type` (AUTO tenue/normal `#B0BEC5`; MANUAL negrita, acento ámbar `#FFB74D`, bandera y fondo tintado);
  contraste ≥ 4.5:1 verificado en test sobre el fondo de la pantalla (#36454F / #2D2D2D).
- Nuevo `ui/freeride/LapHistoryList.kt`: composable único para portrait y landscape (antes había
  dos bloques duplicados en `LapsPage` y `LandscapeLapsPage`). Columnas: # · Dist. · Tiempo · Ritmo · FC
  (la columna Vel. se sustituye por Ritmo; la tabla completa de 10 columnas llega en T-09).
  Fuera el sombreado por posición (par/impar) y los colores verde/naranja anteriores.
- Pendiente: usuario compila, ejecuta la suite y revisa en móvil (vertical y horizontal) con vueltas
  AUTO y MANUAL mezcladas: legibilidad al sol, ritmo correcto en cada fila, `--` en una manual con 0 m.

### T-04 (código escrito; SIN PROBAR en dispositivo — el usuario aclaró que no hizo las comprobaciones de T-04/T-05/T-06)
- Nuevo `util/laps/LapBook.kt` (`LapType`, `CadenceSource`, `LapRecord`, `LapBook`): dos series
  independientes AUTO/MANUAL con numeración propia (`typeNumber`) y orden global (`order`); ventana
  de cada manual contigua con la anterior; manual < 5 s rechazada sin mover la ventana;
  `lastManual` = última manual con distancia/tiempo reales. Tests `LapBookTest`.
- Room **9 → 10**: `lap_records` + `type`, `typeNumber`, `steps`, `cadenceSource`
  (`MIGRATION_9_10` registrada en `ALL_MIGRATIONS`); `LapEntity` ampliada. Test `LapMigrationTest`
  (el esquema 10 y la migración añaden exactamente las mismas columnas; mapeo `LapData.toEntity`).
- `FreeRideService`: `addLap()`/`addAutoLap()` usan `LapBook` (fuera las variables sueltas
  `lastLap*`/`lastAutoLap*`); `LapData` gana `type`, `typeNumber`, `steps`, `elevationGain`,
  `cadenceSource`, `startDistanceMeters`, `startTimestamp` (`isAutoLap` es ahora derivada);
  `FreeRideStats.manualLapsCount`; **las vueltas se guardan por fin** en `stopRide()` →
  `saveLaps()` (borra y reinserta; si falla, la sesión ya está guardada).
- `FreeRideScreen`: la tarjeta "Vuelta" cuenta solo manuales (`manualLapsCount + 1`) y la columna "#" de
  la lista muestra el número dentro de su serie (la lista sigue mezclando AUTO ⚡ y MANUAL hasta T-05).
- Pendiente: usuario compila (la migración 9→10 se aplica al instalar sobre la app existente),
  ejecuta la suite, y en una ruta comprueba: manuales y autos no se alteran entre sí; "Vuelta" de la
  tarjeta sube solo con el botón; tras finalizar, las vueltas quedan guardadas (Logcat `💾 N vueltas guardadas`).

### T-03 (validada por el usuario: compiló y probó, todo bien)
- Nuevo `util/laps/AutoLapTracker.kt` (cruces de 1000 m con tiempo interpolado, multi-km, sin parcial),
  `KmAlertGate.kt` (+ `KmAlert.bannerText()`), tests `AutoLapTrackerTest` y `KmAlertGateTest`.
- `FreeRideService`: `processGpsReading` usa el tracker (fuera el disparo con `_stats` desfasado y la
  protección de 2 s de reloj de pared); `addAutoLap(crossing, resetCounters)` mide exactamente 1000 m;
  `SharedFlow<KmAlert> kmAlerts`; sonido (`Sound.BEEP`) + vibración desde el servicio; inyecta `SoundPlayer`.
- `FreeRideScreen`: banner "KM n · m:ss/km" 4 s en la parte superior.
- Pendiente: usuario compila, ejecuta la suite de tests, y en la calle (o simulando GPS) comprueba: autolap en
  cada km de 1000 m exactos, un único aviso por km (sonido + vibración + banner, también con pantalla
  apagada), y que al finalizar no aparece una vuelta parcial.

### T-02 (validada por el usuario: compiló y probó, todo bien)
- Nuevo `util/laps/LapGuard.kt` (solo tiempo: ≥5 s; la distancia no cuenta, para series explosivas en el sitio) + `LapGuardTest`.
- **Causa raíz confirmada con Logcat (`LapDiag`): H1.** `addLap` llegó 159 ms después de
  `startRide` con tiempo 0 y distancia 0 (GPS conectado): el botón Vuelta aparece bajo el
  dedo donde estaba Iniciar. No era H2 ni H3.
- Corrección en origen: `FreeRideScreen` ignora toques de Vuelta durante 1 s tras Iniciar
  (`LAP_TAP_GUARD_AFTER_START_MS`). Red de seguridad en `FreeRideService.addLap()`: devuelve
  Boolean y rechaza parado/pausa/corta/vacía (Toast "Vuelta ignorada"); `addAutoLap()` no
  actúa en pausa. Logging `LapDiag` retirado.
- Pendiente: usuario compila, ejecuta la suite y comprueba que al iniciar no aparece vuelta
  fantasma (el Logcat ya no existe; se ve en la lista de vueltas).

### T-01 (validada por el usuario: compiló y los tests pasan)
- Nuevo: `util/laps/PaceCalculator.kt`, `util/laps/CadenceDisplayPolicy.kt`; tests
  `PaceCalculatorTest`, `CadenceDisplayPolicyTest`.
- `FreeRideService`: `FreeRideStats.cadenceActive` + `paceSecPerKm`; marca de tiempo del
  último dato de cadencia, vigilante de 1 s, ritmo en vivo en el tick del cronómetro.
- `FreeRideScreen`: composable `CadencePaceCard` en las 3 vistas (portrait, landscape
  métricas, landscape vueltas).
- Pendiente: el usuario compila, ejecuta `./gradlew test` y prueba (sin sensor → ritmo;
  con sensor → rpm; apagar/encender sensor → conmuta en ≤ ~6 s).

---

## 2026-09-27 — Pulsómetro dual: fusión ANT+/BLE y etiqueta de protocolo

### Contexto
En la Tab 9, una única banda dual aparecía como HR1 **y** HR2 (dos líneas casi
calcadas). Trabajo sobre `sensor/HeartRateHub.kt` (Fase C, gestor único de HR).

### Cambios hechos
1. **Fusión de gemelos por media móvil** (`detectTwins`): antes comparaba bpm
   instantáneo con igualdad estricta (18/20), que falla al variar la HR por el
   desfase ANT+/BLE. Ahora usa ventana de ~10 muestras y fusiona si la diferencia
   media de bpm ≤ 5 (`TWIN_WINDOW`, `TWIN_MIN_SAMPLES`, `TWIN_MAX_AVG_DIFF`).
   → Resultado verificado: una banda dual ocupa un solo hueco (HR1). ✅
2. **Etiqueta de protocolo persistente** (`tick`): el hub emitía `Connected` una
   sola vez y luego muchos `Value`; con `replay=1` un suscriptor tardío solo veía
   el `Value` y perdía el protocolo → badge vacío. Ahora reemite `Connected` cada
   tick. → Verificado: la etiqueta ya aparece bajo HR1. ✅

### Abierto / en investigación
- **ANT+ no engancha en la última sesión**: la etiqueta de HR1 sale **BLE** y al
  apagar el Bluetooth se queda sin señal (no hay ANT+ de respaldo). El hub ya
  prioriza ANT+ si está fresco; el problema es que el ANT+ no conecta ahora
  (antes sí: en la 1ª captura HR2 tenía ANT+). **Pendiente: capturar logcat**
  (filtro `ANT|BLE|Hueco|aparato`) iniciando Tabata → 20 s → apagar BT, para ver
  si el ANT+ no detecta, no conecta o llega stale.

### Notas de flujo de trabajo (acordadas hoy)
- Compilar/reinstalar lo hace el usuario (Ctrl+Shift+O). Claude solo avisa
  "compila y reinstala".
- `adb` no está en el PATH: usar
  `C:\Users\APO\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

## 2026-09-27 — Spec 003: registro persistente de pulsómetros (ANT+ ↔ BLE)
- Problema: una banda dual salía en HR1 (ANT+) y HR2 (BLE). Causa: se asignaba hueco
  antes de decidir si eran gemelos, la comparación (media |Δbpm| ≤ 5) fallaba por el
  desfase ANT/BLE y el vínculo se borraba en cada `stop()`.
- Solución (`specs/003-hr-device-registry/`):
  - `HrDeviceRegistry` (SharedPreferences, sin Room): guarda para siempre nº ANT+ ↔ MAC BLE.
  - `HeartRateHub`: el hueco pertenece al aparato físico; cuarentena de 30 s para un
    desconocido que podría ser la otra cara de uno asignado; comparación con desfase
    (±5 s, exige variación de HR para guardar); relevo sin solapamiento (quitas pincho
    ANT+/apagas BLE y la otra señal hereda el hueco).
  - Tests: `HrDeviceBookTest`.
- Pendiente (T-04): compilar, `./gradlew test` y probar la secuencia BLE → +ANT+ →
  −ANT+ → −BLE/+ANT+ mirando Logcat `💓` (debe salir "vinculado ... guardado").
- Prueba en 1.6.25 (logcat): el registro no llegaba a actuar porque
  (a) ANT+ 56551 se encontraba pero requestAccess daba SEARCH_TIMEOUT y luego
  CHANNEL_NOT_AVAILABLE / ALL_CHANNELS_IN_USE: la MultiDeviceSearch continua del hub
  acaparaba la radio del pincho (también bloqueaba la cadencia ANT+);
  (b) BLE tras desconectar (status 22) el reintento connectGatt quedaba colgado y el
  escaneo ignoraba el aparato para siempre.
  Arreglo: búsqueda ANT+ por ventanas (15 s busca / 15 s pausa), se cierra antes de
  conectar, recuperación de aparatos perdidos volviendo a buscar; vigilante BLE de 20 s
  que cierra la conexión colgada para que el escaneo la reenganche.

## Sesión 2026-09-27 — Spec 004: Home responsive
- `HomeScreen.kt` rehecho con `BoxWithConstraints`: 4 layouts (móvil/tablet × vertical/horizontal).
  Arreglado: la tablet en vertical usaba el layout horizontal (`screenWidthDp > 600`).
- Vertical: las tarjetas reparten el alto (sin huecos); scroll si alto < 620dp.
  Horizontal: panel lateral + tarjetas lado a lado a todo el alto.
- Pendiente (T-02): compilar e instalar (usuario) y validar en S22+ y Tab S9.
- Revisión 2: actividades en la misma fila; fila 2 = Última actividad (datos reales) +
  Esta semana (desde lunes: sesiones, tiempo, km, FC media); fila 3 = Historial | Perfil | Cerrar.
  Nuevo `HomeViewModel` (solo lectura de `SessionDao.getAllSessions()`) + `HomeSummaryTest`.

## Sesión 2026-09-27 — Spec 005: batería de sensores
- `SensorBattery` (+test), batería BLE HR en `HeartRateHub` (validado XOSS 75 %, Decathlon 100 %),
  arranque automático del escaneo BLE al encender Bluetooth, ANT+ HR usa la batería del gemelo BLE.
- Cadencia BLE/ANT+ en `SensorManager` (T-04, pendiente de validar en el otro estudio).
- UI (T-05): `BatteryBadge` en Home (2 filas HR1+C1 / HR2+C2), Tabata y Ruta Libre, aviso único de batería baja.
  Tabata vertical validado; horizontal compactado para dar alto a la gráfica (pendiente de validar).
- Todo integrado en `main`; ramas eliminadas.
