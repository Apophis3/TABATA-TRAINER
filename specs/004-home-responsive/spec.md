# Spec 004 — Pantalla principal responsive (solo estética)

## Objetivo
Rediseñar el layout de `HomeScreen` para que no queden huecos vacíos y se adapte a:
móvil vertical, móvil horizontal, tablet vertical y tablet horizontal (S22+ / Tab S9).

## Requisitos
- RF-1: La decisión de layout se basa en ancho/alto reales disponibles (`BoxWithConstraints`), no solo en `screenWidthDp > 600` (bug: la tablet en vertical usaba el layout horizontal).
- RF-2: Vertical: las tarjetas RUTA LIBRE / TABATA se reparten el alto sobrante (sin hueco inferior). Si el alto es insuficiente, se usa scroll.
- RF-3: Horizontal: panel lateral (cabecera, sensores, accesos, última actividad, cerrar) + las dos tarjetas lado a lado ocupando todo el alto.
- RF-4: Tablet: márgenes, tipografía e iconos mayores; en vertical, accesos rápidos y última actividad en la misma fila.
- RF-5: Sin cambios de comportamiento: mismos botones, callbacks, diálogo de cierre y ViewModel. No se toca NavGraph, servicios, Room ni Gradle.

## Revisión 2 (2026-09-27)
- RF-6: Fila 1: RUTA LIBRE y TABATA en la **misma fila** (en todas las orientaciones).
- RF-7: Fila 2: tarjeta **Última actividad** con datos reales (tipo, cuándo, duración, distancia o rondas, FC media) y tarjeta **Esta semana** (desde el lunes 00:00: sesiones, tiempo total, km, FC media). Tocar cualquiera → Historial.
- RF-8: Fila 3: barra de acciones **Historial | Perfil | Cerrar app**. Se eliminan los duplicados (iconos de la cabecera y el botón "Stats" sin función).
- RF-9: Datos vía nuevo `HomeViewModel` (Hilt) que solo **lee** `SessionDao.getAllSessions()` (ya existente). Cálculo en función pura `buildHomeSummary()` con test unitario. Sin cambios en entidades, DAO, NavGraph ni servicios.
