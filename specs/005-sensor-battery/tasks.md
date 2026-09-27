# Tasks 005
- [x] T-01 `SensorBattery` + mapeos puros + parser `0x2A19` + `SensorBatteryTest`
- [x] T-02 BLE HR (`HeartRateHub`): leer/suscribir batería tras `onDescriptorWrite`; `slotBattery(slot)` siguiendo `slotSource` (validado: XOSS 75 %, Decathlon 100 %)
- [x] T-02b Arreglo: si el Bluetooth se enciende con el hub en marcha, el escaneo BLE arranca solo (comprobación cada 5 s en el `tick`)
- [x] T-03 ANT+ HR: el SDK 3.9 no da batería de pulso → opción A: el hueco usa la batería del gemelo BLE de la misma banda
- [ ] T-04 Cadencia BLE + ANT+ (`SensorManager`): `cadenceBattery(slot)` — código escrito, pendiente de validación del usuario
- [ ] T-05 (código escrito, pendiente de validación) UI: `BatteryBadge` en Home (`SensorBar`) + `WorkoutScreen` / `FreeRideScreen` + aviso único LOW/CRITICAL
- [ ] T-06 Validación real del usuario (BLE y ANT+), Logcat `🔋`
