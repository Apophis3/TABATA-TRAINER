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
    - **Ampliación (paseo del usuario 2026-11-22, 1.6.140):** vale también para lo que **se graba**, no solo para
      lo que se ve. Un hueco cuya señal sigue "conectada" pero sin dato reciente deja de dar valor (y de grabarse)
      a los ≈ 8 s; vuelve solo con el primer dato nuevo. En el paseo, HR2 grabó **142 fijo durante 36 min**
      (09:37–10:13) con el pulsómetro de un desconocido ya lejos. Un aparato ANT+ que deja de oírse (el aparato
      está en "buscando" más de 30 s) se da por perdido: se cierra su canal y se vuelve a buscar, como cuando el
      sistema avisa de que está "muerto". Igual en cadencia (0 rpm con datos sigue siendo un dato válido).
- **R3 — Emparejado seguro.**
  - Automático **persistente** solo cuando es seguro: el nº ANT+ aparece en el nombre BLE, o en ese momento
    hay exactamente una señal ANT+ y una BLE sin emparejar del mismo tipo y sus valores coinciden (con desfase
    y con variación suficiente).
  - Con varios aparatos del mismo tipo a la vista, o con valores planos: agrupación **solo en la sesión**
    (no se guarda), con umbral de 4 bpm también en reposo.
  - El emparejado **manual manda**: el automático nunca rompe un vínculo manual ni vuelve a unir una pareja
    que el usuario separó.
  - **R3.1 — Una agrupación de sesión no estorba a la siguiente** *(petición del usuario 2026-11-21, visto en la
    prueba de T-06)*: al comparar valores, una señal ya agrupada en la sesión solo se compara con su pareja; no
    cuenta como rival de las demás. Así, con dos bandas en el mismo pecho, la 2.ª banda se agrupa en cuanto tiene
    muestras en lugar de esperar 30 s por "empate". Lo de guardar para siempre no cambia: con dos bandas a la vista
    sigue siendo solo de sesión. Coste aceptado: si la 1.ª agrupación fuese errónea (ANT+ de una banda con BLE de
    la otra), la pareja que queda se agrupa cruzada en vez de esperar; se corrige desde la hoja ("No son el mismo
    aparato"). No ayuda si las dos ANT+ llegan a la vez sin nada agrupado: ahí sigue el empate (unir en la hoja).
- **R4 — Hueco fijo por aparato.** El aparato guardado recuerda su hueco (HR1/HR2, C1/C2) y lo ocupa
  aunque llegue segundo.
- **R5 — Misma lógica para cadencia.** Cadenciómetros con la misma agrupación, preferencia ANT+ y hueco fijo
  (incluido 0 rpm parado). Radio ANT+ compartida con el pulso sin pisarse; un único escaneo BLE continuo.
- **R6 — Hueco y señal visibles.** En toda pantalla con pulso o cadencia (Tabata, Ruta Libre, Home, Config) se
  ve qué hueco es (HR1/HR2, C1/C2) y por qué señal llega (ANT+ verde / BLE azul), con su batería.
  - **R6.1 — Señal por recuadro de color** *(petición del usuario 2026-11-21)*: la señal ya no se escribe
    ("ANT+"/"BLE"); el nombre del hueco va dentro de un recuadro verde si llega por ANT+ y azul si llega por BLE.
    Sin señal, sin recuadro. Ahorra ancho en barras y tarjetas. Igual en todas las pantallas, también en la
    barra compacta de Ruta en horizontal (páginas 1 y 3; ahí sin batería, como antes, por falta de ancho).
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
    - **Aclaración (paseo 2026-11-22):** "Reconectar" reinicia de verdad las radios (ANT+ y BLE) en **toda**
      pantalla: Home, Tabata y Ruta (también en marcha). Antes, en Tabata y Ruta solo se volvían a escuchar los
      huecos y las radios seguían igual (en el paseo, la banda perdida no volvió). Reconectar conserva el reparto
      de huecos (R9) y las señales ignoradas (R10); no para ni reinicia la sesión (R8.3).
  - **R8.3** — Abrir o cerrar la hoja no para ni reinicia la sesión de Tabata o Ruta. Los cambios (hueco,
    unir, separar) se aplican en vivo.
- **R9 — Sin reinicio al cambiar de pantalla.** Los huecos no se reinician al navegar entre pantallas.
- **R10 — Ignorar una señal ajena** *(petición del usuario 2026-11-22, paseo con la 1.6.140)*: el pulsómetro (o
  cadenciómetro) de otra persona que pasa cerca podía ocupar un hueco libre (desde R11 tiene que aprobarse). En la hoja, **toda** tarjeta con señal
  (guardada o no) tiene "No es mío": tras confirmar, sus señales dejan de usarse **en esta sesión de la app** (hasta
  cerrarla): sueltan su hueco al momento, no se graban, no se vuelven a conectar (ANT+ ni BLE) y no ocupan canal.
  Sobreviven a Reconectar y al cambio de pantalla. La hoja muestra la lista "Ignorados" con "Volver a usar" para
  deshacerlo. No se guarda para siempre: un error sobre la banda propia se arregla cerrando la app.
- **R11 — Un aparato nuevo pregunta antes de usarse** *(petición del usuario 2026-11-22)*: un aparato (pulso o
  cadencia) sin ficha en el registro y sin aprobar en esta sesión **no ocupa ningún hueco por su cuenta**, tampoco el
  primero ni con los huecos vacíos.
  - Tras oírse con dato **15 s seguidos** (después de la espera de 30 s por si es la otra señal de un aparato ya
    asignado, R3), sale un aviso arriba, en cualquier pantalla, sin bloquear: "Pulsómetro nuevo (Cadenciómetro nuevo):
    nombre (señal) · valor. ¿Es tuyo?" con **"Sí, usar"** y **"No es mío"**.
  - "Sí, usar": se guarda en el registro como aparato de una señal (no vuelve a preguntar nunca) y entra en el primer
    hueco libre con las reglas de siempre.
  - "No es mío": se ignora como en R10 (suelta el canal; "Volver a usar" en la hoja).
  - Sin contestar no se usa. Si deja de dar dato, la cuenta vuelve a cero y el aviso se quita; un ANT+ que no se oye
    30 s suelta su canal (R2.1). Alguien que se cruza un momento no llega a provocar el aviso.
  - Entran solos: los aparatos guardados (emparejados, renombrados, con hueco o aprobados) y la otra señal de un
    aparato ya guardado o aprobado cuando se agrupa con él.
  - Un aparato sin aprobar no cuenta para la regla "ANT+ desplaza a BLE".
  - Riesgo conocido: el relevo sin solapamiento (R2) sigue dejando que una señal desconocida del otro protocolo, con
    valor parecido, herede el hueco de un aparato que cae. Se deshace con "No es mío".
- **R12 — BLE vuelve solo tras apagar y encender el Bluetooth** *(petición del usuario 2026-11-25, prueba de campo
  con la 1.6.157)*: con el Bluetooth apagado a media sesión y encendido después, la banda BLE no volvió a conectar
  aunque se oían sus anuncios. Desde ahora:
  - Al apagarse el Bluetooth se paran los escaneos y se cierran las conexiones BLE; sus señales pasan a "sin
    conexión" (no ocupan hueco). Al encenderse, el escaneo arranca solo y la banda vuelve en segundos.
  - Nunca hay más de un escaneo por tipo (pulso, cadencia).
  - Una conexión BLE que lleva 20 s (pulso) o 30 s (cadencia) sin ninguna notificación se cierra y se vuelve a
    enganchar al oírla. "Sin contacto con la piel" (R2.1) sigue llegando como notificación y no cuenta como silencio.
- **R13 — ANT+ no machaca el pincho y avisa si no lo detecta** *(petición del usuario 2026-11-25, misma prueba)*:
  con la pantalla apagada, la búsqueda ANT+ cada 15 s (aunque HR1 ya estuviera conectado) dejó el pincho USB "no
  detectado" hasta el final y la app reintentó sin fin. Desde ahora:
  - Con algún canal ANT+ recibiendo (pulso o cadencia), la búsqueda de aparatos nuevos se repite cada 120 s, no cada
    15 s. Sin canales, cada 15 s como antes. "Reconectar" y la hoja buscan al momento.
  - Con el pincho "no detectado", los reintentos se espacian: 30 s, 60 s, 120 s y 300 s como máximo. Vuelve a lo
    normal al primer éxito, al pulsar "Reconectar" o al enchufar el pincho.
  - Mientras dure, la barra de sensores dice "Pincho ANT+ no detectado".
  - Ajuste del usuario (fuera de la app): las tres apps de ANT en "Aplicaciones que nunca entran en suspensión" y
    "No restringido".

## Restricciones
- Sin cambios en Room, Gradle ni `NavGraph.kt`. Sin cambiar el ciclo de vida de `WorkoutService` /
  `FreeRideService`. El contrato de `SensorManager` se conserva (`observeHeartRate*`, `observeCadence*`,
  baterías) y solo se amplía (R7.1 añade `SlotStatus.fresh`: el hueco tiene dato reciente; T-06 añade
  `hrSignals()` / `cadenceSignals()`, señales visibles con su aparato y hueco, y `unlinkSession()` para la hoja;
  R10 añade `ignoreSignals()` / `unignoreSignal()` / `hrIgnored()` / `cadenceIgnored()`; R11 añade `hrToAsk()` /
  `cadenceToAsk()` / `approveSignal()`; R13 añade `antAdapterState()`, solo lectura). Reconectar (R8.2) usa el `resetFlows()` que ya existía. El aviso de R11 se
  dibuja en `MainActivity` (encima de `NavGraph()`, sin tocar `NavGraph.kt`).
- Registro de aparatos en SharedPreferences; migra los vínculos de la spec 003 sin perderlos.

## Fuera de alcance
- Guardar C2 de Ruta en el historial (Room).
- Más de dos huecos por tipo.

## Riesgo conocido
Si en el momento del primer emparejado solo se ven la ANT+ de un aparato y la BLE de otro (los otros dos
apagados) y los valores coinciden, se guardaría un vínculo erróneo. Se mitiga exigiendo que la situación
"1 ANT+ + 1 BLE sin emparejar" se mantenga toda la ventana de comparación; si ocurre, el usuario lo separa
desde la hoja y la pareja queda bloqueada para el automático.
