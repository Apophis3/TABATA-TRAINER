# Historial de cambios — Tabata Trainer

Formato de versión: `MAJOR.MINOR.BUILD`. `BUILD` sube solo en cada APK generado
(ver `version.properties`); `MAJOR`/`MINOR` se suben a mano en versiones importantes.
La versión instalada se ve en la pantalla de inicio (debajo de "S. CELIS") y en el
nombre del APK: `apk/TabataTrainer-<versión>-debug.apk`.

Al generar un APK nuevo, añadir su entrada arriba del todo.

---

## 1.6.6 — 2026-09-26
- **Versionado:** número de versión automático en cada compilación, visible en la
  pantalla de inicio y en el nombre del APK. Nuevo `CHANGELOG.md`.
- **Ruta Libre:** la fila de pasos ya no sale cortada en vertical; ahora es una franja
  compacta (Pasos · Pasos/min · Zancada) y también aparece en horizontal.

## 1.6 (sin número de build) — 2026-09-25
- **Podómetro en ruta:** pasos en vivo, pasos/min y zancada media (distancia GPS / pasos);
  los pasos en pausa no cuentan. Se guardan en la sesión (base de datos v9) y se
  muestran en el detalle de sesión.
- **Pulsómetros:** gestor único de pulsómetros ANT+ / BLE.
- **Estadísticas:** HR media, velocidad media y gráfica de HR completa.
- **Mantenimiento:** actualización de toolchain y librerías.

## 1.5-GPS — anterior
- Versión de partida del repositorio: Timer Tabata, Ruta Libre con GPS y sensores
  HR1/HR2/Cadencia, historial de sesiones.
