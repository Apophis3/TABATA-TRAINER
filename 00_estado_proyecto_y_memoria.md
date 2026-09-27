# Estado del proyecto y memoria de sesiones — Tabata Trainer

Bitácora de trabajo. Cada sesión se anota aquí: qué se tocó, por qué, en qué quedó
y qué falta. Complementa a `01_historico_errores.md` (errores resueltos) y a la
memoria privada de Claude. **Lo más reciente arriba.**

---

## 2026-09-27 — Pulsómetro dual: fusión ANT+/BLE y etiqueta de protocolo

### Contexto
En la Tab 9, una única banda dual aparecía como HR1 **y** HR2 (dos líneas casi
calcadas). Trabajo sobre `sensor/HeartRateHub.kt` (Fase C, gestor único de HR).

### Cambios hechos
1. **Fusión de gemelos por media móvil** (`detectTwins`): antes comparaba bpm
   instantáneo con igualdad estricta (18/20), que falla al variar la HR por el
   desfase ANT+/BLE. Ahora usa ventana de ~10 muestras y fusiona si la diferencia
   media de bpm ≤ 5 (`TWIN_WINDOW`, `TWIN_MIN_SAMPLES`, `TWIN_MAX_AVG_DIFF`).
   → Resultado verificado: una banda dual ocupa un solo hueco (HR1). ✅
2. **Etiqueta de protocolo persistente** (`tick`): el hub emitía `Connected` una
   sola vez y luego muchos `Value`; con `replay=1` un suscriptor tardío solo veía
   el `Value` y perdía el protocolo → badge vacío. Ahora reemite `Connected` cada
   tick. → Verificado: la etiqueta ya aparece bajo HR1. ✅

### Abierto / en investigación
- **ANT+ no engancha en la última sesión**: la etiqueta de HR1 sale **BLE** y al
  apagar el Bluetooth se queda sin señal (no hay ANT+ de respaldo). El hub ya
  prioriza ANT+ si está fresco; el problema es que el ANT+ no conecta ahora
  (antes sí: en la 1ª captura HR2 tenía ANT+). **Pendiente: capturar logcat**
  (filtro `ANT|BLE|Hueco|aparato`) iniciando Tabata → 20 s → apagar BT, para ver
  si el ANT+ no detecta, no conecta o llega stale.

### Notas de flujo de trabajo (acordadas hoy)
- Compilar/reinstalar lo hace el usuario (Ctrl+Shift+O). Claude solo avisa
  "compila y reinstala".
- `adb` no está en el PATH: usar
  `C:\Users\APO\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

## 2026-09-27 — Spec 003: registro persistente de pulsómetros (ANT+ ↔ BLE)
- Problema: una banda dual salía en HR1 (ANT+) y HR2 (BLE). Causa: se asignaba hueco
  antes de decidir si eran gemelos, la comparación (media |Δbpm| ≤ 5) fallaba por el
  desfase ANT/BLE y el vínculo se borraba en cada `stop()`.
- Solución (`specs/003-hr-device-registry/`):
  - `HrDeviceRegistry` (SharedPreferences, sin Room): guarda para siempre nº ANT+ ↔ MAC BLE.
  - `HeartRateHub`: el hueco pertenece al aparato físico; cuarentena de 30 s para un
    desconocido que podría ser la otra cara de uno asignado; comparación con desfase
    (±5 s, exige variación de HR para guardar); relevo sin solapamiento (quitas pincho
    ANT+/apagas BLE y la otra señal hereda el hueco).
  - Tests: `HrDeviceBookTest`.
- Pendiente (T-04): compilar, `./gradlew test` y probar la secuencia BLE → +ANT+ →
  −ANT+ → −BLE/+ANT+ mirando Logcat `💓` (debe salir "vinculado ... guardado").
- Prueba en 1.6.25 (logcat): el registro no llegaba a actuar porque
  (a) ANT+ 56551 se encontraba pero requestAccess daba SEARCH_TIMEOUT y luego
  CHANNEL_NOT_AVAILABLE / ALL_CHANNELS_IN_USE: la MultiDeviceSearch continua del hub
  acaparaba la radio del pincho (también bloqueaba la cadencia ANT+);
  (b) BLE tras desconectar (status 22) el reintento connectGatt quedaba colgado y el
  escaneo ignoraba el aparato para siempre.
  Arreglo: búsqueda ANT+ por ventanas (15 s busca / 15 s pausa), se cierra antes de
  conectar, recuperación de aparatos perdidos volviendo a buscar; vigilante BLE de 20 s
  que cierra la conexión colgada para que el escaneo la reenganche.

## Sesión 2026-09-27 — Spec 004: Home responsive
- `HomeScreen.kt` rehecho con `BoxWithConstraints`: 4 layouts (móvil/tablet × vertical/horizontal).
  Arreglado: la tablet en vertical usaba el layout horizontal (`screenWidthDp > 600`).
- Vertical: las tarjetas reparten el alto (sin huecos); scroll si alto < 620dp.
  Horizontal: panel lateral + tarjetas lado a lado a todo el alto.
- Pendiente (T-02): compilar e instalar (usuario) y validar en S22+ y Tab S9.
- Revisión 2: actividades en la misma fila; fila 2 = Última actividad (datos reales) +
  Esta semana (desde lunes: sesiones, tiempo, km, FC media); fila 3 = Historial | Perfil | Cerrar.
  Nuevo `HomeViewModel` (solo lectura de `SessionDao.getAllSessions()`) + `HomeSummaryTest`.
