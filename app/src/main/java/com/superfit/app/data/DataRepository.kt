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
import kotlinx.datetime.toKotlinLocalDate

fun getUserSharedPrefs(context: Context): SharedPreferences {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
    return context.getSharedPreferences("superfit_prefs_$userId", Context.MODE_PRIVATE)
}

/** The signed-in user's settings, for the shared stores. */
fun userPrefs(context: Context): Prefs = AndroidPrefs(getUserSharedPrefs(context))

class DataRepository(
    private val context: Context,
    val healthConnectManager: HealthConnectManager
) {
    private val database: SuperfitDatabase get() = SuperfitDatabase.getDatabase(context)
    val firebaseSyncManager get() = FirebaseSyncManager(context)

    // The log itself (database plus cloud backup) is shared with the iPhone app.
    val log: LogRepository
        get() = LogRepository(
            database = { database },
            backup = firebaseSyncManager.backup,
            mealsChanged = { refreshWidget() },
            isImportedWeight = BodyImporter::isImported
        )

    val profileFlow: Flow<UserProfileEntity?> get() = log.profileFlow

    suspend fun getProfile(): UserProfileEntity? = log.getProfile()

    suspend fun saveProfile(profile: UserProfileEntity) = log.saveProfile(profile)

    fun getActivityFlow(date: String): Flow<ActivityTelemetryEntity?> = log.getActivityFlow(date)

    fun getSleepFlow(date: String): Flow<SleepTelemetryEntity?> = log.getSleepFlow(date)

    fun getRecentSleepFlow(): Flow<List<SleepTelemetryEntity>> = log.getRecentSleepFlow()

    fun getNutritionEntriesForDay(date: LocalDate): Flow<List<NutritionEntryEntity>> =
        log.getNutritionEntriesForDay(date.toKotlinLocalDate())

    /** Returns the new meal's id. */
    suspend fun addNutritionEntry(entry: NutritionEntryEntity): Long = log.addNutritionEntry(entry)

    /** Saves the AI's quality tags on an existing meal and updates its cloud backup. */
    suspend fun saveMealTags(entry: NutritionEntryEntity, tags: String) = log.saveMealTags(entry, tags)

    /** Saves an edited meal (amounts, oil or sugar changed) and updates its cloud backup. */
    suspend fun updateNutritionEntry(entry: NutritionEntryEntity) = log.updateNutritionEntry(entry)

    suspend fun deleteNutritionEntry(entry: NutritionEntryEntity) = log.deleteNutritionEntry(entry)

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
                    if (activity != null) log.saveActivity(activity)
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
                    if (sleep != null) log.saveSleep(sleep)
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

    val allNutritionEntriesFlow: Flow<List<NutritionEntryEntity>> = log.allNutritionEntriesFlow

    suspend fun getAllNutritionEntries(): List<NutritionEntryEntity> = log.getAllNutritionEntries()

    val allActivityTelemetryFlow: Flow<List<ActivityTelemetryEntity>> get() = log.allActivityTelemetryFlow

    /** Re-reads one day's steps from Health Connect and saves them; keeps the saved value if the read fails. */
    suspend fun refreshActivity(date: LocalDate) {
        if (!healthConnectManager.isAvailable()) return
        try {
            healthConnectManager.readDailyTelemetry(date)?.let { log.saveActivity(it) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("DataRepository", "Refreshing steps for $date failed", e)
        }
    }

    /**
     * One-time clean-up after the single-app steps fix: re-reads the saved days from [fromDaysAgo]
     * to [toDaysAgo] and saves the watch app's count, up or down. A day Health Connect no longer
     * has (or reads as zero) keeps its saved count, so nothing is wiped.
     */
    suspend fun correctPastSteps(fromDaysAgo: Long, toDaysAgo: Long) {
        if (!healthConnectManager.isAvailable()) return
        val today = LocalDate.now()
        val savedDays = log.getAllActivityTelemetry().associateBy { it.date }
        for (ago in fromDaysAgo..toDaysAgo) {
            val date = today.minusDays(ago)
            val saved = savedDays[date.toString()] ?: continue
            val fresh = try {
                healthConnectManager.readDailyTelemetry(date)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: continue
            if (fresh.steps > 0 && fresh.steps != saved.steps) log.saveActivity(saved.copy(steps = fresh.steps))
        }
    }

    suspend fun getAllActivityTelemetry(): List<ActivityTelemetryEntity> = log.getAllActivityTelemetry()

    suspend fun getAllSleepTelemetry(): List<SleepTelemetryEntity> = log.getAllSleepTelemetry()

    suspend fun getFrequentFoodTexts(limit: Int): List<String> = log.getFrequentFoodTexts(limit)

    suspend fun getPredictedFoods(
        currentHour: Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    ): List<PredictedFood> = log.getPredictedFoods(currentHour)

    fun getWorkoutEntriesForDay(date: LocalDate): Flow<List<WorkoutEntryEntity>> =
        log.getWorkoutEntriesForDay(date.toKotlinLocalDate())

    /** Returns the new workout's id. */
    suspend fun addWorkoutEntry(entry: WorkoutEntryEntity): Long = log.addWorkoutEntry(entry)

    suspend fun getWorkoutEntry(id: Long): WorkoutEntryEntity? = log.getWorkoutEntry(id)

    suspend fun deleteWorkoutEntry(entry: WorkoutEntryEntity) = log.deleteWorkoutEntry(entry)

    suspend fun getAllWorkoutEntries(): List<WorkoutEntryEntity> = log.getAllWorkoutEntries()

    // --- Workouts from other apps (Health Connect) ---

    val workoutImportPrefs: WorkoutImportPrefs get() = WorkoutImportPrefs(userPrefs(context))

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

    val weightEntriesFlow: Flow<List<WeightEntryEntity>> get() = log.weightEntriesFlow

    suspend fun addWeightEntry(weightKg: Double, note: String = "") = log.addWeightEntry(weightKg, note)

    /**
     * Adds a smart scale weigh-in at the time it was taken. One reading a day: a weight the user
     * typed that day wins, and a later scale reading replaces an earlier one.
     */
    suspend fun addImportedWeight(weightKg: Double, timestamp: Long, note: String) =
        log.addImportedWeight(weightKg, timestamp, note)

    suspend fun deleteWeightEntry(entry: WeightEntryEntity) = log.deleteWeightEntry(entry)

    suspend fun getAllWeightEntries(): List<WeightEntryEntity> = log.getAllWeightEntries()

    val streakStateFlow: Flow<StreakStateEntity?> get() = log.streakStateFlow

    suspend fun saveStreakState(state: StreakStateEntity) = log.saveStreakState(state)

    fun getHabitEntryFlow(date: String): Flow<HabitEntryEntity?> = log.getHabitEntryFlow(date)

    suspend fun getHabitEntry(date: String): HabitEntryEntity? = log.getHabitEntry(date)

    suspend fun saveHabitEntry(entry: HabitEntryEntity) = log.saveHabitEntry(entry)

    suspend fun addWater(date: String, addMl: Int) = log.addWater(date, addMl)

    suspend fun toggleCleanEats(date: String) = log.toggleCleanEats(date)
}
