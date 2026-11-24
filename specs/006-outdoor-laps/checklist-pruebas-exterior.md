# Checklist de pruebas en exterior — Spec 006 (+ spec 005 sensores)

Cubre T-04…T-08 y T-10 (las que faltan por validar). T-01, T-02, T-03 y T-09 ya están validadas; aquí se vuelven a pisar de pasada.
Equipo: S22 Ultra (principal). Opcional: Tab S9 para comprobar el apaisado en pantalla grande.

**Cómo usarlo:** ve marcando `[x]` según avanzas. Si algo falla, anota en «Resultado» qué viste y no sigas con esa prueba hasta contármelo. Al terminar cada fase, respóndeme con el formato de la sección final y yo cierro las tareas en `tasks.md` y en la bitácora.

---

## Fase 0 · Preparación (en casa, 10 min)

- [ ] **P-01** Compilar e instalar **encima de la versión anterior** (sin desinstalar): comprueba la migración Room 9→10.
  - Esperado: la app abre; el Historial conserva las sesiones antiguas y se pueden abrir.
- [ ] **P-02** Ejecutar la suite completa de tests (`./gradlew test`).
  - Evidencia: pega la última línea del resultado (`BUILD SUCCESSFUL` o los tests que fallan).
- [ ] **P-03** Permisos concedidos: ubicación (también en segundo plano), Bluetooth/dispositivos cercanos, actividad física (podómetro) y notificaciones.
- [ ] **P-04** Ajustes del móvil: ahorro de batería desactivado para la app y GPS en «Alta precisión».
- [ ] **P-05** Batería cargada (móvil, HR, cadencia). Anota % aprox. de cada sensor: HR ___ · CAD ___.
- [ ] **P-06** Servicios ANT+ del móvil instalados y activos (si la app ve los sensores ANT+ en la Home, está bien).
- [ ] **P-07** Para recoger logs: móvil conectado por USB y `adb devices` lo lista (solo lectura; no instalo yo).

---

## Fase 1 · Sensores ANT+ en casa (5–10 min)

Antes de salir, para no descubrir un fallo de emparejamiento en mitad de la ruta.

- [ ] **S-01** En la Home aparecen los sensores ANT+ (HR y cadencia) y se conectan.
- [ ] **S-02** Icono de batería junto a HR y CAD en la barra de sensores de la Home (spec 005, RF-5).
  - Esperado: cadencia ANT+ muestra icono de estado (verde/amarillo/naranja/rojo). Un pulsómetro **solo por ANT+** queda **sin icono**: es una limitación conocida del SDK, no un fallo. El dato de batería puede tardar hasta ~1 min.
- [ ] **S-03** Abrir Ruta Libre sin iniciar: los puntos de sensor (GPS / corazón / bici) cambian de color al conectar.
- [ ] **S-04** Girar la biela / la rueda a mano con el sensor de cadencia puesto: aparecen rpm en pantalla.
- [ ] **S-05** Quitar la banda HR / dejar de girar: el dato deja de actualizarse sin colgar la app.
  - Anota: ¿el punto sigue «conectado» aunque no emita? (es la mejora M-01 conocida, no es un bug nuevo).

---

## Fase 2 · Ruta A — Solo GPS, sin sensores (caminata/carrera de ≥ 2,2 km)

Objetivo: autolap, ritmo, vueltas manuales y las 4 ventanas. Sin HR ni cadencia para ver el comportamiento «sin dato».
Lleva: móvil con la app; reloj/cronómetro aparte para contrastar tiempos.

### 2.1 Arranque
- [ ] **A-01** Esperar fix GPS (punto GPS en verde) y pulsar **Iniciar**.
  - Esperado: **no** aparece ninguna vuelta fantasma. Ventana 4 (Tabla): vacía («No hay vueltas registradas»).
- [ ] **A-02** Pulsar Iniciar con doble toque rápido.
  - Esperado: sigue sin crearse vuelta (el toque de Vuelta se ignora durante 1 s tras Iniciar).

