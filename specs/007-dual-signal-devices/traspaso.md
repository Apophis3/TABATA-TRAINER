# Traspaso — Spec 007 (aparatos de doble señal ANT+ ↔ BLE)

Documento para continuar en un hilo nuevo (texto de arranque: el que da el agente en el chat).

## Siguiente (2026-11-22, arreglos tras el paseo programados, sin probar)
- **Paseo del usuario 2026-11-22 (1.6.140, Ruta 09:23–10:45):** (a) la banda ANT+ de alguien que pasaba entró como
  HR2 y no se podía quitar; grabó 142 fijo 36 min (canal en "buscando" y `pickSource` daba su último valor);
  (b) la XOSS (HR1) dejó de dar dato a las 10:22 y "Reconectar" no la recuperó (no reiniciaba las radios). Logcat
  del paseo perdido (búfer); visto en el volcado Bluetooth: caída del Bluetooth a las 09:13 y llamadas por
  auriculares 10:10–10:44. Causa exacta de (b) sin probar.
- **Arreglos `plan.md` §13** (`spec.md` R2.1 ampliado, R8.2, R10; entrada propia en `tasks.md`), sin commit, sin
  compilar:
  - Sin valor congelado: `DualSlotAllocator.pickSource` mantiene una señal conectada sin dato 3 s y luego el hueco
    queda sin fuente (conserva el aparato). `FreeRideService` solo graba pulso de menos de 5 s.
  - ANT+ "buscando" > 30 s → perdido: canal cerrado y nueva búsqueda (`onAntState` / `checkAntLost` en los hubs).
  - Reconectar de verdad: `hub.restart()` reinicia radios y conserva huecos e ignoradas; lo llaman
    `WorkoutService.refreshSensors` y `FreeRideService.reconnectSensors`.
  - "No es mío" en cada tarjeta de la hoja (con confirmación) + panel "Ignorados" con "Volver a usar". Ignoradas
    solo en memoria (hasta cerrar la app).
  - Tests +6 → suite esperada **236** (`DualSlotAllocatorTest` +5, `DeviceCardsTest` +1; cambiado un test de
    `CadenceSlotAllocatorTest`).
- **Prueba pedida:** suite 236; "No es mío" en la 2.ª banda; Reconectar con la ignorada; "Volver a usar"; quitar una
  banda → valor apagado en ≈ 8 s y no grabado; con HR1 caída, Reconectar en Ruta. Logcat `💓`/`🚴`.
- **R11 aprobado por el usuario el mismo día ("Sí, así") y programado (`plan.md` §14):** un aparato sin guardar no
  ocupa hueco (tampoco el primero); tras 15 s con dato sale arriba "¿Es tuyo?" con "Sí, usar" (lo guarda con
  `registry.remember`) y "No es mío" (R10). `DualSlotAllocator(askNewMs)` (los hubs pasan 15 s; los tests antiguos
  sin pregunta), `NewDeviceAsk`, `toAsk` en los hubs, `hrToAsk/cadenceToAsk/approveSignal` en `SensorManager`,
  `ui/devices/NewDevicePrompt.kt` dibujado en `MainActivity`. Tests +7 → **243/243 en verde** (1.6.143). Falta la prueba con bandas.
- **Prueba de R11:** con una banda propia sin guardar, el aviso sale a los ~15 s y "Sí, usar" la pone en HR1; al
  reabrir la app ya no pregunta. Con una 2.ª banda, "No es mío" la manda a "Ignorados". Una banda que se apaga antes
  de 15 s no provoca aviso. Logcat `💓` "nuevo, sin aprobar" / "aprobado".
- **Decisión abierta (usuario):** reinicio automático si un hueco guardado lleva mucho sin dato.

