# INFORME DE AUDITORÍA DE CALIDAD (QA AUDIT)
## Código de Especificación: specs/001-fix-metrics/spec.md
## Rol: Lead QA de Aplicaciones Móviles - Tabata Trainer
## Fecha: 2026-09-02

---

## Introducción
Como **Lead QA de Aplicaciones Móviles**, he realizado un análisis exhaustivo de la especificación técnica `specs-001-fix-metrics-spec.md` [cite: 401, 402]. El objetivo es blindar la arquitectura del software y el comportamiento del sistema antes de comenzar la codificación [cite: 399, 402]. 

A continuación, se presenta la lista formal de hallazgos estructurada en: (1) Ambigüedades restantes, (2) Contradicciones de requisitos, (3) Casos límite no cubiertos, y (4) Conflictos con la constitución técnica del proyecto [cite: 402].

---

## 1. Ambigüedades Restantes (Resting Ambiguities)

*   **1.1. Perfiles de Atletas Temporales/Anónimos:** 
    *   *Ambigüedad:* El requisito **RF-1.1** exige un `id_deportista` (UUID), `nombre` y `apellido` [cite: 401]. Sin embargo, el feedback de diseño contempla que en ocasiones no se configuren atletas predeterminados, sino que simplemente se utilicen de forma genérica como "Atleta 1", "Atleta 2", etc.
    *   *Pregunta de QA:* ¿Debería la aplicación generar de forma automática perfiles de atleta locales "fantasma" en la base de datos Room para soportar este flujo de inicio rápido, o se requiere que el usuario cree obligatoriamente un perfil formal antes de la sesión?
*   **1.2. Telemetría de Sensores Parciales (Packs Incompletos):**
    *   *Ambigüedad:* Un "pack" se compone de 1 pulsómetro (HR) y 1 sensor de cadencia [cite: 397]. El **RF-1.2** indica que la asociación es opcional [cite: 401].
    *   *Pregunta de QA:* Si un atleta entrena usando únicamente el sensor de cadencia (sin HR), ¿cómo debe responder la base de datos en la tabla `DeviceSessionMetricsEntity`? ¿Se guardarán valores nulos (`NULL`) para la frecuencia cardíaca, o se omitirá por completo la fila en la tabla? Es vital aclarar esto para evitar excepciones de tipo `NullPointerException` al construir las gráficas de Compose.
*   **1.3. Ámbito de la Telemetría de GPS en Tabata:**
    *   *Ambigüedad:* El **RF-2.5** menciona que el sistema calcula la velocidad y distancia de GPS "MIENTRAS se registre un entrenamiento de Ruta Libre" [cite: 397].
    *   *Pregunta de QA:* ¿La sesión Tabata también registrará velocidad y ubicación mediante GPS en segundo plano (por ejemplo, si se hace un Tabata al aire libre), o la telemetría GPS está estrictamente restringida a la sesión de "Ruta Libre"?
*   **1.4. Tratamiento Matemático del Tiempo de Conexión Activa ($T_{\text{con}}$):**
    *   *Ambigüedad:* El algoritmo del **RF-2.3** calcula la media dividiendo la suma de lecturas válidas por la duración total de conexión active ($T_{\text{con}}$).
    *   *Pregunta de QA:* Si un sensor experimenta microdesconexiones continuas (Jitter de < 2s), el **CL-2** dice que se ignorará el evento de desconexión. Al ignorarlo, ¿el tiempo transcurrido durante esos micro-cortes se suma como "tiempo de conexión activa" en el denominador de la fórmula? ¿Y qué datos se asumen en el numerador para esos instantes sin señal?

---

## 2. Contradicciones entre Requisitos (Requirement Contradictions)

*   **2.1. Visualización de Gráficos de Series Temporales (RF-4.2) vs Estructura de Room (RF-3.1):**
    *   *Contradicción:* El **RF-4.2** exige que la UI de Jetpack Compose reconstruya dinámicamente gráficos temporales específicos (curvas de telemetría a lo largo del tiempo) para cada deportista. Sin embargo, la estructura propuesta en el **RF-3.1** para la tabla `DeviceSessionMetricsEntity` solo almacena métricas agregadas (valores consolidados como HR Media, HR Máxima, etc.) [cite: 397, 401].
    *   *Impacto:* Es matemáticamente imposible renderizar un gráfico de líneas temporales a partir de un único valor medio o máximo consolidado. 
    *   *Acción correctiva:* La especificación debe añadir un requisito para persistir los datos brutos por segundo (series de tiempo) en una tabla relacional como `SensorTelemetrySeriesEntity` o contemplar un almacenamiento en formato estructurado (ej. Blob o JSON indexado) vinculado a cada deportista.
