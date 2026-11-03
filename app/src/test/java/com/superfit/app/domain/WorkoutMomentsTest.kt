package com.superfit.app.domain

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.superfit.app.data.ImportedWorkoutEntity
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutMomentsTest {

    private val utc = ZoneOffset.UTC

    // A Wednesday.
    private val today = LocalDate.of(2026, 11, 4)
    private var n = 0

    private fun at(daysAgo: Long, hour: Int = 7) = today.minusDays(daysAgo).atTime(hour, 0).toInstant(utc).toEpochMilli()

    private fun workout(
        daysAgo: Long,
        type: Int = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        minutes: Long = 45,
        hr: Long? = 130,
        km: Double? = null,
        hour: Int = 7
    ): WorkoutView {
        val id = "w${n++}"
        val start = at(daysAgo, hour)
        val w = ImportedWorkoutEntity(
            id = id, sourcePackage = "com.sec.android.app.shealth", exerciseType = type,
            startTime = start, endTime = start + minutes * 60_000L,
            distanceMeters = km?.times(1000), totalKcal = 300.0, avgHeartRate = hr, detailsRead = true
        )
        return WorkoutDisplay.view(MergedWorkout(w, listOf(id)), "Samsung Health", bmr = 1700.0)
    }

    private val hiit = ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
    private val run = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING

    @Test
    fun hardestSinceTheLastHarderOne() {
        val history = listOf(workout(60, hiit, hr = 160), workout(20, hiit, hr = 140), workout(15, hiit, hr = 145), workout(10, hiit, hr = 142), workout(5, hiit, hr = 150))
        val now = workout(0, hiit, hr = 158)
        assertEquals("Hardest HIIT since September", WorkoutMoments.hardest(now, history, utc))
    }

    @Test
    fun hardestNeedsEnoughHistoryAndAMonth() {
        val few = listOf(workout(40, hr = 120), workout(20, hr = 120), workout(10, hr = 120))
        assertNull(WorkoutMoments.hardest(workout(0, hr = 150), few, utc))
        val recentHarder = listOf(workout(40, hr = 120), workout(30, hr = 120), workout(20, hr = 120), workout(10, hr = 155))
        assertNull(WorkoutMoments.hardest(workout(0, hr = 150), recentHarder, utc))
    }

    @Test
    fun hardestEverInImportedHistorySaysDays() {
        val history = listOf(workout(45, hr = 120), workout(30, hr = 125), workout(20, hr = 122), workout(10, hr = 128))
        assertEquals("Hardest strength workout in 45 days", WorkoutMoments.hardest(workout(0, hr = 150), history, utc))
    }

    @Test
    fun longestRunGoesByDistance() {
        val history = listOf(workout(70, run, km = 12.0), workout(30, run, km = 5.0), workout(20, run, km = 6.0), workout(10, run, km = 5.5))
        assertEquals("Longest run since August", WorkoutMoments.longest(workout(0, run, minutes = 60, km = 10.0), history, utc))
    }

    @Test
    fun genericWorkoutsAndShortOnesSayNothing() {
        val other = ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
        val history = List(6) { workout(10L + it * 10, other, minutes = 20, hr = 110) }
        assertNull(WorkoutMoments.line(workout(0, other, minutes = 90, hr = 170), history, utc))
    }

    @Test
    fun busiestWeekInEightOrMore() {
        // Monday and Tuesday this week, and one a week for the ten weeks before.
        val history = List(10) { workout(9L + it * 7, minutes = 30, hr = 130) } + workout(2) + workout(1)
        assertEquals("Most workouts in a week in 10 weeks", WorkoutMoments.busiestWeek(workout(0), history, utc))
    }

    @Test
    fun busiestWeekNotWhenARecentWeekWasAsBusy() {
        val busy = listOf(workout(16), workout(15), workout(14))
        val history = List(10) { workout(30L + it * 7) } + busy + workout(2) + workout(1)
        assertNull(WorkoutMoments.busiestWeek(workout(0), history, utc))
    }

    @Test
    fun fitterHeartOnRuns() {
        val before = listOf(workout(50, run, minutes = 30, hr = 156, km = 5.0), workout(45, run, minutes = 30, hr = 155, km = 5.0), workout(40, run, minutes = 30, hr = 154, km = 5.0))
        val recent = listOf(workout(20, run, minutes = 30, hr = 148, km = 5.0), workout(10, run, minutes = 30, hr = 147, km = 5.0))
        val now = workout(0, run, minutes = 30, hr = 146, km = 5.0)
        assertEquals("Same pace as last month, 8 bpm lower heart rate", WorkoutMoments.fitterHeart(now, before + recent))
    }

    @Test
    fun laterWorkoutsAreIgnored() {
        val history = listOf(workout(45, hr = 120), workout(30, hr = 125), workout(20, hr = 122), workout(10, hr = 128))
        val now = workout(5, hr = 150)
        val later = workout(1, hr = 170)
        assertEquals("Hardest strength workout in 40 days", WorkoutMoments.line(now, history + later + now, utc))
    }
}
