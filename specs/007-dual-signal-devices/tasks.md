# Tasks 007 — Aparatos de doble señal (ANT+ ↔ BLE)

Reglas: una tarea cada vez, en orden. Cada tarea deja la app compilable. No empezar la siguiente hasta que el
usuario confirme la anterior con evidencia. El agente **no** ejecuta Gradle ni `adb install` (Clave 5).

- [x] T-01 *(validada por el usuario 2026-11-21: compila, suite OK; prueba real con dos bandas → HR1 y HR2 por ANT+)* **Registro genérico + emparejado seguro.**
  `SensorDeviceRegistry` / `DeviceBook` (HR y cadencia, fichas de una señal, hueco preferido, manual, parejas
  bloqueadas, formato v2 con migración del de la spec 003). `PairingPolicy` (persistente solo si es seguro;
  sesión por mutuo-mejor sin empate, también en reposo con umbral 4). `HeartRateHub.detectTwins` pasa a usar
  `PairingPolicy`. Tests: `DeviceBookTest`, `PairingPolicyTest` (sustituye a `HrDeviceBookTest`).
- [x] T-02 *(validada 2026-11-21 con la 1.6.113: suite 187/187 verde. Comprobar en Home que HR2 se apaga al quitar una banda pasa a T-07.) (prueba real OK del usuario 2026-11-21: ANT+↔BLE, dos bandas, poner/quitar. Fallo visto: en Home
  HR2 seguía "activo" (punto y batería) tras quitar la banda → corregido, el hueco sin señal ya no da batería.
  Suite del usuario: fallaba `una ANT caida un momento no cede su hueco…` → corregido en `DualSlotAllocator`: la
  cuarentena cumplida ya no se reinicia mientras el aparato espera hueco.
  Suite completa verde con el arreglo en la 1.6.113)* **Huecos.** `DualSlotAllocator` extraído de `HeartRateHub.tick()`; hueco fijo por aparato; sin
  reinicio de huecos al cambiar de pantalla; `SlotStatus` por hueco. **Regla ANT+ desplaza a BLE:** dos grupos
  ANT+ distintos son siempre dos aparatos → ocupan los dos huecos y un grupo solo-BLE cede el suyo (hoy, con
  dos bandas indistinguibles, los BLE llegan antes, la ANT+ espera 30 s en cuarentena y encuentra los huecos
  llenos). Tests: `DualSlotAllocatorTest` (incluido 2 bandas, BLE antes que ANT+ → HR1/HR2 = ANT+).
- [x] T-03 *(validada 2026-11-21 con la 1.6.113 salvo cadencia real: suite 187/187 verde; dos bandas en HR1/HR2 por ANT+ con la búsqueda compartida; tirón corregido. Prueba real de cadenciómetros pasa a T-07: el usuario no los tiene a mano)*
  **Cadencia.** `CadenceHub` + radio ANT+ compartida; `SensorManager` delega; borrar
  `createCadenceFlow*` y `BleManagers.kt`. Tests de cadencia con el asignador.
  Hecho: `AntRadio` (una búsqueda ANT+ para pulso y cadencia), `CadenceHub` (espejo de `HeartRateHub`, 0 rpm
  válido), `CscParser`, `SensorManager` reducido a fachada; un historial solo de ceros no empareja por valores.
  Tests: `CadenceSlotAllocatorTest`, `CadencePairingTest`, `CscParserTest`.
  Prueba real 1.6.111 (usuario, sin cadenciómetros a mano): dos bandas OK en HR1/HR2 por ANT+ con la búsqueda
  compartida. Fallo visto: tirón de ~1 s cada 30 s (cierre de la búsqueda ANT+ en el hilo principal) →
  corregido, el cierre va en segundo plano. 1.6.113: el hilo principal ya no se bloquea (la espera de ~1 s del
  plugin queda en un hilo de fondo); ANT+ encontrada a los 3 s del arranque.
- [x] T-04 *(validada 2026-11-21 con la 1.6.117: suite 187/187 verde; capturas del usuario de Tabata en vertical y horizontal y de Config, con una y dos bandas por ANT+)*
  **Hueco y señal en Tabata y Config.** `SensorSlotBadge`; Tabata lee `SlotStatus`; Config HR1/HR2/C1/C2.
  Hecho: `SensorManager.hrSlotStatus/cadenceSlotStatus` (solo lectura); `ui/components/SensorSlotBadge.kt`
  (`ProtocolBadge`, `SensorSlotBadge`, `SensorSlotsViewModel`, `SlotStatus.signalState`); Tabata con señal y
  batería por hueco y barra superior apartada de la flecha; Config en rejilla HR1 HR2 / C1 C2.
  Visto en la prueba: la Decathlon (HR2) da una línea más irregular que la XOSS (HR1) aunque se cambien de
  sitio → es la banda (cada una calcula su valor), no la app. Fuera de T-04: al quitar una banda ANT+ sigue dando el último valor ~10–20 s.