*   **2.2. Detección de Desconexión (RF-2.4) vs Tolerancia al Jitter (CL-2):**
    *   *Contradicción:* El **CL-2** establece que las desconexiones de sensores de menos de 2 segundos deben "ignorarse" por software para evitar la fragmentación innecesaria. Sin embargo, a nivel físico en dispositivos Android BLE, una desconexión de hardware real suele ser notificada de forma asíncrona por el sistema operativo (`onConnectionStateChange` con estado `STATE_DISCONNECTED`) tras un periodo de timeout de supervisión del enlace que supera típicamente los 1.5 a 2 segundos.
    *   *Impacto:* Cuando el código de nuestra app se entere de la desconexión, el tiempo de tolerancia de 2 segundos ya habrá transcurrido a nivel de hardware, haciendo inviable el concepto de "ignorar la desconexión de forma inmediata" sin un búfer circular de datos temporal en memoria.

---

## 3. Casos Límite No Cubiertos (Uncovered Edge Cases)

*   **3.1. Agotamiento de Batería del Teléfono vs Desconexión del Sensor:**
    *   *Caso Límite:* Si un atleta apaga accidentalmente su sensor o este se queda sin batería a mitad de un entrenamiento largo, el **RF-2.4** inicia un bucle de reconexión automático. 
    *   *Riesgo:* Mantener escaneos de Bluetooth (BLE) de forma indefinida en segundo plano dentro del `Foreground Service` provocará un drenaje severo de la batería del dispositivo del entrenador (violando críticamente el **RNF-3** de bajo consumo) [cite: 398, 400]. 
    *   *Mitigación:* Se debe definir un timeout de reconexión máximo (ej. detener los intentos de reconexión tras 5 minutos de pérdida total de señal de un sensor específico).
*   **3.2. Cierre y Finalización de Sesión Muerta (Zombi):**
    *   *Caso Límite:* La sesión de entrenamiento ha finalizado en la pantalla principal (el temporizador llegó a cero). Sin embargo, el entrenador tarda varios minutos en pulsar el botón físico de "Guardar y Finalizar" para guardar la sesión en Room DB.
    *   *Riesgo:* Si los sensores siguen conectados y transmitiendo durante este "tiempo muerto", ¿los datos del post-entrenamiento contaminan las medias y máximas de la sesión?
    *   *Mitigación:* El `WorkoutService` debe congelar de forma estricta los buffers de recopilación de telemetría en el instante exacto en que el temporizador o la Ruta Libre se detiene, ignorando cualquier dato posterior para el procesamiento estadístico de la sesión.
*   **3.3. Pérdida de Permisos de Ubicación / Bluetooth en Ejecución:**
    *   *Caso Límite:* El sistema operativo Android permite al usuario revocar los permisos de ubicación o dispositivos cercanos en cualquier momento desde la barra de ajustes rápidos mientras la app corre en segundo plano.
    *   *Riesgo:* El `Foreground Service` colapsará instantáneamente si intenta escanear o reconectar sensores sin comprobar los permisos dinámicamente en cada ciclo de reconexión.

---

## 4. Conflictos con la Constitución del Proyecto (Constitutional Conflicts)

*   **4.1. Mezcla de Idiomas en el Esquema de Código (Sección de Persistencia):**
    *   *Conflicto:* La constitución de **tabata-trainer** exige unificación estricta de idioma en el código fuente [cite: 400]. Sin embargo, el **RF-3.1** mezcla español e inglés en las definiciones de Room (ej. `SessionEntity` con clave foránea `session_id`, pero asociando la tabla de deportistas mediante la clave mezclada `athlete_id` o `id_deportista` en el RF-1.1) [cite: 401].
    *   *Acción Correctiva:* Todo el esquema de base de datos (nombres de tablas, columnas y llaves) debe definirse obligatoriamente en inglés estricto para mantener la consistencia del código profesional.
*   **4.2. Impacto en Rendimiento por Commits Incrementales cada 30 segundos (CL-1):**
    *   *Conflicto:* El **CL-1** propone persistir datos parciales a Room DB cada 30 segundos en segundo plano para mitigar caídas de la app. 
    *   *Riesgo:* Si tenemos 4 atletas transmitiendo HR y cadencia a 1 Hz en paralelo (8 eventos por segundo), realizar transacciones de escritura constantes en disco (Room DB) cada 30 segundos desde el `Foreground Service` generará un cuello de botella de I/O de disco [cite: 398, 400]. Esto competirá con los recursos de la CPU y puede violar el requisito de rendimiento **RNF-2** (mantener la renderización a 60 FPS estables) y la modularidad de la arquitectura [cite: 400].
    *   *Acción Correctiva:* En su lugar, el `Foreground Service` debe almacenar la telemetría en un búfer circular ultraligero en memoria (Kotlin `StateFlow` / `SharedFlow`) [cite: 402], y solo realizar escrituras incrementales agrupadas (batching) en una tabla temporal de recuperación ligera, o usar un archivo caché atómico.

---

## Conclusión y Próximos Pasos

Esta especificación técnica es sumamente sólida y define con gran madurez los requisitos de negocio [cite: 401]. Sin embargo, para evitar retrabajos costosos durante la fase de desarrollo, recomiendo incorporar las soluciones a estos 11 hallazgos de QA antes de firmar la especificación e iniciar el plan técnico de implementación (`plan.md`) [cite: 402].
