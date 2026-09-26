package com.superfit.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserProfileEntity::class,
        ActivityTelemetryEntity::class,
        SleepTelemetryEntity::class,
        NutritionEntryEntity::class,
        WorkoutEntryEntity::class,
        WeightEntryEntity::class,
        StreakStateEntity::class,
        HabitEntryEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class SuperfitDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun telemetryDao(): TelemetryDao
    abstract fun nutritionDao(): NutritionDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun weightDao(): WeightDao
    abstract fun streakDao(): StreakDao
    abstract fun habitDao(): HabitDao

    companion object {
        @Volatile
        private var INSTANCE: SuperfitDatabase? = null
        private var activeUserId: String? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Schema integrity check bump, no table alterations
            }
        }

        // Creates workout_entries as it was at version 3, before difficultyRating existed;
        // MIGRATION_3_5 adds that column, so the 1->5 and 2->5 paths must not create it here.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_entries` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`description` TEXT NOT NULL, " +
                    "`caloriesBurned` REAL NOT NULL, " +
                    "`workoutType` TEXT NOT NULL, " +
                    "`setsCount` INTEGER NOT NULL, " +
                    "`repsCount` INTEGER NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL)"
                )
            }
        }

        val MIGRATION_3_5 = object : Migration(3, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add new columns to user_profile
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `startingWeightKg` REAL NOT NULL DEFAULT 75.0")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `targetWeightKg` REAL NOT NULL DEFAULT 70.0")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `startDateTimestamp` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `fitnessLevel` TEXT NOT NULL DEFAULT 'INTERMEDIATE'")
                db.execSQL("UPDATE `user_profile` SET `startingWeightKg` = `weightKg` WHERE `weightKg` > 0")

                // 2. Add new column to workout_entries
                db.execSQL("ALTER TABLE `workout_entries` ADD COLUMN `difficultyRating` TEXT NOT NULL DEFAULT 'JUST_RIGHT'")

                // 3. Create newly introduced tables
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `weight_entries` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`weightKg` REAL NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`note` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `streak_state` (" +
                    "`id` INTEGER NOT NULL, " +
                    "`currentStreak` INTEGER NOT NULL, " +
                    "`longestStreak` INTEGER NOT NULL, " +
                    "`masteryStreak` INTEGER NOT NULL, " +
                    "`graceDaysRemaining` INTEGER NOT NULL, " +
                    "`lastLoggedDate` TEXT NOT NULL, " +
                    "`lastGraceUsedDate` TEXT NOT NULL, " +
                    "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `habit_entries` (" +
                    "`date` TEXT NOT NULL, " +
                    "`waterMl` INTEGER NOT NULL, " +
                    "`workoutMins` INTEGER NOT NULL, " +
                    "`cleanEatsCompleted` INTEGER NOT NULL, " +
                    "`stepsCompleted` INTEGER NOT NULL, " +
                    "`sleepCompleted` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`date`))"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_3_5.migrate(db)
            }
        }

        val MIGRATION_1_5 = object : Migration(1, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_5.migrate(db)
            }
        }

        val MIGRATION_2_5 = object : Migration(2, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_5.migrate(db)
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_5,
            MIGRATION_4_5,
            MIGRATION_1_5,
            MIGRATION_2_5
        )

        fun getDatabase(context: Context): SuperfitDatabase {
            val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
            return INSTANCE?.takeIf { activeUserId == currentUserId } ?: synchronized(this) {
                INSTANCE?.let {
                    if (it.isOpen) {
                        it.close()
                    }
                }
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SuperfitDatabase::class.java,
                    "superfit_database_$currentUserId"
                )
                .addMigrations(*ALL_MIGRATIONS)
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build()
                INSTANCE = instance
                activeUserId = currentUserId
                instance
            }
        }
    }
}
