package com.superfit.app.domain

import com.superfit.app.data.UserProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    // 80 kg, 180 cm, 30-year-old man: BMR 1,780. Chose "moderately active" (1.55) at sign-up.
    private val runner = UserProfileEntity(
        age = 30, heightCm = 180.0, weightKg = 80.0, isMale = true,
        activityMultiplier = 1.55, goal = "LOSE_WEIGHT", calorieOffset = -500
    )

    @Test
    fun withoutConnectedWorkoutsTheTargetUsesTheChosenActivityLevel() {
        val day = PhysiologyEngine.dailyTarget(runner, workoutsConnected = false, addBack = WorkoutAddBack.HALF, exerciseKcal = 550.0)
        // 1780 x 1.55 - 500 = 2259
        assertEquals(2259.0, day.targets.calories, 0.01)
        assertEquals(0.0, day.workoutKcal, 0.0)
    }

    @Test
    fun connectedWorkoutsAddHalfOfTheDaysExerciseCaloriesToAnEverydayBase() {
        val runDay = PhysiologyEngine.dailyTarget(runner, workoutsConnected = true, addBack = WorkoutAddBack.HALF, exerciseKcal = 550.0)
        val restDay = PhysiologyEngine.dailyTarget(runner, workoutsConnected = true, addBack = WorkoutAddBack.HALF, exerciseKcal = 0.0)
        // Base: 1780 x 1.2 - 500 = 1636. Run day: 1636 + 275 = 1911.
        assertEquals(1636.0, restDay.targets.calories, 0.01)
        assertEquals(1911.0, runDay.targets.calories, 0.01)
        assertEquals(275.0, runDay.workoutKcal, 0.01)
        assertEquals(1636.0, runDay.baseCalories, 0.01)
        assertEquals(1780.0 * 1.2 + 275.0, runDay.tdee, 0.01)
        // The coach's "what you burn" counts the whole run, not just the half added to the target.
        assertEquals(1780.0 * 1.2 + 550.0, runDay.maintenance, 0.01)
        // Protein stays steady; the extra goes to carbs and fat.
        assertEquals(restDay.targets.proteinG, runDay.targets.proteinG, 0.0)
        assertTrue(runDay.targets.carbsG > restDay.targets.carbsG)
        assertTrue(runDay.targets.fatG > restDay.targets.fatG)
    }

    @Test
    fun addBackAllAddsTheWholeWorkoutAndOffKeepsTheChosenActivityLevel() {
        val all = PhysiologyEngine.dailyTarget(runner, workoutsConnected = true, addBack = WorkoutAddBack.ALL, exerciseKcal = 550.0)
        val off = PhysiologyEngine.dailyTarget(runner, workoutsConnected = true, addBack = WorkoutAddBack.OFF, exerciseKcal = 550.0)
        assertEquals(2186.0, all.targets.calories, 0.01)
        assertEquals(2259.0, off.targets.calories, 0.01)
        assertEquals(0.0, off.workoutKcal, 0.0)
    }

    @Test
    fun workoutPartIsWhatActuallyRaisedTheTargetWhenTheMinimumApplies() {
        // Small profile whose base sits on the 1,200 kcal minimum: BMR 1,158, base 1389 - 500 = 889 -> 1200.
        val small = UserProfileEntity(age = 40, heightCm = 155.0, weightKg = 55.0, isMale = false, calorieOffset = -500)
        val day = PhysiologyEngine.dailyTarget(small, workoutsConnected = true, addBack = WorkoutAddBack.HALF, exerciseKcal = 400.0)
        // 1389 + 200 - 500 = 1089, still below the minimum, so the workout changed nothing visible.
        assertEquals(1200.0, day.targets.calories, 0.01)
        assertEquals(0.0, day.workoutKcal, 0.01)
    }
}
