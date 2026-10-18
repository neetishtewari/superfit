package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import java.util.Locale

object RepeatMealCache {

    private val numberWordMap = mapOf(
        "one" to 1.0,
        "two" to 2.0,
        // Speech-to-text often hears "two" as "tu" (common with Indian English accents)
        "tu" to 2.0,
        "three" to 3.0,
        "four" to 4.0,
        "five" to 5.0,
        "six" to 6.0,
        "seven" to 7.0,
        "eight" to 8.0,
        "nine" to 9.0,
        "ten" to 10.0,
        "half" to 0.5,
        "a" to 1.0,
        "an" to 1.0
    )

    /**
     * Normalizes a food text string for consistent comparison. Letters of every script are kept
     * (with their vowel signs), so "2 अंडे" doesn't shrink to just "2" and match some other meal.
     * Example: "2 Boiled Eggs!" -> "2 boiled eggs", "२ अंडे" -> "2 अंडे"
     */
    fun normalize(text: String): String {
        return text.trim()
            .lowercase(Locale.ROOT)
            .map { c -> if (c in '०'..'९') '0' + (c - '०') else c }
            .joinToString("")
            .replace(Regex("[^\\p{L}\\p{M}\\p{Nd}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Strips leading quantity tokens (like "2", "two", "1.5", "a", "an", "half")
     * and returns the quantity multiplier along with the core item name.
     * Example: "2 boiled eggs" -> Pair(2.0, "boiled eggs")
     * Example: "apple" -> Pair(1.0, "apple")
     */
    fun extractQuantityAndCore(normalized: String): Pair<Double, String> {
        val tokens = normalized.split(" ")
        if (tokens.isEmpty()) return Pair(1.0, "")

        val firstToken = tokens.first()
        val numericQty = firstToken.toDoubleOrNull()
        if (numericQty != null && numericQty > 0) {
            val core = tokens.drop(1).joinToString(" ").trim()
            return Pair(numericQty, core)
        }

        val wordQty = numberWordMap[firstToken]
        if (wordQty != null) {
            val core = tokens.drop(1).joinToString(" ").trim()
            return Pair(wordQty, core)
        }

        return Pair(1.0, normalized)
    }

    private val qualifierWords = setOf(
        "a", "an", "the", "large", "medium", "small", "fresh", "ripe", "hard", "soft",
        "cup", "cups", "glass", "glasses", "bowl", "bowls", "slice", "slices",
        "piece", "pieces", "serving", "servings", "plate", "plates", "of", "some",
        "handful", "scoop", "scoops", "cooked", "raw", "freshly"
    )

    /**
     * Extracts canonical stemmed keywords by filtering out numbers and descriptor qualifiers.
     * Example: "1 large boiled egg" -> ["boiled", "egg"]
     */
    fun extractKeywords(normalized: String): List<String> {
        return normalized.split(" ")
            .filter { it.isNotBlank() && it !in qualifierWords && it.toDoubleOrNull() == null && it !in numberWordMap }
            .map { stem(it) }
    }

    private fun stem(word: String): String {
        return when {
            word.endsWith("ies") && word.length > 4 -> word.removeSuffix("ies") + "y"
            word.endsWith("sses") -> word.removeSuffix("es")
            word.endsWith("xes") || word.endsWith("ches") || word.endsWith("shes") -> word.removeSuffix("es")
            word.endsWith("toes") -> word.removeSuffix("es")
            word.endsWith("s") && word.length > 2 && !word.endsWith("ss") -> word.removeSuffix("s")
            else -> word
        }
    }

    /**
     * Finds the same meal in the user's own history, so a repeat skips the AI. Everything else,
     * however common, goes to the AI; the app keeps no built-in list of foods.
     */
    fun findRepeatMeal(
        input: String,
        history: List<NutritionEntryEntity>
    ): ParsedNutritionResult? {
        if (input.isBlank()) return null

        val normInput = normalize(input)
        if (normInput.isBlank()) return null

        val (inputQty, _) = extractQuantityAndCore(normInput)
        val inputKeywords = extractKeywords(normInput)

        // 1. Direct exact match against user's previously logged meals
        val exactMatch = history.firstOrNull { normalize(it.foodText) == normInput }
        if (exactMatch != null && exactMatch.calories > 0) {
            return ParsedNutritionResult(
                foodText = exactMatch.foodText,
                calories = exactMatch.calories,
                proteinG = exactMatch.proteinG,
                carbsG = exactMatch.carbsG,
                fatG = exactMatch.fatG,
                tags = MealTags.decode(exactMatch.qualityTags),
                items = MealItems.decode(exactMatch.items)
            )
        }

        // 2. Semantic keyword matching against user's history with quantity scaling
        if (inputKeywords.isNotEmpty()) {
            for (pastEntry in history) {
                if (pastEntry.calories <= 0) continue

                val normPast = normalize(pastEntry.foodText)
                val (pastQty, _) = extractQuantityAndCore(normPast)
                val pastKeywords = extractKeywords(normPast)

                if (areKeywordListsEquivalent(inputKeywords, pastKeywords)) {
                    val scaleFactor = (inputQty / pastQty).coerceIn(0.1, 20.0)
                    return ParsedNutritionResult(
                        foodText = input.trim(),
                        calories = roundOneDecimal(pastEntry.calories * scaleFactor),
                        proteinG = roundOneDecimal(pastEntry.proteinG * scaleFactor),
                        carbsG = roundOneDecimal(pastEntry.carbsG * scaleFactor),
                        fatG = roundOneDecimal(pastEntry.fatG * scaleFactor),
                        tags = MealTags.decode(pastEntry.qualityTags)?.scaled(scaleFactor),
                        items = MealItems.decode(pastEntry.items)?.scaled(scaleFactor)
                    )
                }
            }
        }

        return null
    }

    // Both meals must name the same foods. A looser "one contains the other" match let
    // "tandoori roti" borrow plain roti's numbers, or "2 rotis" copy a whole past meal of
    // "roti with dal and paneer". Anything that isn't the same food goes to Gemini instead.
    private fun areKeywordListsEquivalent(a: List<String>, b: List<String>): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        return a.toSet() == b.toSet()
    }

    private fun roundOneDecimal(value: Double): Double {
        return Math.round(value * 10.0) / 10.0
    }
}
