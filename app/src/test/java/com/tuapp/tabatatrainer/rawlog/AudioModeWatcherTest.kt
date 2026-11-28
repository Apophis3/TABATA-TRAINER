package com.tuapp.tabatatrainer.rawlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioModeWatcherTest {

    @Test
    fun first_reading_is_the_initial_state() {
        val w = AudioModeWatcher()
        assertEquals("mode=normal,initial", w.observe(0))
        assertEquals(1, w.changes)
    }

    @Test
    fun an_event_only_when_the_mode_changes() {
        val w = AudioModeWatcher()
        // Lecturas cada 5 s: normal, normal, suena, suena, en llamada ×3, normal, normal.
        val events = listOf(0, 0, 1, 1, 2, 2, 2, 0, 0).map { w.observe(it) }
        assertEquals(
            listOf("mode=normal,initial", null, "mode=ringtone", null, "mode=in_call", null, null, "mode=normal", null),
            events
        )
        assertEquals(4, w.changes)
    }

    @Test
    fun communication_and_unknown_modes_have_names() {
        val w = AudioModeWatcher()
        w.observe(0)
        assertEquals("mode=in_communication", w.observe(3))
        assertEquals("mode=mode_9", w.observe(9))
        assertNull(w.observe(9))
    }
}
