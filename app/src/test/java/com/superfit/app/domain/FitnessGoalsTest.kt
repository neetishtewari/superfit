package com.superfit.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FitnessGoalsTest {

    @Test
    fun `keys and calorie offsets stay the same as before`() {
        assertEquals(
            listOf("LOSE_WEIGHT" to -500, "MAINTAIN" to 0, "GAIN_MUSCLE" to 300),
            FitnessGoals.all.map { it.key to it.calorieOffset }
        )
    }

    @Test
    fun `saved goals get the new friendly names`() {
        assertEquals("Lose fat", FitnessGoals.title("LOSE_WEIGHT"))
        assertEquals("Tone up", FitnessGoals.title("MAINTAIN"))
        assertEquals("Build muscle", FitnessGoals.title("GAIN_MUSCLE"))
    }

    @Test
    fun `unknown goal falls back to lose fat`() {
        assertEquals("LOSE_WEIGHT", FitnessGoals.forKey("SOMETHING_OLD").key)
    }

    @Test
    fun `report uses the same goal names`() {
        assertEquals("Tone up", CoachExportBuilder.goalLabel("MAINTAIN"))
    }
}
