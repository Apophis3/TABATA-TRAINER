# Estado del proyecto y memoria de sesiones — Tabata Trainer

Bitácora de trabajo. Cada sesión se anota aquí: qué se tocó, por qué, en qué quedó
y qué falta. Complementa a `01_historico_errores.md` (errores resueltos) y a la
memoria privada de Claude. **Lo más reciente arriba.**

---

## 2026-11-22 — Spec 007 · Arreglos tras el paseo (pendiente de prueba)
- El usuario probó Ruta en un paseo (1.6.140) y pidió reparar dos fallos. Anotados antes del código (`spec.md`
  R2.1, R8.2, R10; `plan.md` §13; entrada propia en `tasks.md`). Fuera de T-06 y T-07.
- **Pulsómetro ajeno como HR2:** su canal ANT+ quedó "buscando" y el hueco siguió dando y grabando 142 fijo 36 min.
  Ahora: sin dato 3 s → el hueco no da valor (conserva el aparato); Ruta no graba pulso de más de 5 s; un canal
  ANT+ "buscando" más de 30 s se cierra y se vuelve a buscar. En la hoja, "No es mío" ignora esa señal (suelta su
  hueco, no se conecta ni se graba) hasta "Volver a usar" o cerrar la app.
- **Pulsómetro propio perdido:** "Reconectar" en Ruta y Tabata no reiniciaba las radios. Ahora sí (conserva huecos e
  ignoradas). La causa de la caída no se pudo ver: el Logcat del paseo ya no estaba; el volcado Bluetooth muestra
  una caída del Bluetooth a las 09:13 y llamadas por auriculares entre 10:10 y 10:44.
- Tests +6 → suite esperada **236**. Sin compilar (lo hace el usuario). Sin tocar Room, Gradle ni `NavGraph.kt`;
  `SensorManager` solo añade funciones (contrato anotado en la spec). Sin commit.
- Abierto (decide el usuario): reinicio automático de radios.
- **Después, R11 (aprobado por el usuario: "Sí, así"):** un pulsómetro o cadenciómetro sin guardar ya no entra solo en
  ningún hueco. Tras 15 s oyéndose sale arriba, en cualquier pantalla, "¿Es tuyo?": "Sí, usar" lo guarda para
  siempre; "No es mío" lo ignora. Si deja de oírse, el aviso se quita y su canal ANT+ se suelta a los 30 s. Las
  bandas propias aún sin guardar preguntarán una vez. Aviso arriba y no abajo: abajo tapaba Pausa/Detener.
  `spec.md` R11, `plan.md` §14. Tests +7 → **243/243 en verde** (1.6.143, 17:39); falta la prueba con bandas. `MainActivity` dibuja el aviso; `NavGraph.kt` sin
  tocar.

---

## 2026-11-21 — Spec 007 · Arreglos tras la prueba parcial de T-06 (pendiente de prueba)
- El usuario pidió terminar los pendientes menores mientras él prueba T-06. Anotados antes del código
  (`spec.md` R3.1, `plan.md` §12, entrada propia en `tasks.md`). Fuera de T-06 y T-07; T-06 sigue `[ ]`.
- **2.ª banda sin espera de 30 s (R3.1):** al comparar valores, una señal ya agrupada en la sesión solo se compara
  con su pareja; ya no hace de rival (con dos bandas en el mismo pecho daba empate). El recuento para guardar
  para siempre no cambia. Misma regla en cadencia.
- **Ruta horizontal, página 3:** separaciones más pequeñas (8 → 4 y 6 → 4 dp) para que quepa la 3.ª fila; el
  margen del indicador de páginas no se toca.
- **Config:** salir ya no deja "Error crítico en collector" (era la cancelación normal).
- **Visto al limpiar:** el aviso "Batería baja en HRx" no salía en Tabata ni Ruta desde T-04/T-05 (nadie leía las
  baterías). `BatteryLowWarnings` las vuelve a leer. Borrado `rememberSensorBatteries` (sin uso).
- No hecho (decide el usuario): acortar la pausa de 15 s entre búsquedas ANT+.
- Tests +3 → suite esperada **230**. Sin compilar (lo hace el usuario). Sin tocar Room, Gradle, `NavGraph.kt`,
  servicios ni contrato de `SensorManager`. Sin commit.

---

## 2026-11-21 — Spec 007 · T-06: hoja "Dispositivos" + `SensorBar` programadas (pendiente de prueba)
- El usuario aprobó `plan.md` §11 («Aprobado, sigue»). Código hecho; sin compilar ni instalar (lo hace el usuario).
- Sensores (solo se añade): señales vistas con su aparato y hueco (`VisibleSignal`, `signals` en los hubs),
  "no son el mismo aparato" (`unlinkSession` + `block` en el registro). Arreglo: tras separar, el asignador ya no
  vuelve a juntar ANT+ y BLE (ni relevo ni espera de 30 s con parejas bloqueadas).
- Nuevo `ui/devices/` (hoja, ViewModel y lógica pura de tarjetas y modo guiado) y `ui/components/SensorBar.kt`.
  La barra común sustituye a las de Home, Config, Tabata y Ruta; tocarla abre la hoja, la flecha reconecta.
- Notas (`plan.md` §11.9): en Home no sale el aviso de "sesión en curso"; Tabata muestra GPS verde/gris (no
  "buscando"); la barra compacta de Ruta en horizontal mantiene su tamaño.
- Tests nuevos: 18 → suite esperada 227. Sin tocar Room, Gradle, `NavGraph.kt` ni el ciclo de vida de los
  servicios. Página 3 de Ruta horizontal sin tocar (menor pendiente). Nada marcado `[x]`. Sin commit.
- Falta: prueba del usuario con la evidencia de `plan.md` §11.8.
- **Prueba parcial 1.6.138 (18:45–18:52):** suite **227/227**. Logcat: hoja abierta/cerrada en Home, Config,
  Tabata y Ruta; renombrar (Decathlon → "…_SER") y hueco fijo HR1 (XOSS) guardados; tras Reconectar, "XOSS ocupa
  su hueco guardado". Captura 18:52: HR1 y HR2 en ANT+ con batería. Sin errores ni cierres.
- ANT+ "tarda": de 18:45 a 18:49 el lápiz USB ANT+ no estaba (`ADAPTER_NOT_DETECTED`; `USB_DEVICE_ATTACHED` a
  las 18:49:24). Luego: xoss ANT+ entra en HR1 en ~15 s (se agrupa por valores). Decathlon ANT+ (8298) se agrupó
  en 14 s, pero Reconectar (18:49:58) borró la agrupación de sesión; la 2.ª vez esperó los 30 s y entró por "ANT+
  preferente" (~57 s desde Reconectar). Causa: las dos bandas en el mismo pecho dan casi el mismo pulso y la
  comparación por valores (`PairingPolicy`) ve empate, porque también cuenta el BLE de la XOSS ya agrupado en la
  sesión. No es de T-06. Remedio sin código: unir cada banda en la hoja (queda guardada y entra sin espera).
- Ruido previo, no de T-06: `ConfigViewModel … Error crítico en collector` con `JobCancellationException` al salir
  de Config (ya salía en la 1.6.137).
- El usuario sigue la prueba mañana (orden en `traspaso.md` → "Siguiente"). Commit de T-06 hecho a petición del
  usuario, con T-06 aún `[ ]`. Relevo de hilo: texto dado en el chat.

---

## 2026-11-21 — Spec 007 · T-06: plan de la hoja "Dispositivos" + `SensorBar` (aprobado)
- Leídos AGENTS, constitución, spec/plan/tasks/traspaso y código de Home, Config, Tabata, Ruta, hubs y registro.
- `plan.md` §11 escrito (datos para la hoja, arreglo de "separar" en el asignador, hoja, barra común, tests,
  evidencia). `spec.md` Restricciones: T-06 amplía `SensorManager` (`hrSignals`, `cadenceSignals`, `unlinkSession`).
