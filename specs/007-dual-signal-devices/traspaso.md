# Traspaso — Spec 007 (aparatos de doble señal ANT+ ↔ BLE)

Documento para continuar en un hilo nuevo. Pegar en el hilo nuevo:

> Lee `AGENTS.md`, `docs/constitution.md`, `specs/007-dual-signal-devices/{spec.md, plan.md, tasks.md, traspaso.md}`
> y la primera entrada de `00_estado_proyecto_y_memoria.md`. Seguimos con la spec 007 en la rama
> `spec-007-dual-signal-devices`. Empieza T-03 (Cadencia). No compiles ni instales (lo hago yo).

## Objetivo
- Un aparato físico (pulsómetro o cadenciómetro, emite por ANT+ y BLE) = un hueco fijo (HR1/HR2, C1/C2).
- ANT+ siempre preferente; BLE solo de respaldo, sin cambiar de hueco.
- Funciona con dos bandas a la vez.
- En todas las pantallas se ve hueco + señal (ANT+/BLE). Cadencia con el mismo criterio.

## Decisiones del usuario
- Emparejado automático (solo si es seguro) + manual.
- Hueco fijo por aparato. Ruta Libre con HR1/HR2 y C1/C2 separados, como Tabata.
- **R8.1–R8.3:** la hoja "Dispositivos" (`ModalBottomSheet`, sin tocar `NavGraph.kt`) se abre tocando la barra
  de sensores en **todas** las pantallas que la tienen (Home, Tabata, Ruta Libre también en marcha, Config).
  Barra común `SensorBar`; la flecha de refrescar sigue reconectando; abrir la hoja no para la sesión. Va en T-06.

## Estado de las tareas (`tasks.md`)
- **T-01 [x]** validada: registro genérico `SensorDeviceRegistry`/`DeviceBook` + `PairingPolicy`.
- **T-02 [~]** prueba real OK del usuario con la **v1.6.106** (ANT+↔BLE, dos bandas, poner/quitar):
  - `sensor/DualSlotAllocator.kt` (lógica pura): hueco preferido con reserva blanda, ANT+ desplaza a BLE,
    hueco ANT+ caído reservado 60 s frente a aparatos solo-BLE, cuarentena 30 s, relevo, `SlotStatus`.
  - `HeartRateHub` lo usa; radios fuera a los 5 s sin observadores y huecos olvidados 55 s después.
    `slotStatus(slot)` expuesto (aún sin uso en UI). Contrato de `SensorManager` intacto.
  - Fallo visto en la 1.6.106: tras quitar una banda, Home seguía mostrando HR2 activo con batería (Home deduce
    HR2/C2 "conectado" de la batería). **Corregido después de la 1.6.106** en `DualSlotAllocator.pickSource`
    (sin fuente → batería null) + test `aparato quitado deja el hueco sin bateria`. **Sin commit todavía.**
  - Para cerrarla: el usuario compila la **1.6.107**, pasa la suite y comprueba que HR2 se apaga en Home al
    quitar una banda. Con eso, marcar T-02 `[x]`.
- **T-03 Cadencia** — siguiente. El usuario pidió continuar con lo planificado mientras prueba T-02 en la
  1.6.107. Ver `plan.md` §4: `CadenceHub` espejo de `HeartRateHub` con `DualSlotAllocator(CADENCE, …, "C", …)`,
  radio ANT+ compartida con el pulso, un único escaneo BLE continuo, `SensorManager.observeCadence*()` y
  `cadenceBattery()` delegan; borrar `createCadenceFlow*`, autoconexión de cadencia por `DeviceProfileDao` y
  `BleManagers.kt`. Tests de cadencia con el asignador (incluido 0 rpm). Logcat `🚴`.
- **T-04 … T-07** sin empezar. Una tarea cada vez.

## Pendiente
- Fallo visual conocido: la flecha de volver tapa el chip HR1 en Tabata → se arregla en T-04.
- Hacer commit del arreglo de batería de T-02 cuando el usuario lo pida.

## Reglas que no hay que olvidar
- El agente no ejecuta Gradle ni `adb install` (hook lo bloquea; ojo: también bloquea comandos de shell que
  contengan el texto "gradlew"). `adb` está en
  `C:\Users\APO\AppData\Local\Android\Sdk\platform-tools\adb.exe` (solo lectura: `devices`, `logcat`).
- La versión instalada se ve en `version.properties` (BUILD sube en cada APK): sirve para saber si el usuario
  probó con el código actual.
- Sin dependencias Gradle, sin cambios en Room, sin tocar el contrato de `SensorManager` sin spec.
- Código en inglés; UI y documentación en español. Anotar cada sesión en `00_estado_proyecto_y_memoria.md`.
- Commit solo cuando el usuario lo pida.
