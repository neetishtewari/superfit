package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

enum class FoodCleanlinessTier {
    CLEANEST,
    BALANCED,
    MODERATE_RISK,
    HIGH_RISK
}

data class FoodItemRating(
    val foodText: String,
    val cleanlinessScore: Int, // 1 to 10
    val tier: FoodCleanlinessTier,
    val weeklyFrequency: Int, // Total times logged in rolling period
    val daysLoggedCount: Int = 1, // Number of distinct days this food was logged
    val avgCalories: Int,
    val habitRiskScore: Double,
    val isHabitualRisk: Boolean,
    val scoreRationale: String = "", // Detailed rationale explaining why good, ok, or bad
    val actionRecommendation: String = "", // "Keep amount same", "Reduce frequency", etc.
    val actionColorType: String, // "GREEN", "YELLOW", "ORANGE", "RED"
    /** False until the AI has tagged this food; its score is then only a placeholder. */
    val rated: Boolean = true
)

data class FoodSwapSuggestion(
    val originalFood: String,
    val suggestedAlternative: String,
    val calorieSavings: Int,
    val benefitHighlight: String
)

data class DietQualityMetrics(
    val overallScore: Int, // 1 to 100
    val scoreCategory: String,
    val topCleanFoods: List<String>,
    val flaggedHabitualFoods: List<FoodItemRating>,
    val swapSuggestions: List<FoodSwapSuggestion>,
    val allRatedItems: List<FoodItemRating> = emptyList(),
    val distinctDaysLogged: Int = 0,
    /** False until meals are logged on enough days (see DietScoreEngine.MIN_DAYS) to give a fair score. */
    val hasEnoughData: Boolean = false,
    /** Where the points came from and where they were lost. */
    val breakdown: List<DietScorePart> = emptyList(),
    /** Change against the 7 days before, when both weeks have enough logs. */
    val changeFromLastWeek: Int? = null,
    /** The one change that would lift the score most. */
    val focusTip: String? = null,
    val treatMealsThisWeek: Int = 0,
    val estimatedShare: Double = 0.0
)

object FoodQualityEngine {

