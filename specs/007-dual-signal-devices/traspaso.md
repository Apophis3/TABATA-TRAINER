# Traspaso — Spec 007 (aparatos de doble señal ANT+ ↔ BLE)

Documento para continuar en un hilo nuevo. Pegar en el hilo nuevo:

> Lee `AGENTS.md`, `docs/constitution.md`, `specs/007-dual-signal-devices/{spec.md, plan.md, tasks.md, traspaso.md}`
> y la primera entrada de `00_estado_proyecto_y_memoria.md`. Seguimos con la spec 007 en la rama
> `spec-007-dual-signal-devices`. No compiles ni instales (lo hago yo).

## Objetivo
- Un aparato físico (pulsómetro o cadenciómetro, emite por ANT+ y BLE) = un hueco fijo (HR1/HR2, C1/C2).
- ANT+ siempre preferente; BLE solo de respaldo, sin cambiar de hueco.
- Funciona con dos bandas a la vez.
- En todas las pantallas se ve hueco + señal (ANT+/BLE). Cadencia con el mismo criterio.

## Decisiones del usuario
- Emparejado automático (solo si es seguro) + manual.
- Gestión desde una hoja deslizante al tocar el bloque de sensores de Home (sin tocar `NavGraph.kt`).
- Hueco fijo por aparato. Ruta Libre con HR1/HR2 y C1/C2 separados, como Tabata.

## Estado de las tareas (`tasks.md`)
- **T-01 [x]** validada: registro genérico `SensorDeviceRegistry`/`DeviceBook` + `PairingPolicy`. Prueba real con
  dos bandas → HR1 y HR2 por ANT+.
- **T-02 [~]** código escrito, **pendiente de evidencia del usuario**:
  - `sensor/DualSlotAllocator.kt` (lógica pura): hueco preferido con reserva blanda, ANT+ desplaza a BLE,
    hueco ANT+ caído reservado 60 s frente a aparatos solo-BLE, cuarentena 30 s, relevo, `SlotStatus`.
  - `HeartRateHub` lo usa; radios fuera a los 5 s sin observadores y huecos olvidados 55 s después.
    `slotStatus(slot)` expuesto (aún sin uso en UI). Contrato de `SensorManager` intacto.
  - `DeviceDirectory` (interfaz) en `SensorDeviceRegistry.kt`.
  - Tests: `DualSlotAllocatorTest`.
- **T-03 … T-07** sin empezar. Una tarea cada vez, solo tras confirmación del usuario.

## Qué tiene que hacer el usuario para cerrar T-02
1. Compilar y pasar la suite de tests (debe pasar `DualSlotAllocatorTest` además de los anteriores).
2. Prueba real con Logcat filtrado por `💓`:
   - Dos bandas encendiendo antes los BLE → en ~30 s HR1 y HR2 acaban en ANT+.
   - Una sola banda dual, BLE primero, en reposo → solo HR1, nada en HR2.
   - Salir de las pantallas con sensores 20–30 s y volver → HR1/HR2 no se intercambian.
3. Aportar el Logcat / resultado de tests; validar y marcar T-02 `[x]`.

## Decisiones posteriores
- **R8.1–R8.3 (2026-11-21):** la hoja "Dispositivos" se abre tocando la barra de sensores en **todas** las
  pantallas que la tienen (Home, Tabata, Ruta Libre también en marcha, Config). Barra común `SensorBar`; la
  flecha de refrescar sigue reconectando; abrir la hoja no para la sesión. Va en T-06.

## Pendiente
- Fallo visual conocido: la flecha de volver tapa el chip HR1 en Tabata → se arregla en T-04.

## Reglas que no hay que olvidar
- El agente no ejecuta Gradle ni `adb install` (hook lo bloquea; ojo: también bloquea comandos de shell que
  contengan el texto "gradlew"). `adb` está en
  `C:\Users\APO\AppData\Local\Android\Sdk\platform-tools\adb.exe` (solo lectura: `devices`, `logcat`).
- Sin dependencias Gradle, sin cambios en Room, sin tocar el contrato de `SensorManager` sin spec.
- Código en inglés; UI y documentación en español. Anotar cada sesión en `00_estado_proyecto_y_memoria.md`.
- Commit solo cuando el usuario lo pida.
