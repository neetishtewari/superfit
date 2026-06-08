package com.superfit.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

fun getUserSharedPrefs(context: Context): SharedPreferences {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
    return context.getSharedPreferences("superfit_prefs_$userId", Context.MODE_PRIVATE)
}

class DataRepository(
    private val context: Context,
    val healthConnectManager: HealthConnectManager
) {
    private val database: SuperfitDatabase get() = SuperfitDatabase.getDatabase(context)
    val firebaseSyncManager get() = FirebaseSyncManager(context)

    val profileFlow: Flow<UserProfileEntity?> get() = database.profileDao().getProfileFlow()

    suspend fun getProfile(): UserProfileEntity? = database.profileDao().getProfile()

    suspend fun saveProfile(profile: UserProfileEntity) {
        database.profileDao().insertProfile(profile)
        firebaseSyncManager.uploadProfile(profile)
    }

    fun getActivityFlow(date: String): Flow<ActivityTelemetryEntity?> {
        return database.telemetryDao().getActivityFlow(date)
    }

    fun getSleepFlow(date: String): Flow<SleepTelemetryEntity?> {
        return database.telemetryDao().getSleepFlow(date)
    }

    fun getRecentSleepFlow(): Flow<List<SleepTelemetryEntity>> {
        return database.telemetryDao().getRecentSleepFlow()
    }

    fun getNutritionEntriesForDay(date: LocalDate): Flow<List<NutritionEntryEntity>> {
        val zoneId = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        return database.nutritionDao().getEntriesForDayFlow(startOfDay, endOfDay)
    }

    suspend fun addNutritionEntry(entry: NutritionEntryEntity) {
        database.nutritionDao().insertEntry(entry)
        firebaseSyncManager.uploadNutrition(entry)
    }

    suspend fun deleteNutritionEntry(entry: NutritionEntryEntity) {
        database.nutritionDao().deleteEntry(entry)
        firebaseSyncManager.deleteNutrition(entry)
    }

    suspend fun syncHealthConnectTelemetry(date: LocalDate) {
        if (healthConnectManager.isAvailable()) {
            val client = healthConnectManager.healthConnectClient ?: return
            try {
                val granted = client.permissionController.getGrantedPermissions()
                if (granted.isEmpty()) return

                // Separate sync steps to support partial permissions gracefully
                try {
                    val activity = healthConnectManager.readDailyTelemetry(date)
                    database.telemetryDao().insertActivity(activity)
                    firebaseSyncManager.uploadActivity(activity)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                try {
                    val sleep = healthConnectManager.readSleepTelemetry(date)
                    if (sleep != null) {
                        database.telemetryDao().insertSleep(sleep)
                        firebaseSyncManager.uploadSleep(sleep)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun clearAllLocalData() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            database.clearAllTables()
        }
    }

    suspend fun getAllNutritionEntries(): List<NutritionEntryEntity> {
        return database.nutritionDao().getAllEntries()
    }

    suspend fun getAllActivityTelemetry(): List<ActivityTelemetryEntity> {
        return database.telemetryDao().getAllActivity()
    }

    suspend fun getAllSleepTelemetry(): List<SleepTelemetryEntity> {
        return database.telemetryDao().getAllSleep()
    }

    suspend fun getFrequentFoodTexts(limit: Int): List<String> {
        return database.nutritionDao().getFrequentFoodTexts(limit)
    }

    fun getWorkoutEntriesForDay(date: LocalDate): Flow<List<WorkoutEntryEntity>> {
        val zoneId = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        return database.workoutDao().getEntriesForDayFlow(startOfDay, endOfDay)
    }

    suspend fun addWorkoutEntry(entry: WorkoutEntryEntity) {
        database.workoutDao().insertEntry(entry)
        firebaseSyncManager.uploadWorkout(entry)
    }

    suspend fun deleteWorkoutEntry(entry: WorkoutEntryEntity) {
        database.workoutDao().deleteEntry(entry)
        firebaseSyncManager.deleteWorkout(entry)
    }

    suspend fun getAllWorkoutEntries(): List<WorkoutEntryEntity> {
        return database.workoutDao().getAllEntries()
    }
}