### 2.2 Hueco cadencia ↔ ritmo (T-01)
- [ ] **A-03** Caminando/corriendo con GPS y **sin sensor de cadencia**: el hueco dice **«Ritmo»** en min/km (formato m:ss).
- [ ] **A-04** Parado en un semáforo / quieto > 10 s: el ritmo pasa a `--`, nunca `0:00`.
- [ ] **A-05** Anota 2–3 ritmos de pantalla y compáralos con tu sensación/reloj: ___ / ___ / ___ (aceptable ±10–15 s/km).

### 2.3 Autolap y aviso por km (T-03)
- [ ] **A-06** Al cruzar el **km 1**: suena el aviso, vibra y sale el banner «KM 1 · m:ss/km».
- [ ] **A-07** Lo mismo en el km 2, **una sola vez por km** (no se repite).
- [ ] **A-08** En la Tabla: aparece la vuelta **A1** (y **A2**) con distancia **1.00 km** exacta (no 1.03).
- [ ] **A-09** Aviso con la **pantalla bloqueada** (apágala entre km 1 y km 2): sigue sonando/vibrando.

### 2.4 Vueltas manuales (T-04)
- [ ] **A-10** Entre el km 0 y el 1, pulsar **NUEVA VUELTA** (botón de la ventana 3) a los ~400 m.
  - Esperado: aparece **M1** con su distancia/tiempo reales (~0.40 km) y, a la vez, el autolap del km 1 **sigue llegando en su sitio**.
- [ ] **A-11** Ventana 3: el título pasa a «Vuelta manual M2» (cuenta solo manuales, no autolaps).
- [ ] **A-12** Pulsar vuelta desde las ventanas **Mapa (2)** y **Tabla (4)** con el botón flotante dorado (abajo-izquierda): crea M2 y M3.
- [ ] **A-13** Pulsar vuelta otra vez a los **2–3 s** de la anterior: sale el aviso «Vuelta ignorada: dura menos de 5 s» y **no** se crea.

### 2.5 Ventana 3 — panel de la vuelta en curso (T-10)
- [ ] **A-14** Se ven: Tiempo, Distancia, Ritmo vuelta, Velocidad, HR, Pasos, Cadencia, Desnivel +.
- [ ] **A-15** Sin sensor: HR = `--`, Cadencia = `--` o ppm (de pasos del móvil), nunca `0`.
- [ ] **A-16** **Pasos** sube mientras caminas y se **reinicia a `--`/pocos pasos** al pulsar NUEVA VUELTA.
- [ ] **A-17** **Desnivel +** muestra un valor razonable (en llano ≈ 0–10 m).
- [ ] **A-18** La franja «Última vuelta · M*n*» muestra tiempo, ritmo, FC y pasos de la vuelta que acabas de cerrar. Antes de la primera: «aún no has cerrado ninguna vuelta manual».
- [ ] **A-19** **No** aparece ya «Historial de Vueltas» en la ventana 3.

### 2.6 Ventana 4 — tabla (T-09, repaso)
- [ ] **A-20** Orden: la vuelta más reciente arriba. AUTO tenue; MANUAL en negrita naranja con bandera.
- [ ] **A-21** Scroll horizontal con la columna nº fija; scroll vertical independiente.
- [ ] **A-22** En el borde izquierdo de la tabla, arrastrar hacia la derecha cambia a la ventana anterior.
- [ ] **A-23** Tocar las 4 pestañas de la barra inferior salta a cada ventana.
- [ ] **A-24** Columnas «Pasos» y «Cadencia» coherentes; `--` donde no hay dato.

### 2.7 Giro de pantalla (landscape)
- [ ] **A-25** Girar el móvil en cada una de las 4 ventanas: se mantiene la ventana y no se pierde ninguna vuelta.
- [ ] **A-26** Ventana 3 apaisada: rejilla de 3×3 legible, timer total + franja de última vuelta a la izquierda, botón NUEVA VUELTA y controles visibles.

