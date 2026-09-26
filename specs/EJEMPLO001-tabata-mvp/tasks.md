

### 📋 3. `specs/001-tabata-mvp/tasks.md`


# Tareas — Spec 001 (Tabata Trainer)

- [ ] **T1. Refactorización en `SensorManager.kt`: Concurrencia BLE/ANT+**
      (RF-1, RF-2) 
      **Hecho cuando:** Test unitario en `SensorManagerTest` confirme que la recepción simultánea de datos de dispositivos con direcciones MAC/IDs distintas actualiza `hr1State` y `hr2State` de forma independiente sin perder paquetes[cite: 4, 5].

- [ ] **T2. Extensión del modelo Room: `SessionEntity` y `SessionDao`**
      (RF-6, RF-8) 
      **Hecho cuando:** Test de integración con base de datos en memoria (`Room.inMemoryDatabaseBuilder`) guarde y recupere exitosamente un objeto `SessionEntity` con todos los campos de HR1, HR2, C1, C2 y Velocidad Media[cite: 4].

- [ ] **T3. Cálculo de Métricas en `WorkoutService` y `FreeRideService`**
      (RF-4, RF-5, RF-9) 
      **Hecho cuando:** Test unitario en el Servicio simule la recepción de un stream de datos y verifique que al llamar a `finishSession()` se calcule correctamente la media y máxima, entregando la entidad lista a Room DB[cite: 4].

- [ ] **T4. Corrección del Buffer de Gráfica en `HeartRateGraph.kt`**
      (RF-3) 
      **Hecho cuando:** La gráfica reciba una lista de 500 puntos (representando >10 minutos) y los renderice completamente en el Canvas de Compose en lugar de limitar la ventana a los últimos 120 segundos[cite: 4, 5].

- [ ] **T5. Integración del Informe en `SessionDetailScreen.kt`**
      (RF-7) 
      **Hecho meante:** Verificación visual o test de UI Compose donde la pantalla reciba el `sessionId` recién guardado por T3 y muestre todas las tarjetas de métricas (HR1, HR2, Cadencias y Velocidad) con valores mayores a cero[cite: 4].

- [ ] **T6. Validación E2E del Flujo Completo (Smoke Test)**
      (Todos los RF) 
      **Hecho cuando:** Se ejecute `./gradlew test` en verde, seguido de un ciclo manual completo en emulador (Iniciar Tabata ➔ Conectar Sensores ➔ Finalizar ➔ Ver Resumen) sin cierres inesperados ni datos perdidos[cite: 4, 5].