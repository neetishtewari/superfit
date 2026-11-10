package com.superfit.app.data

import com.superfit.app.domain.BestKind
import com.superfit.app.domain.CoachStyle
import com.superfit.app.domain.PersonalNutrition
import com.superfit.app.domain.SavedStepGoal
import com.superfit.app.domain.StepGoalReason
import com.superfit.app.domain.WorkoutAddBack
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class PrefsStoresTest {

    private val savedStyle = CoachStyle.current
    private val savedFoods = PersonalNutrition.approvedFoods
    private val savedAdvice = PersonalNutrition.advice

    @AfterTest
    fun restoreGlobals() {
        CoachStyle.current = savedStyle
        PersonalNutrition.approvedFoods = savedFoods
        PersonalNutrition.advice = savedAdvice
    }

    @Test
    fun memoryPrefsRemovesAKeyWhenNullIsWritten() {
        val prefs = MemoryPrefs()
        prefs.edit { putString("a", "1") }
        assertTrue(prefs.contains("a"))
        prefs.edit { putString("a", null) }
        assertFalse(prefs.contains("a"))
        assertEquals("x", prefs.getString("a", "x"))
    }

    @Test
    fun stepGoalRoundTripsAndOwnGoalCanBeCleared() {
        val prefs = MemoryPrefs()
        assertNull(StepGoalStore.load(prefs))
        val saved = SavedStepGoal(8500, StepGoalReason.STARTER, LocalDate(2026, 11, 10))
        StepGoalStore.save(prefs, saved)
        assertEquals(saved, StepGoalStore.load(prefs))

        StepGoalStore.setOwnGoal(prefs, 12000)
        assertEquals(12000, StepGoalStore.ownGoal(prefs))
        StepGoalStore.setOwnGoal(prefs, null)
        assertNull(StepGoalStore.ownGoal(prefs))
    }

    @Test
    fun coachStyleIsChosenOnlyOnceSaved() {
        val prefs = MemoryPrefs()
        assertFalse(CoachStyleStore.hasChosen(prefs))
        val style = CoachStyle.entries.last()
        CoachStyleStore.save(prefs, style)
        assertTrue(CoachStyleStore.hasChosen(prefs))
        CoachStyle.current = CoachStyle.entries.first()
        CoachStyleStore.load(prefs)
        assertEquals(style, CoachStyle.current)
    }

    @Test
    fun fineForMeFoodsSurviveAReload() {
        val prefs = MemoryPrefs()
        PersonalNutrition.approvedFoods = emptySet()
        NutritionAdviceStore.setFineForMe(prefs, "Dark Chocolate", true)
        NutritionAdviceStore.setFineForMe(prefs, "tea", true)
        PersonalNutrition.approvedFoods = emptySet()
        NutritionAdviceStore.load(prefs)
        assertEquals(setOf("dark chocolate", "tea"), PersonalNutrition.approvedFoods)
    }

    @Test
    fun workoutImportRestoreKeepsWorkoutsHiddenOnThisPhone() {
        val prefs = WorkoutImportPrefs(MemoryPrefs())
        prefs.hide(listOf("local-1"))
        prefs.restore(
            mapOf(
                "workout_import_enabled" to true,
                "workout_import_add_back" to "HALF",
                "workout_import_hidden_ids" to listOf("cloud-1"),
                "workout_import_best_baseline" to listOf("LONGEST_RUN=10.5")
            )
        )
        assertTrue(prefs.enabled)
        assertEquals(WorkoutAddBack.HALF, prefs.addBack)
        assertEquals(setOf("local-1", "cloud-1"), prefs.hiddenIds)
        assertEquals(mapOf(BestKind.LONGEST_RUN to 10.5), prefs.bestBaseline)
        assertEquals("HALF", prefs.toBackup()["workout_import_add_back"])
    }
}
