# Plan 007 — Aparatos de doble señal (ANT+ ↔ BLE)

## Diseño

### 1. Registro genérico (`sensor/SensorDeviceRegistry.kt`, sustituye a `HrDeviceRegistry.kt`)
- `SensorKind { HR, CADENCE }`.
- `PhysicalDevice(key, kind, alias, antId?, bleId?, preferredSlot?, manual)`: admite fichas de una sola señal
  (aparato conocido, con hueco o alias, aunque aún no esté emparejado).
- `DeviceBook` (lógica pura, testeable): `keyOf`, `partnerOf`, `deviceOf`, `link(kind, ant, ble, alias, manual)`,
  `split`, `forget`, `remember`, `setSlot`, `rename`, `isBlocked`, `serialize` / `parse`.
  - Un id pertenece como máximo a una ficha por tipo. Un hueco pertenece como máximo a una ficha por tipo.
  - `link` automático no rompe fichas manuales ni une parejas bloqueadas; `link` manual siempre gana y
    desbloquea la pareja. Al unir se conservan alias y hueco de las fichas previas.
  - `split` (separar) deja dos fichas de una señal y bloquea la pareja para el automático.
- Persistencia: mismo archivo de preferencias (`hr_device_registry`, clave `devices`). Formato v2 con cabecera
  `v2`; el formato de la spec 003 (`clave ANT BLE alias`) se lee como fichas HR automáticas → migración sin
  pérdida. `StateFlow<List<PhysicalDevice>>` para la UI.

### 2. Reglas de emparejado (`sensor/PairingPolicy.kt`, lógica pura)
- Entrada: señales frescas de un tipo (id, protocolo, nombre, historial a 1 Hz) + libreta.
- Salida: vínculos persistentes y vínculos de sesión.
- Persistente si (y la pareja no está bloqueada ni hay ficha manual en conflicto):
  - nº ANT+ (≥ 4 dígitos) al final de los dígitos del nombre BLE; o
  - exactamente 1 ANT+ y 1 BLE sin pareja (contando también señales conectadas no frescas), muestras
    suficientes, variación ≥ mínimo en ambas y diferencia media con desfase ≤ umbral.
- Sesión: emparejamiento mutuo-mejor con diferencia ≤ umbral (también con valores planos) y sin empate
  (el segundo candidato debe quedar al menos 1 unidad peor). Si hay empate, no se agrupa; el asignador de
  huecos (T-02) garantiza que con dos ANT+ visibles los dos huecos sean ANT+ y los BLE no reciban hueco.
- Umbrales por tipo (`PairingThresholds`): HR = 15 muestras, desfase 5, diferencia 4, variación 4; la
  situación "1 ANT+ + 1 BLE" debe mantenerse 30 s (más que un ciclo de búsqueda ANT+) para guardarlo.

### 3. Huecos (`sensor/DualSlotAllocator.kt`, lógica pura)
- Extraído de `HeartRateHub.tick()`: cuarentena de desconocidos, relevo sin solapamiento, elección de fuente.
- Orden: aparato con `preferredSlot` → su hueco (quien lo ocupaba pasa al otro) → primer libre, ANT+ primero.
- `preferredSlot` lo asigna el usuario desde la hoja (T-06); el asignador no lo guarda solo (si no, cada banda
  usada reservaría hueco y una banda sola acabaría en HR2).
- Reserva **blanda**: un aparato ajeno evita el hueco reservado de un aparato apagado mientras haya otro
  libre; si no queda otro, lo usa (con A=HR1 y B=HR2 guardados, una tercera banda sola también se ve).
- ANT+ desplaza a BLE: con tantos aparatos ANT+ como huecos (contando los que tienen hueco aunque estén caídos
  un momento) ningún hueco queda en un aparato sin ANT+; el ANT+ lo ocupa en el mismo hueco. Un hueco de
  aparato ANT+ caído no pasa a uno solo-BLE hasta 60 s (búsquedas/reconexiones ANT+ sin saltos).
- Fuente: ANT+ si está fresco, si no BLE.
- Estado por hueco `SlotStatus(slot, deviceKey, alias, sourceId, sourceName, protocol, connected, value,
  battery)` como `StateFlow` en el hub (`slotStatus(slot)`); `SensorManager` y UI lo usan desde T-04.
- Ciclo de vida del hub: sin observadores → radios fuera a los 5 s → huecos y vínculos de sesión olvidados
  55 s después (se cancela si vuelve un observador). `restart()` = parada completa.

### 4. `CadenceHub` (`sensor/CadenceHub.kt`)
- Espejo de `HeartRateHub` para `BIKE_CADENCE` / CSC 0x1816. Rpm BLE desde vueltas de biela y tiempo,
  validación 0–300. Batería por señal.
- Radio ANT+ compartida: planificador común de ventanas de búsqueda para pulso y cadencia.
- Un único escaneo BLE continuo por tipo (sin parar/arrancar en bucle).
- `SensorManager.observeCadence*()` y `cadenceBattery()` delegan; se retiran `createCadenceFlow*`, la
  autoconexión por `DeviceProfileDao` para cadencia y `BleManagers.kt` (código muerto). El DAO y su entidad de
  Room no se tocan (quedan sin uso).
- Detalle (T-03):
  - **0 rpm es un dato válido.** Una señal de cadencia es "fresca" si está conectada y ha dado dato (aunque sea
    0) en los últimos 5 s. Si pasan 3 s sin vuelta de biela nueva (o sin dato), su valor pasa a 0 sin
    desconectarla. Se emite `Value(0)` al hueco igual que cualquier otro valor.
  - **Emparejado con 0 rpm:** un historial solo de ceros no empareja por valores (ni en la sesión): dos
    cadenciómetros distintos parados dan la misma serie y un agrupado de sesión erróneo no se deshace hasta
    reiniciar. En cuanto hay pedaleo, los ceros cuentan en el historial como cualquier valor; con valores planos
    solo hay agrupación de sesión (la persistente exige variación ≥ 4 rpm) y el empate no agrupa. El nombre
    (nº ANT+ en el nombre BLE) sí empareja aunque esté parado. Coste aceptado: un aparato dual sin vínculo
    guardado ni nombre coincidente puede verse en C1 y C2 a 0 rpm hasta que se pedalea; entonces se agrupa y
    queda en un solo hueco.
  - **Radio ANT+ compartida (`sensor/AntRadio.kt`):** una sola búsqueda multi-dispositivo con la unión de
    tipos que piden los hubs (HEARTRATE, BIKE_CADENCE); cada hub deja de pedir su tipo al tener sus 2
    canales. Ventanas 15 s búsqueda / 15 s pausa (sin cambios). La búsqueda se cierra antes de pedir acceso a
    un aparato y no se busca mientras alguien conecta (con límite de 20 s por si el acceso no responde). Cada
    hub para solo lo suyo: parar uno no corta la búsqueda que necesita el otro.
    **Cierre fuera del hilo de pantalla** *(prueba real 1.6.111)*: `MultiDeviceSearch.close()` bloquea ~1 s;
    hecho en el hilo principal congelaba la pantalla cada 30 s. Se cierra en segundo plano, en orden, y antes
    de pedir acceso a un aparato o abrir otra búsqueda se espera a que el cierre termine (`awaitClosed()`).
  - Cadenciómetro ANT+ solo de cadencia (`isSpeedAndCadenceCombined = false`, como hasta ahora).

