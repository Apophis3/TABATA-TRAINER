# Plan 005 — Batería de sensores

## Contratos que cambian (autorizado por esta spec)
- `sensor/SensorBattery.kt` (nuevo): `SensorBattery`, `BatteryLevel`, funciones puras `fromPercent()` / `fromAntStatus()` + parser del byte `0x2A19`.
- `HeartRateHub`: `HrDevice` gana `battery: SensorBattery?`; nueva API `slotBattery(slot): StateFlow<SensorBattery?>` (sigue a `slotSource`).
- `SensorManager`: nuevos `StateFlow<SensorBattery?>` para C1 y C2 (`cadenceBattery(slot)`).
- ~~`ConfigViewModel.BleScanStatus`~~ → sustituido (T-05) por `ui/components/SensorBatteryViewModel` (Hilt, solo lee `SensorManager.hrBattery(slot)` / `cadenceBattery(slot)`), con `rememberSensorBatteries()` y `BatteryLowWarnings()` (Toast una vez por sensor y sesión). Así no se tocan `WorkoutService` / `FreeRideService` ni se pasan parámetros por todas las capas.

## Detalles técnicos
- **BLE, cola GATT:** Android solo admite una operación GATT a la vez. La lectura de `0x2A19` se lanza en `onDescriptorWrite` (tras activar las notificaciones de HR/CSC), y la suscripción a la batería va después de esa lectura. Nada se lanza en paralelo con `writeDescriptor`.
- Lectura en `onCharacteristicRead` (API 33+ y deprecated < 33), igual que el patrón actual de `onCharacteristicChanged`.
- **ANT+:** tras `requestAccess` OK → `pcc.subscribeBatteryStatusEvent { … }` en HR (`HeartRateHub.connectAnt`, `SensorManager`) y cadencia (`AntPlusBikeCadencePcc`). Se libera con el handle, como ahora.
- Logs con prefijo `🔋`.

## Impacto (Clave 3)
- No se toca `NavGraph` ni el ciclo de vida de `WorkoutService` / `FreeRideService`: solo se leen flows nuevos desde la UI.
- Riesgo principal: meter una operación GATT de más y romper la suscripción HR/CSC → mitigado con la secuencia en `onDescriptorWrite` y tests de validación real (T-06).

## UI
- `SensorBar` (Home) y las filas de sensores de `WorkoutScreen` / `FreeRideScreen`: composable compartido `BatteryBadge(battery)` en `ui/components/SharedComponents.kt`.
