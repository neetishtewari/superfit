package com.superfit.app.domain

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.superfit.app.data.ImportedWorkoutEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutMergeTest {

    private val minute = 60_000L
    private val t0 = 1_760_770_000_000L // 18 Oct 2026, morning

    private val samsung = "com.sec.android.app.shealth"
    private val strava = "com.strava"
    private val fit = "com.google.android.apps.fitness"

    private fun workout(
        id: String,
        app: String,
        type: Int = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        startMin: Long = 0,
        endMin: Long = 52,
        km: Double? = 8.66,
        kcal: Double? = 601.0,
        hr: Long? = null
    ) = ImportedWorkoutEntity(
        id = id,
        sourcePackage = app,
        exerciseType = type,
        startTime = t0 + startMin * minute,
        endTime = t0 + endMin * minute,
        distanceMeters = km?.times(1000),
        totalKcal = kcal,
        avgHeartRate = hr,
        detailsRead = true
    )

    @Test
    fun samsungAndStravaCopiesOfOneRunShowOnce_keepingTheOneWithHeartRate() {
        val merged = WorkoutMerge.merge(
            listOf(
                workout("strava-1", strava, startMin = 1, endMin = 52, km = 8.25),
                workout("samsung-1", samsung, hr = 148)
            )
        )
        assertEquals(1, merged.size)
        assertEquals("samsung-1", merged[0].primary.id)
        assertEquals(setOf("samsung-1", "strava-1"), merged[0].memberIds.toSet())
    }

    @Test
    fun googleFitWalkInsideASamsungWalkIsTheSameWalk() {
        val walk = ExerciseSessionRecord.EXERCISE_TYPE_WALKING
        val merged = WorkoutMerge.merge(
            listOf(
                workout("samsung-walk", samsung, type = walk, startMin = 0, endMin = 60, hr = 101),
                workout("fit-walk", fit, type = walk, startMin = 10, endMin = 40, kcal = null)
            )
        )
        assertEquals(1, merged.size)
        assertEquals("samsung-walk", merged[0].primary.id)
    }

    @Test
    fun twoWorkoutsBackToBackStaySeparate() {
        val merged = WorkoutMerge.merge(
            listOf(
                workout("run-1", samsung, startMin = 0, endMin = 30),
                workout("run-2", samsung, startMin = 30, endMin = 60)
            )
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun differentKindsAtTheSameTimeStaySeparate() {
        val merged = WorkoutMerge.merge(
            listOf(
                workout("run", samsung),
                workout("walk", fit, type = ExerciseSessionRecord.EXERCISE_TYPE_WALKING)
            )
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun aSmallOverlapIsNotTheSameWorkout() {
        // 10 minutes shared out of two 40-minute runs.
        val merged = WorkoutMerge.merge(
            listOf(
                workout("a", samsung, startMin = 0, endMin = 40),
                workout("b", strava, startMin = 30, endMin = 70)
            )
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun treadmillAndOutdoorRunTypesCountAsTheSameKind() {
        val merged = WorkoutMerge.merge(
            listOf(
                workout("a", samsung, type = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL),
                workout("b", strava, type = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING)
            )
        )
        assertEquals(1, merged.size)
    }

    @Test
    fun mainWorkoutAppWinsEvenWithLessDetail() {
        val merged = WorkoutMerge.merge(
            listOf(
                workout("samsung-1", samsung, hr = 148),
                workout("strava-1", strava, km = 8.25)
            ),
            mainApp = strava
        )
        assertEquals("strava-1", merged.single().primary.id)
    }

    @Test
    fun mainAppThatDidNotRecordTheWorkoutChangesNothing() {
        val merged = WorkoutMerge.merge(
            listOf(workout("samsung-1", samsung, hr = 148), workout("strava-1", strava)),
            mainApp = fit
        )
        assertEquals("samsung-1", merged.single().primary.id)
    }

    @Test
    fun brokenEntriesAreSkipped() {
        val broken = workout("broken", samsung).let { it.copy(endTime = it.startTime - minute) }
        val zeroLength = workout("zero", samsung).let { it.copy(endTime = it.startTime) }
        val merged = WorkoutMerge.merge(listOf(broken, zeroLength, workout("ok", strava, startMin = 120, endMin = 150)))
        assertEquals(listOf("ok"), merged.map { it.primary.id })
    }

    @Test
    fun hidingAnyCopyHidesTheWorkout() {
        val merged = WorkoutMerge.merge(
            listOf(workout("samsung-1", samsung, hr = 148), workout("strava-1", strava)),
            hiddenIds = setOf("strava-1")
        )
        assertTrue(merged.isEmpty())
    }

    @Test
    fun newestWorkoutComesFirst() {
        val merged = WorkoutMerge.merge(
            listOf(workout("early", samsung, startMin = 0, endMin = 30), workout("late", samsung, startMin = 300, endMin = 330))
        )
        assertEquals(listOf("late", "early"), merged.map { it.primary.id })
    }

    @Test
    fun exerciseCaloriesLeaveOutRestingBurn() {
        // The 18 Oct run: 601 kcal total over 43.2 minutes with a BMR of 1,700 kcal a day.
        // Resting burn for that time is 1700 x 0.72 h / 24 h = 51 kcal.
        assertEquals(550.0, WorkoutMerge.exerciseKcal(601.0, (43.2 * minute).toLong(), 1700.0), 0.01)
    }

    @Test
    fun exerciseCaloriesNeverGoBelowZero() {
        assertEquals(0.0, WorkoutMerge.exerciseKcal(20.0, 60 * minute, 1700.0), 0.0)
    }
}
