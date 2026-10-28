package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import org.json.JSONObject

class NutritionParser {

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
            "\"sugarG\": Double (grams of added sugar, 0 if none), " +
            "\"alternatives\": Array (ONLY when you genuinely can't tell this food apart from similar-looking or " +
            "similar-sounding foods, e.g. milk or buttermilk in a glass, veg or chicken momos, chana or chaas; otherwise " +
            "leave it out). Up to 3 other foods it most likely is, most likely first, each an object with \"name\" and " +
            "\"calories\", \"protein\", \"carbs\", \"fat\", \"fiber\", \"oilG\", \"sugarG\" for the same quantity and unit. " +
            "The item itself is your best guess. The items' numbers must add up to the meal's totals.\n" +
            MealTags.PROMPT_FIELDS +
            "Provide reasonable estimations if quantities or specific types are not detailed by the user.\n" +
            "CRITICAL: If the user's input is gibberish, background noise, or completely unrelated to food, meals, or beverages, set the \"foodText\" field to exactly \"invalid\" and set \"calories\", \"protein\", \"carbs\", and \"fat\" all to 0.0."

    suspend fun parseFoodInput(
        input: String,
        history: List<NutritionEntryEntity> = emptyList(),
        /** Set when the words came from the mic: the AI is told they may be misheard, in any language. */
        spoken: SpokenInput? = null
    ): ParsedNutritionResult {
        if (input.isBlank()) {
            throw IllegalArgumentException("Food input cannot be empty")
        }

        // 1. Instant 0ms cache check for repeated foods from history
        // Newest first, so a meal the user corrected wins over older logs of the same words.
        val cached = RepeatMealCache.findRepeatMeal(input, history.sortedByDescending { it.timestamp })
        if (cached != null) {
            android.util.Log.d("NutritionParser", "Instant local repeat cache hit for '$input' -> ${cached.foodText} (${cached.calories} kcal)")
            return cached
        }

        // 2. Cascade across Gemini models (2.5-flash -> 2.0-flash -> 1.5-flash)
        val jsonText = GeminiClient.generateContent(
            prompt = PersonalNutrition.promptContext() + spokenNote(spoken) + "Meal input: \"$input\"",
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

    companion object {
        /**
         * Tells the AI the meal was spoken. A food that sounds like a misheard word goes in as the
         * item with the sound-alike as an alternative, so the person sees an "Is it…?" choice.
         */
        internal fun spokenNote(spoken: SpokenInput?): String {
            if (spoken == null) return ""
            return buildString {
                append(
                    "This meal was spoken and turned into text by the phone. It may be in any language or a mix " +
                        "(for example Hindi and English, or Portuguese), numbers may be said in any language, and the " +
                        "phone sometimes mishears words. If a word looks misheard, use the food it most likely was, judging " +
                        "by how it sounds and what makes sense in a meal, and give the other reading in \"alternatives\". " +
                        "Keep \"name\" in short English as usual.\n"
                )
                if (spoken.otherVersions.isNotEmpty()) {
                    append("Everything the phone thought it heard, best guess first. Log only the food in the meal input; use these just to spot mishearings:\n")
                    (listOf(spoken.text) + spoken.otherVersions).forEach { append("- \"").append(it).append("\"\n") }
                }
            }
        }
    }
}
