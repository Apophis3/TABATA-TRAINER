# ESPECIFICACIÓN TÉCNICA: Solución de Métricas Incompletas en Sesiones Multiusuario
## Código de Especificación: specs/001-fix-metrics/spec.md
## Estado: Listo para Revisión / QA Audit

---

## 1. Contexto y Objetivo del Negocio

El proyecto **tabata-trainer** es una aplicación móvil Android diseñada para el entrenamiento deportivo inteligente en tiempo real [cite: 397]. Combina temporizadores de intervalos Tabata con un sistema de seguimiento en Ruta Libre [cite: 397]. Para lograr un control profesional del rendimiento en entrenamientos grupales, la aplicación debe actuar como un "Hub Central" que lea y procese de manera concurrente la telemetría de hasta **4 deportistas simultáneos** (cada uno equipado con un "pack" opcional de sensores: 1 pulsómetro de frecuencia cardíaca (HR) y 1 sensor de cadencia) [cite: 397].

**El objetivo de esta especificación (001-fix-metrics) es:**
1. Solucionar el problema de las métricas incompletas, erróneas o truncadas al finalizar sesiones de Tabata o Ruta Libre [cite: 401].
2. Diseñar un esquema de persistencia local robusto y escalable en **Room Database** que permita guardar un único registro de sesión global (Tabata o Ruta Libre) asociado de forma relacional (1:N) a las métricas e historial de rendimiento individual de cada deportista participante [cite: 398].
3. Implementar un algoritmo exacto para el cálculo de promedios de frecuencia cardíaca, cadencia y velocidad (GPS) basado **exclusivamente en el tiempo real de conexión activa** de cada sensor, impidiendo que las desconexiones temporales penalicen o desvíen las métricas reales del atleta.

---

## 2. Historias de Usuario

*   **HU-1 (Configuración de Atletas y Packs):**
    *   *Como* entrenador o administrador,
    *   *quiero* poder crear perfiles persistentes de atletas (con Nombre y Apellido) y preasociarles de manera opcional un pack de sensores predeterminado,
    *   *para* reducir drásticamente la fricción y el tiempo de emparejamiento antes de cada entrenamiento.
*   **HU-2 (Control de Pre-sesión Secuencial):**
    *   *Como* entrenador,
    *   *quiero* una pantalla primaria de pre-sesión que me permita ir encendiendo, detectando y asociando de uno en uno los packs de sensores (Pulsómetro + Cadencia) a los respectivos deportistas registrados (máximo 4),
    *   *para* verificar que todo el hardware esté conectado de forma estable antes de dar inicio a la actividad física.
*   **HU-3 (Visualización en Tiempo Real de Datos Individuales):**
    *   *Como* atleta o entrenador,
    *   *quiero* ver en la pantalla de entrenamiento el temporizador global pero con las métricas en tiempo real de cada uno de los deportistas activos de forma aislada,
    *   *para* supervisar el desempeño grupal sin mezclar la información de los participantes.
*   **HU-4 (Cálculo Inteligente de Promedios de Señal):**
    *   *Como* atleta o entrenador,
    *   *quiero* que si mi sensor de frecuencia cardíaca o de cadencia pierde señal a mitad de sesión o se desconecta temporalmente, la aplicación intente reconectarlo por su canal de origen y calcule mis medias finales basándose únicamente en el tiempo de conexión activa,
    *   *para* que mis estadísticas históricas reflejen mi rendimiento real sin ser contaminadas con valores en cero o nulos generados durante el fallo de conexión.
*   **HU-5 (Análisis Histórico Individualizado):**
    *   *Como* entrenador o atleta,
    *   *quiero* acceder al Historial de entrenamientos, seleccionar una sesión grupal pasada y poder elegir a través de un selector rápido entre Atleta 1, Atleta 2, Atleta 3 y Atleta 4 para visualizar de forma aislada sus estadísticas completas (HR Máx/Media, Cadencia, Velocidad) y gráficos temporales específicos,
    *   *para* realizar un análisis detallado del progreso de cada atleta por separado.

---

## 3. Requisitos Funcionales (EARS en Español)