- [x] T-05 *(validada 2026-11-21 con la 1.6.120: suite 194/194 verde; ruta con dos bandas por ANT+; BD con HR1 y HR2 separados; capturas en vertical y horizontal)*
  **Ruta Libre separada.** HR1/HR2 y C1/C2 con señal; medias HR1/HR2 separadas; escucha C2.
  Hecho: `util/laps/DualSlotSamples` (lecturas por hueco); `FreeRideStats` con campos HR2/C2; colector C2 y
  vigilante de cadencia por hueco; vueltas y Room con el hueco principal (HR1/C1, si no HR2/C2). Pantalla:
  tarjeta HR1 | HR2, C1 | C2 si hay cadencia (si no, ritmo), vuelta en curso con HR1 y HR2, barra con GPS y los
  4 huecos. Sin probar: cierre de vueltas (pasa a T-07), C2 y cadencia real (T-07). Visto: en horizontal la
  franja de pasos queda bajo el indicador de páginas (la columna se desplaza).
- [x] Arreglos menores *(validados 2026-11-21 con la 1.6.134: suite 209/209; capturas de Ruta horizontal páginas 1 y 3)* *(pedidos por el usuario 2026-11-21 antes de T-06; detalle en `plan.md` §8)*: valor
  congelado al quitar una banda (ANT+ sin latidos nuevos, BLE sin contacto → R2.1); Ruta horizontal con la franja
  de pasos bajo el indicador de páginas. (El "lápiz" de Config no es un fallo: es el S Pen del usuario; no se toca.)
  *Prueba 1.6.123: suite 202/202 verde; corte BLE "sin contacto" visto en Logcat. Parte ANT+ vista en la 1.6.128.
  Prueba 1.6.132: suite 209/209; en la página 3 en horizontal los botones tapaban el indicador de páginas y la
  última fila quedaba cortada → corregido (botones a la columna izquierda, `plan.md` §8). Falta: página 3 en
  horizontal tras el arreglo y página 1 en horizontal con la franja de pasos (con pasos > 0). Prueba 1.6.134: vistas
  las dos; queda un recorte menor del borde de la 3.ª fila de la página 3 en horizontal y sin probar con una vuelta
  cerrada (no bloquea).*
- [x] *(validada 2026-11-21 con la 1.6.134: suite 209/209; "Pasos/min" en página 3 vertical y horizontal; tarjeta partida/única vista)* Ruta: un pulsómetro → tarjeta grande; cadencia o pasos/min en la vuelta *(pedido por el usuario
  2026-11-21; `spec.md` R7.1–R7.2, `plan.md` §9)*: en la página 1, con un solo pulsómetro con señal, una sola
  tarjeta de pulso con el número lo más grande posible (con dos, HR1 | HR2); en la página 3, la misma casilla
  muestra "Cadencia" (rpm) con cadenciómetro y "Pasos/min" (ppm de la vuelta) sin él.
  *Prueba 1.6.128: suite 209/209; una y dos bandas, quitar/poner: tarjeta única ↔ partida sin parpadeo.
  Prueba 1.6.132: página 3 en vertical dice "Pasos/min" (7 ppm) sin cadenciómetro; página 1 partida en vertical y
  horizontal. Prueba 1.6.134: página 3 en horizontal con "Pasos/min" (57 ppm).*
- [x] *(validada 2026-11-21 con la 1.6.134: suite 209/209; recuadro azul BLE en Ruta (barra, barra compacta y tarjetas) y Tabata; verde ANT+ visto en Config (1.6.130), confirmado por el usuario)* Señal por recuadro de color *(pedido por el usuario 2026-11-21; `spec.md` R6.1, `plan.md` §10)*: el hueco
  (HR1/HR2/C1/C2) dentro de un recuadro verde (ANT+) o azul (BLE) en lugar del texto "ANT+"/"BLE".
  *Prueba 1.6.130: captura de Config con HR1/HR2 en recuadro verde (ANT+); Logcat: HR1 pasa de BLE a ANT+ y vuelve
  tras una caída de 3 s, sin errores. Prueba 1.6.132: suite 209/209 tras el cambio; Ruta en vertical con HR1/HR2
  en recuadro azul (BLE; ANT+ no respondió) en barra y tarjeta. La barra compacta de Ruta en horizontal seguía con
  puntos → corregido (`plan.md` §10). Prueba 1.6.134: barra compacta y Tabata con recuadro azul.*
- [ ] T-06 **Hoja "Dispositivos" común + Home.** Estado real por hueco en Home; hoja con
  unir/separar/renombrar/hueco/olvidar/reconectar/guiado; barra de sensores común `SensorBar` que abre la hoja
  al tocarla en Home, Tabata, Ruta (también en marcha) y Config (R8.1–R8.3); la flecha sigue reconectando.
  *Código hecho 2026-11-21 (aprobado por el usuario; detalle en `plan.md` §11, notas en §11.9). Pendiente de
  prueba: suite esperada 227 (209 + 18) y la evidencia de `plan.md` §11.8.*
- [ ] T-07 **Validación real (usuario).** Una banda dual; dos bandas; cadenciómetro dual; caída/vuelta ANT+.
  Incluye lo aplazado: cadencia real de T-03 (C1 al pedalear, 0 rpm en ~3 s al parar sin perder hueco, C1/C2 con
  dos aparatos) y, de T-02, HR2 apagado en Home al quitar una banda.
  Logcat `💓` / `🚴`.
