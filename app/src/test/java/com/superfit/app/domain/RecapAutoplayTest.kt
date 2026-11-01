package com.superfit.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecapAutoplayTest {

    @Test
    fun cardTimeGrowsWithTheWordsButStaysInBounds() {
        assertEquals(5_500L, RecapAutoplay.durationMs("Short"))
        assertEquals(10_000L, RecapAutoplay.durationMs("x".repeat(600)))
        assertTrue(RecapAutoplay.durationMs("y".repeat(120)) > RecapAutoplay.durationMs("y".repeat(60)))
    }

    @Test
    fun countsUpDecimalsAndGroupedNumbersTheWayTheCardWritesThem() {
        val kg = RecapAutoplay.parse("1.4")!!
        assertEquals("0.0", RecapAutoplay.format(kg, 0f))
        assertEquals("0.7", RecapAutoplay.format(kg, 0.5f))
        assertEquals("1.4", RecapAutoplay.format(kg, 1f))

        val reps = RecapAutoplay.parse("12,450")!!
        assertEquals("6,225", RecapAutoplay.format(reps, 0.5f))
        assertEquals("12,450", RecapAutoplay.format(reps, 1f))

        val bpm = RecapAutoplay.parse("−6")!!
        assertEquals("−3", RecapAutoplay.format(bpm, 0.5f))
        assertEquals("−6", RecapAutoplay.format(bpm, 1f))
    }

    @Test
    fun leavesPacesAloneAndSkipsTinyNumbers() {
        assertNull(RecapAutoplay.parse("5:42"))
        assertNull(RecapAutoplay.parse("no number"))
        assertFalse(RecapAutoplay.worthCounting(RecapAutoplay.parse("1")!!))
        assertTrue(RecapAutoplay.worthCounting(RecapAutoplay.parse("12")!!))
    }
}
