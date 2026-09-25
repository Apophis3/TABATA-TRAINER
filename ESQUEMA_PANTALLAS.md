# 📱 ESQUEMA DE PANTALLAS - TABATA TRAINER

## 🗺️ ESTRUCTURA DE NAVEGACIÓN

```
┌─────────────────────────────────────────────────────────────┐
│                    🏠 HOME SCREEN                           │
│              (Pantalla Principal / Inicio)                  │
│                                                             │
│  • Estado de sensores (HR, CAD, GPS)                       │
│  • Botones: Ruta Libre | Tabata                            │
│  • Accesos rápidos: Historial | Stats | Perfil             │
│  • Botón: Cerrar aplicación                                │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │ RUTA LIBRE   │  │    TABATA    │  │  HISTORIAL   │    │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘    │
│         │                 │                 │             │
│         │                 │                 │             │
│         ▼                 ▼                 ▼             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ FREE RIDE    │  │   CONFIG      │  │   HISTORY    │  │
│  │   SCREEN     │  │   SCREEN      │  │   SCREEN     │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  │
│         │                 │                 │           │
│         │                 ▼                 │           │
│         │          ┌──────────────┐        │           │
│         │          │   WORKOUT    │        │           │
│         │          │   SCREEN     │        │           │
│         │          └──────┬───────┘        │           │
│         │                 │                │           │
│         │                 ▼                │           │
│         │          ┌──────────────┐        │           │
│         └─────────►│   SESSION    │◄───────┘           │
│                    │   DETAIL     │                    │
│                    │   SCREEN     │                    │
│                    └──────────────┘                    │
└─────────────────────────────────────────────────────────────┘
```

---

## 📋 DETALLE DE PANTALLAS

### 1. 🏠 **HOME SCREEN** (`HomeScreen.kt`)
**Ubicación:** `ui/home/HomeScreen.kt`  
**Ruta:** `"home"` (pantalla de inicio)

**Propósito:**
- Pantalla principal de la aplicación
- Muestra estado de sensores (HR, CAD, GPS)
- Punto de entrada para iniciar actividades

**Funcionalidades:**
- ✅ Escaneo automático de sensores BLE/ANT+
- ✅ Indicadores de estado de conexión (HR, CAD, GPS)
- ✅ Botón "RUTA LIBRE" → Navega a `FreeRideScreen`
- ✅ Botón "TABATA" → Navega a `ConfigScreen`
- ✅ Botón "Historial" → Navega a `HistoryScreen`
- ✅ Botón "Cerrar aplicación" → Detiene servicios y cierra app

**Navegación desde aquí:**
- `onStartFreeRide` → `Screen.FreeRide`
- `onStartTabata` → `Screen.TabataConfig`
- `onNavigateToHistory` → `Screen.History`
- `onNavigateToSettings` → `Screen.Settings` (TODO)

**Layouts:**
- Portrait: Layout vertical con scroll
- Landscape: Layout horizontal con dos columnas

---

### 2. ⚙️ **CONFIG SCREEN** (`ConfigScreen.kt`)
**Ubicación:** `ui/config/ConfigScreen.kt`  
**Ruta:** `"tabata_config"`

**Propósito:**
- Configuración de entrenamiento Tabata
- Permite ajustar parámetros antes de iniciar
- Muestra estado de sensores y entrenamiento activo

**Funcionalidades:**
- ✅ Configuración de tiempos: Warmup, Work, Rest, Rounds
- ✅ Toggle GPS (habilitar/deshabilitar)
- ✅ Estado de sensores en tiempo real
- ✅ Botón "Reanudar entrenamiento" si hay uno activo
- ✅ Botón "Iniciar" → Navega a `WorkoutScreen` con parámetros

**Navegación desde aquí:**
- `onStartWorkout` → `Screen.Workout` (con parámetros)
- `onResumeWorkout` → `Screen.Workout` (si hay entrenamiento activo)
- `onNavigateToHistory` → `Screen.History`

**Parámetros que pasa a WorkoutScreen:**
- `warmupSeconds: Int`
- `workSeconds: Int`
- `restSeconds: Int`
- `rounds: Int`
- `gpsEnabled: Boolean`

---

