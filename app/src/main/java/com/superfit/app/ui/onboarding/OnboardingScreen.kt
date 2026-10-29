package com.superfit.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import com.superfit.app.data.HealthConnectManager
import com.superfit.app.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    healthConnectManager: HealthConnectManager,
    viewModel: OnboardingViewModel,
    onOnboardingComplete: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDarkTheme by ThemeConfig.isDarkTheme.collectAsState()

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Wizard step state (0 to 5)
    var currentStep by remember { mutableIntStateOf(0) }
    // Supportive starts selected: Roast me is there for people who pick it themselves.
    var coachStyle by remember {
        mutableStateOf(if (com.superfit.app.data.CoachStyleStore.hasChosen(context)) com.superfit.app.domain.CoachStyle.current else com.superfit.app.domain.CoachStyle.SUPPORTIVE)
    }
    var validationError by remember { mutableStateOf<String?>(null) }
    // Spoken guidance is opt-in: it starts off and the "Voice" chip turns it on.
    var voiceGuideEnabled by remember { mutableStateOf(false) }

    // Tooltip / Cheat Sheet dialog states
    var showHeightHelper by remember { mutableStateOf(false) }
    var showTdeeHelper by remember { mutableStateOf(false) }

    // Text to Speech engine configuration
    var tts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    DisposableEffect(context) {
        var ttsInstance: android.speech.tts.TextToSpeech? = null
        ttsInstance = android.speech.tts.TextToSpeech(context) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                try {
                    ttsInstance?.language = java.util.Locale.US
                } catch (e: Exception) {
                    // Fallback
                }
            }
        }
        tts = ttsInstance
        onDispose {
            ttsInstance.stop()
            ttsInstance.shutdown()
        }
    }

    val guidanceVoices = remember {
        mapOf(
            0 to "Welcome to Superfit! It is the absolute easiest way to track your fitness and get personalized, science-backed insights. Choose your appearance preference and tap Get Started to begin our journey!",
            1 to "Let's enter your essential stats so we can calculate your resting metabolism.",
            2 to "Awesome! Now, select your baseline activity level and fitness goals. We'll use these to find your daily energy budget.",
            3 to "Pick how your AI coach talks to you. Supportive, straight talk, or roast me.",
            4 to "Great. Now let's connect your steps and sleep. Tap Connect, and your height and weight can fill in too.",
            5 to "We are all set! Remember that tracking your meals and workouts is now easier than ever. By logging your entries regularly, you provide richer, more complete data for analysis—making Superfit vastly more useful to help you reach your goals. Activate your coach to start!"
        )
    }

    // Dynamic narration triggers on step navigation
    LaunchedEffect(currentStep, voiceGuideEnabled) {
        val currentTts = tts ?: return@LaunchedEffect
        if (!voiceGuideEnabled) {
            currentTts.stop()
            return@LaunchedEffect
        }

        currentTts.stop()

        val voicePhrase = guidanceVoices[currentStep] ?: ""
        if (voicePhrase.isNotEmpty()) {
            if (currentStep == 0) {
                val part1 = "Welcome to Superfit!"
                val part2 = "It is the easiest way to track your fitness and get personalized, science-backed insights. Choose your appearance preference and tap Get Started to begin our journey!"

                speakUtterance(currentTts, part1)
                while (currentTts.isSpeaking) {
                    kotlinx.coroutines.delay(100)
                }
                kotlinx.coroutines.delay(1400) // Precise 1.4 second pause strictly after Greeting
                speakUtterance(currentTts, part2)
            } else {
                speakUtterance(currentTts, voicePhrase)
            }
        }
    }

    val view = LocalView.current
    DisposableEffect(view) {
        val original = view.importantForAutofill
        view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        onDispose {
            view.importantForAutofill = original
        }
    }

    // Health Connect Permission Launcher
    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        viewModel.checkPermissions()
        if (granted.isNotEmpty()) {
            viewModel.autofillFromHealthConnect()
            currentStep = 5
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(ThemeBgStart, ThemeBgEnd)
                )
            )
    ) {
        // Ambient Aurora background glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(HyperViolet.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(0f, 0f),
                    radius = size.minDimension * 0.8f
                ),
                radius = size.minDimension * 0.8f,
                center = Offset(0f, 0f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(com.superfit.app.theme.ElectricCyan.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width, size.height * 0.5f),
                    radius = size.minDimension * 0.7f
                ),
                radius = size.minDimension * 0.7f,
                center = Offset(size.width, size.height * 0.5f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header navigation / actions bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentStep > 0) {
                    IconButton(
                        onClick = {
                            validationError = null
                            currentStep -= 1
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ThemeCardBgTranslucent)
                            .border(1.dp, ThemeGlassBorder, RoundedCornerShape(10.dp)),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = ThemeTextPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate Back",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ThemeCardBgTranslucent)
                                .border(1.dp, ThemeGlassBorder, RoundedCornerShape(10.dp)),
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = ThemeTextPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Exit Preview Mode",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(40.dp))
                    }
                }

                Text(
                    text = "SUPERFIT",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = ThemeTextPrimary,
                    letterSpacing = 2.sp
                )

                // Voice Guide Equalizer / Toggle button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (voiceGuideEnabled) ElectricCyan.copy(alpha = 0.12f) else ThemeTextPrimary.copy(alpha = 0.03f))
                        .border(1.dp, if (voiceGuideEnabled) ElectricCyan else ThemeGlassBorder, RoundedCornerShape(20.dp))
                        .clickable { voiceGuideEnabled = !voiceGuideEnabled }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (voiceGuideEnabled) "Voice: On" else "Voice: Off",
                        color = if (voiceGuideEnabled) ElectricCyan else ThemeTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Segmented progress indicator bar
            val animatedProgress by animateFloatAsState(
                targetValue = (currentStep + 1) / 6f,
                label = "progressBarGlow"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.06f), shape = RoundedCornerShape(10.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .background(
                            brush = Brush.horizontalGradient(listOf(NeonGreen, com.superfit.app.theme.ElectricCyan)),
                            shape = RoundedCornerShape(10.dp)
                        )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Wizard step view content area (Animated slide/scale entries)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut()
                            )
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut()
                            )
                        }
                    },
                    label = "StepNavigationTransition"
                ) { step ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (step) {
                            0 -> WelcomeStep(
                                isDarkTheme = isDarkTheme,
                                onThemeChange = { isDark -> ThemeConfig.setThemeMode(context, isDark) },
                                onNext = { currentStep = 1 }
                            )
                            1 -> PhysiologyStep(
                                uiState = uiState,
                                viewModel = viewModel,
                                validationError = validationError,
                                onOpenCheatSheet = { showHeightHelper = true },
                                onNext = {
                                    val ageInt = uiState.age.toIntOrNull()
                                    val heightDouble = uiState.height.toDoubleOrNull()
                                    val weightDouble = uiState.weight.toDoubleOrNull()
                                    if (ageInt == null || ageInt <= 0 || heightDouble == null || heightDouble <= 0 || weightDouble == null || weightDouble <= 0) {
                                        validationError = "Please enter valid age, weight, and height measurements to continue."
                                    } else {
                                        validationError = null
                                        currentStep = 2
                                    }
                                }
                            )
                            2 -> ActivityGoalsStep(
                                uiState = uiState,
                                viewModel = viewModel,
                                onOpenTdeePopup = { showTdeeHelper = true },
                                onNext = { currentStep = 3 }
                            )
                            3 -> CoachStyleStep(
                                selected = coachStyle,
                                onSelect = { coachStyle = it },
                                onNext = {
                                    com.superfit.app.data.CoachStyleStore.save(context, coachStyle)
                                    currentStep = 4
                                }
                            )
                            4 -> HealthConnectStep(
                                uiState = uiState,
                                healthConnectManager = healthConnectManager,
                                requestPermissionsLauncher = requestPermissionsLauncher,
                                onNext = { currentStep = 5 }
                            )
                            5 -> OnboardingSummaryStep(
                                uiState = uiState,
                                onStartClick = { viewModel.saveProfile(onOnboardingComplete) }
                            )
                        }
                    }
                }
            }
        }

        // Height Helper cheat sheet dialog overlay
        if (showHeightHelper) {
            AlertDialog(
                onDismissRequest = { showHeightHelper = false },
                title = {
                    Text(
                        text = "Height conversion chart",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = ThemeTextPrimary
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Reference conversions from feet and inches to metric centimeters:",
                            fontSize = 13.sp,
                            color = ThemeTextSecondary
                        )
                        HorizontalDivider(color = ThemeTextPrimary.copy(alpha = 0.05f))

                        val chart = listOf(
                            "5'0\" (5 ft 0 in)" to "152 cm",
                            "5'2\" (5 ft 2 in)" to "157 cm",
                            "5'4\" (5 ft 4 in)" to "163 cm",
                            "5'6\" (5 ft 6 in)" to "168 cm",
                            "5'8\" (5 ft 8 in)" to "173 cm",
                            "5'10\" (5 ft 10 in)" to "178 cm",
                            "6'0\" (6 ft 0 in)" to "183 cm",
                            "6'2\" (6 ft 2 in)" to "188 cm"
                        )
                        chart.forEach { (imp, met) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = imp, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ThemeTextPrimary)
                                Text(text = met, fontSize = 12.sp, color = ThemeTextSecondary)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showHeightHelper = false },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.White)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = ThemeCardBgTranslucent,
                modifier = Modifier.border(1.dp, ThemeGlassBorder, RoundedCornerShape(28.dp))
            )
        }

        // TDEE informational popup overlay
        if (showTdeeHelper) {
            AlertDialog(
                onDismissRequest = { showTdeeHelper = false },
                title = {
                    Text(
                        text = "What you burn in a day",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = ThemeTextPrimary
                    )
                },
                text = {
                    Text(
                        text = "Total Daily Energy Expenditure (TDEE) is the total calories your body burns in 24 hours. " +
                            "It is calculated by multiplying your resting metabolic speed (BMR) with your physical baseline activity multiplier. " +
                            "Superfit uses your TDEE as the starting energy budget from which we subtract/add calories to meet your targets.",
                        fontSize = 13.sp,
                        color = ThemeTextSecondary,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { showTdeeHelper = false },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.White)
                    ) {
                        Text("Got it", fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = ThemeCardBgTranslucent,
                modifier = Modifier.border(1.dp, ThemeGlassBorder, RoundedCornerShape(28.dp))
            )
        }
    }
}
