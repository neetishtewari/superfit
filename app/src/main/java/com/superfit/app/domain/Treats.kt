package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity

/**
 * When treat food counts as a treat. A real treat is a real amount: a square of dark chocolate or a
 * spoon of halwa doesn't use up one of the week's treats. Small bites add up over the day, and once
 * they reach a treat's worth they count once.
 */
object Treats {
    /** About one gulab jamun, a can of cola or a samosa. */
    const val REAL_TREAT_KCAL = 150.0

    /**
     * Calories of treat food in this entry that count: the AI's own figure, or for meals tagged before
     * it gave one, the whole entry when it called it a treat. Zero for foods marked "Fine for me".
     */
    fun kcalOf(entry: NutritionEntryEntity, tags: MealTags? = MealTags.decode(entry.qualityTags)): Double {
        if (tags == null || tags.estimated || PersonalNutrition.isApproved(entry, tags)) return 0.0
        val kcal = tags.treatKcal?.toDouble() ?: if (tags.treat) entry.calories else 0.0
        return kcal.coerceIn(0.0, maxOf(entry.calories, 0.0))
    }

    /** True when this entry alone is a real treat. */
    fun isRealTreat(entry: NutritionEntryEntity, tags: MealTags? = MealTags.decode(entry.qualityTags)): Boolean =
        kcalOf(entry, tags) >= REAL_TREAT_KCAL

    /**
     * The treat occasions in one day's [sittings] (foods eaten together). A sitting with a treat's worth
     * of treat food is one occasion; the day's smaller bites together make one more once they add up.
     * Each occasion lists its foods that had treat food in them.
     */
    fun occasions(sittings: List<List<NutritionEntryEntity>>): List<List<NutritionEntryEntity>> {
        val withTreats = sittings.map { sitting -> sitting.filter { kcalOf(it) > 0.0 } }.filter { it.isNotEmpty() }
        val (real, bites) = withTreats.partition { sitting -> sitting.sumOf { kcalOf(it) } >= REAL_TREAT_KCAL }
        val smallBites = bites.flatten()
        return if (smallBites.sumOf { kcalOf(it) } >= REAL_TREAT_KCAL) real + listOf(smallBites) else real
    }
}
