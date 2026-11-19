package com.superfit.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AppleWorkoutTypesTest {
    @Test
    fun runsAndRidesKnowIndoorFromOutdoor() {
        assertEquals(ExerciseTypes.RUNNING, AppleWorkoutTypes.exerciseType(37, indoor = false))
        assertEquals(ExerciseTypes.RUNNING_TREADMILL, AppleWorkoutTypes.exerciseType(37, indoor = true))
        assertEquals(ExerciseTypes.BIKING, AppleWorkoutTypes.exerciseType(13, indoor = false))
        assertEquals(ExerciseTypes.BIKING_STATIONARY, AppleWorkoutTypes.exerciseType(13, indoor = true))
        assertEquals(ExerciseTypes.SWIMMING_POOL, AppleWorkoutTypes.exerciseType(46, indoor = true))
    }

    @Test
    fun everyKindOfStrengthWorkoutIsAStrengthWorkout() {
        listOf(20L, 50L, 59L).forEach {
            assertEquals("Strength workout", WorkoutDisplay.title(AppleWorkoutTypes.exerciseType(it, indoor = true)))
        }
    }

    @Test
    fun walksHikesAndHiitKeepTheirNames() {
        assertEquals("Walk", WorkoutDisplay.title(AppleWorkoutTypes.exerciseType(52, indoor = false)))
        assertEquals("Hike", WorkoutDisplay.title(AppleWorkoutTypes.exerciseType(24, indoor = false)))
        assertEquals("HIIT", WorkoutDisplay.title(AppleWorkoutTypes.exerciseType(63, indoor = true)))
    }

    @Test
    fun anythingElseIsAPlainWorkout() {
        assertEquals("Workout", WorkoutDisplay.title(AppleWorkoutTypes.exerciseType(3000, indoor = false)))
    }
}
