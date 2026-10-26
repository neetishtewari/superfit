package com.superfit.app.domain

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Asks the AI for the numbers of one food the user says an item really was. */
class MealItemReplacer {

    /**
     * [item] as [newName], at the same amount unless the user's words give one. Sent through the
     * same meal reader as voice and photo logs, so "2 chilla with chutney" can become two items.
     */
    suspend fun replace(item: MealItem, newName: String, rest: List<MealItem>): List<MealItem> {
        val others = rest.joinToString(", ") { it.label() }.ifEmpty { "nothing else" }
        val response = GeminiClient.generateContent(
            prompt = PersonalNutrition.promptContext() +
                "The user is correcting one food in a meal. If they don't say an amount, it is " +
                "${item.qty} ${item.unit}. The rest of the meal ($others) is already logged; don't include it.\n" +
                "Meal input: \"${newName.trim()}\"",
            systemInstructionText = NutritionParser().systemInstructionText,
            isJson = true
        )
        return parse(response, item) ?: run {
            android.util.Log.w("MealItemReplacer", "Unreadable answer for '$newName': ${response.take(500)}")
            throw UnreadableAnswer()
        }
    }

    /** The AI answered, but not with a food the app could read. */
    class UnreadableAnswer : IllegalStateException("No usable item in the AI's answer")

    companion object {
        /**
         * The new foods, or null when the answer has none. A normal meal answer (with `items`) is
         * read the same way as any logged meal; otherwise the first object with a name is taken,
         * since the AI sometimes wraps one food in a list or under a key such as "item".
         */
        fun parse(response: String, old: MealItem): List<MealItem>? {
            val cleaned = response.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val value = runCatching { JSONTokener(cleaned).nextValue() }.getOrNull()
            (value as? JSONObject)?.optJSONArray("items")?.let { arr ->
                MealItems.fromGemini(arr)?.let { return it.items.map { item -> item.copy(choices = emptyList()) } }
            }
            return parseOne(value, old)?.let { listOf(it) }
        }

        private fun parseOne(value: Any?, old: MealItem): MealItem? {
            val json = findFood(value) ?: return null
            val qty = json.optDouble("quantity", Double.NaN)
            if (qty.isNaN() || qty <= 0.0) json.put("quantity", old.qty)
            if (json.optString("unit", "").isBlank()) json.put("unit", old.unit)
            json.remove("alternatives")
            return MealItems.itemFromGemini(json)
        }

        /** The first object with a name: the answer itself, or one inside a list or wrapper. */
        private fun findFood(value: Any?): JSONObject? = when (value) {
            is JSONObject -> if (value.optString("name", "").isNotBlank()) {
                value
            } else {
                value.keys().asSequence().firstNotNullOfOrNull { findFood(value.opt(it)) }
            }
            is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { findFood(value.opt(it)) }
            else -> null
        }
    }
}
