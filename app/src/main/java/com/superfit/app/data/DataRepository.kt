package com.superfit.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.superfit.app.widget.SuperfitWidget
import com.superfit.app.widget.WidgetSnapshotStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    /** Returns the new meal's id. */
    suspend fun addNutritionEntry(entry: NutritionEntryEntity): Long {
        // Back up under the id Room just assigned. A new entry still has id 0 here, and uploading
        // it as-is wrote every meal to the same cloud document, so only the latest one was kept.
        val id = database.nutritionDao().insertEntry(entry)
        firebaseSyncManager.uploadNutrition(entry.copy(id = id))
        refreshWidget()
        return id
    }

    /** Saves the AI's quality tags on an existing meal and updates its cloud backup. */
    suspend fun saveMealTags(entry: NutritionEntryEntity, tags: String) {
        database.nutritionDao().setQualityTags(entry.id, tags)
        firebaseSyncManager.uploadNutrition(entry.copy(qualityTags = tags))
    }

    /** Saves an edited meal (amounts, oil or sugar changed) and updates its cloud backup. */
    suspend fun updateNutritionEntry(entry: NutritionEntryEntity) {
        database.nutritionDao().updateEntry(entry)
        firebaseSyncManager.uploadNutrition(entry)
        refreshWidget()
    }

    suspend fun deleteNutritionEntry(entry: NutritionEntryEntity) {
        database.nutritionDao().deleteEntry(entry)
        firebaseSyncManager.deleteNutrition(entry)
        refreshWidget()
    }

    /** Keeps the home-screen widget right after a meal is added, edited or deleted from any screen. */
    private suspend fun refreshWidget() {
        try {
            val today = LocalDate.now()
            val eaten = getNutritionEntriesForDay(today).first().sumOf { it.calories }
            if (WidgetSnapshotStore.updateEaten(context, today.toString(), eaten.toInt())) {
                SuperfitWidget.refresh(context)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("DataRepository", "Widget update failed", e)
        }
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
                    if (activity != null) {
                        database.telemetryDao().insertActivity(activity)
                        firebaseSyncManager.uploadActivity(activity)
                    }
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

    suspend fun clearAllCloudData() {
        firebaseSyncManager.clearAllCloudData()
    }

    val allNutritionEntriesFlow: Flow<List<NutritionEntryEntity>> = database.nutritionDao().getAllEntriesFlow()

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

    suspend fun getPredictedFoods(currentHour: Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)): List<PredictedFood> {
        val allEntries = getAllNutritionEntries()
        return com.superfit.app.domain.FoodPredictor.predict(allEntries, currentHour)
    }



    fun getWorkoutEntriesForDay(date: LocalDate): Flow<List<WorkoutEntryEntity>> {
        val zoneId = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        return database.workoutDao().getEntriesForDayFlow(startOfDay, endOfDay)
    }

    suspend fun addWorkoutEntry(entry: WorkoutEntryEntity) {
        val id = database.workoutDao().insertEntry(entry)
        firebaseSyncManager.uploadWorkout(entry.copy(id = id))
    }

    suspend fun deleteWorkoutEntry(entry: WorkoutEntryEntity) {
        database.workoutDao().deleteEntry(entry)
        firebaseSyncManager.deleteWorkout(entry)
    }

    suspend fun getAllWorkoutEntries(): List<WorkoutEntryEntity> {
        return database.workoutDao().getAllEntries()
    }

    val weightEntriesFlow: Flow<List<WeightEntryEntity>> get() = database.weightDao().getAllWeightEntriesFlow()

    suspend fun addWeightEntry(weightKg: Double, note: String = "") {
        val entry = WeightEntryEntity(
            weightKg = weightKg,
            timestamp = System.currentTimeMillis(),
            note = note
        )
        val id = database.weightDao().insertWeight(entry)
        firebaseSyncManager.uploadWeight(entry.copy(id = id))
        // Also update current weight on UserProfile
        val profile = getProfile()
        if (profile != null) {
            saveProfile(profile.copy(weightKg = weightKg))
        }
    }

    suspend fun deleteWeightEntry(entry: WeightEntryEntity) {
        database.weightDao().deleteWeight(entry)
        firebaseSyncManager.deleteWeight(entry)
    }

    suspend fun getAllWeightEntries(): List<WeightEntryEntity> {
        return database.weightDao().getAllWeightEntries()
    }

    val streakStateFlow: Flow<StreakStateEntity?> get() = database.streakDao().getStreakStateFlow()

    suspend fun saveStreakState(state: StreakStateEntity) {
        database.streakDao().insertStreakState(state)
        firebaseSyncManager.uploadStreakState(state)
    }

    fun getHabitEntryFlow(date: String): Flow<HabitEntryEntity?> {
        return database.habitDao().getHabitEntryFlow(date)
    }

    suspend fun getHabitEntry(date: String): HabitEntryEntity? {
        return database.habitDao().getHabitEntry(date)
    }

    suspend fun saveHabitEntry(entry: HabitEntryEntity) {
        database.habitDao().insertHabitEntry(entry)
        firebaseSyncManager.uploadHabitEntry(entry)
    }

    suspend fun addWater(date: String, addMl: Int) {
        val current = database.habitDao().getHabitEntry(date) ?: HabitEntryEntity(date = date)
        val updated = current.copy(waterMl = current.waterMl + addMl)
        database.habitDao().insertHabitEntry(updated)
        firebaseSyncManager.uploadHabitEntry(updated)
    }

    suspend fun toggleCleanEats(date: String) {
        val current = database.habitDao().getHabitEntry(date) ?: HabitEntryEntity(date = date)
        val updated = current.copy(cleanEatsCompleted = !current.cleanEatsCompleted)
        database.habitDao().insertHabitEntry(updated)
        firebaseSyncManager.uploadHabitEntry(updated)
    }
}

