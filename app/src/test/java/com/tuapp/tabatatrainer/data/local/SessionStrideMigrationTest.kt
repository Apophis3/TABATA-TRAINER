package com.tuapp.tabatatrainer.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Spec 009, R4: la migración 10→11 añade a `workout_sessions` exactamente la columna nueva de
 * [WorkoutSessionEntity] (`strideBySpeed`, texto que puede ser null), sin necesitar Android.
 */
class SessionStrideMigrationTest {

    private fun migratedColumns(): List<Pair<String, String>> =
        DatabaseMigrations.MIGRATION_10_11_STATEMENTS.map {
            val m = Regex("""ALTER TABLE workout_sessions ADD COLUMN (\w+) (.+)""").find(it)!!
            m.groupValues[1] to m.groupValues[2]
        }

    @Test
    fun migration_goes_from_10_to_11_and_is_registered() {
        assertEquals(10, DatabaseMigrations.MIGRATION_10_11.startVersion)
        assertEquals(11, DatabaseMigrations.MIGRATION_10_11.endVersion)
        assertTrue(DatabaseMigrations.ALL_MIGRATIONS.contains(DatabaseMigrations.MIGRATION_10_11))
    }

    @Test
    fun migration_adds_nullable_text_column_that_exists_in_the_entity() {
        assertEquals(listOf("strideBySpeed" to "TEXT"), migratedColumns())
        val field = WorkoutSessionEntity::class.java.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            .single { it.name == "strideBySpeed" }
        assertEquals(String::class.java, field.type)
        assertFalse(DatabaseMigrations.MIGRATION_10_11_STATEMENTS.single().contains("NOT NULL"))
    }

    @Test
    fun old_sessions_have_no_stride_by_speed() {
        val s = WorkoutSessionEntity(id = "x", warmupSeconds = 0, workSeconds = 0, restSeconds = 0, totalRounds = 0)
        assertNull(s.strideBySpeed)
    }
}
