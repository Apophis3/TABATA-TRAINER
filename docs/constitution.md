

# Constitución — Tabata Trainer

Principios innegociables. Toda spec, plan, tarea o modificación del código debe cumplirlos estrictamente.

1. **La Spec manda (SSOT):** Ningún comportamiento, botón o migración de datos se implementa si no está formalizado en la especificación activa (`specs/`). Si se detecta un caso límite no cubierto, se detiene el desarrollo y se actualiza la spec antes de tocar código[cite: 1, 3].
2. **Desacople Arquitectónico Estricto:** La UI (`Jetpack Compose`) no contiene lógica de negocio ni persistencia. Los servicios en segundo plano (`WorkoutService`, `FreeRideService`) coordinan la telemetría pero no manipulan vistas. Los datos se propagan mediante `StateFlow`/`SharedFlow`[cite: 4, 5].
3. **Persistencia Atómica y Segura (Room DB):** La única fuente de verdad para el historial de entrenamientos es Room Database (`SessionRoomDatabase`)[cite: 4]. Toda operación de lectura/escritura debe ser asíncrona (Coroutines/DAOs) y garantizar la integridad de métricas completas antes de cerrar la sesión[cite: 4].
4. **Resiliencia en Servicios y Hardware (BLE/ANT+):** El ciclo de vida de los `Foreground Services` y el escaneo de sensores en `SensorManager` deben tolerar desconexiones, reconexiones automáticas y cambios de pantalla sin causar crashes en la app ni fugas de memoria (memory leaks)[cite: 4, 5].
5. **Validación por Evidencias y Tests:** Cada micro-tarea (`T-xx`) termina obligatoriamente con sus tests en verde (`./gradlew test`) o evidencias explícitas de ejecución (Logcat/pantalla)[cite: 4, 5]. Queda prohibido avanzar con tests en rojo o bajo asunciones no probadas.
6. **Convención de Idioma:** Código fuente, arquitecturas, contratos, métodos y variables estrictamente en **inglés**; interfaz de usuario (UI), logs de dominio y documentación del proyecto en **español**[cite: 4].