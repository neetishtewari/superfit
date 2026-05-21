package com.superfit.app.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

class DataRepository(
    private val database: SuperfitDatabase,
    val healthConnectManager: HealthConnectManager
) {
    val profileFlow: Flow<UserProfileEntity?> = database.profileDao().getProfileFlow()

    suspend fun getProfile(): UserProfileEntity? = database.profileDao().getProfile()

    suspend fun saveProfile(profile: UserProfileEntity) {
        database.profileDao().insertProfile(profile)
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
    }

    suspend fun deleteNutritionEntry(entry: NutritionEntryEntity) {
        database.nutritionDao().deleteEntry(entry)
    }

    suspend fun syncHealthConnectTelemetry(date: LocalDate) {
        if (healthConnectManager.isAvailable() && healthConnectManager.hasAllPermissions()) {
            try {
                val activity = healthConnectManager.readDailyTelemetry(date)
                database.telemetryDao().insertActivity(activity)

                val sleep = healthConnectManager.readSleepTelemetry(date)
                if (sleep != null) {
                    database.telemetryDao().insertSleep(sleep)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
