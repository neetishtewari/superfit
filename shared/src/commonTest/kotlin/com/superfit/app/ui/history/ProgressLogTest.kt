package com.superfit.app.ui.history

import com.superfit.app.data.GoalDatePref
import com.superfit.app.data.MemoryPrefs
import com.superfit.app.data.UserProfileEntity
import com.superfit.app.data.WeightEntryEntity
import com.superfit.app.domain.BodyPeriod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class ProgressLogTest {

    private val zone = TimeZone.of("Asia/Kolkata")
    private fun at(day: String, hour: Int) = LocalDateTime.parse("${day}T${hour.toString().padStart(2, '0')}:00").toInstant(zone).toEpochMilliseconds()
    private fun weight(kg: Double, day: String, hour: Int = 8) = WeightEntryEntity(weightKg = kg, timestamp = at(day, hour))
    private fun profile(start: Double, target: Double) = UserProfileEntity(age = 35, heightCm = 175.0, weightKg = start, isMale = true, startingWeightKg = start, targetWeightKg = target, startDateTimestamp = at("2026-10-01", 9))

    @Test
    fun oneWeighInADayAndTheDaysLastWins() {
        val ins = ProgressLog.weighIns(listOf(weight(80.0, "2026-11-02"), weight(79.6, "2026-11-01", 21), weight(79.8, "2026-11-01", 7)), zone)

        assertEquals(listOf(LocalDate.parse("2026-11-01") to 79.6, LocalDate.parse("2026-11-02") to 80.0), ins)
    }

    @Test
    fun theOldMadeUpDefaultIsNotAGoalUntilThePersonSetsOne() {
        val prefs = MemoryPrefs()
        assertFalse(GoalDatePref.hasGoal(prefs, profile(75.0, 70.0)))
        assertTrue(GoalDatePref.hasGoal(prefs, profile(82.0, 76.0)))

        GoalDatePref.markGoalSet(prefs)
        assertTrue(GoalDatePref.hasGoal(prefs, profile(75.0, 70.0)))
        // Less than half a kilo away is no goal at all.
        assertFalse(GoalDatePref.hasGoal(prefs, profile(75.0, 74.8)))
    }

    @Test
    fun theGoalDateIsKeptAndCanBeCleared() {
        val prefs = MemoryPrefs()
        val date = LocalDate.parse("2027-03-31")

        GoalDatePref.set(prefs, date)
        assertEquals(date, GoalDatePref.get(prefs))

        GoalDatePref.set(prefs, null)
        assertNull(GoalDatePref.get(prefs))
    }

    @Test
    fun journeyUsesWeighInsAndTheChosenGoal() {
        val prefs = MemoryPrefs()
        GoalDatePref.set(prefs, LocalDate.parse("2027-01-31"))
        val weights = listOf(weight(82.0, "2026-10-01"), weight(80.0, "2026-11-01"))

        val state = ProgressLog.build(profile(82.0, 76.0), prefs, emptyList(), weights, emptyMap(), emptyList(), BodyPeriod.MONTH, LocalDate.parse("2026-11-11"), zone)

        assertTrue(state.goalSet)
        assertEquals(80.0, state.latestWeightKg)
        assertEquals(80.0, state.journey.currentKg)
        assertEquals(-6.0, state.goalKgChange)
        assertEquals(-2.0, state.weightChange4w)
        assertEquals(LocalDate.parse("2027-01-31"), state.goalDate)
        assertTrue(state.bodyHasHistory)
    }
}
