package com.superfit.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachStyleTest {

    @Test
    fun unknownOrMissingStyleFallsBackToSupportive() {
        assertEquals(CoachStyle.SUPPORTIVE, CoachStyle.fromName(null))
        assertEquals(CoachStyle.SUPPORTIVE, CoachStyle.fromName("SOMETHING_OLD"))
        assertEquals(CoachStyle.ROAST, CoachStyle.fromName("ROAST"))
    }

    @Test
    fun supportiveAddsNothingToTheInstruction() {
        assertEquals("", CoachStyle.SUPPORTIVE.instruction())
    }

    @Test
    fun roastKeepsItsSafetyLimits() {
        val roast = CoachStyle.ROAST.instruction()
        assertTrue(roast.contains("never the person"))
        assertTrue(roast.contains("disordered eating"))
        assertTrue(roast.contains("accurate, safe and useful"))
    }

    @Test
    fun roastAsksForShortPlainJokes() {
        val roast = CoachStyle.ROAST.instruction()
        assertTrue(roast.contains("No long winding sentences"))
        assertTrue(roast.contains("No layered sarcasm"))
        assertTrue(!roast.contains("loaded sarcasm"))
    }

    @Test
    fun roastReminderRotatesAndShowsTheNumbers() {
        val lines = (0..2).map { CoachStyle.ROAST.logReminder(800, 2000, it) }
        assertEquals(3, lines.toSet().size)
        lines.forEach { (_, text) -> assertTrue(text.contains("800") && text.contains("2000")) }
    }
}
