package com.superfit.app.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
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
 * Opens the database of the person signed in now, one file per account like on Android. The
 * iPhone starts at the current version, so it needs none of Android's upgrade steps.
 */
fun openIosDatabase(uid: String): SuperfitDatabase =
    Room.databaseBuilder<SuperfitDatabase>(name = "${iosAppFolder()}/superfit_database_$uid.db")
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
