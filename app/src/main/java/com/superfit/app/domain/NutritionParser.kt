package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import org.json.JSONObject

class NutritionParser(private val apiKey: String) {

    private val systemInstructionText =
        "You are an expert clinical nutrition AI. Parse the user's natural language meal description and estimate " +
        "the total calories (kcal), protein (grams), carbohydrates (grams), and fats (grams).\n" +
        "You must return a raw JSON object with the following fields and types:\n" +
        "- \"foodText\": String (a concise summary of the items parsed, e.g. \"2 scrambled eggs and 1 slice of wheat toast\")\n" +
        "- \"calories\": Double\n" +
        "- \"protein\": Double\n" +
        "- \"carbs\": Double\n" +
        "- \"fat\": Double\n" +
        "Provide reasonable estimations if quantities or specific types are not detailed by the user.\n" +
        "CRITICAL: If the user's input is gibberish, background noise, or completely unrelated to food, meals, or beverages, set the \"foodText\" field to exactly \"invalid\" and set \"calories\", \"protein\", \"carbs\", and \"fat\" all to 0.0."

    suspend fun parseFoodInput(
        input: String,
        history: List<NutritionEntryEntity> = emptyList()
    ): ParsedNutritionResult {
        if (input.isBlank()) {
            throw IllegalArgumentException("Food input cannot be empty")
        }

        // 1. Instant 0ms cache check for repeated foods from history
        val cached = RepeatMealCache.findRepeatMeal(input, history)
        if (cached != null) {
            android.util.Log.d("NutritionParser", "Instant local repeat cache hit for '$input' -> ${cached.foodText} (${cached.calories} kcal)")
            return cached
        }

        // 2. Cascade across Gemini models (2.5-flash -> 2.0-flash -> 1.5-flash)
        val jsonText = GeminiClient.generateContent(
            apiKey = apiKey,
            prompt = "Meal input: \"$input\"",
            systemInstructionText = systemInstructionText,
            isJson = true
        )

        // Clean up codeblock markers if any exist in the response
        val cleanJsonText = jsonText.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val jsonObject = JSONObject(cleanJsonText)
        val foodText = jsonObject.optString("foodText", input)
        val calories = jsonObject.optDouble("calories", 0.0)
        val protein = jsonObject.optDouble("protein", 0.0)
        val carbs = jsonObject.optDouble("carbs", 0.0)
        val fat = jsonObject.optDouble("fat", 0.0)

        return ParsedNutritionResult(
            foodText = foodText,
            calories = calories,
            proteinG = protein,
            carbsG = carbs,
            fatG = fat
        )
    }
}

data class ParsedNutritionResult(
    val foodText: String,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double
)
