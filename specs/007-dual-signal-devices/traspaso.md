# Traspaso — Spec 007 (aparatos de doble señal ANT+ ↔ BLE)

Documento para continuar en un hilo nuevo (texto de arranque: el que da el agente en el chat).

## Siguiente (2026-11-21, tras la 1.6.130)
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

## Estado (T-01…T-05 validadas; tres arreglos previos a T-06 hechos, a falta de capturas)
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
