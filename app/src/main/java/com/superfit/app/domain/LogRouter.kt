package com.superfit.app.domain

import org.json.JSONObject

/** What the user said, split into the part about food and the part about exercise. "" when absent. */
data class RoutedLog(val food: String, val exercise: String) {
    val isEmpty: Boolean get() = food.isBlank() && exercise.isBlank()
}

/**
 * Lets one mic log food, exercise or both: a quick AI call splits the spoken words, and each part
 * then goes to its usual parser. The AI decides what is food and what is exercise; there are no
 * word lists here.
 */
class LogRouter(private val apiKey: String) {

    suspend fun route(input: String): RoutedLog = parse(
        GeminiClient.generateContent(
            apiKey = apiKey,
            prompt = "Said: \"$input\"",
            systemInstructionText = SYSTEM_INSTRUCTION,
            isJson = true
        )
    )

    companion object {
        val SYSTEM_INSTRUCTION =
            "Someone said what they ate, drank or did for exercise, in one sentence. Split their words into two parts:\n" +
                "- \"food\": the words about food or drink they had\n" +
                "- \"exercise\": the words about exercise, sport or physical activity they did\n" +
                "Copy their own words exactly. Never rewrite, translate, summarise or add anything. " +
                "Use \"\" for a part that isn't there. If it is about neither, both are \"\".\n" +
                "Return a raw JSON object: {\"food\": String, \"exercise\": String}"

        fun parse(json: String): RoutedLog {
            val cleaned = json.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val obj = runCatching { JSONObject(cleaned) }.getOrNull() ?: return RoutedLog("", "")
            fun part(key: String) = if (obj.isNull(key)) "" else obj.optString(key, "").trim()
            return RoutedLog(part("food"), part("exercise"))
        }
    }
}
