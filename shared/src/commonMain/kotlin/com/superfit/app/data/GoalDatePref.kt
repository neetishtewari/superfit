package com.superfit.app.data

import kotlinx.datetime.LocalDate

/**
 * The optional date the user wants to reach their weight goal by, and whether they have set a
 * goal weight at all (older profiles carry a made-up 75 -> 70 kg default). Kept with the other settings.
 */
object GoalDatePref {
    private const val KEY = "goal_target_date"
    private const val KEY_SET = "goal_weight_set"

    fun isGoalSet(prefs: Prefs): Boolean = prefs.getBoolean(KEY_SET, false)

    fun markGoalSet(prefs: Prefs) {
        prefs.edit { putBoolean(KEY_SET, true) }
    }

    fun get(prefs: Prefs): LocalDate? =
        prefs.getLong(KEY, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let { LocalDate.fromEpochDays(it) }

    fun set(prefs: Prefs, date: LocalDate?) {
        prefs.edit { if (date == null) remove(KEY) else putLong(KEY, date.toEpochDays()) }
    }

    /** A real goal: set by the person (or changed from the old default) and at least half a kilo away. */
    fun hasGoal(prefs: Prefs, profile: UserProfileEntity): Boolean {
        val oldDefault = profile.startingWeightKg == 75.0 && profile.targetWeightKg == 70.0
        return (isGoalSet(prefs) || !oldDefault) && kotlin.math.abs(profile.startingWeightKg - profile.targetWeightKg) >= 0.5
    }
}