- Visto en el código: tras separar, el asignador podía volver a juntar ANT+ y BLE en la sesión (relevo y espera
  de 30 s no miran parejas bloqueadas) → arreglo propuesto en §11.2.
- Sin código. Esperando aprobación del usuario.

---

## 2026-11-21 — Spec 007 · Prueba 1.6.132 y arreglos de Ruta en horizontal (validados con la 1.6.134)

### Prueba del usuario (1.6.132, instalada 17:54:44)
- Tests (17:53): 209/209 verdes, ya tras el cambio del recuadro de color.
- Logcat: sin cierres ni errores. ANT+ no respondió en toda la prueba (ADAPTER_NOT_DETECTED cada 15 s); HR1
  XOSS y HR2 Decathlon por BLE → recuadros azules, correcto.
- Capturas: Ruta página 1 vertical y horizontal con tarjeta partida HR1 | HR2 y recuadro azul; página 3
  vertical con "Pasos/min" (7 ppm) sin cadenciómetro (R7.2 bien).
- Fallos vistos: (1) página 3 en horizontal: botones parar/vuelta/pausa encima del indicador de páginas y
  última fila de la rejilla cortada; (2) barra compacta de Ruta en horizontal con puntos, sin recuadro (R6.1).
- Visto, de la spec 006 (no se toca sin pedirlo): en la página 3 en vertical el título "Vuelta manual M1" queda
  bajo la flecha de volver.

### Hecho (aprobado por el usuario)
- `spec.md` R6.1 y `plan.md` §8/§10 actualizados antes del código.
- `LandscapeLapsPage`: botones al pie de la columna izquierda (como la página 1); columna derecha con
  `PAGE_INDICATOR_CLEARANCE`; botón "NUEVA VUELTA" 44 dp; casillas 22 sp, relleno 5 dp.
  Columna izquierda: huecos, estado, tiempo (52 sp) y última vuelta en un bloque desplazable; botones fijos abajo
  (con una vuelta cerrada la franja crece y, si no, recortaría los botones).
- `CompactSensorRow` usa `SensorSlotBadge` (texto `slotSignalColor`, sin batería); `SensorSlotBadge` gana
  `showBattery = true` por defecto.
- Sin tocar Room, Gradle, `NavGraph.kt`, servicios ni lógica. Tests sin cambios (suite esperada 209).
- Falta: compilar, instalar y capturas de página 3 en horizontal (sin y con una vuelta manual cerrada),
  página 1 en horizontal con pasos > 0, Tabata con el recuadro y, si ANT+ responde, un recuadro verde en Ruta.

### Prueba del usuario (1.6.134, instalada 18:07:24)
- Tests (18:06): 209/209 verdes. Logcat sin cierres; ANT+ otra vez sin responder (ADAPTER_NOT_DETECTED), HR1 y
  HR2 por BLE.
- Página 3 horizontal: botones a la izquierda, indicador libre, "Pasos/min" 57 ppm. La 3.ª fila se ve con su
  título pero el borde de abajo de las casillas queda recortado unos dp bajo "NUEVA VUELTA" (menor).
- Página 1 horizontal con pasos (38): franja Pasos / Pasos/min / Zancada encima del indicador, sin solaparse.
- Barra compacta horizontal con HR1/HR2 en recuadro azul; no toca la flecha de volver.
- Tabata: HR1/HR2 en recuadro azul (BLE) con batería.
- Sin ver: página 3 horizontal con una vuelta cerrada; recuadro verde ANT+ en Ruta/Tabata (ANT+ no respondió;
  verde visto en Config en la 1.6.130).
- El usuario confirma (ANT+ ya se veía bien antes) → `[x]` en `tasks.md` para los tres arreglos (§8, §9, §10).
  Siguiente: T-06 cuando el usuario lo diga. Sin commit. Nada marcado `[x]` aún. Sin commit.

---

## 2026-11-21 — Spec 007 · Ruta: un pulsómetro → tarjeta grande; cadencia o pasos/min (pendiente de prueba)

### Contexto
Peticiones del usuario antes de T-06 (traspaso "Siguiente"). Sin compilar ni instalar.

### Hecho
- `spec.md` R7.1 (tarjeta única con un pulsómetro) y R7.2 (casilla "Cadencia" o "Pasos/min"); `plan.md` §9;
  `tasks.md` actualizado. T-06 sin empezar.
- Decisión del usuario (página 3): no se añade casilla; la misma casilla dice "Cadencia" (rpm) con cadenciómetro
  y "Pasos/min" (ppm de la vuelta) sin él. El valor ya era así; cambia el título (`CurrentLapView.cadenceLabel`).
  Los ppm salen a partir de 10 s de vuelta (sin cambios).
- `SlotStatus.fresh` (nuevo, por defecto `false`): el hueco tiene dato reciente. Hacía falta porque
  `connected` y `FreeRideStats.heartRate*` siguen "vivos" ≈ 14 s tras quitar una banda.
- Nuevo `util/laps/HeartRateCardPolicy` (+ test, 5): única con un solo hueco fresco, partida con dos; sin ninguno
  se queda como estaba; de única a partida espera 8 s (un dato suelto no la parte), el resto 2 s.
- `FreeRideScreen.MetricsGrid` (vertical y horizontal): `SingleSlotCard` con el número hasta 96 sp y hueco +
  señal + batería en una línea. Log `FreeRideScreen: Ruta: tarjeta de pulso única HR1/HR2 / partida HR1 | HR2`.
- Tests: +1 `DualSlotAllocatorTest`, +5 `HeartRateCardPolicyTest`, +1 `CurrentLapModelTest` → suite esperada 209.
- Sin tocar Room, Gradle, `NavGraph.kt` ni el ciclo de vida de los servicios.

### Prueba del usuario (1.6.128, instalada 16:17:11)
- Tests (16:16): 209/209 verdes (`HeartRateCardPolicyTest` 5/5, `CurrentLapModelTest` 10/10,
  `DualSlotAllocatorTest` 12/12).
- Una banda (xoss, ANT+): HR1 asignado 16:17:26.1 → `Ruta: tarjeta de pulso única HR1` 16:17:29.7. Captura:
  tarjeta única "88 bpm" con HR1 + ANT+. Sin cierres de la app.
- Visto: al arrancar, `Registro: 0 aparatos` (sin aparatos guardados).
- Dos bandas (xoss HR1 + Decathlon 8298 HR2, ANT+): HR2 asignado 16:19:04.4 → partida 16:19:12.4 (espera 8 s).
  Quitada la Decathlon: BLE "sin contacto" 16:19:58.7, ANT+ "sin latidos nuevos" 16:20:01.0 → única HR1
  16:20:07.1. Puesta otra vez: ANT+ "latidos de nuevo" 16:20:25.6 → partida 16:20:35.3. Un cambio por paso, sin
  parpadeo (HR2 pasó un momento por su BLE al reconectarse y la tarjeta no se movió). Capturas: partida HR1 | HR2
  con ANT+ y batería, parada y en ruta.
- Con esto queda vista también la parte ANT+ de los arreglos menores (§8, "sin latidos nuevos").
- Falta: página 3 ("Pasos/min" / "Cadencia") y horizontal (incluida la página 1 con pasos de §8).
- Propuesta nueva del usuario (16:2x): quitar el texto ANT+/BLE y marcar la señal con un recuadro de color
  alrededor de HR1/HR2/C1/C2 (verde ANT+, azul BLE). Aprobada y hecha (ver abajo).

