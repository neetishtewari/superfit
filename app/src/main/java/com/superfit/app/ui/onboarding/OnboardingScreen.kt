package com.superfit.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import com.superfit.app.data.HealthConnectManager
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.ui.platform.LocalView
import android.view.View

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import com.superfit.app.theme.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalClipboardManager

// Premium color system mapping
private val NeonGreen = NeonMint
private val GradientStart = NeonMint
private val GradientEnd = HyperViolet

private fun speakUtterance(tts: android.speech.tts.TextToSpeech, text: String) {
    val voices = tts.voices
    if (!voices.isNullOrEmpty()) {
        val selectedVoice = voices.find { v -> v.name.contains("Google US English", ignoreCase = true) && v.locale.language == "en" }
            ?: voices.find { v -> v.name.contains("natural", ignoreCase = true) && v.locale.language == "en" }
            ?: voices.find { v -> v.name.contains("premium", ignoreCase = true) && v.locale.language == "en" }
            ?: voices.find { v -> v.locale.language == "en" && v.locale.country == "US" }
            ?: voices.find { v -> v.locale.language == "en" }
        
        if (selectedVoice != null) {
            try {
                tts.voice = selectedVoice
            } catch (e: Exception) {
                // Fallback
            }
        }
    }
    tts.setPitch(1.05f)
    tts.setSpeechRate(0.85f)
    tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_ADD, null, "onboarding_chunk_${System.currentTimeMillis()}")
}

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
    
    // Wizard step state (0 to 6)
    var currentStep by remember { mutableIntStateOf(0) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var voiceGuideEnabled by remember { mutableStateOf(true) }
    
    // Tooltip / Cheat Sheet dialog states
    var showHeightHelper by remember { mutableStateOf(false) }
    var showTdeeHelper by remember { mutableStateOf(false) }

    // Text to Speech engine configuration
    var tts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    DisposableEffect(context) {
        val ttsInstance = android.speech.tts.TextToSpeech(context) { status -> }
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
            3 to "Great. Now, let's sync your daily steps and sleep telemetry locally. Tap connect to auto-fill your physical details in one tap.",
            4 to "Let's configure your AI helper. Set up your free Gemini key to enable voice logging. It's super simple, completely free, and your key stays one hundred percent secure on your device. Note that this key is required to activate the assistant.",
            5 to "Perfect. Let's grant microphone access so you can speak your meals and workouts directly to your AI companion.",
            6 to "We are all set! Remember that tracking your meals and workouts is now easier than ever. By logging your entries regularly, you provide richer, more complete data for analysis—making Superfit vastly more useful to help you reach your goals. Activate your coach to start!"
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
            currentStep = 4
        }
    }

    // Microphone Permission Launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onMicPermissionStateChanged(isGranted)
        if (isGranted) {
            currentStep = 6
        } else {
            Toast.makeText(context, "Opening Settings to grant microphone permission...", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Please open Settings and grant microphone permissions manually.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Auto check permissions on entry
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onMicPermissionStateChanged(hasPermission)
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
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Segmented progress indicator bar
            val animatedProgress by animateFloatAsState(
                targetValue = (currentStep + 1) / 7f,
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
                            3 -> HealthConnectStep(
                                uiState = uiState,
                                healthConnectManager = healthConnectManager,
                                requestPermissionsLauncher = requestPermissionsLauncher,
                                onNext = { currentStep = 4 }
                            )
                            4 -> GeminiApiKeyStep(
                                uiState = uiState,
                                viewModel = viewModel,
                                validationError = validationError,
                                onNext = {
                                    val trimmed = uiState.apiKey.trim()
                                    if (trimmed.isEmpty() || (!trimmed.startsWith("AIzaSy") && !trimmed.startsWith("AQ."))) {
                                        validationError = "Please enter a valid Gemini API Key starting with 'AIzaSy' or 'AQ.'."
                                    } else {
                                        validationError = null
                                        currentStep = 5
                                    }
                                }
                            )
                            5 -> MicrophonePermissionStep(
                                uiState = uiState,
                                micPermissionLauncher = micPermissionLauncher,
                                onNext = { currentStep = 6 }
                            )
                            6 -> OnboardingSummaryStep(
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
                        text = "📏 Height Conversion Chart",
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
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black)
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
                        text = "📊 What is TDEE?",
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
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black)
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

// 1. Welcome Step View (Step 0)
@Composable
private fun WelcomeStep(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onNext: () -> Unit
) {
    Text(
        text = "Easiest way to track your fitness & get personalized insights",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = ThemeTextPrimary,
        lineHeight = 30.sp
    )

    // Upfront Appearance Customizer segmented toggle slider
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Choose Appearance",
            fontSize = 12.sp,
            color = Color.Gray,
            fontWeight = FontWeight.SemiBold
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ThemeTextPrimary.copy(alpha = 0.03f))
                .border(1.dp, ThemeGlassBorder, RoundedCornerShape(12.dp))
        ) {
            val themeSliderOffset by animateFloatAsState(
                targetValue = if (isDarkTheme) 0f else 1f,
                label = "ThemeSliderPill"
            )
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val halfWidth = maxWidth / 2
                Box(
                    modifier = Modifier
                        .offset(x = halfWidth * themeSliderOffset)
                        .width(halfWidth)
                        .fillMaxHeight()
                        .padding(3.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(HyperViolet)
                )
            }
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onThemeChange(true) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🌙 Dark Mode",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onThemeChange(false) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "☀️ Light Mode",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }

    // Simplified High-Impact Bullet highlights
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        FeatureItemBullet(icon = "🎙️", title = "Multilingual Voice Log", desc = "Speak naturally. Track macro entries in any Indian language.")
        FeatureItemBullet(icon = "🔒", title = "100% On-Device Privacy", desc = "Your health telemetry stays strictly on your device.")
        FeatureItemBullet(icon = "💸", title = "Free & Ad-Free", desc = "Full feature set with no paywalls or subscription barriers.")
    }

    Button(
        onClick = onNext,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(GradientStart, GradientEnd))),
            contentAlignment = Alignment.Center
        ) {
            Text("Get Started", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

// 2. Physiology Metrics Step View (Step 1)
@Composable
private fun PhysiologyStep(
    uiState: OnboardingUiState,
    viewModel: OnboardingViewModel,
    validationError: String?,
    onOpenCheatSheet: () -> Unit,
    onNext: () -> Unit
) {
    Text(
        text = "Physiological Profile",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = ThemeTextPrimary
    )
    Text(
        text = "Enter your core physical metrics. We use these to calculate your baseline resting metabolism.",
        fontSize = 13.sp,
        color = Color.Gray,
        lineHeight = 18.sp
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ThemeCardBgTranslucent)
            .border(1.dp, ThemeGlassBorder, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Sex Toggle Segment
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "Biological Sex", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ThemeTextPrimary.copy(alpha = 0.03f))
                    .border(1.dp, ThemeGlassBorder, RoundedCornerShape(10.dp))
            ) {
                val sexSliderOffset by animateFloatAsState(
                    targetValue = if (uiState.isMale) 0f else 1f,
                    label = "SexSliderPill"
                )
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val halfWidth = maxWidth / 2
                    Box(
                        modifier = Modifier
                            .offset(x = halfWidth * sexSliderOffset)
                            .width(halfWidth)
                            .fillMaxHeight()
                            .padding(2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeonGreen)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { viewModel.onSexChanged(true) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Male", color = if (uiState.isMale) Color.Black else ThemeTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { viewModel.onSexChanged(false) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Female", color = if (!uiState.isMale) Color.Black else ThemeTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Age field
        OutlinedTextField(
            value = uiState.age,
            onValueChange = { viewModel.onAgeChanged(it) },
            label = { Text("Age (Years)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonGreen,
                unfocusedBorderColor = ThemeGlassBorder,
                focusedTextColor = ThemeTextPrimary,
                unfocusedTextColor = ThemeTextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Height field strictly in Centimeters with Live Foot/Inches conversion text below it
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Height", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Foot/Inches Helper",
                    fontSize = 11.sp,
                    color = HyperViolet,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onOpenCheatSheet() }
                )
            }
            OutlinedTextField(
                value = uiState.height,
                onValueChange = { viewModel.onHeightChanged(it) },
                placeholder = { Text("e.g. 175") },
                label = { Text("Height (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonGreen,
                    unfocusedBorderColor = ThemeGlassBorder,
                    focusedTextColor = ThemeTextPrimary,
                    unfocusedTextColor = ThemeTextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
            // Live Feet/Inches equivalent readout calculation
            val heightDouble = uiState.height.toDoubleOrNull()
            val ftInReadout = if (heightDouble != null && heightDouble > 0) {
                val totalInches = heightDouble / 2.54
                val feet = (totalInches / 12).toInt()
                val inches = Math.round(totalInches % 12).toInt()
                val displayInches = if (inches == 12) 0 else inches
                val displayFeet = if (inches == 12) feet + 1 else feet
                "Equivalent: $displayFeet ft $displayInches in"
            } else {
                "Equivalent: - ft - in"
            }
            Text(text = ftInReadout, color = com.superfit.app.theme.ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        // Weight field
        OutlinedTextField(
            value = uiState.weight,
            onValueChange = { viewModel.onWeightChanged(it) },
            label = { Text("Weight (kg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonGreen,
                unfocusedBorderColor = ThemeGlassBorder,
                focusedTextColor = ThemeTextPrimary,
                unfocusedTextColor = ThemeTextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (validationError != null) {
        Text(text = validationError, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }

    Button(
        onClick = onNext,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(GradientStart, GradientEnd))),
            contentAlignment = Alignment.Center
        ) {
            Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

// 3. Baseline Activity & Goal selection (Step 2)
@Composable
private fun ActivityGoalsStep(
    uiState: OnboardingUiState,
    viewModel: OnboardingViewModel,
    onOpenTdeePopup: () -> Unit,
    onNext: () -> Unit
) {
    Text(
        text = "Baseline Activity",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = ThemeTextPrimary
    )
    Text(
        text = "Configure your baseline activity level and goals. We use these to calculate your target calorie budget.",
        fontSize = 13.sp,
        color = Color.Gray,
        lineHeight = 18.sp
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ThemeCardBgTranslucent)
            .border(1.dp, ThemeGlassBorder, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Activity Slider
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val multiplierDesc = when (uiState.activityMultiplier) {
                1.2 -> "Sedentary (No formal exercise)"
                1.375 -> "Lightly Active (1-3 days/week)"
                1.55 -> "Moderately Active (3-5 days/week)"
                1.725 -> "Very Active (6-7 days/week)"
                else -> "Active Athlete"
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Activity Level", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                Text(text = "${uiState.activityMultiplier}", fontSize = 12.sp, color = NeonGreen, fontWeight = FontWeight.Bold)
            }
            Text(text = multiplierDesc, fontSize = 14.sp, color = ThemeTextPrimary, fontWeight = FontWeight.Bold)

            val values = listOf(1.2, 1.375, 1.55, 1.725)
            Slider(
                value = values.indexOf(uiState.activityMultiplier).toFloat().coerceAtLeast(0f),
                onValueChange = { index ->
                    val idx = index.toInt().coerceIn(0, values.size - 1)
                    viewModel.onActivityMultiplierChanged(values[idx])
                },
                valueRange = 0f..3f,
                steps = 2,
                colors = SliderDefaults.colors(
                    activeTrackColor = NeonGreen,
                    thumbColor = NeonGreen,
                    inactiveTrackColor = Color.DarkGray
                )
            )
        }

        // Goals Cards selection list
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "Fitness Goal", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)

            val goalsList = listOf(
                Triple("LOSE_WEIGHT", "Weight Loss (-500 kcal)", -500),
                Triple("MAINTAIN", "Maintenance (Energy Balance)", 0),
                Triple("GAIN_MUSCLE", "Muscle Gain (+300 kcal)", 300)
            )

            goalsList.forEach { (goalKey, goalLabel, offset) ->
                val isSelected = uiState.goal == goalKey
                val cardBg = if (isSelected) NeonGreen.copy(alpha = 0.08f) else ThemeTextPrimary.copy(alpha = 0.02f)
                val cardBorder = if (isSelected) NeonGreen else ThemeGlassBorder
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(cardBg)
                        .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                        .clickable { viewModel.onGoalChanged(goalKey, offset) }
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = goalLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isSelected) NeonGreen else ThemeTextPrimary)
                    RadioButton(
                        selected = isSelected,
                        onClick = { viewModel.onGoalChanged(goalKey, offset) },
                        colors = RadioButtonDefaults.colors(selectedColor = NeonGreen, unselectedColor = Color.Gray)
                    )
                }
            }
        }

        // Live Mifflin-St Jeor TDEE calculation Ticker
        val ageVal = uiState.age.toIntOrNull() ?: 28
        val heightVal = uiState.height.toDoubleOrNull() ?: 175.0
        val weightVal = uiState.weight.toDoubleOrNull() ?: 75.0
        val bmr = if (uiState.isMale) {
            10.0 * weightVal + 6.25 * heightVal - 5.0 * ageVal + 5.0
        } else {
            10.0 * weightVal + 6.25 * heightVal - 5.0 * ageVal - 161.0
        }
        val tdeeVal = bmr * uiState.activityMultiplier
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(com.superfit.app.theme.ElectricCyan.copy(alpha = 0.06f), HyperViolet.copy(alpha = 0.06f))))
                .border(1.dp, com.superfit.app.theme.ElectricCyan.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "Daily TDEE", color = com.superfit.app.theme.ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "What is this?",
                        fontSize = 11.sp,
                        color = HyperViolet,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { onOpenTdeePopup() }
                    )
                }
                Text(text = "${Math.round(tdeeVal)} kcal", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ThemeTextPrimary)
            }
        }
    }

    Button(
        onClick = onNext,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(GradientStart, GradientEnd))),
            contentAlignment = Alignment.Center
        ) {
            Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

// 4. Google Health Connect Sync Step (Step 3)
@Composable
private fun HealthConnectStep(
    uiState: OnboardingUiState,
    healthConnectManager: HealthConnectManager,
    requestPermissionsLauncher: androidx.activity.result.ActivityResultLauncher<Set<String>>,
    onNext: () -> Unit
) {
    val isHcSynced = uiState.hasHealthConnectPermissions
    Text(
        text = "Google Health Sync",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = ThemeTextPrimary
    )
    Text(
        text = "Sync your baseline activity telemetry. Superfit reads steps, sleep cycles, and active workouts locally from your device.",
        fontSize = 13.sp,
        color = Color.Gray,
        lineHeight = 18.sp
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ThemeCardBgTranslucent)
            .border(1.dp, ThemeGlassBorder, RoundedCornerShape(20.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "💚", fontSize = 42.sp)
        Text(text = "Health Connect Status", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ThemeTextPrimary)
        
        val badgeColor = if (isHcSynced) NeonGreen else Color.Gray
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(badgeColor.copy(alpha = 0.15f))
                .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (isHcSynced) "Connected" else "Disconnected",
                color = badgeColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }

        Text(
            text = "Connecting allows Superfit to auto-calculate metabolic offsets from sleep quality and daily workouts.",
            fontSize = 12.sp,
            color = Color.LightGray,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
        Text(text = "Privacy first. Health records stay strictly locally stored on-device.", fontSize = 11.sp, color = Color.Gray)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                if (isHcSynced) {
                    onNext()
                } else {
                    requestPermissionsLauncher.launch(healthConnectManager.permissions)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(listOf(GradientStart, GradientEnd))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isHcSynced) "Continue" else "Connect Google Health Connect",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (isHcSynced) "Skip / Connect Later" else "Skip / Connect Later",
                color = Color.Gray,
                fontSize = 13.sp
            )
        }
    }
}

