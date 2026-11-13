package com.superfit.app.ui.onboarding

import com.superfit.app.data.Now
import com.superfit.app.domain.BirthMonth

data class OnboardingUiState(
    val birthMonth: Int? = null,
    val birthYear: String = "",
    val height: String = "",
    val weight: String = "",
    val isMale: Boolean = true,
    val activityMultiplier: Double = 1.2,
    val goal: String = "LOSE_WEIGHT",
    val calorieOffset: Int = -500,
    val hasHealthConnectPermissions: Boolean = false,
    val isAutofilling: Boolean = false,
    // True once the health app filled in height or weight, so "About you" can say where they came from.
    val filledFromHealthConnect: Boolean = false,
    val error: String? = null
) {
    /** What's wrong with the birth month and year, in plain words, or null when they're fine. */
    fun birthProblem(): String? = BirthMonth.problem(birthYear.toIntOrNull(), birthMonth, Now.today())

    /** The age from the birth month and year, or null until they're filled in properly. */
    fun ageOrNull(): Int? =
        if (birthProblem() == null) BirthMonth.ageOn(BirthMonth.encode(birthYear.toInt(), birthMonth!!), Now.today()) else null
}
