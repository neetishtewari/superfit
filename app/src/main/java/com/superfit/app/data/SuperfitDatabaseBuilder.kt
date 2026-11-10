package com.superfit.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Android's way of opening the shared SuperfitDatabase: one file per signed-in user, and the
// upgrades that carry phones on older app versions forward without losing data.

/** Opens (or reuses) the database of the person signed in now. */
fun SuperfitDatabase.Companion.getDatabase(context: Context): SuperfitDatabase =
    SuperfitDatabaseHolder.getDatabase(context)

/** Every upgrade step, oldest first. */
val SuperfitDatabase.Companion.ALL_MIGRATIONS: Array<Migration>
    get() = SuperfitMigrations.ALL_MIGRATIONS

private object SuperfitDatabaseHolder {
    @Volatile
    private var instance: SuperfitDatabase? = null
    private var activeUserId: String? = null

    fun getDatabase(context: Context): SuperfitDatabase {
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
        return instance?.takeIf { activeUserId == currentUserId } ?: synchronized(this) {
            instance?.let {
                if (it.isOpen) {
                    it.close()
                }
            }
            val db = Room.databaseBuilder(
                context.applicationContext,
                SuperfitDatabase::class.java,
                "superfit_database_$currentUserId"
            )
                .addMigrations(*SuperfitMigrations.ALL_MIGRATIONS)
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build()
            instance = db
            activeUserId = currentUserId
            db
        }
    }
}

internal object SuperfitMigrations {
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

    // Meal quality tags for Diet Quality and the coach. Older meals keep NULL until tagged.
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `nutrition_entries` ADD COLUMN `qualityTags` TEXT")
        }
    }

    // Each meal's separate foods, so amounts and oil/sugar can be fixed after logging.
    // Older meals keep NULL and open as a single serving.
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `nutrition_entries` ADD COLUMN `items` TEXT")
        }
    }

    // Workouts from other apps through Health Connect. Starts empty; the first import fills it.
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `imported_workouts` (`id` TEXT NOT NULL, `sourcePackage` TEXT NOT NULL, " +
                    "`exerciseType` INTEGER NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER NOT NULL, " +
                    "`distanceMeters` REAL, `totalKcal` REAL, `avgHeartRate` INTEGER, `detailsRead` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
            )
        }
    }

    // A cloud id for every meal, workout and weight, so two devices on one account never
    // overwrite each other's backups. Existing entries keep their number as their cloud id,
    // because that is the name their backup already has.
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            for (table in listOf("nutrition_entries", "workout_entries", "weight_entries")) {
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `cloudId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE `$table` SET `cloudId` = CAST(`id` AS TEXT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_cloudId` ON `$table` (`cloudId`)")
            }
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `activity_telemetry` ADD COLUMN `distanceMeters` REAL")
        }
    }

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_5,
        MIGRATION_4_5,
        MIGRATION_1_5,
        MIGRATION_2_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10
    )
}
