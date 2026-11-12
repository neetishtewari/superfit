package com.superfit.app.data

import com.superfit.app.domain.FoodPredictor
import com.superfit.app.domain.withCurrentAge
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * The user's log on this phone (profile, meals, workouts, weigh-ins, streak, daily habits and
 * steps/sleep already read), the same on Android and iPhone. Every change is saved to the database
 * and then to the cloud backup. The platform adds what only it has (health data, the widget).
 *
 * [mealsChanged] runs after a meal is added, edited or deleted (Android refreshes its widget).
 * [isImportedWeight] tells a smart-scale weigh-in from one the user typed.
 */
@OptIn(ExperimentalTime::class)
class LogRepository(
    private val database: () -> SuperfitDatabase,
    private val backup: CloudBackup,
    private val mealsChanged: suspend () -> Unit = {},
    private val isImportedWeight: (WeightEntryEntity) -> Boolean = { false },
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() }
) {
    private val db get() = database()

    // --- Profile ---

    val profileFlow: Flow<UserProfileEntity?> get() = db.profileDao().getProfileFlow()

    suspend fun getProfile(): UserProfileEntity? = db.profileDao().getProfile()

    suspend fun saveProfile(profile: UserProfileEntity) {
        // The saved age follows the birth month, for older app versions that read only "age".
        val current = profile.withCurrentAge()
        db.profileDao().insertProfile(current)
        backup.uploadProfile(current)
    }

    // --- Steps and sleep already read from the health app ---

    fun getActivityFlow(date: String): Flow<ActivityTelemetryEntity?> = db.telemetryDao().getActivityFlow(date)

    fun getSleepFlow(date: String): Flow<SleepTelemetryEntity?> = db.telemetryDao().getSleepFlow(date)

    fun getRecentSleepFlow(): Flow<List<SleepTelemetryEntity>> = db.telemetryDao().getRecentSleepFlow()

    val allActivityTelemetryFlow: Flow<List<ActivityTelemetryEntity>> get() = db.telemetryDao().getAllActivityFlow()

    suspend fun getAllActivityTelemetry(): List<ActivityTelemetryEntity> = db.telemetryDao().getAllActivity()

    suspend fun getAllSleepTelemetry(): List<SleepTelemetryEntity> = db.telemetryDao().getAllSleep()

    /** Saves a day's steps read from the health app, and backs it up. */
    suspend fun saveActivity(activity: ActivityTelemetryEntity) {
        db.telemetryDao().insertActivity(activity)
        backup.uploadActivity(activity)
    }

    /** Saves a night's sleep read from the health app, and backs it up. */
    suspend fun saveSleep(sleep: SleepTelemetryEntity) {
        db.telemetryDao().insertSleep(sleep)
        backup.uploadSleep(sleep)
    }

    /** Steps and sleep from the phone's health app, for [HealthSync]. */
    val healthStore: HealthStore = object : HealthStore {
        override suspend fun activity(date: String) = db.telemetryDao().getActivity(date)
        override suspend fun saveActivity(activity: ActivityTelemetryEntity) = this@LogRepository.saveActivity(activity)
        override suspend fun sleep(date: String) = db.telemetryDao().getSleep(date)
        override suspend fun saveSleep(sleep: SleepTelemetryEntity) = this@LogRepository.saveSleep(sleep)
    }

    // --- Workouts from other apps (the phone's health app; kept on the phone, not backed up) ---

    val importedWorkouts: ImportedWorkoutDao get() = db.importedWorkoutDao()

    val allImportedWorkoutsFlow: Flow<List<ImportedWorkoutEntity>> get() = db.importedWorkoutDao().getAllFlow()

    // --- Meals ---

    fun getNutritionEntriesForDay(date: LocalDate): Flow<List<NutritionEntryEntity>> {
        val (start, end) = dayRange(date)
        return db.nutritionDao().getEntriesForDayFlow(start, end)
    }

    /** Returns the new meal's id. */
    suspend fun addNutritionEntry(entry: NutritionEntryEntity): Long {
        // Back up under the id Room just assigned. A new entry still has id 0 here, and uploading
        // it as-is wrote every meal to the same cloud document, so only the latest one was kept.
        val id = db.nutritionDao().insertEntry(entry)
        backup.uploadNutrition(entry.copy(id = id))
        mealsChanged()
        return id
    }

    /** Saves the AI's quality tags on an existing meal and updates its cloud backup. */
    suspend fun saveMealTags(entry: NutritionEntryEntity, tags: String) {
        db.nutritionDao().setQualityTags(entry.id, tags)
        backup.uploadNutrition(entry.copy(qualityTags = tags))
    }

    /** Saves an edited meal (amounts, oil or sugar changed) and updates its cloud backup. */
    suspend fun updateNutritionEntry(entry: NutritionEntryEntity) {
        db.nutritionDao().updateEntry(entry)
        backup.uploadNutrition(entry)
        mealsChanged()
    }

    suspend fun deleteNutritionEntry(entry: NutritionEntryEntity) {
        db.nutritionDao().deleteEntry(entry)
        backup.deleteNutrition(entry)
        mealsChanged()
    }

    val allNutritionEntriesFlow: Flow<List<NutritionEntryEntity>> get() = db.nutritionDao().getAllEntriesFlow()

    suspend fun getAllNutritionEntries(): List<NutritionEntryEntity> = db.nutritionDao().getAllEntries()

    suspend fun getFrequentFoodTexts(limit: Int): List<String> = db.nutritionDao().getFrequentFoodTexts(limit)

    suspend fun getPredictedFoods(currentHour: Int): List<PredictedFood> =
        FoodPredictor.predict(getAllNutritionEntries(), currentHour)

    // --- Workouts the user logged ---

    fun getWorkoutEntriesForDay(date: LocalDate): Flow<List<WorkoutEntryEntity>> {
        val (start, end) = dayRange(date)
        return db.workoutDao().getEntriesForDayFlow(start, end)
    }

    /** Returns the new workout's id. */
    suspend fun addWorkoutEntry(entry: WorkoutEntryEntity): Long {
        val id = db.workoutDao().insertEntry(entry)
        backup.uploadWorkout(entry.copy(id = id))
        return id
    }

    suspend fun getWorkoutEntry(id: Long): WorkoutEntryEntity? = db.workoutDao().getEntry(id)

    suspend fun deleteWorkoutEntry(entry: WorkoutEntryEntity) {
        db.workoutDao().deleteEntry(entry)
        backup.deleteWorkout(entry)
    }

    suspend fun getAllWorkoutEntries(): List<WorkoutEntryEntity> = db.workoutDao().getAllEntries()

    // --- Weigh-ins ---

    val weightEntriesFlow: Flow<List<WeightEntryEntity>> get() = db.weightDao().getAllWeightEntriesFlow()

    suspend fun addWeightEntry(weightKg: Double, note: String = "") {
        val entry = WeightEntryEntity(weightKg = weightKg, timestamp = Now.millis(), note = note)
        val id = db.weightDao().insertWeight(entry)
        backup.uploadWeight(entry.copy(id = id))
        // Also update the current weight on the profile.
        getProfile()?.let { saveProfile(it.copy(weightKg = weightKg)) }
    }

    /**
     * Adds a smart scale weigh-in at the time it was taken. One reading a day: a weight the user
     * typed that day wins, and a later scale reading replaces an earlier one.
     */
    suspend fun addImportedWeight(weightKg: Double, timestamp: Long, note: String) {
        val tz = zone()
        fun dayOf(ms: Long) = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz).date
        val day = dayOf(timestamp)
        val sameDay = db.weightDao().getAllWeightEntries().filter { dayOf(it.timestamp) == day }
        if (sameDay.any { !isImportedWeight(it) }) return
        if (sameDay.any { it.timestamp >= timestamp }) return
        sameDay.forEach { deleteWeightEntry(it) }
        val entry = WeightEntryEntity(weightKg = weightKg, timestamp = timestamp, note = note)
        val id = db.weightDao().insertWeight(entry)
        backup.uploadWeight(entry.copy(id = id))
        val latest = db.weightDao().getLatestWeightEntry()
        val profile = getProfile()
        if (profile != null && latest?.timestamp == timestamp) saveProfile(profile.copy(weightKg = weightKg))
    }

    suspend fun deleteWeightEntry(entry: WeightEntryEntity) {
        db.weightDao().deleteWeight(entry)
        backup.deleteWeight(entry)
    }

    suspend fun getAllWeightEntries(): List<WeightEntryEntity> = db.weightDao().getAllWeightEntries()

    // --- Streak and daily habits ---

    val streakStateFlow: Flow<StreakStateEntity?> get() = db.streakDao().getStreakStateFlow()

    suspend fun saveStreakState(state: StreakStateEntity) {
        db.streakDao().insertStreakState(state)
        backup.uploadStreakState(state)
    }

    fun getHabitEntryFlow(date: String): Flow<HabitEntryEntity?> = db.habitDao().getHabitEntryFlow(date)

    suspend fun getHabitEntry(date: String): HabitEntryEntity? = db.habitDao().getHabitEntry(date)

    suspend fun saveHabitEntry(entry: HabitEntryEntity) {
        db.habitDao().insertHabitEntry(entry)
        backup.uploadHabitEntry(entry)
    }

    suspend fun addWater(date: String, addMl: Int) {
        val current = getHabitEntry(date) ?: HabitEntryEntity(date = date)
        saveHabitEntry(current.copy(waterMl = current.waterMl + addMl))
    }

    suspend fun toggleCleanEats(date: String) {
        val current = getHabitEntry(date) ?: HabitEntryEntity(date = date)
        saveHabitEntry(current.copy(cleanEatsCompleted = !current.cleanEatsCompleted))
    }

    /** The first and last millisecond of [date] in the phone's time zone. */
    private fun dayRange(date: LocalDate): Pair<Long, Long> {
        val tz = zone()
        val start = date.atStartOfDayIn(tz).toEpochMilliseconds()
        val end = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds() - 1
        return start to end
    }
}
