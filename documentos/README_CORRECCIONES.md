# 🔧 CORRECCIONES v2 - POI + HR + Mejoras

## ✅ Problemas Corregidos

| # | Problema | Solución |
|---|----------|----------|
| 1 | **POI no funciona en Tabata** | ✅ Implementado `addPoi()` en WorkoutService + conectado en WorkoutScreen |
| 2 | **HR se pierde entre entrenos** | ✅ Añadido `reset()` en BleManagers + reconexión automática |
| 3 | **Layout horizontal malo en Ruta** | ✅ Rediseñado con scroll vertical |
| 4 | **Sin feedback de sensores** | ✅ Estados de escaneo + indicadores amarillos |

---

## 📁 Archivos a Reemplazar (TODOS COMPLETOS)

| Archivo | Destino | 
|---------|---------|
| `Entities.kt` | `data/local/` |
| `GpsDao.kt` | `data/local/` |
| `GpsManager.kt` | `sensor/` |
| `BleManagers.kt` | `sensor/` |
| `AppDatabase.kt` | `data/local/` |
| `WorkoutService.kt` | `service/` |
| `WorkoutScreen.kt` | `ui/workout/` |
| `FreeRideService.kt` | `service/` |
| `FreeRideScreen.kt` | `ui/freeride/` |

**NO hay cambios manuales necesarios - todos los archivos están completos y listos para usar.**

---

## ⚠️ IMPORTANTE: Base de Datos

Al reemplazar `AppDatabase.kt`, la versión cambia de 4 a 5. 

Debido a `fallbackToDestructiveMigration()`, **se borrarán todos los datos anteriores** la primera vez que abras la app después de la actualización.

---

## 🔄 Flujo de Reconexión HR Mejorado

```
┌─────────────────────────────────────────────────────────────┐
│  ENTRENO 1                                                  │
│  ──────────                                                 │
│  1. configureWorkout() → bleHeartRateManager.reset()        │
│  2. startWorkout() → startSensors() → heartRateFlow()       │
│  3. Flow encuentra sensor → Conecta                         │
│  4. lastConnectedDeviceAddress se guarda                    │
│  5. finishWorkout() → Flow se cierra pero dirección guardada│
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  ENTRENO 2                                                  │
│  ──────────                                                 │
│  1. configureWorkout() → reset() limpia GATT               │
│  2. startWorkout() → heartRateFlow()                        │
│  3. Flow detecta lastConnectedDeviceAddress                 │
│  4. Intenta reconectar directamente (sin escanear)          │
│  5. Si falla en 5s → Inicia escaneo normal                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 🎯 POI - Cómo Funciona

### En Tabata (WorkoutScreen):
1. Usuario pulsa botón 📍 durante entreno con GPS activo
2. `service?.addPoi()` se llama
3. WorkoutService obtiene ubicación actual de GpsManager
4. Crea PoiEntity con: ubicación, HR, velocidad, distancia, fase, ronda
5. Guarda en BD y vibra como confirmación

### Verificar que funciona:
- El botón POI solo aparece si `sensorState.isGpsTracking == true`
- En Logcat verás: `📍 POI guardado: POI 1 en (lat, lon)`
- Los POIs se exportan en Excel si implementas la exportación

---

## 🚴 Ruta Libre - 3 Páginas

```
┌─────────┐     ┌─────────┐     ┌─────────┐
│ PÁGINA 1│ ←→  │ PÁGINA 2│ ←→  │ PÁGINA 3│
│ Métricas│     │  Mapa   │     │ Vueltas │
│         │     │         │     │         │
│ Timer   │     │ (futuro)│     │ Lap act.│
│ Vel/Dist│     │ Coords  │     │ [NUEVA] │
│ HR/Alt  │     │ Overlay │     │ Lista   │
│ Controls│     │         │     │ Totales │
└─────────┘     └─────────┘     └─────────┘
```

---

## 📋 Checklist de Implementación

- [ ] Reemplazar `Entities.kt`
- [ ] Reemplazar `GpsDao.kt`
- [ ] Reemplazar `GpsManager.kt`
- [ ] Reemplazar `BleManagers.kt`
- [ ] Reemplazar `AppDatabase.kt`
- [ ] Reemplazar `FreeRideService.kt`
- [ ] Reemplazar `FreeRideScreen.kt`
- [ ] Modificar `WorkoutService.kt` (ver instrucciones)
- [ ] Modificar `WorkoutScreen.kt` (conectar POI)
- [ ] Verificar `AndroidManifest.xml` tiene FreeRideService
- [ ] Compilar y probar

---

## 🧪 Tests Recomendados

1. **HR entre entrenos:**
   - Iniciar entreno Tabata → HR conecta
   - Finalizar → Iniciar nuevo entreno
   - HR debe reconectar automáticamente (máx 5 segundos)

2. **POI en Tabata:**
   - Iniciar entreno con GPS activo
   - Durante fase WORK, pulsar botón POI
   - Debe vibrar como confirmación
   - Ver en Logcat: "📍 POI guardado"

3. **Ruta Libre - Swipe:**
   - Iniciar ruta
   - Swipe izquierda → Página Mapa
   - Swipe izquierda → Página Vueltas
   - Pulsar NUEVA VUELTA
   - Ver lap en lista

4. **Layout horizontal:**
   - En Ruta Libre, girar a horizontal
   - Todas las métricas deben verse (con scroll)
   - Controles accesibles
