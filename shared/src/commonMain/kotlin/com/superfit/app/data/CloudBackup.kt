package com.superfit.app.data

import com.superfit.app.domain.AppLog
import com.superfit.app.domain.ChatEntry
import com.superfit.app.domain.CoachChat
import com.superfit.app.domain.CoachNote
import com.superfit.app.domain.CoachNotes
import com.superfit.app.domain.Now
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What the backup also covers that lives outside the database, kept by each app its own way
 * (Android: files and SharedPreferences for the signed-in user).
 */
interface CloudBackupLocal {
    fun loadChat(): List<ChatEntry>
    fun saveChat(entries: List<ChatEntry>)
    fun hasNotes(): Boolean
    fun loadNotes(): List<CoachNote>
    fun saveNotes(notes: List<CoachNote>)

    /** Puts back the choices for workouts from other apps (Android's Health Connect import). */
    fun restoreWorkoutImportSettings(data: Map<String, Any>)

    fun isEntryBackupRepaired(): Boolean
    fun markEntryBackupRepaired()
}

/**
 * Backs the signed-in user's data up to the cloud and brings it back, the same way on Android
 * and iPhone. Everything lives under users/{uid}; [uid] is the signed-in user (null when signed
 * out, and then nothing is read or written).
 */
class CloudBackup(
    private val store: CloudStore,
    private val uid: () -> String?,
    private val database: () -> SuperfitDatabase,
    private val local: CloudBackupLocal
) {
    private val tag = "CloudBackup"

    private fun user(id: String) = "users/$id"

    /** Uploads the entire local database. */
    suspend fun syncAllUp() {
        val userId = uid() ?: return
        AppLog.d(tag, "Syncing local database up for user: $userId")
        val db = database()
        db.profileDao().getProfile()?.let { uploadProfile(it) }
        db.telemetryDao().getAllActivity().forEach { uploadActivity(it) }
        db.telemetryDao().getAllSleep().forEach { uploadSleep(it) }
        db.nutritionDao().getAllEntries().forEach { uploadNutrition(it) }
        db.workoutDao().getAllEntries().forEach { uploadWorkout(it) }
        db.weightDao().getAllWeightEntries().forEach { uploadWeight(it) }
        db.streakDao().getStreakState()?.let { uploadStreakState(it) }
        db.habitDao().getAllHabitEntries().forEach { uploadHabitEntry(it) }
        val chat = local.loadChat()
        if (chat.isNotEmpty()) uploadCoachChat(CoachChat.encode(chat))
        if (local.hasNotes()) uploadCoachNotes(CoachNotes.encode(local.loadNotes()))
    }

    /** Downloads everything and merges it into the local database. */
    suspend fun syncAllDown() {
        val userId = uid() ?: return
        AppLog.d(tag, "Syncing cloud down to the local database for user: $userId")
        val db = database()
        val base = user(userId)
        try {
            store.get(base)?.let { db.profileDao().insertProfile(it.toUserProfileEntity()) }

            val activities = store.list("$base/activity").map { it.data.toActivityTelemetry() }
            if (activities.isNotEmpty()) db.telemetryDao().insertActivities(activities)

            val sleeps = store.list("$base/sleep").map { it.data.toSleepTelemetry() }
            if (sleeps.isNotEmpty()) db.telemetryDao().insertSleeps(sleeps)

            // Merge only: add what the phone is missing and never delete or overwrite what it has.
            // Restored entries get a new local number and keep their cloud id, which the insert
            // uses to skip entries the phone already has.
            // The cloud copy can be incomplete (older versions backed up just the latest entry),
            // so treating it as the full history would throw away the user's own log.
            val entries = store.list("$base/nutrition").map { it.data.toNutritionEntry(it.id) }
            val localNutritionTimes = db.nutritionDao().getAllEntries().map { it.timestamp }.toSet()
            db.nutritionDao().insertEntriesIfMissing(
                withoutLegacyDuplicates(entries, localNutritionTimes, { it.cloudId }, { it.timestamp })
            )

            val workouts = store.list("$base/workouts").map { it.data.toWorkoutEntry(it.id) }
            val localWorkoutTimes = db.workoutDao().getAllEntries().map { it.timestamp }.toSet()
            db.workoutDao().insertEntriesIfMissing(
                withoutLegacyDuplicates(workouts, localWorkoutTimes, { it.cloudId }, { it.timestamp })
            )

            val weights = store.list("$base/weights").map { it.data.toWeightEntry(it.id) }
            val localWeightTimes = db.weightDao().getAllWeightEntries().map { it.timestamp }.toSet()
            db.weightDao().insertWeightsIfMissing(
                withoutLegacyDuplicates(weights, localWeightTimes, { it.cloudId }, { it.timestamp })
            )

            store.get("$base/streak/current")?.let { db.streakDao().insertStreakState(it.toStreakState()) }

            // Habits are stored one per date, so the cloud copy of a day replaces that day only.
            val habits = store.list("$base/habits").map { it.data.toHabitEntry() }
            if (habits.isNotEmpty()) db.habitDao().insertHabitEntries(habits)

            // Choices for workouts from other apps (the workouts themselves come from the health app)
            store.get("$base/settings/$WORKOUT_IMPORT_DOC")?.let { local.restoreWorkoutImportSettings(it) }

            // Coach chat and notes. The chat is merged; notes are only restored onto a phone that
            // has none, so a note the user deleted here doesn't come back.
            restoreCoach(base)

            AppLog.d(tag, "Sync down complete.")
        } catch (e: Exception) {
            AppLog.w(tag, "Error syncing data down: ${e.message}", e)
        }
    }

    private suspend fun restoreCoach(base: String) {
        val chatJson = store.get("$base/$COACH_COLLECTION/$COACH_CHAT_DOC")?.get(COACH_JSON_FIELD) as? String
        if (!chatJson.isNullOrBlank()) {
            local.saveChat(CoachChat.merge(local.loadChat(), CoachChat.decode(chatJson)))
        }
        if (!local.hasNotes()) {
            val notesJson = store.get("$base/$COACH_COLLECTION/$COACH_NOTES_DOC")?.get(COACH_JSON_FIELD) as? String
            if (!notesJson.isNullOrBlank()) local.saveNotes(CoachNotes.decode(notesJson))
        }
    }

    /** Backs up the coach chat, as encoded by [CoachChat.encode]. */
    suspend fun uploadCoachChat(json: String) = uploadCoachDoc(COACH_CHAT_DOC, json)

    /** Backs up the coach's notes, as encoded by [CoachNotes.encode]. */
    suspend fun uploadCoachNotes(json: String) = uploadCoachDoc(COACH_NOTES_DOC, json)

    private suspend fun uploadCoachDoc(doc: String, json: String) {
        val userId = uid() ?: return
        write("coach $doc") {
            store.set("${user(userId)}/$COACH_COLLECTION/$doc", mapOf(COACH_JSON_FIELD to json, "updatedAt" to Now.millis()))
        }
    }

    suspend fun deleteCoachChat() {
        val userId = uid() ?: return
        write("deleting coach chat") { store.delete("${user(userId)}/$COACH_COLLECTION/$COACH_CHAT_DOC") }
    }

    /**
     * Older versions uploaded every new meal, workout and weight before the database gave it an
     * id, so they all landed in the cloud document "0" and only the latest one survived. Uploads
     * every local entry under its real id so the backup holds the full history. The stale "0"
     * documents are left alone; [syncAllDown] skips them when the entry they hold is already
     * present. Runs once per user and is retried on the next launch if any upload fails.
     */
    suspend fun repairEntryBackupsOnce() {
        val userId = uid() ?: return
        if (local.isEntryBackupRepaired()) return
        try {
            val base = user(userId)
            val db = database()
            val writes = mutableListOf<Pair<String, Map<String, Any>>>()
            db.nutritionDao().getAllEntries().filter { it.cloudId != CloudIds.LEGACY_UNNUMBERED }.forEach {
                writes += "$base/nutrition/${it.cloudId}" to it.toMap()
            }
            db.workoutDao().getAllEntries().filter { it.cloudId != CloudIds.LEGACY_UNNUMBERED }.forEach {
                writes += "$base/workouts/${it.cloudId}" to it.toMap()
            }
            db.weightDao().getAllWeightEntries().filter { it.cloudId != CloudIds.LEGACY_UNNUMBERED }.forEach {
                writes += "$base/weights/${it.cloudId}" to it.toMap()
            }
            // Firestore allows at most 500 writes per batch.
            writes.chunked(400).forEach { store.setAll(it) }
            local.markEntryBackupRepaired()
            AppLog.d(tag, "Backed up ${writes.size} meals, workouts and weights under their own ids.")
        } catch (e: Exception) {
            AppLog.w(tag, "Error repairing entry backups: ${e.message}", e)
        }
    }

    // --- One row at a time ---

    suspend fun uploadProfile(profile: UserProfileEntity) =
        put("profile") { store.set(user(it), profile.toMap()) }

    suspend fun uploadActivity(activity: ActivityTelemetryEntity) =
        put("activity ${activity.date}") { store.set("${user(it)}/activity/${activity.date}", activity.toMap()) }

    suspend fun uploadSleep(sleep: SleepTelemetryEntity) =
        put("sleep ${sleep.date}") { store.set("${user(it)}/sleep/${sleep.date}", sleep.toMap()) }

    suspend fun uploadNutrition(entry: NutritionEntryEntity) =
        put("meal ${entry.cloudId}") { store.set("${user(it)}/nutrition/${entry.cloudId}", entry.toMap()) }

    suspend fun deleteNutrition(entry: NutritionEntryEntity) =
        put("deleting meal ${entry.cloudId}") { store.delete("${user(it)}/nutrition/${entry.cloudId}") }

    suspend fun uploadWorkout(entry: WorkoutEntryEntity) =
        put("workout ${entry.cloudId}") { store.set("${user(it)}/workouts/${entry.cloudId}", entry.toMap()) }

    suspend fun deleteWorkout(entry: WorkoutEntryEntity) =
        put("deleting workout ${entry.cloudId}") { store.delete("${user(it)}/workouts/${entry.cloudId}") }

    suspend fun uploadWeight(entry: WeightEntryEntity) =
        put("weight ${entry.cloudId}") { store.set("${user(it)}/weights/${entry.cloudId}", entry.toMap()) }

    suspend fun deleteWeight(entry: WeightEntryEntity) =
        put("deleting weight ${entry.cloudId}") { store.delete("${user(it)}/weights/${entry.cloudId}") }

    suspend fun uploadStreakState(state: StreakStateEntity) =
        put("streak") { store.set("${user(it)}/streak/current", state.toMap()) }

    suspend fun uploadWorkoutImportSettings(settings: Map<String, Any>) =
        put("workout import settings") { store.set("${user(it)}/settings/$WORKOUT_IMPORT_DOC", settings) }

    suspend fun uploadHabitEntry(entry: HabitEntryEntity) =
        put("habits ${entry.date}") { store.set("${user(it)}/habits/${entry.date}", entry.toMap()) }

    private suspend fun put(what: String, block: suspend (String) -> Unit) {
        val userId = uid() ?: return
        write(what) { block(userId) }
    }

    private suspend fun write(what: String, block: suspend () -> Unit) {
        try {
            block()
            AppLog.d(tag, "Saved $what.")
        } catch (e: Exception) {
            AppLog.w(tag, "Error saving $what: ${e.message}")
        }
    }

    /** Deletes the signed-in user's cloud data. Returns false if it couldn't finish (offline or an error). */
    suspend fun clearAllCloudData(): Boolean {
        val userId = uid() ?: return true
        // A delete only completes once the server has it, so give up rather than hang when offline.
        return withTimeoutOrNull(30_000) { clearCloudData(user(userId)) } ?: false
    }

    private suspend fun clearCloudData(base: String): Boolean = try {
        store.delete(base)
        for (collection in USER_COLLECTIONS) {
            store.list("$base/$collection").forEach { store.delete("$base/$collection/${it.id}") }
        }
        try {
            withTimeoutOrNull(5_000) { store.waitForPendingWrites() }
        } catch (e: Exception) {
            AppLog.w(tag, "Timeout or error waiting for pending writes: ${e.message}")
        }
        AppLog.d(tag, "Cloud data wipe complete.")
        true
    } catch (e: Exception) {
        AppLog.w(tag, "Error clearing cloud data: ${e.message}", e)
        false
    }

    companion object {
        const val WORKOUT_IMPORT_DOC = "workoutImport"
        private const val COACH_COLLECTION = "coach"
        private const val COACH_CHAT_DOC = "chat"
        private const val COACH_NOTES_DOC = "notes"
        private const val COACH_JSON_FIELD = "json"

        /** Every collection under users/{uid}; account deletion clears them all. */
        val USER_COLLECTIONS = listOf(
            "activity", "sleep", "nutrition", "workouts", "weights", "streak", "habits", "settings", COACH_COLLECTION
        )
    }
}
