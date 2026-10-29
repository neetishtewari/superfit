package com.superfit.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superfit.app.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val repository: DataRepository,
    context: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        checkPermissions()
        loadExistingProfile()
    }

    private fun loadExistingProfile() {
        viewModelScope.launch {
            val existing = repository.getProfile()
            if (existing != null) {
                _uiState.value = _uiState.value.copy(
                    age = existing.age.toString(),
                    height = com.superfit.app.domain.BodyReadings.fmt(existing.heightCm),
                    weight = com.superfit.app.domain.BodyReadings.fmt(existing.weightKg),
                    isMale = existing.isMale,
                    activityMultiplier = existing.activityMultiplier,
                    goal = existing.goal,
                    calorieOffset = existing.calorieOffset
                )
            }
        }
    }

    fun checkPermissions() {
        viewModelScope.launch {
            val client = repository.healthConnectManager.healthConnectClient
            val granted = client?.permissionController?.getGrantedPermissions() ?: emptySet()
            val hasAll = repository.healthConnectManager.hasAllPermissions()
            _uiState.value = _uiState.value.copy(
                hasHealthConnectPermissions = hasAll,
                grantedPermissions = granted
            )
        }
    }

    fun onAgeChanged(age: String) {
        _uiState.value = _uiState.value.copy(age = age, error = null)
    }

    fun onHeightChanged(height: String) {
        _uiState.value = _uiState.value.copy(height = height, error = null)
    }

    fun onWeightChanged(weight: String) {
        _uiState.value = _uiState.value.copy(weight = weight, error = null)
    }

    fun onSexChanged(isMale: Boolean) {
        _uiState.value = _uiState.value.copy(isMale = isMale)
    }

    fun onActivityMultiplierChanged(multiplier: Double) {
        _uiState.value = _uiState.value.copy(activityMultiplier = multiplier)
    }

    fun onGoalChanged(goal: String, offset: Int) {
        _uiState.value = _uiState.value.copy(goal = goal, calorieOffset = offset)
    }

    fun autofillFromHealthConnect() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAutofilling = true, error = null)
            val height = repository.healthConnectManager.readLatestHeight()
            val weight = repository.healthConnectManager.readLatestWeight()

            if (height == null && weight == null) {
                _uiState.value = _uiState.value.copy(
                    isAutofilling = false,
                    error = "Could not find height or weight records in Health Connect."
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    height = height?.let { "%.1f".format(it) } ?: _uiState.value.height,
                    weight = weight?.let { "%.1f".format(it) } ?: _uiState.value.weight,
                    isAutofilling = false
                )
            }
        }
    }

    fun saveProfile(onSuccess: () -> Unit) {
        val state = _uiState.value
        val ageInt = state.age.toIntOrNull()
        val heightDouble = state.height.toDoubleOrNull()
        val weightDouble = state.weight.toDoubleOrNull()

        if (ageInt == null || ageInt <= 0) {
            _uiState.value = _uiState.value.copy(error = "Please enter a valid age.")
            return
        }
        if (heightDouble == null || heightDouble <= 0) {
            _uiState.value = _uiState.value.copy(error = "Please enter a valid height in cm.")
            return
        }
        if (weightDouble == null || weightDouble <= 0) {
            _uiState.value = _uiState.value.copy(error = "Please enter a valid weight in kg.")
            return
        }

        viewModelScope.launch {
            try {
                // Keep a goal the user already set; a new profile starts from today's weight until
                // they set a goal weight in Insights > Journey (it used to default to 75 -> 70 kg).
                val existing = repository.getProfile()
                val profile = UserProfileEntity(
                    id = 0,
                    age = ageInt,
                    heightCm = heightDouble,
                    weightKg = weightDouble,
                    isMale = state.isMale,
                    activityMultiplier = state.activityMultiplier,
                    goal = state.goal,
                    calorieOffset = state.calorieOffset,
                    startingWeightKg = existing?.startingWeightKg ?: weightDouble,
                    targetWeightKg = existing?.targetWeightKg ?: weightDouble,
                    startDateTimestamp = existing?.startDateTimestamp ?: System.currentTimeMillis()
                )
                repository.saveProfile(profile)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to save profile: ${e.localizedMessage}")
            }
        }
    }
}

data class OnboardingUiState(
    val age: String = "",
    val height: String = "",
    val weight: String = "",
    val isMale: Boolean = true,
    val activityMultiplier: Double = 1.2,
    val goal: String = "LOSE_WEIGHT",
    val calorieOffset: Int = -500,
    val hasHealthConnectPermissions: Boolean = false,
    val grantedPermissions: Set<String> = emptySet(),
    val isAutofilling: Boolean = false,
    val error: String? = null
)
