package com.superfit.app.domain

import org.json.JSONObject

data class ParsedNutritionResult(
    val foodText: String,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val tags: MealTags? = null,
    /** The meal's separate foods, so the user can adjust them later. Null if the AI gave none usable. */
    val items: MealItems? = null
) {
    companion object {
        /**
         * Reads the AI's meal JSON. When it has a usable item list, the meal's totals are the
         * items' sums, so editing an item later never jumps the numbers.
         */
        fun fromJson(json: JSONObject, input: String): ParsedNutritionResult {
            val foodText = json.optString("foodText", input)
            val tags = runCatching { MealTags.fromJson(json) }.getOrNull()
            val items = if (foodText == "invalid") {
                null
            } else {
                MealItems.fromGemini(json.optJSONArray("items"))?.let { if (input == "photo") it else it.copy(said = input.trim()) }
            }
            val totals = items?.totals()
            return ParsedNutritionResult(
                foodText = foodText,
                calories = totals?.kcal ?: json.optDouble("calories", 0.0),
                proteinG = totals?.protein ?: json.optDouble("protein", 0.0),
                carbsG = totals?.carbs ?: json.optDouble("carbs", 0.0),
                fatG = totals?.fat ?: json.optDouble("fat", 0.0),
                tags = tags,
                items = items
            )
        }
    }
}
