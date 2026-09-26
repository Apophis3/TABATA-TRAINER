# Plan Técnico — Spec 001 (Tabata Trainer)

## Estructura de módulos
- `sensors/SensorManager.kt` → Manejo de callbacks BLE/ANT+, mapa de dispositivos y emisión en `StateFlow` independientes (`hr1State`, `hr2State`)[cite: 4, 5]. (RF-1, RF-2)
- `services/WorkoutService.kt` y `FreeRideService.kt` → Acumulación de series temporales, cálculo de medias/máximas al finalizar[cite: 4]. (RF-4, RF-5, RF-6)
- `data/db/SessionDao.kt` y `SessionEntity.kt` → Definición de esquema Room DB e inserción asíncrona[cite: 4]. (RF-6, RF-8, RF-9)
- `ui/components/HeartRateGraph.kt` → Composables para renderizado de series temporales sin truncado a 2 minutos[cite: 4, 5]. (RF-3)
- `ui/screens/SessionDetailScreen.kt` → Carga y despliegue del resumen guardado[cite: 4]. (RF-7)

## Modelo de datos (Room Entity: `SessionEntity`)
```kotlin
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateTimestamp: Long,
    val sessionType: String, // "TABATA" o "FREE_RIDE"
    val durationSeconds: Long,
    val avgHr1: Int?,
    val maxHr1: Int?,
    val avgHr2: Int?,
    val maxHr2: Int?,
    val avgCadenceC1: Int?,
    val avgCadenceC2: Int?,
    val avgSpeedKmH: Double?
)

Operaciones asíncronas vía Suspend Functions en SessionDao (RF-6, RF-8).


Algoritmo de Cálculo y Agregación (RF-4, RF-5)
Durante la sesión, los servicios almacenan en memoria listas de lecturas: samplesHr1, samplesHr2, samplesSpeed.

Al invocar finishSession():

avgHr1 = if (samplesHr1.isNotEmpty()) samplesHr1.average().toInt() else null

maxHr1 = samplesHr1.maxOrNull()

Mismo cálculo para HR2, C1, C2 y Speed.

Se construye el SessionEntity y se ejecuta sessionDao.insertSession(entity) en Dispatchers.IO.

Decisiones técnicas
Estado de Sensores: Sustituir variables únicas por un ConcurrentHashMap<String, SensorData> en SensorManager para evitar sobrescrituras de hilos/callbacks (RF-1)[cite: 4, 5].

Buffer de Gráfica: Mantener el estado de la lista de puntos en el ViewModel (StateFlow<List<HrPoint>>) en lugar de recortar a los últimos 120 segundos (RF-3)[cite: 4, 5].

Inyección de Dependencias/Dispatchers: Pasar CoroutineDispatcher a los servicios para permitir testing unitario de la persistencia (RF-6).

Contrato de UI & Navigation
Acciones de cierre en WorkoutScreen y FreeRideScreen detienen el temporizador y disparan la navegación a session/{sessionId} tras la confirmación de inserción en Room[cite: 4, 5].

Estrategia de tests
Unitarios (./gradlew test):

Tests en SensorManagerTest para verificar la recepción multicanal de HR1 y HR2 simultáneos[cite: 4, 5].

Tests en MetricsCalculatorTest para validar los algoritmos de promedio y máximos excluyendo valores nulos.

Integración (./gradlew connectedAndroidTest):

Test con Room In-Memory Database para validar el SessionDao.insertSession() y su posterior lectura en SessionDetailScreen[cite: 4].