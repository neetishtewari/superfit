package com.superfit.app.ui.onboarding

/**
 * The phone's health app during sign-up: Health Connect on Android, Apple Health on iPhone. Asking
 * for access is done by the screen ([OnboardingScreen]'s onConnectHealth), as each phone shows its
 * own sheet; this reads what sign-up needs.
 */
interface OnboardingHealth {
    /** "Health Connect" or "Apple Health", as the steps name it. */
    val appName: String

    /** True once access was given (on iPhone: once it was asked, as Apple doesn't say). */
    suspend fun connected(): Boolean

    /** The newest height in cm saved there, or null. */
    suspend fun latestHeightCm(): Double?

    /** The newest weight in kg saved there, or null. */
    suspend fun latestWeightKg(): Double?
}
