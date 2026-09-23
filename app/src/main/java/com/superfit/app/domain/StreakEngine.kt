package com.superfit.app.domain

import com.superfit.app.data.StreakStateEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object StreakEngine {

    fun evaluateStreak(
        savedState: StreakStateEntity?,
        hasLoggedToday: Boolean,
        today: LocalDate = LocalDate.now()
    ): StreakStateEntity {
        val todayStr = today.toString()
        if (savedState == null) {
            val initialStreak = if (hasLoggedToday) 1 else 0
            return StreakStateEntity(
                id = 0,
                currentStreak = initialStreak,
                longestStreak = initialStreak,
                masteryStreak = initialStreak,
                graceDaysRemaining = 1,
                lastLoggedDate = if (hasLoggedToday) todayStr else "",
                lastGraceUsedDate = ""
            )
        }

        val lastLoggedDate = if (savedState.lastLoggedDate.isBlank()) null else LocalDate.parse(savedState.lastLoggedDate)
        
        if (lastLoggedDate == null) {
            val streak = if (hasLoggedToday) 1 else 0
            return savedState.copy(
                currentStreak = streak,
                longestStreak = maxOf(savedState.longestStreak, streak),
                lastLoggedDate = if (hasLoggedToday) todayStr else ""
            )
        }

        val daysBetween = ChronoUnit.DAYS.between(lastLoggedDate, today)

        return when {
            // Logged today already
            daysBetween == 0L -> savedState

            // Consecutive day log (Yesterday -> Today)
            daysBetween == 1L && hasLoggedToday -> {
                val newStreak = savedState.currentStreak + 1
                savedState.copy(
                    currentStreak = newStreak,
                    longestStreak = maxOf(savedState.longestStreak, newStreak),
                    lastLoggedDate = todayStr
                )
            }

            // Missed 1 day (e.g., Day before yesterday -> Today)
            daysBetween == 2L -> {
                if (savedState.graceDaysRemaining > 0) {
                    // Consume 1 Grace Day! Streak is preserved!
                    val newStreak = if (hasLoggedToday) savedState.currentStreak + 1 else savedState.currentStreak
                    savedState.copy(
                        currentStreak = newStreak,
                        longestStreak = maxOf(savedState.longestStreak, newStreak),
                        graceDaysRemaining = savedState.graceDaysRemaining - 1,
                        lastLoggedDate = if (hasLoggedToday) todayStr else savedState.lastLoggedDate,
                        lastGraceUsedDate = today.minusDays(1).toString()
                    )
                } else {
                    // Grace days exhausted, streak resets
                    val newStreak = if (hasLoggedToday) 1 else 0
                    savedState.copy(
                        currentStreak = newStreak,
                        lastLoggedDate = if (hasLoggedToday) todayStr else ""
                    )
                }
            }

            // Missed > 1 day
            daysBetween > 2L -> {
                val newStreak = if (hasLoggedToday) 1 else 0
                savedState.copy(
                    currentStreak = newStreak,
                    lastLoggedDate = if (hasLoggedToday) todayStr else ""
                )
            }

            else -> savedState
        }
    }
}
