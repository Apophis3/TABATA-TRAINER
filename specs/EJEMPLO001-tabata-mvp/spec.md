# Spec 001 — Core Metrics & Session Persistence (Tabata Trainer)

## Contexto y objetivo
Actualmente, los deportistas que usan Tabata Trainer experimentan inconsistencias al finalizar sus entrenamientos: los informes de sesión no registran métricas completas (HR1/HR2 Media, Cadencias C1/C2 y Velocidad Media), la gráfica de pulsómetros solo muestra los últimos 2 minutos y la lectura de múltiples sensores BLE/ANT+ de forma simultánea presenta fallos de concurrencia. 
Esta especificación define el comportamiento exacto para garantizar la agregación, renderizado completo y persistencia de métricas de sesión en Room DB.

## Usuarios
Atletas y deportistas que entrenan con pulsómetros (BLE/ANT+), sensores de cadencia y GPS, y que requieren un resumen preciso de sus métricas de entrenamiento.

## Historias de usuario
- **H1:** Como deportista quiero que al finalizar un entrenamiento Tabata o Ruta Libre se guarde un informe completo con las medias y máximas de frecuencia cardíaca, cadencia y velocidad[cite: 4].
- **H2:** Como deportista quiero ver la gráfica de frecuencia cardíaca de toda la sesión (desde el segundo cero hasta el final) y no solo un fragmento temporal[cite: 4].
- **H3:** Como deportista quiero conectar múltiples sensores (ej: dos pulsómetros HR1 y HR2 o sensores de cadencia C1 y C2) y que la app registre ambos datos de forma concurrente en tiempo real y en el resumen final.

## Requisitos funcionales (criterios de aceptación en EARS)

### Captura Concurrente de Sensores (H3)
- **RF-1:** MIENTRAS `SensorManager` mantenga conexiones activas BLE o ANT+, EL SISTEMA registrará los flujos de datos de HR1 y HR2 (y cadencias C1/C2) en estructuras independientes sin sobrescribir el estado del dispositivo opuesto[cite: 4, 5].
- **RF-2:** SI un sensor de frecuencia cardíaca o cadencia se desconecta temporalmente durante la sesión, ENTONCES EL SISTEMA continuará calculando las métricas con el sensor activo restante y tolerará la reconexión sin reiniciar la sesión ni causar un crash[cite: 4, 5].

### Visualización y Gráfica Histórica (H2)
- **RF-3:** CUANDO la pantalla `WorkoutScreen` o `FreeRideScreen` renderice la gráfica de pulsómetros (`HeartRateGraph`), EL SISTEMA mantendrá en memoria el buffer completo de datos desde `t=0` hasta el tiempo actual de la sesión[cite: 4, 5].
- **RF-4:** EL SISTEMA calculará las métricas en pantalla de HR Media y HR Máxima basándose en la totalidad de los datos acumulados en la sesión y no únicamente en la ventana temporal de visualización de la gráfica.

### Agregación y Persistencia al Cierre (H1)
- **RF-5:** CUANDO el usuario pulse el botón 'Finalizar' en `WorkoutService` o `FreeRideService`, EL SISTEMA calculará de forma atómica la HR Media (HR1/HR2), HR Máxima, Cadencia Media (C1/C2) y Velocidad Media de toda la sesión[cite: 4].
- **RF-6:** SI finaliza la sesión (Tabata o Ruta Libre), ENTONCES EL SISTEMA insertará un registro completo en la entidad `SessionEntity` de Room DB antes de destruir el `Foreground Service`[cite: 4].
- **RF-7:** CUANDO el usuario navegue a `SessionDetailScreen`, EL SISTEMA leerá de Room DB los valores procesados y los desplegará sin dejar campos nulos o con valor 0 por defecto[cite: 4].

### Reglas transversales
- **RF-8:** EL SISTEMA utilizará Room Database como única fuente de verdad local para la persistencia del historial[cite: 4].
- **RF-9:** SI ocurre un cierre inesperado del servicio en segundo plano, ENTONCES EL SISTEMA persistirá el último estado conocido en Room DB evitando archivos o registros corruptos[cite: 4, 5].

## Requisitos no funcionales
- Rendimiento de UI en Jetpack Compose a 60 fps sin parones (jank) al renderizar series temporales en `HeartRateGraph`[cite: 4, 5].
- Guardado en base de datos Room en menos de 300 ms mediante Kotlin Coroutines (`Dispatchers.IO`)[cite: 4].
- Mensajes y métricas expuestos en la UI en español[cite: 4].

## Casos límite ya cubiertos
- Desconexión total de sensores mid-session → RF-2 (se calcula con los datos parciales recopilados).
- Cierre voluntario de la app con servicio activo → RF-9 (el `Foreground Service` salva el estado en Room DB antes de morir)[cite: 4].
- Sesiones de larga duración (>2 horas) → RF-3 (el buffer de la gráfica optimiza la memoria sin purgar datos históricos).

## Fuera de alcance (MVP Spec 001)
Exportación a Strava/Fit (TCX/GPX); sincronización en la nube; mapas 3D avanzados; integración con smartwatches Wear OS.

## Criterios de finalización
- Todos los RF cubiertos por tests unitarios (`./gradlew test`) o de integración (`./gradlew connectedAndroidTest`).
- Verificación en Logcat de la recepción simultánea de HR1 y HR2[cite: 4, 5].
- Registro comprobado en Room DB con métricas mayores a 0 visibles en `SessionDetailScreen`[cite: 4].

## Dudas abiertas
- Ninguna.