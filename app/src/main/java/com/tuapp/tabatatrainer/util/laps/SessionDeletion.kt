package com.tuapp.tabatatrainer.util.laps

/**
 * Borrado de una sesión con todo lo que cuelga de ella (spec 006, RF-P2). Las tablas no tienen
 * claves foráneas, así que no hay cascada en Room: se hace aquí, en un único sitio, para que el
 * historial y el detalle borren exactamente lo mismo. Hijos primero y la sesión al final: si algo
 * falla a medias, la sesión sigue visible y se puede reintentar.
 */
object SessionDeletion {
    suspend fun deleteWithChildren(
        deleteLaps: suspend () -> Unit,
        deleteGpsPoints: suspend () -> Unit,
        deleteSensorReadings: suspend () -> Unit,
        deleteSession: suspend () -> Unit
    ) {
        deleteLaps()
        deleteGpsPoints()
        deleteSensorReadings()
        deleteSession()
    }
}
