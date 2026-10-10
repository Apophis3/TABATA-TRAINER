# Estado del proyecto y memoria de sesiones — Tabata Trainer

Bitácora de trabajo. Cada sesión se anota aquí: qué se tocó, por qué, en qué quedó
y qué falta. Complementa a `01_historico_errores.md` (errores resueltos) y a la
memoria privada de Claude. **Lo más reciente arriba.**

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