### 2.8 Pausa / reanudar / finalizar (T-02)
- [ ] **A-27** **Pausar** y **reanudar** (también con doble toque): no se crea ninguna vuelta.
- [ ] **A-28** Pausar a mitad de un km y reanudar: el km siguiente se cuenta sobre distancia acumulada (el autolap no se desplaza).
- [ ] **A-29** **Finalizar** con una vuelta a medias: no se crea un autolap parcial ni se cierra la vuelta abierta.

---

## Fase 3 — Ruta B: GPS + ANT+ HR + ANT+ cadencia (bici o equivalente)

Objetivo: cadencia rpm en vivo, caídas del sensor, y que con sensor de bici **no** se mezclen rpm con ppm.
Lo ideal es una vuelta en bici de ≥ 2,5 km. Si no puedes, hazlo caminando con el sensor de cadencia sujeto y girándolo a mano (valida la lógica, no la sensación).

- [ ] **B-01** Antes de iniciar: HR y cadencia conectados (puntos en color). Anota batería mostrada: HR ___ · CAD ___.
- [ ] **B-02** Pedaleando: el hueco dice **«Cadencia»** en **rpm** (valor en vivo) en las 3 vistas (portrait, landscape métricas, landscape vueltas).
- [ ] **B-03** **Dejar de pedalear > 5 s**: el hueco pasa a **«Ritmo»** (o `--` si vas rápido y no cuadra) sin tocar nada.
- [ ] **B-04** **Volver a pedalear**: vuelve a «Cadencia» en vivo.
- [ ] **B-05** **Alejar/apagar el sensor de cadencia** unos segundos (sacarlo de la biela o taparlo): conmuta a ritmo; al volver a emitir, recupera cadencia.
- [ ] **B-06** HR: el valor se actualiza en ventanas 1 y 3; color del HR rosa cuando está conectado, gris si no.
- [ ] **B-07** Ventana 3 con sensor de bici activo: Cadencia en **rpm**, nunca ppm; **Pasos** puede seguir mostrando pasos del móvil (no se mezclan unidades).
- [ ] **B-08** Cerrar 2 vueltas manuales mientras pedaleas. En la Tabla, la columna Cadencia muestra **rpm** en esas vueltas (no ppm).
- [ ] **B-09** Aviso de batería baja (RF-6, spec 005): si un sensor llega a LOW/CRITICAL sale un aviso **una sola vez**. Si no ocurre, queda «no aplica».
- [ ] **B-10** Batería: los iconos de HR y cadencia aparecen junto a la lectura en `FreeRideScreen`.

---

## Fase 4 — Series en el sitio (sin avance GPS)

Objetivo: vuelta con 0 m es válida si dura ≥ 5 s (decisión del 2026-10-10).

- [ ] **C-01** Iniciar, quedarte en el sitio (trotar sin avanzar, o series en el mismo punto) y pulsar vuelta a los **15–20 s**: **sí** se crea (distancia 0.00 km, ritmo `--`).
- [ ] **C-02** Pulsar vuelta a los **< 5 s** de la anterior: se ignora con aviso.
- [ ] **C-03** Hacer 3–4 series de 15–30 s: M1…M4 con tiempos coherentes con tu cronómetro.

---

## Fase 5 — Resumen, Excel e Historial (al terminar la ruta)

### 5.1 Resumen de sesión (T-08)
- [ ] **R-01** Tras finalizar, abrir el detalle: sección por vuelta manual con tiempo, distancia, FC media/máx, pasos, cadencia, ritmo, velocidad media y desnivel+.
- [ ] **R-02** Carrera/caminata (Ruta A): cadencia en **ppm** (pasos del bloque ÷ minutos).
- [ ] **R-03** Bici con sensor (Ruta B): la cadencia ppm **no se muestra** (oculta).
- [ ] **R-04** Sin pasos → `--`, nunca 0.
- [ ] **R-05** La suma de pasos de las vueltas manuales ≤ pasos totales de la sesión.
- [ ] **R-06** Una vuelta manual con distancia 0 (Fase 4) muestra ritmo `--`.

