package com.superfit.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserProfileEntity::class,
        ActivityTelemetryEntity::class,
        SleepTelemetryEntity::class,
        NutritionEntryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class SuperfitDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun telemetryDao(): TelemetryDao
    abstract fun nutritionDao(): NutritionDao

    companion object {
        @Volatile
        private var INSTANCE: SuperfitDatabase? = null

        fun getDatabase(context: Context): SuperfitDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SuperfitDatabase::class.java,
                    "superfit_database"
                )
                .fallbackToDestructiveMigration() // Simple strategy for development updates
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
