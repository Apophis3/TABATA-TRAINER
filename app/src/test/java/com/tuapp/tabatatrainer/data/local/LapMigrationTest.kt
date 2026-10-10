package com.tuapp.tabatatrainer.data.local

import com.tuapp.tabatatrainer.service.LapData
import com.tuapp.tabatatrainer.service.toEntity
import com.tuapp.tabatatrainer.util.laps.CadenceSource
import com.tuapp.tabatatrainer.util.laps.LapType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Coherencia del esquema 10 de `lap_records` (spec 006, T-04) sin necesitar Android:
 * la migración 9→10 añade exactamente las columnas nuevas de [LapEntity].
 */
class LapMigrationTest {

    private val columnsV9 = setOf(
        "id", "sessionId", "lapNumber", "startTimestamp", "endTimestamp",
        "startDistanceMeters", "endDistanceMeters", "avgSpeedKmh", "maxSpeedKmh",
        "avgHeartRate", "maxHeartRate", "avgCadence", "elevationGain"
    )

    private fun entityColumns(): Set<String> =
        LapEntity::class.java.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            .map { it.name }
            .toSet()

    private fun migratedColumns(): List<String> =
        DatabaseMigrations.MIGRATION_9_10_STATEMENTS.map {
            Regex("""ALTER TABLE lap_records ADD COLUMN (\w+) """).find(it)!!.groupValues[1]
        }

    @Test
    fun migration_goes_from_9_to_10_and_is_registered() {
        assertEquals(9, DatabaseMigrations.MIGRATION_9_10.startVersion)
        assertEquals(10, DatabaseMigrations.MIGRATION_9_10.endVersion)
        assertTrue(DatabaseMigrations.ALL_MIGRATIONS.contains(DatabaseMigrations.MIGRATION_9_10))
    }

    @Test
    fun migrations_form_an_unbroken_chain_to_the_current_version() {
        val byStart = DatabaseMigrations.ALL_MIGRATIONS.associateBy { it.startVersion }
        // 4→5→6→7 y 8→9→10 (el 7→8 no cambió el esquema y no tiene migración propia)
        assertEquals(10, byStart.getValue(9).endVersion)
        assertEquals(9, byStart.getValue(8).endVersion)
    }

    @Test
    fun migration_adds_exactly_the_new_entity_columns() {
        val newColumns = entityColumns() - columnsV9
        assertEquals(setOf("type", "typeNumber", "steps", "cadenceSource"), newColumns)
        assertEquals(newColumns, migratedColumns().toSet())
        assertEquals(migratedColumns().size, migratedColumns().toSet().size)
    }

    @Test
    fun entity_keeps_every_v9_column() {
        assertTrue(entityColumns().containsAll(columnsV9))
    }

    @Test
    fun not_null_columns_have_a_default_so_existing_rows_survive() {
        DatabaseMigrations.MIGRATION_9_10_STATEMENTS
            .filter { it.contains("NOT NULL") }
            .forEach { assertTrue("Falta DEFAULT en: $it", it.contains("DEFAULT")) }
    }

    @Test
    fun lap_data_maps_to_entity_with_type_and_numbering() {
        val lap = LapData(
            number = 5,
            type = LapType.AUTO,
            typeNumber = 3,
            startTimeSeconds = 660,
            endTimeSeconds = 990,
            distanceMeters = 1000f,
            startDistanceMeters = 2000f,
            avgSpeedKmh = 10.9f,
            maxSpeedKmh = 13f,
            avgHeartRate = 150,
            maxHeartRate = 172,
            avgCadence = 0f,
            cadenceSource = CadenceSource.NONE,
            startTimestamp = 1_000L,
            timestamp = 331_000L
        )
        val e = lap.toEntity("sess-1")

        assertEquals("sess-1", e.sessionId)
        assertEquals(5, e.lapNumber)           // orden global
        assertEquals("AUTO", e.type)
        assertEquals(3, e.typeNumber)          // número dentro de su serie
        assertEquals(2000f, e.startDistanceMeters, 0f)
        assertEquals(3000f, e.endDistanceMeters, 0f)
        assertEquals(1_000L, e.startTimestamp)
        assertEquals(331_000L, e.endTimestamp)
        assertEquals(150, e.avgHeartRate)
        assertEquals(172, e.maxHeartRate)
        assertNull(e.avgCadence)               // 0 = sin dato
        assertEquals("NONE", e.cadenceSource)
    }

    @Test
    fun manual_lap_without_hr_saves_null_instead_of_zero() {
        val lap = LapData(
            number = 1, startTimeSeconds = 0, endTimeSeconds = 12, distanceMeters = 0f,
            avgSpeedKmh = 0f, avgHeartRate = 0
        )
        val e = lap.toEntity("s")
        assertEquals("MANUAL", e.type)
        assertNull(e.avgHeartRate)
        assertNull(e.maxHeartRate)
        assertNull(e.steps)
    }
}
