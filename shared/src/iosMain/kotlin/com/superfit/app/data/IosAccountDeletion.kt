package com.superfit.app.data

import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSFileManager

/**
 * Settings › Delete account on the iPhone, in Android's order: the cloud backup first (it can't be
 * reached once the account is gone), then the account itself, then everything this account kept on
 * the phone. Answers the line to show afterwards; signing out is left to the caller.
 */
class IosAccountDeletion(
    private val auth: IosAuth,
    private val backup: CloudBackup,
    private val database: SuperfitDatabase,
    private val local: IosBackupLocal,
    private val uid: String
) {
    /** Null when nothing was deleted (no internet): the person stays signed in and can try again. */
    suspend fun delete(): String? {
        if (!backup.clearAllCloudData()) return null
        val result = suspendCancellableCoroutine { c -> auth.deleteAccount { c.resume(it) } }
        clearPhone()
        return when (result) {
            "DELETED" -> "Your account and data are deleted."
            "NEEDS_RECENT_SIGN_IN" -> "Your data is deleted. For your security, sign in once more and tap Delete account again to remove the account."
            else -> "Your data is deleted, but the account couldn't be removed. Sign in and tap Delete account again."
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun clearPhone() {
        runCatching { database.close() }
        val files = NSFileManager.defaultManager
        val db = "${iosAppFolder()}/superfit_database_$uid.db"
        listOf(db, "$db-wal", "$db-shm").forEach { files.removeItemAtPath(it, error = null) }
        local.clearChat()
        IosPrefs.forUser(uid).clear()
    }

    companion object {
        const val NOT_DELETED = "Couldn't finish deleting your data. Check your internet connection and try again."
    }
}
