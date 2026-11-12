package com.superfit.app.data

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * The iPhone's own folder for Superfit's files (Application Support, private to the app and
 * cleared when the app is deleted). Everything is also in the cloud backup.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun iosAppFolder(): String {
    val url: NSURL? = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null
    )
    return requireNotNull(url?.path) { "No Application Support folder" }
}

/**
 * Birth month and year (version 10), for iPhone test installs made at version 9. Same change as
 * Android's MIGRATION_9_10.
 */
private val IOS_MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `user_profile` ADD COLUMN `birthYearMonth` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Opens the database of the person signed in now, one file per account like on Android. The
 * iPhone started at version 9, so it needs none of Android's older upgrade steps.
 */
fun openIosDatabase(uid: String): SuperfitDatabase =
    Room.databaseBuilder<SuperfitDatabase>(name = "${iosAppFolder()}/superfit_database_$uid.db")
        .setDriver(BundledSQLiteDriver())
        .addMigrations(IOS_MIGRATION_9_10)
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
