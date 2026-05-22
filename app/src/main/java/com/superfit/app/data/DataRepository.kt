package com.superfit.app.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

class DataRepository(
    private val database: SuperfitDatabase,
    val healthConnectManager: HealthConnectManager
) {
    val firebaseSyncManager = FirebaseSyncManager(database)

    val profileFlow: Flow<UserProfileEntity?> = database.profileDao().getProfileFlow()

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
        database.clearAllTables()
    }

    suspend fun getAllNutritionEntries(): List<NutritionEntryEntity> {
        return database.nutritionDao().getAllEntries()
    }

    suspend fun getAllActivityTelemetry(): List<ActivityTelemetryEntity> {
        return database.telemetryDao().getAllActivity()
    }
}

