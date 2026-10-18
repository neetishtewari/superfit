package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import org.json.JSONObject

class NutritionParser(private val apiKey: String) {

    internal val systemInstructionText =
        "You are an expert clinical nutrition AI. Parse the user's natural language meal description and estimate " +
        "the total calories (kcal), protein (grams), carbohydrates (grams), and fats (grams).\n" +
        "You must return a raw JSON object with the following fields and types:\n" +
        "- \"foodText\": String (a concise summary of the items parsed, e.g. \"2 scrambled eggs and 1 slice of wheat toast\")\n" +
        "- \"calories\": Double\n" +
        "- \"protein\": Double\n" +
        "- \"carbs\": Double\n" +
        "- \"fat\": Double\n" +
        "- \"items\": Array of the separate foods in the meal, each an object with: " +
        "\"name\": String (short English name, e.g. \"roti\", \"dal\", \"chai\"), " +
        "\"quantity\": Double, " +
        "\"unit\": String (one of piece, katori, bowl, cup, glass, slice, tbsp, tsp, g, ml, serving), " +
        "\"calories\", \"protein\", \"carbs\", \"fat\", \"fiber\": Double (for that whole quantity), " +
        "\"oilG\": Double (grams of oil, ghee or butter added in cooking or serving it, 0 if none), " +
        "\"sugarG\": Double (grams of added sugar, 0 if none). The items' numbers must add up to the meal's totals.\n" +
        MealTags.PROMPT_FIELDS +
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

        return ParsedNutritionResult.fromJson(JSONObject(cleanJsonText), input)
    }
}
