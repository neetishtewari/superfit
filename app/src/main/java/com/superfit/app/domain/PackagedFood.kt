package com.superfit.app.domain

import org.json.JSONObject

data class Nutrients(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double, val fiber: Double) {
    fun times(f: Double): Nutrients = Nutrients(kcal * f, protein * f, carbs * f, fat * f, fiber * f)
}

/**
 * A packaged food from a barcode lookup or a photo of its nutrition label. [perServing] is
 * filled in from [per100g] when the packet gives a serving size but no per-serving numbers.
 */
data class PackagedFood(
    val name: String?,
    val per100g: Nutrients,
    val servingGrams: Double?,
    val perServing: Nutrients?
)

/** How much of a packet the user ate: a number of servings, or grams. */
sealed interface PackAmount {
    data class Servings(val count: Double) : PackAmount
    data class Grams(val grams: Double) : PackAmount
}

fun PackagedFood.defaultAmount(): PackAmount =
    if (perServing != null) PackAmount.Servings(1.0) else PackAmount.Grams(100.0)

/** Servings go ½, 1, 2, 3…; grams go in 25 g steps. Never below ½ serving or 25 g. */
fun PackAmount.stepUp(): PackAmount = when (this) {
    is PackAmount.Servings -> PackAmount.Servings(if (count < 1.0) 1.0 else count + 1.0)
    is PackAmount.Grams -> PackAmount.Grams(grams + 25.0)
}

fun PackAmount.stepDown(): PackAmount = when (this) {
    is PackAmount.Servings -> PackAmount.Servings(if (count <= 1.0) 0.5 else count - 1.0)
    is PackAmount.Grams -> PackAmount.Grams(maxOf(25.0, grams - 25.0))
}

/** One meal item, so the meal stays editable later. Packets have no oil or sugar switches. */
fun PackagedFood.toMealItems(amount: PackAmount): MealItems {
    val label = name ?: "Packaged food"
    val item = when (amount) {
        is PackAmount.Servings -> {
            val n = perServing ?: per100g.times((servingGrams ?: 100.0) / 100.0)
            MealItem(label, amount.count, "serving", n.kcal, n.protein, n.carbs, n.fat, n.fiber, 0.0, 0.0)
        }
        is PackAmount.Grams -> {
            val n = per100g.times(0.01)
            MealItem(label, amount.grams, "g", n.kcal, n.protein, n.carbs, n.fat, n.fiber, 0.0, 0.0)
        }
    }
    return MealItems(listOf(item), hasEstimates = false)
}

object PackagedFoodParser {

    val LABEL_SYSTEM_INSTRUCTION =
        "You read nutrition tables on food packages. Return a raw JSON object with:\n" +
            "- \"name\": String or null (the product name if it is visible, in English)\n" +
            "- \"per100g\": {\"kcal\", \"protein\", \"carbs\", \"fat\", \"fiber\"} as Doubles, per 100 g or 100 ml\n" +
            "- \"servingGrams\": Double or null (the serving size in grams or ml, if printed)\n" +
            "- \"perServing\": the same five fields per serving, or null if not printed\n" +
            "If the table gives kJ only, convert to kcal. If no nutrition table can be read, set \"name\" to exactly \"invalid\"."

    /** Null when Open Food Facts doesn't know the product or has no calories for it. */
    fun fromOpenFoodFacts(json: String): PackagedFood? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (root.optInt("status", 0) != 1) return null
        val product = root.optJSONObject("product") ?: return null
        val n = product.optJSONObject("nutriments") ?: return null
        val kcal100 = num(n, "energy-kcal_100g") ?: num(n, "energy_100g")?.div(4.184)
        if (kcal100 == null || kcal100 <= 0.0) return null
        val per100g = Nutrients(
            kcal100,
            num(n, "proteins_100g") ?: 0.0,
            num(n, "carbohydrates_100g") ?: 0.0,
            num(n, "fat_100g") ?: 0.0,
            num(n, "fiber_100g") ?: 0.0
        )
        val serving = num(product, "serving_quantity")?.takeIf { it > 0.0 }
        val kcalServing = num(n, "energy-kcal_serving") ?: num(n, "energy_serving")?.div(4.184)
        val perServing = when {
            serving == null -> null
            kcalServing != null && kcalServing > 0.0 -> Nutrients(
                kcalServing,
                num(n, "proteins_serving") ?: per100g.protein * serving / 100.0,
                num(n, "carbohydrates_serving") ?: per100g.carbs * serving / 100.0,
                num(n, "fat_serving") ?: per100g.fat * serving / 100.0,
                num(n, "fiber_serving") ?: per100g.fiber * serving / 100.0
            )
            else -> per100g.times(serving / 100.0)
        }
        val name = product.optString("product_name", "").trim().ifEmpty { null }
        return PackagedFood(name, per100g, serving, perServing)
    }

    /** Null when the AI couldn't read a table, or read one with no calories. */
    fun fromLabelJson(json: String): PackagedFood? {
        val cleaned = json.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root = runCatching { JSONObject(cleaned) }.getOrNull() ?: return null
        val name = if (root.isNull("name")) null else root.optString("name", "").trim().ifEmpty { null }
        if (name.equals("invalid", ignoreCase = true)) return null
        val per100g = nutrients(root.optJSONObject("per100g")) ?: return null
        if (per100g.kcal <= 0.0) return null
        val serving = num(root, "servingGrams")?.takeIf { it > 0.0 }
        val perServing = nutrients(root.optJSONObject("perServing"))?.takeIf { it.kcal > 0.0 }
            ?: serving?.let { per100g.times(it / 100.0) }
        return PackagedFood(name, per100g, serving, perServing)
    }

    private fun nutrients(o: JSONObject?): Nutrients? {
        if (o == null) return null
        return Nutrients(
            num(o, "kcal") ?: return null,
            num(o, "protein") ?: 0.0,
            num(o, "carbs") ?: 0.0,
            num(o, "fat") ?: 0.0,
            num(o, "fiber") ?: 0.0
        )
    }

    /** A number whether the JSON has it as a number or a string; null if missing or not a number. */
    private fun num(o: JSONObject, key: String): Double? {
        if (!o.has(key) || o.isNull(key)) return null
        val value = when (val raw = o.opt(key)) {
            is Number -> raw.toDouble()
            is String -> raw.trim().replace(',', '.').toDoubleOrNull()
            else -> null
        }
        return value?.takeIf { !it.isNaN() && it >= 0.0 }
    }
}
