package com.superfit.app.data

import com.superfit.app.domain.CoachInsight
import com.superfit.app.domain.FindingKind
import com.superfit.app.domain.StepGoalReason
import com.superfit.app.ui.dashboard.TodayCoach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class CoachInsightStoreTest {

    private val day = LocalDate.parse("2026-11-11")

    @Test
    fun todaysTipIsKeptForTheDayOnlyAndRemembered() {
        val prefs = MemoryPrefs()
        val tip = CoachInsight("Protein was low", "Add dal at lunch", listOf("3 of 7 days under"), "protein", FindingKind.LOGGING)
        CoachInsightStore.save(prefs, day, tip)

        assertEquals(tip, CoachInsightStore.loadForDay(prefs, day))
        assertNull(CoachInsightStore.loadForDay(prefs, LocalDate.parse("2026-11-12")))
        assertEquals(listOf("protein"), CoachInsightStore.memory(prefs).map { it.focusId })
    }

    @Test
    fun anOwnStepGoalWinsAndIsNotSavedAsTheWorkedOutOne() {
        val prefs = MemoryPrefs()
        val profile = UserProfileEntity(age = 35, heightCm = 175.0, weightKg = 80.0, isMale = true)
        StepGoalStore.setOwnGoal(prefs, 12_000)

        val goal = TodayCoach.stepGoal(profile, emptyList(), prefs, day)

        assertEquals(12_000, goal.goal)
        assertEquals(StepGoalReason.OWN, goal.reason)
        assertNull(StepGoalStore.load(prefs))
    }
}
