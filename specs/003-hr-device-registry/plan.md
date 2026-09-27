# Plan: registro persistente de pulsómetros (ANT+ ↔ BLE = un solo aparato)

## Contexto
Una banda dual emite por ANT+ (`ANT:<nº>`) y BLE (`BLE:<MAC>`). `HeartRateHub` asigna hueco a cada identidad
en cuanto conecta y solo después intenta adivinar si son gemelos (media de |Δbpm| ≤ 5 en 8-10 s). Falla con
el desfase ANT/BLE y se olvida en `stop()`. Resultado: un aparato ocupa HR1 y HR2.

Requisito del usuario: el aparato puede alternar sin límite entre receptores (empezar por BLE, 10 min después
enchufar ANT+, quitar el pincho, apagar BLE, volver a ANT+...). Siempre debe verse **un aparato en un hueco
fijo**, y la app debe saber qué dos códigos (nº ANT+ y MAC BLE) le pertenecen.

## Diseño

### 1. `HrDeviceRegistry` (nuevo, `sensor/HrDeviceRegistry.kt`)
- Persistencia en SharedPreferences (JSON). **Sin Room, sin dependencias Gradle nuevas.**
- Ficha: `PhysicalHr(key: String /*uuid*/, alias, antNumber: Int?, bleAddress: String?, preferredSlot: Int?)`.
- API: `find(id)`, `link(antId, bleId)`, `unlink(key)`, `ensure(id, name)`, `setSlot(key, slot)`.
- Un `ANT:`/`BLE:` pertenece como máximo a una ficha.

### 2. Cambios en `HeartRateHub`
- `groupOf(id)` = clave de la ficha del registro (en lugar de `twins` en memoria). Se eliminan `twins`
  volátiles; `stop()` ya no borra nada persistente.
- **Hueco ligado a la ficha, no a la conexión**: `slotGroup[i]` guarda la clave física. Cuando cae ANT+ y
  queda BLE (o al revés), `tick()` solo cambia `slotSource[i]`; el hueco no cambia (ya existe la lógica
  "ANT+ si fresco, si no respaldo", se reutiliza).
- Un hueco cuyo aparato está desconectado se **reserva** (se muestra `Disconnected`) mientras dure la sesión;
  no se lo come otro aparato salvo que no haya hueco libre.
- **Cuarentena de desconocidos (~20 s)**: una identidad sin ficha no recibe hueco si coexiste con otra
  identidad del protocolo contrario, fresca, o si hay un hueco reservado con aparato caído. Durante ese tiempo
  se evalúa:
  - coincidencia de nombre/nº ANT+ en nombre BLE (ya existe);
  - correlación con desfase: mínimo de la media |Δbpm| probando desplazamientos 0..5 s (sustituye al criterio
    actual de umbral fijo).
  - Si es gemelo → `registry.link()` (se guarda para siempre) y hereda el hueco.
- **Relevo sin solapamiento** (BLE cae y aparece ANT+ desconocido, o viceversa): si hay exactamente un hueco
  reservado con aparato caído del protocolo contrario y la nueva señal es compatible en bpm con el último valor,
  hereda ese hueco (sin persistir hasta confirmarlo en un solapamiento o manualmente) → nunca sale en HR2.
- Primer uso tras instalar: basta con tener ambos receptores activos una vez; queda vinculado para siempre.

### 3. Vinculación manual (fase 2, opcional)
Pantalla Ajustes → Pulsómetros para ver/unir/separar/renombrar/elegir HR1-HR2. Requiere tocar `NavGraph.kt`
→ se deja para una spec posterior con análisis de impacto.

## Archivos
- Nuevo: `app/src/main/java/com/tuapp/tabatatrainer/sensor/HrDeviceRegistry.kt` (+ test unitario).
- Modificado: `sensor/HeartRateHub.kt` (`tick`, `detectTwins`, `groupOf`, `stop`).
- Sin cambios: `SensorManager` (sigue usando `slotFlow(0/1)`), Room, NavGraph, servicios.
- Documentación previa (AGENTS.md): `specs/003-hr-device-registry/{spec.md,plan.md,tasks.md}` y
  anotar sesión en `00_estado_proyecto_y_memoria.md`.

## Verificación
- Tests unitarios (lógica pura extraída de `tick` a una función testeable): escenarios
  BLE→+ANT→−ANT→−BLE+ANT repetidos; dos aparatos distintos; relevo sin solapamiento; desfase de 5 s.
- `./gradlew test` (lo ejecuta el usuario según su preferencia).
- Prueba real: la secuencia descrita por el usuario; en Logcat (`💓`) debe verse solo cambio de fuente
  ("Hueco HR1: ... (BLE)" → "(ANT+)") y HR2 en "Buscando" todo el rato.
