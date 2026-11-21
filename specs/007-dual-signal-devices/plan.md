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
  ≈ 39 sp y HR1 | HR2 ≈ 25 sp; sin pasos, ≈ 130 dp. La página 3 en horizontal se vio bien en T-05: sin cambios.
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
- Solo `ui/components/SensorSlotBadge.kt`, que usan todas las pantallas (Tabata, Config, barra y tarjetas de
  Ruta; Home lo heredará en T-06 con `SensorBar`). Ninguna pantalla cambia su llamada.
- `SensorSlotBadge`: el texto del hueco va dentro de un recuadro redondeado (borde 1,5 dp + fondo del mismo color
  al 15 %): verde `0xFF66BB6A` ANT+, azul `0xFF42A5F5` BLE (los colores que ya tenía la etiqueta). Sin señal, el
  borde es transparente con el mismo relleno, para que nada salte de sitio al conectar. Se quita la etiqueta de
  texto "ANT+"/"BLE" (`ProtocolBadge`, sin otros usos). Batería sin cambios (al lado o debajo con `stacked`).
- Lectores de pantalla: descripción "HR1, ANT+" / "HR1, BLE" / "HR1, sin señal".
- Sin tocar lógica, sensores, servicios, Room, Gradle ni `NavGraph.kt`.

## Archivos
- Nuevos: `sensor/SensorDeviceRegistry.kt`, `sensor/PairingPolicy.kt`, `sensor/DualSlotAllocator.kt`,
  `sensor/CadenceHub.kt`, `sensor/AntRadio.kt`, `sensor/CscParser.kt`, `ui/components/SensorSlotBadge.kt`, `ui/components/SensorBar.kt`,
  `ui/devices/DevicesSheet.kt` (+ ViewModel), `SensorSlotsViewModel` (dentro de `SensorSlotBadge.kt`),
  tests en `app/src/test/.../sensor/`.
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
- Prueba real (usuario, S22+ / Tab S9) con Logcat `💓` y `🚴`.
