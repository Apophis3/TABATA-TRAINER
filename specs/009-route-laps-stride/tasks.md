# Tasks 009 — Ruta entera, confirmación de vuelta y zancada por velocidad

Reglas: el agente **no** ejecuta Gradle ni `adb install` (Clave 5). Se trabaja en `main`.
El usuario aprobó en el chat (2026-11-28) hacer las tres tareas seguidas, sin esperar entre ellas, porque iba a
estar una hora fuera: se programan T-01, T-02 y T-03 y se validan juntas al volver.

- [ ] T-01 **Ruta entera + «Ver todo».** `RouteSimplifier`, `FreeRideService.addMapPoint` sin tope,
  `MapFollowPolicy` con `overview`, botón en `RealTimeGoogleMap`. Tests: `RouteSimplifierTest`, `MapFollowPolicyTest`.
- [ ] T-02 **Confirmación de vuelta.** `LapMark`, `lapMarks`, `lapEvents`, vibración corta en `addLap`, aviso
  «🏁 Vuelta N» con zoom y marcadores amarillos en el mapa.
- [ ] T-03 **Zancada por velocidad.** `StrideSpeedBins`, columna `strideBySpeed` (Room 10→11), guardado al parar y
  tarjeta en el detalle. Tests: `StrideSpeedBinsTest`, `LapMigrationTest`.
- [ ] T-04 **Prueba (usuario).** Compilar, `./gradlew test`, instalar y una ruta corta andando (guion en el chat).
  Evidencia: número de tests, capturas del mapa con «Ver todo», de una vuelta marcada y de la tarjeta de zancada.
