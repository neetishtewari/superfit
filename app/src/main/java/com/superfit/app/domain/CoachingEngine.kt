package com.superfit.app.domain

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.superfit.app.data.ActivityTelemetryEntity
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.SleepTelemetryEntity
import com.superfit.app.data.UserProfileEntity
import com.superfit.app.data.WorkoutEntryEntity
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CoachingEngine(private val apiKey: String) {

    private val model = GenerativeModel(
        modelName = "gemini-3.1-flash-lite",
        apiKey = apiKey,
        generationConfig = generationConfig {
            responseMimeType = "text/plain"
        },
        systemInstruction = content {
            text(
                "You are Superfit AI Coach, the world's best personal fitness trainer and health scientist.\n" +
                "Your role is to analyze the user's daily metrics and weekly historical logs (nutrition, specific foods eaten, workouts, step counts, and sleep patterns) to provide a concise, highly analytical, and deeply meaningful coaching insight.\n" +
                "CRITICAL: Think like an elite trainer. Look for non-obvious patterns, habits, or triggers that a casual user might miss (e.g., 'your habit of regularly eating desserts is pushing you back', 'the bread you are logging may not be as healthy as you think', workouts late in the evening disrupting sleep quality, protein targets missed on specific active days, or step counts dropping on weekends).\n" +
                "Keep the tone encouraging but scientifically precise, modern, and direct.\n" +
                "You MUST limit your response to exactly 2 to 3 sentences (approx 50-80 words). Do not write a greeting or a signature (e.g. no 'Hey there,' or 'Best, Coach'). Use clean Markdown styling where appropriate (like bolding key insights or targets).\n" +
                "CRITICAL: Be aware of the 'Current Time of Day' provided in the prompt. If the current time is in the morning, afternoon, or early evening (e.g., before 7:00 PM), do NOT flag low calorie or protein intake as a deficit or critical problem; instead, frame recommendations around what they should focus on eating for the rest of the day. Only diagnose definitive daily calorie/macronutrient deficits or surpluses when it is late in the evening (after 7:00 PM)."
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
        fatEaten: Double,
        historyNutrition: List<NutritionEntryEntity> = emptyList(),
        historyWorkouts: List<WorkoutEntryEntity> = emptyList(),
        historyActivity: List<ActivityTelemetryEntity> = emptyList(),
        historySleep: List<SleepTelemetryEntity> = emptyList()
    ): String {
        val currentTime = LocalTime.now()
        val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")
        val formattedTime = currentTime.format(timeFormatter)

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

        val historyText = if (historyNutrition.isNotEmpty() || historyWorkouts.isNotEmpty() || historyActivity.isNotEmpty()) {
            val sb = StringBuilder()
            sb.append("\nWeekly Historical Trends (Past 7 Days):\n")

            val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.systemDefault())
            
            // Extract unique dates from the last 7 days of logs
            val allDates = (historyNutrition.map { dateFormatter.format(Instant.ofEpochMilli(it.timestamp)) } +
                    historyWorkouts.map { dateFormatter.format(Instant.ofEpochMilli(it.timestamp)) } +
                    historyActivity.map { it.date } +
                    historySleep.map { it.date }).distinct().sortedDescending().take(7)

            allDates.forEach { dateStr ->
                sb.append("- Date: $dateStr\n")
                // Nutrition
                val dayNutrition = historyNutrition.filter { dateFormatter.format(Instant.ofEpochMilli(it.timestamp)) == dateStr }
                if (dayNutrition.isNotEmpty()) {
                    val cals = dayNutrition.sumOf { it.calories }.toInt()
                    val p = dayNutrition.sumOf { it.proteinG }.toInt()
                    val foods = dayNutrition.map { it.foodText }.joinToString(", ")
                    sb.append("  • Nutrition: $cals kcal, ${p}g Protein. Foods logged: $foods\n")
                }
                // Workouts
                val dayWorkouts = historyWorkouts.filter { dateFormatter.format(Instant.ofEpochMilli(it.timestamp)) == dateStr }
                if (dayWorkouts.isNotEmpty()) {
                    val details = dayWorkouts.map { "${it.workoutType} (${it.description}, ${it.caloriesBurned.toInt()} kcal)" }.joinToString(", ")
                    sb.append("  • Workouts: $details\n")
                }
                // Activity & Sleep
                val dayAct = historyActivity.find { it.date == dateStr }
                val daySleep = historySleep.find { it.date == dateStr }
                if (dayAct != null) {
                    sb.append("  • Activity: ${dayAct.steps} steps, ${dayAct.activeCalories.toInt()} kcal burned\n")
                }
                if (daySleep != null) {
                    sb.append("  • Sleep: ${String.format("%.1f", daySleep.sleepDurationSeconds / 3600.0)}h, Readiness: ${daySleep.readinessScore}%\n")
                }
            }
            sb.toString()
        } else {
            "\nWeekly Historical Trends: No historical logs available yet."
        }

        val prompt = """
            Current Time of Day: $formattedTime
            User Profile: Age ${profile.age}, Weight ${profile.weightKg} kg, Height ${profile.heightCm} cm, Activity Level Multiplier ${profile.activityMultiplier}.
            Goal: $goalText
            Daily Physical Metrics:
            - $activityText
            - $sleepText
            Daily Nutrition:
            - $nutritionText
            $historyText

            Based on these daily and weekly historical metrics, think like the world's best personal trainer. Spot key patterns or bad habits in their logs, and write their Coaching Insight.
        """.trimIndent()

        return try {
            val response = model.generateContent(prompt)
            response.text ?: "Your daily metrics are looking solid! Keep moving, eat to your target macros, and get quality sleep tonight."
        } catch (e: Exception) {
            throw e
        }
    }
}
