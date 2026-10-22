package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

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
    val workoutCount: Int,
    val workoutMinutes: Long,
    val avgSteps: Int?,
    val weightStart: Pair<LocalDate, Double>?,
    val weightEnd: Pair<LocalDate, Double>?,
    /** Diet Quality per week of the period, oldest first; null for a week with too little logged. */
    val dietScores: List<Int?>,
    val days: List<CoachExportDay>
) {
    val totalDays: Int get() = days.size
}

/**
 * Builds the "Share with my coach" summary from the same day totals Insights uses. Averages
 * count only logged days, so a forgotten day never reads as under-eating. No AI.
 */
object CoachExportBuilder {

    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    fun build(
        header: CoachExportHeader,
        weeks: Int,
        today: LocalDate,
        days: Map<LocalDate, DayTotals>,
        meals: List<NutritionEntryEntity>,
        weighIns: List<Pair<LocalDate, Double>>,
        proteinTargetG: Double,
        dietScores: List<Int?>,
        zone: ZoneId = ZoneId.systemDefault()
    ): CoachExport {
        // Ends yesterday, like Insights: today isn't over yet.
        val to = today.minusDays(1)
        val from = to.minusDays(weeks * 7L - 1)
        val mealsByDate = meals.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
        val weightByDate = weighIns.toMap()

        val reportDays = InsightsEngine.range(days, from, to).map { d ->
            val dayMeals = mealsByDate[d.date].orEmpty().sortedBy { it.timestamp }
            CoachExportDay(
                date = d.date,
                logged = d.logged,
                kcal = d.kcalEaten.roundToInt(),
                kcalTarget = d.kcalTarget.roundToInt(),
                proteinG = d.proteinG.roundToInt(),
                carbsG = dayMeals.sumOf { it.carbsG }.roundToInt(),
                fatG = dayMeals.sumOf { it.fatG }.roundToInt(),
                steps = d.steps,
                workouts = d.workouts.map { describe(it) },
                weightKg = weightByDate[d.date],
                meals = dayMeals.map {
                    CoachExportMeal(
                        time = Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalTime().format(timeFormat),
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
        val inPeriod = weighIns.filter { !it.first.isBefore(from) && !it.first.isAfter(to) }.sortedBy { it.first }

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
            workoutCount = stats.workouts,
            workoutMinutes = stats.moveMinutes,
            avgSteps = if (withSteps.isEmpty()) null else withSteps.map { it.steps }.average().roundToInt(),
            weightStart = inPeriod.firstOrNull(),
            weightEnd = inPeriod.lastOrNull()?.takeIf { inPeriod.size > 1 },
            dietScores = dietScores,
            days = reportDays
        )
    }

    /** "Run · 32 min · 5.2 km", or just the name when the length isn't known. */
    fun describe(w: ActivityFact): String = buildList {
        add(w.title)
        if (w.minutes > 0) add("${w.minutes} min")
        w.distanceKm?.takeIf { it > 0 }?.let { add(String.format(java.util.Locale.US, "%.1f km", it)) }
    }.joinToString(" · ")

    fun goalLabel(goal: String): String = when (goal) {
        "LOSE_WEIGHT" -> "Lose weight"
        "GAIN_MUSCLE" -> "Build muscle"
        "MAINTAIN" -> "Maintain"
        else -> goal.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}
