package com.superfit.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth

class RecapReminderTest {
    private val name = { m: YearMonth -> m.month.name.lowercase().replaceFirstChar { it.uppercase() } }

    @Test
    fun aReadyMonthIsRemindedAt10OnTheFirstOfNextMonth() {
        val note = RecapData.reminder(MemoryPrefs(), LocalDateTime(2026, 11, 12, 22, 0), thisMonthReady = true, lastMonthReady = true, monthName = name)!!
        assertEquals("recap-2026-11", note.id)
        assertEquals("Your November review is ready", note.title)
    }

    @Test
    fun earlyOnTheFirstItRemindsAboutLastMonthUnlessOpened() {
        val prefs = MemoryPrefs()
        val early = LocalDateTime(2026, 12, 1, 8, 0)
        assertEquals("recap-2026-11", RecapData.reminder(prefs, early, thisMonthReady = false, lastMonthReady = true, monthName = name)?.id)
        RecapData.markOpened(prefs, YearMonth(2026, 11), LocalDate(2026, 12, 1))
        assertNull(RecapData.reminder(prefs, early, thisMonthReady = false, lastMonthReady = true, monthName = name))
    }

    @Test
    fun nothingWhenThereIsTooLittleLogged() {
        assertNull(RecapData.reminder(MemoryPrefs(), LocalDateTime(2026, 11, 12, 9, 0), thisMonthReady = false, lastMonthReady = false, monthName = name))
    }
}
