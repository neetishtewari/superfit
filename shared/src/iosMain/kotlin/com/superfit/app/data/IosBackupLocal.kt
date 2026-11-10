package com.superfit.app.data

import com.superfit.app.domain.ChatEntry
import com.superfit.app.domain.CoachChat
import com.superfit.app.domain.CoachNote
import com.superfit.app.domain.CoachNotes
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/**
 * The iPhone's copy of what the backup covers outside the database: the coach chat (one file per
 * account) and, in the account's settings like on Android, the coach's notes, the choices for
 * workouts from other apps and small flags.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosBackupLocal(private val uid: String) : CloudBackupLocal {
    private val folder = iosAppFolder()
    private val chatFile = "$folder/coach_chat_$uid.json"
    private val prefs = IosPrefs.forUser(uid)

    override fun loadChat(): List<ChatEntry> = CoachChat.decode(read(chatFile))

    override fun saveChat(entries: List<ChatEntry>) = write(chatFile, CoachChat.encode(entries))

    override fun hasNotes(): Boolean = prefs.contains(KEY_NOTES)

    override fun loadNotes(): List<CoachNote> = CoachNotes.decode(prefs.getString(KEY_NOTES, null))

    override fun saveNotes(notes: List<CoachNote>) = prefs.edit { putString(KEY_NOTES, CoachNotes.encode(notes)) }

    // Kept for when the iPhone reads workouts from Apple Health (step 5b-6).
    override fun restoreWorkoutImportSettings(data: Map<String, Any>) = WorkoutImportPrefs(prefs).restore(data)

    override fun isEntryBackupRepaired(): Boolean = prefs.getBoolean(KEY_REPAIRED, false)

    override fun markEntryBackupRepaired() = prefs.edit { putBoolean(KEY_REPAIRED, true) }

    private fun read(path: String): String? = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)

    private fun write(path: String, text: String) {
        @Suppress("CAST_NEVER_SUCCEEDS")
        (text as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    private companion object {
        // The same keys as Android's.
        const val KEY_NOTES = "coach_notes"
        const val KEY_REPAIRED = "entry_backup_repaired_v1"
    }
}
