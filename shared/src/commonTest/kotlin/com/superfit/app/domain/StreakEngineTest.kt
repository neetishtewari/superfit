package com.superfit.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

class StreakEngineTest {

    private val today = LocalDate(2026, 10, 5)
    private fun daysAgo(vararg n: Int) = n.map { today.minus(it, DateTimeUnit.DAY) }

    @Test
    fun noMealsMeansNoStreak() {
        val s = StreakEngine.fromLoggedDates(emptyList(), today)
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.longestStreak)
    }

    @Test
    fun loggingEveryDayCountsEveryDay() {
        val s = StreakEngine.fromLoggedDates(daysAgo(0, 1, 2, 3, 4), today)
        assertEquals(5, s.currentStreak)
        assertEquals(5, s.longestStreak)
        assertEquals("2026-10-05", s.lastLoggedDate)
    }

    @Test
    fun streakSurvivesBeforeTodayIsLogged() {
        val s = StreakEngine.fromLoggedDates(daysAgo(1, 2, 3), today)
        assertEquals(3, s.currentStreak)
        assertEquals(1, s.graceDaysRemaining)
    }

    @Test
    fun oneMissedDayIsForgiven() {
        // Logged 4 days ago, 3 days ago, missed 2 days ago, logged yesterday and today.
        val s = StreakEngine.fromLoggedDates(daysAgo(0, 1, 3, 4), today)
        assertEquals(4, s.currentStreak)
    }

    @Test
    fun missedYesterdayKeepsStreakButFlagsGraceInUse() {
        val s = StreakEngine.fromLoggedDates(daysAgo(2, 3, 4), today)
        assertEquals(3, s.currentStreak)
        assertEquals(0, s.graceDaysRemaining)
        assertEquals("2026-10-04", s.lastGraceUsedDate)
    }

    @Test
    fun twoMissedDaysEndTheStreakButKeepTheLongest() {
        val s = StreakEngine.fromLoggedDates(daysAgo(3, 4, 5, 6), today)
        assertEquals(0, s.currentStreak)
        assertEquals(4, s.longestStreak)
    }

    @Test
    fun twoMissedDaysInTheMiddleStartANewStreak() {
        // Run of 3 (days 10-8), gap of two missed days (7, 6), new run of 2 (days 5, 4)... then today.
        val s = StreakEngine.fromLoggedDates(daysAgo(10, 9, 8, 5, 4, 3, 2, 1, 0), today)
        assertEquals(6, s.currentStreak)
        assertEquals(6, s.longestStreak)

        val s2 = StreakEngine.fromLoggedDates(daysAgo(12, 11, 10, 9, 8, 5, 0), today)
        assertEquals(1, s2.currentStreak)
        assertEquals(5, s2.longestStreak)
    }

    @Test
    fun severalMealsOnOneDayCountOnce() {
        val s = StreakEngine.fromLoggedDates(daysAgo(0, 0, 0, 1, 1), today)
        assertEquals(2, s.currentStreak)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun mealTimesUseTheirLocalDayAndIgnoreTheFuture() {
        // 2026-10-04 20:00 UTC is already 2026-10-05 in India.
        val lateEvening = Instant.parse("2026-10-04T20:00:00Z").toEpochMilliseconds()
        val tomorrow = Instant.parse("2026-10-06T06:00:00Z").toEpochMilliseconds()
        val india = TimeZone.of("Asia/Kolkata")
        val s = StreakEngine.fromMealTimestamps(listOf(lateEvening, tomorrow), india, today)
        assertEquals(1, s.currentStreak)
        assertEquals("2026-10-05", s.lastLoggedDate)
        val utc = StreakEngine.fromMealTimestamps(listOf(lateEvening), TimeZone.UTC, today)
        assertEquals("2026-10-04", utc.lastLoggedDate)
    }
}