### 3. 🏋️ **WORKOUT SCREEN** (`WorkoutScreen.kt`)
**Ubicación:** `ui/workout/WorkoutScreen.kt`  
**Ruta:** `"workout/{warmup}/{work}/{rest}/{rounds}/{gps}"`

**Propósito:**
- Pantalla principal durante el entrenamiento Tabata
- Muestra timer, métricas, gráficas y controles
- Gestiona el ciclo de vida del entrenamiento

**Funcionalidades:**
- ✅ Timer con fases: Warmup → Work → Rest → Finished
- ✅ Métricas en tiempo real: HR1, HR2, Cadence, GPS
- ✅ Gráfica de frecuencia cardíaca (HR1 y HR2)
- ✅ Estadísticas: Promedio, Máximo, Mínimo
- ✅ Controles: Start, Pause, Resume, Stop
- ✅ Páginas deslizables: Workout | GPS (si está habilitado)
- ✅ Botón "Volver" → Detiene entrenamiento y vuelve a Home

**Navegación desde aquí:**
- `onFinish` → `Screen.Home` (al terminar)
- `onViewSession` → `Screen.SessionDetail` (ver detalles de sesión)

**Servicios utilizados:**
- `WorkoutService` (Foreground Service)
- Conecta con `SensorManager` para datos de sensores

**Layouts:**
- Portrait: Layout vertical con gráfica y métricas
- Landscape: Layout horizontal con timer a la izquierda y gráfica/métricas a la derecha

---

### 4. 🚴 **FREE RIDE SCREEN** (`FreeRideScreen.kt`)
**Ubicación:** `ui/freeride/FreeRideScreen.kt`  
**Ruta:** `"freeride"`

**Propósito:**
- Pantalla para entrenamientos de ruta libre (ciclismo, running, etc.)
- Sin intervalos predefinidos, tiempo ilimitado
- Tracking GPS con mapa

**Funcionalidades:**
- ✅ Timer continuo (sin límite de tiempo)
- ✅ Tracking GPS con mapa en tiempo real
- ✅ Métricas: HR, Cadence, Velocidad, Distancia, Elevación
- ✅ Sistema de vueltas (Laps)
- ✅ Páginas deslizables: Métricas | Mapa GPS
- ✅ Controles: Start, Pause, Resume, Stop, Lap

**Navegación desde aquí:**
- `onFinish` → `Screen.Home` (al terminar)
- `onViewSession` → `Screen.SessionDetail` (ver detalles de sesión)

**Servicios utilizados:**
- `FreeRideService` (Foreground Service)
- Conecta con `SensorManager` y `GpsManager`

**Layouts:**
- Portrait: Layout vertical con métricas y mapa
- Landscape: Layout horizontal optimizado

---

### 5. 📜 **HISTORY SCREEN** (`HistoryScreen.kt`)
**Ubicación:** `ui/history/HistoryScreen.kt`  
**Ruta:** `"history"`

**Propósito:**
- Lista de todas las sesiones de entrenamiento guardadas
- Permite ver historial y acceder a detalles

**Funcionalidades:**
- ✅ Lista de sesiones ordenadas por fecha (más recientes primero)
- ✅ Información resumida: Fecha, Duración, Tipo, Rondas
- ✅ Botón "Volver" → Regresa a pantalla anterior
- ✅ Click en sesión → Navega a `SessionDetailScreen`

**Navegación desde aquí:**
- `onBack` → `popBackStack()` (vuelve atrás)
- `onSessionClick` → `Screen.SessionDetail` (con `sessionId`)

**ViewModel:**
- `HistoryViewModel` - Gestiona carga de sesiones desde Room Database

---

### 6. 📊 **SESSION DETAIL SCREEN** (`SessionDetailScreen.kt`)
**Ubicación:** `ui/session/SessionDetailScreen.kt`  
**Ruta:** `"session/{sessionId}"`

**Propósito:**
- Vista detallada de una sesión de entrenamiento específica
- Muestra estadísticas completas, gráficas y datos GPS

**Funcionalidades:**
- ✅ Información completa de la sesión
- ✅ Gráficas de HR1, HR2, Cadence a lo largo del tiempo
- ✅ Estadísticas: Promedio, Máximo, Mínimo, Tiempo total
- ✅ Datos GPS: Mapa, Ruta, Distancia, Velocidad (si aplica)
- ✅ Exportar datos (funcionalidad futura)
- ✅ Botón "Volver" → Regresa a pantalla anterior

