package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * Numbers the user's own nutritionist gave, which they confirmed in Settings. Each one that is set
 * replaces the app's own target; the rest stay as the app works them out.
 */
data class AdviceTargets(
    val calories: Double? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val fibreG: Double? = null
) {
    val isEmpty: Boolean get() = calories == null && proteinG == null && carbsG == null && fatG == null && fibreG == null

    /** "Protein 110 g · Carbs under 150 g" for the confirm dialog and Settings. */
    fun describe(): String = listOfNotNull(
        calories?.let { "Calories ${it.roundToInt()} kcal" },
        proteinG?.let { "Protein ${it.roundToInt()} g" },
        carbsG?.let { "Carbs ${it.roundToInt()} g" },
        fatG?.let { "Fat ${it.roundToInt()} g" },
        fibreG?.let { "Fibre ${it.roundToInt()} g" }
    ).joinToString(" · ")

    fun encode(): String = JSONObject().apply {
        calories?.let { put("kcal", it) }
        proteinG?.let { put("protein", it) }
        carbsG?.let { put("carbs", it) }
        fatG?.let { put("fat", it) }
        fibreG?.let { put("fibre", it) }
    }.toString()

    companion object {
        fun decode(raw: String?): AdviceTargets? {
            if (raw.isNullOrBlank()) return null
            val o = runCatching { JSONObject(raw) }.getOrNull() ?: return null
            fun num(key: String, range: ClosedFloatingPointRange<Double>) =
                if (o.has(key) && !o.isNull(key)) o.optDouble(key).takeIf { !it.isNaN() && it in range } else null
            return AdviceTargets(
                calories = num("kcal", 1000.0..5000.0),
                proteinG = num("protein", 30.0..300.0),
                carbsG = num("carbs", 30.0..600.0),
                fatG = num("fat", 15.0..250.0),
                fibreG = num("fibre", 10.0..80.0)
            ).takeIf { !it.isEmpty }
        }
    }
}

/**
 * What the AI should know about this person when it rates meals or writes advice: their goal, the
 * day's targets, their own nutritionist's advice and the foods they marked "Fine for me". Loaded
 * from the phone at start-up (see NutritionAdviceStore) and kept current by the dashboard.
 */
object PersonalNutrition {
    @Volatile var goal: String = "LOSE_WEIGHT"

    @Volatile var calorieTarget: Double? = null

    @Volatile var proteinTargetG: Double? = null

    /** The user's nutritionist's advice in their own words, or null. */
    @Volatile var advice: String? = null

    /** Targets from that advice the user confirmed; they replace the app's own. */
    @Volatile var targets: AdviceTargets? = null

    /** Generic food names ("dark chocolate") the user marked "Fine for me". */
    @Volatile var approvedFoods: Set<String> = emptySet()

    /** Short fingerprint of the advice, stored on each meal's rating so a change of advice re-rates recent meals. */
    fun adviceVersion(): String = advice?.trim()?.takeIf { it.isNotEmpty() }?.let { Integer.toHexString(it.hashCode()).take(6) } ?: "0"

    /** The lines added to a meal-rating prompt so the rating fits this person. */
    fun promptContext(): String = buildString {
        append("About this person: goal ")
        append(
            when (goal) {
                "GAIN_MUSCLE" -> "build muscle"
                "MAINTAIN" -> "tone up (keep weight steady, build some muscle)"
                else -> "lose fat while keeping muscle"
            }
        )
        calorieTarget?.let { append(", about ${it.roundToInt()} kcal a day") }
        proteinTargetG?.let { append(", ${it.roundToInt()} g protein a day") }
        append(".\n")
        advice?.trim()?.takeIf { it.isNotEmpty() }?.let {
            append("Their own nutritionist's advice (follow it over general rules): \"${it.take(800)}\"\n")
        }
        if (approvedFoods.isNotEmpty()) {
            append("Foods they and their nutritionist consider fine (never a treat, never swap): ${approvedFoods.joinToString(", ")}.\n")
        }
    }

    /** One line for coach prompts: the user's nutritionist's advice, which the coach must follow. Empty when none. */
    fun adviceLine(): String = advice?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { "The user's own nutritionist advised (follow it; never contradict it): \"${it.take(800)}\"\n" }
        .orEmpty()

    fun key(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), " ").take(40)

    /** True when the user marked this meal's food "Fine for me". */
    fun isApproved(entry: NutritionEntryEntity, tags: MealTags? = MealTags.decode(entry.qualityTags)): Boolean {
        if (approvedFoods.isEmpty()) return false
        val type = tags?.foodType?.let { key(it) }
        return (type != null && type.isNotEmpty() && type in approvedFoods) || key(entry.foodText) in approvedFoods
    }

    /** A treat that counts: the AI called it one and the user hasn't marked it "Fine for me". */
    fun countsAsTreat(entry: NutritionEntryEntity, tags: MealTags): Boolean = tags.treat && !isApproved(entry, tags)

    /** A food the AI said to avoid until the goal is reached, and the user hasn't marked "Fine for me". */
    fun countsAsAvoid(entry: NutritionEntryEntity, tags: MealTags): Boolean = tags.verdict == FoodVerdict.AVOID.key && !isApproved(entry, tags)
}