// 5. Gemini API Key Configuration Step (Step 4)
@Composable
private fun GeminiApiKeyStep(
    uiState: OnboardingUiState,
    viewModel: OnboardingViewModel,
    validationError: String?,
    onNext: () -> Unit
) {
    val context = LocalContext.current

    Text(
        text = "AI Assistant Setup",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = ThemeTextPrimary
    )
    Text(
        text = "Superfit uses Gemini for natural voice logging. Get a free API key in 2 clicks. Stored 100% securely on-device.",
        fontSize = 13.sp,
        color = Color.LightGray,
        lineHeight = 18.sp
    )

    // Simplified Setup Checklist guide
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ThemeTextPrimary.copy(alpha = 0.02f))
            .border(1.dp, ThemeGlassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StepGuideItem(number = "1", text = "Tap 'Get Free API Key' to open Google AI Studio.")
        StepGuideItem(number = "2", text = "Tap 'Create API Key' in project and copy it.")
        StepGuideItem(number = "3", text = "Return here and tap 'Paste' to save key.")
    }

    var keyVisible by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = uiState.apiKey,
            onValueChange = { viewModel.onApiKeyChanged(it) },
            label = { Text("Gemini API Key") },
            placeholder = { Text("Starts with AIzaSy or AQ...") },
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val icon = if (keyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(imageVector = icon, contentDescription = "Toggle Visibility", tint = Color.Gray)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonGreen,
                unfocusedBorderColor = ThemeGlassBorder,
                focusedTextColor = ThemeTextPrimary,
                unfocusedTextColor = ThemeTextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Clipboard Paste Helper
        val clipboardManager = LocalClipboardManager.current
        Button(
            onClick = {
                val pasted = clipboardManager.getText()?.text
                if (!pasted.isNullOrBlank()) {
                    viewModel.onApiKeyChanged(pasted.trim())
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = ThemeTextPrimary.copy(alpha = 0.05f),
                contentColor = ThemeTextPrimary
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(text = "Paste Key", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }

    if (validationError != null) {
        Text(text = validationError, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open Browser. Please visit: https://aistudio.google.com/app/apikey", Toast.LENGTH_LONG).show()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = com.superfit.app.theme.ElectricCyan.copy(alpha = 0.08f),
                contentColor = com.superfit.app.theme.ElectricCyan
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp).border(1.dp, com.superfit.app.theme.ElectricCyan.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
        ) {
            Text(text = "Get Free API Key", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(listOf(GradientStart, GradientEnd))),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Save & Continue", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        // Skip button completely omitted from setup step 4
    }
}

// 6. Microphone Permission Step View (Step 5)
@Composable
private fun MicrophonePermissionStep(
    uiState: OnboardingUiState,
    micPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    onNext: () -> Unit
) {
    val granted = uiState.isMicPermissionGranted
    Text(
        text = "Voice Permission",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = ThemeTextPrimary
    )
    Text(
        text = "Required to transcribe your speech when dictating meals. Say what you had, and AI does the calculations.",
        fontSize = 13.sp,
        color = Color.Gray,
        lineHeight = 18.sp
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ThemeCardBgTranslucent)
            .border(1.dp, ThemeGlassBorder, RoundedCornerShape(20.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "🎙️", fontSize = 42.sp)
        Text(text = "Microphone Permission", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ThemeTextPrimary)

        val badgeColor = if (granted) NeonGreen else Color.Gray
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(badgeColor.copy(alpha = 0.15f))
                .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (granted) "Granted" else "Not Granted",
                color = badgeColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }

        Text(
            text = "Superfit records audio only when you actively press the voice log button on your ledger. No background listening.",
            fontSize = 12.sp,
            color = Color.LightGray,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                if (granted) {
                    onNext()
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(listOf(GradientStart, GradientEnd))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (granted) "Continue" else "Grant Microphone Access",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Skip / Grant Later", color = Color.Gray, fontSize = 13.sp)
        }
    }
}

// 7. Onboarding Summary & Targets (Step 6)
@Composable
private fun OnboardingSummaryStep(
    uiState: OnboardingUiState,
    onStartClick: () -> Unit
) {
    Text(
        text = "Activate Your AI Advisor",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = ThemeTextPrimary
    )
    Text(
        text = "Tracking your meals and workouts is now easier than ever! By logging your entries regularly, you provide richer, more complete data for analysis—making Superfit vastly more useful and accurate in helping you reach your fitness goals.",
        fontSize = 13.sp,
        color = Color.Gray,
        lineHeight = 18.sp
    )

    // Calculate final targets
    val ageVal = uiState.age.toIntOrNull() ?: 28
    val heightVal = uiState.height.toDoubleOrNull() ?: 175.0
    val weightVal = uiState.weight.toDoubleOrNull() ?: 75.0
    val bmr = if (uiState.isMale) {
        10.0 * weightVal + 6.25 * heightVal - 5.0 * ageVal + 5.0
    } else {
        10.0 * weightVal + 6.25 * heightVal - 5.0 * ageVal - 161.0
    }
    val tdee = bmr * uiState.activityMultiplier
    val calories = Math.max(1200, Math.round(tdee) + uiState.calorieOffset)

    // Recommended macros Target split
    val protein = Math.max(40, (weightVal * 2.0).toInt())
    val fat = Math.max(30, ((calories * 0.25) / 9.0).toInt())
    val proteinCalories = protein * 4.0
    val fatCalories = fat * 9.0
    val carbs = Math.max(50, ((calories - proteinCalories - fatCalories) / 4.0).toInt())

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary Cards grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ThemeTextPrimary.copy(alpha = 0.02f))
                    .border(1.5.dp, ThemeGlassBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "CALCULATED BMR", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text(text = "${Math.round(bmr)} kcal", fontSize = 18.sp, fontWeight = FontWeight.Black, color = ThemeTextPrimary)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ThemeTextPrimary.copy(alpha = 0.02f))
                    .border(1.5.dp, ThemeGlassBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "DAILY BUDGET", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text(text = "$calories kcal", fontSize = 18.sp, fontWeight = FontWeight.Black, color = NeonGreen)
                }
            }
        }

        // Recommended macro targets list
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ThemeCardBgTranslucent)
                .border(1.dp, ThemeGlassBorder, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "Macronutrient Target Split", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ThemeTextSecondary)
            
            MacroSplitRow(color = HyperViolet, label = "Protein", value = "${protein}g")
            MacroSplitRow(color = com.superfit.app.theme.ElectricCyan, label = "Carbohydrates", value = "${carbs}g")
            MacroSplitRow(color = NeonGreen, label = "Fat", value = "${fat}g")
        }

        // Checklist configurations
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ThemeCardBgTranslucent)
                .border(1.dp, ThemeGlassBorder, RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChecklistRow(label = "💚 Health Connect Integration", value = if (uiState.hasHealthConnectPermissions) "Active Sync" else "Not Configured", isOk = uiState.hasHealthConnectPermissions)
            ChecklistRow(label = "🔑 Gemini AI Parser key", value = if (uiState.apiKey.isNotEmpty()) "Configured" else "Not Configured", isOk = uiState.apiKey.isNotEmpty())
            ChecklistRow(label = "🎙️ Microphone Voice ledger", value = if (uiState.isMicPermissionGranted) "Access Granted" else "Not Granted", isOk = uiState.isMicPermissionGranted)
        }
    }

    Button(
        onClick = onStartClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(GradientStart, GradientEnd))),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Activate Personal AI Coach", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

// Sub-widgets and helper components
@Composable
private fun FeatureItemBullet(icon: String, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ThemeTextPrimary.copy(alpha = 0.04f))
                .border(1.dp, ThemeGlassBorder, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 16.sp)
        }
        Column {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ThemeTextPrimary)
            Text(text = desc, fontSize = 11.sp, color = Color.Gray, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun StepGuideItem(number: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(HyperViolet.copy(alpha = 0.12f))
                .border(1.dp, HyperViolet.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = number, color = HyperViolet, fontWeight = FontWeight.Black, fontSize = 10.sp)
        }
        Text(text = text, fontSize = 12.sp, color = ThemeTextSecondary)
    }
}

@Composable
private fun MacroSplitRow(color: Color, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
            Text(text = label, fontSize = 13.sp, color = ThemeTextPrimary)
        }
        Text(text = value, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ThemeTextPrimary)
    }
}

@Composable
private fun ChecklistRow(label: String, value: String, isOk: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = ThemeTextSecondary)
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = if (isOk) NeonGreen else Color.Gray
        )
    }
}
