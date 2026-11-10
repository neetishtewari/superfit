package com.superfit.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepSourceTest {

    @Test
    fun takesTheLargestSingleAppInsteadOfAddingThemUp() {
        // Samsung Health (watch + phone merged) and Google Fit (phone) counting the same day.
        assertEquals(23534L, HealthConnectManager.pickLargest(listOf(23534L, 7066L)) { it.toDouble() })
    }

    @Test
    fun skipsAppsWhoseReadFailed() {
        assertEquals(5000L, HealthConnectManager.pickLargest(listOf(null, 5000L)) { it.toDouble() })
        assertNull(HealthConnectManager.pickLargest(listOf<Long?>(null, null)) { it.toDouble() })
    }
}
