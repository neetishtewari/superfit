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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.superfit.app.ui.share.SharingSettingsCard

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
    /** The account's name and how it signs in, for Settings › Account. */
    name: String?,
    signInMethod: String,
    themeMode: String,
    onThemeMode: (String) -> Unit,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    /** Settings › Daily reminder, where the app has reminders. */
    reminders: @Composable () -> Unit = {},
    /** Settings › AI coach › What my AI coach knows, where the app has the coach chat. */
    coachMemory: @Composable () -> Unit = {},
    /** How to add the home-screen widget on this phone; null hides it. */
    widgetHelp: String? = null,
    /** Settings › Delete account; null hides it. */
    onDeleteAccount: (() -> Unit)? = null
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
        AccountCard(name = name, email = email, signInMethod = signInMethod)

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
                WorkoutImportSettings(model)
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
        widgetHelp?.let { help ->
            SettingsCard(spacing = 2) {
                Text("Home-screen widget", style = MaterialTheme.typography.titleSmall, color = SuperfitTheme.colors.textPrimary)
                Text(help, style = MaterialTheme.typography.bodySmall, color = SuperfitTheme.colors.textSecondary)
            }
        }

        SettingsGroupLabel("Sharing")
        // Today checks for a run or walk to offer again when it shows.
        SharingSettingsCard()

        AccountActionsCard(onSignOut = onSignOut, onDeleteAccount = onDeleteAccount)
        Spacer(Modifier.fillMaxWidth().height(24.dp))
    }
}

/** Workouts from other apps, from Today's own settings. */
@Composable
private fun WorkoutImportSettings(model: TodayModel) {
    val enabled by model.workoutImportEnabled.collectAsState()
    val prefs = model.workoutImportPrefs
    var mainApp by remember { mutableStateOf(prefs.mainApp) }
    var addBack by remember { mutableStateOf(prefs.addBack) }
    var hiddenCount by remember { mutableStateOf(prefs.hiddenIds.size) }
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(enabled) {
        if (enabled) apps = model.workoutSourceApps()
    }
    HorizontalDivider(color = SuperfitTheme.colors.glassBorder)
    WorkoutImportCard(
        enabled = enabled,
        sources = "Apple Watch, Strava, Nike Run Club and more, through ${HealthApp.name}. Each workout is counted once.",
        mainApp = mainApp,
        apps = apps,
        addBack = addBack,
        hiddenCount = hiddenCount,
        onEnabled = model::setWorkoutImportEnabled,
        onMainApp = {
            mainApp = it
            model.setMainWorkoutApp(it)
        },
        onAddBack = {
            addBack = it
            model.setWorkoutAddBack(it)
        },
        onShowHidden = {
            model.unhideAllWorkouts()
            hiddenCount = 0
        }
    )
}