### Señal por recuadro de color (pendiente de prueba)
- `spec.md` R6.1, `plan.md` §10, línea nueva en `tasks.md`.
- Solo `ui/components/SensorSlotBadge.kt`: el nombre del hueco va dentro de un recuadro (borde 1,5 dp + fondo
  al 15 %) verde ANT+ / azul BLE; sin señal, borde transparente con el mismo hueco (no salta al conectar). Fuera
  la etiqueta de texto "ANT+"/"BLE" (`ProtocolBadge` borrado, no tenía más usos; queda `protocolColor()`).
  Lector de pantalla: "HR1, ANT+" / "HR1, sin señal". Batería igual.
- Afecta a la vez a Tabata, Config y Ruta (barra y tarjetas) sin cambiar sus llamadas; Home lo heredará en T-06.
- Prueba 1.6.130 (16:28): Config bien (HR1/HR2 en recuadro verde, ANT+). Logcat sin errores; HR1 BLE → ANT+ a
  los 15 s, caída breve a BLE a las 16:29:02 y vuelta a ANT+ a los 3 s. Los tests no se han vuelto a pasar tras el
  cambio (últimos: 209/209 de las 16:16). Falta: tests y capturas de Tabata y Ruta.

### Traspaso
- `traspaso.md` reescrito: "Siguiente" = cerrar con evidencia los tres arreglos previos (§8, §9, §10) y luego
  T-06 cuando el usuario lo diga. Sin commit (el usuario no lo ha pedido).

---

## 2026-11-21 — Spec 007 · Arreglos menores antes de T-06 (validados en parte)

### Contexto
T-01…T-05 validadas (1.6.120). El usuario pidió empezar por los "pequeños fallos detectados" (pendientes vistos
en T-04/T-05) antes de T-06. Sin compilar ni instalar.

### Hecho
- `spec.md` R2.1 (sin valores congelados al quitar una banda); `plan.md` §8; nota en `tasks.md`. T-06 sin empezar.
- `HeartRateHub`: un dato ANT+ solo cuenta si el contador de latidos ha cambiado en los últimos 3 s
  (`AntBeatWatch`); sin latidos, la señal deja de ser fresca a los 5 s y el hueco pasa a BLE o se apaga. El
  dato BLE con "sin contacto con la piel" (bits de banderas 0x2A37) se descarta, solo si esa conexión ya dijo
  antes "con contacto" (`BleContactWatch`; una banda que nunca activa el aviso sigue como antes). Logs
  `💓 … sin latidos nuevos` / `latidos de nuevo` / `sin contacto con la piel` / `contacto de nuevo`.
- Nuevo test `HrStaleValueTest` (8 tests): suite esperada 202 (194 + 8).
- `FreeRideScreen`: rejilla de tarjetas extraída a `MetricsGrid` (vertical y horizontal); en horizontal la
  columna derecha ya no se desplaza y deja 56 dp abajo para el indicador de páginas; la franja de pasos queda
  encima.
- "Lápiz" de Config: el usuario confirma que es su S Pen (Air command), imprescindible en su móvil y ajeno a la app.
  Descartado como fallo: no se toca.
- Visto: el móvil conectado es 1440×3088 a densidad 600 (384×823 dp) y ahora sale en `adb devices` como
  `R3CTA0JHTEZ` (el inalámbrico aparece "offline").

### Prueba del usuario (1.6.123, instalada 15:23:54)
- Tests: 202/202 verdes (`HrStaleValueTest` 8/8).
- Ruta 2dd852b4 con dos bandas, las dos por BLE: ANT+ dio `ADAPTER_NOT_DETECTED` toda la prueba.
- Al quitar la Decathlon (HR2): último valor 15:24:32.9 y a las 15:24:33.8 "sin contacto con la piel, no se usa
  su valor". Desde ahí, solo HR1. El valor congelado por BLE queda resuelto.
- Visto: la banda quitada se desconecta y reconecta sola (15:24:47 / 15:24:49) y en la conexión nueva pasó **un**
  valor suelto (70 bpm), porque esa conexión aún no había visto "con contacto". Sin importancia práctica.
- Sin probar: la parte ANT+ ("sin latidos nuevos") y las capturas de Ruta (horizontal con pasos y vertical tras
  `MetricsGrid`).

### Peticiones nuevas del usuario (para el hilo siguiente; formalizar antes en spec/plan)
- Ruta página 1: si solo hay **un** pulsómetro con señal (p. ej. se quitó una de las dos bandas), una sola
  tarjeta de pulso con el número lo más grande posible. Con dos, HR1 | HR2 como ahora.
- Ruta página 3 (vueltas): falta la **cadencia de pasos** (pasos/min, no la de bici) en la vuelta en curso;
  cree que hay hueco para otra casilla. Ahí le da igual que se vean los dos pulsos.

---

## 2026-11-21 — Spec 007 · T-05: Ruta Libre separada (validada)

### Contexto
T-04 validada (1.6.117). El usuario pidió empezar T-05 sin compilar ni instalar.

### Hecho
- `plan.md` §7 "Detalle (T-05)" anotado antes del código.
- Nuevo `util/laps/DualSlotSamples.kt` (+ `DualSlotSamplesTest`, 7 tests): lecturas por hueco; "hueco principal"
  = HR1/C1 y, si no tiene lecturas, HR2/C2.
- `FreeRideService`: `FreeRideStats` con `heartRate2`, `avgHeartRate2`, `maxHeartRate2`, `isHr2Connected`,
  `cadence2`, `avgCadence2`, `isCadence2Connected`, `cadence2Active`. Antes HR1 y HR2 se mezclaban en las medias
  de ruta y vuelta; ahora van separadas. Escucha C2 (antes no). Vigilante de cadencia por hueco. Vueltas y
  Room siguen con un solo valor (el del hueco principal); la sesión guarda HR2 aparte como ya hacía Tabata.
- `FreeRideScreen`: tarjeta de pulso partida HR1 | HR2 y, si emite alguna cadencia, C1 | C2 (si no, ritmo);
  cada mitad con hueco + ANT+/BLE + batería. Vuelta en curso con "HR1" y "HR2". Barra superior con GPS y los 4
  huecos; en horizontal, punto por hueco.
- Sin tocar Room, Gradle, `NavGraph.kt`, ciclo de vida del servicio, Home ni `SensorBar` (T-06).

- Primera compilación falló: `TabataColors.HrCyan` es privado de `WorkoutScreen`; Ruta usa su propio
  `HR2_COLOR` (mismo cian).

### Validación (1.6.120, instalada 15:04)
- Tests (15:03): 194/194 verdes (187 + 7 de `DualSlotSamplesTest`), 0 fallos, 0 errores.
- Logcat: sin cierres de la app. Ruta d43a75d0 15:05:34–15:07:23; HR1 = xoss y HR2 = Decathlon 8298, ambas por
  ANT+ (~4 lecturas/s cada una); C1/C2 escaneando sin aparato. El "Conectado a" cada segundo ya salía antes.
- BD (copia por `run-as`): sesión con avg/max/min HR1 80/93/67 y HR2 80/92/72; `sensor_readings` 108 filas,
  HR1 media 80,6 (68–93) y HR2 81,4 (72–92): cada banda con lo suyo, sin mezclar.
- Capturas: vertical página 1 (barra GPS + 4 huecos con ANT+ y batería, refrescar visible; tarjeta HR1 | HR2;
  ritmo sin cadencia), página 3 (casillas HR1 y HR2) y horizontal páginas 1 y 3: bien.
- No probado: vueltas (0 cerradas en la prueba), C2 y cadencia real (sin sensores de cadencia).
- Visto: en horizontal la franja de pasos queda bajo el indicador de páginas (ahora la tarjeta HR es más alta;
  la columna se desplaza).

---

