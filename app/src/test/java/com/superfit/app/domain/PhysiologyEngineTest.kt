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
    fun testProteinTargetIsSteadyAndFollowsGoal() {
        val profile = UserProfileEntity(
            id = 0,
            age = 30,
            heightCm = 180.0,
            weightKg = 80.0,
            isMale = true,
            activityMultiplier = 1.2,
            goal = "LOSE_WEIGHT"
        )

        // Losing weight or maintaining: 80 * 1.6 = 128g, whatever the night's sleep was
        assertEquals(128.0, PhysiologyEngine.calculateMacroTargets(profile, 2500.0).proteinG, 0.01)
        assertEquals(128.0, PhysiologyEngine.calculateMacroTargets(profile.copy(goal = "MAINTAIN"), 2500.0).proteinG, 0.01)

        // Building muscle: 80 * 2.0 = 160g
        assertEquals(160.0, PhysiologyEngine.calculateMacroTargets(profile.copy(goal = "GAIN_MUSCLE"), 2500.0).proteinG, 0.01)
    }

    @Test
    fun testProteinTargetUsesHealthyWeightAboveBmi27() {
        // 120kg at 175cm is BMI 39; protein is based on the weight at BMI 27: 27 * 1.75^2 = 82.69kg
        val profile = UserProfileEntity(
            id = 0,
            age = 40,
            heightCm = 175.0,
            weightKg = 120.0,
            isMale = true,
            goal = "LOSE_WEIGHT"
        )
        assertEquals(82.69 * 1.6, PhysiologyEngine.calculateMacroTargets(profile, 2800.0).proteinG, 0.05)
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
        // Default goal (lose weight) -> Protein = 80 * 1.6 = 128g
        // Active calories are ignored for macro target scaling
        
        val tdee = PhysiologyEngine.calculateTdee(1780.0, 1.2, 500.0)
        assertEquals(2136.0, tdee, 0.01)

        val targets = PhysiologyEngine.calculateMacroTargets(
            profile = profile,
            tdee = tdee,
            activeCalories = 500.0
        )

        assertEquals(2136.0, targets.calories, 0.01)
        assertEquals(128.0, targets.proteinG, 0.01)
        
        // Fat target: 2136.0 * 0.25 / 9 = 59.33g
        assertEquals(59.33, targets.fatG, 0.01)
        
        // Carbs target: (2136.0 - (128.0 * 4) - (59.33 * 9)) / 4 = (2136.0 - 512 - 534.0) / 4 = 272.5g
        assertEquals(272.50, targets.carbsG, 0.01)
    }
}
