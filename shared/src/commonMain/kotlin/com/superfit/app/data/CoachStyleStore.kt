package com.superfit.app.data

import com.superfit.app.domain.CoachStyle

/** The AI coach style the user picked in Settings, kept in their settings on the phone. */
object CoachStyleStore {

    private const val KEY_STYLE = "coach_style"

    /** Loads the choice into [CoachStyle.current]. Call when the signed-in user may have changed. */
    fun load(prefs: Prefs) {
        CoachStyle.current = CoachStyle.fromName(prefs.getString(KEY_STYLE, null))
    }

    fun save(prefs: Prefs, style: CoachStyle) {
        CoachStyle.current = style
        prefs.edit { putString(KEY_STYLE, style.name) }
    }

    /** True once the user has picked a style, at sign-up, in Settings or in the one-time chooser. */
    fun hasChosen(prefs: Prefs): Boolean = prefs.contains(KEY_STYLE)
}
