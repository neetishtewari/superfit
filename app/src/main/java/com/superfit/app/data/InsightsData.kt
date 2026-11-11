package com.superfit.app.data

import com.superfit.app.domain.DayTotals
import com.superfit.app.domain.WorkoutView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinTimeZone

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
    ): Map<kotlinx.datetime.LocalDate, DayTotals> = DayTotalsBuilder.days(
        nutrition = nutrition,
        voiceWorkouts = voiceWorkouts,
        activity = activity,
        imported = imported.mapKeys { it.key.toKotlinLocalDate() },
        kcalTarget = { kcalTarget(it.toJavaLocalDate()) },
        proteinTargetG = proteinTargetG,
        zone = zone.toKotlinTimeZone()
    )

    fun weighIns(entries: List<WeightEntryEntity>, zone: ZoneId = ZoneId.systemDefault()): List<Pair<LocalDate, Double>> =
        entries.sortedBy { it.timestamp }.map { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() to it.weightKg }
            .groupBy { it.first }
            .map { (date, list) -> date to list.last().second }
            .sortedBy { it.first }
}