### 5.2 Persistencia (T-04)
- [ ] **R-07** Cerrar la app del todo y reabrirla: las vueltas de la sesión siguen en el detalle, con su tipo (AUTO/MANUAL) y numeración propia.

### 5.3 Exportación (T-06)
- [ ] **R-08** Exportar a Excel: hoja de vueltas con las vueltas **tal cual se guardaron**. Una manual de ~1 km sale como **MANUAL** (no como AUTO).
- [ ] **R-09** No hay vueltas de 0 s / 0 m / 0 km/h en el Excel.

### 5.4 Borrado (T-06)
- [ ] **R-10** Borrar una sesión **desde el Historial** y otra **desde su detalle**: desaparece sin errores. (Las vueltas también se borran; no hay forma visual de comprobarlo, así que dame la señal por log si quieres certeza.)

---

## Fase 6 — Robustez (opcional, si hay tiempo)

- [ ] **X-01** Bloquear la pantalla 2–3 min en ruta y desbloquear: el servicio sigue, tiempos y distancias coherentes.
- [ ] **X-02** Cambiar a otra app (mapas/música) unos minutos y volver: no se pierde la sesión.
- [ ] **X-03** GPS débil (bajo árboles/edificios): no aparecen vueltas absurdas ni autolaps repetidos.
- [ ] **X-04** Desconectar el Bluetooth del móvil con la sesión en marcha y volver a activarlo: la ruta no se rompe.
- [ ] **X-05** Ruta larga (≥ 5 km): la tabla con ≥ 5 AUTO + manuales se desplaza con fluidez.

---

## Cómo recoger logs si algo falla

Con el móvil por USB (solo lectura):

```bash
C:\Users\APO\AppData\Local\Android\Sdk\platform-tools\adb.exe logcat -v time -d > logcat_ruta.txt
```

Hazlo justo después de la prueba que falló y adjúntame el archivo (o las ~50 líneas alrededor del fallo). Anota también la hora aproximada en la que ocurrió.

---

## Qué tienes que confirmarme (formato de respuesta)

Contesta por fase con una línea por tarea; yo cierro cada tarea en `tasks.md` y en la bitácora cuando me confirmes.

| Tarea | Se valida con | Tu confirmación |
|---|---|---|
| **T-04** vueltas manuales independientes + migración 9→10 | P-01, A-10…A-13, R-07 | OK / fallo (+ evidencia) |
| **T-05** estilo por tipo y ritmo por vuelta | A-08, A-20, A-24, A-26 | OK / fallo |
| **T-06** export Excel y borrado | R-08, R-09, R-10 | OK / fallo |
| **T-07** pasos, desnivel y origen de cadencia por vuelta | A-16, A-17, B-07, B-08 | OK / fallo |
| **T-08** resumen por vuelta manual | R-01…R-06 | OK / fallo |
| **T-10** ventana 3 como panel de la vuelta en curso | A-14…A-19, A-26, B-07 | OK / fallo |
| Suite de tests | P-02 | `BUILD SUCCESSFUL` o tests rotos |
| Sensores ANT+ (spec 005) | S-01…S-05, B-01…B-10 | OK / fallo / no aplica |

**Ejemplo de respuesta:** «Fase 0 OK. Fase 2 OK salvo A-17 (desnivel marca 40 m en llano) y A-26 (apaisado se corta el botón). Fase 3: B-05 la cadencia no vuelve tras reconectar».

## Si algo falla

- No lo parches en el campo ni sigas con las pruebas dependientes: dime el ID de la prueba y qué viste (captura o log si puedes).
- Los comportamientos ya conocidos (p. ej. `isCadenceConnected` en `true` aunque no emita = M-01; batería ANT+ del HR sin dato = limitación del SDK) se anotan como «conocido», no como fallo nuevo.
