package com.superfit.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.superfit.app.data.CoachStyleStore
import com.superfit.app.data.Prefs
import com.superfit.app.domain.CoachStyle
import com.superfit.app.theme.NeonMint
import com.superfit.app.theme.SuperfitTheme

/** The six sign-up steps, in order. Health Connect comes early so "About you" can fill itself in. */
private const val STEP_WELCOME = 0
private const val STEP_HEALTH = 1
private const val STEP_ABOUT_YOU = 2
private const val STEP_GOAL = 3
private const val STEP_COACH = 4
private const val STEP_ALL_SET = 5
private const val STEP_COUNT = 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    model: OnboardingModel,
    /** The account's settings, where the coach style is saved. */
    prefs: Prefs,
    /**
     * Shows the phone's own health access sheet, then answers whether access was given; null where
     * the phone has no health app.
     */
    onConnectHealth: ((onDone: (granted: Boolean) -> Unit) -> Unit)?,
    onOnboardingComplete: () -> Unit,
    onBack: (() -> Unit)? = null,
    /** Reads the steps aloud; null hides the speaker button. */
    voiceGuide: OnboardingVoiceGuide? = null,
    modifier: Modifier = Modifier
) {
    val uiState by model.uiState.collectAsState()
    val colors = SuperfitTheme.colors
    val healthName = model.health?.appName?.takeIf { onConnectHealth != null }

    var currentStep by rememberSaveable { mutableIntStateOf(STEP_WELCOME) }
    // Supportive starts selected: Roast me is there for people who pick it themselves.
    var coachStyle by remember {
        mutableStateOf(if (CoachStyleStore.hasChosen(prefs)) CoachStyle.current else CoachStyle.SUPPORTIVE)
    }
    var aboutYouErrors by remember { mutableStateOf(AboutYouErrors()) }
    // Spoken guidance is opt-in: it starts off and the speaker button turns it on.
    var voiceGuideEnabled by remember { mutableStateOf(false) }
    var showBurnInfo by remember { mutableStateOf(false) }

    val guidanceVoices = remember(healthName) {
        mapOf(
            STEP_WELCOME to "Welcome to Superfit. Tell it what you ate, or snap your plate, and your AI coach does the counting. Tap Get started.",
            STEP_HEALTH to "Connect ${healthName ?: "your health app"}, and Superfit can add your steps and workouts, and fill in your height and weight. Or tap Not now.",
            STEP_ABOUT_YOU to "Now a few things about you, so we can work out how many calories your body burns at rest.",
            STEP_GOAL to "Pick your goal, and how active a normal week is. We'll set your daily calories and protein from this.",
            STEP_COACH to "Pick how your AI coach talks to you. Supportive, straight talk, or roast me. Same advice, different voice.",
            STEP_ALL_SET to "You're all set. Here's your daily plan. Tap Start using Superfit, and log your first meal."
        )
    }

    LaunchedEffect(currentStep, voiceGuideEnabled) {
        val guide = voiceGuide ?: return@LaunchedEffect
        guide.stop()
        if (!voiceGuideEnabled) return@LaunchedEffect
        guidanceVoices[currentStep]?.let(guide::speak)
    }
    DisposableEffect(voiceGuide) { onDispose { voiceGuide?.stop() } }

    // The health app's access sheet: on the Health step it moves on to "About you" with height and
    // weight filled in; from the last step it just refreshes "Connected".
    val connectHealth: () -> Unit = {
        onConnectHealth?.invoke { granted ->
            model.checkPermissions()
            if (granted) {
                model.autofillFromHealth()
                if (currentStep == STEP_HEALTH) currentStep = STEP_ABOUT_YOU
            }
        }
    }

    fun goBack() {
        aboutYouErrors = AboutYouErrors()
        currentStep = if (currentStep == STEP_ABOUT_YOU && healthName == null) STEP_WELCOME else currentStep - 1
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgStart)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Top bar: back, "Step N of 6", read aloud
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val backAction: (() -> Unit)? = if (currentStep > STEP_WELCOME) ({ goBack() }) else onBack
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (backAction != null) {
                        IconButton(onClick = backAction) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                        }
                    }
                }
                Text(
                    text = "Step ${currentStep + 1} of $STEP_COUNT",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                if (voiceGuide != null) {
                    IconButton(
                        onClick = { voiceGuideEnabled = !voiceGuideEnabled },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (voiceGuideEnabled) colors.brandSoft else androidx.compose.ui.graphics.Color.Transparent,
                            contentColor = if (voiceGuideEnabled) colors.primaryAccent else colors.textSecondary
                        )
                    ) {
                        Icon(
                            imageVector = if (voiceGuideEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = if (voiceGuideEnabled) "Stop reading aloud" else "Read aloud"
                        )
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }

            // Segmented progress: one green bar per finished or current step
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(STEP_COUNT) { i ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (i <= currentStep) NeonMint else colors.fieldBg)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(slideOutHorizontally { width -> -width } + fadeOut())
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(slideOutHorizontally { width -> width } + fadeOut())
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "StepNavigationTransition"
            ) { step ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    when (step) {
                        // Without a health app the Health step is skipped both ways.
                        STEP_WELCOME -> WelcomeStep(onNext = { currentStep = if (healthName != null) STEP_HEALTH else STEP_ABOUT_YOU })
                        STEP_HEALTH -> HealthConnectStep(
                            uiState = uiState,
                            healthName = healthName ?: "your health app",
                            onConnect = connectHealth,
                            onNext = { currentStep = STEP_ABOUT_YOU }
                        )
                        STEP_ABOUT_YOU -> AboutYouStep(
                            uiState = uiState,
                            healthName = healthName ?: "your health app",
                            model = model,
                            errors = aboutYouErrors,
                            onNext = {
                                val errors = AboutYouErrors(
                                    birth = uiState.birthProblem(),
                                    height = (uiState.height.toDoubleOrNull() ?: 0.0) <= 0.0,
                                    weight = (uiState.weight.toDoubleOrNull() ?: 0.0) <= 0.0
                                )
                                aboutYouErrors = errors
                                if (!errors.any) currentStep = STEP_GOAL
                            }
                        )
                        STEP_GOAL -> GoalStep(
                            uiState = uiState,
                            model = model,
                            onOpenBurnInfo = { showBurnInfo = true },
                            onNext = { currentStep = STEP_COACH }
                        )
                        STEP_COACH -> CoachStyleStep(
                            selected = coachStyle,
                            onSelect = { coachStyle = it },
                            onNext = {
                                CoachStyleStore.save(prefs, coachStyle)
                                currentStep = STEP_ALL_SET
                            }
                        )
                        STEP_ALL_SET -> OnboardingSummaryStep(
                            uiState = uiState,
                            healthName = healthName,
                            onConnectHealth = connectHealth,
                            onStartClick = { model.saveProfile(onOnboardingComplete) }
                        )
                    }
                }
            }
        }

        if (showBurnInfo) {
            AlertDialog(
                onDismissRequest = { showBurnInfo = false },
                containerColor = colors.cardBg,
                title = { Text("What you burn in a day", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary) },
                text = {
                    Text(
                        "This is roughly what your body uses in a day: what it burns at rest, plus everyday moving. " +
                            "Your target starts here, then goes down to lose fat or up to build muscle. " +
                            "Workouts you log or connect are added on top.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showBurnInfo = false }) {
                        Text("Got it", style = MaterialTheme.typography.labelLarge, color = colors.primaryAccent)
                    }
                }
            )
        }
    }
}
