APP android, en ruta con GPS con opcion tabata, hr (BLE y ANT+), cadencia  (BLE y ANT+), velocidad, registros datos distancia, velocidad, desnivel, vueltas, autovueltas cada 1km y opcion de poder añadir marcas para vueltas manuales (diferentes a 1km del autovueltas) ...
y con una opción que es para tabatas en interior, con opcion tabata, hr (BLE y ANT+), cadencia  (BLE y ANT+), graficas de pulsometro. Configuracion tabatas y HITT  y randon: descanso, work, rest

al finalizar, resumen de registros full, DB con datos de la sesión, valoración sesión, Opcion de modificar nombre, fecha sesión, ....
# Tabata Trainer - Descripción de la App (Pantalla a Pantalla)

Explicación técnica de cada pantalla de la aplicación, dirigida a alguien sin conocimientos de programación.

---

## HomeScreen.kt - La pantalla de inicio

**¿Qué es?**

Es el menú principal de tu aplicación. Su única función es servir como centro de mando: desde aquí ves si tus dispositivos están listos y eliges qué entrenamiento vas a hacer.

**¿Qué hay en la pantalla y para qué sirve?**

- **Barra superior:** Muestra el nombre de la app ("TABATA TRAINER") y el usuario. A la derecha tienes dos botones: uno para ver el historial de entrenamientos y otro para ir a ajustes.

- **Barra de Sensores:** Te dice de un vistazo si tus aparatos están conectados antes de empezar:
  - **HR1:** Tu medidor de frecuencia cardíaca (pulsómetro).
  - **CAD1:** Tu sensor de cadencia (para la bicicleta).
  - **HR2:** Tu medidor de frecuencia cardíaca (pulsómetro).
  - **CAD2:** Tu sensor de cadencia (para la bicicleta).
  - **GPS:** La ubicación de tu teléfono.
  - Si están en color, están listos; si están en gris, están desconectados.

- **Tarjetas de Entrenamiento (El corazón de la app):**
  - **Ruta Libre:** Para salir a hacer ejercicio sin límites de tiempo, registrando tu recorrido y datos con GPS.
  - **Tabata:** Para entrenamientos de alta intensidad por intervalos (tiempo de trabajo y descanso).

- **Acceso Rápido:** Botones abajo para revisar tus estadísticas, ver tu perfil o acceder al historial.

- **Botón de Cierre:** Para salir de la app de forma segura (con una confirmación para que no te salgas por error a mitad de un entrenamiento).

**¿Cómo funciona por dentro?**

Cuando abres la app, esta pantalla hace tres cosas automáticas:
1. Escanea si hay sensores Bluetooth cerca (pulsómetro, cadencia).
2. Detecta si tu teléfono está en vertical u horizontal y cambia el diseño para que se vea bien en ambos casos.
3. Cuando tocas una tarjeta de entrenamiento, te lleva a la pantalla correspondiente.

---

## ConfigScreen.kt - La pantalla de configuración del Tabata

**¿Qué es?**

Es donde personalizas tu entrenamiento antes de empezar. Aquí decides cuánto tiempo vas a trabajar, cuánto descansar y cuántas rondas quieres hacer. Es como el "panel de control" de tu sesión.

**¿Qué hay en la pantalla y para qué sirve?**

- **Barra superior:** El nombre de la app y un botón para ir al historial rápidamente.

- **Barra de Sensores:** Te dice de un vistazo si tus aparatos están conectados antes de empezar:
  - **HR1:** Tu medidor de frecuencia cardíaca (pulsómetro).
  - **CAD1:** Tu sensor de cadencia (para la bicicleta).
  - **HR2:** Tu medidor de frecuencia cardíaca (pulsómetro).
  - **CAD2:** Tu sensor de cadencia (para la bicicleta).
  - **GPS:** La ubicación de tu teléfono.
  - Si están en color, están listos; si están en gris, están desconectados.

- **Banner de Entrenamiento en Curso:** Si ya tienes un Tabata activo (por ejemplo, si saliste de la app y volvistes), aparece un aviso que dice "EN CURSO" con un botón "IR" para reanudarlo sin perder lo que llevabas.

- **Barra de Sensores:** Igual que en la pantalla principal, pero aquí también puedes activar o desactivar el GPS con un interruptor. Si lo activas, la app grabará tu ruta mientras entrenas.

- **Tiempo Total:** Un cuadro grande que muestra la duración total de tu entrenamiento (por ejemplo, "12:30"). Se calcula automáticamente según lo que configures.

