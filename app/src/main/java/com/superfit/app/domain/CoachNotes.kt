package com.superfit.app.domain

import com.superfit.app.data.NutritionEntryEntity
import com.superfit.app.data.WorkoutEntryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * How long a note matters. LASTING stays until the user removes it (vegetarian on Tuesdays, a
 * knee problem). SHORT fades after a week (a cold, travelling). PLAN is something the user said
 * they'd do; the daily card checks the logs for it and it fades after two weeks.
 */
enum class NoteKind { LASTING, SHORT, PLAN }

/** One thing the coach remembers about the user, listed in Settings under "What my coach knows". */
data class CoachNote(
    val id: String,
    val text: String,
    val kind: NoteKind,
    val added: LocalDate,
    /** The user typed it in Settings rather than the coach picking it out of a chat. */
    val byUser: Boolean = false,
    /** For a PLAN: words from the user's own message to look for in their logs ("egg"). */
    val lookFor: List<String> = emptyList(),
    /** For a PLAN: which log to check. */
    val planLog: PlanLog? = null
) {
    val expires: LocalDate?
        get() = when (kind) {
            NoteKind.LASTING -> null
            NoteKind.SHORT -> added.plusDays(CoachNotes.SHORT_DAYS)
            NoteKind.PLAN -> added.plusDays(CoachNotes.PLAN_DAYS)
        }

    fun isActive(today: LocalDate): Boolean = expires?.let { today < it } ?: true
}

enum class PlanLog { MEAL, WORKOUT }

/** How a plan the user told the coach about is going, counted from their logs. */
data class PlanCheck(val daysLogged: Int, val daysDone: Int)

/** What the AI picked out of one exchange: notes to add, and existing notes it no longer holds true. */
data class NoteUpdate(val add: List<CoachNote>, val outdatedIds: List<String>) {
    val isEmpty: Boolean get() = add.isEmpty() && outdatedIds.isEmpty()
}

object CoachNotes {

    const val SHORT_DAYS = 7L
    const val PLAN_DAYS = 14L
    const val MAX_NOTES = 40
    const val MAX_TEXT = 160

    /** Notes the user types in Settings can be longer, and one paste can hold several. */
    const val MAX_USER_TEXT = 600
    const val MAX_USER_PASTE = 3000

    /** Most chat messages give at most [MAX_NEW]; a message that tells a lot about the user at once can give more. */
    const val MAX_NEW = 2
    const val MAX_NEW_LONG = 6

    fun encode(notes: List<CoachNote>): String = JSONArray().apply {
        notes.forEach { n ->
            put(
                JSONObject().apply {
                    put("id", n.id)
                    put("text", n.text)
                    put("kind", n.kind.name)
                    put("added", n.added.toString())
                    if (n.byUser) put("byUser", true)
                    if (n.lookFor.isNotEmpty()) put("lookFor", JSONArray(n.lookFor))
                    n.planLog?.let { put("log", it.name) }
                }
            )
        }
    }.toString()

