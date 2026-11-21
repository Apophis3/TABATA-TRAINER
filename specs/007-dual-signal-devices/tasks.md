# Tasks 007 — Aparatos de doble señal (ANT+ ↔ BLE)

Reglas: una tarea cada vez, en orden. Cada tarea deja la app compilable. No empezar la siguiente hasta que el
usuario confirme la anterior con evidencia. El agente **no** ejecuta Gradle ni `adb install` (Clave 5).

- [x] T-01 *(validada por el usuario 2026-11-21: compila, suite OK; prueba real con dos bandas → HR1 y HR2 por ANT+)* **Registro genérico + emparejado seguro.**
  `SensorDeviceRegistry` / `DeviceBook` (HR y cadencia, fichas de una señal, hueco preferido, manual, parejas
  bloqueadas, formato v2 con migración del de la spec 003). `PairingPolicy` (persistente solo si es seguro;
  sesión por mutuo-mejor sin empate, también en reposo con umbral 4). `HeartRateHub.detectTwins` pasa a usar
  `PairingPolicy`. Tests: `DeviceBookTest`, `PairingPolicyTest` (sustituye a `HrDeviceBookTest`).
- [~] T-02 *(prueba real OK del usuario 2026-11-21: ANT+↔BLE, dos bandas, poner/quitar. Fallo visto: en Home
  HR2 seguía "activo" (punto y batería) tras quitar la banda → corregido, el hueco sin señal ya no da batería.
  Suite del usuario: fallaba `una ANT caida un momento no cede su hueco…` → corregido en `DualSlotAllocator`: la
  cuarentena cumplida ya no se reinicia mientras el aparato espera hueco.
  Pendiente: compilar, pasar la suite y comprobar que HR2 se apaga en Home)* **Huecos.** `DualSlotAllocator` extraído de `HeartRateHub.tick()`; hueco fijo por aparato; sin
  reinicio de huecos al cambiar de pantalla; `SlotStatus` por hueco. **Regla ANT+ desplaza a BLE:** dos grupos
  ANT+ distintos son siempre dos aparatos → ocupan los dos huecos y un grupo solo-BLE cede el suyo (hoy, con
  dos bandas indistinguibles, los BLE llegan antes, la ANT+ espera 30 s en cuarentena y encuentra los huecos
  llenos). Tests: `DualSlotAllocatorTest` (incluido 2 bandas, BLE antes que ANT+ → HR1/HR2 = ANT+).
- [~] T-03 *(código hecho 2026-11-21, sin compilar: pendiente compilar, suite y prueba real del usuario)*
  **Cadencia.** `CadenceHub` + radio ANT+ compartida; `SensorManager` delega; borrar
  `createCadenceFlow*` y `BleManagers.kt`. Tests de cadencia con el asignador.
  Hecho: `AntRadio` (una búsqueda ANT+ para pulso y cadencia), `CadenceHub` (espejo de `HeartRateHub`, 0 rpm
  válido), `CscParser`, `SensorManager` reducido a fachada; un historial solo de ceros no empareja por valores.
  Tests: `CadenceSlotAllocatorTest`, `CadencePairingTest`, `CscParserTest`.
- [ ] T-04 **Hueco y señal en Tabata y Config.** `SensorSlotBadge`; Tabata lee `SlotStatus`; Config HR1/HR2/C1/C2.
- [ ] T-05 **Ruta Libre separada.** HR1/HR2 y C1/C2 con señal; medias HR1/HR2 separadas; escucha C2.
- [ ] T-06 **Hoja "Dispositivos" común + Home.** Estado real por hueco en Home; hoja con
  unir/separar/renombrar/hueco/olvidar/reconectar/guiado; barra de sensores común `SensorBar` que abre la hoja
  al tocarla en Home, Tabata, Ruta (también en marcha) y Config (R8.1–R8.3); la flecha sigue reconectando.
- [ ] T-07 **Validación real (usuario).** Una banda dual; dos bandas; cadenciómetro dual; caída/vuelta ANT+.
  Logcat `💓` / `🚴`.
