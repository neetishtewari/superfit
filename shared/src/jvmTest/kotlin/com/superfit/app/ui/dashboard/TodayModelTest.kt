package com.superfit.app.ui.dashboard

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
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
import com.superfit.app.domain.ChatEntry
import com.superfit.app.domain.CoachNote
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

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
}
