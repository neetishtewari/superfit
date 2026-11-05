package com.superfit.app.domain

import androidx.health.connect.client.records.ExerciseSessionRecord as E
import org.junit.Assert.assertEquals
import org.junit.Test

/** The shared copies of Health Connect's exercise type numbers must match the library exactly. */
class ExerciseTypesTest {

    @Test
    fun sharedNumbersMatchHealthConnect() {
        assertEquals(E.EXERCISE_TYPE_BADMINTON, ExerciseTypes.BADMINTON)
        assertEquals(E.EXERCISE_TYPE_BASKETBALL, ExerciseTypes.BASKETBALL)
        assertEquals(E.EXERCISE_TYPE_BIKING, ExerciseTypes.BIKING)
        assertEquals(E.EXERCISE_TYPE_BIKING_STATIONARY, ExerciseTypes.BIKING_STATIONARY)
        assertEquals(E.EXERCISE_TYPE_BOOT_CAMP, ExerciseTypes.BOOT_CAMP)
        assertEquals(E.EXERCISE_TYPE_BOXING, ExerciseTypes.BOXING)
        assertEquals(E.EXERCISE_TYPE_CALISTHENICS, ExerciseTypes.CALISTHENICS)
        assertEquals(E.EXERCISE_TYPE_CRICKET, ExerciseTypes.CRICKET)
        assertEquals(E.EXERCISE_TYPE_DANCING, ExerciseTypes.DANCING)
        assertEquals(E.EXERCISE_TYPE_ELLIPTICAL, ExerciseTypes.ELLIPTICAL)
        assertEquals(E.EXERCISE_TYPE_EXERCISE_CLASS, ExerciseTypes.EXERCISE_CLASS)
        assertEquals(E.EXERCISE_TYPE_GOLF, ExerciseTypes.GOLF)
        assertEquals(E.EXERCISE_TYPE_GYMNASTICS, ExerciseTypes.GYMNASTICS)
        assertEquals(E.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING, ExerciseTypes.HIGH_INTENSITY_INTERVAL_TRAINING)
        assertEquals(E.EXERCISE_TYPE_HIKING, ExerciseTypes.HIKING)
        assertEquals(E.EXERCISE_TYPE_MARTIAL_ARTS, ExerciseTypes.MARTIAL_ARTS)
        assertEquals(E.EXERCISE_TYPE_PADDLING, ExerciseTypes.PADDLING)
        assertEquals(E.EXERCISE_TYPE_PILATES, ExerciseTypes.PILATES)
        assertEquals(E.EXERCISE_TYPE_ROCK_CLIMBING, ExerciseTypes.ROCK_CLIMBING)
        assertEquals(E.EXERCISE_TYPE_ROWING, ExerciseTypes.ROWING)
        assertEquals(E.EXERCISE_TYPE_ROWING_MACHINE, ExerciseTypes.ROWING_MACHINE)
        assertEquals(E.EXERCISE_TYPE_RUNNING, ExerciseTypes.RUNNING)
        assertEquals(E.EXERCISE_TYPE_RUNNING_TREADMILL, ExerciseTypes.RUNNING_TREADMILL)
        assertEquals(E.EXERCISE_TYPE_SKIING, ExerciseTypes.SKIING)
        assertEquals(E.EXERCISE_TYPE_SOCCER, ExerciseTypes.SOCCER)
        assertEquals(E.EXERCISE_TYPE_SQUASH, ExerciseTypes.SQUASH)
        assertEquals(E.EXERCISE_TYPE_STAIR_CLIMBING, ExerciseTypes.STAIR_CLIMBING)
        assertEquals(E.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE, ExerciseTypes.STAIR_CLIMBING_MACHINE)
        assertEquals(E.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseTypes.STRENGTH_TRAINING)
        assertEquals(E.EXERCISE_TYPE_STRETCHING, ExerciseTypes.STRETCHING)
        assertEquals(E.EXERCISE_TYPE_SURFING, ExerciseTypes.SURFING)
        assertEquals(E.EXERCISE_TYPE_SWIMMING_OPEN_WATER, ExerciseTypes.SWIMMING_OPEN_WATER)
        assertEquals(E.EXERCISE_TYPE_SWIMMING_POOL, ExerciseTypes.SWIMMING_POOL)
        assertEquals(E.EXERCISE_TYPE_TABLE_TENNIS, ExerciseTypes.TABLE_TENNIS)
        assertEquals(E.EXERCISE_TYPE_TENNIS, ExerciseTypes.TENNIS)
        assertEquals(E.EXERCISE_TYPE_VOLLEYBALL, ExerciseTypes.VOLLEYBALL)
        assertEquals(E.EXERCISE_TYPE_WALKING, ExerciseTypes.WALKING)
        assertEquals(E.EXERCISE_TYPE_WEIGHTLIFTING, ExerciseTypes.WEIGHTLIFTING)
        assertEquals(E.EXERCISE_TYPE_YOGA, ExerciseTypes.YOGA)
    }
}
