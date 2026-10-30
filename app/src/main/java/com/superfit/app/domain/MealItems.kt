package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong
import org.json.JSONArray
import org.json.JSONObject

/** How much oil or sugar went in compared with the AI's estimate. */
enum class Level(val factor: Double) { LOW(0.5), NORMAL(1.0), HIGH(1.5) }

/** A household unit an item was switched away from, so it can be switched back. */
data class HomeUnit(val unit: String, val weight: Double)

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
    val sugarG: Double,
    /**
     * When the AI couldn't tell this food from look-alikes or sound-alikes (milk or buttermilk,
     * veg or chicken momos), every option it considered, its best guess first, each with its own
     * per-unit numbers. Empty when it was sure. The options' qty, unit and choices are unused.
     */
    val choices: List<MealItem> = emptyList(),
    /** Grams (or ml for drinks) in ONE [unit], as the AI weighed it. Null for older meals. */
    val weight: Double? = null,
    /** "g" for solid food, "ml" for drinks, as the AI judged it. Null for older meals. */
    val metric: String? = null,
    /** Set after the user switched a household amount to g or ml: what to switch back to. */
    val home: HomeUnit? = null
) {
    /** True when the AI offered other foods this might be. */
    val isUnsure: Boolean get() = choices.size > 1

    /** This item as choice [index] instead: same amount, that food's numbers. */
    fun choose(index: Int): MealItem {
        val c = choices.getOrNull(index) ?: return this
        return copy(
            // Once switched to g or ml, the amount is already a weight.
            weight = if (home != null) weight else c.weight ?: weight,
            metric = c.metric ?: metric,
            name = c.name,
            kcal = c.kcal,
            protein = c.protein,
            carbs = c.carbs,
            fat = c.fat,
            fiber = c.fiber,
            oilG = c.oilG,
            sugarG = c.sugarG
        )
    }

    /** True when the amount is in grams or ml. */
    val isMetric: Boolean get() = unit == "g" || unit == "ml"

    /** How much one tap of − or + changes the amount: small steps for a spoon of ghee, bigger for rice. */
    val step: Double get() = when {
        !isMetric -> 0.5
        qty < 50.0 -> 5.0
        qty < 250.0 -> 10.0
        else -> 25.0
    }

    /** "about 120 g" for a household amount the AI weighed; null when it's already in g or ml. */
    fun weightText(): String? {
        val w = weight ?: return null
        val m = metric ?: return null
        if (isMetric || w <= 0.0) return null
        return "about ${roundWeight(w * qty)} $m"
    }

    /** True when this item can be shown in g or ml, or switched back to its household unit. */
    val canSwitchUnit: Boolean get() = if (isMetric) home != null else weightText() != null

    /** The same food and amount in g or ml (1 katori at 150 g becomes 150 g), or back again. */
    fun switchUnit(): MealItem {
        if (!canSwitchUnit) return this
        return if (isMetric) {
            val h = home ?: return this
            perUnitTimes(h.weight).copy(qty = qty / h.weight, unit = h.unit, weight = h.weight, home = null)
        } else {
            val w = weight ?: return this
            perUnitTimes(1.0 / w).copy(qty = qty * w, unit = metric ?: "g", weight = 1.0, home = HomeUnit(unit, w))
        }
    }

    /** Every per-unit number, including the choices', multiplied by [factor]. */
    private fun perUnitTimes(factor: Double): MealItem {
        fun MealItem.times() = copy(
            kcal = kcal * factor, protein = protein * factor, carbs = carbs * factor, fat = fat * factor,
            fiber = fiber * factor, oilG = oilG * factor, sugarG = sugarG * factor
        )
        return times().copy(choices = choices.map { it.times() })
    }

    /** "3 roti (120 g)", "1 katori dal (150 g)", "150 g rice". */
    fun label(): String {
        val amount = formatQty(qty)
        val base = when (unit) {
            "piece" -> "$amount $name"
            "serving" -> if (qty == 1.0) name else "$amount × $name"
            else -> "$amount $unit $name"
        }
        val w = weight
        return if (!isMetric && w != null && w > 0.0 && metric != null) "$base (${roundWeight(w * qty)} $metric)" else base
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
    val hasEstimates: Boolean = true,
    /** True once the user has looked at the meal in a sheet and logged or saved it. */
    val checked: Boolean = false,
    /** What the user typed or said, so saying it again reuses this meal and its corrections. */
    val said: String? = null
) {
    /** How many items the AI wasn't sure about, while the user hasn't looked at the meal yet. */
    val itemsToCheck: Int get() = if (checked) 0 else items.count { it.isUnsure }

    /** The meal with item [index] swapped for a different food, keeping the rest as it is. */
    fun replace(index: Int, item: MealItem): MealItems = replace(index, listOf(item))

    /** The meal with item [index] swapped for one or more foods ("chilla with chutney"). */
    fun replace(index: Int, with: List<MealItem>): MealItems =
        if (index !in items.indices || with.isEmpty()) {
            this
        } else {
            copy(
                items = items.toMutableList().also {
                    it.removeAt(index)
                    it.addAll(index, with.map { w -> w.copy(choices = emptyList()) })
                }
            )
        }

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
        .put("chk", checked)
        .apply { if (said != null) put("said", said) }
        .put(
            "items",
            JSONArray().apply {
                items.forEach { item ->
                    put(
                        nutrientsJson(item).put("qty", item.qty).put("unit", item.unit).apply {
                            item.home?.let { put("hu", it.unit).put("hw", it.weight) }
                            if (item.choices.isNotEmpty()) put("alt", JSONArray().apply { item.choices.forEach { put(nutrientsJson(it)) } })
                        }
                    )
                }
            }
        )
        .toString()

    private fun nutrientsJson(item: MealItem): JSONObject = JSONObject()
        .put("name", item.name)
        .put("kcal", item.kcal).put("p", item.protein).put("c", item.carbs).put("f", item.fat)
        .put("fib", item.fiber).put("oil", item.oilG).put("sug", item.sugarG)
        .apply {
            item.weight?.let { put("w", it) }
            item.metric?.let { put("m", it) }
        }

    companion object {
        fun decode(raw: String?): MealItems? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val json = JSONObject(raw)
                val arr = json.getJSONArray("items")
                fun read(o: JSONObject, qty: Double, unit: String) = MealItem(
                    name = o.getString("name"), qty = qty, unit = unit,
                    kcal = o.getDouble("kcal"), protein = o.getDouble("p"), carbs = o.getDouble("c"),
                    fat = o.getDouble("f"), fiber = o.optDouble("fib", 0.0),
                    oilG = o.optDouble("oil", 0.0), sugarG = o.optDouble("sug", 0.0),
                    weight = o.optDouble("w", Double.NaN).takeIf { !it.isNaN() && it > 0.0 },
                    metric = o.optString("m", "").takeIf { it == "g" || it == "ml" }
                )
                val items = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val qty = o.getDouble("qty")
                    val unit = o.getString("unit")
                    val alt = o.optJSONArray("alt")
                    val choices = if (alt == null) emptyList() else (0 until alt.length()).map { read(alt.getJSONObject(it), qty, unit) }
                    val hw = o.optDouble("hw", Double.NaN)
                    val home = o.optString("hu", "").takeIf { it.isNotBlank() && !hw.isNaN() && hw > 0.0 }?.let { HomeUnit(it, hw) }
                    read(o, qty, unit).copy(choices = choices, home = home)
                }
                if (items.isEmpty()) return null
                MealItems(
                    items = items,
                    oil = levelOf(json.optString("oil")),
                    sugar = levelOf(json.optString("sugar")),
                    hasEstimates = json.optBoolean("est", true),
                    checked = json.optBoolean("chk", false),
                    said = json.optString("said", "").ifBlank { null }
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
                itemFromGemini(o) ?: return null
            }
            if (items.sumOf { it.kcal * it.qty } <= 0.0) return null
            return MealItems(items)
        }

        /**
         * One food from the AI, numbers for its whole quantity, stored per unit. Its
         * `alternatives` (other foods it might be, for the same amount) become [MealItem.choices],
         * at most three of them after the AI's own guess. Null without a name or an amount.
         */
        fun itemFromGemini(o: JSONObject): MealItem? {
            val name = o.optString("name", "").trim()
            val qty = o.optDouble("quantity", 0.0)
            if (name.isEmpty() || qty.isNaN() || qty <= 0.0) return null
            val unit = o.optString("unit", "serving").trim().lowercase().ifEmpty { "serving" }
            fun nutrients(src: JSONObject, label: String): MealItem {
                fun perUnit(key: String) = src.optDouble(key, 0.0).let { if (it.isNaN() || it < 0.0) 0.0 else it / qty }
                // The AI says how much the whole amount weighs and whether it's a drink.
                val metric = when {
                    unit == "g" || unit == "ml" -> unit
                    (if (src.has("liquid")) src else o).optBoolean("liquid", false) -> "ml"
                    else -> "g"
                }
                val weight = when {
                    unit == "g" || unit == "ml" -> 1.0
                    else -> (if (src.has("grams")) src else o).optDouble("grams", Double.NaN)
                        .takeIf { !it.isNaN() && it > 0.0 }?.let { it / qty }
                }
                return MealItem(
                    name = label, qty = qty, unit = unit,
                    kcal = perUnit("calories"), protein = perUnit("protein"), carbs = perUnit("carbs"),
                    fat = perUnit("fat"), fiber = perUnit("fiber"), oilG = perUnit("oilG"), sugarG = perUnit("sugarG"),
                    weight = weight, metric = if (weight == null) null else metric
                )
            }
            val item = nutrients(o, name)
            val seen = mutableSetOf(name.lowercase())
            val alternatives = o.optJSONArray("alternatives")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val alt = arr.optJSONObject(i) ?: return@mapNotNull null
                    val altName = alt.optString("name", "").trim()
                    if (altName.isEmpty() || !seen.add(altName.lowercase())) null else nutrients(alt, altName)
                }
            }.orEmpty().take(MAX_ALTERNATIVES)
            return if (alternatives.isEmpty()) item else item.copy(choices = listOf(item) + alternatives)
        }

        private const val MAX_ALTERNATIVES = 3

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
    // A different food (chicken momos for veg) makes the AI's judgements wrong, so the tags are
    // dropped and the meal is tagged again after saving.
    if (hasNewFood(items)) {
        return copy(
            foodText = items.foodText(),
            calories = totals.kcal,
            proteinG = totals.protein,
            carbsG = totals.carbs,
            fatG = totals.fat,
            qualityTags = null,
            items = items.copy(checked = true).encode()
        )
    }
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
        items = items.copy(checked = true).encode()
    )
}

/** True when [items] names a food this meal didn't have. Removing a food doesn't count. */
fun NutritionEntryEntity.hasNewFood(items: MealItems): Boolean {
    val before = (MealItems.decode(this.items) ?: MealItems.legacy(this)).items.map { it.name.trim().lowercase() }.toSet()
    return items.items.any { it.name.trim().lowercase() !in before }
}

private fun round1(value: Double): Double = (value * 10.0).roundToLong() / 10.0

/** Weights to a sensible precision: 7 g, 85 g, 120 g, 350 ml. */
private fun roundWeight(w: Double): String = when {
    w < 20.0 -> w.roundToLong().toString()
    else -> ((w / 5.0).roundToLong() * 5).toString()
}

private fun formatQty(qty: Double): String =
    if (qty == Math.floor(qty)) qty.toLong().toString() else String.format(Locale.US, "%.1f", qty)