## Antes (2026-11-21, arreglos tras la prueba parcial de T-06 programados, sin probar)
- **Arreglos `plan.md` §12** (pedidos por el usuario; entrada propia en `tasks.md`, fuera de T-06/T-07), sin
  commit, sin compilar:
  - R3.1: una señal agrupada en la sesión solo se compara con su pareja (`PairingPolicy.evaluate(…, sessionPartners)`,
    `DualSlotAllocator.sessionPartners`, llamado en `detectTwins` de los dos hubs) → la 2.ª banda ya no espera
    30 s por empate con la 1.ª agrupada. Lo guardado para siempre no cambia.
  - Página 3 de Ruta en horizontal: separaciones 8 → 4 dp (columna) y 6 → 4 dp (rejilla).
  - `ConfigViewModel`: la cancelación al salir ya no se registra como "Error crítico".
  - Borrado `rememberSensorBatteries`; `BatteryLowWarnings` vuelve a leer las baterías (el aviso "Batería baja" no
    salía en Tabata ni Ruta desde T-04/T-05).
  - Suite esperada **230** (227 + 3: `PairingPolicyTest` +2, `DualSlotAllocatorTest` +1).
  - Prueba: Reconectar con las dos bandas puestas → la ANT+ de la 2.ª se agrupa en ~15 s (Logcat `agrupados en la
    sesión`), sin esperar 30 s; página 3 en horizontal con la 3.ª fila entera (y con una vuelta cerrada); salir de
    Config sin `Error crítico`; aviso de batería baja si alguna banda la tiene.
- **No hecho, lo decide el usuario:** acortar la pausa de 15 s entre búsquedas ANT+ (`AntRadio.SEARCH_PAUSE_MS`).
- Las pruebas de T-06 de abajo siguen igual (valen con la 1.6.138 o con la siguiente).

## Antes (2026-11-21, T-06 en commit; prueba parcial con la 1.6.138, el resto mañana)
- **T-06 aprobado, programado y en commit** (`plan.md` §11; notas de lo hecho en §11.9). Probado en parte con la
  1.6.138 (ver abajo); el usuario hace el resto de pruebas al día siguiente.
- **Orden de prueba que falta** (se le dio al usuario):
  1. En la hoja, "Unir con…" la XOSS y la Decathlon con su ANT+ → "Guardado" con dos señales, conserva nombre y
     hueco; tras Reconectar vuelven a ANT+ en segundos, sin la espera de 30 s.
  2. "Emparejar paso a paso" con una sola banda encendida (sin unir u olvidada) hasta "listo".
  3. Separar una banda unida → ANT+ y BLE no se vuelven a juntar solas (`separados en la sesión` / bloqueo).
  4. Olvidar un aparato → su tarjeta pasa a "No guardado".
  5. Con Tabata o Ruta en marcha: abrir la hoja desde la barra, cambiar una banda a HR2 → aviso "sesión en curso".
  6. Ruta en horizontal: tocar la barra compacta de las páginas 1 y 3 → se abre la hoja.
  El usuario apunta la hora aproximada de lo raro para buscarlo en el Logcat.
- Cuando el usuario compile, instale y pruebe: leer tests (`app/build/test-results/testDebugUnitTest/*.xml`,
  esperado **227/227**), Logcat (`Registro:`, `ocupa su hueco guardado`, `Dispositivos: hoja abierta/cerrada`,
  `separados en la sesión`) y capturas (`adb exec-out screencap -p`) de la barra en Home, Config, Tabata y Ruta
  (vertical y horizontal) y de la hoja. Lista completa en `plan.md` §11.8.
- Si algo falla al compilar: los cambios de pantalla están en `HomeScreen`, `ConfigScreen`, `WorkoutScreen`
  (`WorkoutSensorBar`) y `FreeRideScreen` (`freeRideGps`); la hoja en `ui/devices/`.
- **Prueba parcial 1.6.138:** 227/227; hoja abierta/cerrada en las 4 pantallas, renombrar, hueco fijo HR1,
  Reconectar y "ocupa su hueco guardado" vistos. Falta: Ruta en horizontal, cambiar hueco con sesión en marcha,
  Olvidar, unir/separar ANT+ en la hoja y el modo guiado hasta "listo".