### 5. Ciclo de vida — análisis de impacto (Clave 3)
- `WorkoutService` y `FreeRideService` solo se suscriben a los flujos de `SensorManager`; no se cambia su
  ciclo de vida. El cambio está en el hub: al quedarse sin observadores no borra `slotGroup` hasta pasado un
  margen largo (p. ej. 60 s), de modo que navegar Home → Tabata → Home no reparte de nuevo los huecos.
  Riesgo: radios activas unos segundos más sin pantalla; se acota con el margen y con `restart()` manual.
- `NavGraph.kt` no se toca: la hoja "Dispositivos" es un `ModalBottomSheet` dentro de cada pantalla que la
  abre (Home, Tabata, Ruta, Config); no es un destino de navegación.
- Abrir la hoja en Tabata/Ruta con la sesión en marcha no toca el servicio: la hoja solo observa los hubs y
  escribe en el registro (que el asignador lee en el siguiente tick). Con la hoja abierta hay observadores,
  así que las radios no se paran.

### 6. Hoja "Dispositivos" (común) — `ui/devices/DevicesSheet.kt` + `DevicesViewModel` (Hilt)
- Señales visibles (ANT+ nº / BLE MAC, valor en vivo) y aparatos guardados, por tipo.
- Unir, separar, renombrar, asignar hueco, olvidar, reconectar. Modo guiado: con 1 ANT+ y 1 BLE nuevos ofrece
  unirlos.
- Entrada común (R8.1): componente `SensorBar` en `ui/components/` (GPS opcional, HR1/HR2, C1/C2 con
  `SensorSlotBadge`, batería, flecha de refrescar) con `onOpenDevices`. Lo usan Home, Tabata, Ruta y Config
  en lugar de sus barras propias. La flecha llama a reconectar; el resto de la barra abre la hoja.

### 7. Pantallas
- `ui/components/SensorSlotBadge.kt`: HR1/HR2/C1/C2 + insignia ANT+/BLE + batería (reutiliza `ProtocolBadge`).
- Tabata: leer `SlotStatus`. Config: HR1/HR2/C1/C2 con señal. Home: estado real por hueco.
- Detalle (T-04):
  - **Ampliación del contrato de `SensorManager`** (solo se añade): `hrSlotStatus(slot)` y
    `cadenceSlotStatus(slot)`, ambos `StateFlow<SlotStatus>`, delegan en `HeartRateHub` / `CadenceHub`.
    Observar `SlotStatus` **no** arranca los hubs (solo cuentan los observadores de `observeHeartRate*` /
    `observeCadence*`): cada pantalla mantiene sus observadores de esos flujos.
  - `ui/components/SensorSlotBadge.kt`: `ProtocolBadge` (sale de `WorkoutScreen`, ANT+ verde / BLE azul),
    `SensorSlotBadge` (hueco + señal + batería) y `rememberSensorSlots()` con `SensorSlotsViewModel` (Hilt, solo
    lee los 4 `SlotStatus`: HR1, HR2, C1, C2).
  - Tabata: el número en vivo sigue viniendo del servicio (lo que se graba); señal, batería y estado del punto
    salen de `SlotStatus`. Punto: verde con señal, naranja buscando (hueco libre), rojo aparato del hueco sin
    señal. La barra superior deja sitio a la flecha de volver (antes tapaba HR1).
  - Config: rejilla 2×2 (HR1 HR2 / C1 C2) con valor, señal y batería; 0 rpm conectado se muestra como 0.
    `ConfigViewModel` añade `slots`; conserva sus observadores y `BleScanStatus` (Home lo usa hasta T-06).
  - `WorkoutService` no se toca en T-04.
- Ruta: `FreeRideStats` con HR1/HR2 y C1/C2 separados; medias HR1/HR2 separadas; escucha C2.
- Detalle (T-05):
  - **`FreeRideStats`** (solo se añade, con valores por defecto): `heartRate2`, `avgHeartRate2`,
    `maxHeartRate2`, `isHr2Connected`, `cadence2`, `avgCadence2`, `isCadence2Connected`, `cadence2Active`.
    `heartRate`/`avgHeartRate`/`maxHeartRate`/`cadence`/`avgCadence`/`cadenceActive` pasan a ser **solo** HR1/C1:
    se quita el relevo "si no hay HR1, HR2 ocupa `heartRate`" y HR2 ya no marca `isHrConnected`.
  - **`FreeRideService`:** nuevo colector de `observeCadence2()` (su propio `Job`, cancelado en
    `stopSensorCollectors()` como los demás); es lo que arranca el hueco C2 (observar `SlotStatus` no arranca
    sensores). El vigilante de cadencia calcula también `cadence2Active`. Cada `Value` marca conectado su hueco.
    Logs `❤️`/`❤️2`/`🚴`/`🚴2`. Ciclo de vida del servicio sin cambios: solo se añade un colector junto a los otros.
  - **Lecturas por hueco** en un ayudante puro `util/laps/DualSlotSamples` (`add/avg/max/primarySlot/clear`,
    sincronizado: los colectores escriben desde hilos distintos) para la ruta, la vuelta manual y la vuelta AUTO,
    de pulso y de cadencia. Antes las vueltas mezclaban las lecturas de las dos bandas en una sola media.
  - **Vueltas y Room (sin cambios de esquema):** una vuelta guarda un solo pulso y una sola cadencia: HR1/C1 si
    el tramo tiene lecturas de HR1/C1; si no, HR2/C2. La sesión ya guardaba HR1 y HR2 por separado con ese mismo
    criterio (`avgHeartRate*`, `avgHeartRate2*`); la cadencia de la sesión pasa al mismo criterio (C1, si no C2).
    `sensor_readings.cadence` sigue siendo solo C1; C2 solo en vivo (R7).
  - **Pantalla Ruta** (como Tabata en T-04: el número sale del servicio; señal, batería y punto de `SlotStatus`):
    - Tarjeta de pulso partida siempre en **HR1 | HR2**, cada mitad con `SensorSlotBadge` (hueco + ANT+/BLE +
      batería); sin dato, "--".
    - Tarjeta "Cadencia / Ritmo": si C1 o C2 emiten, partida en **C1 | C2** con su insignia; si ninguno
      emite, ritmo (min/km) como hasta ahora.
    - Vuelta en curso (página 3): "HR1" y "HR2" en casillas propias; "Cadencia" = C1, si no C2 (`CurrentLapModel`
      sin cambios).
    - Barra de sensores (vertical): GPS + HR1, HR2, C1, C2 con `SensorSlotBadge` en lugar de "HR"/"CAD" con
      baterías sueltas. Horizontal: puntos HR1/HR2/C1/C2 por hueco (verde señal / naranja buscando / rojo perdido).
      La barra común `SensorBar` y la hoja "Dispositivos" son de T-06.

