package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import java.util.Locale

object RepeatMealCache {

    private val numberWordMap = mapOf(
        "one" to 1.0,
        "two" to 2.0,
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
     * Normalizes a food text string for consistent comparison.
     * Example: "2 Boiled Eggs!" -> "2 boiled eggs"
     */
    fun normalize(text: String): String {
        return text.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\s]"), " ")
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

    private data class BaselineMacro(val cal: Double, val p: Double, val c: Double, val f: Double)

    private val commonStaples = mapOf(
        "boiled egg" to BaselineMacro(78.0, 6.3, 0.6, 5.3),
        "egg" to BaselineMacro(78.0, 6.3, 0.6, 5.3),
        "fried egg" to BaselineMacro(90.0, 6.3, 0.6, 7.0),
        "scrambled egg" to BaselineMacro(95.0, 6.5, 1.0, 7.0),
        "apple" to BaselineMacro(95.0, 0.5, 25.0, 0.3),
        "pear" to BaselineMacro(100.0, 0.6, 27.0, 0.2),
        "banana" to BaselineMacro(105.0, 1.3, 27.0, 0.3),
        "milk tea with stevia" to BaselineMacro(45.0, 2.0, 4.0, 2.2),
        "milk tea" to BaselineMacro(50.0, 2.0, 6.0, 2.0),
        "chai" to BaselineMacro(50.0, 2.0, 6.0, 2.0),
        "tea with stevia" to BaselineMacro(30.0, 1.0, 2.0, 1.0),
        "black coffee" to BaselineMacro(5.0, 0.3, 0.0, 0.0),
        "coffee" to BaselineMacro(5.0, 0.3, 0.0, 0.0),
        "coffee with milk" to BaselineMacro(45.0, 2.5, 4.0, 2.0),
        "chicken breast" to BaselineMacro(165.0, 31.0, 0.0, 3.6),
        "grilled chicken" to BaselineMacro(180.0, 32.0, 0.0, 4.5),
        "rice" to BaselineMacro(200.0, 4.0, 45.0, 0.5),
        "white rice" to BaselineMacro(200.0, 4.0, 45.0, 0.5),
        "brown rice" to BaselineMacro(215.0, 5.0, 45.0, 1.8),
        "oats" to BaselineMacro(150.0, 5.0, 27.0, 2.5),
        "oatmeal" to BaselineMacro(150.0, 5.0, 27.0, 2.5),
        "roti" to BaselineMacro(100.0, 3.0, 20.0, 1.5),
        "chapati" to BaselineMacro(100.0, 3.0, 20.0, 1.5),
        "protein shake" to BaselineMacro(130.0, 25.0, 3.0, 1.5),
        "whey protein" to BaselineMacro(130.0, 25.0, 3.0, 1.5),
        "greek yogurt" to BaselineMacro(120.0, 15.0, 6.0, 2.0),
        "paneer" to BaselineMacro(265.0, 18.0, 3.0, 20.0),
        "peanut butter toast" to BaselineMacro(210.0, 8.0, 24.0, 10.0),
        "toast" to BaselineMacro(80.0, 3.0, 15.0, 1.0),
        "bread" to BaselineMacro(80.0, 3.0, 15.0, 1.0)
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
     * Attempts to find a matching previously logged meal from history or common staples.
     * Guaranteed 0ms response for repeat meals and staple foods.
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
                fatG = exactMatch.fatG
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
                        fatG = roundOneDecimal(pastEntry.fatG * scaleFactor)
                    )
                }
            }
        }

        // 3. Instant common fitness staples lookup (0ms offline fallback)
        if (inputKeywords.isNotEmpty()) {
            for ((stapleName, macro) in commonStaples) {
                val stapleKeywords = extractKeywords(stapleName)
                if (areKeywordListsEquivalent(inputKeywords, stapleKeywords)) {
                    return ParsedNutritionResult(
                        foodText = input.trim(),
                        calories = roundOneDecimal(macro.cal * inputQty),
                        proteinG = roundOneDecimal(macro.p * inputQty),
                        carbsG = roundOneDecimal(macro.c * inputQty),
                        fatG = roundOneDecimal(macro.f * inputQty)
                    )
                }
            }
        }

        return null
    }

    private fun areKeywordListsEquivalent(a: List<String>, b: List<String>): Boolean {
        if (a == b) return true
        if (a.isEmpty() || b.isEmpty()) return false
        // Match if all keywords of shorter list are present in the other
        if (a.size == b.size) {
            return a.toSet() == b.toSet()
        }
        val (smaller, larger) = if (a.size < b.size) Pair(a, b) else Pair(b, a)
        return larger.containsAll(smaller)
    }

    private fun roundOneDecimal(value: Double): Double {
        return Math.round(value * 10.0) / 10.0
    }
}