- **Presets (Atajos):** Botones para elegir configuraciones predefinidas como "Tabata", "HIIT" o "Custom". Si eliges "Custom", puedes modificar todo a tu gusto.

- **Deslizadores (Sliders):** Cuatro controles deslizantes para ajustar:
  - **Calentamiento:** Cuántos segundos quieres calentar antes de empezar.
  - **Trabajo:** Cuántos segundos durará cada intervalo de esfuerzo.
  - **Descanso:** Cuántos segundos descansarás entre intervalos.
  - **Rondas:** Cuántas veces repetirás el ciclo trabajo-descanso.

- **Botón "INICIAR ENTRENAMIENTO":** Cuando todo esté listo, toca aquí para comenzar. Si hay un entrenamiento activo, el botón se desactiva para evitar confusiones.

**¿Cómo funciona por dentro?**

1. **Carga tu última configuración:** Cuando abres esta pantalla, la app recuerda los valores que usaste la última vez (por si quieres repetir el mismo entrenamiento).

2. **Escanea sensores:** Automáticamente busca tu pulsómetro y sensor de cadencia mientras configuras.

3. **Calcula el tiempo total:** Cada vez que mueves un deslizador, la app recalcula cuánto durará todo el entrenamiento y te lo muestra en tiempo real.

4. **Guarda tu configuración:** Si estás en modo "Custom" y tocas iniciar, la app guarda tus ajustes para la próxima vez.

5. **Detecta entrenamiento activo:** Si ya tienes un Tabata corriendo en segundo plano, te muestra el banner con opción de reanudar.

---

## WorkoutScreen.kt - La pantalla de entrenamiento activo

**¿Qué es?**

Es donde ocurre la acción. Cuando tocas "INICIAR" en la pantalla de configuración, esta pantalla toma el control y te guía durante todo el entrenamiento con un gran temporizador, colores por fase y todos tus datos en tiempo real.

**¿Qué hay en la pantalla y para qué sirve?**

- **Indicadores de Sensores (arriba):** Muestran el estado de tus dispositivos:
  - **HR1 y HR2:** Tus pulsómetros (puedes tener dos conectados a la vez).
  - **C1 y C2:** Tus sensores de cadencia.
  - **GPS:** Si está activo y rastreando.
  - Cada chip tiene un punto de color: verde = conectado, naranja = buscando, rojo = desconectado.

- **Título de la Fase:** Te dice en qué parte del entrenamiento estás:
  - **CALENTAMIENTO** (naranja) - Preparación antes del esfuerzo.
  - **TRABAJO** (verde) - Tiempo de máxima intensidad.
  - **DESCANSO** (rojo) - Recuperación entre intervalos.

- **Temporizador Grande:** El número más grande de la pantalla. Muestra los segundos restantes de la fase actual. Cuando quedan 3 segundos o menos, el número pulsa para llamarte la atención.

- **Display de Rondas:** Te dice en qué ronda estás (por ejemplo, "3/8") con bolitas que se iluminan a medida que completas rondas.

- **Gráfica de Frecuencia Cardíaca:** Una línea que dibuja tu pulso en tiempo real. Si tienes dos pulsómetros, verás dos líneas de diferentes colores. Muestra cómo responde tu cuerpo al esfuerzo.

- **Panel de Métricas:** Un cuadro con todos tus datos:
  - HR actual y máximo de cada pulsómetro.
  - Cadencia actual de cada sensor (rpm).

- **Tiempo Total:** Cuántos minutos llevas entrenando desde el inicio.

- **Controles (abajo):**
  - **PLAY:** Para iniciar el entrenamiento.
  - **PAUSA:** Para hacer una pausa (aparece cuando ya estás entrenando).
  - **STOP:** Para detener completamente.
  - Los botones cambian según el estado: si estás pausado, verás "Reanudar" y "Detener".

- **Página GPS (desliza a la derecha):** Si activaste el GPS, puedes deslizar para ver una segunda pantalla con:
  - Velocidad actual en grande.
  - Distancia recorrida.
  - Velocidad media y máxima.
  - Estado del GPS.

- **Diálogo de Finalización:** Cuando terminas todas las rondas, aparece un resumen con:
  - Tiempo total.
  - Rondas completadas.
  - Estadísticas de HR y cadencia.
  - Datos GPS si los tienes activos.

**¿Cómo funciona por dentro?**

