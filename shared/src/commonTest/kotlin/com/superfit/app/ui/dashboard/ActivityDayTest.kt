package com.superfit.app.ui.dashboard

import com.superfit.app.data.SleepTelemetryEntity
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class ActivityDayTest {

    private val today = LocalDate.parse("2026-11-17")
    private fun night(date: String) = SleepTelemetryEntity(date, 25_200L, 5_040L, 87)
    private val tracked = listOf(night("2026-11-16"), night("2026-11-15"))

    @Test
    fun aSleepTrackerKeepsYesterdayUntilTheNightArrives() {
        assertTrue(ActivityDay.showYesterday(hour = 0, today = today, todaysSleep = null, recentSleep = tracked))
        // Asleep after midnight and still not up at 9: still yesterday.
        assertTrue(ActivityDay.showYesterday(hour = 9, today = today, todaysSleep = null, recentSleep = tracked))
        // Woke up: the night ending today is in, so today it is.
        assertFalse(ActivityDay.showYesterday(hour = 6, today = today, todaysSleep = night("2026-11-17"), recentSleep = tracked))
    }

    @Test
    fun noOneStaysOnYesterdayPastMidday() {
        assertFalse(ActivityDay.showYesterday(hour = 12, today = today, todaysSleep = null, recentSleep = tracked))
        assertFalse(ActivityDay.showYesterday(hour = 23, today = today, todaysSleep = null, recentSleep = tracked))
    }

    @Test
    fun withoutASleepTrackerTheDayTurnsAtFour() {
        assertTrue(ActivityDay.showYesterday(hour = 3, today = today, todaysSleep = null, recentSleep = emptyList()))
        assertFalse(ActivityDay.showYesterday(hour = 4, today = today, todaysSleep = null, recentSleep = emptyList()))
    }

    @Test
    fun onlyTheLastWeekCountsAsTrackingSleep() {
        val old = listOf(night("2026-11-09"))
        assertFalse(ActivityDay.tracksSleep(today, old))
        assertTrue(ActivityDay.tracksSleep(today, listOf(night("2026-11-10"))))
        assertFalse(ActivityDay.showYesterday(hour = 5, today = today, todaysSleep = null, recentSleep = old))
    }
}
