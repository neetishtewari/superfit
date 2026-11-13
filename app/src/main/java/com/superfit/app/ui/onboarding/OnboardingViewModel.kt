package com.superfit.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superfit.app.data.DataRepository

/** Keeps the shared sign-up model alive across screen rotations. */
class OnboardingViewModel(repository: DataRepository) : ViewModel() {
    val model = OnboardingModel(repository.log, AndroidOnboardingHealth(repository.healthConnectManager), viewModelScope)
}
