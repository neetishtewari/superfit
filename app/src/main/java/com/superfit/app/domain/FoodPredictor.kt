package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.PredictedFood
import java.util.Calendar

object FoodPredictor {
    fun predict(
        allEntries: List<NutritionEntryEntity>,
        currentHour: Int,
        nowMs: Long = System.currentTimeMillis()
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
                val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
                val loggedHour = cal.get(Calendar.HOUR_OF_DAY)
                val diff = Math.abs(currentHour - loggedHour)
                Math.min(diff, 24 - diff)
            }.minOrNull() ?: 12

            val timeScore = Math.max(0.0, 1.0 - (minHourDiff / 4.0))

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
