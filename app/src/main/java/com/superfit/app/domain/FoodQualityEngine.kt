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
    val actionColorType: String // "GREEN", "YELLOW", "ORANGE", "RED"
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
    val distinctDaysLogged: Int = 0
)

object FoodQualityEngine {

    fun analyzeDietQuality(
        nutritionHistory: List<NutritionEntryEntity> = emptyList(),
        userGoal: String = "LOSE_WEIGHT", // "LOSE_WEIGHT", "BUILD_MUSCLE", "MAINTAIN"
        zoneId: ZoneId = ZoneId.systemDefault()
    ): DietQualityMetrics {
        if (nutritionHistory.isEmpty()) {
            return DietQualityMetrics(
                overallScore = 85,
                scoreCategory = "BALANCED",
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

        val ratedItems = grouped.map { (foodName, entries) ->
            val totalCount = entries.size
            val avgCals = entries.map { it.calories }.average().toInt()
            val avgProtein = entries.map { it.proteinG }.average()
            val proteinCalsRatio = if (avgCals > 0) (avgProtein * 4.0) / avgCals else 0.0
            val isHighProtein = avgProtein >= 14.0 || proteinCalsRatio >= 0.25

            // Coach perspective: evaluate base score considering whole food status and protein utility
            val baseScore = evaluateCoachScore(foodName, isHighProtein, avgCals, userGoal)
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
            val (recommendation, colorType) = evaluateRecommendation(
                score = baseScore,
                weeklyFreq = weeklyFreq,
                foodDaysCount = foodDistinctDays,
                totalDistinctDays = totalDistinctDaysLogged,
                isHighProtein = isHighProtein,
                userGoal = userGoal
            )

            // Objective rationale explaining why the food is good, ok, or bad
            val rationale = generateScoreRationale(foodName, baseScore, isHighProtein, avgCals)

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
                actionColorType = colorType
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

        // Calculate overall score taking ALL food items into account proportionally
        val totalWeightedPoints = ratedItems.sumOf { it.cleanlinessScore.toDouble() * it.weeklyFrequency }
        val totalMealsCount = ratedItems.sumOf { it.weeklyFrequency }
        val weightedAverageCleanScore = if (totalMealsCount > 0) totalWeightedPoints / totalMealsCount else 8.0

        // Habitual penalty: only applied when user has established multi-day logging (>= 3 days) and has recurring habitual foods
        val habitPenalty = if (totalDistinctDaysLogged >= 3) flaggedHabitual.size * 4 else 0
        val finalOverallScore = max(35, min(99, ((weightedAverageCleanScore / 10.0) * 100).toInt() - habitPenalty))

        val scoreCategory = when {
            finalOverallScore >= 85 -> "CLEANEST & NUTRIENT DENSE"
            finalOverallScore >= 70 -> "BALANCED & NOURISHING"
            finalOverallScore >= 55 -> "MODERATELY PROCESSED"
            else -> "HIGH PROCESS / NEEDS ATTENTION"
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
            val swap = generateSwapForItem(item.foodText, item.avgCalories, usedAlternatives, userGoal)
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
            distinctDaysLogged = totalDistinctDaysLogged
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
                "BUILD_MUSCLE" -> Pair("Prime muscle builder — complete bioavailable protein (Keep regular)", "GREEN")
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

    private fun evaluateCoachScore(foodName: String, isHighProtein: Boolean, calories: Int, userGoal: String): Int {
        val name = foodName.lowercase()

        // 1. Whole Bioavailable Eggs & Egg Preparations (Score 10)
        // Eggs (fried, boiled, poached, scrambled, sunny-side up) are among the most complete protein foods in human nutrition!
        if (name.contains("egg") || name.contains("omelet") || name.contains("omelette")) {
            return 10
        }

        // 2. Ultra-processed, deep fried fast foods, sugary drinks, or commercial junk (Score 3)
        // Note: Specific to deep-fried fast food, NOT pan-cooked whole proteins!
        val isDeepFriedJunk = name.contains("french fries") || name.contains("fries") ||
            name.contains("tater tot") || name.contains("hash brown") ||
            name.contains("chip") || name.contains("crisp") || name.contains("dorito") || name.contains("cheeto") ||
            name.contains("deep fried") || name.contains("fried chicken") || name.contains("chicken nugget") ||
            name.contains("bhature") || name.contains("samosa") || name.contains("pakora") || name.contains("puri") ||
            name.contains("donut") || name.contains("doughnut") || name.contains("hot dog")

        val isSugaryJunk = name.contains("soda") || name.contains("coke") || name.contains("pepsi") ||
            name.contains("sprite") || name.contains("fanta") || name.contains("candy") ||
            name.contains("cookie") || name.contains("cake") || name.contains("ice cream") ||
            name.contains("frappuccino") || name.contains("ramen") || name.contains("maggi")

        if (isDeepFriedJunk || isSugaryJunk || name.contains("burger")) {
            return 3
        }

        // 3. High sodium, saturated fat, refined carbs, or alcoholic drinks (Score 5)
        val isProcessedTreat = name.contains("pizza") || name.contains("latte") || name.contains("chocolate") ||
            name.contains("bacon") || name.contains("sausage") || name.contains("salami") || name.contains("pepperoni") ||
            name.contains("sauce") || name.contains("syrup") || name.contains("nacho") || name.contains("bagel") ||
            name.contains("pancake") || name.contains("waffle") || name.contains("beer") ||
            name.contains("wine") || name.contains("cocktail") || name.contains("alcohol") ||
            name.contains("liquor") || name.contains("whiskey") || name.contains("vodka") ||
            name.contains("naan") || name.contains("paratha") || name.contains("mayo") ||
            name.contains("creamy") || name.contains("croissant") || name.contains("pastry") ||
            name.contains("muffin")

        if (isProcessedTreat) {
            return 5
        }

        // 4. Whole single-ingredient nutrient-dense foods (Score 10)
        val isWholeFood = name.contains("chicken") || name.contains("salmon") || name.contains("fish") ||
            name.contains("tuna") || name.contains("turkey") || name.contains("shrimp") || name.contains("prawn") ||
            name.contains("steak") || name.contains("beef") || name.contains("oat") || name.contains("avocado") ||
            name.contains("spinach") || name.contains("broccoli") || name.contains("kale") ||
            name.contains("berry") || name.contains("berries") || name.contains("apple") || name.contains("pear") ||
            name.contains("banana") || name.contains("orange") || name.contains("peach") || name.contains("plum") ||
            name.contains("grape") || name.contains("kiwi") || name.contains("mango") || name.contains("papaya") ||
            name.contains("guava") || name.contains("pomegranate") || name.contains("watermelon") || name.contains("melon") ||
            name.contains("pineapple") || name.contains("cherry") || name.contains("cherries") || name.contains("apricot") ||
            name.contains("fig") || name.contains("date") || name.contains("lemon") || name.contains("lime") ||
            name.contains("salad") || name.contains("almond") || name.contains("walnut") || name.contains("chia") ||
            name.contains("flax") || name.contains("quinoa") || name.contains("lentil") ||
            name.contains("bean") || name.contains("veggie") || name.contains("vegetable") ||
            name.contains("tomato") || name.contains("cucumber") || name.contains("carrot") ||
            name.contains("mushroom") || name.contains("zucchini") || name.contains("cauliflower") ||
            name.contains("cabbage") || name.contains("asparagus") || name.contains("water") ||
            name.contains("green tea")

        if (isWholeFood) {
            return 10
        }

        // 5. Minimally processed nutritious staples (Score 9)
        val isNutritiousStaple = name.contains("rice") || name.contains("tofu") || name.contains("paneer") ||
            name.contains("dal") || name.contains("roti") || name.contains("chapati") ||
            name.contains("phulka") || name.contains("dosa") || name.contains("idli") ||
            name.contains("khichdi") || name.contains("chana") || name.contains("rajma") ||
            name.contains("sambar") || name.contains("sprouts") || name.contains("sweet potato") ||
            name.contains("potato") || name.contains("milk") || name.contains("curd") ||
            name.contains("greek yogurt") || name.contains("cottage cheese") ||
            name.contains("whey") || name.contains("cashew") || name.contains("pistachio") ||
            name.contains("seed") || name.contains("peanut") || name.contains("fruit")

        if (isNutritiousStaple) {
            return 9
        }

        // 6. Moderately processed staples & whole grains (Score 7)
        val isBalancedStaple = name.contains("bread") || name.contains("toast") || name.contains("yogurt") ||
            name.contains("protein") || name.contains("wrap") || name.contains("cheese") ||
            name.contains("peanut butter") || name.contains("pasta") || name.contains("hummus") ||
            name.contains("noodle") || name.contains("soup") || name.contains("coffee") ||
            name.contains("tea")

        if (isBalancedStaple) {
            return 7
        }

        // Coach Bonus: If an unrecognized item has high protein (>= 14g or >= 25% protein cals), score it 8
        if (isHighProtein) {
            return 8
        }

        return 6
    }

    private fun generateScoreRationale(
        foodName: String,
        score: Int,
        isHighProtein: Boolean,
        calories: Int
    ): String {
        val name = foodName.lowercase()
        return when {
            // Specific Fruits
            name.contains("pear") -> "Whole fresh fruit • High soluble pectin fiber & vitamin C with gentle glycemic response"
            name.contains("apple") -> "Whole single-ingredient fruit • High soluble pectin fiber & polyphenols with low glycemic impact"
            name.contains("berry") || name.contains("berries") || name.contains("blueberry") ||
            name.contains("strawberry") || name.contains("raspberry") || name.contains("blackberry") ->
                "Whole antioxidant superfruit • Extremely high polyphenols & anthocyanins with low carbohydrate density"
            name.contains("banana") -> "Whole nutrient-dense fruit • Potassium-rich natural energy source ideal for workout fuel"
            name.contains("orange") || name.contains("citrus") || name.contains("lemon") || name.contains("lime") ||
            name.contains("grapefruit") || name.contains("clementine") || name.contains("tangerine") ->
                "Whole citrus fruit • High bioflavonoids, natural vitamin C & cellular hydration"
            name.contains("watermelon") || name.contains("melon") ->
                "Hydrating whole fruit • Over 90% structured water with rich lycopene and citrulline"
            name.contains("kiwi") || name.contains("mango") || name.contains("papaya") || name.contains("guava") ||
            name.contains("pomegranate") || name.contains("peach") || name.contains("plum") || name.contains("grape") ||
            name.contains("cherry") || name.contains("cherries") || name.contains("pineapple") || name.contains("fruit") ->
                "Whole single-ingredient fruit • Pure cellular hydration, fiber & vitamins with zero additives"

            // Eggs
            name.contains("egg") || name.contains("omelet") || name.contains("omelette") ->
                "Bioavailable complete protein • 100% complete amino acids, choline & leucine supporting lean muscle & satiety"

            // Poultry & Meats
            name.contains("chicken") || name.contains("turkey") ->
                "Lean whole protein • Maximum muscle protein synthesis support & high thermic burn with zero added sugars"
            name.contains("salmon") || name.contains("fish") || name.contains("tuna") || name.contains("trout") ||
            name.contains("cod") || name.contains("tilapia") || name.contains("shrimp") || name.contains("prawn") ||
            name.contains("seafood") ->
                "Heart-healthy whole protein • Packed with essential omega-3 fatty acids, D3 & high bioavailability protein"
            name.contains("steak") || name.contains("beef") || name.contains("sirloin") || name.contains("tenderloin") ->
                "Nutrient-dense red meat • Rich in bioavailable heme iron, creatine & zinc for strength and recovery"

            // Vegetables & Greens
            name.contains("spinach") || name.contains("kale") || name.contains("broccoli") || name.contains("salad") ||
            name.contains("cauliflower") || name.contains("cabbage") || name.contains("lettuce") || name.contains("greens") ||
            name.contains("cucumber") || name.contains("zucchini") || name.contains("asparagus") || name.contains("tomato") ||
            name.contains("carrot") || name.contains("mushroom") || name.contains("pepper") || name.contains("veggie") ||
            name.contains("vegetable") ->
                "Micronutrient powerhouse • High fiber, essential minerals & vitamins with virtually zero caloric load"

            // Grains & Legumes
            name.contains("oat") || name.contains("quinoa") ->
                "Complex whole grain • Beta-glucan soluble fiber delivers long-lasting sustained energy & heart health"
            name.contains("dal") || name.contains("lentil") || name.contains("chana") || name.contains("chickpea") ||
            name.contains("rajma") || name.contains("bean") || name.contains("edamame") || name.contains("sprouts") ->
                "High-fiber plant protein & complex carbs • Prebiotic fuel supporting gut microbiome & sustained satiety"
            name.contains("rice") || name.contains("sweet potato") || name.contains("potato") ->
                "Clean staple carbohydrate • Pure glycogen replenishment, hypoallergenic & easily digestible daily fuel"

            // Cultured Dairy & Proteins
            name.contains("greek yogurt") || name.contains("cottage cheese") || name.contains("curd") ||
            name.contains("paneer") || name.contains("tofu") || name.contains("tempeh") || name.contains("whey") ->
                "Nutrient-dense protein & calcium • Slow-digesting casein/whey supports muscle retention and gut health"

            // Moderately processed staples (Score 6-7)
            name.contains("bread") || name.contains("toast") || name.contains("wrap") || name.contains("roti") ||
            name.contains("chapati") || name.contains("phulka") ->
                "Processed grain staple • Fast-digesting carbohydrates with lower fiber than intact whole grains"
            name.contains("cheese") ->
                "Concentrated dairy food • Good calcium and protein, but high saturated fat and calorie density"
            name.contains("peanut butter") || name.contains("almond butter") ->
                "Calorie-dense whole food fats • Good heart-healthy fats, but calorie dense (requires portion control)"
            name.contains("pasta") || name.contains("noodle") ->
                "Processed semolina carbohydrate • Moderate glycemic energy; best paired with lean protein & veggies"

            // Moderate Risk Foods (Score 4-5)
            name.contains("pizza") ->
                "Refined flour crust & heavy saturated fats • High sodium density and fast-digesting calories with low fiber"
            name.contains("bacon") || name.contains("sausage") || name.contains("hot dog") || name.contains("salami") ||
            name.contains("pepperoni") ->
                "Processed cured meat • High sodium, nitrates & saturated fats with digestive and arterial stress"
            name.contains("latte") || name.contains("frappuccino") || name.contains("mocha") ->
                "Liquid sugar & dairy fat • Concentrated syrup calories trigger insulin spikes with zero chewing satiety"
            name.contains("beer") || name.contains("wine") || name.contains("cocktail") || name.contains("alcohol") ->
                "Empty liquid calories • Metabolized as a toxin, blunts fat oxidation and disrupts deep REM recovery sleep"
            name.contains("naan") || name.contains("paratha") ->
                "Refined flour & cooking fats • High calorie density with lower fiber, causing quicker hunger rebound"

            // High Risk / Ultra-Processed (Score 1-3)
            name.contains("fries") || name.contains("tater tot") || name.contains("hash brown") ->
                "Deep-fried refined potato • High acrylamides, oxidized vegetable oils & calorie density with zero protein"
            name.contains("chip") || name.contains("crisp") || name.contains("nacho") || name.contains("dorito") ->
                "Ultra-processed snack • High sodium, oxidized oils & refined starch engineered for overeating"
            name.contains("soda") || name.contains("coke") || name.contains("pepsi") || name.contains("sprite") ||
            name.contains("fanta") ->
                "Pure liquid sugar • Zero protein or micronutrients, triggers rapid glucose spikes and metabolic stress"
            name.contains("burger") || name.contains("nugget") ->
                "Ultra-processed fast food • Refined flour bun, saturated/trans fats & high sodium with low micronutrient density"
            name.contains("candy") || name.contains("chocolate") || name.contains("sweet") ->
                "Concentrated sucrose & fats • Immediate blood sugar surge followed by an energy crash with zero satiety"
            name.contains("cookie") || name.contains("biscuit") || name.contains("donut") || name.contains("cake") ||
            name.contains("pastry") ->
                "Refined flour & sugar bomb • Combines hydrogenated fats and simple sugars, promoting rapid fat storage"
            name.contains("samosa") || name.contains("pakora") || name.contains("puri") || name.contains("bhature") ->
                "Deep-fried refined starch • High thermal oil oxidation and trans fats with dense, fast-digesting calories"

            // Fallback tiers
            score >= 9 -> "Whole unprocessed food • Pure single-ingredient nutrient density with zero industrial additives"
            score >= 7 -> "Balanced everyday staple • Provides daily macronutrients; pair with whole fiber and lean protein"
            score >= 5 -> "Processed treat • Higher in sodium, refined carbohydrates or saturated fat; enjoy in moderation"
            else -> "Ultra-processed item • High calorie density, refined ingredients and minimal nutritional value"
        }
    }

    private fun evaluateTier(score: Int): FoodCleanlinessTier {
        return when {
            score >= 9 -> FoodCleanlinessTier.CLEANEST
            score >= 7 -> FoodCleanlinessTier.BALANCED
            score >= 5 -> FoodCleanlinessTier.MODERATE_RISK
            else -> FoodCleanlinessTier.HIGH_RISK
        }
    }

    private fun generateSwapForItem(
        foodName: String,
        calories: Int,
        usedAlternatives: Set<String> = emptySet(),
        userGoal: String = "LOSE_WEIGHT"
    ): FoodSwapSuggestion? {
        val name = foodName.lowercase()

        // Helper to pick first alternative not already used
        fun pickUnique(
            options: List<Triple<String, Int, String>>
        ): FoodSwapSuggestion? {
            val choice = options.firstOrNull { it.first !in usedAlternatives } ?: options.firstOrNull()
            return choice?.let { (alternative, savings, benefit) ->
                FoodSwapSuggestion(
                    originalFood = foodName,
                    suggestedAlternative = alternative,
                    calorieSavings = savings,
                    benefitHighlight = benefit
                )
            }
        }

        // Top Fitness Coach Rule: Like-for-Like Category Preservation!
        // Never swap a protein for potato wedges or carbs!
        return when {
            // Eggs (if ever flagged for excessive grease/oil) ➔ Keep protein, optimize preparation
            name.contains("egg") || name.contains("omelet") -> pickUnique(
                listOf(
                    Triple("Soft-Boiled / Poached Eggs with Sautéed Spinach", max(110, (calories * 0.35).toInt()), "Save 120 kcal Cooking Oil, 100% Bioavailable Choline & Leucine"),
                    Triple("2 Whole Eggs + 2 Egg Whites Scramble", max(120, (calories * 0.35).toInt()), "+10g Pure Protein & Lower Saturated Fat")
                )
            )

            // Deep-Fried Chicken / Tenders ➔ Leaner, High-Protein Poultry
            name.contains("fried chicken") || name.contains("chicken tender") || name.contains("crispy chicken") -> pickUnique(
                listOf(
                    Triple("Air-Fried Herb Chicken Breast Tenders", max(180, (calories * 0.45).toInt()), "+28g Lean Protein & Zero Rancid Trans Fats"),
                    Triple("Grilled Lemon Herb Chicken Breast", max(190, (calories * 0.5).toInt()), "Pure Lean Muscle Fuel & Controlled Sodium")
                )
            )

            // Burgers / Fast Food ➔ High-Protein Wrap / Lean Patty
            name.contains("burger") || name.contains("nugget") -> pickUnique(
                listOf(
                    Triple("Grilled Chicken Breast Wrap with Greek Yogurt", max(200, (calories * 0.45).toInt()), "+24g Lean Protein & 60% Less Saturated Fat"),
                    Triple("Air-Fried Turkey Patty on Whole-Wheat Bun", max(180, (calories * 0.4).toInt()), "+26g High Quality Protein & Less Sodium")
                )
            )

            // French Fries / Fried Potatoes ➔ Air-Fried Sweet Potatoes or Edamame
            (name.contains("fries") || name.contains("tater tot") || name.contains("hash brown") || name.contains("wedges")) -> pickUnique(
                listOf(
                    Triple("Air-Fried Sweet Potato Wedges or Edamame", max(160, (calories * 0.45).toInt()), "Complex Carbs, Lower GI & High Antioxidants"),
                    Triple("Crispy Air-Fried Zucchini Fries with Herb Dip", max(170, (calories * 0.5).toInt()), "80% Fewer Carbs & Zero Rancid Trans Fats")
                )
            )

            // Processed Meats ➔ Lean, Low-Sodium Poultry
            name.contains("bacon") || name.contains("sausage") || name.contains("hot dog") || name.contains("salami") || name.contains("pepperoni") -> pickUnique(
                listOf(
                    Triple("Grilled Turkey Bacon or Lean Chicken Sausage", max(130, (calories * 0.45).toInt()), "50% Less Sodium & Free of Added Nitrates"),
                    Triple("Herbed Sliced Chicken Breast Strips", max(140, (calories * 0.5).toInt()), "98% Lean Protein & Clean Fuel")
                )
            )

            // Chips / Crunchy Snacks ➔ High-Fiber / High-Protein Crunchy Snacks
            name.contains("chip") || (name.contains("crisp") && !name.contains("bacon")) || name.contains("nacho") || name.contains("dorito") -> pickUnique(
                listOf(
                    Triple("Air-Popped Makhana / Baked Kale Chips", max(130, (calories * 0.55).toInt()), "+6g Fiber & 60% Less Sodium"),
                    Triple("Spiced Roasted Chickpeas (Chana)", max(110, (calories * 0.45).toInt()), "+11g Protein & Sustained Energy"),
                    Triple("Roasted Edamame with Sea Salt", max(120, (calories * 0.5).toInt()), "+14g Plant Protein & Low Saturated Fat")
                )
            )

            // Soda / Sweet Drinks ➔ Hydrating, Zero-Sugar Drinks
            name.contains("soda") || name.contains("coke") || name.contains("pepsi") || name.contains("sprite") || name.contains("fanta") -> pickUnique(
                listOf(
                    Triple("Flavored Sparkling Water or Iced Green Tea", max(140, calories), "0g Added Sugar & Zero Glycemic Spike"),
                    Triple("Cold Sparkling Water with Fresh Lime & Mint", max(140, calories), "100% Hydrating with Zero Sugar Crash")
                )
            )

            // Pizza ➔ High-Protein Sourdough or Pita Pizza
            name.contains("pizza") -> pickUnique(
                listOf(
                    Triple("Thin Crust Whole-Wheat Sourdough with Cottage Cheese & Veggies", max(180, (calories * 0.4).toInt()), "+15g Protein & Less Processed Saturated Fat"),
                    Triple("Personal High-Protein Pita Pizza with Grilled Chicken & Basil", max(210, (calories * 0.45).toInt()), "+22g Protein & Controlled Sodium")
                )
            )

            // Indian Fried Snacks (Samosa, Pakora, Puri, Bhature)
            name.contains("samosa") || name.contains("pakora") || name.contains("puri") || name.contains("bhature") -> pickUnique(
                listOf(
                    Triple("Air-Fried Moong Dal Chilla with Mint Chutney", max(220, (calories * 0.55).toInt()), "+14g Protein, Zero Deep-Frying"),
                    Triple("Baked Green Pea & Potato Samosa", max(160, (calories * 0.45).toInt()), "70% Less Oil with Crisp Golden Texture")
                )
            )

            // Paratha / Naan ➔ Whole Grain Roti / Thepla
            name.contains("paratha") || name.contains("naan") -> pickUnique(
                listOf(
                    Triple("Sprouted Wheat Phulka or Missi Roti", max(130, (calories * 0.4).toInt()), "Higher Fiber & Free of Heavy Ghee/Butter"),
                    Triple("Multigrain Methi Thepla with Fresh Curd", max(110, (calories * 0.35).toInt()), "Slow Glycemic Release & Natural Herbs")
                )
            )


            // Candy / Chocolate ➔ High-Flavonoid Dark Chocolate
            name.contains("candy") || name.contains("chocolate") || name.contains("sweet") -> pickUnique(
                listOf(
                    Triple("85% Single-Origin Dark Chocolate with Almonds", max(90, (calories * 0.4).toInt()), "+5g Fiber, Magnesium & Flavonoids"),
                    Triple("Medjool Dates Stuffed with Natural Peanut Butter", max(70, (calories * 0.35).toInt()), "Rich in Potassium & Sustained Natural Energy")
                )
            )

            // Cookies / Biscuits ➔ High-Protein / Oat Bites
            name.contains("cookie") || name.contains("biscuit") || name.contains("oreo") -> pickUnique(
                listOf(
                    Triple("Oat & Banana Protein Bites", max(110, (calories * 0.45).toInt()), "Zero Refined Flour & Sustained Energy"),
                    Triple("Almond Flour Cinnamon Crisps", max(90, (calories * 0.4).toInt()), "Low Carb & Rich in Monounsaturated Fats")
                )
            )

            // Cakes / Pastries / Donuts ➔ High-Protein Bakes
            name.contains("cake") || name.contains("donut") || name.contains("doughnut") || name.contains("pastry") || name.contains("muffin") -> pickUnique(
                listOf(
                    Triple("Protein Mug Cake or High-Protein Chia Pudding", max(190, (calories * 0.5).toInt()), "+18g Protein & 80% Less Refined Sugar"),
                    Triple("Baked Cinnamon Apple Slices with Greek Yogurt", max(160, (calories * 0.45).toInt()), "High Natural Fiber & Real Micronutrients")
                )
            )

            // Ice Cream / Shakes ➔ Greek Yogurt / Whey Soft Serve
            name.contains("ice cream") || name.contains("sundae") || name.contains("milkshake") -> pickUnique(
                listOf(
                    Triple("Frozen Greek Yogurt with Berries or Whey Ice Cream", max(170, (calories * 0.45).toInt()), "+16g High Quality Protein & Gut Probiotics"),
                    Triple("Blended Frozen Banana Soft-Serve with Cacao", max(140, (calories * 0.4).toInt()), "100% Whole Fruit & Zero Added Sugars")
                )
            )

            // White Bread / Bagels ➔ Sprouted Whole Grain Sourdough
            name.contains("white bread") || name.contains("bagel") -> pickUnique(
                listOf(
                    Triple("100% Sprouted Whole Grain Bread or Sourdough", max(50, (calories * 0.25).toInt()), "Higher Fiber, Slower Blood Sugar Rise"),
                    Triple("Whole Grain Rye Toast with Avocado Mash", max(60, (calories * 0.3).toInt()), "Complex Carbs & Healthy Fats")
                )
            )

            // Pasta / Noodles / Ramen ➔ High-Protein / Edamame Pasta
            name.contains("pasta") || name.contains("noodle") || name.contains("ramen") || name.contains("maggi") -> pickUnique(
                listOf(
                    Triple("Chickpea / Edamame Pasta with Fresh Tomato Basil", max(120, (calories * 0.35).toInt()), "+14g Plant Protein & Double the Fiber"),
                    Triple("Millet Noodles in Savory Bone/Veggie Broth", max(140, (calories * 0.4).toInt()), "Zero Palm Oil & Higher Micronutrients")
                )
            )

            // Syrupy Coffee / Lattes ➔ Cold Brew with Whey / Almond Milk
            name.contains("latte") || name.contains("frappuccino") || name.contains("mocha") -> pickUnique(
                listOf(
                    Triple("Iced Cold Brew with Splash of Oat Milk & Whey", max(180, (calories * 0.6).toInt()), "Zero Syrups, High Mental Clarity & Clean Energy"),
                    Triple("Americano with Unsweetened Almond Milk", max(210, (calories * 0.7).toInt()), "Clean Caffeine Surge with Zero Sugar Spikes")
                )
            )

            // Alcohol ➔ Kombucha / Sparkling Club Soda
            name.contains("beer") || name.contains("wine") || name.contains("cocktail") || name.contains("alcohol") -> pickUnique(
                listOf(
                    Triple("Ginger Kombucha or Zesty Club Soda with Lime", max(150, calories), "Zero Alcohol Toxicity & Enhances Sleep Recovery"),
                    Triple("Sparkling Hop Water with Lemon Peel", max(150, calories), "Zero Sugar, Refreshing Taste & Zero Hangover")
                )
            )

            // Diverse Fallback Pool (guarantees unique, category-varied alternatives)
            else -> {
                val fallbackPool = listOf(
                    Triple("Grilled Herb Chicken or Paneer Quinoa Power Bowl", max(140, (calories * 0.4).toInt()), "+28g High Quality Protein & Complex Carbs"),
                    Triple("Air-Popped Spiced Makhana with Roasted Pumpkin Seeds", max(100, (calories * 0.35).toInt()), "+8g Fiber & Essential Micronutrients"),
                    Triple("Greek Yogurt Parfait with Mixed Berries & Crushed Walnuts", max(90, (calories * 0.35).toInt()), "+15g Protein & Rich Probiotics"),
                    Triple("Cold-Pressed Green Tea or Iced Mint Matcha", max(110, (calories * 0.45).toInt()), "Zero Added Sugars & High Antioxidants")
                )
                pickUnique(fallbackPool)
            }
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
