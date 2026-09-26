# AGENTS.md — Tabata Trainer

## Proyecto
Aplicación móvil Android de entrenamiento deportivo que combina un Timer Tabata inteligente y modo Ruta Libre con sensores Bluetooth (HR1, HR2, Cadencia C1/C2) y GPS[cite: 4, 5]. Persistencia en Room Database local y servicios en segundo plano (Foreground Services)[cite: 4].

- **UI / Navegación:** Jetpack Compose + Navigation Compose (`ui/`, `navigation/`)[cite: 4].
- **Lógica & Servicios:** `WorkoutService`, `FreeRideService` y `SensorManager` (`services/`, `sensors/`)[cite: 4].
- **Persistencia:** Room Database (`data/`, `db/`)[cite: 4].

## Comandos de Verificación
- Compilar y verificar ensamblado: `./gradlew assembleDebug`
- Ejecutar suite de tests unitarios: `./gradlew test`
- Ejecutar tests de integración en emulador/dispositivo: `./gradlew connectedAndroidTest`

## Estilo y Convenciones
- **Lenguaje:** Kotlin 1.9+, uso estricto de Coroutines, `StateFlow` y `SharedFlow` para la reactividad de datos.
- **Arquitectura UI:** MVI / MVVM con Jetpack Compose y ViewModels (`ConfigViewModel`, `HistoryViewModel`, `SessionDetailViewModel`)[cite: 4].
- **Idiomas:** Código, clases, métodos y variables en inglés; interfaz de usuario y mensajes de la App en español[cite: 4].

## Reglas Innegociables del Orquestador (5 Claves)
1. **SSOT Mandate:** Lee `docs/constitution.md` y la especificación activa dentro de `specs/00X-feature/` antes de evaluar o modificar cualquier archivo Kotlin o XML.
2. **Cero Código sin Autorización:** No agregues dependencias (Gradle), no modifiques entidades de Room DB ni alteres los contratos de `SensorManager` o servicios sin actualizar previamente la spec activa (`spec.md` y `plan.md`)[cite: 4].
3. **Respetar la Navegación y Servicios:** Queda prohibido alterar `NavGraph.kt` o el ciclo de vida de `WorkoutService` / `FreeRideService` sin realizar un análisis de impacto en el Grafo (Graph Lineage) para evitar crashes en el back stack o fugas de memoria en segundo plano[cite: 4, 5].
4. **Protección del Scope:** Trabaja estrictamente sobre la tarea activa (`T-xx`) definida en `tasks.md`. No avances a la siguiente tarea por tu cuenta.

## Al Terminar cualquier Tarea (Validación por Evidencia)
- Ejecutar `./gradlew test` (o la prueba específica solicitada) y adjuntar en la respuesta los logs de Logcat / consola o evidencias de ejecución antes de solicitar marcar la tarea como completada.