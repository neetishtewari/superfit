package com.superfit.app.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.superfit.app.domain.ActivityLevels
import com.superfit.app.domain.OnboardingTargets
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.ui.components.BirthMonthInput
import com.superfit.app.ui.components.ChoiceGrid
import com.superfit.app.ui.components.GoalPicker
import com.superfit.app.ui.components.HeightInput
import com.superfit.app.ui.components.PrimaryButton
import com.superfit.app.ui.components.ProfileFieldLabel
import com.superfit.app.ui.components.SuperfitCard
import com.superfit.app.ui.components.SuperfitField
import com.superfit.app.ui.components.SuperfitLogo
import kotlin.math.roundToInt

// Step 1 of 6: what Superfit is, in one glance.
@Composable
internal fun WelcomeStep(onNext: () -> Unit) {
    SuperfitLogo(size = 56, modifier = Modifier.padding(top = 8.dp))
    StepTitle(
        title = "The easiest way to eat well and get fit",
        subtitle = "Tell Superfit what you ate, or snap your plate. Your AI coach does the counting."
    )
    Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        FeatureRow(icon = Icons.Default.Mic, title = "Log by voice or photo", desc = "Say it in your own words, in your own language.")
        FeatureRow(icon = Icons.Default.Shield, title = "Private by design", desc = "Never sold or used for ads. Delete it any time.")
        FeatureRow(icon = Icons.Default.Star, title = "Free, no ads", desc = "Every feature. No paywall.")
    }
    PrimaryButton(text = "Get started", onClick = onNext)
}

// Step 3 of 6: sex, birth month, height and weight for the resting-calorie formula.
@Composable
internal fun AboutYouStep(
    uiState: OnboardingUiState,
    viewModel: OnboardingViewModel,
    errors: AboutYouErrors,
    onNext: () -> Unit
) {
    val colors = SuperfitTheme.colors
    StepTitle(
        title = "About you",
        subtitle = "We use these to work out how many calories your body burns at rest."
    )

    SuperfitCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileFieldLabel("Sex (used only for the calorie formula)")
                ChoiceGrid(
                    options = listOf(true to "Male", false to "Female"),
                    selected = uiState.isMale,
                    onSelect = { viewModel.onSexChanged(it) }
                )
            }

            BirthMonthInput(
                month = uiState.birthMonth,
                year = uiState.birthYear,
                onMonth = { viewModel.onBirthMonthChanged(it) },
                onYear = { viewModel.onBirthYearChanged(it) },
                error = errors.birth
            )

            HeightInput(heightCm = uiState.height, onHeightCm = { viewModel.onHeightChanged(it) }, isError = errors.height, refreshKey = uiState.filledFromHealthConnect)

            SuperfitField(
                value = uiState.weight,
                onValueChange = { viewModel.onWeightChanged(it) },
                label = { Text("Weight") },
                trailingIcon = { UnitSuffix("kg") },
                isError = errors.weight,
                supportingText = fieldError(errors.weight, "Enter your weight"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )

            if (uiState.filledFromHealthConnect) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.primaryAccent, modifier = Modifier.size(16.dp))
                    Text("Height and weight from Health Connect", style = MaterialTheme.typography.bodySmall, color = colors.primaryAccent)
                }
            }
        }
    }

    PrimaryButton(text = "Continue", onClick = onNext)
}

/** Which "About you" boxes still need fixing; [birth] is the plain line to show, or null. */
internal data class AboutYouErrors(val birth: String? = null, val height: Boolean = false, val weight: Boolean = false) {
    val any: Boolean get() = birth != null || height || weight
}

/** The line under a box that still needs a number, or nothing. */
private fun fieldError(show: Boolean, message: String): (@Composable () -> Unit)? {
    if (!show) return null
    return { Text(message) }
}

@Composable
private fun UnitSuffix(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = SuperfitTheme.colors.textSecondary, modifier = Modifier.padding(end = 12.dp))
}

// Step 4 of 6: the goal first, then how active a normal week is.
@Composable
internal fun GoalStep(
    uiState: OnboardingUiState,
    viewModel: OnboardingViewModel,
    onOpenBurnInfo: () -> Unit,
    onNext: () -> Unit
) {
    val colors = SuperfitTheme.colors
    val plan = uiState.plan()
    StepTitle(title = "Your goal", subtitle = "We'll set your daily calories and protein from this.")

    GoalPicker(
        selected = uiState.goal,
        onSelect = { goal -> viewModel.onGoalChanged(goal.key, goal.calorieOffset) },
        selectedDetail = "${"%,d".format(plan.targets.calories.roundToInt())} kcal · ${plan.targets.proteinG.roundToInt()} g protein a day"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileFieldLabel("How active is a normal week?")
        ChoiceGrid(
            options = ActivityLevels.map { it.multiplier to it.label },
            selected = ActivityLevels.closest(uiState.activityMultiplier),
            onSelect = { viewModel.onActivityMultiplierChanged(it) }
        )
        Text(
            ActivityLevels.describe(uiState.activityMultiplier),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary
        )
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpenBurnInfo)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "You burn about ${"%,d".format(plan.tdee.roundToInt())} kcal a day",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary
        )
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Default.Info, contentDescription = "What does this mean?", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
    }

    Spacer(Modifier.height(4.dp))
    PrimaryButton(text = "Continue", onClick = onNext)
}

/** The targets for what's been entered so far; sensible stand-ins while boxes are still empty. */
internal fun OnboardingUiState.plan(): OnboardingTargets = OnboardingTargets.of(
    age = ageOrNull() ?: 30,
    heightCm = height.toDoubleOrNull()?.takeIf { it > 0 } ?: 170.0,
    weightKg = weight.toDoubleOrNull()?.takeIf { it > 0 } ?: 70.0,
    isMale = isMale,
    activityMultiplier = activityMultiplier,
    goal = goal,
    calorieOffset = calorieOffset
)
