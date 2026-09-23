package com.superfit.app.domain

import com.superfit.app.data.UserProfileEntity
import com.superfit.app.data.WeightEntryEntity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

sealed interface AreaOfConcern {
    object None : AreaOfConcern
    data class StaleLog(val daysAgo: Long) : AreaOfConcern
    data class Plateau(val daysCount: Long) : AreaOfConcern
    data class RapidLoss(val pacePerWeek: Double) : AreaOfConcern
}

data class WeightGoalMetrics(
    val startingWeightKg: Double,
    val currentWeightKg: Double,
    val targetWeightKg: Double,
    val progressPercentage: Int,
    val totalWeightChangeKg: Double,
    val weeklyPaceKg: Double?,
    val daysElapsed: Long,
    val areaOfConcern: AreaOfConcern
)

object WeightTrendEngine {

    fun calculateMetrics(
        profile: UserProfileEntity,
        weightEntries: List<WeightEntryEntity> = emptyList()
    ): WeightGoalMetrics {
        val sorted = weightEntries.sortedByDescending { it.timestamp }
        val latestEntry = sorted.firstOrNull()
        
        val startingWeight = if (profile.startingWeightKg > 0) profile.startingWeightKg else profile.weightKg
        val targetWeight = if (profile.targetWeightKg > 0) profile.targetWeightKg else profile.weightKg
        val currentWeight = latestEntry?.weightKg ?: profile.weightKg

        val totalWeightChange = currentWeight - startingWeight

        val progressPercentage = if (abs(startingWeight - targetWeight) > 0.01) {
            val totalDistance = abs(startingWeight - targetWeight)
            val completedDistance = abs(startingWeight - currentWeight)
            
            // Verify directional progress
            val isLossGoal = targetWeight < startingWeight
            val movedInRightDirection = if (isLossGoal) currentWeight <= startingWeight else currentWeight >= startingWeight
            
            if (movedInRightDirection) {
                min(100, max(0, ((completedDistance / totalDistance) * 100).toInt()))
            } else {
                0
            }
        } else {
            100
        }

        val now = System.currentTimeMillis()
        val daysElapsed = max(1L, (now - profile.startDateTimestamp) / (1000 * 3600 * 24))

        // Time-aware pace calculation
        val weeklyPace: Double? = if (sorted.size >= 2) {
            val earliest = sorted.last()
            val timeDiffDays = max(1L, (sorted.first().timestamp - earliest.timestamp) / (1000 * 3600 * 24))
            if (timeDiffDays >= 3) {
                val weightDiff = sorted.first().weightKg - earliest.weightKg
                (weightDiff / timeDiffDays) * 7.0
            } else {
                null
            }
        } else {
            null
        }

        // Detect Area of Concern
        val daysSinceLastLog = if (latestEntry != null) {
            (now - latestEntry.timestamp) / (1000 * 3600 * 24)
        } else {
            daysElapsed
        }

        val areaOfConcern: AreaOfConcern = when {
            daysSinceLastLog >= 14 -> AreaOfConcern.StaleLog(daysSinceLastLog)
            weeklyPace != null && weeklyPace < -1.5 -> AreaOfConcern.RapidLoss(weeklyPace)
            sorted.size >= 3 && abs(totalWeightChange) < 0.3 && daysElapsed >= 21 -> AreaOfConcern.Plateau(daysElapsed)
            else -> AreaOfConcern.None
        }

        return WeightGoalMetrics(
            startingWeightKg = startingWeight,
            currentWeightKg = currentWeight,
            targetWeightKg = targetWeight,
            progressPercentage = progressPercentage,
            totalWeightChangeKg = totalWeightChange,
            weeklyPaceKg = weeklyPace,
            daysElapsed = daysElapsed,
            areaOfConcern = areaOfConcern
        )
    }
}
