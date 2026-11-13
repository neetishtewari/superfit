package com.superfit.app.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.superfit.app.domain.ChatEntry
import com.superfit.app.domain.CoachNote
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CloudBackupRestoreTest {

    /** Counts collection downloads so tests can tell whether a restore happened. */
    private class CountingStore : CloudStore {
        val docs = mutableMapOf<String, Map<String, Any>>()
        val listed = mutableListOf<String>()
        override suspend fun get(path: String) = docs[path]
        override suspend fun list(collectionPath: String): List<CloudDoc> {
            listed += collectionPath.substringAfterLast('/')
            return docs.filterKeys {
                it.startsWith("$collectionPath/") && !it.removePrefix("$collectionPath/").contains('/')
            }.map { (path, data) -> CloudDoc(path.substringAfterLast('/'), data) }
        }
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

    private val db = Room.inMemoryDatabaseBuilder<SuperfitDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()
    private val store = CountingStore()
    private val backup = CloudBackup(store, { "u1" }, { db }, NoLocal)

    private val profile = UserProfileEntity(age = 35, heightCm = 170.0, weightKg = 72.0, isMale = true, startDateTimestamp = 1L)
    private fun meal(id: String, time: Long) =
        NutritionEntryEntity(cloudId = id, foodText = "dal $id", calories = 200.0, proteinG = 10.0, carbsG = 30.0, fatG = 4.0, timestamp = time)

    @AfterTest
    fun close() = db.close()

    @Test
    fun anEmptyPhoneGetsTheWholeBackup() = runTest {
        backup.uploadProfile(profile)
        backup.uploadNutrition(meal("m1", 1_000L))

        assertTrue(backup.restoreIfEmpty())

        assertEquals(72.0, db.profileDao().getProfile()?.weightKg)
        assertEquals(listOf("m1"), db.nutritionDao().getAllEntries().map { it.cloudId })
    }

    @Test
    fun aPhoneThatHasItsDataDownloadsNothing() = runTest {
        db.profileDao().insertProfile(profile)
        backup.uploadNutrition(meal("m1", 1_000L))

        assertFalse(backup.restoreIfEmpty())

        assertTrue(store.listed.isEmpty())
        assertTrue(db.nutritionDao().getAllEntries().isEmpty())
    }

    @Test
    fun entriesFromAnotherPhoneArriveWithoutTouchingTheProfileOrSteps() = runTest {
        db.profileDao().insertProfile(profile)
        db.nutritionDao().insertEntriesIfMissing(listOf(meal("m1", 1_000L)))
        // Another phone on the account logged a meal and has an older weight in its profile.
        store.docs["users/u1"] = profile.copy(weightKg = 90.0).toMap()
        backup.uploadNutrition(meal("m2", 2_000L))

        backup.syncEntriesDown()

        assertEquals(setOf("m1", "m2"), db.nutritionDao().getAllEntries().map { it.cloudId }.toSet())
        assertEquals(72.0, db.profileDao().getProfile()?.weightKg)
        assertEquals(setOf("nutrition", "workouts", "weights"), store.listed.toSet())
    }

    @Test
    fun refreshIsDueOncePerDay() {
        val day = CloudBackup.ENTRIES_REFRESH_MS
        assertTrue(CloudBackup.isEntriesRefreshDue(0L, 5_000L))
        assertFalse(CloudBackup.isEntriesRefreshDue(1_000L, 1_000L + day - 1))
        assertTrue(CloudBackup.isEntriesRefreshDue(1_000L, 1_000L + day))
        assertTrue(CloudBackup.isEntriesRefreshDue(10_000L, 5_000L))
    }
}