- Visto en la prueba (no es de T-06): con dos bandas en el mismo pecho la ANT+ de la 2.ª banda puede esperar 30 s,
  porque `PairingPolicy` compara también con el BLE ya agrupado en la sesión y ve empate. Reconectar borra las
  agrupaciones de sesión. Uniendo las bandas en la hoja no pasa. Arreglo posible (pendiente de decidir): no
  contar en la comparación las señales ya agrupadas en la sesión.
- Con la prueba buena y el visto bueno del usuario, marcar `[x]` T-06. T-07 solo cuando lo diga.
- Sigue sin tocar (menor): borde de abajo de la 3.ª fila en la página 3 de Ruta en horizontal; falta probarla con
  una vuelta cerrada.

## Antes (tras la 1.6.134)
- **Los tres arreglos previos a T-06 (§8, §9, §10) validados y marcados `[x]`** con la 1.6.134 (suite 209/209).
  Menor, no bloquea: en la página 3 de Ruta en horizontal el borde de abajo de la 3.ª fila queda recortado unos
  dp; sin probar esa página con una vuelta cerrada. Siguiente: **T-06**, solo cuando el usuario lo diga.

## Antes (tras la 1.6.132)
0. **Prueba 1.6.132 hecha** (suite 209/209 tras R6.1; "Pasos/min" visto; BLE por fallo de ANT+). Arreglados
   después y sin probar: página 3 de Ruta en horizontal (botones a la izquierda, `plan.md` §8) y barra compacta
   horizontal con recuadro (`plan.md` §10). Falta: capturas de página 3 y página 1 (con pasos > 0) en
   horizontal, y Tabata con el recuadro. Luego marcar `[x]` los tres arreglos si el usuario lo confirma.

## Antes (tras la 1.6.130)
1. **Cerrar lo pendiente antes de T-06** (código hecho, falta evidencia del usuario). Cuando el usuario compile,
   instale y pruebe: leer tests (`app/build/test-results/testDebugUnitTest/*.xml`, esperado 209/209) y Logcat;
   capturas con `adb exec-out screencap -p`. Si confirma, marcar `[x]` en `tasks.md`:
   - **Arreglos menores (§8):** ANT+ ya validado en la 1.6.128; faltan capturas de Ruta horizontal (franja de
     pasos bajo el indicador) y vertical.
   - **Ruta un pulsómetro / cadencia o pasos/min (R7.1–R7.2, §9):** tarjeta única ↔ partida validada en la 1.6.128
     (log `FreeRideScreen: Ruta: tarjeta de pulso única/partida`). Falta: página 3 dice "Pasos/min" sin
     cadenciómetro (sale a los 10 s de vuelta) y páginas 1 y 3 en horizontal.
   - **Señal por recuadro de color (R6.1, §10):** Config validado en la 1.6.130 (HR1/HR2 en recuadro verde).
     Falta: tests tras el cambio y capturas de Tabata y Ruta (barra y tarjetas), con ANT+ y, si se puede, BLE.
     Detalle visto: en la barra de Ruta el texto del hueco con señal ya es verde (`slotSignalColor`); con BLE
     queda texto verde en recuadro azul. Si al usuario no le gusta, poner el texto en blanco (solo
     `FreeRideSensorBar`).
2. **Después, T-06** (hoja "Dispositivos" + Home + `SensorBar`), solo cuando el usuario lo diga. Punto de partida
   abajo.

## Objetivo de la spec
- Un aparato físico (pulsómetro o cadenciómetro, emite por ANT+ y BLE) = un hueco fijo (HR1/HR2, C1/C2).
- ANT+ siempre preferente; BLE solo de respaldo, sin cambiar de hueco.
- Funciona con dos bandas a la vez.
- En todas las pantallas se ve el hueco dentro de un recuadro verde (ANT+) o azul (BLE) y la batería (R6, R6.1).
  Cadencia con el mismo criterio.