    fun decode(raw: String?): List<CoachNote> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val text = o.optString("text", "").trim()
            val added = runCatching { LocalDate.parse(o.optString("added")) }.getOrNull()
            if (text.isEmpty() || added == null) return@mapNotNull null
            CoachNote(
                id = o.optString("id", "").ifBlank { "n${added.toEpochDay()}_$i" },
                text = text,
                kind = runCatching { NoteKind.valueOf(o.optString("kind")) }.getOrDefault(NoteKind.LASTING),
                added = added,
                byUser = o.optBoolean("byUser", false),
                lookFor = o.optJSONArray("lookFor")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
                planLog = runCatching { PlanLog.valueOf(o.optString("log")) }.getOrNull()
            )
        }
    }

    fun active(notes: List<CoachNote>, today: LocalDate): List<CoachNote> = notes.filter { it.isActive(today) }

    /** Adds [update]'s notes and removes its outdated ones. Repeats of an existing note are skipped. */
    fun apply(notes: List<CoachNote>, update: NoteUpdate): List<CoachNote> {
        val kept = notes.filterNot { it.id in update.outdatedIds }
        val fresh = update.add.filter { new -> kept.none { same(it.text, new.text) } }
        return (kept + fresh).takeLast(MAX_NOTES)
    }

    private fun same(a: String, b: String): Boolean {
        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        return norm(a) == norm(b)
    }

    fun newId(now: Long, index: Int): String = "n${now}_$index"

    /**
     * Notes the user typed or pasted in Settings: each line (or bullet) becomes its own note, so a
     * pasted list stays readable and each item can be removed on its own.
     */
    fun fromUserText(text: String, today: LocalDate, now: Long): List<CoachNote> =
        text.take(MAX_USER_PASTE)
            .lines()
            .map { it.trim().trimStart('-', '*', '•', '·').trim().take(MAX_USER_TEXT) }
            .filter { it.isNotEmpty() }
            .distinctBy { line -> line.lowercase().filter { it.isLetterOrDigit() } }
            .mapIndexed { i, line ->
                CoachNote(id = newId(now, i), text = line, kind = NoteKind.LASTING, added = today, byUser = true)
            }

    /** One line per note for the AI, with its id so it can say which ones are out of date. */
    fun promptLines(notes: List<CoachNote>, today: LocalDate): String {
        val dayFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
        return active(notes, today).joinToString("\n") { n ->
            val kind = when (n.kind) {
                NoteKind.LASTING -> ""
                NoteKind.SHORT -> " (for now)"
                NoteKind.PLAN -> " (their plan)"
            }
            "- [${n.id}] ${n.text}$kind, noted ${n.added.format(dayFormat)}"
        }
    }

    val EXTRACT_INSTRUCTION = """
        You keep the notebook of a personal fitness and nutrition coach. After each chat message you decide whether the user said anything worth remembering for future coaching.

        Worth remembering:
        - health conditions, injuries, pain, allergies, medicines, pregnancy
        - how they eat: vegetarian days, fasting, foods they dislike or can't eat, their cooking situation
        - routine and schedule: night shifts, gym days, travel, busy weeks
        - events and goals with dates: a wedding, a race, a holiday
        - what a doctor, nutritionist or trainer told them
        - plans they commit to ("I'll add eggs to breakfast", "I'll walk after dinner")

        Not worth remembering: questions, small talk, how they feel today, numbers the app already logs (weight, meals, steps), and anything the coach said rather than the user.

        Each note is a short phrase in plain words, at most 20 words, about the user, without "the user" (for example "Vegetarian on Tuesdays", "Left knee hurts after running").
        kind is "lasting" for things that stay true, "short" for things true for about a week (a cold, travelling this week), "plan" for something they said they'll do.
        For a plan, look_for lists 1 to 3 short words from the user's own message that would appear in their meal or workout log when they do it (for example ["egg"] or ["walk"]), and log is "meal" or "workout".
        If the message shows an existing note is no longer true, put its id in outdated.
        Most messages need nothing. Never repeat a note that already exists. At most 2 new notes, or up to 6 when the user shares a lot about themselves in one message (for example a pasted list of their health conditions and routine).

        Reply with JSON only: {"remember": [{"text": "...", "kind": "lasting|short|plan", "look_for": [], "log": "meal|workout"}], "outdated": ["id"]}
    """.trimIndent()

    fun extractPrompt(notes: List<CoachNote>, today: LocalDate, userMessage: String, coachReply: String): String = buildString {
        appendLine("Existing notes:")
        appendLine(promptLines(notes, today).ifBlank { "- none" })
        appendLine()
        appendLine("User said: ${userMessage.take(1500)}")
        appendLine("Coach replied: ${coachReply.take(800)}")
    }

    fun parseUpdate(response: String, notes: List<CoachNote>, today: LocalDate, now: Long): NoteUpdate {
        val cleaned = response.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val json = runCatching { JSONObject(cleaned) }.getOrNull() ?: return NoteUpdate(emptyList(), emptyList())
        val existingIds = notes.map { it.id }.toSet()
        val add = json.optJSONArray("remember")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val text = o.optString("text", "").trim().trimEnd('.').take(MAX_TEXT)
                if (text.isBlank()) return@mapNotNull null
                val kind = when (o.optString("kind").lowercase()) {
                    "short" -> NoteKind.SHORT
                    "plan" -> NoteKind.PLAN
                    else -> NoteKind.LASTING
                }
                val lookFor = if (kind == NoteKind.PLAN) {
                    o.optJSONArray("look_for")?.let { w -> (0 until w.length()).map { w.optString(it).trim().lowercase() } }
                        .orEmpty().filter { it.length >= 3 }.take(3)
                } else {
                    emptyList()
                }
                val log = when (o.optString("log").lowercase()) {
                    "meal" -> PlanLog.MEAL
                    "workout" -> PlanLog.WORKOUT
                    else -> null
                }
                CoachNote(
                    id = newId(now, i),
                    text = text.replaceFirstChar { it.uppercase() },
                    kind = kind,
                    added = today,
                    lookFor = lookFor,
                    planLog = if (kind == NoteKind.PLAN && lookFor.isNotEmpty()) log else null
                )
            }
        }.orEmpty().filter { new -> notes.none { same(it.text, new.text) } }.take(MAX_NEW_LONG)
        val outdated = json.optJSONArray("outdated")?.let { a -> (0 until a.length()).map { a.optString(it) } }
            .orEmpty().filter { it in existingIds }
        return NoteUpdate(add, outdated)
    }

    /**
     * Counts, from the day after the plan was made, the days with a log of the plan's kind and
     * the days the plan's words show up in it. Days without logs are unknown and don't count.
     */
    fun checkPlan(
        note: CoachNote,
        nutrition: List<NutritionEntryEntity>,
        workouts: List<WorkoutEntryEntity>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): PlanCheck? {
        val planLog = note.planLog ?: return null
        if (note.kind != NoteKind.PLAN || note.lookFor.isEmpty()) return null
        fun dateOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val logs: List<Pair<LocalDate, String>> = when (planLog) {
            PlanLog.MEAL -> nutrition.map { e ->
                val names = MealItems.decode(e.items)?.items?.joinToString(" ") { it.name }.orEmpty()
                dateOf(e.timestamp) to "${e.foodText} $names".lowercase()
            }
            PlanLog.WORKOUT -> workouts.map { dateOf(it.timestamp) to it.description.lowercase() }
        }
        val byDay = logs.filter { it.first > note.added && it.first <= today }.groupBy({ it.first }, { it.second })
        val done = byDay.count { (_, texts) -> texts.any { t -> note.lookFor.any { t.contains(it) } } }
        return PlanCheck(daysLogged = byDay.size, daysDone = done)
    }

    /**
     * Daily card findings for the user's plans: a win once the logs show they did it, a gentle
     * reminder after two logged days without it.
     */
    fun planFindings(
        notes: List<CoachNote>,
        nutrition: List<NutritionEntryEntity>,
        workouts: List<WorkoutEntryEntity>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<CoachFinding> {
        val dayFormat = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)
        return active(notes, today).mapNotNull { note ->
            val check = checkPlan(note, nutrition, workouts, today, zone) ?: return@mapNotNull null
            val days = ChronoUnit.DAYS.between(note.added, today)
            val whenSaid = if (days <= 1) "Yesterday" else "On ${note.added.format(dayFormat)}"
            val plan = note.text.replaceFirstChar { it.lowercase() }
            when {
                check.daysDone > 0 -> CoachFinding(
                    id = "plan_${note.id}",
                    area = "plan",
                    kind = FindingKind.WIN,
                    priority = 70,
                    fact = "$whenSaid you told me your plan: $plan. Your logs show it on ${check.daysDone} of the ${check.daysLogged} " +
                        "${if (check.daysLogged == 1) "day" else "days"} you've logged since.",
                    meaning = "You said it and you did it. Small plans that stick turn into habits.",
                    suggestion = "Keep it going today."
                )
                check.daysLogged >= 2 -> CoachFinding(
                    id = "plan_${note.id}",
                    area = "plan",
                    kind = FindingKind.CONCERN,
                    priority = 45,
                    fact = "$whenSaid you told me your plan: $plan. I haven't seen it yet in the ${check.daysLogged} days you've logged since.",
                    meaning = "Plans are easier to keep when they're tied to something you already do every day.",
                    suggestion = "Try it today: $plan."
                )
                else -> null
            }
        }
    }
}
