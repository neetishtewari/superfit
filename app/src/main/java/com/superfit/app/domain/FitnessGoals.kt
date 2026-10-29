package com.superfit.app.domain

/** One goal a person can pick, with the plain words shown for it in onboarding and Settings. */
data class FitnessGoal(
    val key: String,
    val title: String,
    val explanation: String,
    val calorieOffset: Int
)

/**
 * The three goals. Keys and calorie offsets are unchanged from the original
 * Lose / Maintain / Gain, so saved profiles keep working; only the words changed.
 */
object FitnessGoals {
    val all: List<FitnessGoal> = listOf(
        FitnessGoal(
            key = "LOSE_WEIGHT",
            title = "Lose fat",
            explanation = "Eat about 500 kcal under your needs, with high protein so the weight comes off as fat, not muscle.",
            calorieOffset = -500
        ),
        FitnessGoal(
            key = "MAINTAIN",
            title = "Tone up",
            explanation = "Eat at your needs with high protein and some strength training. The scale stays steady while your shape changes.",
            calorieOffset = 0
        ),
        FitnessGoal(
            key = "GAIN_MUSCLE",
            title = "Build muscle",
            explanation = "Eat about 300 kcal over your needs with extra protein, and lift 3 times a week.",
            calorieOffset = 300
        )
    )

    /** The goal for a saved key; unknown keys fall back to Lose fat, the profile default. */
    fun forKey(key: String): FitnessGoal = all.firstOrNull { it.key == key } ?: all.first()

    fun title(key: String): String = forKey(key).title
}
