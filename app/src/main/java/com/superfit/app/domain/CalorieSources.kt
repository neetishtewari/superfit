package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import java.util.Locale
import kotlin.math.roundToInt

/** One food's share of the calories over a week or month. */
data class CalorieFood(
    /** Lower-case key the food is grouped by. */
    val key: String,
    /** The name as the user's logs most often spell it, capitalised. */
    val name: String,
    val kcal: Int,
    /** Meals the food was in. */
    val times: Int,
    /** Share of all calories logged in the period, 0-100. */
    val pct: Int,
    /** Calories from the times it was a treat or a food best avoided. */
    val treatKcal: Int
) {
    /** Mostly eaten as a treat: tagged amber in lists. */
    val isTreat: Boolean get() = treatKcal > 0 && treatKcal * 2 >= kcal
}

/**
 * Where the calories came from: the foods that added the most (not the ones logged most often,
 * so chai twice a day and pizza twice a week are weighed fairly), and the treat foods that added
 * the most. Worked out on the phone from each meal's items; no AI.
 */
data class CalorieSources(
    /** Up to 5 foods with the most calories, biggest first. */
    val top: List<CalorieFood>,
    /** Up to 3 foods with the most treat calories, biggest first; only foods with treat calories. */
    val treats: List<CalorieFood>,
    val totalKcal: Int,
    val treatKcal: Int,
    /** The food logged in the most meals, which may not be in [top]. */
    val mostLogged: CalorieFood?,
    /** The AI's swap for the biggest treat, when it gave one. */
    val swap: MealSwap?,
    /** Share of the calories from meals the AI has rated, 0-1; "no treats" is only claimed when high. */
    val ratedShare: Double
) {
    val biggestTreat: CalorieFood? get() = treats.firstOrNull()

    /** Where the biggest treat sits in [top], 1-5, or null when it stayed out of the top 5. */
    val treatRank: Int? get() = biggestTreat?.let { b -> top.indexOfFirst { it.key == b.key }.takeIf { it >= 0 }?.plus(1) }

    /** An everyday food in the top 5 logged more often than the biggest treat but adding fewer calories. */
    val outweighs: CalorieFood?
        get() {
            val b = biggestTreat ?: return null
            if (treatRank == null) return null
            return top.filter { !it.isTreat && it.key != b.key && it.kcal < b.treatKcal && it.times > b.times }.maxByOrNull { it.times }
        }

    /** True when there is enough rated food to say no treats were eaten. */
    val surelyNoTreats: Boolean get() = treats.isEmpty() && ratedShare >= 0.6
}

object CalorieSourcesEngine {

    private const val TOP = 5

    /**
     * A treat food under this many kcal in one sitting is a small taste (a teaspoon of birthday
     * cake, one square of chocolate): portion control, never counted against the user. Same line
     * as the treat-by-portion rule.
     */
    const val SMALL_TASTE_KCAL = 150.0

    fun build(meals: List<NutritionEntryEntity>): CalorieSources? {
        val perMeal = meals.filter { it.calories > 0 }.map { meal -> meal to parts(meal) }
        val total = perMeal.sumOf { it.first.calories }
        if (total <= 0) return null
        val rated = perMeal.filter { (m, _) -> MealTags.decode(m.qualityTags)?.estimated == false }.sumOf { it.first.calories }

        val parts = perMeal.flatMap { it.second }
        val byFood = parts.groupBy { it.key }
        // A meal counts once per food, however many times the food is listed in it.
        val timesByFood = perMeal.flatMap { (_, list) -> list.map { it.key }.distinct() }.groupingBy { it }.eachCount()
        val foods = byFood.map { (key, list) ->
            val kcal = list.sumOf { it.kcal }
            CalorieFood(
                key = key,
                name = list.groupingBy { it.name }.eachCount().maxByOrNull { it.value }!!.key,
                kcal = kcal.roundToInt(),
                times = timesByFood[key] ?: 0,
                pct = (kcal / total * 100).roundToInt(),
                treatKcal = list.filter { it.treat }.sumOf { it.kcal }.roundToInt()
            )
        }
        val treats = foods.filter { it.treatKcal > 0 }.sortedByDescending { it.treatKcal }.take(3)
        val biggest = treats.firstOrNull()
        val swap = biggest?.let { b ->
            // The AI's swap is for the whole meal, so it only fits a meal that was mostly this treat.
            perMeal.filter { (m, list) -> list.any { it.key == b.key && it.treat && it.kcal * 2 >= m.calories } }
                .sortedByDescending { it.first.timestamp }
                .firstNotNullOfOrNull { (m, _) -> MealTags.decode(m.qualityTags)?.swap }
        }
        return CalorieSources(
            top = foods.sortedWith(compareByDescending<CalorieFood> { it.kcal }.thenByDescending { it.times }).take(TOP),
            treats = treats,
            totalKcal = total.roundToInt(),
            treatKcal = foods.sumOf { it.treatKcal },
            mostLogged = foods.maxWithOrNull(compareBy<CalorieFood> { it.times }.thenBy { it.kcal }),
            swap = swap,
            ratedShare = rated / total
        )
    }

