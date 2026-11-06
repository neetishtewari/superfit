package com.superfit.app.data

import android.content.Context
import com.superfit.app.domain.SavedStepGoal
import com.superfit.app.domain.StepGoalReason
import kotlinx.datetime.LocalDate

/**
 * The daily step goal kept in the user's preferences on the phone (see StepGoal): the worked-out
 * goal and the day it was set, and an own number if the user chose one in Settings. Preferences
 * are in Android's own backup, and the goal is worked out again from step history anyway.
 */
object StepGoalStore {
    private const val KEY_GOAL = "step_goal"
    private const val KEY_REASON = "step_goal_reason"
    private const val KEY_SET_ON = "step_goal_set_on"
    private const val KEY_OWN = "step_goal_own"
    private const val KEY_NOTE_DISMISSED = "step_goal_note_dismissed_on"

    fun load(context: Context): SavedStepGoal? {
        val prefs = getUserSharedPrefs(context)
        val goal = prefs.getInt(KEY_GOAL, 0).takeIf { it > 0 } ?: return null
        val reason = StepGoalReason.fromName(prefs.getString(KEY_REASON, null)) ?: return null
        val setOn = prefs.getString(KEY_SET_ON, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        return SavedStepGoal(goal, reason, setOn)
    }

    fun save(context: Context, saved: SavedStepGoal) {
        getUserSharedPrefs(context).edit()
            .putInt(KEY_GOAL, saved.goal)
            .putString(KEY_REASON, saved.reason.name)
            .putString(KEY_SET_ON, saved.setOn.toString())
            .apply()
    }

    /** The user's own number, or null for the automatic goal. */
    fun ownGoal(context: Context): Int? = getUserSharedPrefs(context).getInt(KEY_OWN, 0).takeIf { it > 0 }

    fun setOwnGoal(context: Context, steps: Int?) {
        val edit = getUserSharedPrefs(context).edit()
        if (steps == null) edit.remove(KEY_OWN) else edit.putInt(KEY_OWN, steps)
        edit.apply()
    }

    fun noteDismissedOn(context: Context): String? = getUserSharedPrefs(context).getString(KEY_NOTE_DISMISSED, null)

    fun dismissNote(context: Context, day: LocalDate) {
        getUserSharedPrefs(context).edit().putString(KEY_NOTE_DISMISSED, day.toString()).apply()
    }
}
