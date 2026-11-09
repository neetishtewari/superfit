package com.superfit.app.data

import com.superfit.app.domain.ChatEntry
import com.superfit.app.domain.CoachChat
import com.superfit.app.domain.CoachNote
import com.superfit.app.domain.CoachNotes
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/**
 * The iPhone's copy of what the backup covers outside the database: the coach chat and notes
 * (one file each per account, like Android) and small per-account flags.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosBackupLocal(private val uid: String) : CloudBackupLocal {
    private val folder = iosAppFolder()
    private val chatFile = "$folder/coach_chat_$uid.json"
    private val notesFile = "$folder/coach_notes_$uid.json"
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun loadChat(): List<ChatEntry> = CoachChat.decode(read(chatFile))

    override fun saveChat(entries: List<ChatEntry>) = write(chatFile, CoachChat.encode(entries))

    override fun hasNotes(): Boolean = NSFileManager.defaultManager.fileExistsAtPath(notesFile)

    override fun loadNotes(): List<CoachNote> = CoachNotes.decode(read(notesFile))

    override fun saveNotes(notes: List<CoachNote>) = write(notesFile, CoachNotes.encode(notes))

    // Choices for Android's Health Connect workout import. The iPhone reads Apple Health instead
    // (step 5b-6), so there is nothing to put back here yet.
    override fun restoreWorkoutImportSettings(data: Map<String, Any>) = Unit

    override fun isEntryBackupRepaired(): Boolean = defaults.boolForKey("entry_backup_repaired_v1_$uid")

    override fun markEntryBackupRepaired() = defaults.setBool(true, forKey = "entry_backup_repaired_v1_$uid")

    private fun read(path: String): String? = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)

    private fun write(path: String, text: String) {
        @Suppress("CAST_NEVER_SUCCEEDS")
        (text as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }
}
