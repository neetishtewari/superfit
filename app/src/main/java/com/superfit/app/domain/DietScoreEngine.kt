package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** One part of the Diet Quality score, e.g. "Vegetables & fruit: 18 / 25". */
data class DietScorePart(
    val key: String,
    val label: String,
    val points: Int,
    val maxPoints: Int,
    val detail: String,
    val tip: String
)

data class DietScore(
    val score: Int,
    val parts: List<DietScorePart>,
    val daysLogged: Int,
    val treatMeals: Int,
    /** Share of meals judged from their name because the AI hadn't tagged them. */
    val estimatedShare: Double
)

/** How many of the 100 points each part is worth, by goal. */
data class DietScoreWeights(val food: Int, val balance: Int, val protein: Int, val veg: Int, val treats: Int, val fibre: Int) {
    companion object {
        fun forGoal(goal: String): DietScoreWeights = when (goal) {
            "GAIN_MUSCLE" -> DietScoreWeights(food = 25, balance = 15, protein = 30, veg = 15, treats = 10, fibre = 5)
            "MAINTAIN" -> DietScoreWeights(food = 30, balance = 20, protein = 15, veg = 20, treats = 10, fibre = 5)
            else -> DietScoreWeights(food = 25, balance = 25, protein = 20, veg = 15, treats = 10, fibre = 5)
        }
    }
}

/**
 * Diet Quality as a nutritionist would judge a week of eating, weighted by the person's goal:
 * the AI's rating of each meal (which weighs the amount, the goal and their own nutritionist's
 * advice), whether each day was balanced (carbs, protein and fat in a sensible mix), protein,
 * a colourful plate, real treats kept to a couple a week, and fibre.
 *
 * Days are judged as a whole, never meal by meal, because people log whenever suits them, often
 * everything at once in the evening. A forgotten meal doesn't cost points: balance, vegetables and
 * fibre use the mix of what was logged, and protein totals use only days that look fully logged.
 */
object DietScoreEngine {

    const val MIN_DAYS = 3

    /** Days lighter than this share of the calorie target are too thin to judge their balance. */
    private const val MIN_DAY_SHARE = 0.4

    /** Fruit servings a day that count fully; more add sugar, not points. */
    const val FRUIT_CAP = 2.0

    /**
     * Scores the 7 days ending [weeksAgo] weeks before [today] (0 = the last 7 days including
     * today). Returns null when fewer than [MIN_DAYS] of those days have any meals logged.
     */
    fun score(
        entries: List<NutritionEntryEntity>,
        today: LocalDate,
        proteinTargetG: Double,
        calorieTarget: Double,
        zone: ZoneId,
        weeksAgo: Int = 0,
        goal: String = PersonalNutrition.goal
    ): DietScore? {
        val dates = (0..6).map { today.minusDays((weeksAgo * 7 + it).toLong()) }.toSet()
        val byDate = entries.groupBy { dateOf(it.timestamp, zone) }
        val inWindow = entries.filter { dateOf(it.timestamp, zone) in dates }
        val daysLogged = inWindow.map { dateOf(it.timestamp, zone) }.distinct().size
        if (daysLogged < MIN_DAYS) return null

        val tagged = inWindow.map { it to MealTags.of(it) }
        // Food quality is judged only from meals the AI has tagged; untagged ones are tagged within a day.
        val aiTagged = tagged.filter { !it.second.estimated }
        if (aiTagged.isEmpty()) return null

        val w = DietScoreWeights.forGoal(goal)
        // Days with enough logged to judge their mix, each with its AI-tagged meals.
        val judgedDays = aiTagged.groupBy { dateOf(it.first.timestamp, zone) }.values
            .filter { day -> day.sumOf { it.first.calories } >= calorieTarget * MIN_DAY_SHARE }

        val parts = listOf(
            foodPart(aiTagged, w.food),
            balancePart(judgedDays.map { day -> day.map { it.first } }, goal, w.balance),
            proteinPart(byDate, dates - today, inWindow, proteinTargetG, calorieTarget, today, zone, w.protein),
            vegPart(judgedDays, w.veg),
            treatPart(aiTagged, w.treats),
            fibrePart(aiTagged, w.fibre)
        )
        return DietScore(
            score = parts.sumOf { it.points }.coerceIn(0, 100),
            parts = parts,
            daysLogged = daysLogged,
            treatMeals = treatOccasions(aiTagged),
            estimatedShare = tagged.count { it.second.estimated }.toDouble() / tagged.size
        )
    }

