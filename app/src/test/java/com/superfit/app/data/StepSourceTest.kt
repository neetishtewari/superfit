package com.superfit.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepSourceTest {

    private fun pick(vararg perApp: Pair<String, Long?>) = HealthConnectManager.pickSource(perApp.toList()) { it.toDouble() }

    private val samsung = "com.sec.android.app.shealth"
    private val googleFit = "com.google.android.apps.fitness"
    private val phone = "com.android.healthconnect.phone.abc"

    @Test
    fun takesTheWatchAppWhenGoogleFitIsLower() {
        // 10 Nov on a real phone: merged total was 31,727, the watch said ~23,500.
        assertEquals(23562L, pick(googleFit to 11391L, samsung to 23562L, phone to 11705L))
    }

    @Test
    fun takesTheWatchAppEvenWhenGoogleFitAddsWatchAndPhoneTogether() {
        // 2 Nov on the same phone: Google Fit's 27,237 is about the watch plus the phone.
        assertEquals(15647L, pick(googleFit to 27237L, samsung to 15647L, phone to 13625L))
    }

    @Test
    fun withoutAWatchAppTakesTheLargestOfTheRest() {
        assertEquals(9000L, pick(googleFit to 9000L, phone to 8500L))
    }

    @Test
    fun skipsAppsWithNothingOrAFailedRead() {
        assertEquals(5000L, pick(samsung to null, phone to 5000L))
        assertEquals(5000L, pick(samsung to 0L, phone to 5000L))
        assertNull(pick(samsung to null, phone to null))
    }
}
