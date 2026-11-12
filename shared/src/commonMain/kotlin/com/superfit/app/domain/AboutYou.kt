package com.superfit.app.domain

import com.superfit.app.data.Now
import com.superfit.app.data.UserProfileEntity
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate

/** The "About you" card in Settings: what it shows, what it checks, and what a change would do. */
object AboutYou {
    const val MIN_HEIGHT_CM = 100.0
    const val MAX_HEIGHT_CM = 250.0

    /** "Male · 34 · 175 cm (5 ft 9 in) · 1-3 workouts". */
    fun summary(profile: UserProfileEntity, today: LocalDate = Now.today()): String {
        val (ft, inch) = HeightUnits.feetInches(profile.heightCm)
        return listOf(
            if (profile.isMale) "Male" else "Female",
            "${profile.currentAge(today)}",
            "${profile.heightCm.roundToInt()} cm ($ft ft $inch in)",
            ActivityLevels.label(profile.activityMultiplier)
        ).joinToString(" · ")
    }

    /** What's wrong with a typed height in cm, or null when it's fine. */
    fun heightProblem(heightCm: String): String? {
        val cm = heightCm.toDoubleOrNull() ?: return "Enter your height"
        return if (cm < MIN_HEIGHT_CM || cm > MAX_HEIGHT_CM) "Check your height" else null
    }

    /**
     * One plain line on what saving would do to the daily target, e.g.
     * "Daily target 2,050 → 1,980 kcal · protein stays 120 g". [ownTargets] is true when the person
     * set their own calories and macros, which a change here doesn't touch.
     */
    fun preview(
        before: UserProfileEntity,
        after: UserProfileEntity,
        ownTargets: Boolean,
        today: LocalDate = Now.today(),
        advice: AdviceTargets? = PersonalNutrition.targets,
        bodyFatPct: Double? = MeasuredBody.currentBodyFat()
    ): String {
        if (ownTargets) return "You set your own calories and macros, so those stay as they are."
        val old = targetsOf(before, today, advice, bodyFatPct)
        val new = targetsOf(after, today, advice, bodyFatPct)
        val oldKcal = old.calories.roundToInt()
        val newKcal = new.calories.roundToInt()
        val oldProtein = old.proteinG.roundToInt()
        val newProtein = new.proteinG.roundToInt()
        val kcal = if (oldKcal == newKcal) {
            "Daily target stays ${LocalFormat.integer(newKcal.toLong())} kcal"
        } else {
            "Daily target ${LocalFormat.integer(oldKcal.toLong())} → ${LocalFormat.integer(newKcal.toLong())} kcal"
        }
        val protein = if (oldProtein == newProtein) "protein stays $newProtein g" else "protein $oldProtein → $newProtein g"
        // Resting calories come from lean mass when a smart scale gave body fat, so age, height and
        // sex can't move them; say why the number stays.
        val why = if (bodyFatPct != null && oldKcal == newKcal) " (set from your scale's body fat reading)" else ""
        return "$kcal · $protein$why"
    }

    private fun targetsOf(p: UserProfileEntity, today: LocalDate, advice: AdviceTargets?, bodyFatPct: Double?): MacroTargets =
        OnboardingTargets.of(
            age = p.currentAge(today),
            heightCm = p.heightCm,
            weightKg = p.weightKg,
            isMale = p.isMale,
            activityMultiplier = p.activityMultiplier,
            goal = p.goal,
            calorieOffset = p.calorieOffset,
            advice = advice,
            bodyFatPct = bodyFatPct
        ).targets
}