### 3.1. Gestión de Perfiles y Pre-sesión
*   **RF-1.1 (Registro de Deportistas):** El sistema DEBERÁ permitir la persistencia en base de datos de perfiles de deportistas compuestos por `id_deportista` (UUID), `nombre` (Texto) y `apellido` (Texto).
*   **RF-1.2 (Preasignación de Sensores):** El sistema DEBERÁ permitir asociar opcionalmente la dirección MAC de un pulsómetro (BLE o ID ANT+) y la dirección MAC de un cadenciómetro a un perfil de deportista.
*   **RF-1.3 (Flujo de Emparejamiento):** CUANDO se configure la pre-sesión en la pantalla primaria, el sistema DEBERÁ guiar al entrenador a registrar los dispositivos secuencialmente de uno en uno (Pack 1 a Deportista 1, Pack 2 a Deportista 2, etc.), mostrando visualmente el estado de escaneo, conexión y tecnología de señal (especificando si es BLE o ANT+) de cada sensor.

### 3.2. Procesamiento de Telemetría en Segundo Plano (WorkoutService / FreeRideService)
*   **RF-2.1 (Aislamiento de Telemetría):** MIENTRAS la sesión de entrenamiento esté activa, el `Foreground Service` DEBERÁ aislar completamente los buffers de datos recibidos de cada uno de los 8 posibles sensores activos (hasta 4 de HR y 4 de Cadencia) sin mezclar registros en memoria.
*   **RF-2.2 (Registro de Estado de Conexión):** El sistema DEBERÁ monitorear de manera continua el estado de conexión de cada dispositivo activo y registrar marcas de tiempo exactas (`connection_events`) cada vez que ocurra una desconexión o reconexión exitosa.
*   **RF-2.3 (Algoritmo de Promedio de Sensores):** CUANDO finalice la sesión, el procesador de datos DEBERÁ calcular la media de frecuencia cardíaca (HR) y la media de cadencia usando únicamente los datos correspondientes a las ventanas temporales en que el sensor estuvo activamente conectado:
    $$\text{Métrica Media} = \frac{\sum_{t \in T_{\text{con}}} \text{Valor}(t)}{\text{Duración Total de Conexión } (T_{\text{con}})}$$
    donde $T_{\text{con}}$ excluye todos los periodos de desconexión o de señal nula constatados por el monitor de eventos de conexión.
*   **RF-2.4 (Reconexión Unidireccional Acotada):** SI un sensor pierde conexión, el sistema DEBERÁ iniciar un bucle de reconexión automática acotado estrictamente a la tecnología de origen (BLE o ANT+) y al puerto/banda física de emparejamiento original del sensor, impidiendo saltar a otras bandas de atletas activos.
*   **RF-2.5 (Telemetría de Ruta Libre y GPS):** MIENTRAS se registre un entrenamiento de Ruta Libre con GPS activo, el sistema DEBERÁ calcular la velocidad y registrar la distancia basándose de forma exclusiva en las coordenadas capturadas cuando la precisión de la señal GPS sea válida (descartando saltos bruscos o pérdida total de satélites).

### 3.3. Persistencia de Datos (Room Database)
*   **RF-3.1 (Estructura Relacional de Sesión):** El sistema DEBERÁ persistir la información en Room DB mediante un esquema relacional de uno a muchos (1:N):
    *   Una tabla única de sesión grupal (`SessionEntity`) que guarde la duración total, intervalos Tabata y metadatos del evento deportivo.
    *   Una tabla de métricas individuales por dispositivo (`DeviceSessionMetricsEntity`) vinculada a `SessionEntity` mediante una clave foránea (`session_id`) y a la tabla de deportistas mediante (`athlete_id`).
*   **RF-3.2 (Atomicidad de Guardado):** El sistema DEBERÁ ejecutar el proceso de guardado de la sesión global y el desglose de métricas individuales dentro de una única transacción de Room DB (`@Transaction`), garantizando que una falla de escritura en un deportista no deje la base de datos en un estado inconsistente o huérfano.

### 3.4. Interfaz de Usuario y Consulta Histórica (Jetpack Compose)
*   **RF-4.1 (Selector de Deportistas en Historial):** CUANDO el usuario navegue al detalle de una sesión guardada en el Historial, la pantalla DEBERÁ presentar un componente de pestañas o selector rápido que contenga los nombres de los deportistas participantes (ej: "Atleta 1: Juan Pérez", "Atleta 2: Carlos Gómez").
*   **RF-4.2 (Actualización Dinámica de Gráficos):** Al seleccionar un deportista en la pantalla de detalle, la UI de Jetpack Compose DEBERÁ reconstruir dinámicamente las métricas específicas (HR Máxima, HR Media, Cadencia Media, Velocidad Media) y renderizar de forma aislada las curvas y gráficos de telemetría correspondientes a ese deportista en esa sesión, sin solapamiento de datos de otros atletas.

---

## 4. Requisitos No Funcionales (RNF)

