# Spec 009 — Ruta entera en el mapa, confirmación de vuelta y zancada por velocidad

Estado: **aprobada por el usuario en el chat (2026-11-28)**: «hagamos lo de la ruta entera visualizada, lo de la
banderita […] el vibrador con el marcador en el mapa. Hacer un cálculo de zancadas […] en base a los kilómetros por
hora». Rama: ninguna, se trabaja en `main` (decisión del usuario del 2026-11-28).
Origen: salida del 2026-11-28 (`specs/008-raw-logging/salida_2026-11-28.md`, §3.1, §3.2 y §3.4).

## Problema
1. En Ruta Libre el mapa en vivo solo muestra los últimos 1000 puntos (unos 15–20 min andando). El usuario no ve
   la ruta entera y el marcador verde «Inicio» no está en la salida real.
2. Al pulsar la banderita, si la vuelta se guarda, no hay ninguna señal. No se sabe si se ha marcado.
3. La app da una sola zancada media para toda la salida, aunque la zancada cambia mucho con la velocidad
   (0,61 m a 2,8 km/h, 0,70 m a 5 km/h y 0,90 m a 8 km/h en la salida del 28-nov).

## Requisitos

### R1 — Ruta entera en el mapa en vivo
- **RF-1.1** El mapa en vivo dibuja la ruta **desde el primer punto de la sesión** hasta el último, sin tope.
- **RF-1.2** Para no cargar el mapa, la línea se dibuja con una versión **aligerada**: se quitan los puntos que no
  cambian la forma más de **2 m**. Siempre se conservan el primer punto (salida real) y el último (posición actual).
- **RF-1.3** Lo que se guarda no cambia: los puntos de Room y el registro bruto siguen completos. El aligerado solo
  afecta al dibujo en vivo.
- **RF-1.4** El marcador verde «Inicio» está en el primer punto de la sesión.

### R2 — Botón «Ver todo»
- **RF-2.1** En el mapa en vivo hay un botón «Ver todo» (arriba a la derecha). Al pulsarlo, la cámara encuadra la
  ruta entera más la posición actual.
- **RF-2.2** Mientras está en «Ver todo», el mapa **no** sigue la posición (no vuelve solo a los 10 s) y el botón se
  ve activado. El usuario puede mover y hacer zoom libremente.
- **RF-2.3** Al pulsarlo otra vez, se sale de «Ver todo» y el mapa vuelve a seguir la posición con el zoom que el
  usuario había elegido antes (el de «Ver todo» no cuenta como zoom elegido).
- **RF-2.4** Con menos de 2 puntos distintos el botón no hace nada.

### R3 — Confirmación al marcar vuelta manual
- **RF-3.1** Cuando una vuelta manual **se guarda**, el móvil vibra una vez, corto (~80 ms). La vibración la lanza el
  servicio.
- **RF-3.2** En cualquier página de Ruta Libre aparece arriba un aviso «🏁 Vuelta N» que entra con un pequeño zoom
  (crece y vuelve a su tamaño en ~0,5 s) y desaparece a los ~1,5 s.
- **RF-3.3** En el mapa en vivo aparece un marcador amarillo en el sitio donde se marcó la vuelta, con el título
  «Vuelta N». Los marcadores de las vueltas de la sesión se mantienen hasta parar.
- **RF-3.4** Si la vuelta se ignora (< 5 s), se mantiene el aviso actual «Vuelta ignorada…» y no hay vibración,
  aviso ni marcador.

### R4 — Zancada según la velocidad (resumen final)
- **RF-4.1** Durante la ruta, cada bloque de **10 s** en marcha (sin pausa) se clasifica por su velocidad media
  (distancia GPS del bloque / 10 s) y suma sus pasos y su distancia al tramo de velocidad que le toca.
- **RF-4.2** Tramos: de **1 km/h** hasta 7 km/h (1–2, 2–3, …, 6–7) y de **2 km/h** a partir de 7 (7–9, 9–11, …).
  Por debajo de 1 km/h (parado) no cuenta. Un bloque con menos de 5 pasos no cuenta.
- **RF-4.3** Al parar, los tramos se guardan con la sesión.
- **RF-4.4** En el detalle de la sesión aparece la tarjeta «Zancada según velocidad»: una fila por tramo con
  zancada (distancia / pasos, 2 decimales), pasos y tiempo. Solo se muestran los tramos con **100 pasos o más**.
  Si no hay ninguno, la tarjeta no aparece.
- **RF-4.5** Las sesiones antiguas (sin este dato) se ven como hoy.

## Fuera de alcance (anotado, no aprobado todavía)
- Desnivel calculado con barómetro (hoy +471 m en una ruta llana).
- Guardar la vuelta parcial al parar.
- Anotar en el registro bruto los cambios ANT+ ↔ Bluetooth.
- Prueba de fotos con Logcat (la app se para ~5 s al abrir la cámara).
- Lo apuntado en `specs/008-raw-logging/plan.md` §11 (saltos de GPS al empezar, puntos parados…).
