package com.superfit.app.domain

/**
 * The phone's health app in on-screen text: Health Connect on Android, Apple Health on iPhone.
 * The iPhone app sets it at start-up, before any screen shows.
 */
object HealthApp {
    var name: String = "Health Connect"

    /** Apps people usually record workouts with on this phone, for "they appear here once…" lines. */
    var workoutApps: String = "Samsung Health, Strava or your watch"

    /**
     * What to tap on the phone's own access sheet, said just before it opens. Apple's sheet keeps
     * Allow greyed out until a history choice is tapped, and nothing there says so; null on Android.
     */
    var accessSheetTip: String? = null

    /**
     * How to turn on what Superfit can't read. Apple Health never tells an app that something was
     * refused (it just returns nothing) and never shows its access sheet twice for the same item, so
     * the iPhone points to the Health app; null on Android.
     */
    var accessFixSteps: String? = null

    /** Opens the phone's health app, or null when it can't be opened from here. */
    var openHealthApp: (() -> Unit)? = null

    fun useAppleHealth(openHealth: (() -> Unit)? = null) {
        name = "Apple Health"
        workoutApps = "your Apple Watch, Strava or another fitness app"
        accessSheetTip = "On Apple's screen, tap Turn On All, then Allow. If it asks how much history to share, " +
            "tap Past 30 Days and Future Data, then Allow."
        accessFixSteps = "Open the Health app, tap your picture at the top right, then Apps › Superfit, " +
            "and turn on everything. It comes in next time you open Superfit."
        openHealthApp = openHealth
    }
}

/**
 * What the health app seems to be holding back. Apple Health answers a refused item with nothing at
 * all, so a gap only shows when other health data does arrive: exercise minutes or workouts with no
 * steps, or steps with no workouts ever.
 */
object HealthGaps {
    const val STEPS = "steps and distance"
    const val WORKOUTS = "workouts"

    fun missing(
        steps: Int,
        exerciseMinutes: Double?,
        importedToday: Int,
        importedEver: Int
    ): List<String> {
        val exercise = (exerciseMinutes ?: 0.0) >= 1.0
        return buildList {
            if (steps == 0 && (exercise || importedToday > 0)) add(STEPS)
            if (importedEver == 0 && (steps > 0 || exercise)) add(WORKOUTS)
        }
    }

    /** "Superfit isn't getting your steps and distance or workouts from Apple Health." */
    fun line(missing: List<String>, healthName: String): String =
        "Superfit isn't getting your ${missing.joinToString(" or ")} from $healthName. " +
            "They're most likely turned off for Superfit."
}
