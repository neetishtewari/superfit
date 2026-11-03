package com.superfit.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.superfit.app.data.HealthConnectManager
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.ui.components.PrimaryButton
import com.superfit.app.ui.components.SuperfitCard
import kotlin.math.roundToInt

// Step 2 of 6: connect Health Connect, so "About you" can fill itself in.
@Composable
internal fun HealthConnectStep(
    uiState: OnboardingUiState,
    healthConnectManager: HealthConnectManager,
    requestPermissionsLauncher: androidx.activity.result.ActivityResultLauncher<Set<String>>,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val colors = SuperfitTheme.colors
    val connected = uiState.hasHealthConnectPermissions
    StepTitle(
        title = "Connect your health data",
        subtitle = "Log less by hand. Superfit reads from Health Connect on your phone:"
    )

    SuperfitCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FeatureRow(icon = Icons.AutoMirrored.Filled.DirectionsRun, title = "Steps and workouts", desc = "Adds what you burn to today's target.")
            FeatureRow(icon = Icons.Default.Scale, title = "Height, weight, body fat", desc = "Fills in your profile and keeps it up to date.")
            FeatureRow(icon = Icons.Default.Bedtime, title = "Sleep", desc = "Shown next to your days.")
        }
    }

    Text(
        "Never sold. It stays on your phone and your private backup. Your AI coach sees a short summary.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.textSecondary
    )

    if (connected) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.primaryAccent, modifier = Modifier.size(18.dp))
            Text("Connected", style = MaterialTheme.typography.titleSmall, color = colors.primaryAccent)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PrimaryButton(
            text = if (connected) "Continue" else "Connect Health Connect",
            onClick = {
                if (connected) {
                    onNext()
                } else {
                    com.superfit.app.ui.launchHealthConnectPermissions(
                        context,
                        requestPermissionsLauncher,
                        healthConnectManager.permissions + healthConnectManager.bodyPermissions
                    )
                }
            }
        )
        if (!connected) {
            TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Not now. You can connect later in Settings.",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// Step 5 of 6: how the AI coach, reminders and the monthly recap talk.
@Composable
internal fun CoachStyleStep(
    selected: com.superfit.app.domain.CoachStyle,
    onSelect: (com.superfit.app.domain.CoachStyle) -> Unit,
    onNext: () -> Unit
) {
    StepTitle(
        title = "Pick your coach",
        subtitle = "How should your AI coach talk to you? Same advice, different voice. Change it any time in Settings."
    )
    com.superfit.app.ui.dashboard.CoachStyleOptions(selected = selected, onSelect = onSelect, showSamples = true)
    Text(
        "Also sets the voice of your monthly recap.",
        style = MaterialTheme.typography.bodySmall,
        color = SuperfitTheme.colors.textSecondary
    )
    PrimaryButton(text = "Continue", onClick = onNext)
}

// Step 6 of 6: the daily plan, with the same numbers Home will show.
@Composable
internal fun OnboardingSummaryStep(
    uiState: OnboardingUiState,
    onConnectHealth: () -> Unit,
    onStartClick: () -> Unit
) {
    val colors = SuperfitTheme.colors
    val plan = uiState.plan()
    val goalWords = when (uiState.goal) {
        "LOSE_WEIGHT" -> "a day to lose fat"
        "MAINTAIN" -> "a day to tone up"
        "GAIN_MUSCLE" -> "a day to build muscle"
        else -> "a day"
    }
    StepTitle(
        title = "You're all set",
        subtitle = "Here's your daily plan. Log what you eat for a few days and your coach gets to know you."
    )

    SuperfitCard(tint = colors.brandSoft) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Eat about", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            Text(
                "${"%,d".format(plan.targets.calories.roundToInt())} kcal",
                style = MaterialTheme.typography.displaySmall,
                color = colors.primaryAccent
            )
            Text(goalWords, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
        }
    }

    SuperfitCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ValueRow("Protein", "${plan.targets.proteinG.roundToInt()} g")
            ValueRow("Carbs", "${plan.targets.carbsG.roundToInt()} g")
            ValueRow("Fat", "${plan.targets.fatG.roundToInt()} g")
            ValueRow("Fibre", "${plan.fibreG.roundToInt()} g")
        }
    }

    SuperfitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Health Connect", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
            if (uiState.hasHealthConnectPermissions) {
                Text("Connected", style = MaterialTheme.typography.titleSmall, color = colors.primaryAccent)
            } else {
                Text("Not connected", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                TextButton(onClick = onConnectHealth, modifier = Modifier.padding(start = 4.dp)) {
                    Text("Connect", style = MaterialTheme.typography.labelLarge, color = colors.primaryAccent)
                }
            }
        }
    }

    uiState.error?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.errorColor)
    }

    PrimaryButton(text = "Start using Superfit", onClick = onStartClick)
}
