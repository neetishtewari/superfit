package com.superfit.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM user_profile WHERE id = 0")
    fun getProfileFlow(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 0")
    suspend fun getProfile(): UserProfileEntity?
}

@Dao
interface TelemetryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(activity: ActivityTelemetryEntity)

    @Query("SELECT * FROM activity_telemetry WHERE date = :date")
    suspend fun getActivity(date: String): ActivityTelemetryEntity?

    @Query("SELECT * FROM activity_telemetry WHERE date = :date")
    fun getActivityFlow(date: String): Flow<ActivityTelemetryEntity?>

    @Query("SELECT * FROM activity_telemetry")
    suspend fun getAllActivity(): List<ActivityTelemetryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivities(activities: List<ActivityTelemetryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleep(sleep: SleepTelemetryEntity)

    @Query("SELECT * FROM sleep_telemetry WHERE date = :date")
    suspend fun getSleep(date: String): SleepTelemetryEntity?

    @Query("SELECT * FROM sleep_telemetry WHERE date = :date")
    fun getSleepFlow(date: String): Flow<SleepTelemetryEntity?>

    @Query("SELECT * FROM sleep_telemetry ORDER BY date DESC LIMIT 7")
    fun getRecentSleepFlow(): Flow<List<SleepTelemetryEntity>>

    @Query("SELECT * FROM sleep_telemetry")
    suspend fun getAllSleep(): List<SleepTelemetryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleeps(sleeps: List<SleepTelemetryEntity>)
}

@Dao
interface NutritionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: NutritionEntryEntity)

    @Delete
    suspend fun deleteEntry(entry: NutritionEntryEntity)

    @Query("SELECT * FROM nutrition_entries WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay ORDER BY timestamp DESC")
    fun getEntriesForDayFlow(startOfDay: Long, endOfDay: Long): Flow<List<NutritionEntryEntity>>

    @Query("SELECT * FROM nutrition_entries WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay ORDER BY timestamp DESC")
    suspend fun getEntriesForDay(startOfDay: Long, endOfDay: Long): List<NutritionEntryEntity>

    @Query("SELECT SUM(calories) FROM nutrition_entries WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay")
    fun getCaloriesSumForDayFlow(startOfDay: Long, endOfDay: Long): Flow<Double?>

    @Query("SELECT SUM(proteinG) FROM nutrition_entries WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay")
    fun getProteinSumForDayFlow(startOfDay: Long, endOfDay: Long): Flow<Double?>

    @Query("SELECT SUM(carbsG) FROM nutrition_entries WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay")
    fun getCarbsSumForDayFlow(startOfDay: Long, endOfDay: Long): Flow<Double?>

    @Query("SELECT SUM(fatG) FROM nutrition_entries WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay")
    fun getFatSumForDayFlow(startOfDay: Long, endOfDay: Long): Flow<Double?>

    @Query("SELECT * FROM nutrition_entries")
    suspend fun getAllEntries(): List<NutritionEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<NutritionEntryEntity>)

    @Query("DELETE FROM nutrition_entries")
    suspend fun deleteAllEntries()
}