**Navegación desde aquí:**
- `onBack` → `popBackStack()` (vuelve atrás)

**ViewModel:**
- `SessionDetailViewModel` - Carga datos de sesión, lecturas de sensores y puntos GPS

**Parámetros:**
- `sessionId: String` (pasado como argumento de navegación)

---

## 🔄 FLUJOS DE NAVEGACIÓN PRINCIPALES

### Flujo 1: Entrenamiento Tabata
```
HomeScreen 
  → (Click "TABATA") 
  → ConfigScreen 
  → (Click "Iniciar") 
  → WorkoutScreen 
  → (Terminar) 
  → HomeScreen
  → (Opcional: Ver sesión) 
  → SessionDetailScreen
```

### Flujo 2: Ruta Libre
```
HomeScreen 
  → (Click "RUTA LIBRE") 
  → FreeRideScreen 
  → (Terminar) 
  → HomeScreen
  → (Opcional: Ver sesión) 
  → SessionDetailScreen
```

### Flujo 3: Ver Historial
```
HomeScreen 
  → (Click "Historial") 
  → HistoryScreen 
  → (Click en sesión) 
  → SessionDetailScreen
```

### Flujo 4: Reanudar Entrenamiento
```
HomeScreen 
  → (Click "TABATA") 
  → ConfigScreen 
  → (Si hay entrenamiento activo: Click "Reanudar") 
  → WorkoutScreen
```

---

## 📦 COMPONENTES COMPARTIDOS

### `ui/components/SharedComponents.kt`
Componentes reutilizables usados en múltiples pantallas:
- `SensorChip` / `SensorChipCompact` - Indicadores de estado de sensores
- `MetricItem` / `MetricItemCompact` - Tarjetas de métricas
- `HeartRateGraph` - Gráfica de frecuencia cardíaca
- Otros componentes compartidos

### `ui/components/MapComponents.kt`
Componentes relacionados con mapas GPS:
- Componentes para mostrar mapas de Google Maps
- Marcadores y rutas

---

## 🎨 TEMA Y ESTILOS

### `ui/theme/Theme.kt`
- `TabataColors` - Paleta de colores
- `TabataSizes` - Tamaños estándar
- Tema Material3 personalizado

---

## 🔧 SERVICIOS Y VIEWMODELS

### Servicios (Foreground Services)
- **`WorkoutService`** - Gestiona entrenamientos Tabata
- **`FreeRideService`** - Gestiona rutas libres

### ViewModels
- **`ConfigViewModel`** - Estado de sensores y configuración (usado en HomeScreen y ConfigScreen)
- **`HistoryViewModel`** - Gestión de historial de sesiones
- **`SessionDetailViewModel`** - Detalles de sesión específica

---

## 📝 NOTAS IMPORTANTES

1. **Navegación:** Todas las pantallas usan Navigation Compose
2. **Estado:** Los servicios mantienen estado mientras la app está en segundo plano
3. **Persistencia:** Las sesiones se guardan en Room Database
4. **Sensores:** Todas las pantallas que muestran sensores usan `SensorManager`
5. **GPS:** Solo disponible en `WorkoutScreen` (si está habilitado) y `FreeRideScreen`

---

## 🚧 PANTALLAS FUTURAS (TODO)

- **Settings Screen** (`Screen.Settings`) - Configuración de la aplicación
- Pantalla de perfil de usuario
- Pantalla de estadísticas generales

---

## 📂 ESTRUCTURA DE ARCHIVOS

```
app/src/main/java/com/tuapp/tabatatrainer/
├── ui/
│   ├── home/
│   │   └── HomeScreen.kt
│   ├── config/
│   │   ├── ConfigScreen.kt
│   │   └── ConfigViewModel.kt
│   ├── workout/
│   │   └── WorkoutScreen.kt
│   ├── freeride/
│   │   └── FreeRideScreen.kt
│   ├── history/
│   │   ├── HistoryScreen.kt
│   │   └── HistoryViewModel.kt
│   ├── session/
│   │   └── SessionDetailScreen.kt
│   ├── components/
│   │   ├── SharedComponents.kt
│   │   └── MapComponents.kt
│   └── theme/
│       └── Theme.kt
└── navigation/
    └── NavGraph.kt
```

---

**Última actualización:** Diciembre 2024
