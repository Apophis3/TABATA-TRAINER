# Spec 005 — Batería de los sensores (BLE y ANT+)

## Objetivo
Mostrar la batería de cada sensor conectado (HR1, HR2, C1, C2) en la **Home** y **durante el entrenamiento** (Tabata y Ruta Libre), para avisar antes de que un sensor se apague a mitad de sesión.

## Requisitos
- RF-1 (BLE): al conectar, si el aparato tiene Battery Service `0x180F`, leer Battery Level `0x2A19` (0–100 %) y suscribirse a notificaciones si las admite. Si no tiene el servicio → batería "desconocida" (sin error, sin icono).
- RF-2 (ANT+): suscribirse a `subscribeBatteryStatusEvent` del PCC (HR y cadencia). Se guarda el estado (NEW / GOOD / OK / LOW / CRITICAL) y el voltaje si viene. El dato puede tardar hasta ~1 min en llegar; hasta entonces "desconocida".
  - **Limitación descubierta (T-03):** en `antpluginlib 3.9`, `AntPlusHeartRatePcc` hereda de `AntPlusLegacyCommonPcc`, que **no** tiene `subscribeBatteryStatusEvent`. La batería por ANT+ solo existe para cadencia (`AntPlusBikeSpdCadCommonPcc`). Un pulsómetro conectado solo por ANT+ queda "desconocida".
- RF-3 (modelo): `SensorBattery(percent: Int?, level: BatteryLevel, voltage: Float?)`, con `BatteryLevel = GOOD | OK | LOW | CRITICAL | UNKNOWN`.
  - Desde %: ≥50 GOOD, 20–49 OK, 10–19 LOW, <10 CRITICAL.
  - Desde ANT+: NEW/GOOD → GOOD, OK → OK, LOW → LOW, CRITICAL → CRITICAL, resto → UNKNOWN.
- RF-4 (HR con relevo/vínculo, spec 003): la batería de HR1/HR2 es la del **dispositivo que alimenta el hueco en ese momento** (`slotSource`).
- RF-5 (UI Home): en `SensorBar`, junto a HR y CAD, icono de batería + "%" (BLE) o icono de estado (ANT+). Color: verde GOOD, amarillo OK, naranja LOW, rojo CRITICAL. Sin dato → no se pinta.
- RF-6 (UI entrenamiento): en `WorkoutScreen` y `FreeRideScreen`, icono pequeño de batería junto a cada lectura de HR/cadencia. Si pasa a LOW o CRITICAL durante la sesión → se muestra un aviso **una sola vez** por sensor y sesión ("Batería baja en HR1").
- RF-7b (BLE): si el Bluetooth estaba apagado al arrancar el hub y se enciende después, el escaneo BLE arranca solo, sin pulsar actualizar.
- RF-7: El dato de batería es solo informativo: nunca desconecta, reconecta ni bloquea nada. Fallos de lectura → UNKNOWN y log `🔋`.
- RF-8: Sin cambios en Room ni en Gradle (el SDK ANT+ ya está incluido). No se guarda la batería en la sesión.

## Fuera de alcance
- Histórico de batería, notificaciones del sistema, batería del propio móvil.
