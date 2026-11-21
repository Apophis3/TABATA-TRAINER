# Traspaso — Spec 007 (aparatos de doble señal ANT+ ↔ BLE)

Documento para continuar en un hilo nuevo. Pegar en el hilo nuevo:

> Lee AGENTS.md, docs/constitution.md, specs/007-dual-signal-devices/{spec.md, plan.md, tasks.md, traspaso.md}
> y la primera entrada de 00_estado_proyecto_y_memoria.md. Seguimos con la spec 007 en la rama
> `spec-007-dual-signal-devices` (T-01…T-05 validadas). Empieza T-06: hoja "Dispositivos" común + Home, con la
> barra de sensores común `SensorBar` que abre la hoja en Home, Tabata, Ruta y Config (R8, R8.1–R8.3). Punto de
> partida en `traspaso.md`, sección "T-06". Anota el detalle en `plan.md` antes del código. Valida con mis
> pruebas (lee tú los resultados de tests y el Logcat del móvil). No compiles ni instales (lo hago yo).

## Objetivo de la spec
- Un aparato físico (pulsómetro o cadenciómetro, emite por ANT+ y BLE) = un hueco fijo (HR1/HR2, C1/C2).
- ANT+ siempre preferente; BLE solo de respaldo, sin cambiar de hueco.
- Funciona con dos bandas a la vez.
- En todas las pantallas se ve hueco + señal (ANT+ verde / BLE azul) y batería (R6). Cadencia con el mismo criterio.

## Estado (T-05 validada, versión instalada 1.6.120)
- **T-01 [x]** registro genérico `SensorDeviceRegistry`/`DeviceBook` + `PairingPolicy`.
- **T-02 [x]** `DualSlotAllocator` (huecos fijos, ANT+ desplaza a BLE, cuarentena, `SlotStatus`).
- **T-03 [x]** salvo prueba real de cadencia: `CadenceHub`, `CscParser`, `AntRadio` (una búsqueda ANT+
  compartida; el cierre va en segundo plano y se espera con `awaitClosed()`), `SensorManager` como fachada.
  Suite 187/187 verde con la 1.6.113; dos bandas en HR1/HR2 por ANT+.
- **Aplazado a T-07:** cadencia real (el usuario no tiene los cadenciómetros a mano) y comprobar que HR2 se
  apaga en Home al quitar una banda.
- **T-04 [x]** validada 2026-11-21 con la 1.6.117 (suite 187/187; capturas de Tabata y Config con dos bandas):
  `SensorManager.hrSlotStatus/cadenceSlotStatus`; `ui/components/SensorSlotBadge.kt` (`ProtocolBadge`,
  `SensorSlotBadge`, `SensorSlotsViewModel` + `rememberSensorSlots()`, `SlotStatus.signalState`); Tabata con
  señal/batería/punto desde `SlotStatus` y barra superior apartada de la flecha; Config con rejilla HR1 HR2 / C1 C2.
  Detalle en `plan.md` §7.
- **T-05 [x]** validada 2026-11-21 con la 1.6.120 (suite 194/194; ruta con dos bandas; BD con HR1/HR2
  separados): `util/laps/DualSlotSamples`; `FreeRideStats` con HR2/C2; colector C2; Ruta con tarjeta HR1 | HR2,
  C1 | C2 si hay cadencia, vuelta en curso con HR1/HR2 y barra con los 4 huecos (`SensorSlotBadge`). Pendiente
  para T-07: probar cierre de vueltas con dos bandas, C2 y cadencia real. Detalle en `plan.md` §7 "Detalle (T-05)".
  Siguiente: T-06 (hoja "Dispositivos" + Home + `SensorBar` común; la barra de Ruta de T-05 pasa a `SensorBar`).
  T-07 sin empezar. Una tarea cada vez.

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
  - Hueco + señal + batería: `ui/components/SensorSlotBadge.kt` (`SensorSlotBadge`, `rememberSensorSlots()`,
    `SlotStatus.signalState`).
  - Barras actuales a sustituir por `SensorBar`: Home `SensorBar` privada (`ui/home/HomeScreen.kt` l. 275, usa
    `BleScanStatus` + `rememberSensorBatteries`); Tabata chips (`ui/workout/WorkoutScreen.kt` ≈ l. 852–869);
    Ruta `FreeRideSensorBar` (`ui/freeride/FreeRideScreen.kt` l. 1292, ya con los 4 huecos de T-05); Config
    (`ui/config/ConfigScreen.kt`, rejilla con `SensorSlotBadge` ≈ l. 633).
  - Nuevo: `ui/devices/DevicesSheet.kt` + `DevicesViewModel` (Hilt), `ModalBottomSheet` dentro de cada
    pantalla; `NavGraph.kt` no se toca.
- No tocar: Room, Gradle, `NavGraph.kt`, ciclo de vida de `WorkoutService` / `FreeRideService`.
- Pendiente visto (fuera de alcance, solo si el usuario lo pide): en Config el lápiz flotante tapa en parte el
  interruptor del GPS; al quitar una banda ANT+ sigue dando el último valor ~10–20 s; en Ruta horizontal la
  franja de pasos queda bajo el indicador de páginas.

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
  contengan el texto "gradlew"). `adb` está en
  `C:\Users\APO\AppData\Local\Android\Sdk\platform-tools\adb.exe` (solo lectura: `devices`, `logcat`).
  Hay dos dispositivos en `adb devices` (uno "offline"): usar `-s adb-R3CTA0JHTEZ-ORXOOh._adb-tls-connect._tcp`.
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
