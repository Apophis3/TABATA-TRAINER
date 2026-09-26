# LISTA DE TAREAS INTERACTIVAS (Vertical Slices) - v2
## Código de Especificación: specs/001-fix-metrics/tasks.md
## Estado: T1.1 Completado - En Progreso

Este documento divide el plan de implementación técnica para **tabata-trainer** en rebanadas verticales (*vertical slices*) de corta duración (máximo 20-30 minutos cada una) en orden estricto de dependencias: **Persistencia / Room DB ➔ Lógica del Servicio en Background ➔ Componentes de Interfaz UI** [cite: 398, 403].

---\n
### Fase 1: Capa de Persistencia y Estructura de Datos (Room Database)

- [x] **T1.1: Entidad de Deportista (`AthleteEntity`) y DAO Base**
  * **Descripción:** Implementar la tabla de deportistas en Room con soporte para perfiles locales (`id` como clave primaria UUID, `firstName`, `lastName`) y preasignación opcional de MAC addresses físicas para los sensores de pulsómetro y cadencia (BLE o ANT+) [cite: 398, 401].
  * **RF Cubiertos:** RF-1.1, RF-1.2
  * **Hecho cuando:** El test unitario `AthleteDaoTest` compila y valida la inserción, consulta y eliminación de perfiles de deportista con direcciones de hardware preestablecidas.

- [ ] **T1.2: Entidad Relacional de Métricas de Sesión (`DeviceSessionMetricsEntity`)**
  * **Descripción:** Implementar la entidad que almacena las métricas individuales consolidadas de la sesión para cada deportista. Debe contener claves foráneas vinculadas a `SessionEntity` (`session_id`) y a `AthleteEntity` (`athlete_id`), con opción de valores nulos si el atleta entrenó con un pack incompleto de sensores [cite: 398, 401].
  * **RF Cubiertos:** RF-3.1
  * **Hecho cuando:** La suite de pruebas de persistencia verifica que se puedan registrar métricas de un dispositivo nulo (ej. solo pulsómetro activo) sin violar restricciones de esquema en Room.

- [ ] **T1.3: Entidad de Series de Tiempo de Telemetría (`SensorTelemetrySeriesEntity`)**
  * **Descripción:** Crear la tabla para el registro detallado (1 Hz) de la frecuencia cardíaca, cadencia y velocidad a lo largo de la sesión para cada atleta individual. Claves requeridas: `telemetry_id` (Auto-increment), `session_id`, `athlete_id`, `timestamp` (Unix epoch), `heartRate`, `cadence`, `velocity` [cite: 397, 401].
  * **RF Cubiertos:** RF-3.1, RF-4.2 (Resolución de Hallazgo QA-2)
  * **Hecho cuando:** Se ejecuta un test que simula la inserción of 600 puntos de datos para un atleta en una sesión y comprueba que se recuperan ordenados cronológicamente sin pérdidas.

- [ ] **T1.4: Implementación de Transacciones Atómicas en `SessionDao`**
  * **Descripción:** Crear la función `@Transaction suspend fun insertCompleteSession(...)` para guardar la sesión global, el conjunto de métricas resumidas y los historiales de series de tiempo de forma unificada [cite: 398, 401].
  * **RF Cubiertos:** RF-3.2
  * **Hecho cuando:** Un test de integración provoca de manera intencionada un error de inserción en la tabla de series temporales y se verifica que Room realiza un rollback completo, dejando la sesión sin persistir parcialmente [cite: 398].

---\n
### Fase 2: Lógica de Captura, Procesamiento e Hilos (WorkoutService / FreeRideService)

- [ ] **T2.1: Aislamiento de Buffers de Telemetría Multiusuario en Memoria**
  * **Descripción:** Implementar en el servicio en segundo plano buffers concurrentes basados en Kotlin `StateFlow` o `SharedFlow` para canalizar las lecturas de hasta 8 sensores de forma aislada e independiente para los 4 atletas posibles [cite: 397, 398].
  * **RF Cubiertos:** RF-2.1
  * **Hecho cuando:** Al inyectar telemetría simultánea de 4 pulsómetros de prueba en el servicio, los logs de consola demuestran que los datos se almacenan en sus respectivos flujos sin colisiones de memoria.

