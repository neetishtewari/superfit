package com.superfit.app.data

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.superfit.app.domain.ChatEntry
import com.superfit.app.domain.CoachNote
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Android's side of the cloud backup. What is backed up and how lives in the shared
 * [CloudBackup]; this class plugs in Firestore's Android SDK, the signed-in Firebase user and
 * the phone's own files, and deletes the Firebase account.
 */
class FirebaseSyncManager(private val context: Context) {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    private val backup = CloudBackup(
        store = AndroidCloudStore(FirebaseFirestore.getInstance()),
        uid = { auth.currentUser?.uid },
        database = { SuperfitDatabase.getDatabase(context) },
        local = AndroidBackupLocal(context)
    )

    suspend fun syncAllUp() = backup.syncAllUp()
    suspend fun syncAllDown() = backup.syncAllDown()
    suspend fun uploadCoachChat(json: String) = backup.uploadCoachChat(json)
    suspend fun uploadCoachNotes(json: String) = backup.uploadCoachNotes(json)
    suspend fun deleteCoachChat() = backup.deleteCoachChat()
    suspend fun repairEntryBackupsOnce() = backup.repairEntryBackupsOnce()
    suspend fun uploadProfile(profile: UserProfileEntity) = backup.uploadProfile(profile)
    suspend fun uploadActivity(activity: ActivityTelemetryEntity) = backup.uploadActivity(activity)
    suspend fun uploadSleep(sleep: SleepTelemetryEntity) = backup.uploadSleep(sleep)
    suspend fun uploadNutrition(entry: NutritionEntryEntity) = backup.uploadNutrition(entry)
    suspend fun deleteNutrition(entry: NutritionEntryEntity) = backup.deleteNutrition(entry)
    suspend fun uploadWorkout(entry: WorkoutEntryEntity) = backup.uploadWorkout(entry)
    suspend fun deleteWorkout(entry: WorkoutEntryEntity) = backup.deleteWorkout(entry)
    suspend fun uploadWeight(entry: WeightEntryEntity) = backup.uploadWeight(entry)
    suspend fun deleteWeight(entry: WeightEntryEntity) = backup.deleteWeight(entry)
    suspend fun uploadStreakState(state: StreakStateEntity) = backup.uploadStreakState(state)
    suspend fun uploadWorkoutImportSettings(settings: Map<String, Any>) = backup.uploadWorkoutImportSettings(settings)
    suspend fun uploadHabitEntry(entry: HabitEntryEntity) = backup.uploadHabitEntry(entry)

    /** Deletes the signed-in user's cloud data. Returns false if it couldn't finish (offline or an error). */
    suspend fun clearAllCloudData(): Boolean = backup.clearAllCloudData()

    /**
     * Deletes the Firebase account itself. Call after [clearAllCloudData], since the data can't be
     * deleted once the account is gone. Firebase refuses if the user last signed in too long ago.
     */
    suspend fun deleteAccount(): AccountDeletion {
        val user = auth.currentUser ?: return AccountDeletion.DELETED
        return try {
            user.delete().await()
            AccountDeletion.DELETED
        } catch (e: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
            AccountDeletion.NEEDS_RECENT_SIGN_IN
        } catch (e: Exception) {
            Log.e("FirebaseSyncManager", "Error deleting account: ${e.message}", e)
            AccountDeletion.FAILED
        }
    }
}

enum class AccountDeletion { DELETED, NEEDS_RECENT_SIGN_IN, FAILED }

/** Turns a Play services Task into a suspend call. */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(task.exception ?: RuntimeException("Task failed"))
        }
    }
}

/** The shared [CloudStore] on Firestore's Android SDK. Paths are Firestore document paths. */
private class AndroidCloudStore(private val firestore: FirebaseFirestore) : CloudStore {
    override suspend fun get(path: String): Map<String, Any>? {
        val doc = firestore.document(path).get().await()
        return if (doc.exists()) doc.data else null
    }

    override suspend fun list(collectionPath: String): List<CloudDoc> =
        firestore.collection(collectionPath).get().await().documents.mapNotNull { doc -> doc.data?.let { CloudDoc(doc.id, it) } }

    override suspend fun set(path: String, data: Map<String, Any>) {
        firestore.document(path).set(data).await()
    }

    override suspend fun setAll(writes: List<Pair<String, Map<String, Any>>>) {
        val batch = firestore.batch()
        writes.forEach { (path, data) -> batch.set(firestore.document(path), data) }
        batch.commit().await()
    }

    override suspend fun delete(path: String) {
        firestore.document(path).delete().await()
    }

    override suspend fun waitForPendingWrites() {
        firestore.waitForPendingWrites().await()
    }
}

/** The coach chat, notes and settings the backup covers, from this phone's per-user storage. */
private class AndroidBackupLocal(private val context: Context) : CloudBackupLocal {
    override fun loadChat(): List<ChatEntry> = CoachChatStore.loadChat(context)
    override fun saveChat(entries: List<ChatEntry>) {
        CoachChatStore.saveChat(context, entries)
    }
    override fun hasNotes(): Boolean = CoachChatStore.hasNotes(context)
    override fun loadNotes(): List<CoachNote> = CoachChatStore.loadNotes(context)
    override fun saveNotes(notes: List<CoachNote>) {
        CoachChatStore.saveNotes(context, notes)
    }
    override fun restoreWorkoutImportSettings(data: Map<String, Any>) =
        WorkoutImportPrefs(getUserSharedPrefs(context)).restore(data)
    override fun isEntryBackupRepaired(): Boolean =
        getUserSharedPrefs(context).getBoolean(PREF_ENTRY_BACKUP_REPAIRED, false)
    override fun markEntryBackupRepaired() =
        getUserSharedPrefs(context).edit().putBoolean(PREF_ENTRY_BACKUP_REPAIRED, true).apply()

    private companion object {
        const val PREF_ENTRY_BACKUP_REPAIRED = "entry_backup_repaired_v1"
    }
}
