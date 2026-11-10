package com.superfit.app.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

/**
 * The on-device database, shared by the Android app and, later, the iPhone app.
 *
 * Opening it (file name per signed-in user, upgrades from older versions) is done by each app:
 * Android in SuperfitDatabaseBuilder.kt. The iPhone starts with a fresh database, so it needs no
 * upgrades.
 */
@Database(
    entities = [
        UserProfileEntity::class,
        ActivityTelemetryEntity::class,
        SleepTelemetryEntity::class,
        NutritionEntryEntity::class,
        WorkoutEntryEntity::class,
        WeightEntryEntity::class,
        StreakStateEntity::class,
        HabitEntryEntity::class,
        ImportedWorkoutEntity::class
    ],
    version = 10,
    exportSchema = true
)
@ConstructedBy(SuperfitDatabaseConstructor::class)
abstract class SuperfitDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun telemetryDao(): TelemetryDao
    abstract fun nutritionDao(): NutritionDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun weightDao(): WeightDao
    abstract fun streakDao(): StreakDao
    abstract fun habitDao(): HabitDao
    abstract fun importedWorkoutDao(): ImportedWorkoutDao

    // Each app adds its own opening code to this (Android: SuperfitDatabase.getDatabase).
    companion object
}

// Room writes the body of this for each platform.
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object SuperfitDatabaseConstructor : RoomDatabaseConstructor<SuperfitDatabase> {
    override fun initialize(): SuperfitDatabase
}