1. **Se conecta al servicio:** La app tiene un "servicio" que corre en segundo plano (incluso si cierras la pantalla). Esta pantalla se conecta a ese servicio para recibir todos los datos.

2. **Cambia de fase automáticamente:** El servicio le dice a la pantalla en qué fase estás (calentamiento, trabajo, descanso) y la pantalla cambia los colores y el temporizador automáticamente.

3. **Funciona en segundo plano:** Si sales de esta pantalla, el entrenamiento sigue corriendo. Puedes volver y retomar donde lo dejaste.

4. **Se adapta a tu posición:** En vertical ves todo en una columna. En horizontal, se divide en dos columnas: temporizador y controles a la izquierda, gráfica y métricas a la derecha.

5. **Guarda todo al terminar:** Cuando finalizas, el servicio guarda automáticamente la sesión con todos los datos para que puedas verla en el historial.

---

## FreeRideScreen.kt - La pantalla de ruta libre

**¿Qué es?**

Es para cuando quieres salir a pedalear sin límites de tiempo ni intervalos. Solo tú, tu bici, y la app registrando todo: velocidad, distancia, ruta, pulsaciones, cadencia. Como un GPS de ciclismo completo.

**¿Qué hay en la pantalla y para qué sirve?**

Esta pantalla tiene **3 páginas** que puedes deslizar horizontalmente:

### Página 1: Métricas (la principal)

- **Barra de Sensores (arriba):** Muestra GPS, HR y Cadencia con animaciones cuando están buscando señal.

- **Estado:** Te dice si estás "PREPARADO", "EN RUTA" o "PAUSADO".

- **Temporizador Grande:** El tiempo total que llevas en la ruta (por ejemplo, "1:23:45").

- **Cuatro Métricas Grandes:**
  - **Velocidad:** A cuántos km/h vas ahora mismo.
  - **Distancia:** Cuántos km has recorrido en total.
  - **HR:** Tu frecuencia cardíaca actual.
  - **Cadencia:** Cuántas pedaladas por minuto (rpm).

- **Controles (abajo):**
  - **PLAY:** Para empezar a grabar la ruta.
  - **PAUSA:** Para hacer una parada (en un semáforo, por ejemplo).
  - **STOP:** Para terminar y guardar la sesión.
  - **NUEVA VUELTA:** Botón amarillo para marcar vueltas manually.

### Página 2: Mapa

- **Mapa de Google Maps:** Muestra tu ubicación en tiempo real con una línea azul dibujando tu ruta.
- **Marcador verde:** Punto de inicio.
- **Marcador azul:** Tu posición actual.
- **Chip de GPS (arriba):** Estado de la conexión GPS, coordenadas y precisión.
- **Barra inferior:** Velocidad, tiempo y distancia siempre visibles.
- **Botón de pausa:** Flotante a la derecha.

### Página 3: Vueltas

- **Métricas de la vuelta actual:** Número de vuelta, HR, distancia y tiempo de la vuelta que estás haciendo.
- **Botón "NUEVA VUELTA":** Para marcar cuando completes una vuelta.
- **Historial de Vueltas:** Una tabla desplegable con todas las vueltas que has hecho, mostrando:
  - Número de vuelta.
  - Tiempo de esa vuelta.
  - Distancia recorrida.
  - Velocidad media.
  - HR medio.

**¿Cómo funciona por dentro?**

1. **Inicia un servicio:** Cuando abres esta pantalla, se activa el "FreeRideService" que corre en segundo plano y gestiona todo: GPS, sensores, temporización.

2. **Graba tu ruta punto a punto:** Cada pocos segundos, el servicio guarda tu ubicación (latitud y longitud) para dibujar la línea en el mapa.

3. **Las vueltas son manuales:** Tú decides cuándo marcar una vuelta tocando el botón. La app calcula automáticamente los datos de cada vuelta.

4. **Funciona con la pantalla apagada:** Gracias al servicio en segundo plano, puedes putar el teléfono en el bolsillo y la app sigue registrando todo.

5. **Se adapta al paisaje:** En horizontal, se divide en dos columnas: temporizador y controles a la izquierda, métricas grandes a la derecha.

6. **Guarda todo al terminar:** Cuando tocas STOP, la sesión se guarda automáticamente en la base de datos para que puedas verla en el historial.

---

## HistoryScreen.kt - La pantalla de historial

**¿Qué es?**

