# Tabata Trainer - App de Entrenamiento HIIT con Sensores BLE

App Android nativa para entrenamientos Tabata/HIIT con soporte para sensores Bluetooth de frecuencia cardíaca y cadencia (compatible con Garmin y otros sensores BLE estándar).

## 📱 Características

- **Timer Tabata configurable**: Calentamiento, trabajo, descanso y número de rondas
- **Conexión BLE automática**: Sensores de frecuencia cardíaca (HR) y cadencia (CSC)
- **Feedback visual**: Colores por fase (naranja/verde/rojo)
- **Vibración**: Alertas en últimos segundos y cambios de fase
- **Persistencia**: Historial de sesiones en base de datos local
- **Estadísticas**: FC media, máxima, mínima y cadencia
- **Foreground Service**: Funciona con pantalla apagada

## 🔧 Requisitos

- **Android Studio** Hedgehog (2023.1.1) o superior
- **JDK 17** o superior
- **Android SDK 34** (API level 34)
- **Dispositivo Android** con BLE (mínimo Android 8.0 / API 26)

## 📦 Instalación y Compilación

### Opción 1: Abrir en Android Studio (Recomendado)

1. **Descarga e instala Android Studio**:
   - Descarga desde: https://developer.android.com/studio
   - Instala siguiendo el asistente

2. **Abre el proyecto**:
   ```
   File → Open → Selecciona la carpeta TabataTrainer
   ```

3. **Sincroniza Gradle**:
   - Android Studio lo hará automáticamente
   - Si no, click en "Sync Project with Gradle Files" (icono elefante)

4. **Compila el APK**:
   ```
   Build → Build Bundle(s) / APK(s) → Build APK(s)
   ```
   
5. **El APK estará en**:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

### Opción 2: Línea de comandos

```bash
# En la carpeta del proyecto
cd TabataTrainer

# En Linux/Mac
./gradlew assembleDebug

# En Windows
gradlew.bat assembleDebug

# El APK se genera en:
# app/build/outputs/apk/debug/app-debug.apk
```

### Opción 3: Instalar directamente en dispositivo conectado

```bash
# Con dispositivo conectado por USB (depuración USB activada)
./gradlew installDebug
```

## 📲 Instalación del APK en tu Android

1. **Activa "Orígenes desconocidos"** en tu teléfono:
   - Ajustes → Seguridad → Orígenes desconocidos (o "Instalar apps desconocidas")

2. **Transfiere el APK** a tu teléfono (USB, Drive, email, etc.)

3. **Abre el APK** y acepta la instalación

4. **Concede permisos** cuando la app los solicite:
   - Bluetooth (escaneo y conexión)
   - Ubicación (requerido para BLE en Android)
   - Notificaciones

## 🚴 Sensores Compatibles

### Frecuencia Cardíaca (HR)
- Cualquier sensor BLE con servicio `0x180D` (Heart Rate Service)
- Garmin HRM-Dual, HRM-Pro, HRM-Run
- Polar H9, H10, OH1
- Wahoo TICKR
- Otros monitores BLE genéricos

### Cadencia (CSC)
- Cualquier sensor BLE con servicio `0x1816` (Cycling Speed and Cadence)
- Garmin Cadence Sensor 2
- Wahoo RPM Cadence
- Otros sensores de cadencia BLE

## 🎯 Uso de la App

1. **Pantalla de Configuración**:
   - Ajusta tiempos de calentamiento, trabajo y descanso
   - Selecciona número de rondas
   - Pulsa "INICIAR ENTRENAMIENTO"

2. **Pantalla de Entrenamiento**:
   - Los sensores se conectan automáticamente
   - Indicadores de conexión arriba (verde = conectado)
   - Timer grande con segundos restantes
   - Indicador de ronda actual
   - Métricas de HR y cadencia en tiempo real
   - Controles: Play/Pause/Stop

3. **Al finalizar**:
   - Resumen con estadísticas
   - Sesión guardada automáticamente

## 🐛 Solución de Problemas

### Los sensores no conectan
1. Verifica que Bluetooth esté activado
2. Asegúrate de que la ubicación esté activada (requerido en Android para BLE)
3. Los sensores deben estar en modo de emparejamiento/transmisión
4. Reinicia los sensores si llevan mucho tiempo encendidos

### La app se cierra en segundo plano
- La app usa un Foreground Service, debería mantenerse activa
- En algunos fabricantes (Xiaomi, Huawei, Samsung), debes desactivar la "optimización de batería" para esta app

### Error de permisos
- Ve a Ajustes → Apps → Tabata Trainer → Permisos
- Concede todos los permisos necesarios

## 📁 Estructura del Proyecto

```
TabataTrainer/
├── app/
│   ├── src/main/
│   │   ├── java/com/tuapp/tabatatrainer/
│   │   │   ├── MainActivity.kt          # Activity principal
│   │   │   ├── TabataApp.kt              # Application con Hilt
│   │   │   ├── data/local/               # Room DB, Entities, DAOs
│   │   │   ├── di/                       # Módulos Hilt
│   │   │   ├── navigation/               # NavGraph
│   │   │   ├── sensor/                   # Managers BLE
│   │   │   ├── service/                  # Foreground Service
│   │   │   └── ui/                       # Screens Compose
│   │   ├── AndroidManifest.xml
│   │   └── res/
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## 🔐 Permisos Requeridos

```xml
<!-- Bluetooth -->
BLUETOOTH_SCAN
BLUETOOTH_CONNECT
ACCESS_FINE_LOCATION (requerido para BLE en Android)

<!-- Servicio en primer plano -->
FOREGROUND_SERVICE
FOREGROUND_SERVICE_CONNECTED_DEVICE
POST_NOTIFICATIONS

<!-- Otros -->
VIBRATE
WAKE_LOCK
```

## 📄 Licencia

Proyecto educativo de código abierto.

## 🤝 Contribuciones

¡Las contribuciones son bienvenidas! Abre un issue o pull request.

---

**Nota**: Esta app es para uso personal/educativo. Consulta a un profesional de la salud antes de comenzar cualquier programa de entrenamiento intenso.
