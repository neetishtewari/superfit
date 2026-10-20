package com.superfit.app.data

import com.superfit.app.domain.ActivityFact
import com.superfit.app.domain.DayTotals
import com.superfit.app.domain.WorkoutView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Turns the stored logs into one [DayTotals] per day for Insights, Journey and "vs your usual". */
object InsightsData {

    fun days(
        nutrition: List<NutritionEntryEntity>,
        voiceWorkouts: List<WorkoutEntryEntity>,
        activity: List<ActivityTelemetryEntity>,
        imported: Map<LocalDate, List<WorkoutView>>,
        kcalTarget: (LocalDate) -> Double,
        proteinTargetG: Double,
        zone: ZoneId = ZoneId.systemDefault()
    ): Map<LocalDate, DayTotals> {
        fun dateOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val meals = nutrition.groupBy { dateOf(it.timestamp) }
        val voice = voiceWorkouts.groupBy { dateOf(it.timestamp) }
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
                workouts = imported[date].orEmpty().map { ActivityFact.of(it) } + voice[date].orEmpty().map { w ->
                    ActivityFact(title = w.description, minutes = 0, distanceKm = null, kcal = w.caloriesBurned, paceSecondsPerKm = null, isRun = false)
                }
            )
        }
    }

    fun weighIns(entries: List<WeightEntryEntity>, zone: ZoneId = ZoneId.systemDefault()): List<Pair<LocalDate, Double>> =
        entries.sortedBy { it.timestamp }.map { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() to it.weightKg }
            .groupBy { it.first }
            .map { (date, list) -> date to list.last().second }
            .sortedBy { it.first }
}
