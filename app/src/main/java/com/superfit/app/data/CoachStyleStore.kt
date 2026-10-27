package com.superfit.app.data

import android.content.Context
import com.superfit.app.domain.CoachStyle

/** The AI coach style the user picked in Settings, kept in their preferences on the phone. */
object CoachStyleStore {

    private const val KEY_STYLE = "coach_style"

    /** Loads the choice into [CoachStyle.current]. Call when the signed-in user may have changed. */
    fun load(context: Context) {
        CoachStyle.current = CoachStyle.fromName(getUserSharedPrefs(context).getString(KEY_STYLE, null))
    }

    fun save(context: Context, style: CoachStyle) {
        CoachStyle.current = style
        getUserSharedPrefs(context).edit().putString(KEY_STYLE, style.name).apply()
    }

    /** True once the user has picked a style, at sign-up, in Settings or in the one-time chooser. */
    fun hasChosen(context: Context): Boolean = getUserSharedPrefs(context).contains(KEY_STYLE)
}
