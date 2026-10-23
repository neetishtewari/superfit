package com.superfit.app.data

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewManagerFactory
import java.time.Instant
import java.time.ZoneId

/**
 * Asks for a Play Store rating once, at a happy moment: after meals are logged on [MEAL_DAYS]
 * different days (any days, not a streak). Ratings given this way come from real users who
 * have built the habit, so Play is far less likely to filter them than reviews from friends
 * asked directly. Play decides whether the sheet actually appears and never says, so the ask
 * is spent either way.
 */
object ReviewPrompt {
    const val MEAL_DAYS = 5

    // Not per-user: a rating belongs to the phone's Play account, not to the signed-in account.
    private const val PREFS = "superfit_device_prefs"
    private const val KEY_ASKED = "review_prompt_asked"

    fun daysWithMeals(mealTimestamps: List<Long>, zoneId: ZoneId = ZoneId.systemDefault()): Int =
        mealTimestamps.map { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }.distinct().size

    fun shouldAsk(mealDays: Int, alreadyAsked: Boolean, otherPromptShowing: Boolean): Boolean =
        mealDays >= MEAL_DAYS && !alreadyAsked && !otherPromptShowing

    fun wasAsked(context: Context): Boolean = prefs(context).getBoolean(KEY_ASKED, false)

    fun ask(activity: Activity) {
        prefs(activity).edit().putBoolean(KEY_ASKED, true).apply()
        // The dev app isn't installed from Play, so there's nothing to rate.
        if (activity.packageName.endsWith(".debug")) return
        try {
            val manager = ReviewManagerFactory.create(activity)
            manager.requestReviewFlow().addOnCompleteListener { request ->
                if (request.isSuccessful && !activity.isFinishing) {
                    manager.launchReviewFlow(activity, request.result)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ReviewPrompt", "Review sheet unavailable", e)
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
