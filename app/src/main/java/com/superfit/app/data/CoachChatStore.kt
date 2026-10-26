package com.superfit.app.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.superfit.app.domain.ChatEntry
import com.superfit.app.domain.CoachChat
import com.superfit.app.domain.CoachNote
import com.superfit.app.domain.CoachNotes
import java.io.File

/**
 * The coach chat and the coach's notes about the user, kept on the phone for the signed-in user.
 * The chat is a file (it can grow to a few hundred messages); the notes sit with the user's
 * other settings, so deleting the account clears them with everything else.
 */
object CoachChatStore {

    private const val KEY_NOTES = "coach_notes"
    private val lock = Any()

    private fun chatFile(context: Context): File {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
        return File(context.filesDir, "coach_chat_$userId.json")
    }

    fun loadChat(context: Context): List<ChatEntry> = synchronized(lock) {
        val file = chatFile(context)
        if (!file.exists()) return emptyList()
        CoachChat.decode(runCatching { file.readText() }.getOrNull())
    }

    /** Saves the chat and returns what was saved, for the cloud backup. */
    fun saveChat(context: Context, entries: List<ChatEntry>): String = synchronized(lock) {
        val json = CoachChat.encode(entries)
        val file = chatFile(context)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(file)) {
            file.writeText(json)
            tmp.delete()
        }
        json
    }

    fun clearChat(context: Context) = synchronized(lock) {
        chatFile(context).delete()
        Unit
    }

    fun hasNotes(context: Context): Boolean = getUserSharedPrefs(context).contains(KEY_NOTES)

    fun loadNotes(context: Context): List<CoachNote> = CoachNotes.decode(getUserSharedPrefs(context).getString(KEY_NOTES, null))

    /** Saves the notes and returns what was saved, for the cloud backup. */
    fun saveNotes(context: Context, notes: List<CoachNote>): String {
        val json = CoachNotes.encode(notes)
        getUserSharedPrefs(context).edit().putString(KEY_NOTES, json).apply()
        return json
    }
}