    fun analyzeDietQuality(
        nutritionHistory: List<NutritionEntryEntity> = emptyList(),
        userGoal: String = "LOSE_WEIGHT", // "LOSE_WEIGHT", "GAIN_MUSCLE", "MAINTAIN"
        zoneId: ZoneId = ZoneId.systemDefault(),
        proteinTargetG: Double = 120.0,
        calorieTarget: Double = 2000.0,
        today: LocalDate = LocalDate.now(zoneId)
    ): DietQualityMetrics {
        if (nutritionHistory.isEmpty()) {
            return DietQualityMetrics(
                overallScore = 0,
                scoreCategory = "NOT ENOUGH DATA",
                topCleanFoods = emptyList(),
                flaggedHabitualFoods = emptyList(),
                swapSuggestions = emptyList(),
                allRatedItems = emptyList(),
                distinctDaysLogged = 0
            )
        }

        // Filter entries for rolling 7 days using timestamp
        val cutoffTimestamp = System.currentTimeMillis() - (7 * 24 * 3600 * 1000L)
        val rolling7DaysEntries = nutritionHistory.filter { entry ->
            entry.timestamp >= cutoffTimestamp
        }

        val entriesToAnalyze = if (rolling7DaysEntries.isNotEmpty()) rolling7DaysEntries else nutritionHistory

        // Calculate total distinct calendar days logged across the analysis window
        val distinctDays = entriesToAnalyze.map { entry ->
            runCatching {
                Instant.ofEpochMilli(entry.timestamp).atZone(zoneId).toLocalDate()
            }.getOrElse { LocalDate.now() }
        }.distinct()
        val totalDistinctDaysLogged = max(1, distinctDays.size)

        // Group entries by sanitized food name
        val grouped = entriesToAnalyze.groupBy { sanitizeFoodName(it.foodText) }
        // The AI's tags for each food, newest first, so its latest reason and swap are used
        val tagsByFood = grouped.mapValues { (_, entries) ->
            entries.sortedByDescending { it.timestamp }.mapNotNull { MealTags.decode(it.qualityTags) }
        }

        val ratedItems = grouped.map { (foodName, entries) ->
            val totalCount = entries.size
            val avgCals = entries.map { it.calories }.average().toInt()
            val avgProtein = entries.map { it.proteinG }.average()
            val proteinCalsRatio = if (avgCals > 0) (avgProtein * 4.0) / avgCals else 0.0
            val isHighProtein = avgProtein >= 14.0 || proteinCalsRatio >= 0.25

            // Coach perspective: evaluate base score considering whole food status and protein utility
            val tags = tagsByFood[foodName]?.firstOrNull()
            // Meals the AI hasn't rated yet sit in the middle: never praised, flagged or swapped.
            val baseScore = tags?.let { scoreFromTags(it, isHighProtein) } ?: UNRATED_SCORE
            val tier = evaluateTier(baseScore)

            // Number of distinct calendar days this specific food was logged
            val foodDistinctDays = entries.map { entry ->
                runCatching {
                    Instant.ofEpochMilli(entry.timestamp).atZone(zoneId).toLocalDate()
                }.getOrElse { LocalDate.now() }
            }.distinct().size

            // Weekly frequency within the window
            val weeklyFreq = totalCount
            val portionFactor = max(0.5, avgCals / 250.0)

            // Habit Risk: A food is a genuine habitual risk ONLY if consumed across multiple distinct days and is lower quality (score <= 5)
            val isHabitual = baseScore <= 5 && foodDistinctDays >= 2
            val habitDoseMultiplier = if (foodDistinctDays >= 2) (1.0 + (foodDistinctDays - 1) * 0.5) else 1.0
            val rawRisk = (11 - baseScore) * habitDoseMultiplier * portionFactor

            // Determine frequency and quality recommendation with fitness coach insight
            val (recommendation, colorType) = if (tags == null) {
                Pair("Not rated yet — the AI rates new meals within a day", "YELLOW")
            } else {
                evaluateRecommendation(
                    score = baseScore,
                    weeklyFreq = weeklyFreq,
                    foodDaysCount = foodDistinctDays,
                    totalDistinctDays = totalDistinctDaysLogged,
                    isHighProtein = isHighProtein,
                    userGoal = userGoal
                )
            }

            // Objective rationale explaining why the food is good, ok, or bad
            val rationale = tagsByFood[foodName]?.firstNotNullOfOrNull { it.why }
                ?: tags?.let { describe(it) }
                ?: "Waiting for the AI to rate this meal"

            FoodItemRating(
                foodText = foodName.capitalizeWords(),
                cleanlinessScore = baseScore,
                tier = tier,
                weeklyFrequency = weeklyFreq,
                daysLoggedCount = foodDistinctDays,
                avgCalories = avgCals,
                habitRiskScore = rawRisk,
                isHabitualRisk = isHabitual,
                scoreRationale = rationale,
                actionRecommendation = recommendation,
                actionColorType = colorType,
                rated = tags != null
            )
        }.sortedWith(
            compareByDescending<FoodItemRating> { it.weeklyFrequency * 5 + it.daysLoggedCount * 10 }
                .thenByDescending { it.cleanlinessScore }
        )

        // Top clean foods logged
        val topClean = ratedItems
            .filter { it.cleanlinessScore >= 8 }
            .sortedByDescending { it.cleanlinessScore }
            .map { it.foodText }
            .distinct()
            .take(3)

        // Flagged habitual high-risk foods (only foods recurring across multiple days)
        val flaggedHabitual = ratedItems
            .filter { it.isHabitualRisk }
            .sortedByDescending { it.habitRiskScore }
            .take(3)

        // The overall score judges the week's eating pattern, not an average of single foods.
        val thisWeek = DietScoreEngine.score(nutritionHistory, today, proteinTargetG, calorieTarget, zoneId)
        val lastWeek = DietScoreEngine.score(nutritionHistory, today, proteinTargetG, calorieTarget, zoneId, weeksAgo = 1)
        val finalOverallScore = thisWeek?.score ?: 0

        val scoreCategory = when {
            thisWeek == null -> "NOT ENOUGH DATA"
            finalOverallScore >= 85 -> "EXCELLENT"
            finalOverallScore >= 70 -> "GOOD"
            finalOverallScore >= 55 -> "FAIR"
            else -> "NEEDS ATTENTION"
        }

        // Generate Smart Food Swaps for relevant lower-quality foods:
        // Priority 1: Recurring habitual risk foods
        // Priority 2: Genuine moderate/high risk items (cleanlinessScore <= 5)
        val candidateItemsForSwap = (flaggedHabitual + ratedItems.filter { it.cleanlinessScore <= 5 })
            .distinctBy { it.foodText.lowercase() }
            .sortedWith(
                compareByDescending<FoodItemRating> { it.isHabitualRisk }
                    .thenBy { it.cleanlinessScore }
                    .thenByDescending { it.avgCalories }
            )
            .take(4)

        val usedAlternatives = mutableSetOf<String>()
        val swapSuggestions = mutableListOf<FoodSwapSuggestion>()

        for (item in candidateItemsForSwap) {
            val swap = tagsByFood[sanitizeFoodName(item.foodText)]?.firstNotNullOfOrNull { it.swap }
                ?.takeIf { it.alternative.capitalizeWords() !in usedAlternatives }
                ?.let { FoodSwapSuggestion(item.foodText, it.alternative.capitalizeWords(), it.kcalSaved, it.benefit) }
            if (swap != null) {
                usedAlternatives.add(swap.suggestedAlternative)
                swapSuggestions.add(swap)
            }
        }

        return DietQualityMetrics(
            overallScore = finalOverallScore,
            scoreCategory = scoreCategory,
            topCleanFoods = topClean,
            flaggedHabitualFoods = flaggedHabitual,
            swapSuggestions = swapSuggestions,
            allRatedItems = ratedItems,
            distinctDaysLogged = totalDistinctDaysLogged,
            hasEnoughData = thisWeek != null,
            breakdown = thisWeek?.parts.orEmpty(),
            changeFromLastWeek = if (thisWeek != null && lastWeek != null) thisWeek.score - lastWeek.score else null,
            focusTip = thisWeek?.let { DietScoreEngine.biggestGain(it) },
            treatMealsThisWeek = thisWeek?.treatMeals ?: 0,
            estimatedShare = thisWeek?.estimatedShare ?: 0.0
        )
    }