## 2026-11-21 — Spec 007 · T-04: hueco y señal en Tabata y Config

### Contexto
T-01…T-03 validadas (1.6.113). El usuario pidió empezar T-04 sin compilar ni instalar.

### Hecho
- `plan.md` §7 anotado antes del código: ampliación del contrato de `SensorManager` y detalle de T-04.
- `SensorManager`: `hrSlotStatus(slot)` / `cadenceSlotStatus(slot)` (solo lectura; no arrancan los sensores).
- Nuevo `ui/components/SensorSlotBadge.kt`: `ProtocolBadge` (sale de `WorkoutScreen`), `SensorSlotBadge`
  (hueco + ANT+/BLE + batería), `SensorSlotsViewModel` + `rememberSensorSlots()`, `SlotStatus.signalState`.
- Tabata: panel de métricas con señal y batería desde `SlotStatus` (el número sigue saliendo del servicio); chips
  superiores sin icono, punto verde/naranja/rojo por hueco; la barra deja 44 dp a la flecha de volver (tapaba HR1).
- Config: HR1 HR2 / C1 C2 en rejilla 2×2 con valor, señal y batería; 0 rpm con señal se ve "0".
- Sin tocar `WorkoutService`, Home (usa `BleScanStatus`, se queda igual hasta T-06), Room, Gradle, `NavGraph.kt`.

### Validación (1.6.117, instalada 14:25)
- Logcat: sin cierres. Una banda dual (xoss 56551 / XOSS_X2P_842983) → HR1 por ANT+; paso a BLE 9 s y vuelta a
  ANT+ sin cambiar de hueco. Tabata en marcha 14:26–14:28 sin errores de la app (el
  `JobCancellationException` de `ConfigViewModel` al pulsar INICIAR ya salía antes: su `catch` lo registra).
- Tests (14:29): 187/187 verdes, 0 fallos, 0 errores.
- Capturas del usuario (Tabata vertical en Preparado y Calentamiento, horizontal en Trabajo): HR1 ya no queda
  bajo la flecha; punto verde en HR1 y naranja en HR2/C1/C2 (sin aparato); panel con "HR1 ANT+" verde y
  batería 100 %; huecos vacíos con "--". Bien en las tres.

- Capturas 14:35–14:41 con dos bandas: Config muestra "73 HR1 ANT+ 100 %" y "77 HR2 ANT+ 100 %", C1/C2 "--";
  Tabata con HR1 y HR2 por ANT+ y batería cada uno. HR1 = XOSS X2P, HR2 = Decathlon Dual HR (ANT+ 8298).
- Pregunta del usuario: la línea azul (HR2) sale más "ruidosa" que la amarilla. Análisis de `sensor_readings`
  (sesión 489fe1df, 14:38–14:41, copia de la BD por `run-as`): salto medio por segundo HR2 1,21 / 1,86 / 0,93
  frente a HR1 0,82 / 0,69 / 0,42 en los tres tramos; tras intercambiar la posición de las bandas el ruido sigue
  en la Decathlon → es la banda, no la posición ni la app (las dos van por el mismo código ANT+, sin filtro; el
  valor lo calcula cada banda). La diferencia de nivel (+5 bpm sentado) bajó a −1 bpm al final (contacto/postura).
- Visto de paso: al quitarse una banda, ANT+ sigue mandando el último valor ~10–20 s (HR1 fijo en 83, HR2 en 70)
  antes de apagarse. No es de T-04.

### Cierre
- T-04 marcada `[x]` en `tasks.md` con el visto bueno del usuario; `traspaso.md` al día.

### Pendiente
- Commit de T-04 cuando el usuario lo pida (incluye `version.properties`).
- Siguiente: T-05 (Ruta Libre separada), solo cuando el usuario la pida.

---

## 2026-11-21 — Spec 007 · T-03: prueba real 1.6.111 y arreglo del tirón ANT+

### Contexto
El usuario probó la 1.6.111 (T-03) con las dos bandas; no tiene los cadenciómetros a mano. Logcat leído por adb.

### Visto
- Dos bandas en HR1/HR2 por ANT+ con la búsqueda compartida; HR2 pasó de BLE a ANT+ en 16 s sin cambiar de hueco.
- Tirón de ~1 s cada 30 s: el cierre de la búsqueda ANT+ (`MultiDeviceSearch.close()`) bloqueaba el hilo principal.
- Al arrancar, ADAPTER_NOT_DETECTED durante 1–2 min (también antes de T-03): por eso una banda tarda en pasar a ANT+.
- Ruta: un solo "HR" y una cadencia → T-05. Detalle de sesión sin C2 → fuera de alcance (Room).

### Hecho
- `plan.md` §4 anotado. `AntRadio`: cierre en segundo plano y en orden; `awaitClosed()` antes de abrir otra
  búsqueda y antes de `requestAccess` en `HeartRateHub` y `CadenceHub`.

### Validación (1.6.113)
- Suite 187/187 verde (22 clases). Logcat: el cierre de la búsqueda ya no bloquea el hilo principal; ANT+ a
  los 3 s del arranque; dos bandas en HR1/HR2 por ANT+; un corte ANT+→BLE de 14 s en HR2 sin cambiar de hueco.
- T-02 y T-03 marcadas `[x]`. A T-07: cadencia real y HR2 apagado en Home al quitar una banda.

### Pendiente
- Commit `7ccad3a` hecho a petición del usuario. `traspaso.md` reescrito para empezar T-04 en un hilo nuevo
  (punto de partida: `SlotStatus` aún no expuesto en `SensorManager`; Tabata con `ProtocolBadge` propio; Config
  solo HR1/C1; flecha de volver tapa HR1 en Tabata).

---

## 2026-11-21 — Spec 007 · T-03: cadencia (CadenceHub y radio ANT+ compartida)

### Contexto
T-02 con prueba real OK y el arreglo de batería ya en el commit `9c89ebb` (falta compilarlo). El usuario pidió
empezar T-03 sin compilar ni instalar.

### Hecho
- `plan.md` §4 ampliado antes del código: 0 rpm válido, emparejado con ceros, radio ANT+ compartida.
- `sensor/AntRadio.kt`: una única búsqueda ANT+ para pulso y cadencia (la búsqueda ocupa la radio; dos
  búsquedas a la vez se pisaban). `HeartRateHub` pasa a usarla.
- `sensor/CadenceHub.kt` (espejo de `HeartRateHub`) + `sensor/CscParser.kt`. C1/C2 con las mismas reglas de
  huecos que HR1/HR2; parado = 0 rpm sin desconectar.
- `PairingPolicy`: solo ceros no empareja por valores (evita juntar dos cadenciómetros distintos parados).
- `SensorManager` queda como fachada (contrato igual). Borrado `BleManagers.kt` y todo el código viejo de
  cadencia y autoconexión (≈2 400 líneas). Room sin tocar.
- Tests: `CadenceSlotAllocatorTest`, `CadencePairingTest`, `CscParserTest`.
- Primera pasada de la suite (usuario): los tests nuevos de cadencia OK; fallaba uno de T-02
  (`una ANT caida un momento no cede su hueco…`). Causa: la cuarentena de 30 s, al cumplirse sin hueco libre,
  se reiniciaba, y el aparato nunca llegaba a usar el hueco que quedaba libre. Corregido en
  `DualSlotAllocator.assignPending` (la espera se borra solo al dar hueco).

### Pendiente
- Commit de T-03 (con el arreglo de cuarentena) hecho a petición del usuario; traspaso listo para hilo nuevo.
- Usuario: repetir la suite y prueba real (Logcat `🚴` y `💓`; el agente los lee por adb): un cadenciómetro (ANT+ y/o BLE) en
  C1; parar de pedalear → 0 rpm en ~3 s sin perder el hueco; dos cadenciómetros → C1 y C2; las dos bandas de
  pulso siguen conectando con la búsqueda compartida. Con eso, cerrar T-02 y T-03.

