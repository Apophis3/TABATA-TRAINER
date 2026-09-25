**Mostrar la versión dentro de la app**
mostrar la versión directamente en la interfaz de la app (por ejemplo, en la pantalla de Inicio, en el menú de Ajustes o en el pie de página).


# 📱 EXPLICACIÓN DE PANTALLAS - FLUJO TABATA

## 🎯 CONFIRMACIÓN DE PANTALLAS (según tus capturas)

### 1. **HOME SCREEN** ✅
**Captura:** Segunda imagen  
**Descripción:** Pantalla principal con "TABATA TRAINER", "S. CELIS", estado de sensores, y botones "RUTA LIBRE" y "TABATA"

**Función:** Punto de entrada de la app. Muestra estado de sensores y permite elegir actividad.

---

### 2. **CONFIG SCREEN** ✅
**Captura:** Primera y cuarta imagen  
**Descripción:** Pantalla de configuración con sliders para Calentamiento, Trabajo, Descanso, Rondas, y banner "EN CURSO" con botón "IR"

**Función:** 
- Configurar parámetros del entrenamiento Tabata antes de iniciar
- **Muestra banner "EN CURSO"** cuando hay un entrenamiento activo en segundo plano
- Permite reanudar un entrenamiento activo con el botón "IR"

**⚠️ PROBLEMA IDENTIFICADO Y CORREGIDO:**
- El botón "IR" no funcionaba cuando se volvía desde RUTA
- **SOLUCIÓN:** Ahora obtiene la configuración directamente del `WorkoutService` activo y navega correctamente

---

### 3. **WORKOUT SCREEN - Estado "PREPARADO"** ⚠️
**Captura:** Quinta imagen  
**Descripción:** Pantalla con texto "Preparado", "Esperando datos HR...", y métricas de sensores (HR1, HR2, CAD) con valores "--"

**Función:** 
- Pantalla de espera ANTES de iniciar el entrenamiento
- Muestra "Preparado" cuando `workoutState.phase == WorkoutPhase.IDLE`
- El usuario debe presionar el botón "Iniciar" para comenzar

**❓ ¿ES NECESARIA?**
- **SÍ**, pero podría mejorarse. Esta pantalla aparece porque:
  1. `WorkoutScreen` se crea cuando navegas desde `ConfigScreen`
  2. El servicio se inicia y configura, pero el entrenamiento aún no ha comenzado
  3. El usuario debe presionar "Iniciar" explícitamente

**💡 MEJORA SUGERIDA:** 
- Podríamos hacer que al llegar a `WorkoutScreen` se inicie automáticamente si viene de `ConfigScreen` con el botón "Iniciar"
- O mostrar un botón grande "INICIAR ENTRENAMIENTO" más visible

---

### 4. **WORKOUT SCREEN - Estado "Calentamiento/Trabajo/Descanso"** ✅
**Captura:** Tercera imagen  
**Descripción:** Pantalla con "Calentamiento", timer grande "07", "Ronda 0/8", "Tiempo Total 03", gráfica de HR, y controles (Stop, Pause)

**Función:**
- **Pantalla principal durante el entrenamiento activo**
- Muestra timer, fase actual, rondas, métricas en tiempo real
- Gráfica de frecuencia cardíaca (HR1 y HR2)
- Controles: Start, Pause, Resume, Stop
- **AQUÍ SÍ DETECTA SENSORES** porque el entrenamiento está corriendo

**Esta es la pantalla más importante durante el entrenamiento.**

---

## 🔄 FLUJO COMPLETO

```
1. HOME SCREEN
   ↓ (Click "TABATA")
   
2. CONFIG SCREEN
   - Configurar parámetros
   - Ver estado de sensores
   ↓ (Click "INICIAR")
   
3. WORKOUT SCREEN (Estado: "PREPARADO")
   - Muestra "Preparado"
   - Esperando que presiones "Iniciar"
   - Sensores pueden no estar conectados aún
   ↓ (Click botón "Iniciar" / Play)
   
4. WORKOUT SCREEN (Estado: "Calentamiento/Trabajo/Descanso")
   - Entrenamiento ACTIVO
   - Timer corriendo
   - Sensores detectando datos
   - Gráficas y métricas en tiempo real
   ↓ (Click flecha atrás)
   
5. HOME SCREEN
   - Entrenamiento sigue corriendo en segundo plano
   ↓ (Click "TABATA" de nuevo)
   
6. CONFIG SCREEN
   - Muestra banner "EN CURSO" con botón "IR"
   - Permite volver al entrenamiento activo
   ↓ (Click "IR")
   
7. WORKOUT SCREEN (Estado: "Calentamiento/Trabajo/Descanso")
   - Vuelves al entrenamiento activo
```

---

## 🐛 PROBLEMA REPORTADO Y SOLUCIONADO

### **Problema:**
Cuando estabas en `HOME SCREEN` → `RUTA` → y luego querías volver al TABATA activo:
1. Aparecías en `CONFIG SCREEN` con el banner "EN CURSO"
2. El botón "IR" no funcionaba
3. La app se bloqueaba y necesitabas cerrarla forzadamente

### **Causa:**
El botón "IR" intentaba navegar usando `lastWorkoutConfig` que podía estar `null` o desincronizado cuando se cambiaba entre actividades.

### **Solución Implementada:**
1. ✅ Agregado método `getCurrentConfig()` en `WorkoutService` para obtener la configuración real del servicio activo
2. ✅ Modificado `ConfigScreen` para obtener la configuración del servicio cuando se hace clic en "IR"
3. ✅ Modificado `NavGraph` para usar la configuración del servicio en lugar de `lastWorkoutConfig`
4. ✅ Mejorada la navegación para evitar loops y problemas de back stack

**Ahora el botón "IR" funciona correctamente** obteniendo la configuración directamente del servicio activo.

---

## 💡 RECOMENDACIONES

### 1. **Pantalla "PREPARADO"**
- **Opción A:** Iniciar automáticamente cuando se llega desde `ConfigScreen` con "Iniciar"
- **Opción B:** Hacer el botón "Iniciar" más visible y grande
- **Opción C:** Eliminar esta pantalla y empezar directamente cuando se navega desde `ConfigScreen`

### 2. **Detección de sensores en "PREPARADO"**
- Los sensores pueden no estar conectados aún porque el entrenamiento no ha comenzado
- El servicio se inicia pero los sensores se activan cuando se llama a `startWorkout()`
- **Solución:** Iniciar el escaneo de sensores inmediatamente al llegar a `WorkoutScreen`, no solo cuando se inicia el entrenamiento

### 3. **Navegación mejorada**
- Considerar agregar un indicador visual cuando hay un entrenamiento activo en segundo plano
- Mostrar notificación persistente con acceso rápido al entrenamiento

---

## ✅ CAMBIOS REALIZADOS

1. ✅ Corregido el botón "IR" en `ConfigScreen` para que funcione correctamente
2. ✅ Agregado método `getCurrentConfig()` en `WorkoutService`
3. ✅ Mejorada la navegación en `NavGraph` para evitar problemas de back stack
4. ✅ El botón "IR" ahora obtiene la configuración directamente del servicio activo

---

**¿Quieres que implemente alguna de las mejoras sugeridas para la pantalla "PREPARADO"?**
