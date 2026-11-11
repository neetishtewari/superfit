package com.superfit.app.domain

/**
 * The phone's own health app in on-screen text: Health Connect on Android, Apple Health on iPhone.
 * The iPhone app sets it at start-up, before any screen shows.
 */
object HealthApp {
    var name: String = "Health Connect"

    /** Apps people usually record workouts with on this phone, for "they appear here once…" lines. */
    var workoutApps: String = "Samsung Health, Strava or your watch"

    fun useAppleHealth() {
        name = "Apple Health"
        workoutApps = "your Apple Watch, Strava or another fitness app"
    }
}
