package com.superfit.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.superfit.app.data.Prefs
import com.superfit.app.domain.HealthApp
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.ui.dashboard.CoachStyleSettings
import com.superfit.app.ui.dashboard.DashboardUiState
import com.superfit.app.ui.dashboard.HealthSettingsSection
import com.superfit.app.ui.dashboard.NutritionistAdviceSettings
import com.superfit.app.ui.dashboard.TodayModel

/**
 * Settings on the iPhone, from the same cards as Android: the account, your plan (with your
 * nutritionist's advice), Apple Health, the coach's style, the look and the daily reminder. What the
 * coach remembers, sharing and privacy join with their iPhone parts.
 */
@Composable
fun SettingsPage(
    model: TodayModel,
    prefs: Prefs,
    email: String?,
    themeMode: String,
    onThemeMode: (String) -> Unit,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    /** Settings › Daily reminder, where the app has reminders. */
    reminders: @Composable () -> Unit = {},
    /** Settings › AI coach › What my AI coach knows, where the app has the coach chat. */
    coachMemory: @Composable () -> Unit = {}
) {
    val state by model.state.collectAsState()
    val stepGoal by model.stepGoal.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SuperfitTheme.colors.bgStart)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = SuperfitTheme.colors.cardBg,
                    contentColor = SuperfitTheme.colors.textPrimary
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(22.dp))
            }
            Text("Settings", style = MaterialTheme.typography.titleLarge, color = SuperfitTheme.colors.textPrimary)
        }

        SettingsGroupLabel("Account")
        // Only email and password on the iPhone for now; Google and Apple sign-in come later.
        AccountCard(name = null, email = email, signInMethod = "Email and password")

        SettingsGroupLabel("Your plan")
        (state as? DashboardUiState.Success)?.let { success ->
            AboutYouCard(
                profile = success.profile,
                ownTargets = prefs.getBoolean("custom_macro_enabled", false),
                onSave = model::saveAboutYou
            )
        }
        PlanSettingsCard(
            state = state,
            stepGoal = stepGoal,
            prefs = prefs,
            onGoal = model::updateGoal,
            onSetOwnStepGoal = model::setOwnStepGoal,
            // Today works its numbers out again with the new targets.
            onTargetsChanged = { model.refreshTargets() },
            confirmInCard = true,
            nutritionistAdvice = {
                NutritionistAdviceSettings(
                    prefs = prefs,
                    onSave = model::saveNutritionistAdvice,
                    onApplyTargets = model::applyNutritionistTargets
                )
            }
        )

        if (model.healthAvailable) {
            SettingsGroupLabel(HealthApp.name)
            SettingsCard {
                val healthAsked by model.healthAsked.collectAsState()
                HealthSettingsSection(asked = healthAsked, onConnect = model::connectHealth)
            }
        }

        SettingsGroupLabel("AI coach")
        SettingsCard(spacing = 14) {
            val coachStyle by model.coachStyle.collectAsState()
            CoachStyleSettings(coachStyle, model::setCoachStyle)
            coachMemory()
        }

        AppearanceCard(mode = themeMode, onSelect = onThemeMode)
        reminders()

        // Deleting the account on the iPhone comes with its own step (the App Store requires it).
        AccountActionsCard(onSignOut = onSignOut, onDeleteAccount = null)
        Spacer(Modifier.fillMaxWidth().height(24.dp))
    }
}