## Estado (T-01…T-05 y los tres arreglos previos a T-06 validados; T-06 en prueba parcial con la 1.6.138; arreglos §12 con código, sin probar)
- **T-01 [x]** registro genérico `SensorDeviceRegistry`/`DeviceBook` + `PairingPolicy`.
- **T-02 [x]** `DualSlotAllocator` (huecos fijos, ANT+ desplaza a BLE, cuarentena, `SlotStatus`).
- **T-03 [x]** salvo cadencia real: `CadenceHub`, `CscParser`, `AntRadio` (búsqueda ANT+ compartida),
  `SensorManager` como fachada.
- **T-04 [x]** hueco y señal en Tabata y Config (`ui/components/SensorSlotBadge.kt`).
- **T-05 [x]** Ruta Libre con HR1/HR2 y C1/C2 separados (`util/laps/DualSlotSamples`, `FreeRideStats` HR2/C2).
- **Arreglos previos a T-06:**
  - §8 valor congelado (R2.1: `AntBeatWatch`, `BleContactWatch` en `HeartRateHub`) y Ruta horizontal.
  - §9 R7.1: `SlotStatus.fresh`, `util/laps/HeartRateCardPolicy` (única ↔ partida, espera 8 s / 2 s),
    `FreeRideScreen` `MetricsGrid` (l. 338), `rememberHeartRateCardLayout` (l. 420), `SingleSlotCard` (l. 1067).
    R7.2: `CurrentLapView.cadenceLabel` ("Cadencia" / "Pasos/min").
  - §10 R6.1: `SensorSlotBadge` con recuadro de color; `ProtocolBadge` borrado; `protocolColor()` nuevo.
- **Aplazado a T-07:** cadencia real, C2, cierre de vueltas con dos bandas, HR2 apagado en Home al quitar banda.

## T-06 — punto de partida (hoja "Dispositivos" común + Home, R8)
- Qué pide (`tasks.md`; `spec.md` R8, R8.1–R8.3; `plan.md` §5 y §6): hoja "Dispositivos" con señales visibles y
  aparatos guardados; unir, separar, renombrar, asignar hueco, olvidar, reconectar; modo guiado. Barra común
  `SensorBar` que abre la hoja al tocarla en Home, Tabata, Ruta (también en marcha) y Config; la flecha sigue
  reconectando; abrir la hoja no para la sesión. Home con estado real por hueco. Anotar el detalle en `plan.md`
  antes del código.
- Lo que ya existe:
  - Registro: `sensor/SensorDeviceRegistry.kt` ya tiene `devices` (StateFlow), `link`, `split`, `forget`,
    `setSlot`, `rename`, `remember` (cada cambio se guarda y se registra en Logcat con 💓/🚴 "Registro:").
  - Señales visibles: `HeartRateHub.devices` (StateFlow, l. 82) y su equivalente en `CadenceHub`; `restart()`
    para reconectar.
  - Hueco + señal + batería: `ui/components/SensorSlotBadge.kt` (`SensorSlotBadge` con recuadro de color,
    `protocolColor()`, `rememberSensorSlots()`, `SlotStatus.signalState`, `SlotStatus.fresh`).
  - Barras actuales a sustituir por `SensorBar`: Home `SensorBar` privada (`ui/home/HomeScreen.kt` l. 275, usa
    `BleScanStatus` + `rememberSensorBatteries`); Tabata chips (`ui/workout/WorkoutScreen.kt` ≈ l. 852–869);
    Ruta `FreeRideSensorBar` (`ui/freeride/FreeRideScreen.kt` l. 1406, ya con los 4 huecos de T-05); Config
    (`ui/config/ConfigScreen.kt`, rejilla con `SensorSlotBadge` ≈ l. 633).
  - Nuevo: `ui/devices/DevicesSheet.kt` + `DevicesViewModel` (Hilt), `ModalBottomSheet` dentro de cada
    pantalla; `NavGraph.kt` no se toca.
