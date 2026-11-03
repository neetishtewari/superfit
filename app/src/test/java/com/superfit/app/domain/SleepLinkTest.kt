package com.superfit.app.domain

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.superfit.app.data.ImportedWorkoutEntity
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepLinkTest {

    private val utc = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 4)
    private var n = 0
    private val run = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING

    private fun workout(daysAgo: Long, minutes: Long, km: Double?, hr: Long? = 150, type: Int = run): WorkoutView {
        val id = "w${n++}"
        val start = today.minusDays(daysAgo).atTime(7, 0).toInstant(utc).toEpochMilli()
        val w = ImportedWorkoutEntity(
            id = id, sourcePackage = "com.sec.android.app.shealth", exerciseType = type,
            startTime = start, endTime = start + minutes * 60_000L,
            distanceMeters = km?.times(1000), totalKcal = 300.0, avgHeartRate = hr, detailsRead = true
        )
        return WorkoutDisplay.view(MergedWorkout(w, listOf(id)), "Samsung Health", bmr = 1700.0)
    }

    /** Six runs after good nights at 5:40 /km and six after short ones at 6:10 /km. */
    private fun history(): Pair<List<WorkoutView>, Map<LocalDate, Double>> {
        val sleep = mutableMapOf<LocalDate, Double>()
        val runs = (1L..12L).map { d ->
            val good = d % 2 == 0L
            sleep[today.minusDays(d)] = if (good) 7.8 else 5.5
            workout(d, minutes = if (good) 34 else 37, km = 6.0)
        }
        return runs to sleep
    }

    @Test
    fun provenAfterAGoodNight() {
        val (earlier, sleep) = history()
        val now = workout(0, 33, 6.0)
        val line = SleepLink.line(now, earlier, sleep + (today to 7.67), utc)
        assertEquals("Slept 7h 40m. Your best runs follow 7+ hour nights.", line)
    }

    @Test
    fun neverAfterAShortNight() {
        val (earlier, sleep) = history()
        assertNull(SleepLink.line(workout(0, 33, 6.0), earlier, sleep + (today to 6.0), utc))
    }

    @Test
    fun needsTenEarlierWorkouts() {
        val (earlier, sleep) = history()
        assertNull(SleepLink.line(workout(0, 33, 6.0), earlier.take(9), sleep + (today to 8.0), utc))
    }

    @Test
    fun noLineWhenSleepMakesNoDifference() {
        val sleep = mutableMapOf<LocalDate, Double>()
        val earlier = (1L..12L).map { d ->
            sleep[today.minusDays(d)] = if (d % 2 == 0L) 7.8 else 5.5
            workout(d, minutes = 36, km = 6.0)
        }
        assertNull(SleepLink.line(workout(0, 33, 6.0), earlier, sleep + (today to 8.0), utc))
    }

    @Test
    fun hoursReadLikeAWatch() {
        assertEquals("7h 05m", SleepLink.hours(7.08))
    }
}
