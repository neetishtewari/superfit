package com.superfit.app.data

import com.superfit.app.ui.onboarding.OnboardingHealth

/**
 * Apple Health for the shared sign-up steps. Apple never says whether reading was allowed, so
 * "connected" means the person answered Apple's access sheet, as Today treats it.
 */
class IosOnboardingHealth(private val health: IosHealthSource, private val prefs: Prefs) : OnboardingHealth {
    override val appName = "Apple Health"

    override suspend fun connected(): Boolean = prefs.getBoolean(HealthSync.KEY_ASKED, false)

    override suspend fun latestHeightCm(): Double? = health.latest("heightCm")

    override suspend fun latestWeightKg(): Double? = health.latest("weightKg")
}
