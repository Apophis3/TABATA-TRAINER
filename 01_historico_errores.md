# Histórico de Errores

Registro de errores resueltos durante el desarrollo, para no repetir el diagnóstico en el futuro.

---

## 2026-09-27 — Un solo pulsómetro aparece como HR1 y HR2 (banda dual ANT+/BLE)

### Síntoma
Con una única banda de pulso (dual, emite por ANT+ y BLE a la vez), la app la
mostraba en los dos huecos: HR1 (BLE) y HR2 (ANT+), con dos líneas casi calcadas
en la gráfica. En la captura, HR1=71 bpm (BLE) y HR2=80 bpm (ANT+ con insignia):
mismos latidos, valores distintos por el desfase entre protocolos.

### Causa raíz
`HeartRateHub.detectTwins()` (`sensor/HeartRateHub.kt`) fusiona ANT+ y BLE del
mismo aparato, pero comparaba **muestras instantáneas** con **igualdad estricta**
(`a.bpm == b.bpm`), exigiendo 18 de 20 coincidencias exactas. ANT+ y BLE reportan
a ~1 Hz pero **desfasados**: en reposo coinciden (fusiona), pero al empezar el
Tabata la HR varía y las muestras instantáneas casi nunca son idénticas → nunca
llega al umbral → no fusiona → dos huecos ocupados por el mismo sensor.

### Solución aplicada
Comparar por **media móvil** en vez de igualdad instantánea: ventana de ~10
muestras (`TWIN_WINDOW`), fusionar cuando la **diferencia media absoluta** de bpm
sea ≤ 5 (`TWIN_MAX_AVG_DIFF`) con ≥ 8 muestras (`TWIN_MIN_SAMPLES`). El promedio
cancela el desfase (mismo aparato → media casi igual; personas distintas → media
distinta). Se mantiene el atajo por nombre (Garmin pone el nº ANT+ en el nombre
BLE). `BUILD SUCCESSFUL` con `./gradlew assembleDebug`.

### Lección para el futuro
Para deduplicar el mismo sensor visto por dos protocolos desfasados, no compares
valores instantáneos: usa medias móviles o co-movimiento. La igualdad estricta
solo funciona en reposo, justo cuando no importa.

---

## 2026-09-27 — Crash al iniciar Tabata: Room "cannot verify data integrity"

### Síntoma
Al pulsar "INICIAR" en Tabata (o Ruta Libre) en la tablet, la app se cerraba inmediatamente.

### Log clave
```
java.lang.IllegalStateException: Room cannot verify the data integrity. Looks like you've
changed schema but forgot to update the version number. Expected identity hash: X, found: Y
```
en `BaseRoomConnectionManager.checkIdentity` al abrir `AppDatabase` (`tabata_trainer_db`).

### Causa raíz
**No era un problema de código ni de migraciones.** `AndroidManifest.xml` tiene
`android:allowBackup="true"` sin reglas de exclusión (`dataExtractionRules` /
`fullBackupContent`). Al reinstalar la app (incluso tras `adb uninstall` + reinstalar,
o `./gradlew clean assembleDebug`), Android restauraba automáticamente desde el
Auto Backup de Google una copia antigua de `tabata_trainer_db` con un esquema
desactualizado, que no coincidía con las entidades Room actuales.

Pistas que lo delataron:
- El hash de identidad (expected vs found) era **idéntico** en cada intento, incluso
  tras compilación limpia → indicaba que no era caché de build.
- El archivo de la base de datos recién "instalada" pesaba **6.2 MB**, cuando una
  instalación nueva debería tener una DB casi vacía.
- `firstInstallTime` == `lastUpdateTime` (instalación realmente nueva) pero con datos
  viejos ya presentes.

### Diagnóstico (comandos usados)
```bash
# Ver logcat del crash en vivo
adb logcat -c; adb logcat *:E AndroidRuntime:E

# Confirmar instalación realmente limpia
adb shell dumpsys package com.tuapp.tabatatrainer | Select-String "versionCode|firstInstallTime|lastUpdateTime"

# Inspeccionar tamaño real de la DB restaurada
adb shell run-as com.tuapp.tabatatrainer ls -la databases
```

### Solución aplicada (temporal, para desbloquear pruebas)
```bash
adb shell pm clear com.tuapp.tabatatrainer
```
Esto borra los datos restaurados y deja que Room cree la base de datos desde cero
con el esquema correcto.

### Solución pendiente (permanente)
Excluir `tabata_trainer_db` del Auto Backup en `AndroidManifest.xml` mediante
`dataExtractionRules` (Android 12+) / `fullBackupContent` (versiones anteriores),
para que no se restaure una DB desactualizada en cada instalación de pruebas.
**Estado: propuesta, no implementada aún** (pendiente de autorización explícita,
ya que toca configuración de persistencia — Regla 2 de AGENTS.md).

### Lección para el futuro
Si `checkIdentity` de Room falla de forma **persistente e idéntica** pese a
desinstalar/reinstalar y hacer build limpia, sospechar primero de **Auto Backup**
antes de tocar migraciones o el número de versión de Room. Verificar el tamaño del
archivo de base de datos recién instalado: si es grande, viene de un backup restaurado.
