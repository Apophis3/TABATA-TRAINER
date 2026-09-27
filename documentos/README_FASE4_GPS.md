# 📍 FASE 4 - Integración GPS Completa + Swipe Pages

## Estado Actual: ✅ FUNCIONAL + NUEVAS FEATURES

### 🆕 NUEVAS FUNCIONALIDADES

#### 1. Swipe entre Páginas durante Entrenamiento
- **Página 1**: Timer principal (lo que ya existía)
- **Página 2**: Métricas GPS detalladas (velocidad grande, distancia, altitud, POI)
- Indicadores de página (dots) en la parte inferior
- Solo aparece si GPS está activado

#### 2. Detección de Entrenamiento en Curso
- Si vuelves a ConfigScreen con un entreno activo, aparece un **banner animado**
- El banner muestra: fase actual, ronda, tiempo restante
- Botón "VOLVER" para regresar al entrenamiento
- El botón "INICIAR" se deshabilita si hay entreno en curso

#### 3. Botón POI (Punto de Interés)
- En la página de GPS hay un botón para marcar ubicaciones importantes
- (La lógica de guardado se puede implementar después)

La integración GPS está **completamente implementada** y lista para funcionar. Este documento describe los cambios realizados y los próximos pasos opcionales.

---

## 📁 Archivos Modificados/Creados

### NUEVOS en esta actualización:

| Archivo | Descripción |
|---------|-------------|
| **WorkoutScreen.kt** | HorizontalPager para swipe, página GPS detallada |
| **ConfigScreen.kt** | Banner de entreno en curso, detección de servicio activo |
| **NavGraph.kt** | Navegación para volver al entreno en curso |
| **ExportUtils.kt** | Fix autoSizeColumn → ancho fijo |
| **WorkoutService.kt** | Integración GPS completa |

---

## 📁 Archivos Corregidos Previamente

### 1. ExportUtils.kt ✅ CORREGIDO

**Problema original:**
```kotlin
// Línea 433 - Causaba crash en Android
sheet.autoSizeColumn(i)
```

**Error:**
```
java.lang.NoClassDefFoundError: java.awt.font.FontRenderContext
```

**Solución:**
```kotlin
// ✅ Usar ancho fijo en lugar de autoSizeColumn
sheet.setColumnWidth(i, 20 * 256)  // ~20 caracteres
```

**Cambios adicionales:**
- Formateador de fechas definido UNA vez fuera del bucle
- Timestamp convertido a fecha legible ("dd/MM/yyyy HH:mm:ss")
- Ancho fijo en TODAS las hojas (Resumen, Datos, GPS)

---

### 2. WorkoutService.kt ✅ INTEGRACIÓN GPS COMPLETA

**Nuevas inyecciones:**
```kotlin
@Inject lateinit var gpsManager: GpsManager
@Inject lateinit var gpsDao: GpsDao
```

**Nuevo Job para GPS:**
```kotlin
private var gpsCollectorJob: Job? = null
```

**Funciones añadidas:**
| Función | Descripción |
|---------|-------------|
| `setGpsEnabled(enabled)` | Habilita/deshabilita GPS |
| `reconnectGps()` | Reconexión manual del GPS |
| `startGpsTracking()` | Inicia el flow de GPS |
| `saveGpsPoint(reading)` | Guarda punto en buffer |
| `flushGpsBuffer()` | Persiste buffer en BD |

**Flujo de datos GPS:**
```
GpsManager.gpsFlow() 
    → GpsReading 
    → saveGpsPoint() 
    → gpsPointsBuffer (10 puntos)
    → flushGpsBuffer() 
    → GpsDao.insertPoints()
```

**Foreground Service actualizado para Android 14+:**
```kotlin
ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
```

---

### 3. HistoryScreen.kt ✅ YA CORREGIDO

**Problema original:** Smart cast imposible con `Float?`

**Solución aplicada:**
```kotlin
// ✅ Extraer a variable local primero
val distance = session.totalDistanceMeters
if (session.gpsEnabled && distance != null && distance > 0) {
    // Usar 'distance' aquí
    String.format("%.2f km", distance / 1000f)
}
```

---

## 📊 Arquitectura GPS

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   GpsManager    │────▶│  WorkoutService  │────▶│     GpsDao      │
│  (Flow GPS)     │     │  (Orquestador)   │     │  (Persistencia) │
└─────────────────┘     └──────────────────┘     └─────────────────┘
        │                        │                        │
        ▼                        ▼                        ▼
   GpsReading              SensorState              GpsPointEntity
   - latitude              - isGpsTracking          - sessionId
   - longitude             - currentSpeedKmh        - timestamp
   - altitude              - totalDistanceKm        - lat/lon/alt
   - speed                 - gpsAccuracy            - phase/round
   - accuracy