---

## 2026-11-21 — Spec 007 · T-02: reparto de huecos (DualSlotAllocator)

### Contexto
T-01 validada por el usuario: compila, suite OK y prueba real con dos bandas puestas una tras otra → HR1 y
HR2, las dos por ANT+. Esperaba ya la hoja de dispositivos (es T-06) y HR1/HR2 en Ruta (T-05); pidió seguir
con T-02. Pregunta abierta: abrir la hoja también tocando HR1/HR2/C1/C2 en Tabata (además de Home).

### Hecho
- `sensor/DualSlotAllocator.kt` (lógica pura, común a pulso y cadencia): sale de `HeartRateHub.tick()` el
  reparto de huecos, la cuarentena, el relevo y la elección de fuente. Novedades:
  - Hueco preferido (lo asigna el usuario desde la hoja, T-06; T-02 no lo guarda solo). Reserva blanda.
  - ANT+ desplaza a BLE: con 2 aparatos ANT+ ningún hueco se queda en un aparato solo-BLE (en el mismo
    hueco). Un hueco ANT+ caído no pasa a un solo-BLE antes de 60 s (evita saltos en búsquedas ANT+).
  - `SlotStatus` por hueco (aparato, alias, señal, protocolo, valor, batería) → `HeartRateHub.slotStatus()`.
- `HeartRateHub`: usa el asignador; sin observadores para las radios a los 5 s pero recuerda los huecos
  55 s más (R9). `restart()` sigue siendo parada completa. Contrato de `SensorManager` intacto.
- `DeviceDirectory` (interfaz) en `SensorDeviceRegistry.kt`, implementada por `DeviceBook` y el registro.
- Tests: `DualSlotAllocatorTest` (dual en un hueco, caída/vuelta ANT+, dos bandas con BLE antes → ANT+,
  ANT caída no cede hueco, hueco preferido, reserva blanda, reanudar tras parar radios).

### Pendiente
- Usuario: compilar, pasar la suite de tests y prueba real (Logcat `💓`): dos bandas encendiendo antes los
  BLE; una banda dual en reposo solo en HR1; salir 20–30 s de las pantallas con sensores sin que cambien los
  huecos.

### Después (misma fecha)
- Commits: spec 006 T-11 en `spec-006-outdoor-laps`; spec 007 T-01/T-02 en la rama nueva
  `spec-007-dual-signal-devices`. Traspaso para hilo nuevo en `specs/007-dual-signal-devices/traspaso.md`.
- Decisión del usuario (R8.1–R8.3 en la spec): la hoja "Dispositivos" se abre tocando la barra de sensores en
  todas las pantallas (Home, Tabata, Ruta también en marcha, Config) con una barra común `SensorBar`; la
  flecha de refrescar sigue reconectando. Va en T-06.
- Prueba real de T-02 (usuario): ANT+↔BLE, dos bandas, poner y quitar → todo bien. Fallo: tras quitar una
  banda, Home seguía mostrando HR2 activo con batería. Causa: Home deduce HR2/C2 "conectado" de la batería
  y el asignador mantenía la batería de las señales del aparato aunque estuvieran desconectadas. Arreglo en
  `DualSlotAllocator.pickSource`: sin fuente → batería null; la batería prestada solo de señales conectadas.
  Test `aparato quitado deja el hueco sin bateria`. El estado real por hueco en Home sigue siendo T-06.

---

## 2026-11-21 — Spec 007 · T-01: aparatos de doble señal (registro genérico + emparejado seguro)

### Contexto
El usuario ve un solo pulsómetro en HR1 y HR2: la app detecta señales (ANT+ y BLE), no aparatos. Pide un
aparato = un hueco (HR1/HR2, C1/C2), ANT+ siempre preferente, ver hueco y señal en toda pantalla, que aguante
dos bandas a la vez y el mismo criterio para la cadencia. Decisiones: emparejado automático (solo si es seguro)
+ manual; gestión en hoja desde el bloque de sensores de Home; hueco fijo por aparato; Ruta con HR1/HR2 y
C1/C2 separados.

### Hecho
- Documentación: `specs/007-dual-signal-devices/{spec,plan,tasks}.md` (diagnóstico completo, 7 tareas).
- T-01 (código): `sensor/SensorDeviceRegistry.kt` sustituye a `HrDeviceRegistry.kt` — `DeviceBook` para pulso
  y cadencia, fichas de una señal, hueco preferido, manual manda, separar bloquea la pareja, formato v2 que
  migra el de la spec 003 (mismo archivo de preferencias). `sensor/PairingPolicy.kt`: guarda el vínculo solo
  si el nº ANT+ va en el nombre BLE o si durante 30 s solo se ven 1 ANT+ y 1 BLE sin pareja; si no, solo en
  la sesión (pareja mutua sin empate, también en reposo hasta 4 bpm). Al arrancar, el registro vuelca en
  Logcat (`💓🚴 Registro`) lo guardado, para detectar vínculos erróneos heredados de la spec 003.
- Conocido y anotado en T-02: con dos bandas indistinguibles los huecos aún pueden quedar en BLE (o
  BLE:A + ANT:A); no hacer todavía la prueba de dos bandas.
- `HeartRateHub`: solo cambian el tipo del registro y `detectTwins` (ahora usa `PairingPolicy`). Huecos,
  cuarentena y relevo sin tocar (T-02).
- Tests: `DeviceBookTest`, `PairingPolicyTest` (sustituyen a `HrDeviceBookTest`).

### Pendiente
- Usuario: compilar y `./gradlew test`. Siguiente tarea (T-02, huecos) solo tras su confirmación.
- Los cambios de la spec 006 (T-11, mapa) siguen sin confirmar en la rama `spec-006-outdoor-laps`; decidir si
  se confirman antes de abrir la rama `spec-007-dual-signal-devices`.

---

## 2026-11-21 — Ruta Libre: valor de Ritmo solapado en la ventana Métricas

### Contexto
Captura en exterior caminando: la celda «Ritmo» mostraba `10:15` partido en dos líneas encima de sí
mismo y la unidad `/km` en vertical letra a letra (el valor a 55 sp no cabía en media pantalla).

### Hecho
- `FreeRideScreen.LapMetricCardNoIcon`: el valor pasa a `BasicText` de una línea con
  `TextAutoSize.StepBased` (de 55 sp baja hasta 16 sp si no cabe); la unidad se mide primero y no se
  parte. Afecta a todas las celdas que usan esa tarjeta (Métricas y tablas de vuelta), solo si no caben.
- Lógica sin cambios (`CadenceDisplayPolicy`): con sensor de cadencia emitiendo → «Cadencia» en rpm;
  sin sensor o >5 s sin datos → «Ritmo» en min/km del GPS (caminar o correr). Validado por el usuario.
- Franja de pasos (Pasos · Pasos/min · Zancada) recortada en portrait: se veían los números pero no las
  etiquetas. `MetricsPage`: las 4 tarjetas van en un `BoxWithConstraints` con weight, la franja se mide
  antes y el tamaño del número de las tarjetas se calcula con el alto sobrante (28–55 sp). Etiquetas
  de la franja a 12 sp y más contraste. Landscape sin cambio de layout (ya tenía scroll).

### Estado
- Validado por el usuario en el S22+ (portrait): celda Ritmo y franja de pasos se leen enteras.

---

## 2026-11-21 — Spec 006 · T-11 (R9): mapa en vivo sin zoom

### Contexto
Prueba en exterior: en la ventana Mapa, durante la actividad, el mapa queda a escala mundial y no deja
hacer zoom; en el resumen final el track se ve bien. El usuario pide ir fallo a fallo y con tests.

