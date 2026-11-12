package com.superfit.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superfit.app.data.*
import com.superfit.app.domain.BirthMonth
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
                    birthMonth = existing.birthYearMonth.takeIf { it > 0 }?.let(BirthMonth::month),
                    birthYear = existing.birthYearMonth.takeIf { it > 0 }?.let { BirthMonth.year(it).toString() } ?: "",
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

    fun onBirthMonthChanged(month: Int) {
        _uiState.value = _uiState.value.copy(birthMonth = month, error = null)
    }

    fun onBirthYearChanged(year: String) {
        _uiState.value = _uiState.value.copy(birthYear = year, error = null)
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
                // Nothing saved there yet: the boxes stay empty for the person to fill in.
                _uiState.value = _uiState.value.copy(isAutofilling = false)
            } else {
                _uiState.value = _uiState.value.copy(
                    height = height?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: _uiState.value.height,
                    weight = weight?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: _uiState.value.weight,
                    isAutofilling = false,
                    filledFromHealthConnect = true
                )
            }
        }
    }

    fun saveProfile(onSuccess: () -> Unit) {
        val state = _uiState.value
        val birthProblem = state.birthProblem()
        val heightDouble = state.height.toDoubleOrNull()
        val weightDouble = state.weight.toDoubleOrNull()

        if (birthProblem != null) {
            _uiState.value = _uiState.value.copy(error = "Go back and check when you were born.")
            return
        }
        val birthYearMonth = BirthMonth.encode(state.birthYear.toInt(), state.birthMonth!!)
        if (heightDouble == null || heightDouble <= 0) {
            _uiState.value = _uiState.value.copy(error = "Go back and enter your height.")
            return
        }
        if (weightDouble == null || weightDouble <= 0) {
            _uiState.value = _uiState.value.copy(error = "Go back and enter your weight.")
            return
        }

        viewModelScope.launch {
            try {
                // Keep a goal the user already set; a new profile starts from today's weight until
                // they set a goal weight in Insights > Journey (it used to default to 75 -> 70 kg).
                val existing = repository.getProfile()
                val profile = UserProfileEntity(
                    id = 0,
                    age = BirthMonth.ageOn(birthYearMonth, Now.today()),
                    birthYearMonth = birthYearMonth,
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
                _uiState.value = _uiState.value.copy(error = "Couldn't save your plan. Please try again.")
            }
        }
    }
}

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
    val grantedPermissions: Set<String> = emptySet(),
    val isAutofilling: Boolean = false,
    // True once Health Connect filled in height or weight, so "About you" can say where they came from.
    val filledFromHealthConnect: Boolean = false,
    val error: String? = null
) {
    /** What's wrong with the birth month and year, in plain words, or null when they're fine. */
    fun birthProblem(): String? = BirthMonth.problem(birthYear.toIntOrNull(), birthMonth, Now.today())

    /** The age from the birth month and year, or null until they're filled in properly. */
    fun ageOrNull(): Int? =
        if (birthProblem() == null) BirthMonth.ageOn(BirthMonth.encode(birthYear.toInt(), birthMonth!!), Now.today()) else null
}
