package com.superfit.app.ui.onboarding

/** Reads each sign-up step aloud when the person turns it on (Android's text to speech). */
interface OnboardingVoiceGuide {
    fun speak(text: String)

    fun stop()
}
