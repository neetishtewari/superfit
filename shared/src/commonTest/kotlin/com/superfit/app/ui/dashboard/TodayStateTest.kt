package com.superfit.app.ui.dashboard

import com.superfit.app.data.ActivityTelemetryEntity
import com.superfit.app.data.MemoryPrefs
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.UserProfileEntity
import com.superfit.app.data.WorkoutImportPrefs
import com.superfit.app.domain.PhysiologyEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.TimeZone

class TodayStateTest {

    private val zone = TimeZone.UTC
    private val profile = UserProfileEntity(age = 35, heightCm = 175.0, weightKg = 80.0, isMale = true)
    private val day = 86_400_000L

    private fun meal(time: Long, kcal: Double, protein: Double) =
        NutritionEntryEntity(foodText = "dal", calories = kcal, proteinG = protein, carbsG = 30.0, fatG = 4.0, timestamp = time)

    private fun build(
        prefs: MemoryPrefs = MemoryPrefs(),
        activity: ActivityTelemetryEntity? = null,
        today: List<NutritionEntryEntity> = emptyList(),
        all: List<NutritionEntryEntity> = today
    ) = TodayState.build(
        today = "2026-11-10",
        zone = zone,
        profile = profile,
        activity = activity,
        sleep = null,
        nutrition = today,
        workouts = emptyList(),
        weightEntries = emptyList(),
        habit = null,
        allNutritionHistory = all,
        importedWorkouts = emptyList(),
        importedCount = 3,
        workoutPrefs = WorkoutImportPrefs(prefs),
        prefs = prefs
    )

    @Test
    fun addsUpTodaysMealsAndUsesTheUsualTargets() {
        val state = build(today = listOf(meal(10 * day, 200.0, 10.0), meal(10 * day + 1, 350.0, 25.0)))

        assertEquals(550.0, state.caloriesEaten)
        assertEquals(35.0, state.proteinEaten)
        assertEquals(PhysiologyEngine.dailyTarget(profile, false, WorkoutImportPrefs(MemoryPrefs()).addBack, 0.0).targets, state.macroTargets)
        assertEquals("2026-11-10", state.activity.date)
        assertEquals(50, state.readinessScore)
        // The import is off, so other apps' workouts are not counted.
        assertEquals(0, state.importedWorkoutCount)
    }

    @Test
    fun thePersonsOwnTargetsWin() {
        val prefs = MemoryPrefs()
        prefs.edit {
            putBoolean("custom_macro_enabled", true)
            putInt("custom_calories", 1800)
            putInt("custom_protein_g", 140)
        }
        val state = build(prefs)

        assertEquals(1800.0, state.macroTargets.calories)
        assertEquals(140.0, state.macroTargets.proteinG)
        assertEquals(200.0, state.macroTargets.carbsG)
        assertEquals(0.0, state.workoutTargetKcal)
    }

    @Test
    fun stepsTickOffAgainstThePersonsOwnGoal() {
        val prefs = MemoryPrefs()
        prefs.edit { putInt("step_goal_own", 6000) }
        val walked = ActivityTelemetryEntity("2026-11-10", 6500, 0.0)

        assertEquals(6000, com.superfit.app.data.StepGoalStore.ownGoal(prefs))
        assertTrue(build(prefs, walked).habitEntry.stepsCompleted)
        assertFalse(build(prefs, ActivityTelemetryEntity("2026-11-10", 5999, 0.0)).habitEntry.stepsCompleted)
    }

    @Test
    fun countsDifferentDaysWithMeals() {
        val all = listOf(meal(day, 1.0, 1.0), meal(day + 5, 1.0, 1.0), meal(3 * day, 1.0, 1.0))
        val state = build(all = all)

        assertEquals(2, state.mealDaysLogged)
        assertEquals(3, state.totalMealsLogged)
    }
}
