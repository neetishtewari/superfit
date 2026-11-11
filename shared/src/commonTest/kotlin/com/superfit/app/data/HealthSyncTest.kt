package com.superfit.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class HealthSyncTest {
    private val today = LocalDate(2026, 11, 12)

    private class FakeSource(
        val activity: Map<String, ActivityTelemetryEntity?> = emptyMap(),
        val sleep: Map<String, SleepTelemetryEntity?> = emptyMap(),
        val failSteps: Boolean = false
    ) : HealthSource {
        override fun isAvailable() = true
        override suspend fun requestAccess() = true
        override suspend fun readActivity(date: LocalDate): ActivityTelemetryEntity? {
            if (failSteps) error("Health data is locked")
            return activity[date.toString()]
        }
        override suspend fun readDistanceMeters(date: LocalDate): Double? = null
        override suspend fun readSleep(date: LocalDate) = sleep[date.toString()]
        override suspend fun readWorkouts(fromMs: Long, toMs: Long, bmr: Double): List<HealthWorkout>? = null
        override suspend fun readBody(sinceMs: Long): BodySamples? = null
    }

    private class FakeStore : HealthStore {
        val activity = mutableMapOf<String, ActivityTelemetryEntity>()
        val sleep = mutableMapOf<String, SleepTelemetryEntity>()
        var saves = 0
        override suspend fun activity(date: String) = activity[date]
        override suspend fun saveActivity(activity: ActivityTelemetryEntity) {
            saves++
            this.activity[activity.date] = activity
        }
        override suspend fun sleep(date: String) = sleep[date]
        override suspend fun saveSleep(sleep: SleepTelemetryEntity) {
            saves++
            this.sleep[sleep.date] = sleep
        }
    }

    private fun steps(date: String, steps: Int, kcal: Double = 0.0) = ActivityTelemetryEntity(date, steps, kcal)

    @Test
    fun todayAndYesterdayAreBothSaved() = runTest {
        val store = FakeStore()
        val night = SleepTelemetryEntity("2026-11-12", 25200, 5040, 87)
        HealthSync.sync(
            FakeSource(
                activity = mapOf("2026-11-12" to steps("2026-11-12", 4200, 180.0), "2026-11-11" to steps("2026-11-11", 9100)),
                sleep = mapOf("2026-11-12" to night)
            ),
            store,
            today
        )

        assertEquals(4200, store.activity["2026-11-12"]?.steps)
        assertEquals(9100, store.activity["2026-11-11"]?.steps)
        assertEquals(night, store.sleep["2026-11-12"])
    }

    @Test
    fun anEmptyDayNeverWipesStepsAlreadySaved() = runTest {
        val store = FakeStore().apply { activity["2026-11-12"] = steps("2026-11-12", 6000) }
        HealthSync.sync(FakeSource(activity = mapOf("2026-11-12" to steps("2026-11-12", 0))), store, today)

        assertEquals(6000, store.activity["2026-11-12"]?.steps)
        assertEquals(0, store.saves)
    }

    @Test
    fun aFailedReadKeepsWhatWasSaved() = runTest {
        val store = FakeStore().apply { activity["2026-11-12"] = steps("2026-11-12", 6000) }
        HealthSync.sync(FakeSource(failSteps = true), store, today)

        assertEquals(6000, store.activity["2026-11-12"]?.steps)
    }

    @Test
    fun anUnchangedDayIsNotSavedAgain() = runTest {
        val store = FakeStore().apply { activity["2026-11-12"] = steps("2026-11-12", 6000) }
        HealthSync.sync(FakeSource(activity = mapOf("2026-11-12" to steps("2026-11-12", 6000))), store, today)

        assertEquals(0, store.saves)
    }
}
