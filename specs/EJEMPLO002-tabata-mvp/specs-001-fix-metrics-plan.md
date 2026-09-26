# PLAN DE IMPLEMENTACIÓN: Solución de Métricas Incompletas (specs/001-fix-metrics/plan.md)
## Estado: Listo para Desarrollo
## Referencia: docs/constitution.md & specs/001-fix-metrics/spec.md

---

## 1. Impacto en Componentes Existentes

La solución de métricas multiusuario requiere modificar e introducir responsabilidades en las capas clave de la arquitectura del proyecto `tabata-trainer` [cite: 397]:

*   **`SensorManager` (Capa de Hardware/Ingesta):**
    *   *Actual:* Maneja un único canal simplificado de pulsómetro y cadencia [cite: 397].
    *   *Cambio:* Debe evolucionar para gestionar de forma concurrente hasta 8 flujos físicos independientes (4 de Frecuencia Cardíaca y 4 de Cadencia) [cite: 397]. Expondrá un mapa reactivo `StateFlow<Map<String, SensorState>>` donde la clave será la dirección MAC del dispositivo o el ANT+ ID [cite: 401], y el valor representará las pulsaciones/revoluciones recibidas y el estado actual de la conexión.
*   **`WorkoutService` y `FreeRideService` (Capa de Aplicación / Foreground Services):**
    *   *Actual:* Guardan métricas en variables individuales en memoria que se pierden o se guardan de manera incompleta [cite: 398, 401].
    *   *Cambio:* Actuarán como los recolectores de la sesión grupal [cite: 397]. Cada servicio mantendrá internamente una colección de agregadores temporales (`AthleteSessionTracker`), uno por deportista activo. Este tracker calculará el tiempo acumulado de conexión activa ($T_{\text{con}}$) y mantendrá en memoria los buffers con las series de tiempo recibidas.
    *   *Mecanismo de Guardado Incremental:* Cada 30 segundos, el servicio escribirá en disco un respaldo del progreso de la sesión a través de tablas temporales en Room [cite: 398], cubriendo el caso límite de muerte súbita (CL-1).
*   **`SessionDao` (Capa de Datos):**
    *   *Actual:* Operaciones básicas de inserción de un único registro lineal de sesión [cite: 398].
    *   *Cambio:* Se añadirá un método anotado con `@Transaction` para realizar la persistencia atómica de la sesión grupal (`SessionEntity`), las métricas agregadas finales de todos los atletas participantes (`DeviceSessionMetricsEntity`) y el historial detallado de telemetría de las series temporales (`SensorTelemetrySeriesEntity`) [cite: 398, 401].

---

## 2. Flujo de Datos (Del Sensor a Room DB)

El flujo de información técnica sigue un recorrido determinista para evitar colisiones y demoras en el hilo principal [cite: 400]:

1.  **Emisión Física:** Los sensores envían broadcasts de telemetría por Bluetooth BLE o ANT+ a una frecuencia estable de 1 Hz [cite: 397].
2.  **Ingesta de Red:** El sistema operativo despacha estos bytes a `SensorManager` en hilos de background dedicados.
3.  **Procesamiento y Filtrado de Ruido (Jitter):** 
    *   El servicio en segundo plano recibe el flujo [cite: 398].
    *   Si un sensor reporta desconexión temporal menor a 2 segundos (CL-2), el temporizador de conexión $T_{\text{con}}$ se mantiene activo y los datos nulos se filtran para evitar "baches" artificiales.
    *   Si supera los 2 segundos, se congela el acumulador temporal de conexión y se emite un evento de reconexión acotado.
4.  **Agregación en Memoria:** Las señales válidas se acumulan en un búfer dinámico asignado al atleta dentro de la sesión de `WorkoutService` o `FreeRideService` [cite: 397].
5.  **Persistencia Transaccional:** Al pulsar "Finalizar sesión", el servicio despacha el lote de datos agrupado en tres entidades relacionales al `SessionDao`, ejecutando una transacción única SQLite en la base de datos de Room [cite: 398].

---

## 3. Modelo de Entidades y Base de Datos (Room DB)

Para cumplir con la constitución del proyecto (código estrictamente en inglés) [cite: 400] y solventar las necesidades de gráficos dinámicos (mencionadas en la QA Audit) [cite: 397], estructuramos el siguiente esquema relacional de base de datos en Room:

### 3.1. `AthleteEntity` (Tabla de Deportistas)
*   `athleteId`: String (PK - UUID)
*   `firstName`: String (Nombre)
*   `lastName`: String (Apellido)
*   `assignedHrMac`: String? (Opcional - MAC de Pulsómetro)
*   `assignedCadenceMac`: String? (Opcional - MAC de Cadencia)

