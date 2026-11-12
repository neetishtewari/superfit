package com.superfit.app.data

/**
 * Anonymous usage counts and handled-error reports, from any screen on either phone. Each app plugs
 * in its own reporter (Android: AppAnalytics; iPhone: IosUsageReports), which stays off in the dev
 * app and when the person turns usage reports off in Settings › Privacy.
 * Events carry only how something was done (voice, photo, ...) or which screen was opened: never
 * food, numbers, health data, names or email.
 */
object UsageEvents {
    var report: (event: String, params: Map<String, String>) -> Unit = { _, _ -> }

    /** Sends a handled error (never a crash) so it can be fixed. */
    var error: (Throwable) -> Unit = {}

    /** A detail attached to any later crash report, such as which speech service was used. */
    var key: (name: String, value: String) -> Unit = { _, _ -> }

    private fun log(event: String, vararg params: Pair<String, String>) = report(event, mapOf(*params))

    /** A meal item swapped for a different food, by picking a choice or typing/saying a name. */
    fun mealItemChanged(typed: Boolean) = log("meal_item_changed", "how" to if (typed) "typed" else "choice")

    /** A meal item's amount typed in, or switched between household units and g/ml ([how]: typed, switch). */
    fun mealAmountChanged(how: String) = log("meal_amount_changed", "how" to how)

    /** A suggestion under the meal box picked while typing ([kind]: meal, food). */
    fun mealSuggestionUsed(kind: String) = log("meal_suggestion_used", "kind" to kind)

    /** [method]: voice, typed, photo, barcode or label. */
    fun mealLogged(method: String) = log("meal_logged", "method" to method)
    fun mealEdited() = log("meal_edited")

    fun workoutLogged() = log("workout_logged", "method" to "voice")
    fun workoutsConnected() = log("workouts_connected")

    /**
     * [type]: day, activity, best, roast or a recap card. For workout and best cards also: whether a
     * photo was used and zoomed/moved, numbers only or with the stand-out line, where the stats sat,
     * and whether the heart-rate line was on it.
     */
    fun cardShared(
        type: String,
        photo: Boolean? = null,
        adjusted: Boolean? = null,
        chips: String? = null,
        statsPlace: String? = null,
        heartLine: Boolean? = null
    ) = log(
        "share_card_shared",
        *listOfNotNull(
            "type" to type,
            photo?.let { "photo" to yesNo(it) },
            adjusted?.let { "adjusted" to yesNo(it) },
            chips?.let { "chips" to it },
            statsPlace?.let { "stats_place" to it },
            heartLine?.let { "heart_line" to yesNo(it) }
        ).toTypedArray()
    )

    /** The monthly recap played through to its last story card. */
    fun recapWatched(cards: Int) = log("recap_watched", "cards" to "$cards")

    /** [how] for the bottom menu tabs: menu, card (the coach card on Today), back or start. */
    fun screenOpened(screen: String, how: String? = null) =
        log("screen_opened", *listOfNotNull("screen" to screen, how?.let { "how" to it }).toTypedArray())
    fun goalSet(withDate: Boolean) = log("goal_set", "target_date" to yesNo(withDate))
    fun weighIn() = log("weigh_in")
    fun profileEdited() = log("profile_edited")
    fun widgetAdded() = log("widget_added")
    fun coachMessageSent(how: String) = log("coach_message_sent", "how" to how)
    fun coachNoteSaved(byUser: Boolean) = log("coach_note_saved", "by" to if (byUser) "user" else "coach")
    fun coachNoteRemoved(how: String) = log("coach_note_removed", "how" to how)
    fun coachMealLogged() = log("coach_meal_logged")
    fun coachStyleChanged(style: String) = log("coach_style_changed", "style" to style)
    fun stepGoalChanged(direction: String, mode: String) = log("step_goal_changed", "direction" to direction, "mode" to mode)

    /** The "ready to share" card on Today: [action] shown, shared or dismissed; [type] run or walk. */
    fun shareOffer(action: String, type: String) = log("share_offer", "action" to action, "type" to type)
    fun reportShared(weeks: Int) = log("coach_report_shared", "weeks" to "$weeks")

    /** Which speech service voice logging used, attached to any later crash report. */
    fun setSpeechService(name: String) = key("speech_service", name)

    /** A handled error worth seeing in the crash reports, such as a background job failing. */
    fun recordError(e: Throwable) = error(e)

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"
}