    /** A meal's rating out of 10: the AI's own, a food the user marked "Fine for me" at least 7. */
    fun mealRating(entry: NutritionEntryEntity, tags: MealTags): Int {
        val r = tags.rating ?: FoodQualityEngine.scoreFromTags(tags, isHighProtein = entry.proteinG >= 14.0)
        return if (PersonalNutrition.isApproved(entry, tags)) maxOf(r, 7) else r
    }

    private fun foodPart(tagged: List<Pair<NutritionEntryEntity, MealTags>>, max: Int): DietScorePart {
        val kcal = tagged.sumOf { it.first.calories }
        val avg = if (kcal > 0) {
            tagged.sumOf { (e, t) -> mealRating(e, t) * e.calories } / kcal
        } else {
            tagged.map { (e, t) -> mealRating(e, t) }.average()
        }
        val lowest = tagged.filter { (e, t) -> mealRating(e, t) <= 5 }
            .groupBy { (e, t) -> t.foodType.ifBlank { e.foodText.lowercase().take(30) } }
            .maxByOrNull { (_, list) -> list.sumOf { it.first.calories } }?.key
        return DietScorePart(
            key = "food",
            label = "Food choices",
            points = scaled((avg - 4.0) / 4.5, 1.0, max),
            maxPoints = max,
            detail = "Your meals averaged ${fmt1(avg)} / 10 as a nutritionist would rate them" +
                (lowest?.let { ", lowest: $it" } ?: ""),
            tip = lowest?.let { "Have $it less often, or in a smaller amount." } ?: "Keep choosing meals like this week's."
        )
    }

    /** Healthy share of a day's calories from carbs, protein and fat, by goal. */
    private fun carbRange(goal: String) = if (goal == "LOSE_WEIGHT") 0.35..0.55 else 0.40..0.60
    private fun minProteinShare(goal: String) = if (goal == "MAINTAIN") 0.15 else 0.18
    private val FAT_RANGE = 0.20..0.38

    /** 0 to 1: how well a day's carbs, protein and fat are balanced. */
    fun dayBalance(day: List<NutritionEntryEntity>, goal: String): Double {
        val c = day.sumOf { it.carbsG } * 4
        val p = day.sumOf { it.proteinG } * 4
        val f = day.sumOf { it.fatG } * 9
        val total = c + p + f
        if (total <= 0) return 1.0
        fun inRange(x: Double, range: ClosedFloatingPointRange<Double>, slack: Double) = when {
            x < range.start -> (1 - (range.start - x) / slack).coerceIn(0.0, 1.0)
            x > range.endInclusive -> (1 - (x - range.endInclusive) / slack).coerceIn(0.0, 1.0)
            else -> 1.0
        }
        val carbs = inRange(c / total, carbRange(goal), 0.15)
        val protein = inRange(p / total, minProteinShare(goal)..1.0, 0.08)
        val fat = inRange(f / total, FAT_RANGE, 0.12)
        return carbs * 0.4 + protein * 0.35 + fat * 0.25
    }

