# AGENTS.md — Tabata Trainer

## Proyecto
Aplicación móvil Android de entrenamiento deportivo que combina un Timer Tabata inteligente y modo Ruta Libre con sensores Bluetooth (HR1, HR2, Cadencia C1/C2) y GPS. Persistencia en Room Database local y servicios en segundo plano (Foreground Services).

- **UI / Navegación:** Jetpack Compose + Navigation Compose (`ui/`, `navigation/`).
- **Lógica & Servicios:** `WorkoutService`, `FreeRideService` y `SensorManager` (`services/`, `sensors/`).
- **Persistencia:** Room Database (`data/`, `db/`).

## Comandos (los ejecuta el usuario; el agente no, ver Clave 5)
- Compilar y verificar ensamblado: `./gradlew assembleDebug`
- Ejecutar suite de tests unitarios: `./gradlew test`
- Ejecutar tests de integración en emulador/dispositivo: `./gradlew connectedAndroidTest`

## Estilo y Convenciones
- **Lenguaje:** Kotlin 1.9+, uso estricto de Coroutines, `StateFlow` y `SharedFlow` para la reactividad de datos.
- **Arquitectura UI:** MVI / MVVM con Jetpack Compose y ViewModels (`ConfigViewModel`, `HistoryViewModel`, `SessionDetailViewModel`).
- **Idiomas:** Código, clases, métodos y variables en inglés; interfaz de usuario y mensajes de la App en español.

## Reglas Innegociables del Orquestador (5 Claves)
1. **SSOT Mandate:** Lee `docs/constitution.md` y la especificación activa dentro de `specs/00X-feature/` antes de evaluar o modificar cualquier archivo Kotlin o XML.
2. **Cero Código sin Autorización:** No agregues dependencias (Gradle), no modifiques entidades de Room DB ni alteres los contratos de `SensorManager` o servicios sin actualizar previamente la spec activa (`spec.md` y `plan.md`).
3. **Respetar la Navegación y Servicios:** Queda prohibido alterar `NavGraph.kt` o el ciclo de vida de `WorkoutService` / `FreeRideService` sin realizar un análisis de impacto en el Grafo (Graph Lineage) para evitar crashes en el back stack o fugas de memoria en segundo plano.
4. **Protección del Scope:** Trabaja estrictamente sobre la tarea activa (`T-xx`) definida en `tasks.md`. No avances a la siguiente tarea por tu cuenta.
5. **Compilación y Despliegue son del Usuario (INNEGOCIABLE):** El agente **NUNCA** ejecuta la compilación ni el despliegue. Están **prohibidos** para el agente: `./gradlew` / `gradlew.bat` / `gradle` (cualquier tarea: `assembleDebug`, `test`, `build`, etc.) y `adb install` (instalar/enviar la APK). De ello se encarga **personalmente el usuario** en su equipo y sobre sus dispositivos (S22 Ultra / Tab S9). El agente entrega el código modificado y **pide al usuario** que compile, instale y pruebe. Esta regla está reforzada por un hook `PreToolUse` en `.claude/settings.json` que bloquea de forma determinista esos comandos. (Comandos de solo lectura como `adb devices` o `adb logcat` sí están permitidos.)

## Al terminar una tarea
El agente indica qué debe compilar/probar el usuario y qué evidencia aportar (Logcat, resultado de tests). Con esa evidencia se valida el resultado antes de pedir marcar la tarea como completada.
