package com.superfit.app.data

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Anonymous usage counts and crash reports. Events carry only how something was done (voice,
 * photo, ...) or which screen was opened: never food, numbers, health data, names or email.
 * The dev app never reports, and users can turn both off in Settings.
 */
object AppAnalytics {
    private const val PREFS = "superfit_usage_reports"
    private const val KEY_ENABLED = "enabled"

    private var analytics: FirebaseAnalytics? = null
    private var active = false

    /** Call once from Application.onCreate. Collection stays off until this says otherwise. */
    fun init(context: Context) {
        try {
            analytics = FirebaseAnalytics.getInstance(context)
            apply(context)
        } catch (e: Exception) {
            android.util.Log.w("AppAnalytics", "Usage reports unavailable", e)
        }
    }

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        apply(context)
    }

    private fun apply(context: Context) {
        active = isEnabled(context) && !isDevApp(context)
        try {
            analytics?.setAnalyticsCollectionEnabled(active)
            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = active
        } catch (e: Exception) {
            android.util.Log.w("AppAnalytics", "Could not change usage reports", e)
        }
    }

    private fun isDevApp(context: Context) = context.packageName.endsWith(".debug")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun log(event: String, vararg params: Pair<String, String>) {
        if (!active) return
        analytics?.logEvent(event, Bundle().apply { params.forEach { (k, v) -> putString(k, v) } })
    }

    /** [method]: voice, typed, photo, barcode or label. */
    fun mealLogged(method: String) = log("meal_logged", "method" to method)
    fun mealEdited() = log("meal_edited")

    /** A meal item swapped for a different food, by picking a choice or typing/saying a name. */
    fun mealItemChanged(typed: Boolean) = log("meal_item_changed", "how" to if (typed) "typed" else "choice")

    /** A meal item's amount typed in, or switched between household units and g/ml ([how]: typed, switch). */
    fun mealAmountChanged(how: String) = log("meal_amount_changed", "how" to how)
    fun workoutLogged() = log("workout_logged", "method" to "voice")
    fun workoutsConnected() = log("workouts_connected")

    /** [type]: day, activity or best. */
    fun cardShared(type: String) = log("share_card_shared", "type" to type)
    fun screenOpened(screen: String) = log("screen_opened", "screen" to screen)
    fun goalSet(withDate: Boolean) = log("goal_set", "target_date" to if (withDate) "yes" else "no")
    fun weighIn() = log("weigh_in")
    fun widgetAdded() = log("widget_added")
    fun coachMessageSent(how: String) = log("coach_message_sent", "how" to how)
    fun coachNoteSaved(byUser: Boolean) = log("coach_note_saved", "by" to if (byUser) "user" else "coach")
    fun coachNoteRemoved(how: String) = log("coach_note_removed", "how" to how)
    fun coachMealLogged() = log("coach_meal_logged")
    fun coachStyleChanged(style: String) = log("coach_style_changed", "style" to style)
    fun reportShared(weeks: Int) = log("coach_report_shared", "weeks" to "$weeks")

    /** "Invite family and friends" sent from [from] (settings). */
    fun inviteSent(from: String) = log("invite_sent", "from" to from)

    /** This install came from someone's invite link (no code or person in the event). */
    fun inviteJoined() = log("invite_joined")

    /** Which speech service voice logging used, attached to any later crash report. */
    fun setSpeechService(name: String) {
        if (!active) return
        try {
            FirebaseCrashlytics.getInstance().setCustomKey("speech_service", name)
        } catch (_: Exception) {
        }
    }

    /** A handled error worth seeing in Crashlytics, such as a background job failing. */
    fun recordError(e: Throwable) {
        if (!active) return
        try {
            FirebaseCrashlytics.getInstance().recordException(e)
        } catch (_: Exception) {
        }
    }
}
