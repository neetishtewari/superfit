package com.superfit.app.ui.dashboard

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.superfit.app.data.ActivityTelemetryEntity
import com.superfit.app.data.CloudBackup
import com.superfit.app.data.CloudBackupLocal
import com.superfit.app.data.CloudDoc
import com.superfit.app.data.CloudStore
import com.superfit.app.data.LogRepository
import com.superfit.app.data.MemoryPrefs
import com.superfit.app.data.Now
import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.SuperfitDatabase
import com.superfit.app.data.UserProfileEntity
import com.superfit.app.data.WorkoutEntryEntity
import com.superfit.app.domain.ChatEntry
import com.superfit.app.domain.CoachNote
import com.superfit.app.domain.LogKind
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

class TodayModelTest {

    private object NoStore : CloudStore {
        override suspend fun get(path: String): Map<String, Any>? = null
        override suspend fun list(collectionPath: String) = emptyList<CloudDoc>()
        override suspend fun set(path: String, data: Map<String, Any>) = Unit
        override suspend fun setAll(writes: List<Pair<String, Map<String, Any>>>) = Unit
        override suspend fun delete(path: String) = Unit
        override suspend fun waitForPendingWrites() = Unit
    }

    private object NoLocal : CloudBackupLocal {
        override fun loadChat() = emptyList<ChatEntry>()
        override fun saveChat(entries: List<ChatEntry>) = Unit
        override fun hasNotes() = false
        override fun loadNotes() = emptyList<CoachNote>()
        override fun saveNotes(notes: List<CoachNote>) = Unit
        override fun restoreWorkoutImportSettings(data: Map<String, Any>) = Unit
        override fun isEntryBackupRepaired() = true
        override fun markEntryBackupRepaired() = Unit
    }

    private val db = Room.inMemoryDatabaseBuilder<SuperfitDatabase>().setDriver(BundledSQLiteDriver()).build()
    private val log = LogRepository({ db }, CloudBackup(NoStore, { "u1" }, { db }, NoLocal))

    @AfterTest
    fun close() = db.close()

    @Test
    fun todayShowsTheMealsLoggedTodayOnceThereIsAProfile() = runTest {
        val model = TodayModel(log, MemoryPrefs(), backgroundScope)
        assertIs<DashboardUiState.NotInitialized>(model.state.first { it !is DashboardUiState.Loading })

        log.saveProfile(UserProfileEntity(age = 35, heightCm = 175.0, weightKg = 80.0, isMale = true))
        log.addNutritionEntry(NutritionEntryEntity(foodText = "dal", calories = 300.0, proteinG = 12.0, carbsG = 40.0, fatG = 8.0, timestamp = Now.millis()))

        val shown = model.state.first { it is DashboardUiState.Success && it.nutritionList.isNotEmpty() } as DashboardUiState.Success
        assertEquals(300.0, shown.caloriesEaten)
        assertEquals(listOf("dal"), shown.nutritionList.map { it.foodText })

        model.deleteMeal(shown.nutritionList.single())
        val after = model.state.first { it is DashboardUiState.Success && it.nutritionList.isEmpty() } as DashboardUiState.Success
        assertEquals(0.0, after.caloriesEaten)
    }

    @Test
    fun activityShowsTodaysWorkoutsAndTheUsualForThisWeekday() = runTest {
        val model = TodayModel(log, MemoryPrefs(), backgroundScope)
        val today = Now.today()
        log.saveProfile(UserProfileEntity(age = 35, heightCm = 175.0, weightKg = 80.0, isMale = true))
        log.saveActivity(ActivityTelemetryEntity(date = today.minus(1, DateTimeUnit.WEEK).toString(), steps = 9000, activeCalories = 300.0))
        log.saveActivity(ActivityTelemetryEntity(date = today.minus(2, DateTimeUnit.WEEK).toString(), steps = 7000, activeCalories = 250.0))
        log.saveActivity(ActivityTelemetryEntity(date = today.toString(), steps = 4000, activeCalories = 100.0))
        log.addWorkoutEntry(WorkoutEntryEntity(description = "20 push-ups", caloriesBurned = 15.0, workoutType = "Strength", setsCount = 1, repsCount = 20, timestamp = Now.millis()))

        val usual = model.usualDay.first { it != null }!!
        assertEquals(8000, usual.steps)
        val shown = model.state.first { it is DashboardUiState.Success && it.workoutList.isNotEmpty() } as DashboardUiState.Success
        val vs = model.vsUsual(shown, usual)
        assertEquals(4000, vs.steps)
        assertEquals(15.0, vs.workoutKcal)
        assertTrue(vs.distanceEstimated)

        model.deleteWorkout(shown.workoutList.single())
        model.state.first { it is DashboardUiState.Success && it.workoutList.isEmpty() }
    }

    @Test
    fun saidAgainAnExactRepeatOfAPastMealIsLoggedWithoutTheAi() = runTest {
        val model = TodayModel(log, MemoryPrefs(), backgroundScope)
        log.saveProfile(UserProfileEntity(age = 35, heightCm = 175.0, weightKg = 80.0, isMale = true))
        log.addNutritionEntry(NutritionEntryEntity(foodText = "2 roti and dal", calories = 420.0, proteinG = 15.0, carbsG = 60.0, fatG = 10.0, timestamp = Now.millis() - 86_400_000L))

        model.smartLog("2 roti and dal", kind = LogKind.MEAL)

        assertIs<ParsingState.Success>(model.parsing.first { it is ParsingState.Success || it is ParsingState.Error })
        val today = model.state.first { it is DashboardUiState.Success && it.nutritionList.isNotEmpty() } as DashboardUiState.Success
        assertEquals(420.0, today.caloriesEaten)
    }
}
