package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity

/**
 * Once a day, the last 4 weeks' meals that have no tags yet are judged, and the last 2 weeks'
 * again when they lack a nutritionist rating or were rated before the person's own nutritionist's
 * advice changed. The same on Android and iPhone.
 */
object MealTagBackfill {
    private const val DAY_MS = 24L * 3600 * 1000
    private const val LOOK_BACK_DAYS = 28L
    const val RERATE_DAYS = 14L
    private const val MAX_MEALS = 60
    private const val BATCH = 20

    /** The meals to judge again, newest first. */
    fun due(meals: List<NutritionEntryEntity>, nowMs: Long): List<NutritionEntryEntity> {
        val cutoff = nowMs - LOOK_BACK_DAYS * DAY_MS
        val rerateFrom = nowMs - RERATE_DAYS * DAY_MS
        return meals
            .filter { it.timestamp >= cutoff && it.id != 0L && needsTags(it, rerate = it.timestamp >= rerateFrom) }
            .sortedByDescending { it.timestamp }
            .take(MAX_MEALS)
    }

    /** Judges [meals] in batches and saves each one's tags. */
    suspend fun tag(meals: List<NutritionEntryEntity>, tagger: MealTagger, save: suspend (NutritionEntryEntity, String) -> Unit) {
        meals.chunked(BATCH).forEach { batch ->
            val tags = tagger.tag(batch)
            batch.forEach { entry ->
                tags[entry.id]?.let { t ->
                    // Asked once: when the AI gives no food-by-food ratings, don't ask again every day.
                    val saved = if (t.itemRatings == null && hasSeveralFoods(entry)) t.copy(itemRatings = emptyList()) else t
                    save(entry, saved.encode())
                }
            }
        }
    }

    /**
     * Untagged meals always; recent meals also when they have no nutritionist rating yet, or were
     * rated before the user's nutritionist's advice last changed.
     */
    fun needsTags(entry: NutritionEntryEntity, rerate: Boolean): Boolean {
        val tags = MealTags.decode(entry.qualityTags) ?: return true
        if (rerate && (tags.rating == null || tags.verdict == null || tags.adviceVersion != PersonalNutrition.adviceVersion())) return true
        // Meals of several foods rated before each food got its own rating, once.
        if (tags.itemRatings == null && hasSeveralFoods(entry)) return true
        return !tags.hasAdvice && FoodQualityEngine.scoreFromTags(tags, isHighProtein = false) <= 5
    }

    fun hasSeveralFoods(entry: NutritionEntryEntity): Boolean =
        MealItems.decode(entry.items)?.items.orEmpty()
            .filter { it.name.isNotBlank() }
            .map { MealTags.itemKey(it.name) }
            .distinct().size >= 2
}
