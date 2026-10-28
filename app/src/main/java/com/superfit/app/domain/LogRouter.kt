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
class LogRouter {

    suspend fun route(input: String): RoutedLog = route(SpokenInput(input))

    suspend fun route(input: SpokenInput): RoutedLog = parse(
        GeminiClient.generateContent(
            prompt = prompt(input),
            systemInstructionText = SYSTEM_INSTRUCTION,
            isJson = true
        )
    )

    companion object {
        val SYSTEM_INSTRUCTION =
            "Someone said what they ate, drank or did for exercise, in one sentence, and the phone turned their speech " +
                "into text. They may speak any language or mix languages (for example Hindi and English, or Portuguese), " +
                "and may say numbers in any language. The phone sometimes mishears words.\n" +
                "When you are given other ways the phone heard it, first pick the version that makes the most sense as " +
                "food, drink or exercise. Then split that version's words into two parts:\n" +
                "- \"food\": the words about food or drink they had\n" +
                "- \"exercise\": the words about exercise, sport or physical activity they did\n" +
                "Copy the words exactly, numbers and amounts included. Never rewrite, translate, summarise or add anything. " +
                "Use \"\" for a part that isn't there. If it is about neither, both are \"\".\n" +
                "Return a raw JSON object: {\"food\": String, \"exercise\": String}"

        /** The words to split, with the phone's other versions when it offered any. */
        fun prompt(input: SpokenInput): String = buildString {
            append("Said: \"").append(input.text).append('"')
            if (input.otherVersions.isNotEmpty()) {
                append("\nOther ways the phone heard it:")
                input.otherVersions.forEach { append("\n- \"").append(it).append('"') }
            }
        }

        fun parse(json: String): RoutedLog {
            val cleaned = json.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val obj = runCatching { JSONObject(cleaned) }.getOrNull() ?: return RoutedLog("", "")
            fun part(key: String) = if (obj.isNull(key)) "" else obj.optString(key, "").trim()
            return RoutedLog(part("food"), part("exercise"))
        }
    }
}