### 8. Arreglos menores pedidos por el usuario (2026-11-21, antes de T-06)
Los tres "pendientes vistos" en T-04/T-05. No es T-06 (que sigue sin empezar).
- **Valor ANT+ congelado al quitar la banda (R2.1).** Sin contacto, la banda sigue emitiendo el último pulso
  calculado 10–20 s y el plugin lo marca `LIVE_DATA`; lo que deja de avanzar es el contador de latidos
  (`heartBeatCount`, 4.º parámetro de `onNewHeartRateData`, `long`; firma comprobada con `javap` en
  `antpluginlib_3-9-0.aar`). `HeartRateHub` solo acepta un dato ANT+ si el contador ha cambiado (`!=`, así no
  importa que dé la vuelta) en los últimos 3 s (`ANT_BEAT_STALL_MS`; a 25 bpm, el mínimo admitido, hay un latido
  cada 2,4 s). Si no, no llama a `onBpm`: `lastUpdateMs` deja de avanzar y la señal deja de ser fresca a los
  `STALE_MS` (5 s, sin cambios: lo usan el emparejado y el asignador) → el hueco pasa al BLE del mismo aparato o
  se apaga. Lógica pura en `AntBeatWatch` (dentro de `HeartRateHub.kt`), con test. Log `💓 ANT+ …: sin latidos
  nuevos` / `latidos de nuevo`, una vez por cambio.
- **BLE sin contacto con la piel (R2.1).** Si no, el valor congelado pasaría de ANT+ al BLE de la misma banda.
  En la medida de pulso BLE (0x2A37) el byte de banderas trae "contacto admitido" (bit 2, 0x04) y "contacto
  detectado" (bit 1, 0x02). Admitido y no detectado → el dato se descarta (`bleSkinContactLost`), pero solo si
  esa misma conexión ya ha dicho alguna vez "con contacto" (`BleContactWatch`): una banda que admite el aviso y
  nunca lo activa sigue como antes (no se pierde su BLE, ni el respaldo ni el emparejado por valores). Bandas que
  no admiten el bit: como hasta ahora. Log del cambio (una vez por cambio, por conexión). Tests en
  `HrStaleValueTest` (8).
- **Ruta horizontal: franja de pasos bajo el indicador de páginas.** En vertical quedan 60 dp libres abajo; en
  horizontal la columna derecha se desplazaba sin margen y, con la tarjeta HR1 | HR2 de T-05 (más alta), la
  franja quedaba bajo el indicador. Arreglo en `LandscapeMetricsPage`: la columna derecha deja de desplazarse
  (un `BoxWithConstraints` dentro de un desplazamiento tendría alto infinito) y reserva 56 dp abajo
  (`PAGE_INDICATOR_CLEARANCE`: indicador 40 dp + 16 dp de margen). Las dos filas de tarjetas ocupan el alto que
  queda y el número se ajusta a él con el mismo cálculo que en vertical, extraído a `MetricsGrid` (lo usan las
  dos orientaciones; en horizontal con relleno de tarjeta 10 dp). La franja de pasos va debajo, sin `weight`
  (se mide antes). Cálculo para el S22 (384 dp de alto en horizontal): filas de ≈ 100 dp con pasos → número
  ≈ 39 sp y HR1 | HR2 ≈ 25 sp; sin pasos, ≈ 130 dp.
- **Ruta horizontal, página 3 (visto en la prueba 1.6.132).** Los botones parar/vuelta/pausa
  (`FreeRideControlsCompact`) iban al pie de la columna derecha, encima del indicador de páginas, y la última
  fila de la rejilla (pasos, pasos/min, desnivel) quedaba cortada. Arreglo en `LandscapeLapsPage`, igual que la
  página 1: los botones pasan al pie de la columna izquierda (bajo la última vuelta, que no llega al indicador)
  y la columna derecha reserva `PAGE_INDICATOR_CLEARANCE` abajo. Para que quepan las 3 filas: botón "NUEVA
  VUELTA" 48 → 44 dp y número de las casillas 26 → 22 sp (relleno 6 → 5 dp). La rejilla se sigue pudiendo
  desplazar si no cupiera. Cálculo para el S22 (≈ 338 dp útiles): título 20 + rejilla ≈ 200 (filas ≈ 63 dp,
  casilla ≈ 58) + botón 44 + márgenes 16 + 56 = 336.
  Columna izquierda: con una vuelta cerrada la franja de la última vuelta crece (título + 2x2 ≈ 130 dp) y no
  cabría con los botones; la parte de arriba (huecos, estado, tiempo, última vuelta) va en un bloque
  desplazable que ocupa lo que queda y los botones quedan fijos abajo. Tiempo 56 → 52 sp y separación 10 → 6 dp
  para que no haga falta desplazar: 24 + 20 + 70 + 130 + 18 = 262 ≤ 338 − 56 − 6.
- **"Lápiz" en Config: descartado, no es un fallo** *(confirmado por el usuario 2026-11-21)*. Es el icono
  flotante del S Pen de Samsung (Air command), que el usuario usa a diario y le es imprescindible. No tiene nada que ver
  con la app: no se toca, ni en el código ni en el móvil, ni se vuelve a apuntar como pendiente.
- Sin tocar Room, Gradle, `NavGraph.kt`, ciclo de vida de los servicios ni el contrato de `SensorManager`.

### 9. Ruta: un pulsómetro → tarjeta grande; cadencia o pasos/min en la vuelta (R7.1, R7.2; 2026-11-21, antes de T-06)
- **Señal de verdad (R7.1).** Ni `SlotStatus.connected` ni `FreeRideStats.heartRate*` sirven: el hueco sigue
  "conectado" ≈ 14 s tras quitar la banda (el asignador cae a un miembro solo conectado) y el servicio deja el
  último valor hasta "Desconectado". Se añade `SlotStatus.fresh: Boolean = false` (último campo, con valor por
  defecto; solo se amplía el contrato) = la fuente elegida en `DualSlotAllocator.pickSource` tiene dato reciente
  (`SignalState.fresh`: < `STALE_MS` 5 s y, por R2.1, con latidos nuevos en ANT+ / con contacto en BLE).
  `signalState` no cambia (lo usan Tabata, Config y Home).
- **Regla pura** `util/laps/HeartRateCardPolicy` (junto a `CadenceDisplayPolicy`; sin Android, con test):
  `desired(hr1Fresh, hr2Fresh, current)` → `SINGLE_HR1` / `SINGLE_HR2` con uno solo con señal, `SPLIT` con los
  dos, y `current` sin ninguno (una caída corta no cambia la tarjeta). `holdMs(from, to)`: tiempo que el
  cambio debe mantenerse antes de aplicarse: de única a partida 8 s (> `STALE_MS`: el valor suelto de 70 bpm
  visto en la 1.6.123 al reconectarse la banda quitada no parte la tarjeta); el resto 2 s (la frescura ya
  tarda 5 s en caer).
- **Pantalla.** `MetricsGrid` (vertical y horizontal) guarda la disposición en un estado que arranca en la
  deseada (sin espera al girar o al volver a la página) y la cambia con `LaunchedEffect(desired)` tras
  `holdMs`. Log solo al cambiar: `Ruta: tarjeta de pulso única HR1/HR2` / `Ruta: tarjeta de pulso partida HR1 | HR2`.
  La tarjeta única (`SingleSlotCard`) ocupa el sitio de la partida: número con el valor del servicio (el que
  se graba) y el color del hueco; debajo `SensorSlotBadge` en una línea (hueco + ANT+/BLE + batería) en lugar
  de la etiqueta, así el número usa el mismo alto que Velocidad/Distancia y todo el ancho de la tarjeta.
