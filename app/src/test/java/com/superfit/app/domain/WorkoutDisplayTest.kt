package com.superfit.app.domain

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.superfit.app.data.ImportedWorkoutEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutDisplayTest {

    private fun view(type: Int, minutes: Long, meters: Double?, kcal: Double?, hr: Long? = null): WorkoutView {
        val w = ImportedWorkoutEntity(
            id = "x", sourcePackage = "com.strava", exerciseType = type,
            startTime = 0L, endTime = minutes * 60_000L,
            distanceMeters = meters, totalKcal = kcal, avgHeartRate = hr, detailsRead = true
        )
        return WorkoutDisplay.view(MergedWorkout(w, listOf("x")), "Strava", bmr = 1700.0)
    }

    @Test
    fun runShowsDistanceTimePaceAndHeartRate() {
        val run = view(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, minutes = 50, meters = 10_000.0, kcal = 650.0, hr = 150)
        assertEquals("Run", run.title)
        assertEquals("10.00 km · 50 min · 5:00 /km · 150 bpm", run.detailLine)
        assertNull(run.speedKmh)
    }

    @Test
    fun rideShowsSpeedInsteadOfPace() {
        val ride = view(ExerciseSessionRecord.EXERCISE_TYPE_BIKING, minutes = 60, meters = 25_000.0, kcal = null)
        assertEquals("Ride", ride.title)
        assertEquals("25.00 km · 60 min · 25.0 km/h", ride.detailLine)
        assertNull(ride.exerciseKcal)
    }

    @Test
    fun swimWithoutDistanceShowsOnlyTime() {
        val swim = view(ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, minutes = 30, meters = null, kcal = 300.0)
        assertEquals("30 min", swim.detailLine)
    }

    @Test
    fun unknownTypesAreCalledWorkout() {
        assertEquals("Workout", WorkoutDisplay.title(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT))
    }

    @Test
    fun dayExerciseCaloriesAddImportedAndVoiceLoggedWorkouts() {
        val run = view(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, minutes = 48, meters = 8000.0, kcal = 600.0)
        // 600 - 1700 x 48/1440 = 543.33, plus 120 from a voice-logged workout.
        assertEquals(663.33, WorkoutDisplay.dayExerciseKcal(listOf(run), 120.0), 0.01)
    }

    @Test
    fun boostNamesTheWorkoutWhenThereIsOne() {
        val run = view(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, minutes = 48, meters = 8000.0, kcal = 600.0)
        val walk = view(ExerciseSessionRecord.EXERCISE_TYPE_WALKING, minutes = 30, meters = 2500.0, kcal = 150.0)
        assertEquals("from your run", WorkoutDisplay.boostSource(listOf(run), voiceLoggedCount = 0))
        assertEquals("from your workouts", WorkoutDisplay.boostSource(listOf(run, walk), voiceLoggedCount = 0))
        assertEquals("from your workouts", WorkoutDisplay.boostSource(listOf(run), voiceLoggedCount = 1))
    }
}