```

---

## 🎯 Estado de la Hoja de Ruta GPS

| # | Funcionalidad | Estado | Notas |
|---|---------------|--------|-------|
| 9 | Integración GPS | ✅ | GpsManager + permisos |
| 10 | Dibujar track | ⏳ | Ver sección siguiente |
| 11 | Autolaps GPS | ⏳ | Detección por velocidad |
| 12 | Velocidad actual/media | ✅ | En SensorState |
| 13 | Distancia recorrida | ✅ | GpsStats.totalDistanceMeters |
| 14 | **Swipe Pages** | ✅ | HorizontalPager |
| 15 | **Entreno en curso** | ✅ | Banner + navegación |
| 16 | **POI (Marcas)** | 🔨 | Botón listo, falta persistencia |

---

## 🔄 Arquitectura Swipe Pages

```
┌─────────────────────────────────────────────────────────────┐
│                    WorkoutScreen                            │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              HorizontalPager (2 páginas)              │  │
│  │                                                       │  │
│  │   ┌─────────────────┐     ┌─────────────────┐        │  │
│  │   │   PÁGINA 1      │ ←→  │   PÁGINA 2      │        │  │
│  │   │                 │     │                 │        │  │
│  │   │  Timer Grande   │     │  VELOCIDAD      │        │  │
│  │   │  Fase/Ronda     │     │  72.5 km/h      │        │  │
│  │   │  Gráfico HR     │     │                 │        │  │
│  │   │  Métricas       │     │  ┌─────┬─────┐  │        │  │
│  │   │  Controles      │     │  │ Dist│ Alt │  │        │  │
│  │   │                 │     │  ├─────┼─────┤  │        │  │
│  │   │                 │     │  │ HR  │ Cad │  │        │  │
│  │   │                 │     │  └─────┴─────┘  │        │  │
│  │   │                 │     │                 │        │  │
│  │   │                 │     │  [POI] [⏸] GPS │        │  │
│  │   └─────────────────┘     └─────────────────┘        │  │
│  │                                                       │  │
│  │                    ● ○  (indicadores)                 │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Gestos:
- **Swipe izquierda**: Ir a página GPS (desde timer)
- **Swipe derecha**: Volver a timer (desde GPS)
- Indicador visual "GPS →" en el borde derecho

---

## 🔔 Detección de Entrenamiento en Curso

### Problema Original
Cuando el usuario navegaba hacia atrás durante un entreno:
- El entreno seguía en background (sonidos, vibración)
- Pero no había forma de volver a la pantalla

### Solución Implementada

```
┌─────────────────────────────────────────────────────────────┐
│  ConfigScreen                                               │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ ⚡ ENTRENAMIENTO EN CURSO                              │  │
│  │ TRABAJO  Ronda 3/8               :45  [→ VOLVER]      │  │
│  │ 📍 GPS activo                                         │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ Sensores                                             │    │
│  │ ...                                                  │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                             │
│  [INICIAR ENTRENAMIENTO] ← Deshabilitado si hay entreno     │
└─────────────────────────────────────────────────────────────┘
```

### Flujo:
1. ConfigScreen se conecta al WorkoutService existente
2. Observa `workoutState` para detectar si hay entreno activo
3. Si `isRunning || isPaused` → Muestra banner
4. Al pulsar "VOLVER" → `navController.popBackStack()` al WorkoutScreen

---

## 🗺️ PRÓXIMO PASO: Visualización de Track en Mapa

### Opción A: Google Maps (Recomendado)

**1. Añadir dependencia en build.gradle:**
```kotlin
implementation("com.google.android.gms:play-services-maps:18.2.0")
implementation("com.google.maps.android:maps-compose:4.3.0")
```

**2. Crear MapScreen.kt:**
```kotlin
@Composable
fun TrackMapScreen(
    gpsPoints: List<GpsPointEntity>,
    onBack: () -> Unit
) {
    val cameraPositionState = rememberCameraPositionState()
    
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState
    ) {
        // Dibujar polyline con colores por fase
        val workPoints = gpsPoints.filter { it.phase == "WORK" }
        val restPoints = gpsPoints.filter { it.phase == "REST" }
        
        Polyline(
            points = workPoints.map { LatLng(it.latitude, it.longitude) },
            color = Color.Green,
            width = 8f
        )
        
        Polyline(
            points = restPoints.map { LatLng(it.latitude, it.longitude) },
            color = Color.Red,
            width = 8f
        )
    }
}
```

