package com.superfit.app.domain

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.superfit.app.data.ActivityTelemetryEntity
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.SleepTelemetryEntity
import com.superfit.app.data.UserProfileEntity

class CoachingEngine(private val apiKey: String) {

    private val model = GenerativeModel(
        modelName = "gemini-3.5-flash",
        apiKey = apiKey,
        generationConfig = generationConfig {
            responseMimeType = "text/plain"
        },
        systemInstruction = content {
            text(
                "You are Superfit AI Coach, a premium, friendly, highly analytical personal trainer and health scientist.\n" +
                "Your role is to analyze the user's daily metrics (steps, sleep quality, active/passive calories, macronutrients eaten vs targets, and fitness goal) and provide a concise, highly actionable, personalized daily summary.\n" +
                "Keep the tone encouraging but scientifically precise, modern, and direct.\n" +
                "You MUST limit your response to exactly 2 to 3 sentences (approx 50-80 words). Do not write a greeting or a signature (e.g. no 'Hey there,' or 'Best, Coach'). Use clean Markdown styling where appropriate (like bolding key insights or targets)."
            )
        }
    )

    suspend fun generateDailyInsight(
        profile: UserProfileEntity,
        activity: ActivityTelemetryEntity,
        sleep: SleepTelemetryEntity?,
        nutrition: List<NutritionEntryEntity>,
        macroTargets: MacroTargets,
        caloriesEaten: Double,
        proteinEaten: Double,
        carbsEaten: Double,
        fatEaten: Double
    ): String {
        val sleepText = if (sleep != null) {
            "Sleep Duration: ${String.format("%.1f", sleep.sleepDurationSeconds / 3600.0)}h (Deep Sleep: ${sleep.deepSleepDurationSeconds / 60}m), Sleep Readiness Score: ${sleep.readinessScore}%"
        } else {
            "Sleep: No sleep telemetry available today (assume baseline recovery)."
        }

        val nutritionText = "Calories Eaten: ${caloriesEaten.toInt()} kcal (Target: ${macroTargets.calories.toInt()} kcal). " +
                "Protein: ${proteinEaten.toInt()}g (Target: ${macroTargets.proteinG.toInt()}g). " +
                "Carbs: ${carbsEaten.toInt()}g (Target: ${macroTargets.carbsG.toInt()}g). " +
                "Fat: ${fatEaten.toInt()}g (Target: ${macroTargets.fatG.toInt()}g)."

        val activityText = "Steps: ${activity.steps}, Active Calories Burned: ${activity.activeCalories.toInt()} kcal."

        val goalText = "Current Goal: ${profile.goal} (Calorie Offset: ${profile.calorieOffset} kcal)."

        val prompt = """
            User Profile: Age ${profile.age}, Weight ${profile.weightKg} kg, Height ${profile.heightCm} cm, Activity Level Multiplier ${profile.activityMultiplier}.
            Goal: $goalText
            Daily Physical Metrics:
            - $activityText
            - $sleepText
            Daily Nutrition:
            - $nutritionText

            Based on these metrics and their goal, give them their Coaching Insight.
        """.trimIndent()

        return try {
            val response = model.generateContent(prompt)
            response.text ?: "Your daily metrics are looking solid! Keep moving, eat to your target macros, and get quality sleep tonight."
        } catch (e: Exception) {
            throw e
        }
    }
}
