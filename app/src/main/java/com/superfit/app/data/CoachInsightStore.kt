package com.superfit.app.data

import android.content.SharedPreferences
import com.superfit.app.domain.CoachInsight
import com.superfit.app.domain.CoachMemory
import com.superfit.app.domain.CoachMemoryEntry
import com.superfit.app.domain.FindingKind
import java.time.LocalDate

/** Keeps today's coach message for the home screen, plus a short memory of past messages. */
object CoachInsightStore {
    private const val KEY_DATE = "coaching_insight_date"
    private const val KEY_TEXT = "coaching_insight_text"
    private const val KEY_HEADLINE = "coaching_insight_headline"
    private const val KEY_ACTION = "coaching_insight_action"
    private const val KEY_WHY = "coaching_insight_why"
    private const val KEY_FOCUS = "coaching_insight_focus"
    private const val KEY_KIND = "coaching_insight_kind"
    private const val KEY_NOTE = "coaching_insight_note"
    private const val KEY_MEMORY = "coach_memory"

    fun loadForDay(prefs: SharedPreferences, date: LocalDate): CoachInsight? {
        if (prefs.getString(KEY_DATE, "") != date.toString()) return null
        val headline = prefs.getString(KEY_HEADLINE, null)
        if (headline.isNullOrBlank()) {
            // Saved by an older version of the app: plain text only.
            val text = prefs.getString(KEY_TEXT, null)
            if (text.isNullOrBlank()) return null
            return CoachInsight(text, "", emptyList(), "legacy", FindingKind.LOGGING)
        }
        return CoachInsight(
            headline = headline,
            action = prefs.getString(KEY_ACTION, "").orEmpty(),
            why = prefs.getString(KEY_WHY, "").orEmpty().lines().filter { it.isNotBlank() },
            focusId = prefs.getString(KEY_FOCUS, "").orEmpty(),
            focusKind = runCatching { FindingKind.valueOf(prefs.getString(KEY_KIND, "").orEmpty()) }
                .getOrDefault(FindingKind.LOGGING),
            note = prefs.getString(KEY_NOTE, null)
        )
    }

    fun memory(prefs: SharedPreferences): List<CoachMemoryEntry> =
        CoachMemory.decode(prefs.getString(KEY_MEMORY, null))

    fun save(prefs: SharedPreferences, date: LocalDate, insight: CoachInsight) {
        val memory = CoachMemory.remember(
            memory(prefs),
            CoachMemoryEntry(date, insight.focusId, insight.focusKind, insight.headline)
        )
        prefs.edit()
            .putString(KEY_DATE, date.toString())
            .putString(KEY_TEXT, insight.asText())
            .putString(KEY_HEADLINE, insight.headline)
            .putString(KEY_ACTION, insight.action)
            .putString(KEY_WHY, insight.why.joinToString("\n"))
            .putString(KEY_FOCUS, insight.focusId)
            .putString(KEY_KIND, insight.focusKind.name)
            .putString(KEY_NOTE, insight.note)
            .putString(KEY_MEMORY, CoachMemory.encode(memory))
            .apply()
    }
}
