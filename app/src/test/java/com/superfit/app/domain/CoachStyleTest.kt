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
}
