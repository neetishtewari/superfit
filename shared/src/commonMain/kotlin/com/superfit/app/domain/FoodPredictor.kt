package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.PredictedFood
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object FoodPredictor {
    @OptIn(ExperimentalTime::class)
    fun predict(
        allEntries: List<NutritionEntryEntity>,
        currentHour: Int,
        nowMs: Long = Clock.System.now().toEpochMilliseconds(),
        zone: TimeZone = TimeZone.currentSystemDefault()
    ): List<PredictedFood> {
        if (allEntries.isEmpty()) return emptyList()

        val grouped = allEntries.groupBy { it.foodText.trim().lowercase() }

        return grouped.map { (loweredName, entries) ->
            // Use the most common exact casing used for this food
            val originalName = entries.groupBy { it.foodText }
                .maxByOrNull { it.value.size }?.key ?: entries.first().foodText

            val avgCalories = entries.map { it.calories }.average()
            val avgProtein = entries.map { it.proteinG }.average()
            val avgCarbs = entries.map { it.carbsG }.average()
            val avgFat = entries.map { it.fatG }.average()

            val frequency = entries.size.toDouble()

            // Recency score: how many days ago it was last logged
            val lastTimestamp = entries.maxOf { it.timestamp }
            val diffMs = nowMs - lastTimestamp
            val diffDays = (diffMs / (1000.0 * 60 * 60 * 24)).coerceAtLeast(0.0)
            val recencyScore = 1.0 / (1.0 + diffDays)

            // Time-of-day affinity: minimum hour difference to currentHour
            val minHourDiff = entries.map { entry ->
                val loggedHour = Instant.fromEpochMilliseconds(entry.timestamp).toLocalDateTime(zone).hour
                val diff = abs(currentHour - loggedHour)
                min(diff, 24 - diff)
            }.minOrNull() ?: 12

            val timeScore = max(0.0, 1.0 - (minHourDiff / 4.0))

            // Score incorporates frequency, recency, and time score
            val score = frequency * (1.0 + recencyScore) * (1.0 + timeScore * 1.5)

            PredictedFood(
                foodText = originalName,
                calories = avgCalories,
                proteinG = avgProtein,
                carbsG = avgCarbs,
                fatG = avgFat,
                score = score
            )
        }.sortedByDescending { it.score }
    }
}
