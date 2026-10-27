package com.superfit.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * One message in the coach chat. Saved on the phone and backed up with the user's account, so
 * the conversation is still there the next time the chat opens.
 */
data class ChatEntry(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val time: Long,
    /** The user said this with the mic instead of typing it. */
    val spoken: Boolean = false,
    /** Notes the coach saved from this exchange, shown as "Remembered: …" with an Undo. */
    val rememberedIds: List<String> = emptyList(),
    /** Notes this exchange made out of date ("my knee is fine now"), kept so Undo can bring them back. */
    val forgotten: List<CoachNote> = emptyList(),
    /** A meal the coach suggested, which "Log this" sends to the meal log. */
    val mealToLog: String? = null,
    val mealLogged: Boolean = false,
    /** The AI couldn't be reached. Shown, but never saved or sent back to the AI. */
    val isError: Boolean = false
)

/** One turn of the conversation as the AI sees it. */
data class AiTurn(val fromUser: Boolean, val text: String)

object CoachChat {

    /** The chat keeps this many messages; older ones live on only as the coach's notes. */
    const val KEEP = 300

    /** How far back the AI reads the conversation word for word. */
    const val HISTORY_DAYS = 3L
    const val HISTORY_TURNS = 24

    /** Longest message saved, so one huge paste can't fill the backup. */
    const val MAX_TEXT = 4000

    const val TELL_ME_MORE = "Tell me more"

    fun encode(entries: List<ChatEntry>): String = JSONArray().apply {
        entries.filterNot { it.isError }.takeLast(KEEP).forEach { e ->
            put(
                JSONObject().apply {
                    put("id", e.id)
                    put("u", e.fromUser)
                    put("t", e.text.take(MAX_TEXT))
                    put("at", e.time)
                    if (e.spoken) put("v", true)
                    if (e.rememberedIds.isNotEmpty()) put("rem", JSONArray(e.rememberedIds))
                    if (e.forgotten.isNotEmpty()) put("fgt", JSONArray(CoachNotes.encode(e.forgotten)))
                    e.mealToLog?.let { put("log", it) }
                    if (e.mealLogged) put("logged", true)
                }
            )
        }
    }.toString()

    fun decode(raw: String?): List<ChatEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val text = o.optString("t", "")
            if (text.isBlank()) return@mapNotNull null
            ChatEntry(
                id = o.optLong("id", o.optLong("at", 0L)),
                fromUser = o.optBoolean("u", false),
                text = text,
                time = o.optLong("at", 0L),
                spoken = o.optBoolean("v", false),
                rememberedIds = o.optJSONArray("rem")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
                forgotten = CoachNotes.decode(o.optJSONArray("fgt")?.toString()),
                mealToLog = o.optString("log", "").ifBlank { null },
                mealLogged = o.optBoolean("logged", false)
            )
        }
    }

    /** Joins the phone's chat with a backed-up copy: every message once, oldest first. */
    fun merge(local: List<ChatEntry>, cloud: List<ChatEntry>): List<ChatEntry> =
        (local + cloud).distinctBy { it.id }.sortedBy { it.time }.takeLast(KEEP)

    /**
     * The recent conversation for the AI: the last few days, word for word, oldest first. The AI
     * needs turns to alternate and to start with the user, so back-to-back messages from the same
     * side (a reply that failed) are joined.
     */
    fun historyForAi(entries: List<ChatEntry>, now: Long): List<AiTurn> {
        val since = now - HISTORY_DAYS * 24 * 3600 * 1000
        val turns = mutableListOf<AiTurn>()
        entries.filter { !it.isError && it.time >= since }.takeLast(HISTORY_TURNS).forEach { e ->
            val last = turns.lastOrNull()
            if (last != null && last.fromUser == e.fromUser) {
                turns[turns.lastIndex] = last.copy(text = last.text + "\n" + e.text)
            } else {
                turns += AiTurn(e.fromUser, e.text)
            }
        }
        while (turns.isNotEmpty() && !turns.first().fromUser) turns.removeAt(0)
        // The question being asked now is sent on its own, so the history ends with the coach.
        if (turns.isNotEmpty() && turns.last().fromUser) turns.removeAt(turns.lastIndex)
        return turns
    }

    /**
     * What the user asked about before the word-for-word history, so the coach can refer back
     * ("last week you asked about knee pain"). Newest first.
     */
    fun earlierTopics(entries: List<ChatEntry>, now: Long, zone: ZoneId = ZoneId.systemDefault(), limit: Int = 8): List<String> {
        val since = now - HISTORY_DAYS * 24 * 3600 * 1000
        val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
        return entries
            .filter { it.fromUser && it.time < since && it.text != TELL_ME_MORE }
            .takeLast(limit)
            .reversed()
            .map { e ->
                val day = Instant.ofEpochMilli(e.time).atZone(zone).toLocalDate().format(dayFormat)
                "$day: ${e.text.replace('\n', ' ').take(120)}"
            }
    }

    /** The coach's answer and, when it suggested one meal, that meal on its own for "Log this". */
    data class Reply(val text: String, val mealToLog: String?)

    private val logLine = Regex("""^\W*LOG\W*:\s*(.+)$""", RegexOption.IGNORE_CASE)

    fun parseReply(raw: String): Reply {
        val lines = raw.trim().lines()
        var meal: String? = null
        val kept = lines.filter { line ->
            val match = logLine.find(line.trim())
            if (match != null) {
                meal = match.groupValues[1].trim().trim('*', '_', '"', '.', ' ').ifBlank { null }
                false
            } else {
                true
            }
        }
        return Reply(kept.joinToString("\n").trim(), meal?.take(200))
    }

    /** "Today", "Yesterday" or "Fri 23 Oct", for the line between days in the chat. */

    /** The user's question that the coach reply at [index] answers, or null for a user message or an error. */
    fun questionFor(entries: List<ChatEntry>, index: Int): String? {
        val entry = entries.getOrNull(index) ?: return null
        if (entry.fromUser || entry.isError) return null
        return entries.getOrNull(index - 1)?.takeIf { it.fromUser }?.text
    }

    /**
     * True when [question] asks for a roast: the Roast button, or the same ask typed or spoken
     * ("roast", "Roast my day!", "roast me please").
     */
    fun isRoastRequest(question: String?): Boolean {
        if (question == null) return false
        val words = question.lowercase(Locale.ENGLISH).replace(Regex("[^a-z ]"), " ").split(' ').filter { it.isNotBlank() }
        if (words == listOf("roast")) return true
        return words.windowed(2).any { it == listOf("roast", "me") } || words.windowed(3).any { it == listOf("roast", "my", "day") }
    }

    fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
    }
}