### Hecho
- Causa en código: `RealTimeGoogleMap` lanzaba `animate(newLatLng)` en cada fix (~1 s), que corta el
  pellizco en curso y conserva cualquier zoom alejado; +/- y logo tapados por la franja de métricas.
- Nuevo `util/map/MapFollowPolicy.kt` (+ `MapFollowPolicyTest`): no sigue mientras el usuario toca ni
  10 s después; luego centra con su zoom; si la cámara está en un zoom que el usuario no eligió
  (p. ej. mundial) restaura el suyo o 16 si nunca tocó.
- `FreeRideScreen`: `move` instantáneo según la política, detección de gesto/+/- por
  `cameraMoveStartedReason`, `contentPadding` inferior de 172 dp. Fuera `savedZoom`/`savedPosition`.
- Log temporal `MapDiag` (zoom al aparecer el mapa, zoom que deja el usuario, restauraciones) para
  confirmar la causa del zoom mundial en Logcat; retirar al validar.
- Spec R9 (RF-9.1…9.4) y tarea T-11 añadidas. Pager, `NavGraph` y `FreeRideService` sin tocar.

### Pendiente
- Usuario: compilar, `./gradlew test`, instalar y probar zoom durante una ruta; aportar Logcat `MapDiag`.
- Detectado y no tocado (fallo aparte): la etiqueta «Mapa» del indicador de páginas se solapa con el
  tiempo de la franja de métricas.

---

## 2026-10-10 — Spec 006: Ruta Libre (cadencia/ritmo, vueltas AUTO/MANUAL, tabla de vueltas)

### Contexto
Brief del usuario R1–R8 para la pantalla de actividad exterior con GPS. Sesión de
**análisis y planificación**: no se ha tocado código Kotlin.

### Hecho
- Creada `specs/006-outdoor-laps/` con `spec.md`, `plan.md` y `tasks.md` (T-01…T-09
  secuenciales e independientes + mejoras M-01…M-05 pendientes de aprobación).
- Hallazgos clave: las vueltas **nunca se persisten** (`insertLap` sin llamadas);
  numeración AUTO/MANUAL compartida; autolap pierde km si el GPS salta y mide ≈1,03 km;
  no existe ritmo en ningún sitio; la cadencia de sensor es de bici (rpm), los pasos
  salen del podómetro; Excel infiere AUTO/MANUAL por distancia 900–1100 m.
- Vuelta fantasma: hipótesis H1 (botón Vuelta bajo el dedo al pulsar Iniciar), H2
  (Excel), H3 (sin fix GPS). Se confirma con Logcat en T-02.

### Decisiones del usuario
- Q1: dos cadencias distintas — bici (sensor, rpm, en vivo) y carrera (pasos del podómetro,
  ppm, media por tramo/vuelta). Q3: sin hueco cadencia/ritmo en Tabata con GPS por ahora.
- Q4/R8: el pager de FreeRideScreen pasa a 4 ventanas (4ª = tabla de vueltas); botonera
  inferior con la vuelta manual fija en todas.

### T-10 (código escrito; SIN PROBAR en dispositivo)
- Petición del usuario tras probar T-09: la ventana 3 seguía mostrando «Historial de Vueltas» (colapsado, sin pasos a la
  vista). Ahora es el panel de la vuelta manual en curso: Vuelta · Tiempo · Distancia · Ritmo · Velocidad · FC · Pasos ·
  Cadencia · Desnivel+, más franja «Última vuelta» (tiempo, ritmo, FC, pasos). El historial se quita (vive en la ventana 4).
- `FreeRideStats` + `currentLapSteps` y `currentLapElevationGainM` (se refrescan cada segundo; `FreeRideService`
  sin cambios de ciclo de vida). Nuevo `util/laps/CurrentLapModel.kt` (+ `CurrentLapModelTest`).
- `LapHistoryList` queda sin uso en pantalla (se conserva el fichero; candidata a limpieza si el usuario lo aprueba).
- Pendiente: usuario compila, pasa la suite y prueba la ventana 3 (portrait y landscape) con una ruta con 1–2 vueltas.
- Checklist estructurado de pruebas en exterior (GPS + ANT+) para validar T-04…T-08 y T-10:
  `specs/006-outdoor-laps/checklist-pruebas-exterior.md`. El usuario va marcando y responde por fase.

### T-09 (validada por el usuario: compiló y probó, todo bien)
- Pager de 3 → 4 ventanas en `FreeRideScreen` (portrait y landscape): 1 Métricas · 2 Mapa · 3 Vueltas · 4 Tabla.
  Sin cambios en `NavGraph` ni en el ciclo de vida de `FreeRideService`.
- Nuevo `util/laps/LapTableModel.kt` (+ `LapTableModelTest`, incluye caso de 20 vueltas): celdas de la tabla; `--` sin
  dato; cadencia ppm (pasos ÷ min de esa vuelta), con sensor de bici se muestra rpm y nunca ppm; estilo R5.
- Nuevo `ui/freeride/LapTable.kt`: columna nº fija + 9 columnas con scroll horizontal compartido (un `ScrollState`
  para cabecera y filas) y scroll vertical independiente (`LazyColumn`). El relevo al pager en los bordes es el
  comportamiento por defecto de `horizontalScroll` (nested scroll): SIN VALIDAR en dispositivo.
- `PageIndicatorBar`: 4 entradas pulsables (toque = salto a la ventana).
- Botón de vuelta flotante fuera del pager en las ventanas Mapa y Tabla (las ventanas 1 y 3 ya tenían el suyo).
- `LapHistoryList` (ventana 3): nueva columna "Pasos".
- Plan B si el gesto es ambiguo: desactivar el swipe del pager solo en la ventana 4 y navegar con la barra.
- No se ha creado androidTest (no existe el módulo; M-04): el test de 20 vueltas es JVM sobre el modelo.
- Pendiente: usuario compila, pasa la suite y prueba T-04…T-09 juntas (especialmente: arrastrar la tabla en
  vertical y horizontal, relevo al pager en el borde izquierdo, toque en la barra de pestañas, botón de vuelta en
  Mapa y Tabla, tabla con ≥20 vueltas, landscape).

### T-08 (código escrito; SIN PROBAR en dispositivo)
- Nuevo `util/laps/SessionLapSummaryModel.kt` (+ `SessionLapSummaryModelTest`): por cada vuelta MANUAL, tiempo,
  distancia, ritmo medio, vel. media, FC media/máx, pasos, cadencia (ppm) y desnivel+; `--` sin dato (nunca 0);
  la cadencia ppm se omite si la fuente es `BIKE_SENSOR`; las AUTO no llevan resumen.
- Nuevo `util/laps/LapDuration.kt`: duración de la vuelta guardada = distancia ÷ vel. media (tiempo de ruta, sin
  pausas); 0 m → reloj de pared. Ahora también lo usa el Excel (antes: fin − inicio de reloj de pared, que
  incluía pausas) — afecta a la columna "Tiempo (s)" de la hoja Laps de T-06.
- `SessionDetailScreen`: nueva tarjeta `ManualLapsSummaryCard` ("Vueltas manuales (n)") tras las métricas GPS,
  visible solo si hay manuales. Usa `laps` que ya expone `SessionDetailViewModel` desde T-06 (sin cambios en el VM).
- Pendiente: usuario compila, pasa la suite y prueba T-04…T-08 juntas (finalizar ruta con 2–3 manuales y abrir el
  detalle desde el Historial; probar también una ruta antigua sin vueltas guardadas: no debe salir la tarjeta).

