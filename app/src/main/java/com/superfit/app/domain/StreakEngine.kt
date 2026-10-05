package com.superfit.app.domain

import com.superfit.app.data.StreakStateEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object StreakEngine {

    // Days with a meal logged stay in the same streak as long as the gap between them is at
    // most this many days, i.e. one missed day is forgiven but two in a row end the streak.
    private const val MAX_GAP_DAYS = 2L

    /**
     * Computes the streak from the meal log itself rather than from a stored counter, so it is
     * always right after reinstalls, cloud sync or edits to past days.
     *
     * The streak counts days with at least one meal logged. A single missed day keeps the streak
     * alive (grace day); two missed days in a row end it. Today not being logged yet never breaks it.
     */
    fun fromMealTimestamps(
        mealTimestamps: List<Long>,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): StreakStateEntity {
        val dates = mealTimestamps
            .map { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
            .filter { !it.isAfter(today) }
        return fromLoggedDates(dates, today)
    }

    fun fromLoggedDates(loggedDates: Collection<LocalDate>, today: LocalDate = LocalDate.now()): StreakStateEntity {
        val days = loggedDates.filter { !it.isAfter(today) }.distinct().sorted()
        if (days.isEmpty()) return StreakStateEntity(id = 0, graceDaysRemaining = 1)

        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in days) {
            run = if (previous != null && ChronoUnit.DAYS.between(previous, day) <= MAX_GAP_DAYS) run + 1 else 1
            longest = maxOf(longest, run)
            previous = day
        }

        val lastLogged = days.last()
        val daysSinceLast = ChronoUnit.DAYS.between(lastLogged, today)
        val current = if (daysSinceLast <= MAX_GAP_DAYS) run else 0

        // Yesterday was missed and nothing is logged today yet: the grace day is in use and the
        // streak ends unless a meal is logged today.
        val graceInUse = current > 0 && daysSinceLast == MAX_GAP_DAYS

        return StreakStateEntity(
            id = 0,
            currentStreak = current,
            longestStreak = longest,
            masteryStreak = current,
            graceDaysRemaining = if (graceInUse) 0 else 1,
            lastLoggedDate = lastLogged.toString(),
            lastGraceUsedDate = if (graceInUse) today.minusDays(1).toString() else ""
        )
    }
}
