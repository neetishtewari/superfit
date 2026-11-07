package com.superfit.app.data

import com.superfit.app.domain.ChatEntry
import com.superfit.app.domain.CoachNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CloudBackupTest {

    private class FakeStore : CloudStore {
        val docs = mutableMapOf<String, Map<String, Any>>()
        var failWrites = false

        override suspend fun get(path: String) = docs[path]
        override suspend fun list(collectionPath: String) = docs.filterKeys {
            it.startsWith("$collectionPath/") && !it.removePrefix("$collectionPath/").contains('/')
        }.map { (path, data) -> CloudDoc(path.substringAfterLast('/'), data) }
        override suspend fun set(path: String, data: Map<String, Any>) {
            if (failWrites) error("offline")
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

    private fun backup(store: CloudStore, uid: String?) =
        CloudBackup(store, { uid }, { error("database not needed here") }, NoLocal)

    private val meal = NutritionEntryEntity(cloudId = "m1", foodText = "dal", calories = 200.0, proteinG = 10.0, carbsG = 30.0, fatG = 4.0, timestamp = 1L)

    @Test
    fun savesEachRowUnderTheSignedInUsersOwnPath() = runTest {
        val store = FakeStore()
        val b = backup(store, "u1")
        b.uploadNutrition(meal)
        b.uploadHabitEntry(HabitEntryEntity("2026-11-07", waterMl = 500))
        b.uploadStreakState(StreakStateEntity(currentStreak = 3))
        b.uploadCoachChat("[]")
        assertEquals(
            setOf("users/u1/nutrition/m1", "users/u1/habits/2026-11-07", "users/u1/streak/current", "users/u1/coach/chat"),
            store.docs.keys
        )
        b.deleteNutrition(meal)
        assertFalse("users/u1/nutrition/m1" in store.docs)
    }

    @Test
    fun signedOutWritesNothing() = runTest {
        val store = FakeStore()
        backup(store, null).uploadNutrition(meal)
        assertTrue(store.docs.isEmpty())
    }

    @Test
    fun aFailedWriteDoesNotThrow() = runTest {
        val store = FakeStore().apply { failWrites = true }
        backup(store, "u1").uploadNutrition(meal)
        assertTrue(store.docs.isEmpty())
    }

    @Test
    fun clearingDeletesOnlyThisUsersData() = runTest {
        val store = FakeStore()
        store.docs["users/u1"] = mapOf("age" to 30)
        CloudBackup.USER_COLLECTIONS.forEach { store.docs["users/u1/$it/x"] = mapOf("a" to 1) }
        store.docs["users/u2/nutrition/keep"] = mapOf("a" to 1)
        assertTrue(backup(store, "u1").clearAllCloudData())
        assertEquals(setOf("users/u2/nutrition/keep"), store.docs.keys)
    }
}
