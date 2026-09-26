package com.superfit.app.data

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Builds databases at each schema version real installs have shipped with, then opens them
 * with the current [SuperfitDatabase]. Room runs the registered migrations and validates the
 * result against the current entities, so a migration that crashes or leaves the schema out
 * of line with Entities.kt fails here instead of on a user's phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SuperfitDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun migratesFromVersion1() {
        createDatabase(version = 1, statements = V2_SCHEMA)
        assertMigratedWithDataIntact(expectWorkoutRow = false)
    }

    @Test
    fun migratesFromVersion2() {
        createDatabase(version = 2, statements = V2_SCHEMA)
        assertMigratedWithDataIntact(expectWorkoutRow = false)
    }

    @Test
    fun migratesFromVersion3() {
        createDatabase(version = 3, statements = V2_SCHEMA + V3_WORKOUT_TABLE)
        rawDatabase().use { db ->
            db.execSQL(
                "INSERT INTO workout_entries (description, caloriesBurned, workoutType, setsCount, repsCount, timestamp) " +
                    "VALUES ('Run 5k', 320.0, 'Cardio', 0, 0, 1700000000000)"
            )
        }
        assertMigratedWithDataIntact(expectWorkoutRow = true)
    }

    private fun rawDatabase(): SQLiteDatabase =
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(TEST_DB).apply { parentFile?.mkdirs() }, null)

    private fun createDatabase(version: Int, statements: List<String>) {
        rawDatabase().use { db ->
            statements.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO user_profile (id, age, heightCm, weightKg, isMale, activityMultiplier, goal, calorieOffset) " +
                    "VALUES (0, 34, 178.0, 82.5, 1, 1.375, 'LOSE_WEIGHT', -500)"
            )
            db.execSQL(
                "INSERT INTO nutrition_entries (foodText, calories, proteinG, carbsG, fatG, timestamp) " +
                    "VALUES ('Boiled Egg', 78.0, 6.3, 0.6, 5.3, 1700000000000)"
            )
            db.version = version
        }
    }

    private fun assertMigratedWithDataIntact(expectWorkoutRow: Boolean) {
        val database = Room.databaseBuilder(context, SuperfitDatabase::class.java, TEST_DB)
            .addMigrations(*SuperfitDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            // Opening the database runs the migrations and Room's schema validation.
            val db = database.openHelper.writableDatabase
            assertEquals(5, db.version)

            db.query("SELECT weightKg, startingWeightKg, fitnessLevel FROM user_profile WHERE id = 0").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(82.5, cursor.getDouble(0), 0.0)
                assertEquals(82.5, cursor.getDouble(1), 0.0)
                assertEquals("INTERMEDIATE", cursor.getString(2))
            }
            db.query("SELECT COUNT(*) FROM nutrition_entries").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            db.query("SELECT difficultyRating FROM workout_entries").use { cursor ->
                assertEquals(if (expectWorkoutRow) 1 else 0, cursor.count)
                if (expectWorkoutRow) {
                    cursor.moveToFirst()
                    assertEquals("JUST_RIGHT", cursor.getString(0))
                }
            }
        } finally {
            database.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"

        // Tables as Room created them at versions 1 and 2 (identical schemas).
        val V2_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `user_profile` (`id` INTEGER NOT NULL, `age` INTEGER NOT NULL, " +
                "`heightCm` REAL NOT NULL, `weightKg` REAL NOT NULL, `isMale` INTEGER NOT NULL, " +
                "`activityMultiplier` REAL NOT NULL, `goal` TEXT NOT NULL, `calorieOffset` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `activity_telemetry` (`date` TEXT NOT NULL, `steps` INTEGER NOT NULL, " +
                "`activeCalories` REAL NOT NULL, PRIMARY KEY(`date`))",
            "CREATE TABLE IF NOT EXISTS `sleep_telemetry` (`date` TEXT NOT NULL, " +
                "`sleepDurationSeconds` INTEGER NOT NULL, `deepSleepDurationSeconds` INTEGER NOT NULL, " +
                "`readinessScore` INTEGER NOT NULL, PRIMARY KEY(`date`))",
            "CREATE TABLE IF NOT EXISTS `nutrition_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`foodText` TEXT NOT NULL, `calories` REAL NOT NULL, `proteinG` REAL NOT NULL, " +
                "`carbsG` REAL NOT NULL, `fatG` REAL NOT NULL, `timestamp` INTEGER NOT NULL)"
        )

        // workout_entries as Room created it at version 3.
        val V3_WORKOUT_TABLE = listOf(
            "CREATE TABLE IF NOT EXISTS `workout_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`description` TEXT NOT NULL, `caloriesBurned` REAL NOT NULL, `workoutType` TEXT NOT NULL, " +
                "`setsCount` INTEGER NOT NULL, `repsCount` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)"
        )
    }
}
