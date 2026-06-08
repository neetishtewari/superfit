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
        NutritionEntryEntity::class,
        WorkoutEntryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class SuperfitDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun telemetryDao(): TelemetryDao
    abstract fun nutritionDao(): NutritionDao
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile
        private var INSTANCE: SuperfitDatabase? = null
        private var activeUserId: String? = null

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
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                activeUserId = currentUserId
                instance
            }
        }
    }
}
