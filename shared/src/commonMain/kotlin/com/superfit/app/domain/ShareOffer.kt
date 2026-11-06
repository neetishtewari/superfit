package com.superfit.app.domain

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * The "ready to share" card on Today: a run, or a walk at least [walkMinMinutes] long, from
 * Health Connect, done today or yesterday so an evening run still shows the next morning. Only
 * the newest one is offered; once it is shared or dismissed ([handledIds]) nothing older comes back.
 */
@OptIn(ExperimentalTime::class)
object ShareOffer {
    const val DEFAULT_WALK_MINUTES = 30
    val WALK_CHOICES = listOf(20, 30, 45, 60)

    fun isRun(w: WorkoutView) = w.merged.primary.exerciseType in setOf(ExerciseTypes.RUNNING, ExerciseTypes.RUNNING_TREADMILL)

    fun isWalk(w: WorkoutView) = w.merged.primary.exerciseType == ExerciseTypes.WALKING

    /** [walkMinMinutes] null means walks are off. */
    fun pick(
        workouts: List<WorkoutView>,
        today: LocalDate,
        runsOn: Boolean,
        walkMinMinutes: Int?,
        handledIds: Set<String>,
        zone: TimeZone = TimeZone.currentSystemDefault()
    ): WorkoutView? {
        val yesterday = today.minus(1, DateTimeUnit.DAY)
        val newest = workouts
            .filter { Instant.fromEpochMilliseconds(it.startTime).toLocalDateTime(zone).date in yesterday..today }
            .filter { (runsOn && isRun(it)) || (walkMinMinutes != null && isWalk(it) && it.minutes >= walkMinMinutes) }
            .maxByOrNull { it.startTime } ?: return null
        return newest.takeIf { it.merged.memberIds.none { id -> id in handledIds } && it.merged.primary.id !in handledIds }
    }
}