- **Vuelta en curso (R7.2).** `CurrentLapView` añade `cadenceLabel` ("Cadencia" con cadenciómetro, "Pasos/min"
  sin él); `currentLapStats` lo usa como título de la casilla. El valor ya era rpm o ppm de la vuelta (RF-7.5;
  ppm desde 10 s de vuelta, sin cambios). Siguen 9 casillas. Test en `CurrentLapModelTest`.
- Sin tocar Room, Gradle, `NavGraph.kt` ni el ciclo de vida de los servicios.

### 10. Señal por recuadro de color (R6.1; 2026-11-21, antes de T-06)
- `ui/components/SensorSlotBadge.kt`, que usan todas las pantallas (Tabata, Config, barra y tarjetas de
  Ruta; Home lo heredará en T-06 con `SensorBar`). Ninguna pantalla cambia su llamada.
- `SensorSlotBadge`: el texto del hueco va dentro de un recuadro redondeado (borde 1,5 dp + fondo del mismo color
  al 15 %): verde `0xFF66BB6A` ANT+, azul `0xFF42A5F5` BLE (los colores que ya tenía la etiqueta). Sin señal, el
  borde es transparente con el mismo relleno, para que nada salte de sitio al conectar. Se quita la etiqueta de
  texto "ANT+"/"BLE" (`ProtocolBadge`, sin otros usos). Batería sin cambios (al lado o debajo con `stacked`).
- Lectores de pantalla: descripción "HR1, ANT+" / "HR1, BLE" / "HR1, sin señal".
- **Barra compacta de Ruta en horizontal (visto en la prueba 1.6.132).** `CompactSensorRow` (páginas 1 y 3 en
  horizontal) seguía con un punto de color por hueco, sin recuadro. Pasa a usar `SensorSlotBadge` con el
  color de texto de la barra vertical (`slotSignalColor`) y sin batería (no cabe en la columna izquierda; antes
  tampoco la tenía). Para ello `SensorSlotBadge` gana `showBattery: Boolean = true` (las llamadas actuales no
  cambian).
- Sin tocar lógica, sensores, servicios, Room, Gradle ni `NavGraph.kt`.

### 11. T-06 — Hoja "Dispositivos" común + Home + `SensorBar` (R8, R8.1–R8.3) — *aprobado 2026-11-21; código hecho, pendiente de prueba*

**11.1 Datos para la hoja (sensores; solo se añade)**
- `VisibleSignal(kind, id, name, isAnt, value, connected, fresh, battery, group, slot: Int?, sessionOnly)`
  (en `DualSlotAllocator.kt`, junto a `SignalState`). `group` = aparato al que pertenece (`allocator.groupOf`),
  `slot` = hueco de ese aparato o null, `sessionOnly` = agrupada con otra señal solo en esta sesión (no guardado).
- Cada hub publica `signals: StateFlow<List<VisibleSignal>>` en su `tick()` (1 Hz, mismo hilo y cerrojo que
  el reparto; vacío con las radios paradas). Hoy `devices` no dice a qué aparato ni hueco va cada señal.
