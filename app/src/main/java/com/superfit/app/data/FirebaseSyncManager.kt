package com.superfit.app.data

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseSyncManager(private val context: Context) {
    private val database: SuperfitDatabase get() = SuperfitDatabase.getDatabase(context)

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private val tag = "FirebaseSyncManager"

    companion object {
        // Sign-in and app start can both start a sync; running two at once would restore the
        // same cloud items twice.
        private val syncMutex = Mutex()
    }

    // Helper for converting Tasks into coroutines suspend functions
    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(task.exception ?: RuntimeException("Task failed"))
            }
        }
    }

    /**
     * Brings the phone and the cloud copy in line without deleting anything on either side:
     * items only in the cloud are copied onto the phone, items only on the phone are uploaded.
     * Safe to run on every launch and after every sign-in.
     */
    suspend fun syncAll() = syncMutex.withLock {
        val userId = auth.currentUser?.uid ?: return@withLock
        val userDoc = firestore.collection("users").document(userId)
        Log.d(tag, "Syncing local database with Firestore for user: $userId")

        // 1. Profile: the phone's copy wins; the cloud copy is only used to fill an empty phone
        try {
            val localProfile = database.profileDao().getProfile()
            if (localProfile != null) {
                uploadProfile(localProfile)
            } else {
                userDoc.get().await().data?.let { database.profileDao().insertProfile(it.toUserProfileEntity()) }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error syncing profile: ${e.message}", e)
        }

        // 2. Activity and sleep telemetry (one document per day)
        try {
            val localDates = database.telemetryDao().getAllActivity().map { it.date }.toSet()
            val cloud = userDoc.collection("activity").get().await().documents.mapNotNull { it.data?.toActivityTelemetry() }
            val missing = cloud.filter { it.date.isNotBlank() && it.date !in localDates }
            if (missing.isNotEmpty()) database.telemetryDao().insertActivities(missing)
        } catch (e: Exception) {
            Log.e(tag, "Error syncing activity: ${e.message}", e)
        }
        try {
            val localDates = database.telemetryDao().getAllSleep().map { it.date }.toSet()
            val cloud = userDoc.collection("sleep").get().await().documents.mapNotNull { it.data?.toSleepTelemetry() }
            val missing = cloud.filter { it.date.isNotBlank() && it.date !in localDates }
            if (missing.isNotEmpty()) database.telemetryDao().insertSleeps(missing)
        } catch (e: Exception) {
            Log.e(tag, "Error syncing sleep: ${e.message}", e)
        }

        // 3. Logged items: meals, workouts and weigh-ins
        syncLog(
            collection = userDoc.collection("nutrition"),
            local = database.nutritionDao().getAllEntries(),
            timestampOf = { it.timestamp },
            fromCloud = { it.toNutritionEntry().copy(id = 0L) },
            toCloud = { it.toMap() },
            insertLocal = { database.nutritionDao().insertEntries(it) }
        )
        syncLog(
            collection = userDoc.collection("workouts"),
            local = database.workoutDao().getAllEntries(),
            timestampOf = { it.timestamp },
            fromCloud = { it.toWorkoutEntry().copy(id = 0L) },
            toCloud = { it.toMap() },
            insertLocal = { database.workoutDao().insertEntries(it) }
        )
        syncLog(
            collection = userDoc.collection("weights"),
            local = database.weightDao().getAllWeightEntries(),
            timestampOf = { it.timestamp },
            fromCloud = { it.toWeightEntry().copy(id = 0L) },
            toCloud = { it.toMap() },
            insertLocal = { database.weightDao().insertWeights(it) }
        )

        // 4. Streak: the phone's copy wins; the cloud copy is only used to fill an empty phone
        try {
            val localStreak = database.streakDao().getStreakState()
            if (localStreak != null) {
                uploadStreakState(localStreak)
            } else {
                userDoc.collection("streak").document("current").get().await().data?.let {
                    database.streakDao().insertStreakState(it.toStreakState())
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error syncing streak: ${e.message}", e)
        }

        // 5. Habits (one document per day)
        try {
            val local = database.habitDao().getAllHabitEntries()
            val localDates = local.map { it.date }.toSet()
            val snapshot = userDoc.collection("habits").get().await()
            val cloudDates = snapshot.documents.map { it.id }.toSet()
            val missing = snapshot.documents.mapNotNull { it.data?.toHabitEntry() }
                .filter { it.date.isNotBlank() && it.date !in localDates }
            if (missing.isNotEmpty()) database.habitDao().insertHabitEntries(missing)
            local.filter { it.date !in cloudDates }.forEach { uploadHabitEntry(it) }
        } catch (e: Exception) {
            Log.e(tag, "Error syncing habits: ${e.message}", e)
        }

        Log.d(tag, "Sync complete.")
    }

    private suspend fun <T> syncLog(
        collection: CollectionReference,
        local: List<T>,
        timestampOf: (T) -> Long,
        fromCloud: (Map<String, Any>) -> T,
        toCloud: (T) -> Map<String, Any>,
        insertLocal: suspend (List<T>) -> Unit
    ) {
        try {
            val docs = collection.get().await().documents
            val cloudDocs = docs.mapNotNull { doc ->
                val timestamp = (doc.data?.get("timestamp") as? Number)?.toLong() ?: return@mapNotNull null
                CloudLogMerge.CloudDoc(doc.id, timestamp)
            }
            val plan = CloudLogMerge.plan(local.map(timestampOf), cloudDocs)

            val cloudByTimestamp = docs.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val timestamp = (data["timestamp"] as? Number)?.toLong() ?: return@mapNotNull null
                timestamp to data
            }.toMap()
            val restored = plan.restoreTimestamps.mapNotNull { cloudByTimestamp[it]?.let(fromCloud) }
            if (restored.isNotEmpty()) insertLocal(restored)

            val localByTimestamp = local.associateBy(timestampOf)
            val uploaded = mutableSetOf<Long>()
            for (timestamp in plan.uploadTimestamps) {
                val data = localByTimestamp[timestamp]?.let(toCloud) ?: cloudByTimestamp[timestamp] ?: continue
                try {
                    collection.document(CloudLogMerge.cloudKey(timestamp)).set(data).await()
                    uploaded += timestamp
                } catch (e: Exception) {
                    Log.e(tag, "Error uploading ${collection.id} item $timestamp: ${e.message}")
                }
            }

            // Old documents named by Room id are removed only once the same item is safely saved
            // under its new name.
            for (stale in plan.staleDocs) {
                if (stale.timestamp in uploaded || stale.timestamp !in plan.uploadTimestamps) {
                    collection.document(stale.id).delete().await()
                }
            }
            Log.d(tag, "${collection.id}: restored ${restored.size}, uploaded ${uploaded.size}, tidied ${plan.staleDocs.size}.")
        } catch (e: Exception) {
            Log.e(tag, "Error syncing ${collection.id}: ${e.message}", e)
        }
    }

    // --- Incremental Upload Helpers ---

    suspend fun uploadProfile(profile: UserProfileEntity) {
        val userId = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users").document(userId).set(profile.toMap()).await()
            Log.d(tag, "Profile uploaded to Firestore.")
        } catch (e: Exception) {
            Log.e(tag, "Error uploading profile: ${e.message}")
        }
    }

    suspend fun uploadActivity(activity: ActivityTelemetryEntity) {
        val userId = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users").document(userId)
                .collection("activity").document(activity.date).set(activity.toMap()).await()
            Log.d(tag, "Activity telemetry uploaded for ${activity.date}.")
        } catch (e: Exception) {
            Log.e(tag, "Error uploading activity: ${e.message}")
        }
    }

    suspend fun uploadSleep(sleep: SleepTelemetryEntity) {
        val userId = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users").document(userId)
                .collection("sleep").document(sleep.date).set(sleep.toMap()).await()
            Log.d(tag, "Sleep telemetry uploaded for ${sleep.date}.")
        } catch (e: Exception) {
            Log.e(tag, "Error uploading sleep: ${e.message}")
        }
    }

    suspend fun uploadNutrition(entry: NutritionEntryEntity) {
        val userId = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users").document(userId)
                .collection("nutrition").document(CloudLogMerge.cloudKey(entry.timestamp)).set(entry.toMap()).await()
            Log.d(tag, "Nutrition entry uploaded for ${entry.timestamp}.")
        } catch (e: Exception) {
            Log.e(tag, "Error uploading nutrition: ${e.message}")
        }
    }

    suspend fun deleteNutrition(entry: NutritionEntryEntity) {
        val userId = auth.currentUser?.uid ?: return
        try {
            deleteLogItem(firestore.collection("users").document(userId).collection("nutrition"), entry.timestamp)
            Log.d(tag, "Nutrition entry deleted from Firestore for ${entry.timestamp}.")
        } catch (e: Exception) {
            Log.e(tag, "Error deleting nutrition: ${e.message}")
        }
    }

    suspend fun uploadWorkout(entry: WorkoutEntryEntity) {
        val userId = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users").document(userId)
                .collection("workouts").document(CloudLogMerge.cloudKey(entry.timestamp)).set(entry.toMap()).await()
            Log.d(tag, "Workout entry uploaded for ${entry.timestamp}.")
        } catch (e: Exception) {
            Log.e(tag, "Error uploading workout: ${e.message}")
        }
    }

    suspend fun deleteWorkout(entry: WorkoutEntryEntity) {
        val userId = auth.currentUser?.uid ?: return
        try {
            deleteLogItem(firestore.collection("users").document(userId).collection("workouts"), entry.timestamp)
            Log.d(tag, "Workout entry deleted from Firestore for ${entry.timestamp}.")
        } catch (e: Exception) {
            Log.e(tag, "Error deleting workout: ${e.message}")
        }
    }

    suspend fun uploadWeight(entry: WeightEntryEntity) {
        val userId = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users").document(userId)
                .collection("weights").document(CloudLogMerge.cloudKey(entry.timestamp)).set(entry.toMap()).await()
            Log.d(tag, "Weight entry uploaded for ${entry.timestamp}.")
        } catch (e: Exception) {
            Log.e(tag, "Error uploading weight: ${e.message}")
        }
    }

    suspend fun deleteWeight(entry: WeightEntryEntity) {
        val userId = auth.currentUser?.uid ?: return
        try {
            deleteLogItem(firestore.collection("users").document(userId).collection("weights"), entry.timestamp)
            Log.d(tag, "Weight entry deleted from Firestore for ${entry.timestamp}.")
        } catch (e: Exception) {
            Log.e(tag, "Error deleting weight: ${e.message}")
        }
    }

    // Removes the item's document and any older copy saved under its Room id.
    private suspend fun deleteLogItem(collection: CollectionReference, timestamp: Long) {
        collection.document(CloudLogMerge.cloudKey(timestamp)).delete().await()
        val oldCopies = collection.whereEqualTo("timestamp", timestamp).get().await()
        for (doc in oldCopies.documents) {
            doc.reference.delete().await()
        }
    }

    suspend fun uploadStreakState(state: StreakStateEntity) {
        val userId = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users").document(userId)
                .collection("streak").document("current").set(state.toMap()).await()
            Log.d(tag, "Streak state uploaded to Firestore.")
        } catch (e: Exception) {
            Log.e(tag, "Error uploading streak state: ${e.message}")
        }
    }

    suspend fun uploadHabitEntry(entry: HabitEntryEntity) {
        val userId = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users").document(userId)
                .collection("habits").document(entry.date).set(entry.toMap()).await()
            Log.d(tag, "Habit entry uploaded for date: ${entry.date}.")
        } catch (e: Exception) {
            Log.e(tag, "Error uploading habit entry: ${e.message}")
        }
    }

    suspend fun clearAllCloudData() {
        val userId = auth.currentUser?.uid ?: return
        try {
            // 1. Delete profile doc
            firestore.collection("users").document(userId).delete().await()

            // 2. Delete activity documents
            val activitySnap = firestore.collection("users").document(userId).collection("activity").get().await()
            for (doc in activitySnap.documents) {
                doc.reference.delete().await()
            }

            // 3. Delete sleep documents
            val sleepSnap = firestore.collection("users").document(userId).collection("sleep").get().await()
            for (doc in sleepSnap.documents) {
                doc.reference.delete().await()
            }

            // 4. Delete nutrition documents
            val nutritionSnap = firestore.collection("users").document(userId).collection("nutrition").get().await()
            for (doc in nutritionSnap.documents) {
                doc.reference.delete().await()
            }

            // 5. Delete workout documents
            val workoutSnap = firestore.collection("users").document(userId).collection("workouts").get().await()
            for (doc in workoutSnap.documents) {
                doc.reference.delete().await()
            }

            // 6. Delete weight documents
            val weightSnap = firestore.collection("users").document(userId).collection("weights").get().await()
            for (doc in weightSnap.documents) {
                doc.reference.delete().await()
            }

            // 7. Delete streak documents
            val streakSnap = firestore.collection("users").document(userId).collection("streak").get().await()
            for (doc in streakSnap.documents) {
                doc.reference.delete().await()
            }

            // 8. Delete habit documents
            val habitSnap = firestore.collection("users").document(userId).collection("habits").get().await()
            for (doc in habitSnap.documents) {
                doc.reference.delete().await()
            }

            // Wait for all deletes to sync to the server (up to 5 seconds)
            try {
                kotlinx.coroutines.withTimeoutOrNull(5000) {
                    firestore.waitForPendingWrites().await()
                }
            } catch (e: Exception) {
                Log.e(tag, "Timeout or error waiting for pending writes: ${e.message}")
            }

            Log.d(tag, "Full Firestore cloud data wipe complete.")
        } catch (e: Exception) {
            Log.e(tag, "Error clearing cloud data: ${e.message}", e)
        }
    }

    // --- Entity Mappers ---

    private fun UserProfileEntity.toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "age" to age,
        "heightCm" to heightCm,
        "weightKg" to weightKg,
        "isMale" to isMale,
        "activityMultiplier" to activityMultiplier,
        "goal" to goal,
        "calorieOffset" to calorieOffset,
        "startingWeightKg" to startingWeightKg,
        "targetWeightKg" to targetWeightKg,
        "startDateTimestamp" to startDateTimestamp,
        "fitnessLevel" to fitnessLevel
    )

    private fun Map<String, Any>.toUserProfileEntity(): UserProfileEntity = UserProfileEntity(
        id = (this["id"] as? Number)?.toInt() ?: 0,
        age = (this["age"] as? Number)?.toInt() ?: 0,
        heightCm = (this["heightCm"] as? Number)?.toDouble() ?: 0.0,
        weightKg = (this["weightKg"] as? Number)?.toDouble() ?: 0.0,
        isMale = this["isMale"] as? Boolean ?: true,
        activityMultiplier = (this["activityMultiplier"] as? Number)?.toDouble() ?: 1.2,
        goal = this["goal"] as? String ?: "LOSE_WEIGHT",
        calorieOffset = (this["calorieOffset"] as? Number)?.toInt() ?: -500,
        startingWeightKg = (this["startingWeightKg"] as? Number)?.toDouble() ?: (this["weightKg"] as? Number)?.toDouble() ?: 75.0,
        targetWeightKg = (this["targetWeightKg"] as? Number)?.toDouble() ?: 70.0,
        startDateTimestamp = (this["startDateTimestamp"] as? Number)?.toLong() ?: 0L,
        fitnessLevel = this["fitnessLevel"] as? String ?: "INTERMEDIATE"
    )

    private fun ActivityTelemetryEntity.toMap(): Map<String, Any> = mapOf(
        "date" to date,
        "steps" to steps,
        "activeCalories" to activeCalories
    )

    private fun Map<String, Any>.toActivityTelemetry(): ActivityTelemetryEntity = ActivityTelemetryEntity(
        date = this["date"] as? String ?: "",
        steps = (this["steps"] as? Number)?.toInt() ?: 0,
        activeCalories = (this["activeCalories"] as? Number)?.toDouble() ?: 0.0
    )

    private fun SleepTelemetryEntity.toMap(): Map<String, Any> = mapOf(
        "date" to date,
        "sleepDurationSeconds" to sleepDurationSeconds,
        "deepSleepDurationSeconds" to deepSleepDurationSeconds,
        "readinessScore" to readinessScore
    )

    private fun Map<String, Any>.toSleepTelemetry(): SleepTelemetryEntity = SleepTelemetryEntity(
        date = this["date"] as? String ?: "",
        sleepDurationSeconds = (this["sleepDurationSeconds"] as? Number)?.toLong() ?: 0L,
        deepSleepDurationSeconds = (this["deepSleepDurationSeconds"] as? Number)?.toLong() ?: 0L,
        readinessScore = (this["readinessScore"] as? Number)?.toInt() ?: 0
    )

    private fun NutritionEntryEntity.toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "foodText" to foodText,
        "calories" to calories,
        "proteinG" to proteinG,
        "carbsG" to carbsG,
        "fatG" to fatG,
        "timestamp" to timestamp
    )

    private fun Map<String, Any>.toNutritionEntry(): NutritionEntryEntity = NutritionEntryEntity(
        id = (this["id"] as? Number)?.toLong() ?: 0L,
        foodText = this["foodText"] as? String ?: "",
        calories = (this["calories"] as? Number)?.toDouble() ?: 0.0,
        proteinG = (this["proteinG"] as? Number)?.toDouble() ?: 0.0,
        carbsG = (this["carbsG"] as? Number)?.toDouble() ?: 0.0,
        fatG = (this["fatG"] as? Number)?.toDouble() ?: 0.0,
        timestamp = (this["timestamp"] as? Number)?.toLong() ?: 0L
    )

    private fun WorkoutEntryEntity.toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "description" to description,
        "caloriesBurned" to caloriesBurned,
        "workoutType" to workoutType,
        "setsCount" to setsCount,
        "repsCount" to repsCount,
        "timestamp" to timestamp,
        "difficultyRating" to difficultyRating
    )

    private fun Map<String, Any>.toWorkoutEntry(): WorkoutEntryEntity = WorkoutEntryEntity(
        id = (this["id"] as? Number)?.toLong() ?: 0L,
        description = this["description"] as? String ?: "",
        caloriesBurned = (this["caloriesBurned"] as? Number)?.toDouble() ?: 0.0,
        workoutType = this["workoutType"] as? String ?: "Cardio",
        setsCount = (this["setsCount"] as? Number)?.toInt() ?: 0,
        repsCount = (this["repsCount"] as? Number)?.toInt() ?: 0,
        timestamp = (this["timestamp"] as? Number)?.toLong() ?: 0L,
        difficultyRating = this["difficultyRating"] as? String ?: "JUST_RIGHT"
    )

    private fun WeightEntryEntity.toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "weightKg" to weightKg,
        "timestamp" to timestamp,
        "note" to note
    )

    private fun Map<String, Any>.toWeightEntry(): WeightEntryEntity = WeightEntryEntity(
        id = (this["id"] as? Number)?.toLong() ?: 0L,
        weightKg = (this["weightKg"] as? Number)?.toDouble() ?: 0.0,
        timestamp = (this["timestamp"] as? Number)?.toLong() ?: 0L,
        note = this["note"] as? String ?: ""
    )

    private fun StreakStateEntity.toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "currentStreak" to currentStreak,
        "longestStreak" to longestStreak,
        "masteryStreak" to masteryStreak,
        "graceDaysRemaining" to graceDaysRemaining,
        "lastLoggedDate" to lastLoggedDate,
        "lastGraceUsedDate" to lastGraceUsedDate
    )

    private fun Map<String, Any>.toStreakState(): StreakStateEntity = StreakStateEntity(
        id = (this["id"] as? Number)?.toInt() ?: 0,
        currentStreak = (this["currentStreak"] as? Number)?.toInt() ?: 0,
        longestStreak = (this["longestStreak"] as? Number)?.toInt() ?: 0,
        masteryStreak = (this["masteryStreak"] as? Number)?.toInt() ?: 0,
        graceDaysRemaining = (this["graceDaysRemaining"] as? Number)?.toInt() ?: 1,
        lastLoggedDate = this["lastLoggedDate"] as? String ?: "",
        lastGraceUsedDate = this["lastGraceUsedDate"] as? String ?: ""
    )

    private fun HabitEntryEntity.toMap(): Map<String, Any> = mapOf(
        "date" to date,
        "waterMl" to waterMl,
        "workoutMins" to workoutMins,
        "cleanEatsCompleted" to cleanEatsCompleted,
        "stepsCompleted" to stepsCompleted,
        "sleepCompleted" to sleepCompleted
    )

    private fun Map<String, Any>.toHabitEntry(): HabitEntryEntity = HabitEntryEntity(
        date = this["date"] as? String ?: "",
        waterMl = (this["waterMl"] as? Number)?.toInt() ?: 0,
        workoutMins = (this["workoutMins"] as? Number)?.toInt() ?: 0,
        cleanEatsCompleted = this["cleanEatsCompleted"] as? Boolean ?: false,
        stepsCompleted = this["stepsCompleted"] as? Boolean ?: false,
        sleepCompleted = this["sleepCompleted"] as? Boolean ?: false
    )
}
