package com.superfit.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActivityPatternsTest {

    private fun walk(bpm: Long?, pace: Long = 720, hour: Int? = null) = ActivityFact("Walk", 60, 5.0, 250.0, pace, isRun = false, avgHeartRate = bpm, startHour = hour)
    private fun voice(text: String, reps: Int) = ActivityFact(text, 0, null, 100.0, null, isRun = false, reps = reps)
    private fun day(date: LocalDate, steps: Int = 0, workouts: List<ActivityFact> = emptyList(), rating: Double? = null, meals: Int = 3) =
        DayTotals(date.toKotlinLocalDate(), 1800.0, 1900.0, meals, 100.0, 110.0, steps, workouts, rating)

    @Test
    fun lowerHeartRateAtTheSamePaceIsAFitterHeart() {
        val h = ActivityPatterns.heart(listOf(walk(110), walk(112)), listOf(walk(118), walk(117), walk(119)))!!
        assertEquals(7, h.bpmDrop)
    }

    @Test
    fun aSlowerPaceExplainsALowerHeartRateSoItDoesNotCount() {
        assertNull(ActivityPatterns.heart(listOf(walk(110, pace = 800), walk(110, pace = 800)), listOf(walk(120), walk(120), walk(120))))
    }

    @Test
    fun workoutsWithoutHeartRateAreIgnored() {
        assertNull(ActivityPatterns.heart(listOf(walk(null), walk(null)), listOf(walk(120), walk(120), walk(120))))
    }

    @Test
    fun repsAddUpAndNameTheMainExerciseWhenTheWordsMatch() {
        val r = ActivityPatterns.reps(
            listOf(voice("50 pushups and 20 squats", 70), voice("60 pushups", 60)),
            listOf(voice("40 pushups", 40))
        )!!
        assertEquals(130, r.total)
        assertEquals(40, r.prev)
        assertEquals("pushups" to 110, r.top)
    }

    @Test
    fun noExerciseIsNamedWhenTheWordsDoNotAddUp() {
        val r = ActivityPatterns.reps(listOf(voice("3 sets of 12 squats", 36), voice("50 pushups", 50)), emptyList())!!
        assertNull(r.top)
    }

    @Test
    fun exercisesAreReadFromTheWorkoutSummary() {
        assertEquals(listOf("burpees" to 20, "jumping jacks" to 50, "pushups" to 30), ActivityPatterns.exercises("20 burpees, 50 jumping jacks, and 30 pushups"))
        assertEquals(emptyList<Pair<String, Int>>(), ActivityPatterns.exercises("30 minutes of weights"))
    }

    @Test
    fun activeDaysWithClearlyBetterMealsAreFound() {
        val start = LocalDate.of(2026, 10, 1)
        val days = (0 until 10).map { i ->
            val active = i % 2 == 0
            day(start.plusDays(i.toLong()), steps = if (active) 9000 else 2000, rating = if (active) 7.8 else 6.4)
        }
        val m = ActivityPatterns.moveDayPlates(days)!!
        assertEquals(7.8, m.activeRating, 0.01)
        assertEquals(6.4, m.restRating, 0.01)
    }

    @Test
    fun aUsualWorkoutHourAndBigStepDayMakeARhythm() {
        val start = LocalDate.of(2026, 10, 5) // a Monday
        val days = (0 until 28).map { i ->
            val d = start.plusDays(i.toLong())
            day(d, steps = if (d.dayOfWeek == DayOfWeek.SUNDAY) 12000 else 6000, workouts = if (i % 4 == 0) listOf(walk(120, hour = 7)) else emptyList())
        }
        val r = ActivityPatterns.rhythm(days)!!
        assertEquals(7, r.hour)
        assertEquals(DayOfWeek.SUNDAY, r.bestDay)
        assertEquals("7 am", ActivityPatterns.hourLabel(7))
    }
}
