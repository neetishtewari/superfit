package com.superfit.app.data

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The current time in milliseconds, on Android and iPhone alike. */
object Now {
    @OptIn(ExperimentalTime::class)
    fun millis(): Long = Clock.System.now().toEpochMilliseconds()
}
