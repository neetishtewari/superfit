package com.superfit.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakEngineTest {

    private val today = LocalDate.of(2026, 10, 5)
    private fun daysAgo(vararg n: Long) = n.map { today.minusDays(it) }

    @Test
    fun `no meals means no streak`() {
        val s = StreakEngine.fromLoggedDates(emptyList(), today)
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.longestStreak)
    }

    @Test
    fun `logging every day counts every day`() {
        val s = StreakEngine.fromLoggedDates(daysAgo(0, 1, 2, 3, 4), today)
        assertEquals(5, s.currentStreak)
        assertEquals(5, s.longestStreak)
    }

    @Test
    fun `streak survives before today is logged`() {
        val s = StreakEngine.fromLoggedDates(daysAgo(1, 2, 3), today)
        assertEquals(3, s.currentStreak)
        assertEquals(1, s.graceDaysRemaining)
    }

    @Test
    fun `one missed day is forgiven`() {
        // Logged 4 days ago, 3 days ago, missed 2 days ago, logged yesterday and today.
        val s = StreakEngine.fromLoggedDates(daysAgo(0, 1, 3, 4), today)
        assertEquals(4, s.currentStreak)
    }

    @Test
    fun `missed yesterday keeps streak but flags grace in use`() {
        val s = StreakEngine.fromLoggedDates(daysAgo(2, 3, 4), today)
        assertEquals(3, s.currentStreak)
        assertEquals(0, s.graceDaysRemaining)
    }

    @Test
    fun `two missed days end the streak but keep the longest`() {
        val s = StreakEngine.fromLoggedDates(daysAgo(3, 4, 5, 6), today)
        assertEquals(0, s.currentStreak)
        assertEquals(4, s.longestStreak)
    }

    @Test
    fun `two missed days in the middle start a new streak`() {
        // Run of 3 (days 10-8), gap of two missed days (7, 6), new run of 2 (days 5, 4)... then today.
        val s = StreakEngine.fromLoggedDates(daysAgo(10, 9, 8, 5, 4, 3, 2, 1, 0), today)
        assertEquals(6, s.currentStreak)
        assertEquals(6, s.longestStreak)

        val s2 = StreakEngine.fromLoggedDates(daysAgo(12, 11, 10, 9, 8, 5, 0), today)
        assertEquals(1, s2.currentStreak)
        assertEquals(5, s2.longestStreak)
    }

    @Test
    fun `several meals on one day count once`() {
        val s = StreakEngine.fromLoggedDates(daysAgo(0, 0, 0, 1, 1), today)
        assertEquals(2, s.currentStreak)
    }
}