- Hub: `unlinkSession(antId, bleId)` → `allocator.leaveSession` + bloqueo de la pareja en el registro ("No son el
  mismo aparato" para un agrupado solo de sesión, que no tiene ficha que separar).
- `SensorManager` (fachada, **amplía el contrato**; anotado en `spec.md` Restricciones): `hrSignals()`,
  `cadenceSignals()`, `unlinkSession(kind, antId, bleId)`. Nada existente cambia.
- `DeviceBook` / `SensorDeviceRegistry`: `block(kind, antId, bleId)` (bloquea una pareja sin ficha emparejada;
  se guarda en el formato v2 que ya admite líneas `B`). Log `Registro: … bloqueados`.

**11.2 Separar tiene que aguantar (arreglo en `DualSlotAllocator`)**
- Hoy, tras separar (o bloquear), el asignador puede volver a juntarlos: `canHandoff` (relevo, regla 6) y
  `inQuarantine` (espera de 30 s) no miran `isBlocked`. Si la ANT+ cae un momento, el BLE separado la relevaría
  en la sesión; y mientras la ANT+ tiene hueco, ese BLE espera 30 s antes de recibir el suyo.
- Arreglo: las dos ignoran parejas bloqueadas. Tests nuevos en `DualSlotAllocatorTest`.

**11.3 Cambios desde la hoja, en vivo (R8.3)**
- El registro ya se lee en cada tick: hueco fijo (regla 2), unir (regla 1 junta los dos huecos en uno), separar
  (el BLE pasa a ser otro aparato y busca hueco). Ni la pantalla ni el servicio se tocan.
- **Aviso:** cambiar el hueco con la sesión en marcha cambia, desde ese segundo, qué banda se graba como HR1/HR2
  (y C1/C2) en la sesión. Es lo que pide R8.3; la hoja lo dice en una línea bajo el selector de hueco si hay
  una sesión en curso.
- Reglas de las acciones:
  - Renombrar o fijar hueco a una señal no guardada: primero `remember` (ficha de una señal), luego la acción.
  - "Unir con…" solo ofrece señales del otro protocolo que **no** estén ya en un aparato emparejado (un `link`
    manual borraría sin avisar la otra mitad de la pareja anterior). Para reutilizar una, primero se separa.
  - "Olvidar" (con confirmación): borra nombre, hueco y vínculo. El texto avisa de que, si es seguro, la app
    puede volver a emparejarlo sola.
  - "Separar" un aparato guardado: `split` (bloquea la pareja). Un agrupado solo de sesión: "No son el mismo
    aparato" (`unlinkSession`) o "Guardar como un aparato" (`link` manual).

**11.4 Hoja `ui/devices/DevicesSheet.kt` + `DevicesViewModel` (Hilt)**
- `ModalBottomSheet` dentro de cada pantalla (no es destino de navegación; `NavGraph.kt` no se toca). Atrás la
  cierra sin salir de la pantalla. Log `Dispositivos: hoja abierta/cerrada (<pantalla>)`.
- Arriba: título "Dispositivos" y botón **Reconectar** (R8.2): recibe el **mismo** callback que la flecha de cada
  pantalla y con su misma regla de visibilidad — Home/Config `ConfigViewModel.refreshSensors`, Tabata
  `WorkoutService.refreshSensors` (oculto en pausa, como la flecha), Ruta `FreeRideService.reconnectSensors`.
  La hoja nunca llama a `resetFlows` por su cuenta.
- Dos pestañas: **Pulsómetros** / **Cadenciómetros**. En cada una:
  - Una tarjeta por aparato (guardados del registro + señales vistas sin guardar, agrupadas por `group`): nombre;
    hueco actual en su recuadro de señal (verde ANT+ / azul BLE, R6.1); una línea por señal ("ANT+ 12345 · 72 bpm",
    "BLE XOSS · 71 bpm", atenuada si no es fresca); "Agrupado solo en esta sesión" si toca.
  - Acciones por tarjeta: hueco fijo `Auto | HR1 | HR2` (o `C1 | C2`); Renombrar (diálogo); Unir con…;
    Separar / No son el mismo / Guardar como un aparato; Olvidar.
  - **Modo guiado** ("Emparejar un aparato"): pide dejar encendido solo ese aparato y mira las señales conectadas
    del tipo: falta ANT+ ("Esperando ANT+…", útil cuando el receptor ANT+ no responde) / falta BLE / hay más de una
    de algún tipo ("Apaga los demás") / ya están unidas ("Ya es «XOSS»") / listo: "¿ANT+ 12345 (72) y BLE XOSS (71)
    son el mismo aparato?" → **Unir** (manual, nombre editable, hueco opcional). Lógica pura `GuidedPairing`.
- El ViewModel arma las tarjetas con una función pura `buildDeviceCards(kind, saved, signals)` (testeable).
- Mientras la hoja está abierta, el ViewModel se suscribe a `observeHeartRate()` y `observeCadence()` (solo
  como observador; descarta los datos) para que los dos hubs sigan en marcha aunque la pantalla de debajo no los
  observe (p. ej. Tabata sin cadencia). Al cerrarla se suelta; el hub aplica su parada de 5 s de siempre.

**11.5 Barra común `ui/components/SensorBar.kt` (R8.1, R8.2)**
- `SensorBar(slots, gps: GpsIndicator?, style, onOpenDevices, modifier, onReconnect: (() -> Unit)?, showBattery,
  containerColor, accent)`; estilos `CARD` / `ROW` / `COMPACT`. Toda la
  barra abre la hoja (`clickable`, descripción "Abrir dispositivos"); la flecha (si hay `onReconnect`) solo
  reconecta. `GpsIndicator` = apagado / buscando (parpadeo, como Ruta hoy) / con señal; null = sin GPS.
- Hueco: `SensorSlotBadge` (recuadro de señal + batería) con el color de texto de estado de Ruta
  (`slotSignalColor`, pasa a `SensorSlotBadge.kt`): verde con señal, naranja buscando, rojo perdido. Es el que ya
  usa Ruta y el que vio el usuario; usa `signalState` (`connected`) como hasta ahora.
- **Números (solo estilo tarjeta):** se muestran solo si el hueco es `fresh`; si no, "--". Así no sale el valor
  congelado ≈ 14 s tras quitar una banda (R2.1). En cadencia 0 rpm fresco = "0".
- Estilos:
  - **Tarjeta** (Home y Config): rejilla HR1 HR2 / C1 C2 con valor + hueco + batería; GPS a la derecha (Home);
    flecha. En Config el interruptor de GPS sigue debajo, fuera de la barra.
  - **Fila** (Ruta vertical: GPS + 4 huecos con batería debajo + flecha; Tabata: 4 huecos sin batería + GPS si
    está activado + flecha según la regla actual, con el margen de la flecha de volver; Ruta horizontal, páginas
    1 y 3: GPS + 4 huecos sin batería, sin flecha, igual que hoy).
- Sustituye a: Home `SensorBar` privada + `SensorWithBattery`/`SensorDot` (Home deja de deducir HR2/C2 de la
  batería: estado real por hueco); Tabata `SensorStatusChips`/`SensorChip`; Ruta `FreeRideSensorBar` y
  `CompactSensorRow`; Config la fila de `SlotStatusChip` + flecha de `CompactSensorRowWithGps`.
- Cambio visible: Home pasa a mostrar el valor de cada hueco (como Config). Tabata pasa de puntos a recuadros
  (como Ruta). Las tarjetas de datos (Tabata, Ruta) no cambian.

**11.6 Ciclo de vida (Clave 3)**
- Servicios: sin cambios. La hoja solo lee flujos y escribe en el registro; las llamadas a reconectar son las
  que ya existen. `NavGraph.kt`: sin cambios. Room, Gradle: sin cambios.
- Back stack: la hoja vive en la pantalla; al salir de la pantalla se destruye con ella.

**11.7 Tests (los lanza el usuario)**
- `DeviceBookTest`: `block` (bloquea, se guarda y se lee; `link` manual lo desbloquea).
- `DualSlotAllocatorTest`: separado + caída de ANT+ → el BLE no la releva; separado → el BLE no espera 30 s.
- `GuidedPairingTest`: falta ANT+, falta BLE, sobran, ya unidos, listo.
- `DeviceCardsTest`: guardado + señales vistas, señal sin guardar, agrupado de sesión, "Unir con…" excluye
  señales ya emparejadas.
- Suite esperada: 209 + 18 = **227** (`DeviceBookTest` +1, `DualSlotAllocatorTest` +3 —incluye el caso sin
  bloquear, en el que el BLE sí releva—, `DeviceCardsTest` 8, `GuidedPairingTest` 6; los dos últimos en
  `app/src/test/.../ui/devices/`).

**11.8 Evidencia para validar T-06**
- Solo con BLE (ANT+ suele no responder): barra en Home/Tabata/Ruta (vertical y horizontal)/Config abre la hoja;
  la flecha sigue reconectando; renombrar; fijar HR2 a una banda y verla pasar a HR2 en vivo con la sesión en
  marcha sin que se pare; olvidar; Home con HR2 real. Logcat: `Registro:`, `ocupa su hueco guardado`,
  `Dispositivos: hoja abierta/cerrada`.
- Con ANT+: unir y separar una banda (separada, sus dos señales en HR1 y HR2; no se vuelven a juntar al caer
  ANT+); modo guiado hasta "listo".

**11.9 Hecho (2026-11-21) — notas de implementación**
- Nuevos: `ui/devices/DeviceCards.kt` (lógica pura: `buildDeviceCards`, `linkCandidates`, `guidedPairing`),
  `ui/devices/DevicesViewModel.kt`, `ui/devices/DevicesSheet.kt`, `ui/components/SensorBar.kt`.
- Sensores (solo se añade): `VisibleSignal` + `describe()` y `blockedPair` en `DualSlotAllocator`; `signals` y
  `unlinkSession` en los dos hubs; `block` en `DeviceBook` / `SensorDeviceRegistry`; fachada en `SensorManager`.
- `slotSignalColor` pasa a `SensorSlotBadge.kt` (público). Borrados: Home `SensorBar`/`SensorWithBattery`/
  `SensorDot`; Config `SlotStatusChip`; Tabata `SensorStatusChips`/`SensorChip`; Ruta `FreeRideSensorBar`,
  `CompactSensorRow`, `SensorChipAnimated`, `SensorDot` y su `slotSignalColor` privado. `rememberSensorBatteries`
  queda sin uso (se borra en §12).
- Aviso "sesión en curso" bajo el selector de hueco: Config si no se puede empezar (`!canStartWorkout`), Tabata
  con la fase distinta de parado/terminado, Ruta con `isRunning`. **Home: nunca** (no sabe si hay una sesión en
  segundo plano); el cambio de hueco se aplica igual.
- GPS en la barra: Home verde/gris según haya GPS; Tabata solo si está activado, verde con posición
  (`isGpsTracking`) o gris (no hay estado "buscando" en Tabata); Ruta con/buscando/sin, como antes.
- Ruta en horizontal (páginas 1 y 3): la barra compacta tiene el mismo tamaño que antes (solo se añade el toque);
  el recorte de la 3.ª fila de la página 3 no se ha tocado.

### 12. Arreglos tras la prueba parcial de T-06 (pedidos por el usuario 2026-11-21; fuera de T-06 y T-07)
- **Espera de 30 s de la 2.ª banda (R3.1).** Visto en la 1.6.138: tras Reconectar, la XOSS se agrupa en la sesión
  (ANT+ ↔ BLE) y, al llegar la ANT+ de la Decathlon, `PairingPolicy` compara las 4 señales: con las dos bandas en
  el mismo pecho, Decathlon ANT+ se parece igual a su BLE que al de la XOSS → empate → sin agrupar → la ANT+
  espera la cuarentena de 30 s.
  - `PairingPolicy.evaluate(signals, lookup, sessionPartners = emptyMap())`: `sessionPartners` = señal agrupada
    solo en la sesión → señales del otro protocolo de su mismo grupo. En la comparación por valores (paso 2) se
    descartan los candidatos que unen una señal agrupada con otra que no es su pareja. Su propia pareja **sí** se
    sigue comparando (con una sola banda, la agrupación de sesión de los 15 s pasa a guardada a los 30 s, como
    hasta ahora). El recuento "1 ANT+ + 1 BLE sin emparejar" (guardar para siempre) **no cambia**: sigue contando
    todas las señales, así con dos bandas nada se guarda solo.
  - `DualSlotAllocator.sessionPartners(ids)`: entre las señales dadas que no están emparejadas en el registro, las
    que comparten grupo con otra del otro protocolo (agrupadas en la sesión). Lo llaman `HeartRateHub` y
    `CadenceHub` en `detectTwins` (misma lógica en cadencia, R5).
  - Tests: `PairingPolicyTest` +2 (XOSS agrupada + Decathlon indistinguible → la Decathlon se agrupa en la sesión
    y nunca se guarda; la pareja agrupada sigue comparándose), `DualSlotAllocatorTest` +1 (`sessionPartners`).
- **Ruta horizontal, página 3: borde de la 3.ª fila cortado.** Faltan unos dp en la columna derecha
  (`LandscapeLapsPage`). Se quitan de los huecos, no del margen del indicador de páginas (`PAGE_INDICATOR_CLEARANCE`
  se queda en 56 dp para no volver a taparlo): separación de la columna 8 → 4 dp y de la rejilla 6 → 4 dp
  (≈ 12 dp más para las filas). Falta que el usuario la pruebe también con una vuelta cerrada (columna izquierda).
- **Error falso al salir de Config.** Los dos colectores de `ConfigViewModel` capturan `Exception`, que incluye la
  cancelación normal al salir de la pantalla, y la registran como "Error crítico". Se deja pasar la cancelación
  (`CancellationException`) antes de la captura general. Sin cambio de comportamiento.
- **Limpieza:** se borra `rememberSensorBatteries` (sin uso desde T-06). `SensorBatteryViewModel` y
  `BATTERY_SLOT_LABELS` siguen en uso.
- **Aviso "Batería baja en HRx" perdido (visto al limpiar).** El aviso (spec 005) sale de `SensorBatteryViewModel
  .batteries`, que solo se calcula si alguien lo lee. Tabata y Ruta lo leían con `rememberSensorBatteries` hasta
  T-04/T-05 de esta spec (pasaron a `SlotStatus`); desde entonces `BatteryLowWarnings` escucha el aviso pero nadie
  lee las baterías y el aviso no sale nunca. Arreglo: `BatteryLowWarnings` lee también `batteries` mientras la
  pantalla está abierta. Sin cambios en sensores ni servicios. Sin test (es de pantalla); se ve con una banda con
  batería baja (si no hay ninguna, queda sin probar).
- **No se hace (pendiente de decidir por el usuario):** acortar la pausa de 15 s entre búsquedas ANT+
  (`AntRadio.SEARCH_PAUSE_MS`). Esa pausa deja la radio libre para conectar los canales (con la búsqueda abierta,
  `requestAccess` falla); no se puede probar con tests y mezclaría su efecto con el arreglo de R3.1 en la misma
  prueba.
- Suite esperada: 227 + 3 = **230**. Sin tocar Room, Gradle, `NavGraph.kt`, ciclo de vida de los servicios ni el
  contrato de `SensorManager`.

### 13. Arreglos tras el paseo del 2026-11-22 (1.6.140; R2.1 ampliado, R8.2 aclarado, R10; fuera de T-06 y T-07)
**Evidencia (BD y volcado Bluetooth del S22 Ultra; el Logcat del paseo ya no estaba en el búfer).** Sesión de Ruta
09:23–10:45. HR2 (pulsómetro de un desconocido, nunca guardado) aparece a las 09:27 y graba **142 fijo de 09:37 a
10:13**. HR1 (XOSS) graba hasta las 10:22:48 y luego nada; Reconectar no lo recupera. Bluetooth del sistema caído y
reiniciado a las 09:13 (antes de salir); llamadas por los auriculares 10:10–10:44. La causa de perder la XOSS por
ANT+ y BLE a la vez **no se puede probar** sin Logcat.

**Fallos confirmados en el código:**
1. Un aparato ANT+ que deja de oírse queda en "buscando" (`DeviceState.SEARCHING`) y solo se trataba `DEAD`: sigue
   "conectado" sin fin y ocupa un canal ANT+ (con 2 canales ocupados no se busca más).
2. `DualSlotAllocator.pickSource` mantiene como fuente una señal conectada sin dato reciente, con su último valor;
   el hub reemite `Connected` y nunca `Disconnected` → `FreeRideService` sigue grabando el último valor.
3. `FreeRideService.reconnectSensors` y `WorkoutService.refreshSensors` solo cancelan y relanzan sus colectores;
   como vuelven a suscribirse a los 0,5 s (antes de los 5 s de parada del hub), las radios **nunca** se reinician.
   Solo Home (`ConfigViewModel.refreshSensors`) llamaba a `resetFlows()`.
4. La hoja no permite quitar una señal no guardada ("Olvidar" solo existe para aparatos guardados).

**Arreglos:**
- **Sin valor congelado (R2.1).** `DualSlotAllocator` recibe `staleHoldMs` (3 s): si la fuente del hueco está
  conectada pero sin dato reciente (la frescura ya caduca a los 5 s), a los 3 s más el hueco se queda sin fuente
  (`sourceId = null`, `connected = false`) → el hub emite `Disconnected` (como al apagarse) y los servicios borran el
  valor. En cuanto llega un dato nuevo vuelve a tener fuente y valor. El aparato **no** pierde su hueco (sigue
  "vivo" para el reparto: sin relevos ni esperas nuevas). Defensa extra en `FreeRideService`: cada valor de pulso
  guarda su hora y `saveSensorReading` solo graba el de los últimos 5 s (como ya hace `WorkoutService`).
- **ANT+ perdido (R2.1).** En los dos hubs, el receptor de estado anota cuándo pasa a `SEARCHING` (y lo borra al
  volver a `TRACKING`); el tick de 1 s da por perdido el aparato que lleva ≥ 30 s buscando: el mismo camino que
  `DEAD` (marca desconectado, cierra el canal, vuelve a pedir búsqueda). Logcat `buscando (no se oye)` /
  `se oye de nuevo` / `sin señal 30 s, se volverá a buscar`.
- **Reconectar de verdad (R8.2).** `restart()` de los hubs pasa a parar solo las radios (`stopRadios`) y volver a
  arrancar: conserva el reparto de huecos y los vínculos de sesión (R9; antes también los olvidaba). Se llama con
  `sensorManager.resetFlows()` en `WorkoutService.refreshSensors` (al principio, con los colectores aún suscritos: el
  hub para y arranca en el acto) y en `FreeRideService.reconnectSensors` (en segundo plano, tras cancelar los
  colectores: el hub para y arranca cuando se vuelven a suscribir a los 0,5 s). `FreeRideService` borra además
  `currentHr1/2` al reconectar.
- **Ignorar (R10).** `DualSlotAllocator`: lista de ignoradas (id → etiqueta) que **no** borra `clear()`; `ignore()`
  suelta en el acto el hueco de su aparato y los vínculos de sesión; `tick()` y `describe()` las dejan fuera.
  Hubs: `ignore(ids)` / `unignore(id)` / `ignored` (StateFlow); `isAntKnown` devuelve true para un ANT+ ignorado (la
  búsqueda no lo conecta), el escaneo y el reintento BLE lo saltan, y el tick cierra cualquier canal o GATT de una
  señal ignorada que siga abierto. "Volver a usar" pide una búsqueda ANT+ nueva. `SensorManager` lo expone. Hoja:
  botón "No es mío" con confirmación en toda tarjeta con señal y sección "Ignorados" con "Volver a usar".

**Análisis de impacto (Clave 3, servicios).** No cambia el ciclo de vida de `WorkoutService` ni de
`FreeRideService`: ni arranque, ni parada, ni notificación, ni colectores nuevos. Solo se añade una llamada a un
método que ya existía (`resetFlows()`) dentro del método de reconexión que ya existía, y en `FreeRideService` dos
marcas de tiempo internas. El cronómetro y la grabación siguen. R8.3 se conserva (la hoja no para la sesión) y R9
también (Reconectar ya no olvida los huecos). Tras Reconectar, los huecos pasan por `Scanning`/`Disconnected` unos
segundos (sin valor grabado) hasta que vuelven los datos. `NavGraph.kt`, Room y Gradle sin tocar.

**No se hace (lo decide el usuario):**
- Vigilante que reinicie solo las radios si un aparato guardado lleva N s sin fuente: podría reiniciar en bucle con
  una banda que simplemente no se lleva puesta.
- Que un desconocido no ocupe HR2 solo con una sesión en marcha (cambia R1; con R10 se quita en dos toques).

**Tests (suite esperada 230 + 6 = 236):** `DualSlotAllocatorTest` +5 (fuente conectada sin dato: valor hasta 3 s y
luego sin fuente con el hueco conservado; vuelve con dato nuevo; ignorar suelta el hueco y no lo vuelve a ocupar;
ignorar sobrevive a `clear()`; volver a usar). `DeviceCardsTest` +1 (ids de una tarjeta para ignorar). Reconectar,
`SEARCHING` y el cierre de canales no se pueden probar sin radio: prueba real.
**Test cambiado:** `CadenceSlotAllocatorTest` "ANT conectada sin datos (biela parada) conserva el hueco" pasa a
"…conserva el hueco pero no da valor (R2_1)": 30 s sin **ningún** paquete → hueco conservado y sin fuente. La biela
parada real sigue siendo 0 rpm válido porque la ANT+ manda paquetes sin vuelta (`onAlive` la mantiene fresca). Un
cadenciómetro BLE que deja de notificar al parar pasa a "sin valor" a los ≈ 8 s en lugar de mostrar 0 fijo (a
vigilar en T-07).

### 14. Un aparato nuevo pregunta antes de usarse (R11; pedido por el usuario 2026-11-22; fuera de T-06 y T-07)
**Motivo:** en el paseo, la banda de alguien que pasaba entró sola como HR2. El usuario prefiere que la app
pregunte "¿quieres conectar este otro pulsómetro?" y que, si solo se oye un rato, se suelte sola.

**`DualSlotAllocator`** (lógica pura):
- Nuevo parámetro `askNewMs: Long? = null` (null = no pregunta, como hasta ahora; los dos hubs pasan 15 s). Los tests
  antiguos no lo usan y siguen probando el reparto sin la pregunta; los nuevos lo activan.
- Aparato de confianza: alguna de sus señales (`idsOf`) tiene ficha en el registro o está en `approved` (aprobadas en
  la sesión; sobreviven a `clear()`).
- `assignPending`: tras la cuarentena (R3) y antes de `slotFor`, un aparato sin confianza no recibe hueco; si su señal
  tiene dato, cuenta en `askSince` desde cuándo; sin dato o fuera de la lista se borra. A los `askNewMs` pasa a
  `asking()` (`NewDeviceAsk`: tipo, id, nombre, señal, valor; ANT+ primero).
- `slotFor`: la cuenta de aparatos ANT+ para "ANT+ desplaza a BLE" solo incluye aparatos de confianza o con hueco.
- `approve(id)` (borra su cuenta), `ignore()` y `pause()` también borran cuentas.
- Logcat: "… nuevo, sin aprobar: se preguntará" y "… aprobado".

**Hubs** (`HeartRateHub`, `CadenceHub`): `toAsk: StateFlow<List<NewDeviceAsk>>` (se rellena en cada tick, vacío al
parar las radios) y `approve(id, name)` = `registry.remember(kind, id, name)` + `allocator.approve(id)`.
`SensorManager`: `hrToAsk()`, `cadenceToAsk()`, `approveSignal(kind, id, name)` (solo se añade).

**UI:** `ui/devices/NewDevicePrompt.kt`: `NewDevicePromptViewModel` (Hilt; pulso primero, luego cadencia; un aviso
cada vez) y `NewDevicePrompt` (franja arriba con "Sí, usar" / "No es mío"; "No es mío" usa `ignoreSignals` con la
etiqueta "nombre · señal"). Se dibuja en `MainActivity` dentro del `Box` de `NavGraph()`, alineada arriba (abajo
taparía Pausa/Detener e indicadores de página; arriba ya sale el aviso de km de Ruta). Observarla no arranca sensores.

**Impacto (Clave 3):** sin cambios en `NavGraph.kt`, servicios, Room ni Gradle. `MainActivity` ya es
`@AndroidEntryPoint`; el ViewModel vive con la actividad. Los aparatos ya guardados no cambian. Las bandas propias no
guardadas preguntan una vez (el "Sí" las guarda).

**Tests (suite esperada 236 + 7 = 243), `DualSlotAllocatorTest` con la pregunta activada:** un desconocido no recibe
hueco y se pregunta a los 15 s; la cuenta vuelve a cero si deja de dar dato; aprobar le da hueco y quita el aviso;
un aparato guardado entra sin preguntar; la otra señal de un aparato aprobado no pregunta; un ANT+ sin aprobar no
desplaza a un BLE propio; ignorar quita el aviso. La franja y el guardado en el registro: prueba real.

### 15. Arreglos tras la prueba del 2026-11-25 (1.6.157, sesión 7701dccb; R12, R13; fuera de T-06 y T-07)
**Motivo:** en la prueba de campo de la spec 008, ANT+ se cayó con la pantalla apagada y el BLE no volvió a
conectar al encender el Bluetooth. Se hace primero A y, tras la prueba del usuario, B.

**Causa BLE (vista en `dumpsys bluetooth_manager`):** al apagar el Bluetooth, el sistema da de baja la conexión de la
app (11:30:58, `unregisterClient(android)`) antes de cortar el enlace. Por eso la app nunca recibe
`STATE_DISCONNECTED` y la dirección se queda en `gatts`. El vigilante de 20 s no actúa porque el aparato ya estaba
conectado. Al encender, `onScanResult` descarta los 1138 anuncios recibidos por `gatts.containsKey`. Además, Samsung
deja la radio en modo solo BLE, y `ensureBle` borraba la referencia del escaneo sin pararlo: quedaron 4 escaneos
de la app (2 de pulso, 2 de cadencia).

**Causa ANT+ (logcat del sistema):**
- Samsung congela (Freecess) `com.dsi.ant.usbservice` y `com.dsi.ant.service.socket` entre búsquedas. La búsqueda
  se repetía cada 15 s aunque HR1 estuviera conectado: el pulso quiere 2 huecos y la cadencia busca sin aparato.
- Cada ciclo descongela y vuelve a congelar el servicio USB. Resultado: «Failed to write/claim USB», huecos de
  6–10 s y, al final, ADAPTER_NOT_DETECTED sin fin. El servicio llegó a cerrarse a las 12:20:24.
- `AntRadio.onStopped` reintentaba cada 15 s sin espaciar ni avisar.

**A — BLE** (`HeartRateHub`, `CadenceHub`, nuevo `sensor/BleLinkWatch.kt`, lógica pura):
- **Receptor `ACTION_STATE_CHANGED`**: se registra en `start()` y se quita en `stopRadios()`; aviso en hilo de
  fondo.
  - `STATE_TURNING_OFF` / `STATE_OFF`: `onBluetoothOff()` = `stopBle()` + las señales BLE pasan a
    `connected=false` + log «Bluetooth apagado, radios BLE reiniciadas».
  - `STATE_ON`: `startBle()`.
  - `ensureBle()` (cada 5 s) pasa a `@Synchronized`; si ve el Bluetooth apagado con escaneo o conexiones vivas,
    llama a `onBluetoothOff()`.
- **Escaneo**: se guarda el `BluetoothLeScanner` con que se arrancó y se para con él (pedirlo de nuevo con el
  Bluetooth a medias puede dar nulo).
  - `startBle()` para antes el escaneo previo.
  - Un callback que ya no es el actual ignora sus resultados (`this !== scanCallback`), así que un escaneo escapado
    no conecta nada.
  - `connectBle` no conecta con el Bluetooth apagado (Samsung en modo solo BLE).
- **`BleLinkWatch(silentMs)`**: la conexión entra al suscribirse (`onServicesDiscovered`). Cada notificación de la
  medida (con o sin contacto con la piel, R2.1) renueva su hora. `silent(now)` da las direcciones mudas.
  - Se quita al desconectar, en `stopBle()` y al cerrarla por silencio.
  - En `tick()`, cada conexión muda se cierra, sale de `gatts`, pasa a `connected=false` y el escaneo la vuelve a
    enganchar. Log «sin datos N s, se reconecta».
  - Umbral: 20 s en pulso y 30 s en cadencia (una cadencia BLE parada puede notificar menos).
- **Impacto (Clave 3):** sin cambios en servicios, `NavGraph.kt`, Room, Gradle ni en `SensorManager`. El receptor
  vive con las radios del hub (`start`/`stopRadios`).

**B — ANT+** (`AntRadio`, nuevo `sensor/AntRetryPolicy.kt`, lógica pura; `SensorManager.antAdapterState()`;
`SensorBar`):
- `AntRetryPolicy`:
  - `pauseMs(anyChannelTracking)`: 15 s sin canales, 120 s con alguno.
  - `onAdapterFailure()`: 30, 60, 120 y 300 s como tope; vale para ADAPTER_NOT_DETECTED y OTHER_FAILURE de la
    búsqueda y de `requestAccess`.
  - `onSuccess()` y `reset()` (Reconectar o pincho enchufado) vuelven a lo normal.
- `AntRadioClient` gana `antTracking(): Boolean` (algún canal recibiendo).
- `AntRadio.adapterState: StateFlow<AntAdapterState>` (`Ok` / `NotDetected(retryAtMs)`). La `SensorBar` muestra
  «Pincho ANT+ no detectado».
- `onAntLost`: el `close()` del canal sale del `tick()` sincronizado y pasa a la cadena `closing` de `AntRadio`.
- Ajuste del usuario: las tres apps `com.dsi.ant.*` en «Aplicaciones que nunca entran en suspensión» y «No
  restringido».
- **Experimento condicional (solo si con lo anterior sigue la congelación):** enlace persistente a
  `AntRadioService` mientras funcionen los hubs (`<queries>` en el manifiesto). Se valida aparte con
  `dumpsys activity processes` y el logcat de Freecess.

**Tests:**
- `BleLinkWatchTest` para A.
- `AntRetryPolicyTest` para B.
- El receptor y el GATT se comprueban en la prueba real (apagar y encender el Bluetooth; `dumpsys
  bluetooth_manager` con 2 escaneos de la app).

## Archivos
- Nuevos: `sensor/SensorDeviceRegistry.kt`, `sensor/PairingPolicy.kt`, `sensor/DualSlotAllocator.kt`,
  `sensor/CadenceHub.kt`, `sensor/AntRadio.kt`, `sensor/CscParser.kt`, `ui/components/SensorSlotBadge.kt`, `ui/components/SensorBar.kt`,
  `ui/devices/DevicesSheet.kt`, `ui/devices/DevicesViewModel.kt`, `ui/devices/DeviceCards.kt`,
  `SensorSlotsViewModel` (dentro de `SensorSlotBadge.kt`), tests en `app/src/test/.../sensor/` y
  `app/src/test/.../ui/devices/`.
- Modificados: `sensor/HeartRateHub.kt`, `sensor/SensorManager.kt`, `service/FreeRideService.kt`,
  `ui/freeride/FreeRideScreen.kt`, `ui/home/HomeScreen.kt`, `ui/workout/WorkoutScreen.kt`,
  `ui/config/ConfigViewModel.kt`, `ui/config/ConfigScreen.kt`.
- Borrados: `sensor/HrDeviceRegistry.kt` (sustituido), `sensor/BleManagers.kt`.
- Sin cambios: Room, Gradle, `NavGraph.kt`, ciclo de vida de los servicios.

## Verificación
- Tests unitarios (los lanza el usuario con `./gradlew test`):
  - Registro: migración v1 → v2, manual manda, separar bloquea, hueco único por tipo.
  - Emparejado: nombre; 1+1 seguro; 2+2 nunca persistente; plano solo sesión; empate no agrupa.
  - Huecos: un aparato dual en un solo hueco; caída y vuelta de ANT+; hueco fijo aunque llegue segundo.
  - Cadencia: lo mismo, incluido 0 rpm.
- Prueba real (usuario, S22 Ultra / Tab S9) con Logcat `💓` y `🚴`.
