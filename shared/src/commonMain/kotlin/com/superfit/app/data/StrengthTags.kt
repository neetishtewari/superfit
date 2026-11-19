package com.superfit.app.data

import com.superfit.app.domain.Ai
import com.superfit.app.domain.BodyArea
import com.superfit.app.domain.StrengthSessions
import com.superfit.app.json.JSONObject

/**
 * What each voice-logged strength session worked and how long it took, as the person said it.
 * Asked of the AI once per log (in one batch) and kept in the account's settings, so a month is
 * never tagged twice. Used by the monthly recap and the strength share card, on both phones.
 */
object StrengthTags {
    private const val KEY = "recap_strength_tags"
    private const val MAX_PER_CALL = 60

    fun key(w: WorkoutEntryEntity) = "${w.timestamp}_${w.description.hashCode()}"

    suspend fun load(prefs: Prefs, logs: List<WorkoutEntryEntity>): Map<String, Pair<Set<BodyArea>, Int?>> {
        val saved = runCatching { JSONObject(prefs.getString(KEY, null) ?: "{}") }.getOrElse { JSONObject() }
        fun read(key: String): Pair<Set<BodyArea>, Int?>? = saved.optJSONObject(key)?.let { o ->
            o.optString("a").split(',').mapNotNull { BodyArea.of(it) }.toSet() to o.optInt("m", 0).takeIf { it > 0 }
        }
        val todo = logs.filter { !saved.has(key(it)) }.take(MAX_PER_CALL)
        if (todo.isNotEmpty()) {
            val json = runCatching {
                Ai.generate(prompt = StrengthSessions.prompt(todo.map { it.description }), systemInstructionText = StrengthSessions.TAG_INSTRUCTION, isJson = true)
            }.getOrNull()
            if (json != null) {
                StrengthSessions.parse(json, todo.size).forEachIndexed { i, r ->
                    if (r != null) saved.put(key(todo[i]), JSONObject().put("a", r.first.joinToString(",") { it.key }).put("m", r.second ?: 0))
                }
                prefs.edit { putString(KEY, saved.toString()) }
            }
        }
        return logs.mapNotNull { w -> read(key(w))?.let { key(w) to it } }.toMap()
    }
}
