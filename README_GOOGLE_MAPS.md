# 🗺️ Implementación de Google Maps - TabataTrainer

## ✅ Checklist de Implementación

- [ ] **Paso 1**: Configurar proyecto en Google Cloud Console
- [ ] **Paso 2**: Obtener API Key
- [ ] **Paso 3**: Añadir dependencias al `build.gradle`
- [ ] **Paso 4**: Añadir API Key al `AndroidManifest.xml`
- [ ] **Paso 5**: Reemplazar archivos de código
- [ ] **Paso 6**: Compilar y probar

---

## 📋 Paso 1: Configurar Google Cloud Console

1. Ve a [Google Cloud Console](https://console.cloud.google.com/)
2. Crea un nuevo proyecto o selecciona uno existente
3. Nombre del proyecto: **TabataTrainer**

---

## 🔑 Paso 2: Habilitar API y obtener Key

### Habilitar Maps SDK:
1. En el menú izquierdo: **APIs & Services** → **Library**
2. Busca **"Maps SDK for Android"**
3. Haz clic en **ENABLE**

### Crear API Key:
1. Ve a **APIs & Services** → **Credentials**
2. Clic en **+ CREATE CREDENTIALS** → **API Key**
3. **Copia la API Key** (algo como: `AIzaSyD...xxxx`)

### Restringir la Key (recomendado):
1. Clic en la key creada
2. En **Application restrictions**: selecciona **Android apps**
3. En **Add an item**:
   - **Package name**: `com.tuapp.tabatatrainer`
   - **SHA-1**: (obtenerlo con el comando abajo)

```bash
# Para obtener SHA-1 del debug keystore:
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

⚠️ **IMPORTANTE**: Si ves el mapa en gris/blanco, necesitas:
1. Ir a **Billing** en Google Cloud Console
2. Añadir una tarjeta de crédito (no te cobrarán, hay $200/mes gratis)

---

## 📦 Paso 3: Añadir Dependencias

Abre `app/build.gradle.kts` y añade en el bloque `dependencies`:

```kotlin
dependencies {
    // ... otras dependencias ...
    
    // 🗺️ GOOGLE MAPS
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.maps.android:maps-compose:4.3.3")
    implementation("com.google.maps.android:android-maps-utils:3.8.2")
}
```

Haz **Sync Now**.

---

## 📄 Paso 4: Configurar AndroidManifest.xml

Abre `app/src/main/AndroidManifest.xml` y:

### 1. Añadir permiso de Internet (si no existe):
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### 2. Añadir API Key dentro de `<application>`:
```xml
<application ...>

    <!-- 🗺️ GOOGLE MAPS API KEY -->
    <meta-data
        android:name="com.google.android.geo.API_KEY"
        android:value="PEGA_AQUI_TU_API_KEY" />
    
    <!-- resto de activities, services, etc. -->
    
</application>
```

**¡Reemplaza `PEGA_AQUI_TU_API_KEY` con tu clave real!**

---

## 📁 Paso 5: Archivos a Reemplazar

| Archivo | Destino | Descripción |
|---------|---------|-------------|
| `AndroidManifest.xml` | `app/src/main/` | Con API Key de Maps |
| `FreeRideScreen.kt` | `ui/freeride/` | Mapa en tiempo real |
| `FreeRideService.kt` | `service/` | Expone puntos para mapa |
| `MapComponents.kt` | `ui/components/` | **NUEVO** - Componentes de mapa reutilizables |

### Estructura de archivos:
```
app/src/main/
├── AndroidManifest.xml          ← MODIFICAR (añadir API Key)
└── java/com/tuapp/tabatatrainer/
    ├── service/
    │   └── FreeRideService.kt   ← REEMPLAZAR
    └── ui/
        ├── components/
        │   └── MapComponents.kt ← CREAR NUEVO
        └── freeride/
            └── FreeRideScreen.kt ← REEMPLAZAR
```

---

## 🗺️ Cómo Funciona el Mapa

### En FreeRideScreen (Página 2):
```
┌────────────────────────────────┐
│  📍 GPS Conectado              │ ← Overlay superior
│  43.26701, -2.93458            │
│  Precisión: ±8m                │
├────────────────────────────────┤
│                                │
│     [MAPA DE GOOGLE]           │
│                                │
│   🟢 Inicio                    │
│      ═══════════               │ ← Polyline azul (ruta)
│              ═══════           │
│                  🔵 Tú         │ ← Posición actual
│                                │
├────────────────────────────────┤
│  28.5 km/h │ 00:45:30 │ 12.5 km│ ← Overlay inferior
│  Velocidad │  Tiempo  │ Dist.  │
└────────────────────────────────┘
```

### Componentes disponibles:

1. **`WorkoutMap`** - Mapa básico con ruta
   ```kotlin
   WorkoutMap(
       gpsPoints = listOfGpsPoints,
       pois = listOfPois,
       currentLocation = LatLng(lat, lon),
       isLive = true,  // Sigue ubicación actual
       routeColor = Color.Blue
   )
   ```

2. **`LiveMapWithMetrics`** - Mapa con overlay de métricas
   ```kotlin
   LiveMapWithMetrics(
       gpsPoints = points,
       currentLatitude = 43.26,
       currentLongitude = -2.93,
       currentSpeedKmh = 25.5f,
       totalDistanceMeters = 5000f,
       totalTimeSeconds = 1800,
       gpsAccuracy = 8f,
       isGpsConnected = true
   )
   ```

3. **`SessionMapCard`** - Card con mapa y estadísticas
   ```kotlin
   SessionMapCard(
       gpsPoints = sessionPoints,
       pois = sessionPois,
       totalDistanceMeters = 15000f,
       avgSpeedKmh = 22.5f,
       maxSpeedKmh = 45.0f,
       elevationGain = 150f
   )
   ```

---

## 🧪 Testing

### Probar sin GPS real (emulador):
1. En Android Studio, abre **Extended Controls** del emulador
2. Ve a **Location**
3. Puedes:
   - Establecer ubicación manual
   - Cargar un archivo GPX para simular ruta
   - Usar "Routes" para crear una ruta

### Verificar que funciona:
1. Abrir modo **Ruta Libre**
2. Swipe a **página 2** (Mapa)
3. Debería verse:
   - Mapa de Google (no gris)
   - Tu ubicación (marcador azul)
   - Al moverte, línea azul de la ruta

---

## ⚠️ Solución de Problemas

### Mapa en gris/blanco:
- ✅ Verifica que habilitaste **"Maps SDK for Android"** en Google Cloud
- ✅ Verifica que añadiste **facturación** (tarjeta de crédito)
- ✅ Verifica que la API Key está en el Manifest
- ✅ Verifica que el package name coincide con la restricción

### Error "API key not found":
- La API key debe estar dentro de `<application>`, no fuera
- El nombre debe ser exactamente `com.google.android.geo.API_KEY`

### Mapa carga pero no muestra ubicación:
- Verifica permisos de ubicación en la app
- En emulador, establece ubicación manual primero

### Crash al abrir mapa:
- Verifica que añadiste todas las dependencias
- Haz Clean + Rebuild del proyecto

---

## 📊 Uso de la API de Maps

Google Maps SDK tiene un **nivel gratuito generoso**:
- **28,000 cargas de mapa/mes** gratis
- Después: ~$7 por 1000 cargas

Para una app personal de entrenamiento, nunca llegarás al límite de pago.

---

## 🔄 Próximos Pasos (Opcional)

1. **Añadir mapa a SessionDetailScreen** - Mostrar ruta en historial
2. **Exportar a GPX** - Ya tienes esta funcionalidad
3. **Mapa offline** - Requiere implementación adicional
4. **Heatmap de velocidad** - Colorear ruta según velocidad

---

## 📎 Archivos Incluidos

- `AndroidManifest.xml` - Manifest actualizado
- `FreeRideScreen.kt` - Pantalla con mapa real
- `FreeRideService.kt` - Servicio con mapPoints
- `MapComponents.kt` - Componentes reutilizables
- `BUILD_GRADLE_DEPENDENCIES.txt` - Dependencias a añadir

¡Disfruta de tu mapa! 🚴‍♂️🗺️
