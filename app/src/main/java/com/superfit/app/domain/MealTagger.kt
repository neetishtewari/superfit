package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import org.json.JSONObject

/** Asks the AI to tag meals that were logged before quality tags existed, in batches. */
class MealTagger {

    suspend fun tag(entries: List<NutritionEntryEntity>): Map<Long, MealTags> {
        if (entries.isEmpty()) return emptyMap()
        val list = entries.mapIndexed { i, e -> "${i + 1}. ${e.foodText} (${e.calories.toInt()} kcal)" }.joinToString("\n")
        val response = GeminiClient.generateContent(
            prompt = "Meals:\n$list",
            systemInstructionText = SYSTEM_INSTRUCTION,
            isJson = true
        )
        return parse(response, entries)
    }

    companion object {
        val SYSTEM_INSTRUCTION =
            "You are a nutrition expert. For each numbered meal, describe what kind of food it is.\n" +
                "Return a raw JSON object {\"meals\": [ ... ]} with one object per meal, in the same order, each with:\n" +
                "- \"n\": Int (the meal's number)\n" +
                MealTags.PROMPT_FIELDS

        fun parse(response: String, entries: List<NutritionEntryEntity>): Map<Long, MealTags> {
            val cleaned = response.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val meals = runCatching { JSONObject(cleaned).getJSONArray("meals") }.getOrNull() ?: return emptyMap()
            val result = mutableMapOf<Long, MealTags>()
            for (i in 0 until meals.length()) {
                val obj = meals.optJSONObject(i) ?: continue
                val index = obj.optInt("n", i + 1) - 1
                val entry = entries.getOrNull(index) ?: continue
                val tags = runCatching { MealTags.fromJson(obj) }.getOrNull() ?: continue
                result[entry.id] = tags
            }
            return result
        }
    }
}