Es donde se guardan todas tus sesiones de entrenamiento. Como un diario deportivo automático: cada vez que terminas un Tabata o una ruta libre, aquí aparece con todos los datos.

**¿Qué hay en la pantalla y para qué sirve?**

- **Barra superior:** Título "Historial" y botón para volver atrás.

- **Lista de Sesiones:** Cada sesión aparece como una tarjeta con:
  - **Fecha y hora:** Cuándo hiciste ese entrenamiento.
  - **Rondas:** Cuántas completaste de cuántas totales (por ejemplo, "8/8").
  - **Duración:** Cuántos minutos duró.
  - **HR:** Tu frecuencia cardíaca media y máxima (si tenías pulsómetro conectado).
  - **GPS:** Distancia recorrida (si tenías GPS activado).
  - **Icono de completado:** Un check verde si terminaste todo, o un bandera gris si lo dejaste a medias.

- **Si no hay sesiones:** Muestra un mensaje amigable diciendo "No hay entrenamientos" con un icono de pesa.

- **Al tocar una sesión:** Te lleva al detalle completo de esa sesión.

**¿Cómo funciona por dentro?**

1. **Consulta la base de datos:** Cada vez que abres esta pantalla, la app busca en su base de datos todas las sesiones guardadas y las ordena de más reciente a más antigua.

2. **Muestra resúmenes rápidos:** En lugar de mostrarte todos los datos de golpe, te da un resumen para que puedas escanear rápidamente tus entrenamientos.

3. **Filtra automáticamente:** Solo muestra sesiones que se guardaron correctamente (con inicio y fin).

---

## SessionDetailScreen.kt - El detalle de cada sesión

**¿Qué es?**

Es la pantalla completa de una sesión específica. Cuando tocas una sesión en el historial, esta pantalla te muestra TODO lo que pasó durante ese entrenamiento: gráficas, estadísticas, configuración original, y opciones para exportar.

**¿Qué hay en la pantalla y para qué sirve?**

- **Barra superior:** Botón para volver, botón para editar notas, y botón para eliminar la sesión.

- **Cabecera:** Fecha completa, hora de inicio, duración total, rondas completadas, y si tenía GPS activado.

- **Métricas Principales (grid):**
  - **HR1:** Frecuencia cardíaca media del primer pulsómetro.
  - **HR1 Máx:** Tu pulso más alto durante la sesión.
  - **Cadencia:** Promedio de pedaladas por minuto.
  - **HR2:** Si tenías un segundo pulsómetro, su promedio y máximo.

- **Métricas GPS (si aplica):**
  - **Distancia:** Kilómetros totales recorridos.
  - **Velocidad Media:** km/h promedio.
  - **Velocidad Máxima:** Tu punto más rápido.

- **Gráfica de Frecuencia Cardíaca:** Una línea que muestra cómo evolucionó tu pulso durante toda la sesión. Si tenías dos pulsómetros, verás dos líneas de diferentes colores.

- **Configuración del Entrenamiento:** Te recuerda cómo tenías configurado ese Tabata:
  - Calentamiento: X segundos.
  - Trabajo: X segundos.
  - Descanso: X segundos.
  - Rondas: X.

- **Botones de Exportación:**
  - **Excel:** Genera un archivo con todos los datos numéricos para analizar en tu computadora.
  - **GPX:** Genera un archivo con tu ruta GPS para importar en otras apps como Strava o Komoot.

- **Calificación:** 5 estrellas para que califiques el esfuerzo de esa sesión (1 = muy suave, 5 = máximo esfuerzo).

- **Notas:** Un cuadro de texto donde puedes escribir recuerdos sobre ese entrenamiento (por ejemplo: "Me sentí fuerte hoy" o "Día de recuperación").

**¿Cómo funciona por dentro?**

1. **Carga todos los datos:** Cuando abres esta pantalla, la app busca en la base de datos la sesión específica, todas sus lecturas de sensores, y todos los puntos GPS.

2. **Calcula estadísticas:** Promedia los valores de HR, encuentra el máximo, calcula la distancia total, etc.

3. **Dibuja la gráfica:** Toma todos los datos de pulso cardíaco y los dibuja como una línea en el tiempo.

4. **Exporta datos:** Cuando tocas "Excel" o "GPX", la app crea un archivo con todos los datos y te deja compartirlo por email, WhatsApp, etc.

5. **Permite edición:** Puedes cambiar las notas o la calificación en cualquier momento y se guarda automáticamente.

---