    private fun balancePart(days: List<List<NutritionEntryEntity>>, goal: String, max: Int): DietScorePart {
        val range = carbRange(goal)
        fun share(day: List<NutritionEntryEntity>, f: (NutritionEntryEntity) -> Double, perG: Int): Double {
            val total = day.sumOf { it.carbsG * 4 + it.proteinG * 4 + it.fatG * 9 }
            return if (total > 0) day.sumOf(f) * perG / total else 0.0
        }
        if (days.isEmpty() || days.all { d -> d.sumOf { it.carbsG + it.proteinG + it.fatG } <= 0 }) {
            return DietScorePart("balance", "Balance & portions", max / 2, max, "Not enough full days logged to judge yet", "")
        }
        val avg = days.map { dayBalance(it, goal) }.average()
        val carbs = days.map { share(it, { e -> e.carbsG }, 4) }.average()
        val protein = days.map { share(it, { e -> e.proteinG }, 4) }.average()
        val fat = days.map { share(it, { e -> e.fatG }, 9) }.average()
        val aim = "${pct(range.start)}–${pct(range.endInclusive)}%"
        return DietScorePart(
            key = "balance",
            label = "Balance & portions",
            points = scaled(avg, 0.9, max),
            maxPoints = max,
            detail = "On a typical day ${pct(carbs)}% of calories came from carbs (aim $aim), ${pct(protein)}% from protein, ${pct(fat)}% from fat",
            tip = when {
                carbs > range.endInclusive -> "Days lean heavy on carbs: fewer rotis or less rice, and an extra bowl of dal, curd, paneer or eggs instead."
                protein < minProteinShare(goal) -> "Add a protein to more of your day: dal, curd, paneer, eggs, chicken, fish or tofu."
                fat > FAT_RANGE.endInclusive -> "Go lighter on oil, ghee and fried food."
                else -> "Keep each day's plate mixed: some protein, some carbs, a little fat and plenty of vegetables."
            }
        )
    }

    private fun proteinPart(
        byDate: Map<LocalDate, List<NutritionEntryEntity>>,
        days: Set<LocalDate>,
        inWindow: List<NutritionEntryEntity>,
        target: Double,
        calorieTarget: Double,
        today: LocalDate,
        zone: ZoneId,
        max: Int
    ): DietScorePart {
        val tip = "Have a protein with most meals: eggs, paneer, chicken, fish, tofu, curd, dal or a whey shake."
        val history = (1..CoachPatternEngine.LOOKBACK_DAYS).map { byDate[today.minusDays(it.toLong())].orEmpty() }
        val typical = CoachPatternEngine.typicalDayCalories(history, calorieTarget)
        val complete = days.mapNotNull { d ->
            byDate[d]?.let { CoachPatternEngine.buildDayLog(d, it, typical, calorieTarget, zone) }
        }.filter { it.status == DayLogStatus.COMPLETE }

        if (complete.size >= CoachPatternEngine.MIN_COMPLETE_DAYS) {
            val reached = complete.map { (it.proteinG / target).coerceAtMost(1.0) }.average()
            return DietScorePart(
                key = "protein",
                label = "Protein",
                points = scaled(reached, 0.95, max),
                maxPoints = max,
                detail = "Reached ${pct(reached)}% of your ${target.roundToInt()}g goal on average, over ${complete.size} fully logged days",
                tip = tip
            )
        }
        // Too few full days to judge totals: judge how protein-rich the logged food was instead.
        val kcal = inWindow.sumOf { it.calories }
        val density = if (kcal > 0) inWindow.sumOf { it.proteinG } * 4 / kcal else 0.0
        val needed = (target * 4 / calorieTarget).coerceIn(0.12, 0.40)
        return DietScorePart(
            key = "protein",
            label = "Protein",
            points = scaled(density / needed, 0.95, max),
            maxPoints = max,
            detail = "${pct(density)}% of the calories you logged came from protein (your goal needs about ${pct(needed)}%)",
            tip = tip
        )
    }

    private fun vegPart(days: List<List<Pair<NutritionEntryEntity, MealTags>>>, max: Int): DietScorePart {
        if (days.isEmpty()) {
            return DietScorePart("veg", "Vegetables & fruit", max / 2, max, "Not enough full days logged to judge yet", "")
        }
        val servings = days.map { day ->
            val fruit = day.sumOf { it.second.fruitServings }
            val veg = (day.sumOf { it.second.vegFruitServings } - fruit).coerceAtLeast(0.0)
            veg + fruit.coerceAtMost(FRUIT_CAP)
        }
        val kinds = days.map { day -> day.sumOf { it.second.vegKinds } }
        val dayScores = servings.zip(kinds) { s, k -> (s / 4.0).coerceAtMost(1.0) * 0.8 + (k / 3.0).coerceAtMost(1.0) * 0.2 }
        return DietScorePart(
            key = "veg",
            label = "Vegetables & fruit",
            points = scaled(dayScores.average(), 0.95, max),
            maxPoints = max,
            detail = "About ${fmt1(servings.average())} servings a day, ${fmt1(kinds.average())} different kinds (aim for 4+ servings and a colourful mix; fruit counts up to 2)",
            tip = "Make the plate colourful: a sabzi plus a salad or raita, and a piece of fruit."
        )
    }

