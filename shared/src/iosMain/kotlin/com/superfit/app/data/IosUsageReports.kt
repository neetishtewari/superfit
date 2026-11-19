package com.superfit.app.data

import kotlin.experimental.ExperimentalNativeApi

/**
 * The iPhone's usage counts and crash reports, done by Swift (UsageReportsBridge.swift) with
 * Firebase Analytics and Crashlytics. Off in the dev build and when the person turns it off.
 */
interface IosUsageReports {
    /** The Settings › Privacy switch: on unless the person turned it off. */
    fun isEnabled(): Boolean

    fun setEnabled(on: Boolean)

    fun log(event: String, params: Map<String, String>)

    /** A handled error, or a crash in the shared Kotlin code, with its stack for finding the line. */
    fun recordError(name: String, message: String, stack: List<String>)

    fun setKey(name: String, value: String)
}

/** Sends every shared screen's events and errors to [reports], and Kotlin crashes too. */
@OptIn(ExperimentalNativeApi::class)
fun installUsageReports(reports: IosUsageReports) {
    UsageEvents.report = reports::log
    UsageEvents.key = reports::setKey
    val record: (Throwable) -> Unit = { e ->
        reports.recordError(e::class.simpleName ?: "Error", e.message.orEmpty(), e.getStackTrace().toList())
    }
    UsageEvents.error = record
    // A crash in Kotlin code is noted before the app closes, so it shows up with its Kotlin stack.
    val previous = getUnhandledExceptionHook()
    setUnhandledExceptionHook { e ->
        runCatching { record(e) }
        previous?.invoke(e)
        terminateWithUnhandledException(e)
    }
}