    private fun evaluateRecommendation(
        score: Int,
        weeklyFreq: Int,
        foodDaysCount: Int,
        totalDistinctDays: Int,
        isHighProtein: Boolean,
        userGoal: String
    ): Pair<String, String> {
        // High protein foods receive goal-tailored coach praise
        if (isHighProtein && score >= 8) {
            return when (userGoal) {
                "GAIN_MUSCLE" -> Pair("Prime muscle builder — complete bioavailable protein (Keep regular)", "GREEN")
                "LOSE_WEIGHT" -> Pair("High satiety & lean mass preservation — essential staple", "GREEN")
                else -> Pair("Clean whole protein — premier foundation for daily recovery", "GREEN")
            }
        }

        return when {
            // Clean whole foods
            score >= 8 -> {
                if (foodDaysCount >= 3 || (totalDistinctDays <= 2 && weeklyFreq >= 2)) {
                    Pair("Clean staple — nutrient dense fuel", "GREEN")
                } else {
                    Pair("Clean choice — great wholesome food", "GREEN")
                }
            }
            // Balanced foods
            score >= 6 -> {
                if (isHighProtein) {
                    Pair("High protein source — good daily energy", "YELLOW")
                } else if (foodDaysCount >= 4) {
                    Pair("Balanced staple — healthy in moderation", "YELLOW")
                } else {
                    Pair("Balanced food — solid energy source", "YELLOW")
                }
            }
            // Moderate processing (scores 4-5)
            score >= 4 -> {
                if (foodDaysCount >= 2) {
                    Pair("Recurring item (logged across $foodDaysCount days) — lighter swap suggested", "ORANGE")
                } else {
                    Pair("Occasional treat — fine in moderation, swap available", "ORANGE")
                }
            }
            // Ultra-processed / high risk (scores 1-3)
            else -> {
                if (foodDaysCount >= 2) {
                    Pair("Habitual risk (logged across $foodDaysCount days) — reduce frequency", "RED")
                } else {
                    Pair("Single-day indulgence — healthier swap recommended", "ORANGE")
                }
            }
        }
    }

    /** Score shown for a meal the AI hasn't tagged yet. */
    const val UNRATED_SCORE = 6

    /** A plain reason from the AI's tags, for meals tagged before the AI wrote its own reason. */
    private fun describe(tags: MealTags): String = buildList {
        add(
            when (tags.processing) {
                1 -> "Whole food or home-style cooking"
                2 -> "Somewhat processed"
                else -> "Ultra-processed or fast food"
            }
        )
        if (tags.fried) add("fried")
        if (tags.hiddenOil) add("oily")
        if (tags.addedSugar) add("added sugar")
        if (tags.vegFruitServings >= 1.0) add("good serving of vegetables or fruit")
    }.joinToString(" • ")

    /** Score for a food from the AI's tags: whole and home-cooked food high, ultra-processed and treats low. */
    fun scoreFromTags(tags: MealTags, isHighProtein: Boolean): Int {
        var score = when (tags.processing) {
            1 -> if (tags.vegFruitServings >= 1.0) 10 else 9
            2 -> 6
            else -> 3
        }
        if (isHighProtein && tags.processing <= 2) score += 1
        if (tags.fried) score -= 1
        if (tags.addedSugar) score -= 1
        if (tags.hiddenOil) score -= 1
        if (tags.treat) score = minOf(score, 4)
        return score.coerceIn(1, 10)
    }

    private fun evaluateTier(score: Int): FoodCleanlinessTier {
        return when {
            score >= 9 -> FoodCleanlinessTier.CLEANEST
            score >= 7 -> FoodCleanlinessTier.BALANCED
            score >= 5 -> FoodCleanlinessTier.MODERATE_RISK
            else -> FoodCleanlinessTier.HIGH_RISK
        }
    }

    private fun sanitizeFoodName(foodText: String): String {
        return foodText.trim().lowercase().take(30)
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}
