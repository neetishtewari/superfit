package com.superfit.app.domain

import com.superfit.app.data.UserProfileEntity

object PhysiologyEngine {

    /**
     * Calculates the Basal Metabolic Rate (BMR) using the Mifflin-St Jeor Equation.
     */
    fun calculateBmr(profile: UserProfileEntity): Double {
        return calculateBmr(
            weightKg = profile.weightKg,
            heightCm = profile.heightCm,
            age = profile.age,
            isMale = profile.isMale
        )
    }

    fun calculateBmr(weightKg: Double, heightCm: Double, age: Int, isMale: Boolean): Double {
        return if (isMale) {
            10.0 * weightKg + 6.25 * heightCm - 5.0 * age + 5.0
        } else {
            10.0 * weightKg + 6.25 * heightCm - 5.0 * age - 161.0
        }
    }

    /**
     * Calculates Total Daily Energy Expenditure (TDEE) in real-time using BMR and the Activity Multiplier:
     * BMR * Activity Multiplier.
     * Note: Active calories burned from Health Connect are tracked on the dashboard but omitted from target budget calculations to avoid inflation.
     */
    fun calculateTdee(bmr: Double, activityMultiplier: Double, activeCalories: Double = 0.0): Double {
        return bmr * activityMultiplier
    }

    /**
     * Generates a 0-100% sleep readiness score based on sleep duration and deep sleep duration.
     * Ideal sleep: 8 hours (28800 seconds).
     * Ideal deep sleep: 20% of sleep duration.
     */
    fun calculateReadinessScore(sleepDurationSeconds: Long, deepSleepDurationSeconds: Long): Int {
        if (sleepDurationSeconds <= 0) return 50 // Default neutral score

        val targetSleepSeconds = 8.0 * 3600.0 // 8 hours
        val sleepRatio = (sleepDurationSeconds.toDouble() / targetSleepSeconds).coerceAtMost(1.0)
        
        val deepSleepRatio = if (sleepDurationSeconds > 0) {
            deepSleepDurationSeconds.toDouble() / sleepDurationSeconds
        } else {
            0.0
        }
        // Deep sleep factor: 20% is ideal (1.0 weight)
        val deepSleepQualityFactor = (deepSleepRatio / 0.20).coerceAtMost(1.0)

        // Combine duration (70% weight) and deep sleep quality (30% weight)
        val score = (sleepRatio * 70.0) + (deepSleepQualityFactor * 30.0)
        return score.toInt().coerceIn(0, 100)
    }

    /**
     * Computes macronutrient targets from TDEE, body weight and goal.
     * Protein is a steady daily number (it no longer moves with the night's sleep), so the
     * coach can judge it over days and weeks: about 1.6g per kg to lose weight or maintain,
     * 2.0g per kg to build muscle. Above a BMI of 27 it is based on the weight at BMI 27,
     * so the target stays realistic for people with a lot of weight to lose.
     */
    fun calculateMacroTargets(
        profile: UserProfileEntity,
        tdee: Double,
        activeCalories: Double = 0.0
    ): MacroTargets {
        // Apply the calorie offset to the computed TDEE
        val adjustedTargetCalories = (tdee + profile.calorieOffset).coerceAtLeast(1200.0)

        val proteinG = (proteinReferenceWeightKg(profile) * proteinPerKg(profile.goal)).coerceAtLeast(40.0)

        // Fat: hormonal baseline, 25% of adjusted target calories
        val fatCalories = adjustedTargetCalories * 0.25
        val fatG = (fatCalories / 9.0).coerceAtLeast(30.0)

        // Carbs: remainder of calories
        val proteinCalories = proteinG * 4.0
        val fatCalActual = fatG * 9.0
        val remainingCalories = (adjustedTargetCalories - proteinCalories - fatCalActual).coerceAtLeast(0.0)
        val carbsG = (remainingCalories / 4.0).coerceAtLeast(50.0)

        return MacroTargets(
            calories = adjustedTargetCalories,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG
        )
    }

    fun proteinPerKg(goal: String): Double = if (goal == "GAIN_MUSCLE") 2.0 else 1.6

    fun proteinReferenceWeightKg(profile: UserProfileEntity): Double {
        val heightM = profile.heightCm / 100.0
        if (heightM <= 0.0) return profile.weightKg
        return minOf(profile.weightKg, 27.0 * heightM * heightM)
    }
}

data class MacroTargets(
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double
)