### 3.2. `SessionEntity` (Tabla de Sesión Global)
*   `sessionId`: String (PK - UUID)
*   `sessionType`: String (TABATA o FREE_RIDE) [cite: 397]
*   `startTime`: Long (Timestamp de inicio)
*   `endTime`: Long (Timestamp de fin)
*   `totalDuration`: Long (Duración total en segundos)
*   `tabataConfig`: String? (JSON con configuración de intervalos si aplica) [cite: 397]

### 3.3. `DeviceSessionMetricsEntity` (Tabla de Métricas Agregadas)
*   `metricsId`: String (PK - UUID)
*   `sessionId`: String (FK - ON DELETE CASCADE)
*   `athleteId`: String (FK - ON DELETE RESTRICT)
*   `avgHeartRate`: Double? (Calculado solo sobre $T_{\text{con}}$)
*   `maxHeartRate`: Int?
*   `avgCadence`: Double? (Calculado solo sobre $T_{\text{con}}$)
*   `maxCadence`: Int?
*   `avgSpeed`: Double? (GPS - Coordenadas válidas)
*   `activeConnectionDuration`: Long (Suma total de $T_{\text{con}}$ en segundos)

### 3.4. `SensorTelemetrySeriesEntity` (Tabla de Gráficos de Tiempo)
Para solventar la contradicción del QA Audit y renderizar curvas reales en Compose [cite: 397], guardaremos muestras comprimidas cada segundo o por cambios significativos:
*   `telemetryId`: Long (PK - AutoIncrement)
*   `sessionId`: String (FK - ON DELETE CASCADE)
*   `athleteId`: String (FK - ON DELETE CASCADE)
*   `timeOffset`: Int (Segundos transcurridos desde el inicio de la sesión, ej. 45s)
*   `heartRateValue`: Int?
*   `cadenceValue`: Int?
*   `speedValue`: Double?

---

## 4. Decisiones Técnicas Justificadas

1.  **Separación de Telemetría Detallada (Series) vs. Métricas Consolidadas (RF-3.1, RF-4.2):**
    *   *Justificación:* Separar los agregados finales de las series temporales optimiza las consultas de la UI del Historial [cite: 397]. Cargar el listado de sesiones anteriores es instantáneo porque no necesita leer miles de muestras de sensores por segundo. La telemetría detallada solo se consulta de forma perezosa al abrir el detalle de una sesión grupal específica [cite: 397].
2.  **Mecanismo de Buffering y Escritura Diferida (RNF-2, RNF-3):**
    *   *Justificación:* Para mantener el renderizado en Compose por encima de los 60 FPS estables, los eventos de los sensores no escribirán directamente en Room DB de forma inmediata. Se almacenarán en una estructura Kotlin de tipo `MutableList` protegida por un semáforo de exclusión mutua (`Mutex`) en segundo plano y se vaciarán a Room DB únicamente bajo dos circunstancias: commits temporales automáticos cada 30 segundos (CL-1) y el guardado definitivo de fin de sesión [cite: 398].
3.  **Límite de Reconexión de 5 Minutos (RNF-3):**
    *   *Justificación:* Evita que un sensor apagado genere hilos de escaneo continuo en el hardware del teléfono, previniendo descargas aceleradas de la batería por encima del 5% por hora.

---

## 5. Estrategia de Pruebas (Test Strategy)

Garantizaremos la calidad técnica del software mediante un conjunto de pruebas automatizadas que cubrirán el 100% de los requisitos críticos:

*   **Tests Unitarios (Kotlin / JUnit 5) [cite: 398]:**
    *   `verifyAverageCalculationWithSignalLoss()`: Simula una sesión de 100 segundos con lecturas de 150 lpm durante 50s, seguidas de 50s de desconexión (valores nulos). Comprueba que el promedio final sea exactamente 150 lpm y no 75 lpm. (Cubre: **RF-2.3**).
    *   `verifyReconnectionTargetConstrained()`: Simula un sensor BLE de ID `X` desconectado y verifica que el gestor de reconexión intente buscar únicamente ese ID y no el ID del atleta vecino. (Cubre: **RF-2.4**).
*   **Tests de Integración (Room DB con AndroidX Test) [cite: 398]:**
    *   `verifyAtomicSessionWriteTransaction()`: Fuerza un fallo de persistencia de métricas individuales a mitad del guardado y valida que el método anotado con `@Transaction` revierta el guardado de la sesión de manera íntegra, impidiendo registros huérfanos. (Cubre: **RF-3.2**).
    *   `verifyCascadeDeletionOfTelemetrySeries()`: Al borrar una sesión del historial, verifica que todas las series de datos asociadas en la tabla `SensorTelemetrySeriesEntity` y métricas agregadas se borren en cascada automáticamente. (Cubre: **RF-3.1**).
