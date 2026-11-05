package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/** Who the report is about, shown at the top of page 1. */
data class CoachExportHeader(
    val name: String?,
    val goal: String,
    val age: Int,
    val heightCm: Double,
    val isMale: Boolean,
    val startKg: Double,
    val targetKg: Double
)

data class CoachExportMeal(val time: String, val name: String, val kcal: Int, val proteinG: Int)

data class CoachExportDay(
    val date: LocalDate,
    /** Two or more meals; days with fewer are shown as not (fully) logged, never as zero eating. */
    val logged: Boolean,
    val kcal: Int,
    val kcalTarget: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val steps: Int,
    val workouts: List<String>,
    val weightKg: Double?,
    val meals: List<CoachExportMeal>
)

data class CoachExport(
    val header: CoachExportHeader,
    val from: LocalDate,
    val to: LocalDate,
    val daysLogged: Int,
    val avgKcal: Int?,
    val avgKcalTarget: Int?,
    val avgProteinG: Int?,
    val proteinTargetG: Int,
    /** Same measure as Insights: average share of the protein goal, each day capped at 100%. */
    val proteinPct: Int?,
    val avgCarbsG: Int?,
    val avgFatG: Int?,
    /** Workouts other than walks. */
    val workoutCount: Int,
    val walkCount: Int = 0,
    val workoutMinutes: Long,
    val avgSteps: Int?,
    val weightStart: Pair<LocalDate, Double>?,
    val weightEnd: Pair<LocalDate, Double>?,
    /** Diet Quality per week of the period, oldest first; null for a week with too little logged. */
    val dietScores: List<Int?>,
    val days: List<CoachExportDay>,
    /** From the user's smart scale, when they have one. */
    val body: BodySummary? = null
) {
    val totalDays: Int get() = days.size
}

/**
 * Builds the "Share my report" summary from the same day totals Insights uses. Averages
 * count only logged days, so a forgotten day never reads as under-eating. No AI.
 */
@OptIn(ExperimentalTime::class)
object CoachExportBuilder {

    fun build(
        header: CoachExportHeader,
        weeks: Int,
        today: LocalDate,
        days: Map<LocalDate, DayTotals>,
        meals: List<NutritionEntryEntity>,
        weighIns: List<Pair<LocalDate, Double>>,
        proteinTargetG: Double,
        dietScores: List<Int?>,
        zone: TimeZone = TimeZone.currentSystemDefault(),
        body: BodySummary? = null
    ): CoachExport {
        // Ends yesterday, like Insights: today isn't over yet.
        val to = today.minus(1, DateTimeUnit.DAY)
        val from = to.minus(weeks * 7 - 1, DateTimeUnit.DAY)
        val mealsByDate = meals.groupBy { Instant.fromEpochMilliseconds(it.timestamp).toLocalDateTime(zone).date }
        val weightByDate = weighIns.toMap()

        val reportDays = InsightsEngine.range(days, from, to).map { d ->
            val date = d.date
            val dayMeals = mealsByDate[date].orEmpty().sortedBy { it.timestamp }
            CoachExportDay(
                date = date,
                logged = d.logged,
                kcal = d.kcalEaten.roundToInt(),
                kcalTarget = d.kcalTarget.roundToInt(),
                proteinG = d.proteinG.roundToInt(),
                carbsG = dayMeals.sumOf { it.carbsG }.roundToInt(),
                fatG = dayMeals.sumOf { it.fatG }.roundToInt(),
                steps = d.steps,
                workouts = group(d.workouts),
                weightKg = weightByDate[date],
                meals = dayMeals.map {
                    CoachExportMeal(
                        time = Instant.fromEpochMilliseconds(it.timestamp).toLocalDateTime(zone).time.let { t -> "${two(t.hour)}:${two(t.minute)}" },
                        name = it.foodText,
                        kcal = it.calories.roundToInt(),
                        proteinG = it.proteinG.roundToInt()
                    )
                }
            )
        }

        val logged = reportDays.filter { it.logged }
        fun avg(f: (CoachExportDay) -> Int): Int? = if (logged.isEmpty()) null else logged.map(f).average().roundToInt()
        val stats = InsightsEngine.stats(InsightsEngine.range(days, from, to))
        val withSteps = reportDays.filter { it.steps > 0 }
        // Short walks are counted apart so a coach doesn't read 70 walks as 70 training sessions.
        val facts = InsightsEngine.range(days, from, to).flatMap { it.workouts }
        val walks = facts.count { isWalk(it) }
        val inPeriod = weighIns.filter { it.first >= from && it.first <= to }.sortedBy { it.first }

        return CoachExport(
            header = header,
            from = from,
            to = to,
            daysLogged = logged.size,
            avgKcal = avg { it.kcal },
            avgKcalTarget = avg { it.kcalTarget },
            avgProteinG = avg { it.proteinG },
            proteinTargetG = proteinTargetG.roundToInt(),
            proteinPct = stats.proteinPct,
            avgCarbsG = avg { it.carbsG },
            avgFatG = avg { it.fatG },
            workoutCount = facts.size - walks,
            walkCount = walks,
            workoutMinutes = stats.moveMinutes,
            avgSteps = if (withSteps.isEmpty()) null else withSteps.map { it.steps }.average().roundToInt(),
            weightStart = inPeriod.firstOrNull(),
            weightEnd = inPeriod.lastOrNull()?.takeIf { inPeriod.size > 1 },
            dietScores = dietScores,
            days = reportDays,
            body = body?.takeIf { it.hasScaleData }
        )
    }

    private fun two(n: Int) = n.toString().padStart(2, '0')

    private fun isWalk(w: ActivityFact) = !w.isRun && w.title.contains("walk", ignoreCase = true)

    /** One line per kind of workout in a day: "4× Walk · 70 min · 3.6 km" instead of four lines. */
    fun group(workouts: List<ActivityFact>): List<String> = workouts.groupBy { it.title }.map { (title, list) ->
        if (list.size == 1) {
            describe(list.first())
        } else {
            val minutes = list.sumOf { it.minutes }
            val km = list.mapNotNull { it.distanceKm?.takeIf { d -> d > 0 } }.sum()
            buildList {
                add("${list.size}× $title")
                if (minutes > 0) add("$minutes min")
                if (km > 0) add("${Decimals.one(km)} km")
            }.joinToString(" · ")
        }
    }

    /** "Run · 32 min · 5.2 km", or just the name when the length isn't known. */
    fun describe(w: ActivityFact): String = buildList {
        add(w.title)
        if (w.minutes > 0) add("${w.minutes} min")
        w.distanceKm?.takeIf { it > 0 }?.let { add("${Decimals.one(it)} km") }
    }.joinToString(" · ")

    fun goalLabel(goal: String): String = FitnessGoals.title(goal)
}
