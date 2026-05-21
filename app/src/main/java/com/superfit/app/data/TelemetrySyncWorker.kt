package com.superfit.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalDate

class TelemetrySyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = SuperfitDatabase.getDatabase(applicationContext)
        val healthConnectManager = HealthConnectManager(applicationContext)

        if (!healthConnectManager.isAvailable() || !healthConnectManager.hasAllPermissions()) {
            return Result.failure()
        }

        try {
            val telemetryDao = database.telemetryDao()
            val today = LocalDate.now()

            // Sync today's activity telemetry
            val todayActivity = healthConnectManager.readDailyTelemetry(today)
            telemetryDao.insertActivity(todayActivity)

            // Sync yesterday's activity telemetry to ensure final values are captured
            val yesterday = today.minusDays(1)
            val yesterdayActivity = healthConnectManager.readDailyTelemetry(yesterday)
            telemetryDao.insertActivity(yesterdayActivity)

            // Sync today's sleep telemetry
            val todaySleep = healthConnectManager.readSleepTelemetry(today)
            if (todaySleep != null) {
                telemetryDao.insertSleep(todaySleep)
            }

            // Sync yesterday's sleep telemetry
            val yesterdaySleep = healthConnectManager.readSleepTelemetry(yesterday)
            if (yesterdaySleep != null) {
                telemetryDao.insertSleep(yesterdaySleep)
            }

            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }
}
