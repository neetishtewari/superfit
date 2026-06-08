package com.superfit.app.data

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseSyncManager(private val context: Context) {
    private val database: SuperfitDatabase get() = SuperfitDatabase.getDatabase(context)

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private val tag = "FirebaseSyncManager"

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
     * Uploads the entire local database to Firestore.
     */
    suspend fun syncAllUp() {
        val userId = auth.currentUser?.uid ?: return
        Log.d(tag, "Syncing local database up to Firestore for user: $userId")

        // 1. Sync Profile
        val profile = database.profileDao().getProfile()
        if (profile != null) {
            uploadProfile(profile)
        }

        // 2. Sync Activity Telemetry
        val activities = database.telemetryDao().getAllActivity()
        for (activity in activities) {
            uploadActivity(activity)
        }

        // 3. Sync Sleep Telemetry
        val sleeps = database.telemetryDao().getAllSleep()
        for (sleep in sleeps) {
            uploadSleep(sleep)
        }

        // 4. Sync Nutrition Entries
        val entries = database.nutritionDao().getAllEntries()
        for (entry in entries) {
            uploadNutrition(entry)
        }

        // 5. Sync Workout Entries
        val workouts = database.workoutDao().getAllEntries()
        for (workout in workouts) {
            uploadWorkout(workout)
        }
    }

    /**
     * Downloads all data from Firestore and replaces/merges into Room.
     */
    suspend fun syncAllDown() {
        val userId = auth.currentUser?.uid ?: return
        Log.d(tag, "Syncing Firestore down to local Room for user: $userId")

        try {
            // 1. Profile
            val profileDoc = firestore.collection("users").document(userId).get().await()
            if (profileDoc.exists()) {
                val data = profileDoc.data
                if (data != null) {
                    database.profileDao().insertProfile(data.toUserProfileEntity())
                }
            }

            // 2. Activity Telemetry
            val activitySnap = firestore.collection("users").document(userId).collection("activity").get().await()
            val activities = activitySnap.documents.mapNotNull { it.data?.toActivityTelemetry() }
            if (activities.isNotEmpty()) {
                database.telemetryDao().insertActivities(activities)
            }

            // 3. Sleep Telemetry
            val sleepSnap = firestore.collection("users").document(userId).collection("sleep").get().await()
            val sleeps = sleepSnap.documents.mapNotNull { it.data?.toSleepTelemetry() }
            if (sleeps.isNotEmpty()) {
                database.telemetryDao().insertSleeps(sleeps)
            }

            // 4. Nutrition Entries
            val nutritionSnap = firestore.collection("users").document(userId).collection("nutrition").get().await()
            val entries = nutritionSnap.documents.mapNotNull { it.data?.toNutritionEntry() }
            // Clear local nutrition entries first to ensure an exact sync match
            database.nutritionDao().deleteAllEntries()
            if (entries.isNotEmpty()) {
                database.nutritionDao().insertEntries(entries)
            }

            // 5. Workout Entries
            val workoutSnap = firestore.collection("users").document(userId).collection("workouts").get().await()
            val workouts = workoutSnap.documents.mapNotNull { it.data?.toWorkoutEntry() }
            // Clear local workout entries first to ensure an exact sync match
            database.workoutDao().deleteAllEntries()
            if (workouts.isNotEmpty()) {
                database.workoutDao().insertEntries(workouts)
            }

            Log.d(tag, "Full bi-directional sync down complete.")
        } catch (e: Exception) {
            Log.e(tag, "Error syncing data down: ${e.message}", e)
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
                .collection("nutrition").document(entry.id.toString()).set(entry.toMap()).await()
            Log.d(tag, "Nutrition entry uploaded with ID: ${entry.id}.")
        } catch (e: Exception) {
            Log.e(tag, "Error uploading nutrition: ${e.message}")
        }
    }

    suspend fun deleteNutrition(entry: NutritionEntryEntity) {
        val userId = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users").document(userId)
                .collection("nutrition").document(entry.id.toString()).delete().await()
            Log.d(tag, "Nutrition entry deleted from Firestore with ID: ${entry.id}.")
        } catch (e: Exception) {
            Log.e(tag, "Error deleting nutrition: ${e.message}")
        }
    }

    suspend fun uploadWorkout(entry: WorkoutEntryEntity) {
        val userId = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users").document(userId)
                .collection("workouts").document(entry.id.toString()).set(entry.toMap()).await()
            Log.d(tag, "Workout entry uploaded with ID: ${entry.id}.")
        } catch (e: Exception) {
            Log.e(tag, "Error uploading workout: ${e.message}")
        }
    }

    suspend fun deleteWorkout(entry: WorkoutEntryEntity) {
        val userId = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users").document(userId)
                .collection("workouts").document(entry.id.toString()).delete().await()
            Log.d(tag, "Workout entry deleted from Firestore with ID: ${entry.id}.")
        } catch (e: Exception) {
            Log.e(tag, "Error deleting workout: ${e.message}")
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
        "calorieOffset" to calorieOffset
    )

    private fun Map<String, Any>.toUserProfileEntity(): UserProfileEntity = UserProfileEntity(
        id = (this["id"] as? Number)?.toInt() ?: 0,
        age = (this["age"] as? Number)?.toInt() ?: 0,
        heightCm = (this["heightCm"] as? Number)?.toDouble() ?: 0.0,
        weightKg = (this["weightKg"] as? Number)?.toDouble() ?: 0.0,
        isMale = this["isMale"] as? Boolean ?: true,
        activityMultiplier = (this["activityMultiplier"] as? Number)?.toDouble() ?: 1.2,
        goal = this["goal"] as? String ?: "LOSE_WEIGHT",
        calorieOffset = (this["calorieOffset"] as? Number)?.toInt() ?: -500
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
        "timestamp" to timestamp
    )

    private fun Map<String, Any>.toWorkoutEntry(): WorkoutEntryEntity = WorkoutEntryEntity(
        id = (this["id"] as? Number)?.toLong() ?: 0L,
        description = this["description"] as? String ?: "",
        caloriesBurned = (this["caloriesBurned"] as? Number)?.toDouble() ?: 0.0,
        workoutType = this["workoutType"] as? String ?: "Cardio",
        setsCount = (this["setsCount"] as? Number)?.toInt() ?: 0,
        repsCount = (this["repsCount"] as? Number)?.toInt() ?: 0,
        timestamp = (this["timestamp"] as? Number)?.toLong() ?: 0L
    )
}
