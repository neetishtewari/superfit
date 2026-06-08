package com.superfit.app.domain

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import org.json.JSONObject

class WorkoutParser(private val apiKey: String) {

    private val model = GenerativeModel(
        modelName = "gemini-3.1-flash-lite",
        apiKey = apiKey,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
        },
        systemInstruction = content {
            text(
                "You are an expert exercise physiology AI. Parse the user's natural language workout or exercise description " +
                "and estimate the total active calories (kcal) burned.\n" +
                "You must return a raw JSON object with the following fields and types:\n" +
                "- \"workoutText\": String (a concise, clean summary of the exercises performed, e.g. \"20 burpees, 50 jumping jacks, and 30 pushups\")\n" +
                "- \"caloriesBurned\": Double (an estimate of total calories burned based on standard MET values)\n" +
                "- \"workoutType\": String (must be either \"Cardio\" or \"Strength\" based on the dominant exercise type)\n" +
                "- \"setsCount\": Int (total number of sets performed. Default to 1 if not specified or for general cardio)\n" +
                "- \"repsCount\": Int (total number of repetitions completed across all exercises. Default to 0 for general cardio)\n\n" +
                "Provide reasonable estimations if duration, intensity, sets, reps or specific dumbbells/weights are not detailed by the user.\n" +
                "CRITICAL: If the user's input is gibberish, background noise, or completely unrelated to exercises, workouts, gym training, sports, or physical activity, set the \"workoutText\" field to exactly \"invalid\", \"caloriesBurned\" to 0.0, \"workoutType\" to \"Cardio\", \"setsCount\" to 0, and \"repsCount\" to 0."
            )
        }
    )

    suspend fun parseWorkoutInput(input: String): ParsedWorkoutResult {
        if (input.isBlank()) {
            throw IllegalArgumentException("Workout input cannot be empty")
        }

        try {
            val response = model.generateContent("Workout input: \"$input\"")
            val jsonText = response.text ?: throw Exception("Received empty response from Gemini model")
            
            val cleanJsonText = jsonText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val jsonObject = JSONObject(cleanJsonText)
            val workoutText = jsonObject.optString("workoutText", input)
            val caloriesBurned = jsonObject.optDouble("caloriesBurned", 0.0)
            val workoutType = jsonObject.optString("workoutType", "Cardio")
            val setsCount = jsonObject.optInt("setsCount", 1)
            val repsCount = jsonObject.optInt("repsCount", 0)

            return ParsedWorkoutResult(
                workoutText = workoutText,
                caloriesBurned = caloriesBurned,
                workoutType = workoutType,
                setsCount = setsCount,
                repsCount = repsCount
            )
        } catch (e: Exception) {
            throw e
        }
    }
}

data class ParsedWorkoutResult(
    val workoutText: String,
    val caloriesBurned: Double,
    val workoutType: String,
    val setsCount: Int,
    val repsCount: Int
)
