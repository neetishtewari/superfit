package com.superfit.app.data

import com.superfit.app.domain.ActivityFact
import com.superfit.app.domain.DayTotals
import com.superfit.app.domain.DietScoreEngine
import com.superfit.app.domain.MealTags
import com.superfit.app.domain.WorkoutView
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Turns the stored logs into one [DayTotals] per day for Insights, Journey and "vs your usual",
 * on both phones. Android's InsightsData hands its work to this.
 */
@OptIn(ExperimentalTime::class)
object DayTotalsBuilder {

    fun dateOf(millis: Long, zone: TimeZone): LocalDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date

    /** Workouts from other apps grouped by the day they started. */
    fun byDate(views: List<WorkoutView>, zone: TimeZone): Map<LocalDate, List<WorkoutView>> =
        views.groupBy { dateOf(it.startTime, zone) }

    fun days(
        nutrition: List<NutritionEntryEntity>,
        voiceWorkouts: List<WorkoutEntryEntity>,
        activity: List<ActivityTelemetryEntity>,
        imported: Map<LocalDate, List<WorkoutView>>,
        kcalTarget: (LocalDate) -> Double,
        proteinTargetG: Double,
        zone: TimeZone
    ): Map<LocalDate, DayTotals> {
        val meals = nutrition.groupBy { dateOf(it.timestamp, zone) }
        val voice = voiceWorkouts.groupBy { dateOf(it.timestamp, zone) }
        val steps = activity.mapNotNull { a -> runCatching { LocalDate.parse(a.date) }.getOrNull()?.let { it to a.steps } }.toMap()
        val dates = meals.keys + voice.keys + steps.keys + imported.keys
        return dates.associateWith { date ->
            val dayMeals = meals[date].orEmpty()
            DayTotals(
                date = date,
                kcalEaten = dayMeals.sumOf { it.calories },
                kcalTarget = kcalTarget(date),
                meals = dayMeals.size,
                proteinG = dayMeals.sumOf { it.proteinG },
                proteinTargetG = proteinTargetG,
                steps = steps[date] ?: 0,
                workouts = imported[date].orEmpty().map { ActivityFact.of(it, zone) } + voice[date].orEmpty().map { w ->
                    ActivityFact(title = w.description, minutes = 0, distanceKm = null, kcal = w.caloriesBurned, paceSecondsPerKm = null, isRun = false, reps = w.repsCount.coerceAtLeast(0))
                },
                mealRating = dayMeals.mapNotNull { m -> MealTags.decode(m.qualityTags)?.takeIf { !it.estimated }?.let { DietScoreEngine.mealRating(m, it).toDouble() } }
                    .takeIf { it.isNotEmpty() }?.average()
            )
        }
    }
}
