package com.superfit.app.data

import android.content.SharedPreferences
import java.time.LocalDate

/**
 * The optional date the user wants to reach their weight goal by, and whether they have set a
 * goal weight at all (older profiles carry a made-up 75 -> 70 kg default). Kept with the other settings.
 */
object GoalDatePref {
    private const val KEY = "goal_target_date"
    private const val KEY_SET = "goal_weight_set"

    fun isGoalSet(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY_SET, false)

    fun markGoalSet(prefs: SharedPreferences) {
        prefs.edit().putBoolean(KEY_SET, true).apply()
    }

    fun get(prefs: SharedPreferences): LocalDate? =
        prefs.getLong(KEY, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let { LocalDate.ofEpochDay(it) }

    fun set(prefs: SharedPreferences, date: LocalDate?) {
        prefs.edit().apply { if (date == null) remove(KEY) else putLong(KEY, date.toEpochDay()) }.apply()
    }
}
