package com.superfit.app.domain

import android.graphics.Bitmap
import org.json.JSONObject

/** Sends a meal photo or a nutrition-label photo to Gemini. The photo is never stored. */
class FoodPhotoParser {

    /** The same result as a spoken meal. foodText "invalid" means no food was seen. */
    suspend fun parseMeal(photo: Bitmap): ParsedNutritionResult {
        val json = GeminiClient.generateContentWithImage(
            image = photo,
            prompt = PersonalNutrition.promptContext() + "Estimate the nutrition of the meal in this photo. Describe what you see as the user's meal. " +
                "If the photo shows no food or drink, treat it as unrelated to food.",
            systemInstructionText = NutritionParser().systemInstructionText
        )
        val cleaned = json.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val result = ParsedNutritionResult.fromJson(JSONObject(cleaned), "photo")
        return if (result.calories <= 0.0) result.copy(foodText = "invalid") else result
    }

    /** Null when the label couldn't be read. */
    suspend fun parseLabel(photo: Bitmap): PackagedFood? {
        val json = GeminiClient.generateContentWithImage(
            image = photo,
            prompt = "Read the nutrition table on this package.",
            systemInstructionText = PackagedFoodParser.LABEL_SYSTEM_INSTRUCTION
        )
        return PackagedFoodParser.fromLabelJson(json)
    }
}
