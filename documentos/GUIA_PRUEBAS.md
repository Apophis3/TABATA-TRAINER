# 🧪 Guía de Pruebas - Mejoras Implementadas

## 📋 Pre-requisitos

1. **Sincronizar Gradle** (si usas Android Studio):
   - Click en "Sync Project with Gradle Files" (icono de elefante)
   - O: `File → Sync Project with Gradle Files`

2. **Limpiar proyecto** (recomendado):
   ```bash
   # En Windows
   gradlew.bat clean
   
   # O desde Android Studio: Build → Clean Project
   ```
'probando modificación'
## 🔨 Compilación

### Opción 1: Android Studio
1. `Build → Rebuild Project`
2. Espera a que termine la compilación
3. Si hay errores, revisa el panel "Build"

### Opción 2: Línea de comandos
```bash
# En Windows
gradlew.bat assembleDebug

# El APK estará en:
# app/build/outputs/apk/debug/app-debug.apk
```

## ✅ Checklist de Pruebas

### 1. Verificación de Permisos

**Qué probar:**
- [ ] La app solicita permisos al iniciar
- [ ] Si deniegas permisos de ubicación, el GPS no se activa
- [ ] Si deniegas permisos de Bluetooth, los sensores no se conectan
- [ ] Los mensajes de error son claros cuando faltan permisos

**Cómo probar:**
1. Desinstala la app si ya está instalada
2. Instala la nueva versión
3. Observa la solicitud de permisos
4. Prueba denegar algunos permisos y verifica el comportamiento

### 2. Sistema de Logging (Timber)

**Qué probar:**
- [ ] Los logs aparecen en Logcat con formato Timber
- [ ] Los logs incluyen el nombre de la clase automáticamente
- [ ] No hay errores relacionados con TAG

**Cómo probar:**
1. Abre Android Studio → Logcat
2. Filtra por "Timber" o el nombre de tu app
3. Inicia la app y realiza acciones
4. Verifica que los logs aparezcan correctamente

**Ejemplo de logs esperados:**
```
D/TabataApp: Iniciando aplicación...
D/SensorManager: Buscando sensor HR...
D/WorkoutService: Workout configurado: GPS=true
```

### 3. Migraciones de Base de Datos

**Qué probar:**
- [ ] Si tienes datos antiguos, se preservan al actualizar
- [ ] La app inicia sin errores de BD
- [ ] Las nuevas tablas (POI, Laps) funcionan correctamente

**Cómo probar:**
1. Si tienes una versión anterior instalada con datos:
   - NO desinstales la app
   - Actualiza la app (instala el nuevo APK)
   - Verifica que tus sesiones anteriores sigan ahí
2. Si es instalación nueva:
   - Crea una sesión de entrenamiento
   - Verifica que se guarde correctamente

### 4. Verificaciones de Permisos Mejoradas

**Qué probar:**
- [ ] GpsManager verifica permisos antes de iniciar GPS
- [ ] SensorManager verifica permisos antes de escanear BLE
- [ ] Los Flows se cierran correctamente si no hay permisos

**Cómo probar:**
1. Ve a Configuración → Apps → Tabata Trainer → Permisos
2. Desactiva "Ubicación"
3. Intenta iniciar un entrenamiento con GPS
4. Verifica que no crashee y muestre un mensaje apropiado

### 5. Constantes Centralizadas

**Qué probar:**
- [ ] La app funciona igual que antes
- [ ] Los valores por defecto son correctos

**Cómo probar:**
1. Inicia un entrenamiento Tabata
2. Verifica que los tiempos por defecto sean correctos (10s warmup, 20s work, 10s rest, 8 rounds)

### 6. ProGuard Rules

**Qué probar:**
- [ ] Compila en modo Release sin errores
- [ ] La app funciona en Release

**Cómo probar:**
```bash
# Compilar Release
gradlew.bat assembleRelease

# O desde Android Studio:
# Build → Generate Signed Bundle / APK → Release
```

## 🐛 Solución de Problemas

### Error: "Unresolved reference: Timber"
**Solución:**
- Sincroniza Gradle de nuevo
- Verifica que `timber:timber:5.0.1` esté en `build.gradle.kts`

### Error: "Unresolved reference: AppConstants"
**Solución:**
- Verifica que `AppConstants.kt` existe en `app/src/main/java/com/tuapp/tabatatrainer/util/`
- Limpia y reconstruye el proyecto

### Error: "Unresolved reference: PermissionHelper"
**Solución:**
- Verifica que `PermissionHelper.kt` existe en `app/src/main/java/com/tuapp/tabatatrainer/util/`
- Limpia y reconstruye el proyecto

### La app crashea al iniciar
**Solución:**
1. Revisa Logcat para ver el error exacto
2. Verifica que Timber esté inicializado en `TabataApp.onCreate()`
3. Verifica que todas las dependencias estén sincronizadas

### Los sensores no conectan
**Solución:**
1. Verifica permisos de Bluetooth y Ubicación
2. Revisa Logcat para ver mensajes de Timber
3. Verifica que el Bluetooth esté activado en el dispositivo

## 📊 Logs a Revisar

En Logcat, busca estos mensajes clave:

**Inicio de app:**
```
D/TabataApp: Iniciando aplicación...
```

**Sensores:**
```
D/SensorManager: Buscando sensor HR...
D/SensorManager: Conectado a HR: [nombre]
```

**GPS:**
```
D/GpsManager: 🛰️ Iniciando GPS tracking...
W/GpsManager: GPS: Sin permisos de ubicación (si faltan permisos)
```

**Servicios:**
```
D/WorkoutService: Workout configurado: GPS=true
D/FreeRideService: 🚴 Iniciando ruta...
```

## 🎯 Pruebas Específicas por Funcionalidad

### Entrenamiento Tabata
1. Configura un entrenamiento
2. Inicia el entrenamiento
3. Verifica que los sensores se conecten
4. Verifica que el timer funcione
5. Finaliza el entrenamiento
6. Verifica que se guarde en el historial

### Ruta Libre
1. Inicia una ruta libre
2. Verifica que GPS se active
3. Verifica que los sensores se conecten
4. Marca algunos LAPs
5. Finaliza la ruta
6. Verifica que se guarde correctamente

### Exportación
1. Completa un entrenamiento
2. Ve al historial
3. Exporta a Excel
4. Verifica que el archivo se cree correctamente

## ✅ Criterios de Éxito

La prueba es exitosa si:
- ✅ La app compila sin errores
- ✅ La app inicia correctamente
- ✅ Los permisos se solicitan y verifican correctamente
- ✅ Los sensores se conectan (si están disponibles)
- ✅ El GPS funciona (si hay permisos)
- ✅ Los logs aparecen en Logcat con formato Timber
- ✅ No hay crashes relacionados con permisos
- ✅ Los datos se guardan correctamente

## 📝 Notas

- Si encuentras algún problema, revisa primero Logcat con filtro "Timber"
- Los logs ahora son más informativos y fáciles de seguir
- Las constantes están centralizadas en `AppConstants.kt` para fácil modificación
