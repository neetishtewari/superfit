package com.superfit.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDateTime

class DayChangeTest {
    @Test
    fun waitsUntilJustAfterMidnight() {
        assertEquals(60_000L + 1000, DayChange.millisToNextDay(LocalDateTime(2026, 11, 16, 23, 59, 0)))
        assertEquals(24L * 3600 * 1000 + 1000, DayChange.millisToNextDay(LocalDateTime(2026, 11, 16, 0, 0, 0)))
        assertEquals(12L * 3600 * 1000 + 1000, DayChange.millisToNextDay(LocalDateTime(2026, 11, 16, 12, 0, 0)))
    }
}
