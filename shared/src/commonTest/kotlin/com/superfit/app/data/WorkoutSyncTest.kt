package com.superfit.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class WorkoutSyncTest {
    private val day = 24L * 3600 * 1000
    private val now = 200L * day

    private class FakeDao : ImportedWorkoutDao {
        val rows = mutableMapOf<String, ImportedWorkoutEntity>()
        override suspend fun upsertAll(workouts: List<ImportedWorkoutEntity>) = workouts.forEach { rows[it.id] = it }
        override fun getBetweenFlow(from: Long, to: Long): Flow<List<ImportedWorkoutEntity>> = flowOf(emptyList())
        override suspend fun getBetween(from: Long, to: Long) = rows.values.filter { it.startTime in from..to }
        override suspend fun getAll() = rows.values.toList()
        override fun countFlow(): Flow<Int> = flowOf(rows.size)
        override fun getAllFlow(): Flow<List<ImportedWorkoutEntity>> = flowOf(rows.values.toList())
        override suspend fun deleteByIds(ids: List<String>) = ids.forEach { rows.remove(it) }
        override suspend fun deleteAll() = rows.clear()
    }

    private class FakeSource(val workouts: List<HealthWorkout>?) : HealthSource {
        var askedFrom = 0L
        override fun isAvailable() = true
        override suspend fun requestAccess() = true
        override suspend fun readActivity(date: LocalDate): ActivityTelemetryEntity? = null
        override suspend fun readDistanceMeters(date: LocalDate): Double? = null
        override suspend fun readSleep(date: LocalDate): SleepTelemetryEntity? = null
        override suspend fun readWorkouts(fromMs: Long, toMs: Long, bmr: Double): List<HealthWorkout>? {
            askedFrom = fromMs
            return workouts
        }
    }

    private fun run(id: String, daysAgo: Long, source: String = "com.apple.health.watch", name: String = "Apple Watch") = HealthWorkout(
        ImportedWorkoutEntity(id, source, 56, now - daysAgo * day, now - daysAgo * day + 1_800_000, 5000.0, 400.0, 150, true),
        name
    )

    private fun prefs(enabled: Boolean = true) = WorkoutImportPrefs(MemoryPrefs()).apply { this.enabled = enabled }

    @Test
    fun theFirstImportGoesBackNinetyDaysThenAWeek() = runTest {
        val p = prefs()
        val source = FakeSource(listOf(run("a", 3)))
        val names = HealthSourceNames(MemoryPrefs())
        WorkoutSync.import(source, FakeDao(), p, names, 1700.0, now)
        assertEquals(now - 90 * day, source.askedFrom)

        WorkoutSync.import(source, FakeDao(), p, names, 1700.0, now)
        assertEquals(now - 7 * day, source.askedFrom)
    }

    @Test
    fun workoutsDeletedInAppleHealthGoHereToo() = runTest {
        val dao = FakeDao()
        val p = prefs()
        val names = HealthSourceNames(MemoryPrefs())
        WorkoutSync.import(FakeSource(listOf(run("a", 2), run("b", 3))), dao, p, names, 1700.0, now)
        WorkoutSync.import(FakeSource(listOf(run("a", 2))), dao, p, names, 1700.0, now)

        assertEquals(setOf("a"), dao.rows.keys)
    }

    @Test
    fun aFailedReadKeepsWhatWasThere() = runTest {
        val dao = FakeDao()
        val p = prefs()
        val names = HealthSourceNames(MemoryPrefs())
        WorkoutSync.import(FakeSource(listOf(run("a", 2))), dao, p, names, 1700.0, now)

        assertNull(WorkoutSync.import(FakeSource(null), dao, p, names, 1700.0, now))
        assertEquals(setOf("a"), dao.rows.keys)
    }

    @Test
    fun nothingIsReadWhileTheImportIsOff() = runTest {
        val dao = FakeDao()
        assertNull(WorkoutSync.import(FakeSource(listOf(run("a", 2))), dao, prefs(enabled = false), HealthSourceNames(MemoryPrefs()), 1700.0, now))
        assertEquals(0, dao.rows.size)
    }

    @Test
    fun eachWorkoutIsNamedAfterTheAppThatRecordedIt() = runTest {
        val p = prefs()
        val names = HealthSourceNames(MemoryPrefs())
        val dao = FakeDao()
        WorkoutSync.import(FakeSource(listOf(run("a", 2), run("b", 9, "com.strava", "Strava"))), dao, p, names, 1700.0, now)

        val views = WorkoutSync.views(dao.rows.values.toList(), p, names, 1700.0)
        assertEquals(listOf("Apple Watch", "Strava"), views.map { it.sourceName })
        assertEquals("another app", names.label("com.unknown"))
    }
}
