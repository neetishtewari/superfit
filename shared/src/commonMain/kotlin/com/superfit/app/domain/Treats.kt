package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity

/**
 * When treat food counts as a treat. A real treat is a real amount: a square of dark chocolate or a
 * spoon of halwa doesn't use up one of the week's treats. Small bites add up over the day, and once
 * they reach a treat's worth they count once.
 */
object Treats {
    /** About one gulab jamun, a can of cola or a samosa. Same line as the calorie sources' small taste. */
    const val REAL_TREAT_KCAL = CalorieSourcesEngine.SMALL_TASTE_KCAL

    /**
     * Calories of treat food in this entry: the foods the AI marked as the treat (the gulab jamun,
     * not the dal rice), or for meals rated before it marked foods, the whole entry when it called it
     * a treat. Zero for foods marked "Fine for me" and meals not rated yet.
     */
    fun kcalOf(entry: NutritionEntryEntity, tags: MealTags? = MealTags.decode(entry.qualityTags)): Double {
        if (tags == null || tags.estimated || PersonalNutrition.isApproved(entry, tags)) return 0.0
        if (tags.treatItems == null) return if (tags.treat) entry.calories.coerceAtLeast(0.0) else 0.0
        return CalorieSourcesEngine.parts(entry).filter { it.treatFood }.sumOf { it.kcal }
    }

    /** True when this entry alone is a real treat. */
    fun isRealTreat(entry: NutritionEntryEntity): Boolean = kcalOf(entry) >= REAL_TREAT_KCAL

    /**
     * The treat occasions in one day's [sittings] (foods eaten together). A sitting with a treat's worth
     * of treat food is one occasion; the day's smaller bites together make one more once they add up.
     * Each occasion lists its foods that had treat food in them.
     */
    fun occasions(sittings: List<List<NutritionEntryEntity>>): List<List<NutritionEntryEntity>> {
        val withTreats = sittings.map { sitting -> sitting.map { it to kcalOf(it) }.filter { it.second > 0.0 } }.filter { it.isNotEmpty() }
        val (real, bites) = withTreats.partition { sitting -> sitting.sumOf { it.second } >= REAL_TREAT_KCAL }
        val counted = real.map { sitting -> sitting.map { it.first } }
        return if (bites.flatten().sumOf { it.second } >= REAL_TREAT_KCAL) counted + listOf(bites.flatten().map { it.first }) else counted
    }
}