### Opción B: OpenStreetMap (Sin API Key)

**Dependencia:**
```kotlin
implementation("org.osmdroid:osmdroid-android:6.1.18")
```

---

## 🔧 Autolaps GPS (Futuro)

**Concepto:** Detectar automáticamente Work vs Rest por velocidad.

```kotlin
// En GpsManager o WorkoutService
fun detectPhaseBySpeed(currentSpeed: Float): WorkoutPhase {
    return when {
        currentSpeed > WORK_THRESHOLD_KMH -> WorkoutPhase.WORK
        currentSpeed < REST_THRESHOLD_KMH -> WorkoutPhase.REST
        else -> currentPhase  // Mantener fase actual
    }
}

companion object {
    const val WORK_THRESHOLD_KMH = 8f   // >8 km/h = Trabajo
    const val REST_THRESHOLD_KMH = 3f   // <3 km/h = Descanso
}
```

---

## ✅ Checklist de Implementación

- [x] GpsManager.kt - Flow de ubicaciones
- [x] GpsDao.kt - Persistencia de puntos
- [x] GpsPointEntity - Entidad Room
- [x] WorkoutService - Integración completa
- [x] SensorState - Campos GPS
- [x] SessionStats - Estadísticas GPS
- [x] WorkoutSessionEntity - Campos GPS
- [x] ExportUtils - Excel con hoja GPS + GPX
- [x] HistoryScreen - Mostrar distancia
- [x] AndroidManifest - Permisos GPS
- [ ] MapScreen - Visualización de track
- [ ] Autolaps - Detección por velocidad

---

## 📦 Dependencias Necesarias

### Para HorizontalPager (Swipe)
```kotlin
// build.gradle.kts (app level)
dependencies {
    // Foundation para HorizontalPager
    implementation("androidx.compose.foundation:foundation:1.6.0")
    // o si ya tienes Compose actualizado, viene incluido
}
```

### Para Google Maps (Futuro - Track)
```kotlin
implementation("com.google.android.gms:play-services-maps:18.2.0")
implementation("com.google.maps.android:maps-compose:4.3.0")
```

---

## 🚀 Para Probar

1. **Reemplazar archivos:**
   - `WorkoutScreen.kt` → app/src/main/.../ui/workout/
   - `ConfigScreen.kt` → app/src/main/.../ui/config/
   - `NavGraph.kt` → app/src/main/.../navigation/
   - `ExportUtils.kt` → app/src/main/.../util/
   - `WorkoutService.kt` → app/src/main/.../service/

2. **Verificar import de HorizontalPager:**
   ```kotlin
   import androidx.compose.foundation.pager.HorizontalPager
   import androidx.compose.foundation.pager.rememberPagerState
   ```
   Si da error, añadir `@OptIn(ExperimentalFoundationApi::class)`

3. **Limpiar y reconstruir:**
   ```bash
   ./gradlew clean
   ./gradlew assembleDebug
   ```

4. **Probar:**
   - ✅ Activar GPS en ConfigScreen
   - ✅ Iniciar entrenamiento
   - ✅ Deslizar izquierda ← para ver página GPS
   - ✅ Deslizar derecha → para volver al timer
   - ✅ Navegar hacia atrás (botón Android)
   - ✅ Ver banner "ENTRENAMIENTO EN CURSO"
   - ✅ Pulsar "VOLVER" para regresar
   - ✅ Al terminar, exportar Excel y verificar hoja "GPS"

---

## 📱 Permisos Requeridos

El usuario debe conceder estos permisos:

| Permiso | Propósito |
|---------|-----------|
| ACCESS_FINE_LOCATION | GPS preciso |
| ACCESS_COARSE_LOCATION | Ubicación aproximada |
| FOREGROUND_SERVICE_LOCATION | GPS en background |

**Solicitar en runtime (MainActivity o ConfigScreen):**
```kotlin
val locationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { permissions ->
    val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    // Actualizar UI según resultado
}

// Llamar cuando el usuario active GPS
locationPermissionLauncher.launch(arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
))
```

---

## 🎨 Colores Pantone de la App

| Color | Código Pantone | Hex | Uso |
|-------|----------------|-----|-----|
| Naranja | 165 C | #FF5722 | Logo, botones primarios |
| Gris | Cool Gray 11 C | #607D8B | Fondos, texto secundario |

---

## 📞 Soporte

Si hay algún error:
1. Verificar que GpsManager esté inyectado con `@Inject`
2. Verificar que AppModule provea `GpsDao`
3. Verificar versión de Room (mismo para todas las entities)
4. Revisar logs con tag "GpsManager" o "WorkoutService"
