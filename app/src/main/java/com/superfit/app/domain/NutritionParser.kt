package com.superfit.app.domain

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import org.json.JSONObject

class NutritionParser(private val apiKey: String) {

    private val model = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = apiKey,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
        },
        systemInstruction = content {
            text(
                "You are an expert clinical nutrition AI. Parse the user's natural language meal description and estimate " +
                "the total calories (kcal), protein (grams), carbohydrates (grams), and fats (grams).\n" +
                "You must return a raw JSON object with the following fields and types:\n" +
                "- \"foodText\": String (a concise summary of the items parsed, e.g. \"2 scrambled eggs and 1 slice of wheat toast\")\n" +
                "- \"calories\": Double\n" +
                "- \"protein\": Double\n" +
                "- \"carbs\": Double\n" +
                "- \"fat\": Double\n" +
                "Provide reasonable estimations if quantities or specific types are not detailed by the user."
            )
        }
    )

    suspend fun parseFoodInput(input: String): ParsedNutritionResult {
        if (input.isBlank()) {
            throw IllegalArgumentException("Food input cannot be empty")
        }

        try {
            val response = model.generateContent("Meal input: \"$input\"")
            val jsonText = response.text ?: throw Exception("Received empty response from Gemini model")
            
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
        } catch (e: Exception) {
            throw e
        }
    }
}

data class ParsedNutritionResult(
    val foodText: String,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double
)
