package com.superfit.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class MoveTimeTest {
    private val day = LocalDate(2026, 11, 16)
    private val gym = ActivityFact("Strength workout", 47, null, 255.0, null, false)

    @Test
    fun exerciseMinutesCountWithoutAWorkout() {
        assertEquals(32L, TodayVsUsual.of(day, 4000, emptyList(), null, exerciseMinutes = 32.4).moveMinutes)
    }

    @Test
    fun theLargerOfWorkoutsAndExerciseMinutesIsUsed() {
        assertEquals(60L, TodayVsUsual.of(day, 4000, listOf(gym), null, exerciseMinutes = 60.0).moveMinutes)
        assertEquals(47L, TodayVsUsual.of(day, 4000, listOf(gym), null, exerciseMinutes = 20.0).moveMinutes)
        assertEquals(47L, TodayVsUsual.of(day, 4000, listOf(gym), null).moveMinutes)
    }
}
