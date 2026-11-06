package com.superfit.app.domain

import com.superfit.app.data.UserProfileEntity
import kotlin.math.roundToInt

/**
 * The numbers shown while signing up. They come from the same engine as Home, so the goal step,
 * the "You're all set" step and Home always agree (the last step used to work protein out again at
 * 2 g per kg for everyone).
 */
data class OnboardingTargets(
    val bmr: Double,
    val tdee: Double,
    val targets: MacroTargets,
    val fibreG: Double
) {
    companion object {
        fun of(
            age: Int,
            heightCm: Double,
            weightKg: Double,
            isMale: Boolean,
            activityMultiplier: Double,
            goal: String,
            calorieOffset: Int,
            advice: AdviceTargets? = PersonalNutrition.targets,
            bodyFatPct: Double? = MeasuredBody.currentBodyFat()
        ): OnboardingTargets {
            val profile = UserProfileEntity(
                age = age,
                heightCm = heightCm,
                weightKg = weightKg,
                isMale = isMale,
                activityMultiplier = activityMultiplier,
                goal = goal,
                calorieOffset = calorieOffset
            )
            val bmr = PhysiologyEngine.calculateBmr(profile, bodyFatPct)
            val tdee = PhysiologyEngine.calculateTdee(bmr, activityMultiplier)
            val targets = PhysiologyEngine.calculateMacroTargets(profile, tdee, advice = advice)
            val fibre = advice?.fibreG ?: (targets.calories / 1000.0 * 14.0).coerceIn(25.0, 38.0).roundToInt().toDouble()
            return OnboardingTargets(bmr, tdee, targets, fibre)
        }
    }
}

/** Height in centimetres or feet and inches. */
object HeightUnits {
    /** Whole feet and rounded inches, e.g. 175 cm -> 5 ft 9 in. */
    fun feetInches(cm: Double): Pair<Int, Int> {
        val totalInches = (cm / 2.54).roundToInt()
        return totalInches / 12 to totalInches % 12
    }

    /** Centimetres to one decimal, e.g. 5 ft 9 in -> 175.3. */
    fun cm(feet: Int, inches: Int): Double = ((feet * 12 + inches) * 2.54 * 10).roundToInt() / 10.0
}
