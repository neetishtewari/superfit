package com.superfit.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class HealthGapsTest {
    @Test
    fun watchMinutesButNoStepsMeansStepsAreOff() {
        // A tester's day: 122 exercise minutes from the watch, four workouts in Health, 0 steps and none imported.
        assertEquals(listOf(HealthGaps.STEPS, HealthGaps.WORKOUTS), HealthGaps.missing(0, 122.0, 0, 0))
    }

    @Test
    fun stepsWithoutAnyWorkoutMeansWorkoutsMayBeOff() {
        assertEquals(listOf(HealthGaps.WORKOUTS), HealthGaps.missing(6000, null, 0, 0))
    }

    @Test
    fun everythingArrivingShowsNothing() {
        assertEquals(emptyList(), HealthGaps.missing(6000, 40.0, 2, 30))
    }

    @Test
    fun anEmptyMorningIsNotAGap() {
        assertEquals(emptyList(), HealthGaps.missing(0, null, 0, 12))
    }

    @Test
    fun workoutsTodayWithNoStepsMeansStepsAreOff() {
        assertEquals(listOf(HealthGaps.STEPS), HealthGaps.missing(0, null, 1, 5))
    }

    @Test
    fun lineNamesWhatIsMissing() {
        assertEquals(
            "Superfit isn't getting your steps and distance or workouts from Apple Health. They're most likely turned off for Superfit.",
            HealthGaps.line(listOf(HealthGaps.STEPS, HealthGaps.WORKOUTS), "Apple Health")
        )
    }
}
