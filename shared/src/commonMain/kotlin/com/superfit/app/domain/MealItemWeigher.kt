package com.superfit.app.domain

import com.superfit.app.json.JSONArray
import com.superfit.app.json.JSONObject
import com.superfit.app.json.JSONTokener

/**
 * Asks the AI how much the foods of a meal weigh, for items it never weighed: meals logged before
 * grams came in, repeats of those meals, and answers that left the weight out. Then the g or ml
 * switch works on every item, not only on meals the AI happened to weigh.
 */
class MealItemWeigher {

    /** [items] with a weight on each one that had none. Throws [UnreadableAnswer] if none came back. */
    suspend fun weigh(items: List<MealItem>): List<MealItem> {
        val todo = items.withIndex().filter { it.value.needsWeight }
        if (todo.isEmpty()) return items
        val list = todo.joinToString("\n") { (i, item) -> "${i + 1}. ${item.label()}" }
        val response = Ai.generate(
            prompt = PersonalNutrition.promptContext() + "Foods:\n$list",
            systemInstructionText = SYSTEM_INSTRUCTION,
            isJson = true
        )
        val weighed = withWeights(response, items)
        if (weighed == items) {
            AppLog.w("MealItemWeigher", "No weights in the AI's answer: ${response.take(500)}")
            throw UnreadableAnswer()
        }
        return weighed
    }

    /** The AI answered, but with no weight the app could read. */
    class UnreadableAnswer : IllegalStateException("No weights in the AI's answer")

    companion object {
        val SYSTEM_INSTRUCTION =
            "You are a nutrition AI. For each numbered food, estimate how much that whole amount weighs, as it is usually " +
                "served (e.g. 1 home-made aloo paratha about 120 g, 1 katori dal about 150 g, 1 cup chai about 150 ml). " +
                "Return a raw JSON object: {\"weights\": [{\"n\": Int (the food's number), \"grams\": Double (grams for food, " +
                "ml for drinks), \"liquid\": Boolean (true for drinks and other pourable liquids such as milk, chai, juice, " +
                "soup, lassi; false for solid food)}]}."

        /** [items] with the weights in [response] added; items already weighed are left alone. */
        fun withWeights(response: String, items: List<MealItem>): List<MealItem> {
            val cleaned = response.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val value = runCatching { JSONTokener(cleaned).nextValue() }.getOrNull()
            val arr = when (value) {
                is JSONObject -> value.optJSONArray("weights") ?: value.optJSONArray("items")
                is JSONArray -> value
                else -> null
            } ?: return items
            val out = items.toMutableList()
            for (k in 0 until arr.length()) {
                val o = arr.optJSONObject(k) ?: continue
                val index = o.optInt("n", k + 1) - 1
                val item = out.getOrNull(index) ?: continue
                val grams = o.optDouble("grams", Double.NaN)
                if (!item.needsWeight || grams.isNaN() || grams <= 0.0 || item.qty <= 0.0) continue
                out[index] = item.withWeight(grams / item.qty, if (o.optBoolean("liquid", false)) "ml" else "g")
            }
            return out
        }
    }
}