    /** Real treats, where foods eaten within an hour of each other are one occasion. */
    private fun treatOccasions(tagged: List<Pair<NutritionEntryEntity, MealTags>>): Int {
        val times = tagged.filter { (e, t) -> PersonalNutrition.countsAsTreat(e, t) }.map { it.first.timestamp }.sorted()
        var count = 0
        var last = Long.MIN_VALUE
        for (t in times) {
            if (count == 0 || t - last > 3_600_000L) count++
            last = t
        }
        return count
    }

    private fun treatPart(tagged: List<Pair<NutritionEntryEntity, MealTags>>, max: Int): DietScorePart {
        val treats = treatOccasions(tagged)
        val allowance = CoachPatternEngine.WEEKLY_TREAT_ALLOWANCE
        val extra = (treats - allowance).coerceAtLeast(0)
        // Foods to avoid until the goal is reached have no free allowance.
        val avoid = tagged.filter { (e, t) -> PersonalNutrition.countsAsAvoid(e, t) }
        val avoidNames = avoid.map { (e, t) -> t.foodType.ifBlank { e.foodText.lowercase().take(30) } }.distinct()
        val lost = extra * 3 + avoid.size * 3
        val treatText = if (extra == 0) {
            "$treats treats, within the $allowance a week that fit any plan"
        } else {
            "$treats treats; $allowance a week fit any plan"
        }
        return DietScorePart(
            key = "treats",
            label = "Treats & foods to avoid",
            points = (max - lost).coerceAtLeast(0),
            maxPoints = max,
            detail = treatText + (if (avoid.isNotEmpty()) ". Best avoided for now: ${avoidNames.take(3).joinToString(", ")}" else ""),
            tip = if (avoid.isNotEmpty()) {
                "Skip ${avoidNames.first()} until you reach your goal; treats are fine up to $allowance a week."
            } else {
                "Keep treats to $allowance a week, and save them for the ones you'll really enjoy."
            }
        )
    }

    private fun fibrePart(tagged: List<Pair<NutritionEntryEntity, MealTags>>, max: Int): DietScorePart {
        val kcal = tagged.sumOf { it.first.calories }
        val perThousand = if (kcal > 0) tagged.sumOf { it.second.fiberG } / kcal * 1000 else 0.0
        return DietScorePart(
            key = "fibre",
            label = "Fibre",
            points = scaled(perThousand / 14.0, 1.0, max),
            maxPoints = max,
            detail = "About ${perThousand.roundToInt()}g per 1,000 kcal (aim for 14g)",
            tip = "Add dal, beans, whole grains, vegetables or fruit for more fibre."
        )
    }

    /** The part that lost the most points, as one line to act on; null if the week was strong all round. */
    fun biggestGain(score: DietScore): String? {
        val worst = score.parts.maxBy { (it.maxPoints - it.points).toDouble() / it.maxPoints }
        if (worst.maxPoints - worst.points < 3 || worst.tip.isBlank()) return null
        return "${worst.label}: ${worst.detail.replaceFirstChar { it.lowercase() }}. ${worst.tip}"
    }

    private fun pct(x: Double): Int = (x * 100).roundToInt()

    private fun fmt1(x: Double): String = ((x * 10).roundToInt() / 10.0).toString()

    private fun scaled(ratio: Double, fullAt: Double, max: Int): Int =
        (max * (ratio / fullAt).coerceIn(0.0, 1.0)).roundToInt()

    private fun dateOf(timestamp: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
}
