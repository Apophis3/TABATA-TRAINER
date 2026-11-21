# Traspaso — Spec 007 (aparatos de doble señal ANT+ ↔ BLE)

Documento para continuar en un hilo nuevo. Pegar en el hilo nuevo:

> Lee `AGENTS.md`, `docs/constitution.md`, `specs/007-dual-signal-devices/{spec.md, plan.md, tasks.md, traspaso.md}`
> y la primera entrada de `00_estado_proyecto_y_memoria.md`. Seguimos con la spec 007 en la rama
> `spec-007-dual-signal-devices`. Sigue T-03 (Cadencia): valida con mis pruebas (lee tú los resultados de tests
> y el Logcat del móvil). No compiles ni instales (lo hago yo).

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
    (sin fuente → batería null) + test `aparato quitado deja el hueco sin bateria`. Commit `9c89ebb`.
  - La suite (1.ª vez que se pasaba entera) falló en `una ANT caida un momento no cede su hueco…`: al cumplir
    la cuarentena sin hueco libre se borraba y volvía a empezar, y el BLE nunca cogía el hueco huérfano.
    Corregido en `DualSlotAllocator.assignPending` (se borra solo al asignar). En el commit de T-03.
    **Falta repetir la suite** con el arreglo.
  - Para cerrarla: el usuario compila (1.6.107 o la que incluya T-03), pasa la suite y comprueba que HR2 se
    apaga en Home al quitar una banda. Con eso, marcar T-02 `[x]`.
- **T-03 [~] Cadencia** — código hecho y en commit (2026-11-21, ver `git log`). Compila (BUILD 111) y sus 21
  tests nuevos pasan; **falta** repetir la suite entera y la prueba real:
  - `sensor/AntRadio.kt`: una sola búsqueda ANT+ multi-dispositivo para pulso y cadencia (unión de tipos,
    ventanas 15/15 s, se cierra antes de pedir acceso, no busca mientras alguien conecta, límite 20 s).
    `HeartRateHub` ya no tiene búsqueda propia: la usa (cambio también en el pulso → probar 💓).
  - `sensor/CadenceHub.kt`: espejo de `HeartRateHub` (`DualSlotAllocator(CADENCE, …, "C")`, `PairingPolicy`
    CADENCE, BLE CSC 0x1816 con escaneo continuo, ANT+ `BIKE_CADENCE` solo cadencia). 0 rpm es dato válido:
    3 s sin vuelta → 0 sin desconectar; fresca = conectada y con dato en 5 s (ANT+: evento en bruto la mantiene
    viva aunque esté parada).
  - `sensor/CscParser.kt`: rpm BLE desde vueltas/tiempo de biela (desbordamiento 16 bits, 0–300).
  - `PairingPolicy`: un historial solo de ceros no empareja por valores (dos cadenciómetros parados son
    iguales y un agrupado de sesión erróneo no se deshace). Sin efecto en el pulso.
  - `SensorManager` reducido a fachada (mismo contrato público). Borrados `BleManagers.kt`, `createCadenceFlow*`,
    autoconexión por `DeviceProfileDao` (DAO y entidad de Room siguen, sin uso) y `CADENCE_ANT_PLUS_DELAY_MS`.
  - Tests nuevos: `CadenceSlotAllocatorTest`, `CadencePairingTest`, `CscParserTest`.
  - Para cerrarla: compilar, suite verde, prueba real con Logcat `🚴` y `💓` (ver bitácora).
  - Punto a vigilar: si al dejar de pedalear un ANT+ no aparece "parado, 0 rpm" en ~3 s, el plugin repite la
    última cadencia; arreglo previsto: marcar vuelta solo cuando cambie el contador acumulado del evento en bruto.
- **T-04 … T-07** sin empezar. Una tarea cada vez.

## Pendiente
- Fallo visual conocido: la flecha de volver tapa el chip HR1 en Tabata → se arregla en T-04.
- Usuario: repetir la suite y prueba real de T-02/T-03. Agente: leer la evidencia y, si va bien, proponer
  marcar T-02 y T-03 `[x]`.

## Reglas que no hay que olvidar
- El agente no ejecuta Gradle ni `adb install` (hook lo bloquea; ojo: también bloquea comandos de shell que
  contengan el texto "gradlew"). `adb` está en
  `C:\Users\APO\AppData\Local\Android\Sdk\platform-tools\adb.exe` (solo lectura: `devices`, `logcat`).
- **La evidencia la lee el agente, no se pide en capturas:** el S22+ está conectado por adb inalámbrico
  (`adb devices` → `adb-R3CTA0JHTEZ-…`) y, tras compilar el usuario, los resultados de tests quedan en
  `app/build/test-results/testDebugUnitTest/*.xml`.
- La versión instalada se ve en `version.properties` (BUILD sube en cada APK): sirve para saber si el usuario
  probó con el código actual.
- Sin dependencias Gradle, sin cambios en Room, sin tocar el contrato de `SensorManager` sin spec.
- Código en inglés; UI y documentación en español. Anotar cada sesión en `00_estado_proyecto_y_memoria.md`.
- Commit solo cuando el usuario lo pida.
