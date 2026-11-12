package com.superfit.app.ui.coach

import com.superfit.app.data.CloudBackup
import com.superfit.app.data.CloudBackupLocal
import com.superfit.app.data.Now
import com.superfit.app.domain.Ai
import com.superfit.app.domain.AiChatTurn
import com.superfit.app.domain.AiFailover
import com.superfit.app.domain.AppLog
import com.superfit.app.domain.ChatEntry
import com.superfit.app.domain.CoachChat
import com.superfit.app.domain.CoachNote
import com.superfit.app.domain.CoachNotes
import com.superfit.app.domain.CoachStyle
import com.superfit.app.domain.NoteUpdate
import com.superfit.app.ui.dashboard.CoachChatPrompt
import com.superfit.app.ui.dashboard.TodayModel
import com.superfit.app.ui.dashboard.VoiceLog
import com.superfit.app.ui.dashboard.VoiceRecognizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The coach chat for the iPhone, working the same way as Android's: the conversation and the
 * coach's notes are kept on the phone ([local]) and backed up ([backup]); each question is
 * answered from the person's own day, logs and notes ([today]); afterwards the coach picks out
 * anything worth remembering, which the person can undo.
 */
class CoachChatModel(
    private val local: CloudBackupLocal,
    private val clearLocalChat: () -> Unit,
    private val backup: CloudBackup?,
    private val today: TodayModel,
    private val scope: CoroutineScope,
    voice: VoiceRecognizer? = null,
    private val clock: () -> Long = Now::millis
) {
    private val _messages = MutableStateFlow(local.loadChat())
    val messages: StateFlow<List<ChatEntry>> = _messages.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _notes = MutableStateFlow(local.loadNotes())
    val notes: StateFlow<List<CoachNote>> = _notes.asStateFlow()

    val style: StateFlow<CoachStyle> get() = today.coachStyle

    private var lastId = _messages.value.maxOfOrNull { it.id } ?: 0L

    /** The chat's mic: what was heard is sent as the question. */
    val voiceLog: VoiceLog? = voice?.let { recognizer -> VoiceLog(recognizer) { heard, _ -> send(heard.text, how = "voice") } }

    fun questions(): List<String> = today.coachQuestions()

    private fun newId(): Long = maxOf(clock(), lastId + 1).also { lastId = it }

    private fun persist() {
        val entries = _messages.value
        local.saveChat(entries)
        scope.launch { backup?.uploadCoachChat(CoachChat.encode(entries)) }
    }

    private fun update(id: Long, change: (ChatEntry) -> ChatEntry) {
        _messages.value = _messages.value.map { if (it.id == id) change(it) else it }
        persist()
    }

    private fun addError(message: String) {
        _messages.value = _messages.value + ChatEntry(id = newId(), fromUser = false, text = message, time = clock(), isError = true)
    }

    /** Asks the coach [text]; [how] is "typed", "voice" or "chip". */
    fun send(text: String, how: String = "typed") {
        val question = text.trim().take(CoachChat.MAX_TEXT)
        if (question.isBlank() || _loading.value) return
        val before = _messages.value.filterNot { it.isError }
        _messages.value = _messages.value + ChatEntry(id = newId(), fromUser = true, text = question, time = clock(), spoken = how == "voice")
        persist()
        _loading.value = true
        scope.launch {
            try {
                val facts = today.coachChatFacts(_notes.value, before)
                if (facts == null) {
                    addError("Your day is still loading. Try again in a moment.")
                    return@launch
                }
                val history = CoachChat.historyForAi(before, clock()).map { AiChatTurn(fromUser = it.fromUser, text = it.text) }
                val raw = Ai.chat(systemInstructionText = CoachChatPrompt.instruction(facts), history = history, message = question)
                val reply = CoachChat.parseReply(raw)
                val coachEntry = ChatEntry(
                    id = newId(),
                    fromUser = false,
                    text = reply.text.ifBlank { raw.trim() },
                    time = clock(),
                    mealToLog = reply.mealToLog
                )
                _messages.value = _messages.value + coachEntry
                persist()
                rememberFrom(question, coachEntry)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(TAG, "Chat message failed", e)
                addError(AiFailover.sanitizeError(e))
            } finally {
                _loading.value = false
            }
        }
    }

    /** Picks out anything worth remembering from one exchange and shows it under the reply. */
    private fun rememberFrom(question: String, reply: ChatEntry) {
        if (question.length < 12 || question == CoachChat.TELL_ME_MORE) return
        scope.launch {
            try {
                val day = Now.today()
                val response = Ai.generate(
                    prompt = CoachNotes.extractPrompt(_notes.value, day, question, reply.text),
                    systemInstructionText = CoachNotes.EXTRACT_INSTRUCTION,
                    isJson = true
                )
                val noteUpdate = CoachNotes.parseUpdate(response, _notes.value, day, clock())
                if (noteUpdate.isEmpty) return@launch
                val current = _notes.value
                saveNotes(CoachNotes.apply(current, noteUpdate))
                update(reply.id) {
                    it.copy(rememberedIds = noteUpdate.add.map { n -> n.id }, forgotten = current.filter { n -> n.id in noteUpdate.outdatedIds })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(TAG, "Picking out coach notes failed", e)
            }
        }
    }

    private fun saveNotes(notes: List<CoachNote>) {
        _notes.value = notes
        local.saveNotes(notes)
        scope.launch { backup?.uploadCoachNotes(CoachNotes.encode(notes)) }
    }

    /** Undo on "Remembered: …": takes back the notes that reply saved and brings back any it retired. */
    fun undoNotes(entryId: Long) {
        val entry = _messages.value.firstOrNull { it.id == entryId } ?: return
        val current = _notes.value
        saveNotes(current.filterNot { it.id in entry.rememberedIds } + entry.forgotten.filter { old -> current.none { it.id == old.id } })
        update(entryId) { it.copy(rememberedIds = emptyList(), forgotten = emptyList()) }
    }

    /** "Log this" under a coach reply: the suggested meal goes through the normal meal log. */
    fun logSuggestedMeal(entryId: Long) {
        val entry = _messages.value.firstOrNull { it.id == entryId } ?: return
        val meal = entry.mealToLog ?: return
        if (entry.mealLogged) return
        today.logMeal(meal)
        update(entryId) { it.copy(mealLogged = true) }
    }

    fun deleteNote(id: String) = saveNotes(_notes.value.filterNot { it.id == id })

    /** Adds what the person typed or pasted in Settings; each line becomes its own note. */
    fun addNote(text: String) {
        val added = CoachNotes.fromUserText(text, Now.today(), clock())
        if (added.isEmpty()) return
        saveNotes(CoachNotes.apply(_notes.value, NoteUpdate(added, emptyList())))
    }

    /** Clears the conversation on the phone and in the backup. The coach's notes stay. */
    fun clearHistory() {
        _messages.value = emptyList()
        clearLocalChat()
        scope.launch { backup?.deleteCoachChat() }
    }

    private companion object {
        const val TAG = "CoachChat"
    }
}
