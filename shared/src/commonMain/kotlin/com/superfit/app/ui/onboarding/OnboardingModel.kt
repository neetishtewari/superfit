package com.superfit.app.ui.onboarding

import com.superfit.app.data.LogRepository
import com.superfit.app.data.Now
import com.superfit.app.data.UserProfileEntity
import com.superfit.app.domain.AppLog
import com.superfit.app.domain.BirthMonth
import com.superfit.app.domain.BodyReadings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Sign-up, the same on Android and iPhone: what's been entered, filling height and weight from the
 * phone's health app, and saving the profile (to the phone and the person's own backup).
 */
class OnboardingModel(
    private val log: LogRepository,
    /** The phone's health app; null where there is none. */
    val health: OnboardingHealth?,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        checkPermissions()
        loadExistingProfile()
    }

    private fun loadExistingProfile() {
        scope.launch {
            val existing = log.getProfile() ?: return@launch
            _uiState.value = _uiState.value.copy(
                birthMonth = existing.birthYearMonth.takeIf { it > 0 }?.let(BirthMonth::month),
                birthYear = existing.birthYearMonth.takeIf { it > 0 }?.let { BirthMonth.year(it).toString() } ?: "",
                height = BodyReadings.fmt(existing.heightCm),
                weight = BodyReadings.fmt(existing.weightKg),
                isMale = existing.isMale,
                activityMultiplier = existing.activityMultiplier,
                goal = existing.goal,
                calorieOffset = existing.calorieOffset
            )
        }
    }

    fun checkPermissions() {
        val source = health ?: return
        scope.launch {
            val connected = safe { source.connected() } ?: false
            _uiState.value = _uiState.value.copy(hasHealthConnectPermissions = connected)
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

    /** After access was given: height and weight from the health app, when it has them. */
    fun autofillFromHealth() {
        val source = health ?: return
        scope.launch {
            _uiState.value = _uiState.value.copy(isAutofilling = true, error = null)
            val height = safe { source.latestHeightCm() }
            val weight = safe { source.latestWeightKg() }
            _uiState.value = if (height == null && weight == null) {
                // Nothing saved there yet: the boxes stay empty for the person to fill in.
                _uiState.value.copy(isAutofilling = false)
            } else {
                _uiState.value.copy(
                    height = height?.let(BodyReadings::fmt) ?: _uiState.value.height,
                    weight = weight?.let(BodyReadings::fmt) ?: _uiState.value.weight,
                    isAutofilling = false,
                    filledFromHealthConnect = true
                )
            }
        }
    }

    fun saveProfile(onSuccess: () -> Unit) {
        val state = _uiState.value
        val heightCm = state.height.toDoubleOrNull()
        val weightKg = state.weight.toDoubleOrNull()
        val problem = when {
            state.birthProblem() != null -> "Go back and check when you were born."
            heightCm == null || heightCm <= 0 -> "Go back and enter your height."
            weightKg == null || weightKg <= 0 -> "Go back and enter your weight."
            else -> null
        }
        if (problem != null || heightCm == null || weightKg == null) {
            _uiState.value = state.copy(error = problem)
            return
        }
        val birthYearMonth = BirthMonth.encode(state.birthYear.toInt(), state.birthMonth!!)
        scope.launch {
            try {
                // Keep a goal the user already set; a new profile starts from today's weight until
                // they set a goal weight in Progress > Journey.
                val existing = log.getProfile()
                log.saveProfile(
                    UserProfileEntity(
                        id = 0,
                        age = BirthMonth.ageOn(birthYearMonth, Now.today()),
                        birthYearMonth = birthYearMonth,
                        heightCm = heightCm,
                        weightKg = weightKg,
                        isMale = state.isMale,
                        activityMultiplier = state.activityMultiplier,
                        goal = state.goal,
                        calorieOffset = state.calorieOffset,
                        startingWeightKg = existing?.startingWeightKg ?: weightKg,
                        targetWeightKg = existing?.targetWeightKg ?: weightKg,
                        startDateTimestamp = existing?.startDateTimestamp ?: Now.millis()
                    )
                )
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Onboarding", "Saving the profile failed", e)
                _uiState.value = _uiState.value.copy(error = "Couldn't save your plan. Please try again.")
            }
        }
    }

    private suspend fun <T> safe(block: suspend () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLog.w("Onboarding", "Reading the health app failed", e)
        null
    }
}
