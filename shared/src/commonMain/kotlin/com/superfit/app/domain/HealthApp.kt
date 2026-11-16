package com.superfit.app.domain

/**
 * The phone's own health app in on-screen text: Health Connect on Android, Apple Health on iPhone.
 * The iPhone app sets it at start-up, before any screen shows.
 */
object HealthApp {
    var name: String = "Health Connect"

    /** Apps people usually record workouts with on this phone, for "they appear here once…" lines. */
    var workoutApps: String = "Samsung Health, Strava or your watch"

    /**
     * Where to turn workouts on when the health app shares steps but no workouts. Apple Health never
     * tells an app that workouts were refused, and can't show its access sheet twice, so the iPhone
     * points to the phone's Settings; null on Android.
     */
    var workoutAccessHelp: String? = null

    fun useAppleHealth() {
        name = "Apple Health"
        workoutApps = "your Apple Watch, Strava or another fitness app"
        workoutAccessHelp = "Workouts on your watch but none here? Open the Health app, tap your picture at the top right, " +
            "then Apps › Superfit, and turn on Workouts. They come in next time you open Superfit."
    }
}