    /** One part of a meal: a food, its share of the meal's calories and whether it was the treat. */
    data class MealPart(val key: String, val name: String, val kcal: Double, val treat: Boolean)

    /**
     * Splits a meal into its foods, sharing the meal's logged calories by each item's own calories
     * so the parts add up to what was logged. Meals without items count as one food.
     */
    fun parts(meal: NutritionEntryEntity): List<MealPart> {
        val tags = MealTags.decode(meal.qualityTags)
        val items = MealItems.decode(meal.items)?.items.orEmpty().filter { it.name.isNotBlank() }
        val mealApproved = PersonalNutrition.isApproved(meal, tags)
        val mealIsTreat = tags != null && !tags.estimated && !mealApproved &&
            FoodVerdict.of(tags, DietScoreEngine.mealRating(meal, tags), false).let { it == FoodVerdict.TREAT || it == FoodVerdict.AVOID }
        val treatKeys = tags?.treatItems?.map { key(it) }

        fun isTreat(name: String, single: Boolean): Boolean {
            if (tags == null || tags.estimated || mealApproved) return false
            if (PersonalNutrition.key(name) in PersonalNutrition.approvedFoods) return false
            val k = key(name)
            // Meals rated before items were marked: only a single-food meal can be called a treat.
            return treatKeys?.any { it == k || (it.length >= 4 && (k.contains(it) || it.contains(k))) } ?: (single && mealIsTreat)
        }

        if (items.isEmpty()) {
            val name = shortName(meal.foodText)
            return listOf(MealPart(key(name), name, meal.calories, meal.calories >= SMALL_TASTE_KCAL && isTreat(name, single = true)))
        }
        val weights = items.map { (it.kcal * it.qty).coerceAtLeast(0.0) }
        val sum = weights.sum()
        val single = items.map { key(it.name) }.distinct().size == 1
        return items.mapIndexed { i, item ->
            val share = if (sum > 0) weights[i] / sum else 1.0 / items.size
            val name = item.name.trim().replaceFirstChar { it.titlecase(Locale.getDefault()) }
            val kcal = meal.calories * share
            MealPart(key(item.name), name, kcal, kcal >= SMALL_TASTE_KCAL && isTreat(item.name, single))
        }
    }

    fun key(name: String): String = name.trim().lowercase(Locale.getDefault()).replace(Regex("\\s+"), " ")

    private fun shortName(text: String): String {
        val name = text.trim().replaceFirstChar { it.titlecase(Locale.getDefault()) }
        return if (name.length <= 32) name else name.take(31).substringBeforeLast(' ').trimEnd(',', ' ') + "…"
    }

    /** "about twice a day" or "about daily" when a food is that regular over [days] days; else null. */
    fun howOften(times: Int, days: Int): String? {
        if (days <= 0) return null
        val perDay = times.toDouble() / days
        return when {
            perDay >= 2.6 -> "about 3 times a day"
            perDay >= 1.7 -> "about twice a day"
            perDay >= 0.85 -> "about daily"
            else -> null
        }
    }
}
