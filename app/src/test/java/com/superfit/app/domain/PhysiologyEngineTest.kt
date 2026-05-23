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
        // 1780.0 * 1.2 = 2136.0 (active calories ignored)
        assertEquals(2136.0, tdee, 0.01)

        val tdeeHighMultiplier = PhysiologyEngine.calculateTdee(1780.0, 1.725, 500.0)
        // 1780.0 * 1.725 = 3070.5 (active calories ignored)
        assertEquals(3070.5, tdeeHighMultiplier, 0.01)
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
    fun testMacroTargetsWithActiveCaloriesBurnedIgnored() {
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
        // TDEE = BMR * Multiplier = 1780.0 * 1.2 = 2136.0 (active calories ignored in target)
        // Target Calories = TDEE + Calorie Offset = 2136.0
        // Readiness = 100 (readinessFactor = 1.0) -> Base Protein = 80 * 2.2 = 176g
        // Active calories are ignored for macro target scaling
        
        val tdee = PhysiologyEngine.calculateTdee(1780.0, 1.2, 500.0)
        assertEquals(2136.0, tdee, 0.01)

        val targets = PhysiologyEngine.calculateMacroTargets(
            profile = profile,
            tdee = tdee,
            readinessScore = 100,
            activeCalories = 500.0
        )

        assertEquals(2136.0, targets.calories, 0.01)
        assertEquals(176.0, targets.proteinG, 0.01)
        
        // Fat target: 2136.0 * 0.25 / 9 = 59.33g
        assertEquals(59.33, targets.fatG, 0.01)
        
        // Carbs target: (2136.0 - (176.0 * 4) - (59.33 * 9)) / 4 = (2136.0 - 704 - 533.97) / 4 = 224.5g
        assertEquals(224.50, targets.carbsG, 0.01)
    }
}
