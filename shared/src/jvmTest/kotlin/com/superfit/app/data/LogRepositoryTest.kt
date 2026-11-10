package com.superfit.app.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.superfit.app.domain.ChatEntry
import com.superfit.app.domain.CoachNote
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

class LogRepositoryTest {

    private class MapStore : CloudStore {
        val docs = mutableMapOf<String, Map<String, Any>>()
        override suspend fun get(path: String) = docs[path]
        override suspend fun list(collectionPath: String) = docs.filterKeys {
            it.startsWith("$collectionPath/") && !it.removePrefix("$collectionPath/").contains('/')
        }.map { (path, data) -> CloudDoc(path.substringAfterLast('/'), data) }
        override suspend fun set(path: String, data: Map<String, Any>) {
            docs[path] = data
        }
        override suspend fun setAll(writes: List<Pair<String, Map<String, Any>>>) = writes.forEach { set(it.first, it.second) }
        override suspend fun delete(path: String) {
            docs.remove(path)
        }
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

    private val zone = TimeZone.of("Asia/Kolkata")
    private val db = Room.inMemoryDatabaseBuilder<SuperfitDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()
    private val store = MapStore()
    private var mealsChanged = 0
    private val log = LogRepository(
        database = { db },
        backup = CloudBackup(store, { "u1" }, { db }, NoLocal),
        mealsChanged = { mealsChanged++ },
        isImportedWeight = { it.note.startsWith("from ") },
        zone = { zone }
    )

    @AfterTest
    fun close() = db.close()

    private fun at(date: LocalDate, hour: Int) = date.atStartOfDayIn(zone).toEpochMilliseconds() + hour * 3_600_000L

    private val day = LocalDate(2026, 11, 10)

    private fun meal(time: Long) =
        NutritionEntryEntity(foodText = "dal", calories = 200.0, proteinG = 10.0, carbsG = 30.0, fatG = 4.0, timestamp = time)

    @Test
    fun aNewMealIsSavedBackedUpAndShownOnItsDayOnly() = runTest {
        val m = meal(at(day, 13))
        log.addNutritionEntry(m)
        log.addNutritionEntry(meal(at(day, 25))) // 1 am the next day

        assertEquals(listOf("dal"), log.getNutritionEntriesForDay(day).first().map { it.foodText })
        assertTrue("users/u1/nutrition/${m.cloudId}" in store.docs)
        assertEquals(2, mealsChanged)

        val saved = log.getAllNutritionEntries().first { it.cloudId == m.cloudId }
        log.deleteNutritionEntry(saved)
        assertTrue("users/u1/nutrition/${m.cloudId}" !in store.docs)
        assertEquals(3, mealsChanged)
    }

    @Test
    fun aTypedWeighInBeatsTheScaleOnTheSameDay() = runTest {
        log.addImportedWeight(80.0, at(day, 7), "from FitDays")
        log.addImportedWeight(79.8, at(day, 9), "from FitDays")
        assertEquals(listOf(79.8), log.getAllWeightEntries().map { it.weightKg })

        // A weight the user typed that day stays; later scale readings are ignored.
        val typed = WeightEntryEntity(weightKg = 79.5, timestamp = at(day, 10))
        db.weightDao().insertWeight(typed)
        log.getAllWeightEntries().filter { it.note.startsWith("from ") }.forEach { log.deleteWeightEntry(it) }
        log.addImportedWeight(79.0, at(day, 20), "from FitDays")
        assertEquals(listOf(79.5), log.getAllWeightEntries().map { it.weightKg })
    }

    @Test
    fun waterAddsUpAndIsBackedUp() = runTest {
        log.addWater("2026-11-10", 250)
        log.addWater("2026-11-10", 500)
        assertEquals(750, log.getHabitEntry("2026-11-10")?.waterMl)
        assertEquals(750L, (store.docs["users/u1/habits/2026-11-10"]?.get("waterMl") as Number).toLong())
    }
}
