package com.superfit.app.ui.onboarding

import com.superfit.app.data.HealthConnectManager

/** Health Connect for the shared sign-up steps. */
class AndroidOnboardingHealth(private val manager: HealthConnectManager) : OnboardingHealth {
    override val appName = "Health Connect"

    override suspend fun connected(): Boolean = manager.hasAllPermissions()

    override suspend fun latestHeightCm(): Double? = manager.readLatestHeight()

    override suspend fun latestWeightKg(): Double? = manager.readLatestWeight()
}
