package com.superfit.app.domain

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.superfit.app.data.ImportedWorkoutEntity
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalBestsTest {

    private val day = 24L * 3600 * 1000
    private val now = 100 * day
    private val today = LocalDate.ofEpochDay(100)
    private val run = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING

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

    private fun compute(workouts: List<WorkoutView>, steps: List<DaySteps> = emptyList()) =
        PersonalBests.compute(workouts, steps, today, ZoneOffset.UTC)

    @Test
    fun longestRunRemembersWhatItBeat() {
        val bests = compute(
            listOf(
                workout("a", run, 10, 10.2, 60),
                workout("b", run, 20, 12.4, 70),
                workout("c", run, 30, 8.0, 45)
            )
        )
        val longest = best(bests, BestKind.LONGEST_RUN)
        assertEquals(12.4, longest.value, 0.001)
        assertEquals("b", longest.workout?.merged?.primary?.id)
        assertEquals(10.2, longest.previousValue!!, 0.001)
        assertEquals(10 * day, longest.previousTime)
    }

    @Test
    fun paceBestsOnlyCountRunsLongEnough() {
        val bests = compute(
            listOf(
                workout("short", run, 10, 3.0, 12), // 4:00 /km but too short
                workout("five", run, 20, 6.0, 33), // 5:30 /km
                workout("ten", run, 30, 9.96, 60) // just short of 10 km still counts
            )
        )
        assertEquals(330.0, best(bests, BestKind.FASTEST_RUN_PACE).value, 0.5)
        assertEquals("ten", best(bests, BestKind.FASTEST_10K_PACE).workout?.merged?.primary?.id)
        assertTrue(bests.none { it.kind == BestKind.FASTEST_HALF_PACE })
    }

    @Test
    fun runsKeepTheirSecondAndThirdBest() {
        val bests = compute(
            listOf(
                workout("a", run, 10, 10.0, 60), // 6:00
                workout("b", run, 20, 10.0, 55), // 5:30
                workout("c", run, 30, 10.0, 58), // 5:48
                workout("d", run, 40, 10.0, 65) // 6:30
            )
        )
        val tenK = best(bests, BestKind.FASTEST_10K_PACE)
        assertEquals("b", tenK.workout?.merged?.primary?.id)
        assertEquals(listOf("c", "a"), tenK.runnersUp.map { it.workout?.merged?.primary?.id })
        assertEquals("2nd fastest 10K+ pace", tenK.runnersUp.first().title)
    }

    @Test
    fun walksAndRidesAreSeparateBests() {
        val bests = compute(
            listOf(
                workout("w", ExerciseSessionRecord.EXERCISE_TYPE_WALKING, 10, 6.0, 70),
                workout("r", ExerciseSessionRecord.EXERCISE_TYPE_BIKING, 11, 30.0, 70)
            )
        )
        assertEquals(6.0, best(bests, BestKind.LONGEST_WALK).value, 0.001)
        assertEquals(30.0, best(bests, BestKind.LONGEST_RIDE).value, 0.001)
        assertTrue(bests.none { it.kind == BestKind.LONGEST_RUN || it.kind == BestKind.LONGEST_SWIM })
    }

    @Test
    fun stepsCountOnlyFinishedDaysWeeksAndMonths() {
        // Day 100 (Sat 11 Apr 1970) is today; day 88 is Mon 30 Mar and day 95 is Mon 6 Apr, this week.
        val steps = listOf(
            DaySteps(LocalDate.ofEpochDay(88), 9000),
            DaySteps(LocalDate.ofEpochDay(89), 12000),
            DaySteps(LocalDate.ofEpochDay(95), 15000),
            DaySteps(LocalDate.ofEpochDay(100), 30000)
        )
        val bests = compute(emptyList(), steps)
        assertEquals(15000.0, best(bests, BestKind.MOST_STEPS_DAY).value, 0.1)
        assertEquals(21000.0, best(bests, BestKind.MOST_STEPS_WEEK).value, 0.1)
        assertEquals(LocalDate.ofEpochDay(88), best(bests, BestKind.MOST_STEPS_WEEK).periodStart)
        // April isn't over yet, so the month best is March.
        assertEquals(21000.0, best(bests, BestKind.MOST_STEPS_MONTH).value, 0.1)
    }

    @Test
    fun distanceOnFootAddsRunsAndWalksNotRides() {
        val bests = compute(
            listOf(
                workout("r", run, 90, 5.0, 30),
                workout("w", ExerciseSessionRecord.EXERCISE_TYPE_WALKING, 90, 3.0, 40),
                workout("b", ExerciseSessionRecord.EXERCISE_TYPE_BIKING, 90, 30.0, 60)
            )
        )
        assertEquals(8.0, best(bests, BestKind.MOST_DISTANCE_DAY).value, 0.001)
    }

    @Test
    fun firstCheckSetsStartingPointsSilently() {
        val bests = compute(listOf(workout("a", run, 99, 12.0, 70), workout("b", run, 98, 11.0, 70)))
        val check = PersonalBests.check(bests, emptyMap(), emptySet(), now)
        assertTrue(check.announce.isEmpty())
        assertEquals(12.0, check.baseline[BestKind.LONGEST_RUN]!!, 0.001)
        assertTrue("LONGEST_RUN:b" in check.podiumSeen)
    }

    @Test
    fun recentNewBestIsOfferedUntilAcknowledged() {
        val bests = compute(listOf(workout("old", run, 50, 10.0, 60), workout("new", run, 99, 12.0, 70)))
        val baseline = mapOf(BestKind.LONGEST_RUN to 10.0, BestKind.FASTEST_10K_PACE to 300.0, BestKind.FASTEST_RUN_PACE to 300.0)
        val check = PersonalBests.check(bests, baseline, emptySet(), now)
        assertEquals(listOf(BestKind.LONGEST_RUN), check.announce.map { it.kind })
        assertEquals(10.0, check.baseline[BestKind.LONGEST_RUN]!!, 0.001)

        val after = PersonalBests.acknowledge(check.announce.first(), check.baseline)
        assertTrue(PersonalBests.check(bests, after, emptySet(), now).announce.isEmpty())
    }

    @Test
    fun recentSecondBestRunIsOfferedOnce() {
        val bests = compute(
            listOf(workout("fast", run, 50, 10.0, 50), workout("slow", run, 60, 10.0, 70), workout("new", run, 99, 10.0, 55))
        )
        val baseline = bests.associate { it.kind to it.value }
        val seen = setOf("FASTEST_10K_PACE:slow", "FASTEST_RUN_PACE:slow", "LONGEST_RUN:slow", "LONGEST_RUN:new")
        val check = PersonalBests.check(bests, baseline, seen, now)
        val offered = check.announce.first()
        assertEquals(2, offered.rank)
        assertEquals("new", offered.workout?.merged?.primary?.id)

        val after = PersonalBests.acknowledgePodium(offered, check.podiumSeen)
        val next = PersonalBests.check(bests, check.baseline, after, now).announce
        assertTrue(next.none { it.kind == offered.kind && it.rank == 2 })
    }

    @Test
    fun tinyGainsDontCount() {
        val bests = compute(listOf(workout("a", run, 99, 10.05, 60)))
        val check = PersonalBests.check(bests, mapOf(BestKind.LONGEST_RUN to 10.0), emptySet(), now)
        assertTrue(check.announce.none { it.kind == BestKind.LONGEST_RUN })
    }

    @Test
    fun oldWorkoutUpdatesStartingPointQuietly() {
        val bests = compute(listOf(workout("a", run, 60, 15.0, 90)))
        val check = PersonalBests.check(bests, mapOf(BestKind.LONGEST_RUN to 10.0), emptySet(), now)
        assertTrue(check.announce.isEmpty())
        assertEquals(15.0, check.baseline[BestKind.LONGEST_RUN]!!, 0.001)
    }

    @Test
    fun hiddenBestLowersTheStartingPoint() {
        // The 20 km run was hidden, so only the 10 km one is left.
        val bests = compute(listOf(workout("a", run, 99, 10.0, 60)))
        val check = PersonalBests.check(bests, mapOf(BestKind.LONGEST_RUN to 20.0), emptySet(), now)
        assertTrue(check.announce.none { it.kind == BestKind.LONGEST_RUN })
        assertEquals(10.0, check.baseline[BestKind.LONGEST_RUN]!!, 0.001)
    }
}