- [ ] **T2.2: Implementación de Temporizador de Conexión Activa ($T_{\\text{con}}$)**
  * **Descripción:** Desarrollar el sistema que rastree el estado de conexión de cada dispositivo. Al desconectarse, pausa la acumulación de tiempo activo del sensor; al reconectarse, la reanuda mediante marcas de tiempo de sistema precisas [cite: 398].
  * **RF Cubiertos:** RF-2.2
  * **Hecho cuando:** El test unitario de temporizador verifica que si un sensor está desconectado durante 3 minutos de una sesión de 10 minutos, el valor acumulado de conexión activa ($T_{\\text{con}}$) es exactamente de 7 minutos (420 segundos).

- [ ] **T2.3: Implementación del Algoritmo de Cálculo de Promedios Reales**
  * **Descripción:** Codificar la fórmula matemática para obtener las medias aritméticas de la frecuencia cardíaca y cadencia final, sumando los valores instantáneos solo dentro de los intervalos temporales donde el sensor estuvo conectado de forma estable y dividiendo entre el total de muestras válidas.
  * **RF Cubiertos:** RF-2.3
  * **Hecho cuando:** Un test de regresión con datos sintéticos que contiene un 50% de lecturas a cero debido a una desconexión calcula la HR media ignorando de forma absoluta el periodo inactivo.

- [ ] **T2.4: Bucle de Reconexión Acotada con Bloqueo de Canal**
  * **Descripción:** Implementar en el gestor de Bluetooth (`SensorManager`) un ciclo de reconexión automático que solo intente buscar la MAC registrada en el protocolo de origen (BLE o ANT+) y que incluya un timeout de detención automática tras 5 minutos de inactividad física para proteger el consumo de batería [cite: 398, 400].
  * **RF Cubiertos:** RF-2.4 (Resolución de Hallazgo QA-3)
  * **Hecho cuando:** Al apagar un pulsómetro BLE emparejado, el servicio intenta reconectar por BLE durante 5 minutos de forma exclusiva y desiste de manera ordenada al alcanzar el timeout, emitiendo un log de desconexión definitiva.

- [ ] **T2.5: Filtro de Precisión GPS para Ruta Libre**
  * **Descripción:** Agregar una capa de filtrado determinista en el receptor de coordenadas de `FreeRideService` para ignorar los reportes de localización del sistema Android cuya precisión sea baja (ej: `location.accuracy > 20 meters`) [cite: 397].
  * **RF Cubiertos:** RF-2.5
  * **Hecho cuando:** Al inyectar una coordenada simulada de error de precisión masiva, el servicio la descarta de inmediato para los cálculos de velocidad instantánea y distancia de ruta libre.

---\n
### Fase 3: Capa de Interfaz de Usuario y Detalle (Jetpack Compose)

- [ ] **T3.1: Pantalla de Pre-sesión y Hub de Emparejamiento Secuencial**
  * **Descripción:** Diseñar en Jetpack Compose el flujo visual interactivo para asociar deportistas a los packs físicos activos. Debe guiar al entrenador a encender y comprobar el estado de conexión individual y el protocolo activo (BLE/ANT+) antes de desbloquear el botón de inicio de entrenamiento [cite: 397].
  * **RF Cubiertos:** RF-1.3
  * **Hecho cuando:** El flujo visual se completa manualmente con éxito en emulador, mostrando alertas visuales si se intenta emparejar el mismo sensor físico a dos atletas concurrentes.

- [ ] **T3.2: Componente de Selección Multi-Atleta en el Historial**
  * **Descripción:** Implementar en la pantalla de detalle del Historial un componente interactivo de tipo pestañas (*Tabs*) que lea los deportistas participantes registrados en `DeviceSessionMetricsEntity` y genere botones dinámicos para cambiar entre ellos [cite: 397].
  * **RF Cubiertos:** RF-4.1
  * **Hecho cuando:** Tras cargar una sesión de prueba con 3 deportistas, la UI renderiza exactamente 3 pestañas personalizadas con los nombres y apellidos de los atletas participantes.

- [ ] **T3.3: Renderizado Dinámico de Telemetría y Gráficos**
  * **Descripción:** Vincular el estado de selección de pestañas del Historial con los gráficos de curvas de rendimiento. Al cambiar de atleta, la vista debe recomponerse, consultar `SensorTelemetrySeriesEntity` para ese `athlete_id` y repintar los gráficos de forma aislada [cite: 397].
  * **RF Cubiertos:** RF-4.2
  * **Hecho cuando:** Al interactuar en el emulador con la pestaña de \"Atleta 2\" tras visualizar al \"Atleta 1\", los gráficos lineales se actualizan al instante para mostrar la curva del Atleta 2, sin tirones visuales ni solapamientos de datos en pantalla.