- No tocar: Room, Gradle, `NavGraph.kt`, ciclo de vida de `WorkoutService` / `FreeRideService`.
- Arreglos menores hechos antes de T-06 (detalle en `plan.md` §8, `spec.md` R2.1): valor congelado al quitar
  una banda (ANT+ sin latidos nuevos / BLE sin contacto) y franja de pasos bajo el indicador en Ruta horizontal
  (`MetricsGrid`). Prueba 1.6.123: suite 202/202; corte BLE "sin contacto" visto (HR2 dejó de usarse al segundo).
  Visto: al reconectarse sola la banda quitada pasó un valor suelto (70 bpm), porque la conexión nueva aún no
  había visto "con contacto"; sin importancia práctica. Falta lo del punto 1 de "Siguiente".
- El "lápiz" que se ve en Config es el icono del S Pen del usuario: **no es un fallo y no se toca** (ni código ni
  móvil, ni se apunta como pendiente).

## Observaciones de la última prueba (1.6.111 → 1.6.113)
- Al arrancar, el receptor ANT+ a veces responde ADAPTER_NOT_DETECTED 1–2 min (del móvil/servicio ANT+; ya
  pasaba antes). Mientras, las bandas van por BLE. En la 1.6.113 respondió a los 3 s.
- Cortes ANT+→BLE de pocos segundos sin cambiar de hueco: es R2 funcionando.
- Posible mejora no hecha (pedir antes): acortar la pausa entre ventanas de búsqueda ANT+ (15 s) para que la
  2.ª banda pase antes a ANT+.
- Peticiones del usuario ya cubiertas por la spec: Ruta con HR1/HR2 y C1/C2 separados (prefiere ver los dos)
  → T-05. Detalle de sesión sin C2 → fuera de alcance (Room), posible spec futura.

## Decisiones del usuario
- Emparejado automático (solo si es seguro) + manual.
- Hueco fijo por aparato. Ruta Libre con HR1/HR2 y C1/C2 separados, como Tabata.
- **R8.1–R8.3:** la hoja "Dispositivos" (`ModalBottomSheet`, sin tocar `NavGraph.kt`) se abre tocando la barra
  de sensores en todas las pantallas (Home, Tabata, Ruta también en marcha, Config). Barra común `SensorBar`;
  la flecha de refrescar sigue reconectando; abrir la hoja no para la sesión. Va en T-06.

## Reglas que no hay que olvidar
- El agente no ejecuta Gradle ni `adb install` (hook lo bloquea; ojo: también bloquea comandos de shell que
  contengan el texto "gradlew" y a veces comandos `adb` largos encadenados: lanzar `adb` solo en su comando). `adb` está en
  `C:\Users\APO\AppData\Local\Android\Sdk\platform-tools\adb.exe` (solo lectura: `devices`, `logcat`).
  Hay dos entradas en `adb devices` (una "offline"): usar la que diga `device`, `-s adb-R3CTA0JHTEZ-ORXOOh._adb-tls-connect._tcp`
  (el 2026-11-21 por la tarde `-s R3CTA0JHTEZ` daba "not found").
- **La evidencia la lee el agente, no se pide en capturas:** S22+ por adb inalámbrico; tras compilar el
  usuario, los tests quedan en `app/build/test-results/testDebugUnitTest/*.xml`.
- Lecturas guardadas por segundo (HR1/HR2/cadencia): copiar la BD con
  `adb exec-out run-as com.tuapp.tabatatrainer cat databases/tabata_trainer_db` (y `-wal`, `-shm`) a la carpeta
  temporal y leerla con `C:/Users/APO/Tools/SQLite/sqlite3` (tabla `sensor_readings`). Solo lectura.
- La versión instalada se ve en `version.properties` (BUILD sube en cada APK) y con
  `adb shell dumpsys package com.tuapp.tabatatrainer` (versionName, lastUpdateTime).
- Sin dependencias Gradle, sin cambios en Room, sin tocar el contrato de `SensorManager` sin spec (solo ampliar).
- Código en inglés; UI y documentación en español. Anotar cada sesión en `00_estado_proyecto_y_memoria.md`.
- Commit solo cuando el usuario lo pida (`version.properties` va en el commit).
