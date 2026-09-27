# Guía Rápida de Android Studio para Windows 11
> **Objetivo:** Mantener un flujo de trabajo limpio, ordenado y eficiente durante el proceso de refactorización, compilación con Gradle y transmisión de la APK a un dispositivo Android.

---
Secuencia Exacta para Probar Cambios en el Dispositivo / Tablet
Sigue este orden visual o mediante teclados cada vez que quieras reinstalar una versión limpia de tus cambios:
### Paso 1: Sincronizar Gradle (Solo si cambiaste dependencias o librerías)
•	Por Menú: File $\rightarrow$ Sync Project with Gradle Files
•	Atajo de teclado: Ctrl + Shift + O
### Paso 2: Limpiar Caché Antigua (Clean Project)
•	Por Menú: Build $\rightarrow$ Clean Project
•	Atajo de teclado: Ctrl + Shift + A $\rightarrow$ Escribir Clean Project y presionar Enter
### Paso 3: Reconstruir Todo el Proyecto (Rebuild)
•	Por Menú: Build $\rightarrow$ Rebuild Project
•	Atajo de teclado: Ctrl + F9
### Paso 4: Generar el Archivo APK
•	Por Menú: Build $\rightarrow$ Build Bundle(s) / APK(s) $\rightarrow$ Build APK(s)
### Paso 5: Enviar e Instalar en la Tablet
•	Visual: Clic en el Triángulo Verde ▶ (Run 'app') en la barra superior.
•	Atajo de teclado: Shift + F10




1. **Refactorización Limpia:** Cambia nombres, estructura y archivos usando las herramientas de refactorización automáticas para evitar romper dependencias en XML o referencias indirectas.
2. **Sincronización:** Tras cualquier cambio en archivos `build.gradle.kts` o manifest, sincroniza el entorno.
3. **Limpieza de Binarios (Clean):** Elimina versiones previas compiladas y cachés temporales para evitar errores de binarios obsoletos.
4. **Reconstrucción (Rebuild):** Genera desde cero todos los binarios asegurando que no existan errores de sintaxis o ensamblado.
5. **Generación / Despliegue:** Genera el `.apk` firmado/debug o instala directamente vía USB / Wi-Fi con ADB.

---

## 2. Atajos de Teclado en Windows 11

### A. Reestructuración y Calidad de Código
| Acción | Atajo (Windows 11) | Descripción |
| :--- | :--- | :--- |
| **Renombrar elemento** | `Shift` + `F6` | Cambia el nombre de variables, clases o archivos en todo el proyecto de forma segura. |
| **Formatear código** | `Ctrl` + `Alt` + `L` | Aplica sangrías, espacios y reglas estéticas según las guías de estilo. |
| **Optimizar importaciones** | `Ctrl` + `Alt` + `O` | Remueve librerías no utilizadas y organiza las necesarias. |
| **Mover líneas** | `Alt` + `Shift` + `Arriba` / `Abajo` | Desplaza el bloque de código activo sin necesidad de cortar y pegar. |
| **Extraer Variable** | `Ctrl` + `Alt` + `V` | Convierte una expresión seleccionada en una variable local. |
| **Extraer Método/Función** | `Ctrl` + `Alt` + `M` | Mueve el código seleccionado a una nueva función separada. |
| **Eliminar línea** | `Ctrl` + `Y` | Borra la línea actual por completo. |
| **Duplicar línea** | `Ctrl` + `D` | Duplica la línea o selección actual inmediatamente abajo. |

---

### B. Compilación y Sistema Gradle
| Acción | Atajo (Windows 11) | Descripción |
| :--- | :--- | :--- |
| **Sincronizar Gradle** | `Ctrl` + `Shift` + `O` | Actualiza dependencias y estructura tras editar scripts Gradle. |
| **Compilar (Make Project)** | `Ctrl` + `F9` | Revisa sintaxis y compila los módulos modificados. |
| **Buscar Acción del Menú** | `Ctrl` + `Shift` + `A` | Abre el buscador de comandos (útil para ejecutar *"Clean Project"* o *"Rebuild Project"*). |

---

### C. Generación, Despliegue y Pruebas
| Acción | Atajo (Windows 11) | Descripción |
| :--- | :--- | :--- |
| **Ejecutar App (Run)** | `Shift` + `F10` | Compila, transfiere e instala la app en el dispositivo seleccionado. |
| **Depurar App (Debug)** | `Shift` + `F9` | Ejecuta la app vinculada al depurador con puntos de interrupción (*breakpoints*). |
| **Cambios en Vivo (Apply Changes)** | `Ctrl` + `F10` | Aplica cambios de código en la app en ejecución sin reinstalar por completo. |
| **Seleccionar Dispositivo** | `Alt` + `Shift` + `F10` | Abre el menú rápido para elegir el emulador o dispositivo físico de destino. |
| **Abrir Terminal Integrada** | `Alt` + `F12` | Acceso rápido a la consola de Windows para ejecutar comandos `adb`. |

---

### D. Navegación Rápida
| Acción | Atajo (Windows 11) | Descripción |
| :--- | :--- | :--- |
| **Búsqueda Global** | `Doble Shift` | Busca cualquier cosa: archivos, clases, configuraciones y acciones. |
| **Buscar Clase** | `Ctrl` + `N` | Busca únicamente clases Kotlin/Java por su nombre. |
| **Buscar Archivo** | `Ctrl` + `Shift` + `N` | Busca layouts XML, imágenes, recursos o archivos de configuración. |
| **Ir a la Declaración** | `Ctrl` + `B` o `Ctrl` + `Clic` | Salta al origen o definición de la variable, clase o método. |
| **Panel de Proyecto** | `Alt` + `1` | Muestra u oculta la barra lateral con la estructura de carpetas del proyecto. |

---

## 3. Comandos Útiles para el Terminal ADB (`Alt + F12`)

Si prefieres trabajar con comandos para gestionar la APK desde la consola interna:

* **Verificar dispositivos conectados:**
  ```bash
  adb devices
  ```
* **Instalar APK manualmente:**
  ```bash
  adb install app/build/outputs/apk/debug/app-debug.apk
  ```
* **Reinstalar manteniendo datos de usuario:**
  ```bash
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```
* **Desinstalar la aplicación:**
  ```bash
  adb uninstall com.tu_paquete.tu_aplicacion
  ```