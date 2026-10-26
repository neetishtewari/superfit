package com.superfit.app.domain

import org.json.JSONObject

/** Asks the AI for the numbers of one food the user says an item really was. */
class MealItemReplacer {

    /** [item] as [newName], at the same amount unless the user's words give one. */
    suspend fun replace(item: MealItem, newName: String, rest: List<MealItem>): MealItem {
        val others = rest.joinToString(", ") { it.label() }.ifEmpty { "nothing else" }
        val response = GeminiClient.generateContent(
            prompt = PersonalNutrition.promptContext() +
                "The user corrected one food in their meal. It is: \"${newName.trim()}\". " +
                "Amount so far: ${item.qty} ${item.unit}. The rest of the meal: $others.",
            systemInstructionText = SYSTEM_INSTRUCTION,
            isJson = true
        )
        return parse(response, item) ?: throw IllegalStateException("No usable item in the AI's answer")
    }

    companion object {
        val SYSTEM_INSTRUCTION =
            "You are an expert clinical nutrition AI. Estimate the nutrition of ONE food the user names.\n" +
                "If the user's words include an amount, use it; otherwise keep the amount you are given.\n" +
                "Return a raw JSON object with: \"name\": String (short English name), \"quantity\": Double, " +
                "\"unit\": String (one of piece, katori, bowl, cup, glass, slice, tbsp, tsp, g, ml, serving), " +
                "\"calories\", \"protein\", \"carbs\", \"fat\", \"fiber\": Double (for that whole quantity), " +
                "\"oilG\": Double (grams of oil, ghee or butter added in cooking or serving it, 0 if none), " +
                "\"sugarG\": Double (grams of added sugar, 0 if none)."

        /** The new item, or null when the answer has no name or amount. */
        fun parse(response: String, old: MealItem): MealItem? {
            val cleaned = response.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val json = runCatching { JSONObject(cleaned) }.getOrNull() ?: return null
            if (!json.has("quantity")) json.put("quantity", old.qty)
            if (!json.has("unit")) json.put("unit", old.unit)
            json.remove("alternatives")
            return MealItems.itemFromGemini(json)
        }
    }
}
