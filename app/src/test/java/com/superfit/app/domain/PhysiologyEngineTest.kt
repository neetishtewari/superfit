package com.superfit.app.domain

import com.superfit.app.data.UserProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PhysiologyEngineTest {

    @Test
    fun testBmrCalculationMale() {
        val bmrMale = PhysiologyEngine.calculateBmr(
            weightKg = 80.0,
            heightCm = 180.0,
            age = 30,
            isMale = true
        )
        // Mifflin-St Jeor: 10 * 80 + 6.25 * 180 - 5 * 30 + 5 = 800 + 1125 - 150 + 5 = 1780.0
        assertEquals(1780.0, bmrMale, 0.01)
    }

    @Test
    fun testBmrCalculationFemale() {
        val bmrFemale = PhysiologyEngine.calculateBmr(
            weightKg = 60.0,
            heightCm = 165.0,
            age = 25,
            isMale = false
        )
        // Mifflin-St Jeor: 10 * 60 + 6.25 * 165 - 5 * 25 - 161 = 600 + 1031.25 - 125 - 161 = 1345.25
        assertEquals(1345.25, bmrFemale, 0.01)
    }

    @Test
    fun testTdeeCalculation() {
        val tdee = PhysiologyEngine.calculateTdee(1780.0, 1.2, 450.0)
        // 1780.0 * 1.2 + 450.0 = 2136.0 + 450.0 = 2586.0
        assertEquals(2586.0, tdee, 0.01)
    }

    @Test
    fun testTdeeCalculationPreventsDoubleCounting() {
        // High activity level multiplier (1.725) but active calories synced (500.0)
        val tdee = PhysiologyEngine.calculateTdee(1780.0, 1.725, 500.0)
        // Should adjust base multiplier to 1.2 (sedentary baseline) to avoid double-counting:
        // 1780.0 * 1.2 + 500.0 = 2136.0 + 500.0 = 2636.0
        assertEquals(2636.0, tdee, 0.01)

        // Active calories zero: should fall back to user's chosen multiplier (1.725):
        // 1780.0 * 1.725 + 0.0 = 3070.5
        val tdeeNoActivity = PhysiologyEngine.calculateTdee(1780.0, 1.725, 0.0)
        assertEquals(3070.5, tdeeNoActivity, 0.01)
    }

    @Test
    fun testReadinessScoreOptimum() {
        // 8 hours sleep, 1.6 hours deep sleep (exactly 20%)
        val score = PhysiologyEngine.calculateReadinessScore(28800, 5760)
        assertEquals(100, score)
    }

    @Test
    fun testReadinessScoreSuboptimal() {
        // 4 hours sleep, 0 deep sleep
        val score = PhysiologyEngine.calculateReadinessScore(14400, 0)
        // sleepRatio = 0.5 -> 35 points, deepSleepQuality = 0 -> 0 points. Total = 35
        assertEquals(35, score)
    }

    @Test
    fun testMacroTargetsShiftBasedOnReadiness() {
        val profile = UserProfileEntity(
            id = 0,
            age = 30,
            heightCm = 180.0,
            weightKg = 80.0,
            isMale = true,
            activityMultiplier = 1.2
        )

        // Scenario 1: Perfect readiness (100) -> higher protein target
        val targetsHighReadiness = PhysiologyEngine.calculateMacroTargets(
            profile = profile,
            tdee = 2500.0,
            readinessScore = 100,
            activeCalories = 0.0
        )
        // Protein should be 80 * 2.2 = 176g
        assertEquals(176.0, targetsHighReadiness.proteinG, 0.01)

        // Scenario 2: Zero readiness (0) -> lower protein target
        val targetsLowReadiness = PhysiologyEngine.calculateMacroTargets(
            profile = profile,
            tdee = 2500.0,
            readinessScore = 0,
            activeCalories = 0.0
        )
        // Protein should be 80 * 1.6 = 128g
        assertEquals(128.0, targetsLowReadiness.proteinG, 0.01)
    }

    @Test
    fun testMacroTargetsWithActiveCaloriesBurned() {
        val profile = UserProfileEntity(
            id = 0,
            age = 30,
            heightCm = 180.0,
            weightKg = 80.0,
            isMale = true,
            activityMultiplier = 1.2,
            calorieOffset = 0 // set to 0 for simpler math
        )
        // BMR is 1780.0
        // TDEE = BMR * Multiplier + Active Calories = 1780.0 * 1.2 + 500 = 2136.0 + 500 = 2636.0
        // Target Calories = TDEE + Calorie Offset = 2636.0
        // Readiness = 100 (readinessFactor = 1.0) -> Base Protein = 80 * 2.2 = 176g
        // Recovery Protein Bonus = 500 * 0.05 = 25g
        // Total Protein = 176 + 25 = 201g
        
        val tdee = PhysiologyEngine.calculateTdee(1780.0, 1.2, 500.0)
        assertEquals(2636.0, tdee, 0.01)

        val targets = PhysiologyEngine.calculateMacroTargets(
            profile = profile,
            tdee = tdee,
            readinessScore = 100,
            activeCalories = 500.0
        )

        assertEquals(2636.0, targets.calories, 0.01)
        assertEquals(201.0, targets.proteinG, 0.01)
        
        // Fat target: 2636.0 * 0.25 / 9 = 73.22g
        assertEquals(73.22, targets.fatG, 0.01)
        
        // Carbs target: (2636 - (201 * 4) - (73.22 * 9)) / 4 = 293.25g
        assertEquals(293.25, targets.carbsG, 0.01)
    }
}