### T-07 (código escrito; SIN PROBAR en dispositivo)
- Q2 resuelta con la propuesta de la spec: `cadenceSource = BIKE_SENSOR` si el sensor de pedal emitió dentro de la
  ventana de la vuelta; si no, `PEDOMETER` si hay pasos; si no, `NONE`.
- Nuevo `util/laps/LapBlockMetrics.kt` (+ `LapBlockMetricsTest`): pasos y desnivel de la ventana = diferencia de
  contadores acumulados (`BlockCounters`); `cadencePpm(steps, duración, fuente)` = pasos ÷ minutos, `null`
  con sensor de bici o sin pasos (`--`, nunca 0). Las ventanas contiguas no cuentan nada dos veces.
- `FreeRideService` (solo estado interno, sin tocar el ciclo de vida): foto de contadores al abrirse la ventana
  de cada serie (`manualWindowStartCounters`, `autoWindowStartCounters`), `stepsSeen` (0 pasos ≠ sin podómetro),
  `currentCounters()`. `LapData.steps/elevationGain/cadenceSource` ya salen rellenos y se guardan en `lap_records`.
  Sin cambios de esquema Room ni de contratos.
- Límites conocidos: (1) las AUTO se procesan al llegar el fix GPS, así que los pasos de su frontera llevan el
  retraso de ese fix (~1 s); las MANUAL son exactas (instante del botón). (2) En un hueco de GPS que cruza varios
  km de golpe, esas AUTO quedan sin pasos/desnivel. (3) Sin altitud GPS el desnivel queda sin dato, no 0.
- Aún no se muestra en pantalla: la UI llega en T-08 (resumen) y T-09 (tabla).
- Pendiente: usuario compila, ejecuta la suite y prueba T-04…T-07 juntas (en una carrera con vueltas manuales,
  comprobar en Logcat/BD que `steps` de cada manual suma ≤ pasos totales).

### T-06 (código escrito; SIN PROBAR en dispositivo)
- Excel: `ExportUtils.exportToExcel(..., laps)` recibe las vueltas persistidas; la hoja "Laps" sale de
  `LapExportRows` (tipo Auto/Manual = `type` guardado; fuera la heurística 900–1100 m y la agrupación por
  `gpsPoints.round`). Columnas: Lap #, Tipo, Nº en su serie, Tiempo, Dist. m/km, Vel. media/máx, HR media/máx,
  Cadencia media (rpm), Pasos; `--` si no hay dato. Sesiones sin vueltas guardadas → no hay hoja "Laps".
  Tiempo de la vuelta = fin − inicio en reloj de pared (incluye pausas si las hubo).
- Borrado: nuevo `SessionDeletion` (vueltas → GPS → lecturas → sesión). Lo usan `SessionDetailViewModel` y
  `HistoryViewModel` (que antes solo borraba la fila de la sesión y dejaba huérfanos GPS/lecturas/vueltas).
  `HistoryViewModel` ahora inyecta también `GpsDao` y `SensorReadingDao` (ya provistos por Hilt).
- `SessionDetailViewModel` expone `laps` (la reutilizará T-08).
- Tests nuevos: `LapExportTest`. Pendiente: usuario compila, ejecuta la suite y prueba T-04+T-05+T-06 juntas
  (finalizar una ruta con autos y manuales → exportar Excel → comprobar hoja "Laps"; borrar desde Historial y
  desde Detalle).

