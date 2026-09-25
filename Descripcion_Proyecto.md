Tabata Trainer - Resumen del Proyecto
Es una app Android nativa para entrenamientos Tabata/HIIT que se conecta por Bluetooth a sensores de frecuencia cardíaca y cadencia (pulsómetros Garmin, Polar, Wahoo, etc.).
Pantallas principales
Pantalla	Función
HomeScreen	Punto de entrada. Muestra estado de sensores BLE/ANT+ y botones para elegir entre Ruta Libre, Tabata o Historial
ConfigScreen	Configurar parámetros del Tabata (calentamiento, trabajo, descanso, rondas) con sliders. Si hay un entrenamiento activo, muestra banner "EN CURSO" con botón "IR" para reanudar
WorkoutScreen	Pantalla del entrenamiento activo. Muestra timer, fase actual (calentamiento/trabajo/descanso), rondas, gráficas de HR y controles (play/pause/stop)
FreeRideScreen	Ruta libre sin intervalos. Timer continuo, tracking GPS con mapa, métricas de HR/cadencia/velocidad/distancia
HistoryScreen	Lista de sesiones guardadas ordenadas por fecha
SessionDetailScreen	Vista detallada de una sesión: estadísticas, gráficas HR, datos GPS
Flujo del Tabata
WARMUP (14s) → WORK 1 → REST → WORK 2 → REST → ... → WORK N → FIN
Nota importante de la v4: El entrenamiento termina después del último WORK, sin REST final.
Servicios
- WorkoutService - Foreground Service que gestiona el Tabata en segundo plano
- FreeRideService - Foreground Service para rutas libres
- SensorManager - Gestiona la conexión BLE con sensores (HR tiene prioridad sobre cadencia)
Problema corregido (v4)
El botón "IR" en ConfigScreen no funcionaba al volver desde Ruta Libre. Se solucionó obteniendo la configuración directamente del WorkoutService activo en lugar de usar una variable que podía estar null o desincronizada.
Sonidos por fase
- Inicio calentamiento: start_warmup.mp3
- Primer WORK: pistol.mp3 (disparo de salida)
- WORK posteriores: lets_go.mp3
- Descanso: stop_rest.mp3
- Countdown 3-2-1: countdown.mp3
- Fin de sesión: finish_session.wav
Stack técnico
- Android nativo con Kotlin + Jetpack Compose
- Room Database para persistencia de sesiones
- Hilt para inyección de dependencias
- Navigation Compose para navegación entre pantallas
- SDK 34, mínimo Android 8.0 (API 26)


Explicación Detallada del Proyecto Tabata Trainer
¿Qué es?
Tabata Trainer es una aplicación Android hecha para ciclistas y deportistas que quieren hacer entrenamientos Tabata (intervalos de alta intensidad) con datos reales de sus sensores Bluetooth: pulsómetro y sensor de cadencia.
El problema que resuelve
Cuando haces Tabata en bici, no es fácil controlar los tiempos (calentamiento, trabajo, descanso) y al mismo tiempo mirar tu frecuencia cardíaca o cadencia. Esta app hace todo eso automáticamente: te dice cuándo cambiar de fase, te mide el pulso en tiempo real, y guarda todo el historial para que puedas ver tu progreso.
Las dos actividades principales
1. Tabata (intervalos)
- Configuras: cuánto calentar, cuánto trabajar, cuánto descansar, y cuántas rondas
- La app te va guiando con sonidos y vibración
- Muestra tu frecuencia cardíaca y cadencia en tiempo real
- Al terminar, guarda la sesión con todas las estadísticas
2. Ruta Libre
- Sin límite de tiempo, simplemente pedaleas
- Lleva tu ruta con GPS y mapa
- Muestra velocidad, distancia, elevación
- Puedes marcar vueltas
Cómo funciona por dentro
Sensores Bluetooth:
- La app busca automáticamente sensores pulsómetro (HR) y cadencia (CAD) por Bluetooth
- El pulsómetro tiene prioridad
- Los sensores de cadencia solo emiten señal cuando estás pedaleando
- Si se desconecta, intenta reconectar solo
Servicios en segundo plano:
- Usa un "Foreground Service" para que el entrenamiento siga aunque cierres la pantalla o cambies de app
- Cuando vuelves a la app, te muestra que hay un entrenamiento activo y puedes reanudarlo
Persistencia:
- Cada sesión se guarda en una base de datos local (Room)
- Puedes ver el historial de todas tus sesiones con estadísticas detalladas
Recorrido del usuario
Pantalla Principal
  ├── Elige "TABATA" → Configura tiempos y rondas → Entrena → Guarda sesión
  ├── Elige "RUTA LIBRE" → Pedaleas con GPS → Guarda sesión
  └── Elige "HISTORIAL" → Revisa sesiones anteriores → Detalles con gráficas
Correcciones importantes (v4)
1. El Tabata ahora termina después del último WORK (antes sobraba un descanso innecesario al final)
2. El botón "IR" para reanudar un entrenamiento ahora funciona - antes se bloqueaba si volvías de la pantalla de ruta libre
3. Los sensores se reconectan automáticamente entre entrenamientos
4. Sonidos corregidos por fase (disparo de salida, countdown, etc.)
Requisitos técnicos
- Android 8.0 o superior
- Bluetooth activo
- Permisos de Bluetooth y ubicación (necesario para BLE en Android)
- Compatible con sensores Garmin, Polar, Wahoo y cualquier sensor BLE estándar
En resumen
Es una app de entrenamiento deportivo que combina un timer Tabata inteligente con sensores Bluetooth reales, guardando todo el historial para que el deportista pueda analizar su rendimiento. No es solo un timer básico: conecta con tu cuerpo (pulsómetro) y tu bici (cadencia) para darte datos reales de cada sesión.