# Spec 003 — Registro persistente de pulsómetros (ANT+ ↔ BLE)

## Problema
Una banda dual emite por ANT+ y BLE. La app la veía como dos aparatos y la ponía en HR1 y HR2.

## Requisitos
- R1: Un aparato físico ocupa un único hueco (HR1 o HR2), nunca los dos.
- R2: El usuario puede alternar receptores sin límite (BLE → +ANT+ → quitar pincho → apagar BLE → ANT+ ...)
  y el aparato permanece en su hueco; solo cambia la fuente (ANT+ preferente, BLE respaldo).
- R3: La app recuerda (persistente, SharedPreferences) qué nº ANT+ y qué MAC BLE son el mismo aparato.
- R4: Un segundo aparato real distinto sigue yendo a HR2.
- R5: Sin cambios en Room, Gradle, NavGraph, servicios ni en el contrato de SensorManager (`slotFlow(0/1)`).

## Fuera de alcance
Pantalla de gestión manual (fase 2, requiere análisis de NavGraph).
