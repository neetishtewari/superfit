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

    @Test
    fun roastAsksForAboutTwoThirdsOfEveryWordLimit() {
        val roast = CoachStyle.ROAST.instruction()
        assertTrue(roast.contains("8 words or fewer"))
        assertTrue(roast.contains("two thirds of any word limit"))
        assertTrue(CoachChatContext.RULES.contains("at most 16 words"))
    }

    @Test
    fun roastLinesWrittenByTheAppAreShortToo() {
        fun words(text: String) = text.split(Regex("""\s+""")).count { it.isNotBlank() }
        assertTrue(words(CoachStyle.ROAST.sample) <= 12)
        (0..2).forEach { day -> assertTrue(words(CoachStyle.ROAST.logReminder(800, 2000, day).second) <= 12) }
        assertTrue(words(CoachStyle.ROAST.completeDayFallback(1800).second) <= 14)
    }
}
