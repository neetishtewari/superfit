package com.superfit.app.domain

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.superfit.app.data.ImportedWorkoutEntity
import com.superfit.app.data.WorkoutEntryEntity
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareCardsTest {

    private val utc = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 19)
    private val sixFortyTwo = today.atTime(6, 42).toInstant(utc).toEpochMilli()

    private fun run(km: Double?, minutes: Long, hr: Long? = 152, kcal: Double? = 600.0): WorkoutView {
        val w = ImportedWorkoutEntity(
            id = "r", sourcePackage = "com.sec.android.app.shealth", exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            startTime = sixFortyTwo, endTime = sixFortyTwo + minutes * 60_000L,
            distanceMeters = km?.times(1000), totalKcal = kcal, avgHeartRate = hr, detailsRead = true
        )
        return WorkoutDisplay.view(MergedWorkout(w, listOf("r")), "Samsung Health", bmr = 1700.0)
    }

    @Test
    fun fullDayShowsAllFourTiles() {
        val card = ShareCards.daily(
            today, streakDays = 12, mealsToday = 3, proteinEatenG = 132.0, proteinGoalG = 140.0,
            dietScore = 78, steps = 9412, workout = ShareCards.DayWorkout("8.7 km", "Morning run"),
            coachLine = "Protein on target **five days** running."
        )
        assertEquals(listOf("PROTEIN", "DIET QUALITY", "STEPS", "WORKOUT"), card.tiles.map { it.label })
        assertEquals("9,412", card.tiles[2].value)
        assertEquals("Protein on target five days running.", card.coachLine)
    }

    @Test
    fun roastMyDayReplacesTheCoachLineAndKeepsTheTiles() {
        val day = ShareCards.daily(
            today, streakDays = 3, mealsToday = 2, proteinEatenG = 48.0, proteinGoalG = 105.0,
            dietScore = null, steps = 3100, workout = null, coachLine = "Protein is short."
        )
        val card = ShareCards.roast(day, "Two samosas **and** a gulab jamun. Bold.")
        assertTrue(card.roast)
        assertEquals("Two samosas and a gulab jamun. Bold.", card.coachLine)
        assertEquals(day.tiles, card.tiles)
        val long = ShareCards.roast(day, "word ".repeat(80)).coachLine.orEmpty()
        assertTrue(long.length <= ShareCards.ROAST_MAX && long.endsWith("…"))
        assertTrue(!day.roast)
    }

    @Test
    fun aLongRoastKeepsWholeSentences() {
        val first = "I can't roast thin air, and your food log is currently an empty void."
        val second = "You've managed to burn over 300 active calories today, but the one lonely puri you logged at lunch is doing all of the heavy lifting."
        val third = "Bold."
        val fitted = ShareCards.fitRoast("$first $second $third")
        assertEquals(first, fitted)
        assertEquals("$first $third", ShareCards.fitRoast("$first $third"))
    }

    @Test
    fun blankRestDayLeavesTilesOutInsteadOfZeros() {
        val card = ShareCards.daily(
            today, streakDays = 4, mealsToday = 0, proteinEatenG = 0.0, proteinGoalG = 140.0,
            dietScore = 70, steps = 0, workout = null, coachLine = " "
        )
        assertTrue(card.tiles.isEmpty())
        assertNull(card.coachLine)
    }

    @Test
    fun dayWorkoutPicksLongestImportedThenVoiceLogged() {
        val voice = WorkoutEntryEntity(description = "Gym: chest and back", caloriesBurned = 320.0, workoutType = "Strength", setsCount = 0, repsCount = 0, timestamp = sixFortyTwo)
        assertEquals(ShareCards.DayWorkout("8.7 km", "Morning run"), ShareCards.dayWorkout(listOf(run(8.66, 48)), listOf(voice), utc))
        assertEquals(ShareCards.DayWorkout("320 kcal", "Gym: chest and back"), ShareCards.dayWorkout(emptyList(), listOf(voice), utc))
        assertNull(ShareCards.dayWorkout(emptyList(), emptyList(), utc))
    }

    @Test
    fun runCardShowsDistanceTimePaceHeartRateAndCalories() {
        val card = ShareCards.activity(run(8.66, 48), utc)
        assertEquals("MORNING RUN", card.heading)
        assertEquals("8.66", card.bigValue)
        assertEquals("km", card.bigUnit)
        assertEquals(listOf("TIME", "PACE", "AVG HEART RATE", "BURNED"), card.stats.map { it.label })
        assertEquals("48:00", card.stats[0].value)
    }

    @Test
    fun workoutWithoutDistanceLeadsWithMinutes() {
        val card = ShareCards.activity(run(null, 40, hr = null, kcal = null), utc)
        assertEquals("40", card.bigValue)
        assertEquals("min", card.bigUnit)
        assertTrue(card.stats.isEmpty())
    }

    @Test
    fun voiceLoggedCardShowsWhatWasSaid() {
        val voice = WorkoutEntryEntity(description = "Gym: chest and back", caloriesBurned = 320.0, workoutType = "Strength", setsCount = 0, repsCount = 0, timestamp = sixFortyTwo)
        val card = ShareCards.activity(voice, utc)
        assertEquals("MORNING WORKOUT", card.heading)
        assertEquals("320", card.bigValue)
        assertEquals("Gym: chest and back", card.subtitle)
    }

    @Test
    fun bestCardShowsGainOverPreviousBest() {
        val best = PersonalBest(BestKind.LONGEST_RUN, 12.4, run(12.4, 70), previousValue = 10.2, previousTime = null)
        val card = ShareCards.best(best, isNew = true, today = today, zone = utc)
        assertEquals("12.40", card.bigValue)
        assertEquals("10.20 km", card.previous)
        assertEquals("+2.20 km", card.gain)
        assertEquals("1:10:00 · 5:39 /km", card.detail)
    }

    @Test
    fun durationsReadLikeAWatch() {
        assertEquals("48:12", ShareCards.duration((48 * 60 + 12) * 1000L))
        assertEquals("1:09:40", ShareCards.duration((3600 + 9 * 60 + 40) * 1000L))
    }
}
