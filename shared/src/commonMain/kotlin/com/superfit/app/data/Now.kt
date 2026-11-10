package com.superfit.app.data

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** The current time, on Android and iPhone alike. */
@OptIn(ExperimentalTime::class)
object Now {
    fun millis(): Long = Clock.System.now().toEpochMilliseconds()

    /** Today's date on this phone. */
    fun today(): LocalDate = local().date

    /** The hour of the day on this phone, 0 to 23. */
    fun hour(): Int = local().hour

    /** [millis] as a date and time on this phone. */
    fun local(millis: Long = millis()): LocalDateTime =
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
}
