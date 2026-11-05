package com.superfit.app.domain

import com.superfit.app.json.JSONObject
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** Which part of the body a strength session worked. */
enum class BodyArea(val key: String, val label: String) {
    UPPER("upper", "upper body"),
    LOWER("lower", "lower body"),
    CORE("core", "core"),
    FULL("full", "full body");

    companion object {
        fun of(key: String): BodyArea? = entries.firstOrNull { it.key == key.trim().lowercase(Locale.US) }
    }
}

/** One strength session: when, how long if known, and what it worked if the user said. */
data class StrengthSession(val date: LocalDate, val minutes: Int? = null, val areas: Set<BodyArea> = emptySet())

/** What the month's strength sessions add up to, from whatever the user said when logging them. */
data class StrengthDetail(
    val perWeek: Double,
    /** Minutes over the sessions whose length is known. */
    val minutes: Int,
    val timedSessions: Int,
    val areas: Map<BodyArea, Int>,
    val taggedSessions: Int,
    /** Upper or lower body when every described session left it out (and none was full body). */
    val missing: BodyArea?
) {
    /** "about 2 a week", "about 1.5 a week", "once a fortnight or so". */
    fun perWeekText(): String {
        val half = (perWeek * 2).roundToInt() / 2.0
        return when {
            half < 0.5 -> "now and then"
            half == 1.0 -> "about once a week"
            half % 1.0 == 0.0 -> "about ${half.toInt()} a week"
            else -> "about $half a week"
        }
    }
}

object StrengthSessions {

    fun detail(sessions: List<StrengthSession>, daysInMonth: Int): StrengthDetail? {
        if (sessions.isEmpty()) return null
        val timed = sessions.filter { (it.minutes ?: 0) > 0 }
        val tagged = sessions.filter { it.areas.isNotEmpty() }
        val areas = BodyArea.entries.associateWith { a -> tagged.count { a in it.areas } }.filterValues { it > 0 }
        val missing = when {
            tagged.size < 2 || (areas[BodyArea.FULL] ?: 0) > 0 -> null
            (areas[BodyArea.UPPER] ?: 0) > 0 && (areas[BodyArea.LOWER] ?: 0) == 0 -> BodyArea.LOWER
            (areas[BodyArea.LOWER] ?: 0) > 0 && (areas[BodyArea.UPPER] ?: 0) == 0 -> BodyArea.UPPER
            else -> null
        }
        return StrengthDetail(
            perWeek = sessions.size / (daysInMonth / 7.0),
            minutes = timed.sumOf { it.minutes!! },
            timedSessions = timed.size,
            areas = areas,
            taggedSessions = tagged.size,
            missing = missing
        )
    }

    /** The plain list of areas, e.g. "2 upper body · 2 lower body". */
    fun areasText(d: StrengthDetail): String = d.areas.entries.joinToString(" · ") { "${it.value} ${it.key.label}" }

    val TAG_INSTRUCTION = """
        You read workout logs and say what each strength session worked. For each numbered log return:
        - "n": the log's number
        - "areas": array of "upper", "lower", "core", "full". Upper body is chest, back, shoulders, arms (push-ups, bench press, rows, curls, pull-ups). Lower body is legs and glutes (squats, lunges, deadlifts, leg press, calf raises). Core is abs and lower back (planks, crunches). "full" for full-body sessions or circuits that work both halves. An empty array when the log doesn't say what was trained (e.g. "gym", "weights").
        - "minutes": Int, only when the log says how long it took; otherwise null. Never guess.
        Reply with JSON only: {"logs": [ ... ]}
    """.trimIndent()

    fun prompt(descriptions: List<String>): String = descriptions.mapIndexed { i, d -> "${i + 1}. $d" }.joinToString("\n")

    /** One (areas, minutes) per log, in order; null where the AI skipped it. */
    fun parse(json: String, count: Int): List<Pair<Set<BodyArea>, Int?>?> {
        val logs = runCatching { JSONObject(json.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()).getJSONArray("logs") }.getOrNull()
        val out = MutableList<Pair<Set<BodyArea>, Int?>?>(count) { null }
        if (logs == null) return out
        for (i in 0 until logs.length()) {
            val o = logs.optJSONObject(i) ?: continue
            val index = o.optInt("n", i + 1) - 1
            if (index !in 0 until count) continue
            val areas = o.optJSONArray("areas")?.let { arr -> (0 until arr.length()).mapNotNull { BodyArea.of(arr.optString(it, "")) }.toSet() }.orEmpty()
            val minutes = if (o.isNull("minutes")) null else o.optInt("minutes", 0).takeIf { it in 1..600 }
            out[index] = areas to minutes
        }
        return out
    }
}
