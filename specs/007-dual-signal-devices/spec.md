# Spec 007 — Un aparato = un hueco (pulsómetros y cadenciómetros de doble señal, ANT+ preferente)

## Problema
Cada pulsómetro y cada cadenciómetro emiten por **dos señales**: ANT+ y BLE. La app detecta **señales**, no
**aparatos**: un solo aparato puede ocupar HR1 y HR2 (o C1 y C2). La spec 003 lo intentó solo para pulso y
quedan fallos:
- El emparejado automático ANT+ ↔ BLE se guarda para siempre aunque haya varios aparatos a la vista: con
  dos bandas puestas puede unir la ANT+ de una con la BLE de la otra.
- En reposo (pulso plano, diferencia de 2 a 4 bpm) no se agrupan ni en la sesión → el BLE acaba en HR2.
- El hueco es "el primero que llega": no se recuerda en qué hueco va cada aparato.
- Al cambiar de pantalla se reinician los huecos (parada 5 s después de quedarse sin observadores).
- La cadencia no tiene agrupación ni preferencia ANT+; el escaneo BLE de C2 nunca arranca.
- Ruta Libre mezcla HR1 y HR2 en un solo valor y no escucha C2; Home deduce HR2/C2 de la batería; Config
  solo muestra HR1/C1; ninguna de esas pantallas dice si el dato llega por ANT+ o por BLE.

## Requisitos
- **R1 — Un aparato, un hueco.** Un aparato físico ocupa un único hueco (HR1/HR2, C1/C2), llegue por la
  señal que llegue. Un segundo aparato real distinto va al otro hueco.
- **R2 — ANT+ preferente.** Si el aparato emite por ANT+ se usa ANT+; BLE solo de respaldo. Al caer ANT+
  pasa a BLE **sin cambiar de hueco**; al volver ANT+, recupera ANT+.
  - **R2.1 — Sin valores congelados** *(petición del usuario 2026-11-21)*: al quitarse una banda, su hueco deja
    de dar valor en ≈ 5–8 s (antes seguía con el último pulso 10–20 s). Un dato ANT+ sin latidos nuevos o un
    dato BLE que indica "sin contacto con la piel" no cuenta como dato.
- **R3 — Emparejado seguro.**
  - Automático **persistente** solo cuando es seguro: el nº ANT+ aparece en el nombre BLE, o en ese momento
    hay exactamente una señal ANT+ y una BLE sin emparejar del mismo tipo y sus valores coinciden (con desfase
    y con variación suficiente).
  - Con varios aparatos del mismo tipo a la vista, o con valores planos: agrupación **solo en la sesión**
    (no se guarda), con umbral de 4 bpm también en reposo.
  - El emparejado **manual manda**: el automático nunca rompe un vínculo manual ni vuelve a unir una pareja
    que el usuario separó.
- **R4 — Hueco fijo por aparato.** El aparato guardado recuerda su hueco (HR1/HR2, C1/C2) y lo ocupa
  aunque llegue segundo.
- **R5 — Misma lógica para cadencia.** Cadenciómetros con la misma agrupación, preferencia ANT+ y hueco fijo
  (incluido 0 rpm parado). Radio ANT+ compartida con el pulso sin pisarse; un único escaneo BLE continuo.
- **R6 — Hueco y señal visibles.** En toda pantalla con pulso o cadencia (Tabata, Ruta Libre, Home, Config) se
  ve qué hueco es (HR1/HR2, C1/C2) y por qué señal llega (ANT+ verde / BLE azul), con su batería.
  - **R6.1 — Señal por recuadro de color** *(petición del usuario 2026-11-21)*: la señal ya no se escribe
    ("ANT+"/"BLE"); el nombre del hueco va dentro de un recuadro verde si llega por ANT+ y azul si llega por BLE.
    Sin señal, sin recuadro. Ahorra ancho en barras y tarjetas. Igual en todas las pantallas.
- **R7 — Ruta Libre separada.** HR1/HR2 y C1/C2 por separado, como en Tabata; medias de HR1 y HR2 separadas.
  C2 en Ruta solo en vivo (guardarlo exige cambiar Room → otra spec).
  - **R7.1 — Un pulsómetro, tarjeta grande** *(petición del usuario 2026-11-21)*: en la página 1 de Ruta
    (vertical y horizontal), si solo **un** hueco de pulso tiene señal (dato reciente, no basta "conectado"),
    una sola tarjeta con ese pulso lo más grande posible, con su hueco (HR1 o HR2), señal y batería (R6). Con
    los dos con señal, la tarjeta partida HR1 | HR2 como hasta ahora. Sin ninguno con señal, se queda como
    estaba. Sin parpadeo: un dato suelto de una banda quitada no vuelve a partir la tarjeta.
  - **R7.2 — Cadencia o pasos/min en la vuelta en curso** *(decisión del usuario 2026-11-21)*: en la página 3,
    la misma casilla muestra la cadencia de bici (rpm, título "Cadencia") si un cadenciómetro emite y, si no,
    los pasos/min de la vuelta (ppm, título "Pasos/min"). Nunca las dos a la vez; siguen 9 casillas.
- **R8 — Gestión manual.** Hoja "Dispositivos": ver señales visibles y aparatos guardados; unir, separar,
  renombrar, asignar hueco, olvidar; modo guiado "enciende solo un aparato".
  - **R8.1 — Misma entrada en todas las pantallas** *(decisión del usuario 2026-11-21 tras probar T-01)*: la
    hoja se abre al tocar la barra/bloque de sensores (GPS, HR1/HR2, C1/C2, batería) en **toda** pantalla que
    la tenga: Home, Tabata, Ruta Libre (también con la actividad en marcha) y Config si la muestra. Es la
    misma hoja en todas, con el mismo comportamiento.
  - **R8.2** — La flecha de refrescar conserva su función (reconectar sensores); el resto de la barra abre la
    hoja. La hoja incluye también "Reconectar".
  - **R8.3** — Abrir o cerrar la hoja no para ni reinicia la sesión de Tabata o Ruta. Los cambios (hueco,
    unir, separar) se aplican en vivo.
- **R9 — Sin reinicio al cambiar de pantalla.** Los huecos no se reinician al navegar entre pantallas.

## Restricciones
- Sin cambios en Room, Gradle ni `NavGraph.kt`. Sin cambiar el ciclo de vida de `WorkoutService` /
  `FreeRideService`. El contrato de `SensorManager` se conserva (`observeHeartRate*`, `observeCadence*`,
  baterías) y solo se amplía (R7.1 añade `SlotStatus.fresh`: el hueco tiene dato reciente).
- Registro de aparatos en SharedPreferences; migra los vínculos de la spec 003 sin perderlos.

## Fuera de alcance
- Guardar C2 de Ruta en el historial (Room).
- Más de dos huecos por tipo.

## Riesgo conocido
Si en el momento del primer emparejado solo se ven la ANT+ de un aparato y la BLE de otro (los otros dos
apagados) y los valores coinciden, se guardaría un vínculo erróneo. Se mitiga exigiendo que la situación
"1 ANT+ + 1 BLE sin emparejar" se mantenga toda la ventana de comparación; si ocurre, el usuario lo separa
desde la hoja y la pareja queda bloqueada para el automático.
