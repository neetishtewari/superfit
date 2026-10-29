package com.superfit.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.superfit.app.widget.SuperfitWidget
import com.superfit.app.widget.WidgetSnapshotStore
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

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

                importWorkouts()

                try {
                    BodyImporter.importNew(context, this)
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
            CoachChatStore.clearChat(context)
        }
    }

    // --- Coach chat and notes ---

    suspend fun loadCoachChat(): List<com.superfit.app.domain.ChatEntry> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CoachChatStore.loadChat(context) }

    suspend fun saveCoachChat(entries: List<com.superfit.app.domain.ChatEntry>) {
        val json = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CoachChatStore.saveChat(context, entries) }
        firebaseSyncManager.uploadCoachChat(json)
    }

    suspend fun clearCoachChat() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CoachChatStore.clearChat(context) }
        firebaseSyncManager.deleteCoachChat()
    }

    fun loadCoachNotes(): List<com.superfit.app.domain.CoachNote> = CoachChatStore.loadNotes(context)

    suspend fun saveCoachNotes(notes: List<com.superfit.app.domain.CoachNote>) {
        firebaseSyncManager.uploadCoachNotes(CoachChatStore.saveNotes(context, notes))
    }

    suspend fun clearAllCloudData(): Boolean = firebaseSyncManager.clearAllCloudData()

    suspend fun deleteAccount(): AccountDeletion = firebaseSyncManager.deleteAccount()

    val allNutritionEntriesFlow: Flow<List<NutritionEntryEntity>> = database.nutritionDao().getAllEntriesFlow()

    suspend fun getAllNutritionEntries(): List<NutritionEntryEntity> {
        return database.nutritionDao().getAllEntries()
    }

    val allActivityTelemetryFlow: Flow<List<ActivityTelemetryEntity>> get() = database.telemetryDao().getAllActivityFlow()

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

    /** Returns the new workout's id. */
    suspend fun addWorkoutEntry(entry: WorkoutEntryEntity): Long {
        val id = database.workoutDao().insertEntry(entry)
        firebaseSyncManager.uploadWorkout(entry.copy(id = id))
        return id
    }

    suspend fun getWorkoutEntry(id: Long): WorkoutEntryEntity? = database.workoutDao().getEntry(id)

    suspend fun deleteWorkoutEntry(entry: WorkoutEntryEntity) {
        database.workoutDao().deleteEntry(entry)
        firebaseSyncManager.deleteWorkout(entry)
    }

    suspend fun getAllWorkoutEntries(): List<WorkoutEntryEntity> {
        return database.workoutDao().getAllEntries()
    }

    // --- Workouts from other apps (Health Connect) ---

    val workoutImportPrefs: WorkoutImportPrefs get() = WorkoutImportPrefs(getUserSharedPrefs(context))

    fun getImportedWorkoutsForDay(date: LocalDate): Flow<List<ImportedWorkoutEntity>> {
        val zoneId = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        return database.importedWorkoutDao().getBetweenFlow(startOfDay, endOfDay)
    }

    val importedWorkoutCountFlow: Flow<Int> get() = database.importedWorkoutDao().countFlow()

    val allImportedWorkoutsFlow: Flow<List<ImportedWorkoutEntity>> get() = database.importedWorkoutDao().getAllFlow()

    suspend fun getAllImportedWorkouts(): List<ImportedWorkoutEntity> = database.importedWorkoutDao().getAll()

    /** Workouts from other apps without doubles or hidden ones, empty while the import is off. */
    suspend fun getImportedWorkoutViews(bmr: Double): List<com.superfit.app.domain.WorkoutView> {
        val prefs = workoutImportPrefs
        if (!prefs.enabled) return emptyList()
        return ImportedWorkouts.views(context, getAllImportedWorkouts(), prefs, bmr)
    }

    /** Voice-logged workouts plus imported ones, for the coach. */
    suspend fun getAllWorkoutsForCoach(bmr: Double): List<WorkoutEntryEntity> =
        getAllWorkoutEntries() + getImportedWorkoutViews(bmr).map(ImportedWorkouts::coachEntry)

    /** Reads new workouts from Health Connect when the user has turned the import on. */
    suspend fun importWorkouts(): Int? {
        val prefs = workoutImportPrefs
        if (!prefs.enabled || !healthConnectManager.isAvailable()) return null
        val client = healthConnectManager.healthConnectClient ?: return null
        return WorkoutImporter.import(client, database.importedWorkoutDao(), prefs)
    }

    /** Turns the import on or off. Turning it off forgets the imported workouts; they can be read again. */
    suspend fun setWorkoutImportEnabled(enabled: Boolean) {
        val prefs = workoutImportPrefs
        prefs.enabled = enabled
        if (!enabled) {
            database.importedWorkoutDao().deleteAll()
            prefs.lastImportMillis = 0L
        }
        backUpWorkoutImportSettings()
    }

    suspend fun hideImportedWorkout(ids: List<String>) {
        workoutImportPrefs.hide(ids)
        backUpWorkoutImportSettings()
    }

    suspend fun backUpWorkoutImportSettings() {
        firebaseSyncManager.uploadWorkoutImportSettings(workoutImportPrefs.toBackup())
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

    /**
     * Adds a smart scale weigh-in at the time it was taken. One reading a day: a weight the user
     * typed that day wins, and a later scale reading replaces an earlier one.
     */
    suspend fun addImportedWeight(weightKg: Double, timestamp: Long, note: String) {
        val zone = java.time.ZoneId.systemDefault()
        fun dayOf(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val day = dayOf(timestamp)
        val sameDay = database.weightDao().getAllWeightEntries().filter { dayOf(it.timestamp) == day }
        if (sameDay.any { !BodyImporter.isImported(it) }) return
        if (sameDay.any { it.timestamp >= timestamp }) return
        sameDay.forEach { deleteWeightEntry(it) }
        val entry = WeightEntryEntity(weightKg = weightKg, timestamp = timestamp, note = note)
        val id = database.weightDao().insertWeight(entry)
        firebaseSyncManager.uploadWeight(entry.copy(id = id))
        val latest = database.weightDao().getLatestWeightEntry()
        val profile = getProfile()
        if (profile != null && latest?.timestamp == timestamp) saveProfile(profile.copy(weightKg = weightKg))
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
