package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity

/**
 * One food as Diet Quality judges it: a whole meal, or one food of a meal the AI rated food by
 * food. "Eggs and beer" becomes eggs and beer, each with its own rating and share of the calories.
 */
data class RatedFood(
    val entry: NutritionEntryEntity,
    /** Groups the same food across meals. */
    val key: String,
    /** As logged, for lists ("2 boiled eggs", "Beer"). */
    val name: String,
    /** Short lower-case name for sentences ("lowest: beer"). */
    val label: String,
    /** This food's share of the meal's logged calories. */
    val kcal: Double,
    val proteinG: Double,
    /** 1 to 10; null until the AI has rated the meal. */
    val rating: Int?,
    val why: String?,
    val swap: MealSwap?,
    val verdict: FoodVerdict?,
    val approved: Boolean,
    /** The name "Fine for me" is saved under. */
    val approvalKey: String,
    /** True for one food of a meal with several. */
    val isItem: Boolean,
    /** The meal's tags, for reasons written before the AI gave them. */
    val tags: MealTags?
)

object RatedFoods {

    /** The meal's foods, rated one by one when the AI did that, else the meal as one food. */
    fun of(entry: NutritionEntryEntity, tags: MealTags? = MealTags.decode(entry.qualityTags)): List<RatedFood> {
        val items = MealItems.decode(entry.items)?.items.orEmpty().filter { it.name.isNotBlank() }
        if (tags == null || tags.estimated || tags.itemRatings.isNullOrEmpty() ||
            items.map { MealTags.itemKey(it.name) }.distinct().size < 2
        ) {
            return listOf(whole(entry, tags))
        }
        val mealRating = DietScoreEngine.mealRating(entry, tags)
        val mealApproved = PersonalNutrition.isApproved(entry, tags)
        val weights = items.map { (it.kcal * it.qty).coerceAtLeast(0.0) }
        val sum = weights.sum()
        val proteinSum = items.sumOf { (it.protein * it.qty).coerceAtLeast(0.0) }
        val treatKeys = tags.treatItems.orEmpty().map { MealTags.itemKey(it) }.toSet()
        val foods = items.mapIndexed { i, item ->
            val share = if (sum > 0) weights[i] / sum else 1.0 / items.size
            val own = tags.itemRating(item.name)
            val approved = mealApproved || PersonalNutrition.key(item.name) in PersonalNutrition.approvedFoods
            val rating = (own?.rating ?: mealRating).let { if (approved) maxOf(it, 7) else it }
            val isTreat = MealTags.itemKey(item.name) in treatKeys
            RatedFood(
                entry = entry,
                key = "item:" + MealTags.itemKey(item.name),
                name = item.name.trim().replaceFirstChar { it.titlecase() },
                label = item.name.trim().lowercase(),
                kcal = entry.calories * share,
                proteinG = if (proteinSum > 0) entry.proteinG * (item.protein * item.qty).coerceAtLeast(0.0) / proteinSum else entry.proteinG * share,
                rating = rating,
                why = own?.why,
                swap = null,
                verdict = itemVerdict(tags, rating, isTreat, approved),
                approved = approved,
                approvalKey = PersonalNutrition.key(item.name),
                isItem = true,
                tags = tags
            )
        }
        // The meal's swap is about its weakest food, so it goes with that one.
        val weakest = foods.filter { (it.rating ?: 10) <= 5 && !it.approved }.minByOrNull { it.rating ?: 10 }
        return foods.map { if (it === weakest) it.copy(swap = tags.swap) else it }
    }

    private fun whole(entry: NutritionEntryEntity, tags: MealTags?): RatedFood {
        val key = entry.foodText.trim().lowercase().take(30)
        val rating = tags?.let { DietScoreEngine.mealRating(entry, it) }
        val approved = PersonalNutrition.isApproved(entry, tags)
        return RatedFood(
            entry = entry,
            key = key,
            name = entry.foodText.trim(),
            label = tags?.foodType?.ifBlank { null } ?: key,
            kcal = entry.calories,
            proteinG = entry.proteinG,
            rating = rating,
            why = tags?.why,
            swap = tags?.swap,
            verdict = if (tags != null && rating != null) FoodVerdict.of(tags, rating, approved) else null,
            approved = approved,
            approvalKey = PersonalNutrition.key(tags?.foodType?.takeIf { it.isNotBlank() } ?: key),
            isItem = false,
            tags = tags
        )
    }

    /**
     * One food's verdict: a treat or avoid food keeps the meal's word for it, the rest follow
     * their own rating. A food marked "Fine for me" is at least Good.
     */
    private fun itemVerdict(tags: MealTags, rating: Int, isTreat: Boolean, approved: Boolean): FoodVerdict {
        val v = when {
            isTreat && tags.verdict == FoodVerdict.AVOID.key -> FoodVerdict.AVOID
            isTreat -> FoodVerdict.TREAT
            rating >= 8 -> FoodVerdict.GREAT
            rating >= 6 -> FoodVerdict.GOOD
            else -> FoodVerdict.PORTION
        }
        return if (approved && v !in setOf(FoodVerdict.GREAT, FoodVerdict.GOOD)) FoodVerdict.GOOD else v
    }
}
