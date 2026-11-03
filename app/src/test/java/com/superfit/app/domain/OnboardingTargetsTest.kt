package com.superfit.app.domain

import com.superfit.app.data.UserProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingTargetsTest {

    private fun plan(goal: String, offset: Int, weight: Double = 75.0) = OnboardingTargets.of(
        age = 32,
        heightCm = 175.0,
        weightKg = weight,
        isMale = true,
        activityMultiplier = 1.375,
        goal = goal,
        calorieOffset = offset,
        advice = null,
        bodyFatPct = null
    )

    @Test
    fun proteinMatchesWhatHomeUses() {
        // 75 kg losing fat: 1.6 g per kg = 120 g (the last sign-up step used to say 150 g).
        assertEquals(120.0, plan("LOSE_WEIGHT", -500).targets.proteinG, 0.01)
        // Building muscle: 2.0 g per kg.
        assertEquals(150.0, plan("GAIN_MUSCLE", 300).targets.proteinG, 0.01)
        val profile = UserProfileEntity(age = 32, heightCm = 175.0, weightKg = 75.0, isMale = true, activityMultiplier = 1.375, goal = "LOSE_WEIGHT", calorieOffset = -500)
        assertEquals(PhysiologyEngine.proteinTargetG(profile, advice = null), plan("LOSE_WEIGHT", -500).targets.proteinG, 0.01)
    }

    @Test
    fun caloriesAreTdeePlusTheGoalOffset() {
        val p = plan("LOSE_WEIGHT", -500)
        assertEquals(p.tdee - 500, p.targets.calories, 0.01)
        assertEquals(p.bmr * 1.375, p.tdee, 0.01)
    }

    @Test
    fun fibreFollowsCalories() {
        // About 14 g per 1,000 kcal, kept between 25 and 38 g.
        val p = plan("LOSE_WEIGHT", -500)
        assertEquals(Math.round(p.targets.calories / 1000.0 * 14.0).toDouble().coerceIn(25.0, 38.0), p.fibreG, 0.01)
    }

    @Test
    fun nutritionistAdviceWins() {
        val p = OnboardingTargets.of(32, 175.0, 75.0, true, 1.375, "LOSE_WEIGHT", -500, advice = AdviceTargets(proteinG = 100.0, fibreG = 30.0), bodyFatPct = null)
        assertEquals(100.0, p.targets.proteinG, 0.01)
        assertEquals(30.0, p.fibreG, 0.01)
    }

    @Test
    fun heightConvertsBothWays() {
        assertEquals(5 to 9, HeightUnits.feetInches(175.0))
        assertEquals(6 to 0, HeightUnits.feetInches(182.9))
        assertEquals(175.3, HeightUnits.cm(5, 9), 0.001)
        assertEquals(152.4, HeightUnits.cm(5, 0), 0.001)
    }
}
