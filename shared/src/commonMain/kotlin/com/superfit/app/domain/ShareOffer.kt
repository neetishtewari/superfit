package com.superfit.app.domain

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * The "ready to share" card on Today: a run, a walk at least [walkMinMinutes] long, or any other
 * workout from a watch or fitness app (at least [MIN_OTHER_MINUTES] long), through Health Connect,
 * done today or yesterday so an evening workout still shows the next morning. Workouts logged by
 * voice are not imported workouts, so they are never offered. Only the newest one is offered; once
 * it is shared or dismissed ([handledIds]) nothing older comes back.
 */
@OptIn(ExperimentalTime::class)
object ShareOffer {
    const val DEFAULT_WALK_MINUTES = 30
    val WALK_CHOICES = listOf(20, 30, 45, 60)

    /** Other workouts (gym, cycling, yoga...) must last this long, so a two-minute stretch isn't offered. */
    const val MIN_OTHER_MINUTES = 10

    fun isRun(w: WorkoutView) = w.merged.primary.exerciseType in setOf(ExerciseTypes.RUNNING, ExerciseTypes.RUNNING_TREADMILL)

    fun isWalk(w: WorkoutView) = w.merged.primary.exerciseType == ExerciseTypes.WALKING

    fun isOther(w: WorkoutView) = !isRun(w) && !isWalk(w)

    /** "run", "walk" or "workout", for the card wording and analytics. */
    fun kind(w: WorkoutView) = when {
        isRun(w) -> "run"
        isWalk(w) -> "walk"
        else -> "workout"
    }

    /** [walkMinMinutes] null means walks are off. */
    fun pick(
        workouts: List<WorkoutView>,
        today: LocalDate,
        runsOn: Boolean,
        walkMinMinutes: Int?,
        handledIds: Set<String>,
        zone: TimeZone = TimeZone.currentSystemDefault(),
        othersOn: Boolean = true
    ): WorkoutView? {
        val yesterday = today.minus(1, DateTimeUnit.DAY)
        val newest = workouts
            .filter { Instant.fromEpochMilliseconds(it.startTime).toLocalDateTime(zone).date in yesterday..today }
            .filter {
                (runsOn && isRun(it)) ||
                    (walkMinMinutes != null && isWalk(it) && it.minutes >= walkMinMinutes) ||
                    (othersOn && isOther(it) && it.minutes >= MIN_OTHER_MINUTES)
            }
            .maxByOrNull { it.startTime } ?: return null
        return newest.takeIf { it.merged.memberIds.none { id -> id in handledIds } && it.merged.primary.id !in handledIds }
    }
}