*   **RNF-1 (Modularidad del Contexto de Sensores):** Todo el procesamiento de señales, gestión de sockets BLE/ANT+ y cálculo estadístico temporal debe ejecutarse estrictamente dentro del contexto del `Foreground Service` (Workout/FreeRide), totalmente independiente del ciclo de vida de las Actividades de UI en Compose, garantizando que el cierre o bloqueo de la pantalla de la app no destruya el cálculo de promedios [cite: 398, 400].
*   **RNF-2 (Desempeño y Concurrencia):** El sistema de despacho de eventos de Kotlin (`StateFlow` / `SharedFlow`) debe ser capaz de procesar hasta 8 señales de sensores en paralelo con frecuencias de actualización de 1 Hz cada una, manteniendo el hilo de renderizado de la interfaz por encima de los 60 FPS estables.
*   **RNF-3 (Bajo Consumo de Batería):** El rastreo y reconexión en segundo plano de múltiples sensores no debe incrementar el consumo de batería del dispositivo móvil por encima del 5% adicional por hora de sesión activa.
*   **RNF-4 (Seguridad y Privacidad):** El archivo JSON local con las configuraciones y los engramas temporales de emparejamiento debe persistirse en el almacenamiento interno de la app con permisos privados de lectura/escritura del sistema operativo, permaneciendo totalmente invisible a otras aplicaciones [cite: 398].

---

## 5. Casos Límite y de Error

*   **CL-1 (Cierre Repentino / Caída del Sistema Operativo):** Si el sistema operativo destruye la app por falta de memoria RAM o batería, el `Foreground Service` debe haber realizado commits incrementales de telemetría a una tabla temporal en Room DB cada 30 segundos, permitiendo recuperar el historial parcial de los atletas al reiniciar la app.
*   **CL-2 (Desconexión Intermitente Rápida - Jitter):** Si un sensor se desconecta y reconecta en un lapso menor a 2 segundos, el sistema debe ignorar el evento de desconexión para efectos de cálculo de promedios, evitando la fragmentación innecesaria de los segmentos de conexión activa.
*   **CL-3 (Pérdida Total de Señal del Sensor):** Si un sensor se desconecta al inicio de la sesión y nunca más vuelve a emitir datos, el sistema registrará valores nulos o "sin datos" para ese atleta específico, pero permitirá que las métricas de los atletas restantes con sensores estables continúen calculándose con absoluta normalidad.
*   **CL-4 (Conflicto de Direcciones MAC / ANT+ ID):** Si en la fase de pre-sesión se intenta emparejar la misma dirección física de sensor a dos deportistas distintos en el mismo entrenamiento, el sistema bloqueará la acción y emitirá una alerta visible en pantalla antes de dar inicio a la sesión.

---

## 6. Fuera de Alcance (Out of Scope)

*   Sincronización en tiempo real con servidores en la nube o bases de datos externas. Toda la persistencia de perfiles y sesiones se ejecuta localmente en el dispositivo mediante Room DB [cite: 398].
*   Soporte para más de 4 perfiles de atletas activos simultáneamente en una sola sesión Tabata o Ruta Libre (MVP limitado a 4 packs físicos de sensores) [cite: 401].
*   Funciones de compartición de métricas individuales en redes sociales directamente desde la pantalla de detalle de la sesión.

---

## 7. Criterios de Aceptación (Definición de Hecho)

La especificación **001-fix-metrics** se considerará completada de forma exitosa cuando:
1. **Prueba de Selección y Renderizado Histórico:** En el módulo de Historial de la aplicación móvil, tras cargar una sesión en la que participaron al menos 2 deportistas, al pulsar sobre el selector de cada atleta se actualizan correctamente las gráficas de frecuencia cardíaca y cadencia sin solaparse ni arrojar errores de renderizado.
2. **Prueba de Algoritmo de Desconexión Activa:** Durante un test de integración de 10 minutos con 2 deportistas conectados, se apaga voluntariamente el sensor de HR del Deportista 1 en el minuto 5. Al finalizar la sesión, Room DB almacena para el Deportista 1 un promedio de HR correspondiente únicamente a los primeros 5 minutos registrados, mientras que el Deportista 2 posee sus promedios calculados sobre los 10 minutos completos de su entrenamiento.
3. **Prueba de Atomicidad de Guardado:** Se simula de forma programática un fallo de persistencia (como una violación de llave foránea intencional en la tabla `DeviceSessionMetricsEntity` de un deportista). El orquestador de Room DB ejecuta el `@Transaction` y comprueba que ni la sesión global ni las métricas parciales de los otros atletas se guarden de forma inconsistente, revirtiendo la base de datos al estado anterior de manera íntegra.
