# 🚴 OPCIÓN A: Ruta Libre + Tabata

## Estado: ✅ IMPLEMENTADO

La app ahora tiene 2 modos principales:

```
┌─────────────────────────────────────────────────────────────┐
│                    TABATA TRAINER                           │
│                    ━━━━━━━━━━━━━━━                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  [HR ✓] [CAD ✓] [GPS ✓]                        [🔄] [⚙️]   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  🏃 RUTA LIBRE                              [▶️]    │   │
│  │  Ciclismo, Senderismo, Running...                   │   │
│  │  📍 Track GPS  ⏱️ Tiempo  📏 Distancia  ⚡ Velocidad │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  ⏱️ ENTRENAMIENTO TABATA                    [▶️]    │   │
│  │  HIIT, Intervalos, Indoor/Outdoor                   │   │
│  │  ⏱️ Intervalos  🔄 Rondas  ❤️ Monitor  🔊 Alertas  │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  [📊 Historial]  [📈 Estadísticas]  [👤 Perfil]           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 📁 Archivos Nuevos/Modificados

| Archivo | Ubicación | Descripción |
|---------|-----------|-------------|
| **HomeScreen.kt** | `ui/home/` | Nueva pantalla principal |
| **FreeRideScreen.kt** | `ui/freeride/` | Pantalla de Ruta Libre |
| **FreeRideService.kt** | `service/` | Servicio para tracking |
| **NavGraph.kt** | `navigation/` | Actualizado con nuevas rutas |

---

## 🏃 Modo RUTA LIBRE

### Características:
- ✅ Timer continuo (sin intervalos)
- ✅ GPS obligatorio
- ✅ Velocidad actual, media y máxima
- ✅ Distancia total
- ✅ Altitud y desnivel acumulado
- ✅ HR y Cadencia
- ✅ LAPs manuales
- ✅ Soporte landscape

### Pantalla:
```
┌─────────────────────────────────────────────────────────────┐
│  [GPS ●] [HR ●] [CAD ○]                          EN RUTA   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│                      01:23:45                               │
│                      ━━━━━━━━                               │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐                        │
│  │    28.5      │  │    42.3      │                        │
│  │    km/h      │  │     km       │                        │
│  │  Velocidad   │  │  Distancia   │                        │
│  └──────────────┘  └──────────────┘                        │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐                        │
│  │     142      │  │     450      │                        │
│  │     bpm      │  │      m       │                        │
│  │    Pulso     │  │   Altitud    │                        │
│  └──────────────┘  └──────────────┘                        │
│                                                             │
│  Vel.Med: 25.2  │  Vel.Máx: 45.1  │  Cadencia: 85  │  ↑120m│
│                                                             │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│  🏁 LAP 3 • 8.2 km • 15:23 • 32.1 km/h                  >  │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                                             │
│       [🛑]           [🏁 LAP]           [⏸️]               │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Controles:
- **▶️ Iniciar**: Comienza la grabación
- **⏸️ Pausar**: Pausa la actividad
- **🏁 LAP**: Marca un nuevo segmento
- **🛑 Finalizar**: Termina y guarda

---

## ⏱️ Modo TABATA (existente)

Sin cambios en funcionalidad, solo en navegación:
- Home → TabataConfig → Workout
- Swipe para página GPS (si activado)
- Banner de entreno en curso

---

## 🗂️ Estructura de Navegación

```
NavGraph
│
├── Home (nueva pantalla principal)
│   │
│   ├── → FreeRide (ruta libre)
│   │      └── → SessionDetail
│   │
│   ├── → TabataConfig (configuración tabata)
│   │      └── → Workout
│   │             └── → SessionDetail
│   │
│   └── → History
│          └── → SessionDetail
```

---

## 🔧 Implementación

### 1. Crear carpetas nuevas:
```
app/src/main/java/com/tuapp/tabatatrainer/
├── ui/
│   ├── home/
│   │   └── HomeScreen.kt          ← NUEVO
│   ├── freeride/
│   │   └── FreeRideScreen.kt      ← NUEVO
│   ├── config/
│   │   └── ConfigScreen.kt        (existente)
│   └── workout/
│       └── WorkoutScreen.kt       (existente)
├── service/
│   ├── WorkoutService.kt          (existente)
│   └── FreeRideService.kt         ← NUEVO
└── navigation/
    └── NavGraph.kt                (actualizado)
```

### 2. Añadir servicio al AndroidManifest.xml:
```xml
<service
    android:name=".service.FreeRideService"
    android:foregroundServiceType="connectedDevice|location"
    tools:targetApi="34"
    android:exported="false" />
```

### 3. Copiar archivos:
- `HomeScreen.kt` → `ui/home/`
- `FreeRideScreen.kt` → `ui/freeride/`
- `FreeRideService.kt` → `service/`
- `NavGraph.kt` → `navigation/` (reemplazar)

### 4. Compilar:
```bash
./gradlew clean
./gradlew assembleDebug
```

---

## 🔜 Próximos Pasos (Opcionales)

### Fase inmediata:
- [ ] Conectar FreeRideScreen con FreeRideService
- [ ] Guardar LAPs en base de datos
- [ ] Mostrar última actividad en HomeScreen

### Futuro:
- [ ] Añadir tipo de actividad (Ciclismo, Running, Senderismo)
- [ ] Autolaps por distancia (cada 1km, 5km, etc.)
- [ ] Autolaps por tiempo (cada 5min, 10min, etc.)
- [ ] Mapa con track en SessionDetail
- [ ] Exportar a Strava/Garmin
- [ ] Estadísticas globales (km totales, tiempo total, etc.)

---

## 📱 Permisos

Los mismos que antes:
- `ACCESS_FINE_LOCATION` - GPS
- `BLUETOOTH_SCAN/CONNECT` - Sensores BLE
- `FOREGROUND_SERVICE_LOCATION` - GPS en background

---

## 🎨 Colores

| Estado | Gradiente |
|--------|-----------|
| Preparado | Gris (#78909C → #546E7A) |
| En Ruta | Verde (#4CAF50 → #2E7D32) |
| Pausado | Naranja (#FFA726 → #FF9800) |

---

## ✅ Checklist de Archivos

- [x] HomeScreen.kt
- [x] FreeRideScreen.kt
- [x] FreeRideService.kt
- [x] NavGraph.kt (actualizado)
- [ ] AndroidManifest.xml (añadir servicio)
- [ ] Entities.kt (opcional: añadir LapEntity, ActivityType)
