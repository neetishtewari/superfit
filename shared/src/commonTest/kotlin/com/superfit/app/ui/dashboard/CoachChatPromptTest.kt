package com.superfit.app.ui.dashboard

import com.superfit.app.data.ImportedWorkoutEntity
import com.superfit.app.data.MemoryPrefs
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.UserProfileEntity
import com.superfit.app.data.WorkoutImportPrefs
import com.superfit.app.data.WorkoutSync
import com.superfit.app.domain.CoachChatContext
import com.superfit.app.domain.CoachNote
import com.superfit.app.domain.CoachStyle
import com.superfit.app.domain.NoteKind
import com.superfit.app.domain.WorkoutDisplay
import com.superfit.app.domain.WorkoutMerge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

class CoachChatPromptTest {

    private val today = LocalDate(2026, 11, 12)
    private val noon = today.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() + 12 * 3_600_000L
    private val profile = UserProfileEntity(age = 35, heightCm = 175.0, weightKg = 80.0, isMale = true)

    private fun state(meals: List<NutritionEntryEntity>) = TodayState.build(
        today = today.toString(),
        zone = TimeZone.currentSystemDefault(),
        profile = profile,
        activity = null,
        sleep = null,
        nutrition = meals,
        workouts = emptyList(),
        weightEntries = emptyList(),
        habit = null,
        allNutritionHistory = meals,
        importedWorkouts = emptyList(),
        importedCount = 0,
        workoutPrefs = WorkoutImportPrefs(MemoryPrefs()),
        prefs = MemoryPrefs()
    )

    private fun facts(meals: List<NutritionEntryEntity>, notes: List<CoachNote> = emptyList()) = CoachChatFacts(
        state = state(meals),
        today = today,
        now = LocalTime(12, 0),
        nowMs = noon,
        scaleReadings = emptyList(),
        vsUsualLine = "Steps vs usual: about the same.",
        allNutrition = meals,
        workouts = emptyList(),
        activity = emptyList(),
        sleep = emptyList(),
        weights = emptyList(),
        stepGoal = 8000,
        customMacros = false,
        targetWeightKg = 75.0,
        goalDate = null,
        notes = notes,
        todayTip = null,
        conversation = emptyList()
    )

    @Test
    fun givesTheCoachTheDayTheLogsTheNotesAndTheRules() {
        val meal = NutritionEntryEntity(foodText = "2 roti with dal", calories = 420.0, proteinG = 18.0, carbsG = 60.0, fatG = 9.0, timestamp = noon - 3_600_000L)
        val note = CoachNote(id = "n1", text = "Bad knee, no running", kind = NoteKind.LASTING, added = today, byUser = true)
        val text = CoachChatPrompt.instruction(facts(listOf(meal), listOf(note)), CoachStyle.SUPPORTIVE)

        assertTrue(text.startsWith("You are Superfit AI Coach"))
        assertTrue("Calories Eaten: 420 /" in text)
        assertTrue("Body (smart scale): no smart scale data" in text)
        assertTrue("No sleep data today." in text)
        assertTrue("Steps vs usual: about the same." in text)
        assertTrue("2 roti with dal" in text)
        assertTrue("Bad knee, no running" in text)
        assertTrue("Goal weight: 75 kg" in text)
        assertTrue(CoachChatContext.RULES in text)
        assertTrue(text.endsWith(CoachStyle.SUPPORTIVE.instruction()))
    }

    @Test
    fun saysWhenNothingIsLoggedYet() {
        val text = CoachChatPrompt.instruction(facts(emptyList()), CoachStyle.ROAST)
        assertTrue("- none logged yet" in text)
        assertTrue(text.endsWith(CoachStyle.ROAST.instruction()))
    }

    @Test
    fun workoutsFromOtherAppsReachTheCoachAsTheirExercisePart() {
        val row = ImportedWorkoutEntity(
            id = "w1",
            sourcePackage = "com.strava",
            exerciseType = 56,
            startTime = noon - 7_200_000L,
            endTime = noon - 5_400_000L,
            distanceMeters = 5000.0,
            totalKcal = 380.0,
            detailsRead = true
        )
        val view = WorkoutDisplay.view(WorkoutMerge.merge(listOf(row), null, emptySet()).single(), "Strava", 1700.0)
        val entry = WorkoutSync.coachEntry(view)

        assertTrue(entry.description.endsWith("(from Strava)"))
        assertEquals(view.exerciseKcal ?: 0.0, entry.caloriesBurned)
        assertEquals(view.startTime, entry.timestamp)
    }
}
