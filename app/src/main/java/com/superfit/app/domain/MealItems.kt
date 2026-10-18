package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong

/** How much oil or sugar went in compared with the AI's estimate. */
enum class Level(val factor: Double) { LOW(0.5), NORMAL(1.0), HIGH(1.5) }

/**
 * One food in a meal. The nutrient numbers are for ONE unit (one roti, one katori, one gram),
 * at the AI's normal amount of oil and sugar, so changing the amount is a multiplication.
 */
data class MealItem(
    val name: String,
    val qty: Double,
    val unit: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    /** Grams of added oil, ghee or butter per unit. */
    val oilG: Double,
    /** Grams of added sugar per unit. */
    val sugarG: Double
) {
    /** How much one tap of − or + changes the amount. */
    val step: Double get() = if (unit == "g" || unit == "ml") 25.0 else 0.5

    /** "3 roti", "1 katori dal", "150 g rice". */
    fun label(): String {
        val amount = formatQty(qty)
        return when (unit) {
            "piece" -> "$amount $name"
            "serving" -> if (qty == 1.0) name else "$amount × $name"
            else -> "$amount $unit $name"
        }
    }
}

data class MealTotals(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double, val fiber: Double)

/**
 * A logged meal broken into its foods, so the user can fix amounts and the oil and sugar level
 * after logging. Stored as JSON in the meal's `items` column. The meal's own calorie and macro
 * columns are rewritten from this whenever it changes, so the rest of the app never reads it.
 */
data class MealItems(
    val items: List<MealItem>,
    val oil: Level = Level.NORMAL,
    val sugar: Level = Level.NORMAL,
    /** False for older meals, which have no oil or sugar estimates to adjust. */
    val hasEstimates: Boolean = true
) {
    private val oilGrams: Double get() = items.sumOf { it.oilG * it.qty }
    private val sugarGrams: Double get() = items.sumOf { it.sugarG * it.qty }

    val hasOil: Boolean get() = hasEstimates && oilGrams > 0.0
    val hasSugar: Boolean get() = hasEstimates && sugarGrams > 0.0

    fun totals(): MealTotals {
        // Oil is fat at 9 kcal per gram; added sugar is carbs at 4 kcal per gram.
        val extraFat = if (hasEstimates) (oil.factor - 1.0) * oilGrams else 0.0
        val extraSugar = if (hasEstimates) (sugar.factor - 1.0) * sugarGrams else 0.0
        return MealTotals(
            kcal = round1(max(0.0, items.sumOf { it.kcal * it.qty } + 9.0 * extraFat + 4.0 * extraSugar)),
            protein = round1(max(0.0, items.sumOf { it.protein * it.qty })),
            carbs = round1(max(0.0, items.sumOf { it.carbs * it.qty } + extraSugar)),
            fat = round1(max(0.0, items.sumOf { it.fat * it.qty } + extraFat)),
            fiber = round1(max(0.0, items.sumOf { it.fiber * it.qty }))
        )
    }

    fun foodText(): String = items.joinToString(", ") { it.label() }

    /** The same meal at a different portion size. */
    fun scaled(factor: Double): MealItems = copy(items = items.map { it.copy(qty = it.qty * factor) })

    fun encode(): String = JSONObject()
        .put("v", 1)
        .put("oil", oil.name)
        .put("sugar", sugar.name)
        .put("est", hasEstimates)
        .put("items", JSONArray().apply {
            items.forEach {
                put(
                    JSONObject()
                        .put("name", it.name).put("qty", it.qty).put("unit", it.unit)
                        .put("kcal", it.kcal).put("p", it.protein).put("c", it.carbs).put("f", it.fat)
                        .put("fib", it.fiber).put("oil", it.oilG).put("sug", it.sugarG)
                )
            }
        })
        .toString()

    companion object {
        fun decode(raw: String?): MealItems? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val json = JSONObject(raw)
                val arr = json.getJSONArray("items")
                val items = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    MealItem(
                        name = o.getString("name"), qty = o.getDouble("qty"), unit = o.getString("unit"),
                        kcal = o.getDouble("kcal"), protein = o.getDouble("p"), carbs = o.getDouble("c"),
                        fat = o.getDouble("f"), fiber = o.optDouble("fib", 0.0),
                        oilG = o.optDouble("oil", 0.0), sugarG = o.optDouble("sug", 0.0)
                    )
                }
                if (items.isEmpty()) return null
                MealItems(
                    items = items,
                    oil = levelOf(json.optString("oil")),
                    sugar = levelOf(json.optString("sugar")),
                    hasEstimates = json.optBoolean("est", true)
                )
            }.getOrNull()
        }

        /** An older meal with no item list: one serving of the whole meal. */
        fun legacy(entry: NutritionEntryEntity): MealItems = MealItems(
            items = listOf(
                MealItem(
                    name = entry.foodText, qty = 1.0, unit = "serving",
                    kcal = entry.calories, protein = entry.proteinG, carbs = entry.carbsG, fat = entry.fatG,
                    fiber = MealTags.decode(entry.qualityTags)?.fiberG ?: 0.0, oilG = 0.0, sugarG = 0.0
                )
            ),
            hasEstimates = false
        )

        /**
         * Reads the AI's `items` array, whose numbers are for the whole quantity, and stores them
         * per unit. Null when the list is missing, empty, or has an item with no amount, or when
         * the items add up to no calories at all.
         */
        fun fromGemini(arr: JSONArray?): MealItems? {
            if (arr == null || arr.length() == 0) return null
            val items = (0 until arr.length()).map { i ->
                val o = arr.optJSONObject(i) ?: return null
                val name = o.optString("name", "").trim()
                val qty = o.optDouble("quantity", 0.0)
                if (name.isEmpty() || qty.isNaN() || qty <= 0.0) return null
                fun perUnit(key: String) = o.optDouble(key, 0.0).let { if (it.isNaN() || it < 0.0) 0.0 else it / qty }
                MealItem(
                    name = name, qty = qty, unit = o.optString("unit", "serving").trim().lowercase().ifEmpty { "serving" },
                    kcal = perUnit("calories"), protein = perUnit("protein"), carbs = perUnit("carbs"),
                    fat = perUnit("fat"), fiber = perUnit("fiber"), oilG = perUnit("oilG"), sugarG = perUnit("sugarG")
                )
            }
            if (items.sumOf { it.kcal * it.qty } <= 0.0) return null
            return MealItems(items)
        }

        private fun levelOf(name: String): Level = Level.entries.firstOrNull { it.name == name } ?: Level.NORMAL
    }
}

/**
 * The meal after an edit: name, calories and macros rebuilt from [items]. Fibre in the meal's
 * tags becomes the items' fibre, and veg/fruit servings follow the change in calories. The AI's
 * judgements (fried, treat, how processed) stay as they were.
 */
fun NutritionEntryEntity.withItems(items: MealItems): NutritionEntryEntity {
    val totals = items.totals()
    val tags = MealTags.decode(qualityTags)?.let { tags ->
        val scaled = if (calories > 0.0) tags.scaled(totals.kcal / calories) else tags
        scaled.copy(fiberG = totals.fiber)
    }
    return copy(
        foodText = items.foodText(),
        calories = totals.kcal,
        proteinG = totals.protein,
        carbsG = totals.carbs,
        fatG = totals.fat,
        qualityTags = tags?.encode() ?: qualityTags,
        items = items.encode()
    )
}

private fun round1(value: Double): Double = (value * 10.0).roundToLong() / 10.0

private fun formatQty(qty: Double): String =
    if (qty == Math.floor(qty)) qty.toLong().toString() else String.format(Locale.US, "%.1f", qty)
