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

/**
 * Diet Quality as a coach would judge a week of eating: enough protein, vegetables and fruit,
 * mostly whole food, treats kept to a couple a week, and enough fibre. Every part works from
 * meals that were logged, so a forgotten meal doesn't cost points; protein is only judged by
 * the day on days that look fully logged, and by the meal otherwise.
 */
object DietScoreEngine {

    const val MIN_DAYS = 3
    const val PROTEIN_POINTS = 25
    const val VEG_POINTS = 25
    const val WHOLE_POINTS = 20
    const val TREAT_POINTS = 20
    const val FIBRE_POINTS = 10

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
        weeksAgo: Int = 0
    ): DietScore? {
        val dates = (0..6).map { today.minusDays((weeksAgo * 7 + it).toLong()) }.toSet()
        val byDate = entries.groupBy { dateOf(it.timestamp, zone) }
        val inWindow = entries.filter { dateOf(it.timestamp, zone) in dates }
        val daysLogged = inWindow.map { dateOf(it.timestamp, zone) }.distinct().size
        if (daysLogged < MIN_DAYS) return null

        val tagged = inWindow.map { it to MealTags.of(it) }
        // A meal is one sitting: a day plus a meal time.
        val meals = tagged.groupBy { (e, _) -> dateOf(e.timestamp, zone) to CoachPatternEngine.slotOf(e.timestamp, zone) }
        val mainMeals = meals.filterKeys { it.second != MealSlot.SNACK }.values.toList().ifEmpty { meals.values.toList() }

        val parts = listOf(
            proteinPart(byDate, dates - today, mainMeals, proteinTargetG, calorieTarget, today, zone),
            vegPart(mainMeals),
            wholeFoodPart(tagged),
            treatPart(meals.values.toList()),
            fibrePart(tagged)
        )
        return DietScore(
            score = parts.sumOf { it.points }.coerceIn(0, 100),
            parts = parts,
            daysLogged = daysLogged,
            treatMeals = meals.values.count { meal -> meal.any { it.second.treat } },
            estimatedShare = tagged.count { it.second.estimated }.toDouble() / tagged.size
        )
    }

    private fun proteinPart(
        byDate: Map<LocalDate, List<NutritionEntryEntity>>,
        days: Set<LocalDate>,
        mainMeals: List<List<Pair<NutritionEntryEntity, MealTags>>>,
        target: Double,
        calorieTarget: Double,
        today: LocalDate,
        zone: ZoneId
    ): DietScorePart {
        val tip = "Build each main meal around a protein: eggs, paneer, chicken, fish, tofu, curd or dal."
        val history = (1..CoachPatternEngine.LOOKBACK_DAYS).map { byDate[today.minusDays(it.toLong())].orEmpty() }
        val typical = CoachPatternEngine.typicalDayCalories(history, calorieTarget)
        val complete = days.mapNotNull { d ->
            byDate[d]?.let { CoachPatternEngine.buildDayLog(d, it, typical, calorieTarget, zone) }
        }.filter { it.status == DayLogStatus.COMPLETE }

        if (complete.size >= CoachPatternEngine.MIN_COMPLETE_DAYS) {
            val hit = complete.count { it.proteinG >= target * 0.9 }
            return DietScorePart(
                key = "protein", label = "Protein",
                points = scaled(hit.toDouble() / complete.size, 0.8, PROTEIN_POINTS),
                maxPoints = PROTEIN_POINTS,
                detail = "Hit your ${target.roundToInt()}g target on $hit of ${complete.size} fully logged days",
                tip = tip
            )
        }
        val perMeal = target / 3
        val good = mainMeals.count { meal -> meal.sumOf { it.first.proteinG } >= perMeal * 0.7 }
        return DietScorePart(
            key = "protein", label = "Protein",
            points = if (mainMeals.isEmpty()) 0 else scaled(good.toDouble() / mainMeals.size, 0.8, PROTEIN_POINTS),
            maxPoints = PROTEIN_POINTS,
            detail = "$good of ${mainMeals.size} meals had a good protein serving (about ${(perMeal * 0.7).roundToInt()}g or more)",
            tip = tip
        )
    }

    private fun vegPart(mainMeals: List<List<Pair<NutritionEntryEntity, MealTags>>>): DietScorePart {
        val withVeg = mainMeals.count { meal -> meal.sumOf { it.second.vegFruitServings } >= 0.5 }
        return DietScorePart(
            key = "veg", label = "Vegetables & fruit",
            points = if (mainMeals.isEmpty()) 0 else scaled(withVeg.toDouble() / mainMeals.size, 0.67, VEG_POINTS),
            maxPoints = VEG_POINTS,
            detail = "In $withVeg of ${mainMeals.size} main meals",
            tip = "Add a bowl of sabzi, a salad or a piece of fruit to more of your meals."
        )
    }

    private fun wholeFoodPart(tagged: List<Pair<NutritionEntryEntity, MealTags>>): DietScorePart {
        val totalKcal = tagged.sumOf { it.first.calories }
        val share = if (totalKcal > 0) {
            tagged.filter { it.second.processing == 1 }.sumOf { it.first.calories } / totalKcal
        } else {
            tagged.count { it.second.processing == 1 }.toDouble() / tagged.size
        }
        val points = (WHOLE_POINTS * ((share - 0.35) / 0.40)).coerceIn(0.0, WHOLE_POINTS.toDouble()).roundToInt()
        return DietScorePart(
            key = "whole", label = "Whole & home-cooked food",
            points = points,
            maxPoints = WHOLE_POINTS,
            detail = "${(share * 100).roundToInt()}% of your calories (aim for 75% or more)",
            tip = "Swap one packaged or fast-food item a day for something home-cooked."
        )
    }

    private fun treatPart(meals: List<List<Pair<NutritionEntryEntity, MealTags>>>): DietScorePart {
        val treatMeals = meals.count { meal -> meal.any { it.second.treat } }
        val allowance = CoachPatternEngine.WEEKLY_TREAT_ALLOWANCE
        val extra = (treatMeals - allowance).coerceAtLeast(0)
        return DietScorePart(
            key = "treats", label = "Treats",
            points = (TREAT_POINTS - extra * 5).coerceAtLeast(0),
            maxPoints = TREAT_POINTS,
            detail = if (extra == 0) "$treatMeals treat meals, within the $allowance a week that fit your plan"
            else "$treatMeals treat meals; $allowance a week fit your plan, the extra $extra cost ${extra * 5} points",
            tip = "Keep treats to $allowance a week, and save them for the meals you'll really enjoy."
        )
    }

    private fun fibrePart(tagged: List<Pair<NutritionEntryEntity, MealTags>>): DietScorePart {
        val kcal = tagged.sumOf { it.first.calories }
        val perThousand = if (kcal > 0) tagged.sumOf { it.second.fiberG } / kcal * 1000 else 0.0
        return DietScorePart(
            key = "fibre", label = "Fibre",
            points = scaled(perThousand / 14.0, 1.0, FIBRE_POINTS),
            maxPoints = FIBRE_POINTS,
            detail = "About ${perThousand.roundToInt()}g per 1,000 kcal (aim for 14g)",
            tip = "Add dal, beans, whole grains, vegetables or fruit for more fibre."
        )
    }

    /** The part that lost the most points, as one line to act on; null if the week was strong all round. */
    fun biggestGain(score: DietScore): String? {
        val worst = score.parts.maxBy { it.maxPoints - it.points }
        if (worst.maxPoints - worst.points < 3) return null
        return "${worst.label}: ${worst.detail.replaceFirstChar { it.lowercase() }}. ${worst.tip}"
    }

    private fun scaled(ratio: Double, fullAt: Double, max: Int): Int =
        (max * (ratio / fullAt).coerceIn(0.0, 1.0)).roundToInt()

    private fun dateOf(timestamp: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
}
