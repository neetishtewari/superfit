package com.superfit.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * The phone's health app, read only: Apple Health on iPhone (Android reads Health Connect in its
 * own code for now). Every read returns null when nothing could be read, so a refused or failed
 * read never replaces a saved day with zeros.
 */
interface HealthSource {
    /** False on devices without a health store (iPads). */
    fun isAvailable(): Boolean

    /**
     * Shows the phone's own sheet asking what Superfit may read. True once the sheet was answered;
     * Apple never tells an app what was allowed, only that the question was asked.
     */
    suspend fun requestAccess(): Boolean

    suspend fun readActivity(date: LocalDate): ActivityTelemetryEntity?

    /** The day's walked and run distance in meters, or null (then it's estimated from steps). */
    suspend fun readDistanceMeters(date: LocalDate): Double?

    suspend fun readSleep(date: LocalDate): SleepTelemetryEntity?

    /**
     * Workouts any app or watch saved between two times, or null when they couldn't be read.
     * [bmr] fills in the resting part of total calories when only active calories were recorded.
     */
    suspend fun readWorkouts(fromMs: Long, toMs: Long, bmr: Double): List<HealthWorkout>?
}

/** Where [HealthSync] keeps what it read: the account's database, backed up like Android's. */
interface HealthStore {
    suspend fun activity(date: String): ActivityTelemetryEntity?
    suspend fun saveActivity(activity: ActivityTelemetryEntity)
    suspend fun sleep(date: String): SleepTelemetryEntity?
    suspend fun saveSleep(sleep: SleepTelemetryEntity)
}

/**
 * Brings today's and yesterday's steps and sleep in from the health app, like Android's sync: today
 * so far, and yesterday again so its final count is kept. A day is only saved when the health app
 * has something for it and it differs from what's saved, so an empty or refused read never wipes
 * steps another phone on the same account already backed up.
 */
object HealthSync {
    /**
     * Set once the person has answered the phone's access sheet. The number goes up when Superfit
     * reads something new (v2: workouts), so the sheet is offered again for just the new part.
     */
    const val KEY_ASKED = "health_access_asked_v2"

    /** Asked before workouts were read: steps and sleep keep coming in until the new sheet is answered. */
    const val KEY_ASKED_V1 = "health_access_asked"

    suspend fun sync(source: HealthSource, store: HealthStore, today: LocalDate) {
        for (date in listOf(today, today.minus(DatePeriod(days = 1)))) {
            read { source.readActivity(date) }
                ?.takeIf { it.steps > 0 || it.activeCalories > 0 }
                ?.takeIf { it != store.activity(it.date) }
                ?.let { store.saveActivity(it) }
            read { source.readSleep(date) }
                ?.takeIf { it.sleepDurationSeconds > 0 }
                ?.takeIf { it != store.sleep(it.date) }
                ?.let { store.saveSleep(it) }
        }
    }

    private suspend fun <T> read(block: suspend () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        com.superfit.app.domain.AppLog.w("HealthSync", "Reading the health app failed", e)
        null
    }
}