### T-05 (código escrito; SIN PROBAR en dispositivo)
- Nuevo `util/laps/LapRowFormatter.kt` (+ `LapRowFormatterTest`): fila = etiqueta A/M + nº de su serie ·
  distancia · tiempo · ritmo; ritmo de ESA vuelta, `--` si distancia 0 o > 60:00/km; estilo solo
  por `type` (AUTO tenue/normal `#B0BEC5`; MANUAL negrita, acento ámbar `#FFB74D`, bandera y fondo tintado);
  contraste ≥ 4.5:1 verificado en test sobre el fondo de la pantalla (#36454F / #2D2D2D).
- Nuevo `ui/freeride/LapHistoryList.kt`: composable único para portrait y landscape (antes había
  dos bloques duplicados en `LapsPage` y `LandscapeLapsPage`). Columnas: # · Dist. · Tiempo · Ritmo · FC
  (la columna Vel. se sustituye por Ritmo; la tabla completa de 10 columnas llega en T-09).
  Fuera el sombreado por posición (par/impar) y los colores verde/naranja anteriores.
- Pendiente: usuario compila, ejecuta la suite y revisa en móvil (vertical y horizontal) con vueltas
  AUTO y MANUAL mezcladas: legibilidad al sol, ritmo correcto en cada fila, `--` en una manual con 0 m.

### T-04 (código escrito; SIN PROBAR en dispositivo — el usuario aclaró que no hizo las comprobaciones de T-04/T-05/T-06)
- Nuevo `util/laps/LapBook.kt` (`LapType`, `CadenceSource`, `LapRecord`, `LapBook`): dos series
  independientes AUTO/MANUAL con numeración propia (`typeNumber`) y orden global (`order`); ventana
  de cada manual contigua con la anterior; manual < 5 s rechazada sin mover la ventana;
  `lastManual` = última manual con distancia/tiempo reales. Tests `LapBookTest`.
- Room **9 → 10**: `lap_records` + `type`, `typeNumber`, `steps`, `cadenceSource`
  (`MIGRATION_9_10` registrada en `ALL_MIGRATIONS`); `LapEntity` ampliada. Test `LapMigrationTest`
  (el esquema 10 y la migración añaden exactamente las mismas columnas; mapeo `LapData.toEntity`).
- `FreeRideService`: `addLap()`/`addAutoLap()` usan `LapBook` (fuera las variables sueltas
  `lastLap*`/`lastAutoLap*`); `LapData` gana `type`, `typeNumber`, `steps`, `elevationGain`,
  `cadenceSource`, `startDistanceMeters`, `startTimestamp` (`isAutoLap` es ahora derivada);
  `FreeRideStats.manualLapsCount`; **las vueltas se guardan por fin** en `stopRide()` →
  `saveLaps()` (borra y reinserta; si falla, la sesión ya está guardada).
- `FreeRideScreen`: la tarjeta "Vuelta" cuenta solo manuales (`manualLapsCount + 1`) y la columna "#" de
  la lista muestra el número dentro de su serie (la lista sigue mezclando AUTO ⚡ y MANUAL hasta T-05).
- Pendiente: usuario compila (la migración 9→10 se aplica al instalar sobre la app existente),
  ejecuta la suite, y en una ruta comprueba: manuales y autos no se alteran entre sí; "Vuelta" de la
  tarjeta sube solo con el botón; tras finalizar, las vueltas quedan guardadas (Logcat `💾 N vueltas guardadas`).

### T-03 (validada por el usuario: compiló y probó, todo bien)
- Nuevo `util/laps/AutoLapTracker.kt` (cruces de 1000 m con tiempo interpolado, multi-km, sin parcial),
  `KmAlertGate.kt` (+ `KmAlert.bannerText()`), tests `AutoLapTrackerTest` y `KmAlertGateTest`.
- `FreeRideService`: `processGpsReading` usa el tracker (fuera el disparo con `_stats` desfasado y la
  protección de 2 s de reloj de pared); `addAutoLap(crossing, resetCounters)` mide exactamente 1000 m;
  `SharedFlow<KmAlert> kmAlerts`; sonido (`Sound.BEEP`) + vibración desde el servicio; inyecta `SoundPlayer`.
- `FreeRideScreen`: banner "KM n · m:ss/km" 4 s en la parte superior.
- Pendiente: usuario compila, ejecuta la suite de tests, y en la calle (o simulando GPS) comprueba: autolap en
  cada km de 1000 m exactos, un único aviso por km (sonido + vibración + banner, también con pantalla
  apagada), y que al finalizar no aparece una vuelta parcial.

### T-02 (validada por el usuario: compiló y probó, todo bien)
- Nuevo `util/laps/LapGuard.kt` (solo tiempo: ≥5 s; la distancia no cuenta, para series explosivas en el sitio) + `LapGuardTest`.
- **Causa raíz confirmada con Logcat (`LapDiag`): H1.** `addLap` llegó 159 ms después de
  `startRide` con tiempo 0 y distancia 0 (GPS conectado): el botón Vuelta aparece bajo el
  dedo donde estaba Iniciar. No era H2 ni H3.
- Corrección en origen: `FreeRideScreen` ignora toques de Vuelta durante 1 s tras Iniciar
  (`LAP_TAP_GUARD_AFTER_START_MS`). Red de seguridad en `FreeRideService.addLap()`: devuelve
  Boolean y rechaza parado/pausa/corta/vacía (Toast "Vuelta ignorada"); `addAutoLap()` no
  actúa en pausa. Logging `LapDiag` retirado.
- Pendiente: usuario compila, ejecuta la suite y comprueba que al iniciar no aparece vuelta
  fantasma (el Logcat ya no existe; se ve en la lista de vueltas).

### T-01 (validada por el usuario: compiló y los tests pasan)
- Nuevo: `util/laps/PaceCalculator.kt`, `util/laps/CadenceDisplayPolicy.kt`; tests
  `PaceCalculatorTest`, `CadenceDisplayPolicyTest`.
- `FreeRideService`: `FreeRideStats.cadenceActive` + `paceSecPerKm`; marca de tiempo del
  último dato de cadencia, vigilante de 1 s, ritmo en vivo en el tick del cronómetro.
- `FreeRideScreen`: composable `CadencePaceCard` en las 3 vistas (portrait, landscape
  métricas, landscape vueltas).
- Pendiente: el usuario compila, ejecuta `./gradlew test` y prueba (sin sensor → ritmo;
  con sensor → rpm; apagar/encender sensor → conmuta en ≤ ~6 s).

---

## 2026-09-27 — Pulsómetro dual: fusión ANT+/BLE y etiqueta de protocolo

### Contexto
En la Tab 9, una única banda dual aparecía como HR1 **y** HR2 (dos líneas casi
calcadas). Trabajo sobre `sensor/HeartRateHub.kt` (Fase C, gestor único de HR).

### Cambios hechos
1. **Fusión de gemelos por media móvil** (`detectTwins`): antes comparaba bpm
   instantáneo con igualdad estricta (18/20), que falla al variar la HR por el
   desfase ANT+/BLE. Ahora usa ventana de ~10 muestras y fusiona si la diferencia
   media de bpm ≤ 5 (`TWIN_WINDOW`, `TWIN_MIN_SAMPLES`, `TWIN_MAX_AVG_DIFF`).
   → Resultado verificado: una banda dual ocupa un solo hueco (HR1). ✅
2. **Etiqueta de protocolo persistente** (`tick`): el hub emitía `Connected` una
   sola vez y luego muchos `Value`; con `replay=1` un suscriptor tardío solo veía
   el `Value` y perdía el protocolo → badge vacío. Ahora reemite `Connected` cada
   tick. → Verificado: la etiqueta ya aparece bajo HR1. ✅

### Abierto / en investigación
- **ANT+ no engancha en la última sesión**: la etiqueta de HR1 sale **BLE** y al
  apagar el Bluetooth se queda sin señal (no hay ANT+ de respaldo). El hub ya
  prioriza ANT+ si está fresco; el problema es que el ANT+ no conecta ahora
  (antes sí: en la 1ª captura HR2 tenía ANT+). **Pendiente: capturar logcat**
  (filtro `ANT|BLE|Hueco|aparato`) iniciando Tabata → 20 s → apagar BT, para ver
  si el ANT+ no detecta, no conecta o llega stale.

### Notas de flujo de trabajo (acordadas hoy)
- Compilar/reinstalar lo hace el usuario (Ctrl+Shift+O). Claude solo avisa
  "compila y reinstala".
- `adb` no está en el PATH: usar
  `C:\Users\APO\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

## 2026-09-27 — Spec 003: registro persistente de pulsómetros (ANT+ ↔ BLE)
- Problema: una banda dual salía en HR1 (ANT+) y HR2 (BLE). Causa: se asignaba hueco
  antes de decidir si eran gemelos, la comparación (media |Δbpm| ≤ 5) fallaba por el
  desfase ANT/BLE y el vínculo se borraba en cada `stop()`.
- Solución (`specs/003-hr-device-registry/`):
  - `HrDeviceRegistry` (SharedPreferences, sin Room): guarda para siempre nº ANT+ ↔ MAC BLE.
  - `HeartRateHub`: el hueco pertenece al aparato físico; cuarentena de 30 s para un
    desconocido que podría ser la otra cara de uno asignado; comparación con desfase
    (±5 s, exige variación de HR para guardar); relevo sin solapamiento (quitas pincho
    ANT+/apagas BLE y la otra señal hereda el hueco).
  - Tests: `HrDeviceBookTest`.
- Pendiente (T-04): compilar, `./gradlew test` y probar la secuencia BLE → +ANT+ →
  −ANT+ → −BLE/+ANT+ mirando Logcat `💓` (debe salir "vinculado ... guardado").
- Prueba en 1.6.25 (logcat): el registro no llegaba a actuar porque
  (a) ANT+ 56551 se encontraba pero requestAccess daba SEARCH_TIMEOUT y luego
  CHANNEL_NOT_AVAILABLE / ALL_CHANNELS_IN_USE: la MultiDeviceSearch continua del hub
  acaparaba la radio del pincho (también bloqueaba la cadencia ANT+);
  (b) BLE tras desconectar (status 22) el reintento connectGatt quedaba colgado y el
  escaneo ignoraba el aparato para siempre.
  Arreglo: búsqueda ANT+ por ventanas (15 s busca / 15 s pausa), se cierra antes de
  conectar, recuperación de aparatos perdidos volviendo a buscar; vigilante BLE de 20 s
  que cierra la conexión colgada para que el escaneo la reenganche.

## Sesión 2026-09-27 — Spec 004: Home responsive
- `HomeScreen.kt` rehecho con `BoxWithConstraints`: 4 layouts (móvil/tablet × vertical/horizontal).
  Arreglado: la tablet en vertical usaba el layout horizontal (`screenWidthDp > 600`).
- Vertical: las tarjetas reparten el alto (sin huecos); scroll si alto < 620dp.
  Horizontal: panel lateral + tarjetas lado a lado a todo el alto.
- Pendiente (T-02): compilar e instalar (usuario) y validar en S22+ y Tab S9.
- Revisión 2: actividades en la misma fila; fila 2 = Última actividad (datos reales) +
  Esta semana (desde lunes: sesiones, tiempo, km, FC media); fila 3 = Historial | Perfil | Cerrar.
  Nuevo `HomeViewModel` (solo lectura de `SessionDao.getAllSessions()`) + `HomeSummaryTest`.

## Sesión 2026-09-27 — Spec 005: batería de sensores
- `SensorBattery` (+test), batería BLE HR en `HeartRateHub` (validado XOSS 75 %, Decathlon 100 %),
  arranque automático del escaneo BLE al encender Bluetooth, ANT+ HR usa la batería del gemelo BLE.
- Cadencia BLE/ANT+ en `SensorManager` (T-04, pendiente de validar en el otro estudio).
- UI (T-05): `BatteryBadge` en Home (2 filas HR1+C1 / HR2+C2), Tabata y Ruta Libre, aviso único de batería baja.
  Tabata vertical validado; horizontal compactado para dar alto a la gráfica (pendiente de validar).
- Todo integrado en `main`; ramas eliminadas.
