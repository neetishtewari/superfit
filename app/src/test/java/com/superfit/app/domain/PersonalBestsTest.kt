package com.superfit.app.domain

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.superfit.app.data.ImportedWorkoutEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalBestsTest {

    private val day = 24L * 3600 * 1000
    private val now = 100 * day

    private fun workout(id: String, type: Int, startDay: Long, km: Double, minutes: Long): WorkoutView {
        val start = startDay * day
        val w = ImportedWorkoutEntity(
            id = id, sourcePackage = "com.sec.android.app.shealth", exerciseType = type,
            startTime = start, endTime = start + minutes * 60_000L,
            distanceMeters = km * 1000, totalKcal = 500.0, avgHeartRate = 150, detailsRead = true
        )
        return WorkoutDisplay.view(MergedWorkout(w, listOf(id)), "Samsung Health", bmr = 1700.0)
    }

    private fun best(bests: List<PersonalBest>, kind: BestKind) = bests.first { it.kind == kind }

    @Test
    fun longestRunRemembersWhatItBeat() {
        val bests = PersonalBests.compute(
            listOf(
                workout("a", ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, 10, 10.2, 60),
                workout("b", ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, 20, 12.4, 70),
                workout("c", ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, 30, 8.0, 45)
            ),
            longestStreak = 0
        )
        val run = best(bests, BestKind.LONGEST_RUN)
        assertEquals(12.4, run.value, 0.001)
        assertEquals("b", run.workout?.merged?.primary?.id)
        assertEquals(10.2, run.previousValue!!, 0.001)
        assertEquals(10 * day, run.previousTime)
    }

    @Test
    fun paceOnlyCountsRunsOfFiveKmOrMore() {
        val bests = PersonalBests.compute(
            listOf(
                workout("short", ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, 10, 3.0, 12), // 4:00 /km but too short
                workout("long", ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, 20, 6.0, 33) // 5:30 /km
            ),
            longestStreak = 0
        )
        assertEquals(330.0, best(bests, BestKind.FASTEST_RUN_PACE).value, 0.5)
    }

    @Test
    fun walksAndRidesAreSeparateBests() {
        val bests = PersonalBests.compute(
            listOf(
                workout("w", ExerciseSessionRecord.EXERCISE_TYPE_WALKING, 10, 6.0, 70),
                workout("r", ExerciseSessionRecord.EXERCISE_TYPE_BIKING, 11, 30.0, 70)
            ),
            longestStreak = 12
        )
        assertEquals(6.0, best(bests, BestKind.LONGEST_WALK).value, 0.001)
        assertEquals(30.0, best(bests, BestKind.LONGEST_RIDE).value, 0.001)
        assertEquals(12.0, best(bests, BestKind.LONGEST_STREAK).value, 0.001)
        assertTrue(bests.none { it.kind == BestKind.LONGEST_RUN || it.kind == BestKind.LONGEST_SWIM })
    }

    @Test
    fun firstCheckSetsStartingPointsSilently() {
        val bests = PersonalBests.compute(listOf(workout("a", ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, 99, 12.0, 70)), longestStreak = 20)
        val check = PersonalBests.check(bests, emptyMap(), streakRiding = false, currentStreak = 20, now = now)
        assertTrue(check.announce.isEmpty())
        assertEquals(12.0, check.baseline[BestKind.LONGEST_RUN]!!, 0.001)
        assertEquals(20.0, check.baseline[BestKind.LONGEST_STREAK]!!, 0.001)
    }

    @Test
    fun recentNewBestIsOfferedUntilAcknowledged() {
        val bests = PersonalBests.compute(
            listOf(workout("old", ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, 50, 10.0, 60), workout("new", ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, 99, 12.0, 70)),
            longestStreak = 0
        )
        val baseline = mapOf(BestKind.LONGEST_RUN to 10.0)
        val check = PersonalBests.check(bests, baseline, false, 0, now)
        assertEquals(listOf(BestKind.LONGEST_RUN), check.announce.map { it.kind })
        assertEquals(10.0, check.baseline[BestKind.LONGEST_RUN]!!, 0.001)

        val after = PersonalBests.acknowledge(check.announce.first(), check.baseline)
        assertTrue(PersonalBests.check(bests, after, false, 0, now).announce.isEmpty())
    }

    @Test
    fun tinyGainsDontCount() {
        val bests = PersonalBests.compute(listOf(workout("a", ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, 99, 10.05, 60)), longestStreak = 0)
        val check = PersonalBests.check(bests, mapOf(BestKind.LONGEST_RUN to 10.0), false, 0, now)
        assertTrue(check.announce.isEmpty())
    }

    @Test
    fun oldWorkoutUpdatesStartingPointQuietly() {
        val bests = PersonalBests.compute(listOf(workout("a", ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, 60, 15.0, 90)), longestStreak = 0)
        val check = PersonalBests.check(bests, mapOf(BestKind.LONGEST_RUN to 10.0), false, 0, now)
        assertTrue(check.announce.isEmpty())
        assertEquals(15.0, check.baseline[BestKind.LONGEST_RUN]!!, 0.001)
    }

    @Test
    fun hiddenBestLowersTheStartingPoint() {
        // The 20 km run was hidden, so only the 10 km one is left.
        val bests = PersonalBests.compute(listOf(workout("a", ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, 99, 10.0, 60)), longestStreak = 0)
        val check = PersonalBests.check(bests, mapOf(BestKind.LONGEST_RUN to 20.0), false, 0, now)
        assertTrue(check.announce.isEmpty())
        assertEquals(10.0, check.baseline[BestKind.LONGEST_RUN]!!, 0.001)
    }

    @Test
    fun streakBestNeedsSevenDaysAndTheCurrentRun() {
        val baseline = mapOf(BestKind.LONGEST_STREAK to 4.0)
        val six = PersonalBests.compute(emptyList(), longestStreak = 6)
        assertTrue(PersonalBests.check(six, baseline, false, currentStreak = 6, now = now).announce.isEmpty())

        val eight = PersonalBests.compute(emptyList(), longestStreak = 8)
        val offered = PersonalBests.check(eight, mapOf(BestKind.LONGEST_STREAK to 5.0), false, currentStreak = 8, now = now)
        assertEquals(5.0, offered.announce.single().previousValue!!, 0.001)
        assertNull(offered.announce.single().previousTime)

        // A past run that beat the old best, but the streak has since ended.
        assertTrue(PersonalBests.check(eight, mapOf(BestKind.LONGEST_STREAK to 5.0), false, currentStreak = 2, now = now).announce.isEmpty())
    }

    @Test
    fun sharedStreakIsNotOfferedAgainEachDay() {
        val baseline = mapOf(BestKind.LONGEST_STREAK to 13.0)
        val nextDay = PersonalBests.compute(emptyList(), longestStreak = 14)
        val check = PersonalBests.check(nextDay, baseline, streakRiding = true, currentStreak = 14, now = now)
        assertTrue(check.announce.isEmpty())
        assertTrue(check.streakRiding)

        // Streak ended: riding stops, and a later run must beat 14.
        val ended = PersonalBests.check(nextDay, check.baseline, streakRiding = true, currentStreak = 1, now = now)
        assertFalse(ended.streakRiding)
    }
}
